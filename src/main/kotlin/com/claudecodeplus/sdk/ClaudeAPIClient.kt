package com.claudecodeplus.sdk

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.Disposable
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 使用 IntelliJ 平台 API 的 Claude API 客户端 - 已废弃
 * 现在直接使用 ClaudeCliWrapper 调用 claude 命令
 * 
 * @deprecated 使用 ClaudeCliWrapper 代替
 */
@Deprecated("使用 ClaudeCliWrapper 代替", ReplaceWith("ClaudeCliWrapper"))
class ClaudeAPIClient(private val port: Int = 9925) : Disposable {
    companion object {
        private val LOG = Logger.getInstance(ClaudeAPIClient::class.java)
    }
    
    private var webSocketClient: WebSocketClient? = null
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val responseChannels = ConcurrentHashMap<String, Channel<Map<String, Any?>>>()
    private val streamChannels = ConcurrentHashMap<String, Channel<Map<String, Any?>>>()
    
    /**
     * 连接到 WebSocket 服务器
     */
    suspend fun connect(): Boolean = suspendCoroutine { cont ->
        try {
            val uri = URI("ws://localhost:$port")
            LOG.info("Attempting to connect to WebSocket at: $uri")
            
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(handshakedata: ServerHandshake) {
                    LOG.info("WebSocket connected to port $port")
                    cont.resume(true)
                }
                
                override fun onMessage(message: String) {
                    handleMessage(message)
                }
                
                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    LOG.info("WebSocket closed: $reason (code: $code)")
                    closeAllChannels()
                }
                
                override fun onError(ex: Exception) {
                    LOG.error("WebSocket error", ex)
                    // Only resume if continuation is still active
                    try {
                        cont.resumeWithException(ex)
                    } catch (e: Exception) {
                        // Continuation already completed
                    }
                }
            }
            
            webSocketClient?.connect()
            
        } catch (e: Exception) {
            LOG.error("Failed to create WebSocket connection", e)
            cont.resumeWithException(e)
        }
    }
    
    
    /**
     * 处理接收到的消息
     */
    private fun handleMessage(message: String) {
        try {
            val data = objectMapper.readValue<Map<String, Any?>>(message)
            val id = data["id"] as? String
            val type = data["type"] as? String
            
            when (type) {
                "response" -> {
                    id?.let { responseChannels[it]?.trySend(data) }
                }
                "stream", "stream.start", "stream.chunk", "stream.end", "stream.error" -> {
                    // 处理所有流相关的消息类型
                    id?.let { streamChannels[it]?.trySend(data) }
                }
                "error" -> {
                    LOG.error("Received error message: $data")
                    id?.let {
                        responseChannels[it]?.close()
                        streamChannels[it]?.close()
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to handle message", e)
        }
    }
    
    /**
     * 发送请求并获取响应
     */
    private suspend fun sendRequest(request: Map<String, Any?>): Map<String, Any?> {
        val id = request["id"] as? String ?: throw IllegalArgumentException("Request must have an id")
        val responseChannel = Channel<Map<String, Any?>>(1)
        responseChannels[id] = responseChannel
        
        try {
            val requestJson = objectMapper.writeValueAsString(request)
            LOG.info("Sending request: $requestJson")
            webSocketClient?.send(requestJson)
            return responseChannel.receive()
        } finally {
            responseChannels.remove(id)
            responseChannel.close()
        }
    }
    
    /**
     * 发送流式请求
     */
    private suspend fun sendStreamRequest(request: Map<String, Any?>): Flow<Map<String, Any?>> {
        val id = request["id"] as? String ?: throw IllegalArgumentException("Request must have an id")
        val streamChannel = Channel<Map<String, Any?>>(Channel.UNLIMITED)
        streamChannels[id] = streamChannel
        
        val requestJson = objectMapper.writeValueAsString(request)
        LOG.info("Sending stream request: $requestJson")
        webSocketClient?.send(requestJson)
        
        return flow {
            try {
                for (message in streamChannel) {
                    emit(message)
                    
                    // 检查是否是最后一条消息
                    if (message["done"] == true) {
                        break
                    }
                }
            } finally {
                streamChannels.remove(id)
                streamChannel.close()
            }
        }
    }
    
    /**
     * 获取健康状态
     */
    suspend fun getHealthStatus(): HealthStatus {
        return try {
            val response = sendRequest(
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "type" to "health"
                )
            )
            
            val data = response["data"] as? Map<*, *>
            HealthStatus(
                isHealthy = data?.get("isHealthy") as? Boolean ?: false,
                isProcessing = data?.get("isProcessing") as? Boolean ?: false
            )
        } catch (e: Exception) {
            LOG.error("Failed to get health status", e)
            HealthStatus(false, false)
        }
    }
    
    /**
     * 创建流式对话
     */
    suspend fun createStreamMessage(
        messages: List<Map<String, String>>,
        options: ClaudeOptions = ClaudeOptions()
    ): Flow<StreamResponse> {
        val request = mapOf(
            "id" to UUID.randomUUID().toString(),
            "type" to "stream",
            "data" to mapOf(
                "messages" to messages,
                "options" to mapOf(
                    "model" to options.model,
                    "max_tokens" to options.maxTokens,
                    "temperature" to options.temperature,
                    "system" to options.system,
                    "mcp" to options.mcp
                )
            )
        )
        
        return sendStreamRequest(request).map { message ->
            val type = message["type"] as? String
            val data = message["data"] as? Map<*, *>
            
            when (type) {
                "stream.start" -> {
                    // 流开始，返回一个开始消息
                    StreamResponse.MessageStart(
                        id = data?.get("sessionId") as? String ?: "",
                        model = "claude"
                    )
                }
                "stream.chunk" -> {
                    // 流数据块，包含实际的文本内容
                    val chunk = data?.get("chunk") as? String ?: ""
                    StreamResponse.ContentBlockDelta(
                        index = 0,
                        text = chunk
                    )
                }
                "stream.end" -> {
                    // 流结束
                    StreamResponse.MessageStop
                }
                "stream.error" -> {
                    // 流错误
                    val error = message["error"] as? Map<*, *>
                    StreamResponse.Error(
                        message = error?.get("message") as? String ?: "Unknown error"
                    )
                }
                else -> StreamResponse.Unknown
            }
        }
    }
    
    /**
     * 中断流式对话
     */
    suspend fun abortStream(streamId: String) {
        try {
            sendRequest(
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "type" to "abort",
                    "data" to mapOf(
                        "sessionId" to streamId
                    )
                )
            )
        } catch (e: Exception) {
            LOG.error("Failed to abort stream", e)
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        webSocketClient?.close()
        closeAllChannels()
    }
    
    /**
     * 关闭所有通道
     */
    private fun closeAllChannels() {
        responseChannels.values.forEach { it.close() }
        responseChannels.clear()
        streamChannels.values.forEach { it.close() }
        streamChannels.clear()
    }
    
    /**
     * 检查连接状态
     */
    fun isConnected(): Boolean {
        return webSocketClient?.isOpen == true
    }
    
    override fun dispose() {
        disconnect()
    }
}

/**
 * 流式响应类型
 */
sealed class StreamResponse {
    data class MessageStart(val id: String, val model: String) : StreamResponse()
    data class ContentBlockDelta(val index: Int, val text: String) : StreamResponse()
    object MessageStop : StreamResponse()
    data class Error(val message: String) : StreamResponse()
    object Unknown : StreamResponse()
}