package com.claudecodeplus.sdk

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Claude API 客户端，用于与 Node.js 服务通信
 */
class ClaudeAPIClient(
    private val baseUrl: String = "http://127.0.0.1:18080"
) {
    companion object {
        private val logger = Logger.getInstance(ClaudeAPIClient::class.java)
        private const val TIMEOUT = 60000 // 60秒超时
    }
    
    private var sessionId: String? = null
    
    /**
     * 健康检查
     */
    suspend fun checkHealth(): HealthStatus = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                logger.info("Health check response: $response")
                
                // 解析响应
                val json = JSONObject(response)
                return@withContext HealthStatus(
                    isHealthy = json.getString("status") == "ok",
                    isProcessing = json.optBoolean("is_processing", false),
                    activeSessions = json.optInt("active_sessions", 0)
                )
            }
            
            logger.error("Health check failed with code: $responseCode")
            return@withContext HealthStatus(false, false, 0)
        } catch (e: Exception) {
            logger.error("Health check error", e)
            return@withContext HealthStatus(false, false, 0)
        }
    }
    
    /**
     * 中断当前请求
     */
    suspend fun abortCurrentRequest(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/abort")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            
            // 发送空请求体
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write("{}")
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                return@withContext json.getBoolean("success")
            }
            
            return@withContext false
        } catch (e: Exception) {
            logger.error("Abort request error", e)
            return@withContext false
        }
    }
    
    /**
     * 流式发送消息（使用 Server-Sent Events）
     */
    fun streamMessage(
        message: String,
        newSession: Boolean = false,
        options: Map<String, Any>? = null
    ): Flow<ClaudeResponse> = flow {
        try {
            val url = URL("$baseUrl/stream")
            val connection = url.openConnection() as HttpURLConnection
            
            // 设置请求
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            
            // 构建请求体
            val requestBody = JSONObject().apply {
                put("message", message)
                put("new_session", newSession)
                sessionId?.let { put("session_id", it) }
                options?.let { put("options", JSONObject(it)) }
            }
            
            logger.info("Sending request: ${requestBody.toString()}")
            
            // 发送请求
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            // 检查响应码
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                throw Exception("Server returned $responseCode: $error")
            }
            
            // 读取 SSE 流
            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var line: String?
                var eventData = StringBuilder()
                
                while (reader.readLine().also { line = it } != null) {
                    when {
                        line!!.startsWith("data: ") -> {
                            val data = line!!.substring(6)
                            
                            // 检查是否是结束标记
                            if (data == "[DONE]") {
                                logger.info("Stream completed")
                                break
                            }
                            
                            // 解析 JSON 数据
                            try {
                                val json = JSONObject(data)
                                val response = parseResponse(json)
                                
                                // 更新会话 ID
                                response.sessionId?.let { sessionId = it }
                                
                                emit(response)
                            } catch (e: Exception) {
                                logger.error("Error parsing SSE data: $data", e)
                            }
                        }
                        line!!.isEmpty() -> {
                            // SSE 事件分隔符
                            eventData.clear()
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error("Stream error", e)
            emit(ClaudeResponse(
                type = "error",
                error = e.message ?: "Unknown error"
            ))
        }
    }
    
    /**
     * 发送单次消息
     */
    suspend fun sendMessage(
        message: String,
        newSession: Boolean = false,
        options: Map<String, Any>? = null
    ): ClaudeResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$baseUrl/message")
            val connection = url.openConnection() as HttpURLConnection
            
            // 设置请求
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.doInput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            
            // 构建请求体
            val requestBody = JSONObject().apply {
                put("message", message)
                put("new_session", newSession)
                sessionId?.let { put("session_id", it) }
                options?.let { put("options", JSONObject(it)) }
            }
            
            // 发送请求
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            // 读取响应
            val responseCode = connection.responseCode
            val responseText = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            
            if (responseCode != 200) {
                throw Exception("Server returned $responseCode: $responseText")
            }
            
            // 解析响应
            val json = JSONObject(responseText)
            if (json.getBoolean("success")) {
                sessionId = json.getString("session_id")
                return@withContext ClaudeResponse(
                    type = "text",
                    content = json.getString("response"),
                    sessionId = sessionId
                )
            } else {
                return@withContext ClaudeResponse(
                    type = "error",
                    error = json.optString("error", "Unknown error")
                )
            }
            
        } catch (e: Exception) {
            logger.error("Send message error", e)
            return@withContext ClaudeResponse(
                type = "error",
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * 重置会话
     */
    fun resetSession() {
        sessionId = null
        logger.info("Session reset")
    }
    
    /**
     * 解析响应
     */
    private fun parseResponse(json: JSONObject): ClaudeResponse {
        val type = json.getString("type")
        
        return when (type) {
            "text" -> ClaudeResponse(
                type = type,
                content = json.optString("content"),
                messageType = json.optString("message_type"),
                sessionId = json.optString("session_id")
            )
            "error" -> ClaudeResponse(
                type = type,
                error = json.optString("error", "Unknown error")
            )
            else -> ClaudeResponse(
                type = type,
                content = json.optString("content"),
                messageType = json.optString("message_type"),
                sessionId = json.optString("session_id")
            )
        }
    }
}

/**
 * Claude 响应数据类
 */
data class ClaudeResponse(
    val type: String,
    val content: String? = null,
    val messageType: String? = null,
    val sessionId: String? = null,
    val error: String? = null
)

/**
 * 健康状态数据类
 */
data class HealthStatus(
    val isHealthy: Boolean,
    val isProcessing: Boolean,
    val activeSessions: Int
)