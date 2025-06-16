package com.claudecodeplus.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Service(Service.Level.APP)
class ClaudeHttpClient {
    companion object {
        private val LOG = logger<ClaudeHttpClient>()
        private const val DEFAULT_BASE_URL = "http://127.0.0.1:18080"
        private const val CONNECTION_TIMEOUT = 30L
        private const val READ_TIMEOUT = 300L // 5分钟，用于长对话
    }

    internal val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECTION_TIMEOUT * 1000
            requestTimeoutMillis = READ_TIMEOUT * 1000
            socketTimeoutMillis = CONNECTION_TIMEOUT * 1000
        }
        
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
    
    internal var baseUrl = DEFAULT_BASE_URL
        private set
    private var currentSessionId: String? = null

    @Serializable
    data class MessageRequest(
        val message: String,
        @SerialName("session_id") val sessionId: String? = null,
        @SerialName("new_session") val newSession: Boolean = false,
        val options: Map<String, String>? = null
    )

    @Serializable
    data class MessageResponse(
        val success: Boolean,
        val response: String? = null,
        val error: String? = null
    )

    @Serializable
    data class StreamChunk(
        val type: String,
        val content: String? = null,
        @SerialName("message_type") val messageType: String? = null,
        val error: String? = null,
        @SerialName("session_id") val sessionId: String? = null
    )

    @Serializable
    data class SessionResponse(
        val success: Boolean,
        @SerialName("session_id") val sessionId: String? = null,
        val error: String? = null
    )

    @Serializable
    data class HealthResponse(
        val status: String,
        val initialized: Boolean,
        @SerialName("sdk_available") val sdkAvailable: Boolean,
        @SerialName("active_sessions") val activeSessions: Int
    )

    fun setBaseUrl(url: String) {
        baseUrl = url.removeSuffix("/")
    }

    suspend fun checkHealth(): HealthResponse? = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$baseUrl/health")
            if (response.status.isSuccess()) {
                response.body<HealthResponse>()
            } else {
                LOG.warn("Health check failed: ${response.status}")
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to check health", e)
            null
        }
    }

    suspend fun createSession(): String? = withContext(Dispatchers.IO) {
        try {
            val response = client.post("$baseUrl/session/create") {
                setBody("{}")
            }
            
            if (response.status.isSuccess()) {
                val sessionResponse = response.body<SessionResponse>()
                if (sessionResponse.success) {
                    currentSessionId = sessionResponse.sessionId
                    LOG.info("Created session: $currentSessionId")
                    sessionResponse.sessionId
                } else {
                    LOG.error("Failed to create session: ${sessionResponse.error}")
                    null
                }
            } else {
                LOG.error("Failed to create session: ${response.status}")
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to create session", e)
            null
        }
    }

    suspend fun sendMessage(
        message: String, 
        newSession: Boolean = false,
        options: Map<String, String>? = null
    ): MessageResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = MessageRequest(message, currentSessionId, newSession, options)
            
            val response = client.post("$baseUrl/message") {
                setBody(requestBody)
            }
            
            if (response.status.isSuccess()) {
                val messageResponse = response.body<MessageResponse>()
                // 更新会话ID如果返回了新的
                response.headers["X-Session-Id"]?.let { 
                    currentSessionId = it 
                }
                messageResponse
            } else {
                val errorBody = response.bodyAsText()
                MessageResponse(false, null, "HTTP ${response.status}: $errorBody")
            }
        } catch (e: Exception) {
            LOG.error("Failed to send message", e)
            MessageResponse(false, null, e.message)
        }
    }

    fun sendMessageStream(
        message: String,
        newSession: Boolean = false,
        options: Map<String, String>? = null
    ): Flow<StreamChunk> = flow {
        try {
            val requestBody = MessageRequest(message, currentSessionId, newSession, options)
            
            val response = withContext(Dispatchers.IO) {
                client.post("$baseUrl/stream") {
                    setBody(requestBody)
                }
            }
            
            if (!response.status.isSuccess()) {
                emit(StreamChunk("error", error = "HTTP ${response.status}: ${response.status.description}"))
                return@flow
            }
            
            val channel = response.bodyAsChannel()
            
            withContext(Dispatchers.IO) {
                val buffer = StringBuilder()
                
                while (!channel.isClosedForRead) {
                    val byte = channel.readByte()
                    val char = byte.toInt().toChar()
                    
                    if (char == '\n') {
                        val line = buffer.toString().trim()
                        buffer.clear()
                        
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6)
                            
                            if (data == "[DONE]") {
                                break
                            }
                            
                            try {
                                val json = Json { ignoreUnknownKeys = true }
                                val chunk = json.decodeFromString<StreamChunk>(data)
                                
                                // 检查是否是错误消息
                                if (chunk.type == "error") {
                                    LOG.error("Server error: ${chunk.error}")
                                    emit(chunk)
                                    // 如果是服务未初始化错误，应该中断循环
                                    if (chunk.error?.contains("not initialized") == true) {
                                        break
                                    }
                                } else {
                                    // 更新会话ID
                                    chunk.sessionId?.let { 
                                        if (currentSessionId != it) {
                                            currentSessionId = it
                                            LOG.info("Updated session ID: $currentSessionId")
                                        }
                                    }
                                    emit(chunk)
                                }
                            } catch (e: Exception) {
                                LOG.error("Failed to parse SSE data: $data", e)
                                emit(StreamChunk("error", error = "Failed to parse response: ${e.message}"))
                            }
                        }
                    } else {
                        buffer.append(char)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Stream request failed", e)
            emit(StreamChunk("error", error = "Stream request failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.Default)

    fun getCurrentSessionId(): String? = currentSessionId

    fun clearSession() {
        currentSessionId = null
    }
}