package com.claudecodeplus.service

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.claudecodeplus.util.ResponseLogger
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket 客户端 - 提供实时流式通信
 */
@Service(Service.Level.APP)
class ClaudeWebSocketClient {
    companion object {
        private val LOG = logger<ClaudeWebSocketClient>()
        private const val DEFAULT_WS_URL = "ws://127.0.0.1:18080/ws"
        private const val RECONNECT_DELAY = 5000L // 5秒重连延迟
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES) // WebSocket 无超时
        .build()

    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var currentSessionId: String? = null
    private val messageChannel = Channel<StreamChunk>(Channel.UNLIMITED)
    private var currentLogFile: File? = null
    
    data class WebSocketMessage(
        val command: String,
        val message: String? = null,
        val session_id: String? = null,
        val new_session: Boolean = false,
        val options: Map<String, Any>? = null
    )
    
    data class StreamChunk(
        val type: String,
        val content: String? = null,
        val error: String? = null,
        val session_id: String? = null,
        val message_type: String? = null
    )
    
    private inner class WebSocketListener : okhttp3.WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            LOG.info("WebSocket connected")
            // 创建新的会话日志（WebSocket 暂时使用默认目录）
            currentLogFile = ResponseLogger.createSessionLog("websocket", null)
        }
        
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                // 记录接收到的消息
                currentLogFile?.let { logFile ->
                    ResponseLogger.logWebSocketMessage(logFile, "RECEIVED", text)
                }
                
                val chunk = gson.fromJson(text, StreamChunk::class.java)
                
                // 更新会话ID
                chunk.session_id?.let {
                    if (currentSessionId != it) {
                        currentSessionId = it
                        LOG.info("Updated session ID: $currentSessionId")
                    }
                }
                
                // 发送到通道
                messageChannel.trySend(chunk)
                
            } catch (e: Exception) {
                LOG.error("Failed to parse WebSocket message: $text", e)
            }
        }
        
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            LOG.error("WebSocket error", t)
            messageChannel.trySend(StreamChunk("error", error = t.message))
            // 可以在这里实现重连逻辑
        }
        
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            LOG.info("WebSocket closed: $code $reason")
            // 关闭日志
            currentLogFile?.let {
                ResponseLogger.closeSessionLog(it)
            }
            messageChannel.close()
        }
    }
    
    fun connect(url: String = DEFAULT_WS_URL) {
        disconnect() // 先断开现有连接
        
        val request = Request.Builder()
            .url(url)
            .build()
            
        webSocket = client.newWebSocket(request, WebSocketListener())
    }
    
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        currentSessionId = null
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    fun sendMessageStream(
        message: String,
        newSession: Boolean = false,
        options: Map<String, Any>? = null
    ): Flow<StreamChunk> = flow {
        // 确保连接
        if (webSocket == null) {
            connect()
            // 等待连接建立
            kotlinx.coroutines.delay(500)
        }
        
        // 清空之前的消息
        while (!messageChannel.isEmpty) {
            messageChannel.tryReceive()
        }
        
        // 发送消息
        val wsMessage = WebSocketMessage(
            command = "message",
            message = message,
            session_id = currentSessionId,
            new_session = newSession,
            options = options
        )
        
        val json = gson.toJson(wsMessage)
        
        // 记录发送的消息
        currentLogFile?.let { logFile ->
            ResponseLogger.logWebSocketMessage(logFile, "SENT", json)
        }
        
        webSocket?.send(json) ?: throw IllegalStateException("WebSocket not connected")
        
        // 接收响应
        try {
            while (true) {
                val chunk = withTimeoutOrNull(30000) { // 30秒超时
                    messageChannel.receive()
                }
                
                if (chunk == null) {
                    LOG.warn("Timeout waiting for WebSocket response")
                    emit(StreamChunk(
                        type = "error",
                        content = null,
                        error = "Timeout waiting for response",
                        session_id = currentSessionId,
                        message_type = null
                    ))
                    break
                }
                
                emit(chunk)
                
                // 如果是错误或完成消息，结束循环
                if (chunk.type == "error" || chunk.type == "done" || 
                    (chunk.type == "text" && chunk.content?.contains("</response>") == true)) {
                    break
                }
            }
        } catch (e: Exception) {
            LOG.error("Error receiving WebSocket response", e)
            emit(StreamChunk(
                type = "error",
                content = null,
                error = "Error receiving response: ${e.message}",
                session_id = currentSessionId,
                message_type = null
            ))
        }
    }
    
    suspend fun checkHealth(): Boolean {
        if (webSocket == null) {
            connect()
            kotlinx.coroutines.delay(500)
        }
        
        val healthMessage = WebSocketMessage(command = "health")
        webSocket?.send(gson.toJson(healthMessage))
        
        // 等待健康检查响应
        val response = messageChannel.receive()
        return response.type == "health"
    }
    
    fun getCurrentSessionId(): String? = currentSessionId
    
    fun clearSession() {
        currentSessionId = null
    }
}