package com.claudecodeplus.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Claude Code 服务
 * 管理Claude Code会话的生命周期
 */
@Service(Service.Level.APP)
class ClaudeCodeService {
    companion object {
        private val LOG = logger<ClaudeCodeService>()
        
        fun getInstance(): ClaudeCodeService {
            return service()
        }
    }
    
    private val httpClient = service<ClaudeHttpClient>()
    private val wsClient = service<ClaudeWebSocketClient>()
    
    /**
     * 初始化服务
     */
    fun initialize(config: Map<String, Any>? = null) {
        // 可以在这里设置基础URL等配置
        config?.let {
            // 如果配置中有 baseUrl，更新它
            (it["baseUrl"] as? String)?.let { url ->
                httpClient.setBaseUrl(url)
            }
        }
    }
    
    /**
     * 使用自定义配置初始化服务
     */
    suspend fun initializeWithConfig(
        systemPrompt: String? = null,
        maxTurns: Int? = null,
        allowedTools: List<String>? = null,
        permissionMode: String? = null,
        cwd: String? = null,
        outputStyle: String? = null,
        skipUpdateCheck: Boolean? = null,
        showUsage: Boolean? = null,
        style: String? = null,
        theme: String? = null
    ): Boolean {
        val config = mutableMapOf<String, Any>()
        
        systemPrompt?.let { config["system_prompt"] = it }
        maxTurns?.let { config["max_turns"] = it }
        allowedTools?.let { config["allowed_tools"] = it }
        permissionMode?.let { config["permission_mode"] = it }
        cwd?.let { config["cwd"] = it }
        outputStyle?.let { config["output_style"] = it }
        skipUpdateCheck?.let { config["skip_update_check"] = it }
        showUsage?.let { config["show_usage"] = it }
        style?.let { config["style"] = it }
        theme?.let { config["theme"] = it }
        
        return try {
            // 发送初始化请求到服务器
            val request = okhttp3.Request.Builder()
                .url("${httpClient.baseUrl}/initialize")
                .post(
                    com.google.gson.Gson().toJson(mapOf("config" to config))
                        .toRequestBody("application/json".toMediaType())
                )
                .build()
            
            val response = httpClient.client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            LOG.error("Failed to initialize with config", e)
            false
        }
    }
    
    /**
     * 销毁服务
     */
    fun dispose() {
        httpClient.clearSession()
        wsClient.disconnect()
    }
    
    suspend fun checkServiceHealth(): Boolean {
        return try {
            // 优先尝试 WebSocket 健康检查
            val wsHealthy = wsClient.checkHealth()
            if (wsHealthy) {
                return true
            }
            
            // 如果 WebSocket 不可用，回退到 HTTP
            val health = httpClient.checkHealth()
            health != null && health.status == "ok"
        } catch (e: Exception) {
            LOG.error("Failed to check service health", e)
            false
        }
    }
    
    suspend fun sendMessage(
        message: String,
        newSession: Boolean = false,
        options: Map<String, Any>? = null
    ): String {
        return try {
            val response = httpClient.sendMessage(message, newSession, options)
            if (response.success) {
                response.response ?: "No response received"
            } else {
                "Error: ${response.error ?: "Unknown error"}"
            }
        } catch (e: Exception) {
            LOG.error("Failed to send message", e)
            "Error: ${e.message}"
        }
    }
    
    fun sendMessageStream(
        message: String,
        newSession: Boolean = false,
        options: Map<String, Any>? = null
    ): Flow<ClaudeHttpClient.StreamChunk> {
        return try {
            // 优先使用 WebSocket
            wsClient.sendMessageStream(message, newSession, options)
                .map { wsChunk ->
                    // 将 WebSocket 的 StreamChunk 转换为 HttpClient 的 StreamChunk
                    ClaudeHttpClient.StreamChunk(
                        type = wsChunk.type,
                        content = wsChunk.content,
                        error = wsChunk.error,
                        session_id = wsChunk.session_id,
                        message_type = wsChunk.message_type
                    )
                }
        } catch (e: Exception) {
            LOG.warn("WebSocket stream failed, falling back to HTTP", e)
            try {
                httpClient.sendMessageStream(message, newSession, options)
            } catch (httpError: Exception) {
                LOG.error("Failed to send message stream via HTTP", httpError)
                emptyFlow()
            }
        }
    }
    
    suspend fun createNewSession(): Boolean {
        // 新会话的创建现在由服务器端管理
        // 只需要清除本地的会话ID
        httpClient.clearSession()
        return true
    }
    
    fun clearSession() {
        httpClient.clearSession()
        wsClient.clearSession()
    }
    
    fun getCurrentSessionId(): String? {
        // 优先返回 WebSocket 的会话 ID
        return wsClient.getCurrentSessionId() ?: httpClient.getCurrentSessionId()
    }
}