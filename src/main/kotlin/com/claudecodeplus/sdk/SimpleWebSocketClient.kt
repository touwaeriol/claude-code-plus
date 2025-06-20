package com.claudecodeplus.sdk

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.intellij.openapi.diagnostic.Logger
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.WebSocketConnectOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * 简单的 WebSocket 客户端
 */
class SimpleWebSocketClient(private val port: Int = 9925) {
    companion object {
        private val logger = Logger.getInstance(SimpleWebSocketClient::class.java)
    }
    
    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }
    
    private val vertx = Vertx.vertx()
    private var httpClient: HttpClient? = null
    private var webSocket: io.vertx.core.http.WebSocket? = null
    
    private val responseHandlers = ConcurrentHashMap<String, (Map<String, Any?>) -> Unit>()
    private val streamHandlers = ConcurrentHashMap<String, (String) -> Unit>()
    
    /**
     * 连接到 WebSocket 服务器
     */
    suspend fun connect(): Boolean = suspendCoroutine { cont ->
        try {
            logger.info("Connecting to WebSocket server on port: $port")
            
            // 创建 HTTP 客户端
            httpClient = vertx.createHttpClient(HttpClientOptions())
            
            // 创建连接选项
            val connectOptions = WebSocketConnectOptions()
                .setHost("localhost")
                .setPort(port)
                .setURI("/")
            
            // 连接 WebSocket
            httpClient!!.webSocket(connectOptions) { ar ->
                if (ar.succeeded()) {
                    webSocket = ar.result()
                    logger.info("Connected to WebSocket server")
                    
                    // 设置消息处理器
                    webSocket!!.textMessageHandler { message ->
                        handleMessage(message)
                    }
                    
                    // 设置关闭处理器
                    webSocket!!.closeHandler {
                        logger.info("WebSocket connection closed")
                        responseHandlers.clear()
                        streamHandlers.clear()
                    }
                    
                    // 设置错误处理器
                    webSocket!!.exceptionHandler { error ->
                        logger.error("WebSocket error", error)
                    }
                    
                    cont.resume(true)
                } else {
                    logger.error("Failed to connect to WebSocket server", ar.cause())
                    cont.resume(false)
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error connecting to WebSocket server", e)
            cont.resumeWithException(e)
        }
    }
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(message: String) {
        try {
            @Suppress("UNCHECKED_CAST")
            val json = objectMapper.readValue(message, Map::class.java) as Map<String, Any?>
            
            val type = json["type"] as? String ?: return
            val id = json["id"] as? String
            @Suppress("UNCHECKED_CAST")
            val data = json["data"] as? Map<String, Any?>
            
            when (type) {
                "welcome" -> {
                    logger.info("Received welcome message: ${data?.get("message")}")
                }
                
                "health", "abort", "stream.start", "stream.end" -> {
                    // 响应类消息
                    if (id != null) {
                        responseHandlers.remove(id)?.invoke(json)
                    }
                }
                
                "stream.chunk" -> {
                    // 流数据块
                    val sessionId = data?.get("sessionId") as? String ?: return
                    val chunk = data["chunk"] as? String ?: ""
                    streamHandlers[sessionId]?.invoke(chunk)
                }
                
                "error", "stream.error" -> {
                    // 错误消息
                    logger.error("Received error: ${json["error"]}")
                    if (id != null) {
                        responseHandlers.remove(id)?.invoke(json)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling message: $message", e)
        }
    }
    
    /**
     * 发送请求并等待响应
     */
    suspend fun request(type: String, data: Any? = null): Map<String, Any?> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val request = mutableMapOf<String, Any?>(
            "type" to type,
            "id" to id
        )
        if (data != null) {
            request["data"] = data
        }
        
        suspendCoroutine { cont ->
            responseHandlers[id] = { response ->
                cont.resume(response)
            }
            
            try {
                val requestJson = objectMapper.writeValueAsString(request)
                webSocket?.writeTextMessage(requestJson)
                
                // 设置超时
                GlobalScope.launch {
                    delay(30000) // 30秒超时
                    if (responseHandlers.containsKey(id)) {
                        responseHandlers.remove(id)
                        cont.resumeWithException(TimeoutException("Request timeout"))
                    }
                }
            } catch (e: Exception) {
                responseHandlers.remove(id)
                cont.resumeWithException(e)
            }
        }
    }
    
    /**
     * 健康检查
     */
    suspend fun health(): HealthStatus {
        val response = request("health")
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?> ?: throw Exception("Invalid response")
        
        return HealthStatus(
            isHealthy = data["isHealthy"] as? Boolean ?: false,
            isProcessing = data["isProcessing"] as? Boolean ?: false
        )
    }
    
    /**
     * 流式请求
     */
    fun streamChat(
        message: String,
        options: ClaudeOptions? = null,
        onChunk: (String) -> Unit
    ): Flow<String> = flow<String> {
        val id = UUID.randomUUID().toString()
        
        // 发送流式请求
        val response = request("stream", mapOf(
            "message" to message,
            "options" to (options ?: ClaudeOptions())
        ))
        
        @Suppress("UNCHECKED_CAST")
        val startData = response["data"] as? Map<String, Any?>
        val sessionId = startData?.get("sessionId") as? String
            ?: throw Exception("No session ID in response")
        
        // 注册流处理器
        streamHandlers[sessionId] = onChunk
        
        try {
            // 等待流结束
            val endResponse = suspendCoroutine<Map<String, Any?>> { cont ->
                responseHandlers[id] = { response ->
                    cont.resume(response)
                }
            }
            
            @Suppress("UNCHECKED_CAST")
            val endData = endResponse["data"] as? Map<String, Any?>
            val status = endData?.get("status") as? String
            
            if (status == "aborted") {
                throw CancellationException("Stream aborted")
            }
            
        } finally {
            streamHandlers.remove(sessionId)
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * 中止流
     */
    suspend fun abort(sessionId: String? = null) {
        request("abort", mapOf("sessionId" to sessionId))
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        try {
            webSocket?.close()
            httpClient?.close()
            responseHandlers.clear()
            streamHandlers.clear()
            logger.info("WebSocket connection closed")
        } catch (e: Exception) {
            logger.error("Error closing WebSocket connection", e)
        } finally {
            vertx.close()
        }
    }
    
    /**
     * 检查连接是否存活
     */
    fun isConnected(): Boolean = webSocket?.isClosed == false
}

/**
 * 超时异常
 */
class TimeoutException(message: String) : Exception(message)