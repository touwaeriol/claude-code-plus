package com.claudecodeplus.service

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Service(Service.Level.APP)
class ClaudeHttpClient {
    companion object {
        private val LOG = logger<ClaudeHttpClient>()
        private const val DEFAULT_BASE_URL = "http://127.0.0.1:18080"
        private const val CONNECTION_TIMEOUT = 30L
        private const val READ_TIMEOUT = 300L // 5分钟，用于长对话
    }

    internal val client = OkHttpClient.Builder()
        .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    internal var baseUrl = DEFAULT_BASE_URL
        private set
    private var currentSessionId: String? = null

    data class MessageRequest(
        val message: String,
        val session_id: String? = null,
        val new_session: Boolean = false,
        val options: Map<String, Any>? = null
    )

    data class MessageResponse(
        val success: Boolean,
        val response: String? = null,
        val error: String? = null
    )

    data class StreamChunk(
        val type: String,
        val content: String? = null,
        val message_type: String? = null,
        val error: String? = null,
        val session_id: String? = null
    )

    data class SessionResponse(
        val success: Boolean,
        val session_id: String? = null,
        val error: String? = null
    )

    data class HealthResponse(
        val status: String,
        val initialized: Boolean,
        val sdk_available: Boolean,
        val active_sessions: Int
    )

    fun setBaseUrl(url: String) {
        baseUrl = url.removeSuffix("/")
    }

    suspend fun checkHealth(): HealthResponse? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { json ->
                    gson.fromJson(json, HealthResponse::class.java)
                }
            } else {
                LOG.warn("Health check failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            LOG.error("Failed to check health", e)
            null
        }
    }

    suspend fun createSession(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/session/create")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { json ->
                    val sessionResponse = gson.fromJson(json, SessionResponse::class.java)
                    if (sessionResponse.success) {
                        currentSessionId = sessionResponse.session_id
                        LOG.info("Created session: $currentSessionId")
                        sessionResponse.session_id
                    } else {
                        LOG.error("Failed to create session: ${sessionResponse.error}")
                        null
                    }
                }
            } else {
                LOG.error("Failed to create session: ${response.code}")
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
        options: Map<String, Any>? = null
    ): MessageResponse = withContext(Dispatchers.IO) {
        try {
            val requestBody = MessageRequest(message, currentSessionId, newSession, options)
            val json = gson.toJson(requestBody)
            
            val request = Request.Builder()
                .url("$baseUrl/message")
                .post(json.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                val messageResponse = gson.fromJson(responseBody, MessageResponse::class.java)
                // 更新会话ID如果返回了新的
                response.headers["X-Session-Id"]?.let { 
                    currentSessionId = it 
                }
                messageResponse
            } else {
                MessageResponse(false, null, "HTTP ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            LOG.error("Failed to send message", e)
            MessageResponse(false, null, e.message)
        }
    }

    fun sendMessageStream(
        message: String,
        newSession: Boolean = false,
        options: Map<String, Any>? = null
    ): Flow<StreamChunk> = flow {
        // 不需要手动管理会话，服务器会自动处理默认会话
        // 只在明确要求新会话时传递 newSession 参数
        
        val requestBody = MessageRequest(message, currentSessionId, newSession, options)
        val json = gson.toJson(requestBody)
        
        val request = Request.Builder()
            .url("$baseUrl/stream")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }
        
        if (!response.isSuccessful) {
            emit(StreamChunk("error", error = "HTTP ${response.code}: ${response.message}"))
            return@flow
        }
        
        response.body?.let { body ->
            val chunks = mutableListOf<StreamChunk>()
            
            withContext(Dispatchers.IO) {
                body.source().use { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6)
                            
                            if (data == "[DONE]") {
                                break
                            }
                            
                            try {
                                val chunk = gson.fromJson(data, StreamChunk::class.java)
                                
                                // 检查是否是错误消息
                                if (chunk.type == "error") {
                                    LOG.error("Server error: ${chunk.error}")
                                    chunks.add(chunk)
                                    // 如果是服务未初始化错误，应该中断循环
                                    if (chunk.error?.contains("not initialized") == true) {
                                        break
                                    }
                                } else {
                                    // 更新会话ID
                                    chunk.session_id?.let { 
                                        if (currentSessionId != it) {
                                            currentSessionId = it
                                            LOG.info("Updated session ID: $currentSessionId")
                                        }
                                    }
                                    chunks.add(chunk)
                                }
                            } catch (e: Exception) {
                                LOG.error("Failed to parse SSE data: $data", e)
                                // 尝试创建一个错误 chunk
                                chunks.add(StreamChunk("error", error = "Failed to parse response: ${e.message}"))
                            }
                        }
                    }
                }
            }
            
            // 在正确的上下文中 emit
            chunks.forEach { chunk ->
                emit(chunk)
            }
        }
    }.flowOn(Dispatchers.Default)

    fun getCurrentSessionId(): String? = currentSessionId

    fun clearSession() {
        currentSessionId = null
    }
}