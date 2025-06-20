package com.claudecodeplus.sdk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于 Unix Domain Socket 的 JSON-RPC 客户端
 * 使用 Java 16+ 的原生 Unix socket 支持
 */
class UnixSocketClient(private val nodeServicePath: String) {
    private val logger = Logger.getInstance(UnixSocketClient::class.java)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    private var process: Process? = null
    private var socketChannel: SocketChannel? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    
    private val requestIdCounter = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonRpcResponse>>()
    private val streamHandlers = ConcurrentHashMap<String, (JsonRpcNotification) -> Unit>()
    
    private var socketPath: String? = null
    private var readingJob: Job? = null
    
    // JSON-RPC 2.0 数据结构
    data class JsonRpcRequest(
        val jsonrpc: String = "2.0",
        val id: Any,
        val method: String,
        val params: Any? = null
    )
    
    data class JsonRpcResponse(
        val jsonrpc: String,
        val id: Any?,
        val result: Any? = null,
        val error: JsonRpcError? = null
    )
    
    data class JsonRpcError(
        val code: Int,
        val message: String,
        val data: Any? = null
    )
    
    data class JsonRpcNotification(
        val jsonrpc: String,
        val method: String,
        val params: Any? = null
    )
    
    /**
     * 启动 Node 服务并连接到 Unix socket
     */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 启动 Node 进程
            val command = listOf(
                "node",
                "$nodeServicePath/start.js"
            )
            
            logger.info("Starting Node service: ${command.joinToString(" ")}")
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(nodeServicePath))
            processBuilder.environment()["NODE_ENV"] = "production"
            
            process = processBuilder.start()
            
            // 读取 socket 路径
            val stdoutReader = BufferedReader(InputStreamReader(process!!.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process!!.errorStream))
            
            // 启动错误流读取（简单记录，不做处理）
            GlobalScope.launch {
                try {
                    var line: String?
                    while (stderrReader.readLine().also { line = it } != null) {
                        if (line!!.isNotBlank()) {
                            logger.warn("[Node Stderr] $line")
                        }
                    }
                } catch (e: Exception) {
                    logger.debug("Error stream closed")
                }
            }
            
            // 等待 socket 路径
            var line: String?
            var attempts = 0
            while (stdoutReader.readLine().also { line = it } != null && attempts < 50) {
                logger.debug("Node output: $line")
                
                // 只查找 socket 路径，错误处理交给 NodeServiceManager
                if (line!!.startsWith("SOCKET_PATH:")) {
                    socketPath = line!!.substringAfter("SOCKET_PATH:")
                    logger.info("Got socket path: $socketPath")
                    break
                }
                attempts++
                delay(100)
            }
            
            if (socketPath == null) {
                // 检查进程是否异常退出
                process?.let { proc ->
                    if (!proc.isAlive) {
                        val exitCode = proc.exitValue()
                        logger.error("Node process exited with code: $exitCode")
                    }
                }
                throw Exception("Failed to get socket path from Node service")
            }
            
            // 等待 socket 文件创建
            delay(500)
            
            // 连接到 Unix socket
            connectToSocket()
            
            true
        } catch (e: Exception) {
            logger.error("Failed to start service", e)
            stop()
            false
        }
    }
    
    /**
     * 连接到 Unix socket
     */
    private suspend fun connectToSocket() = withContext(Dispatchers.IO) {
        val socketAddress = UnixDomainSocketAddress.of(Path.of(socketPath!!))
        socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)
        socketChannel!!.connect(socketAddress)
        
        logger.info("Connected to Unix socket: $socketPath")
        
        // 创建读写流
        val inputStream = java.nio.channels.Channels.newInputStream(socketChannel)
        val outputStream = java.nio.channels.Channels.newOutputStream(socketChannel)
        
        reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        
        // 启动读取协程
        readingJob = GlobalScope.launch {
            readLoop()
        }
    }
    
    /**
     * 读取循环
     */
    private suspend fun readLoop() = withContext(Dispatchers.IO) {
        try {
            var line: String?
            while (reader?.readLine().also { line = it } != null) {
                try {
                    handleMessage(line!!)
                } catch (e: Exception) {
                    logger.error("Error handling message: $line", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Read loop error", e)
        } finally {
            // 连接断开，清理所有待处理请求
            pendingRequests.values.forEach { 
                it.completeExceptionally(IOException("Connection closed"))
            }
            pendingRequests.clear()
            streamHandlers.clear()
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(line: String) {
        val json = objectMapper.readTree(line)
        
        // 判断消息类型
        when {
            json.has("id") && (json.has("result") || json.has("error")) -> {
                // 响应消息
                val response: JsonRpcResponse = objectMapper.readValue(line)
                val idStr = response.id.toString()
                pendingRequests.remove(idStr)?.complete(response)
            }
            json.has("method") && !json.has("id") -> {
                // 通知消息
                val notification: JsonRpcNotification = objectMapper.readValue(line)
                handleNotification(notification)
            }
        }
    }
    
    /**
     * 处理通知消息
     */
    private fun handleNotification(notification: JsonRpcNotification) {
        when (notification.method) {
            "stream.start" -> {
                val params = notification.params as? Map<*, *>
                val id = params?.get("id")?.toString()
                logger.debug("Stream started: $id")
            }
            "stream.chunk" -> {
                val params = notification.params as? Map<*, *>
                val id = params?.get("id")?.toString()
                if (id != null) {
                    streamHandlers[id]?.invoke(notification)
                }
            }
        }
    }
    
    /**
     * 发送请求并等待响应
     */
    private suspend fun sendRequest(method: String, params: Any? = null): JsonRpcResponse {
        val id = requestIdCounter.getAndIncrement().toString()
        val request = JsonRpcRequest(
            id = id,
            method = method,
            params = params
        )
        
        val deferred = CompletableDeferred<JsonRpcResponse>()
        pendingRequests[id] = deferred
        
        return try {
            send(request)
            
            withTimeout(30000) {
                val response = deferred.await()
                if (response.error != null) {
                    throw Exception("${response.error.message} (code: ${response.error.code})")
                }
                response
            }
        } catch (e: Exception) {
            pendingRequests.remove(id)
            throw e
        }
    }
    
    /**
     * 发送数据
     */
    private suspend fun send(data: Any) = withContext(Dispatchers.IO) {
        val json = objectMapper.writeValueAsString(data) + "\n"
        writer?.write(json)
        writer?.flush()
    }
    
    /**
     * 健康检查
     */
    suspend fun checkHealth(): HealthStatus {
        val response = sendRequest("health")
        val result = response.result as Map<*, *>
        
        return HealthStatus(
            isHealthy = result["isHealthy"] as Boolean,
            isProcessing = result["isProcessing"] as Boolean,
            activeSessions = (result["activeSessions"] as Number).toInt()
        )
    }
    
    /**
     * 流式消息
     */
    fun streamMessage(
        message: String,
        options: ClaudeOptions,
        onChunk: (String) -> Unit
    ): Flow<String> = flow {
        val id = requestIdCounter.getAndIncrement().toString()
        val chunks = Channel<String>(Channel.UNLIMITED)
        
        // 注册流处理器
        streamHandlers[id] = { notification ->
            val params = notification.params as? Map<*, *>
            val chunk = params?.get("chunk") as? String
            if (chunk != null) {
                runBlocking {
                    chunks.send(chunk)
                }
                onChunk(chunk)
            }
        }
        
        try {
            // 发送流请求
            val response = sendRequest("stream", mapOf(
                "message" to message,
                "options" to options
            ))
            
            // 等待流完成
            val result = response.result as? Map<*, *>
            val status = result?.get("status") as? String
            
            if (status == "completed") {
                chunks.close()
            } else if (status == "aborted") {
                chunks.close()
                throw CancellationException("Stream aborted")
            }
            
            // 从 channel 读取所有数据
            for (chunk in chunks) {
                emit(chunk)
            }
            
        } finally {
            streamHandlers.remove(id)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 中止请求
     */
    suspend fun abort(streamId: String? = null): Boolean {
        val params = if (streamId != null) {
            mapOf("streamId" to streamId)
        } else {
            null
        }
        
        val response = sendRequest("abort", params)
        val result = response.result as? Map<*, *>
        return result?.get("aborted") as? Boolean ?: false
    }
    
    /**
     * 停止服务
     */
    fun stop() {
        try {
            readingJob?.cancel()
            writer?.close()
            reader?.close()
            socketChannel?.close()
            process?.destroyForcibly()
            
            pendingRequests.clear()
            streamHandlers.clear()
            
            logger.info("Service stopped")
        } catch (e: Exception) {
            logger.error("Error stopping service", e)
        }
    }
    
    /**
     * 检查服务是否存活
     */
    fun isAlive(): Boolean = process?.isAlive == true && socketChannel?.isOpen == true
}