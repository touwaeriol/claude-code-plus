package com.claudecodeplus.ui.models

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 全局 Claude CLI Wrapper 管理器
 * 
 * 提供单例的 ClaudeCliWrapper 实例，支持多会话并发访问
 * 通过会话ID区分不同会话的输出回调
 */
object GlobalCliWrapper {
    
    /**
     * 全局唯一的 ClaudeCliWrapper 实例
     */
    val instance: com.claudecodeplus.sdk.ClaudeCliWrapper by lazy {
        com.claudecodeplus.sdk.ClaudeCliWrapper().also { wrapper ->
            // 设置全局输出回调，根据会话ID分发到具体会话
            wrapper.setOutputLineCallback { jsonLine ->
                handleGlobalOutput(jsonLine)
            }
        }
    }
    
    /**
     * 会话ID到输出回调的映射
     * 使用 ConcurrentHashMap 保证线程安全
     */
    private val sessionCallbacks = ConcurrentHashMap<String, (String) -> Unit>()
    
    /**
     * 注册会话的输出回调
     * @param sessionId 会话ID，为null时使用默认会话
     * @param callback 输出回调函数
     */
    fun registerSessionCallback(sessionId: String?, callback: (String) -> Unit) {
        val key = sessionId ?: "default"
        sessionCallbacks[key] = callback
        println("[GlobalCliWrapper] 注册会话回调: sessionId=$key")
    }
    
    /**
     * 注销会话的输出回调
     * @param sessionId 会话ID
     */
    fun unregisterSessionCallback(sessionId: String?) {
        val key = sessionId ?: "default"
        sessionCallbacks.remove(key)
        println("[GlobalCliWrapper] 注销会话回调: sessionId=$key")
    }
    
    /**
     * 处理全局输出，分发到对应的会话
     */
    private fun handleGlobalOutput(jsonLine: String) {
        try {
            // 解析JSON以提取会话信息
            val sessionId = extractSessionIdFromOutput(jsonLine)
            val key = sessionId ?: "default"
            
            // 查找对应会话的回调
            val callback = sessionCallbacks[key]
            if (callback != null) {
                println("[GlobalCliWrapper] 找到会话 $key 的回调，转发消息")
                callback(jsonLine)
            } else {
                // 如果找不到具体会话，尝试智能匹配
                val matchedCallback = findBestMatchingCallback(sessionId, jsonLine)
                if (matchedCallback != null) {
                    matchedCallback(jsonLine)
                } else {
                    println("[GlobalCliWrapper] 收到无归属的输出: sessionId=$sessionId, 活跃会话=${sessionCallbacks.keys}")
                    println("[GlobalCliWrapper] 输出内容前100字符: ${jsonLine.take(100)}...")
                    
                    // 如果只有一个活跃会话，直接发送给它
                    if (sessionCallbacks.size == 1) {
                        val singleCallback = sessionCallbacks.values.first()
                        val singleSessionId = sessionCallbacks.keys.first()
                        println("[GlobalCliWrapper] 只有一个活跃会话 $singleSessionId，直接转发")
                        singleCallback(jsonLine)
                    }
                }
            }
        } catch (e: Exception) {
            println("[GlobalCliWrapper] 处理全局输出异常: ${e.message}")
            e.printStackTrace()
            
            // 异常情况下，尝试发送给所有活跃会话
            sessionCallbacks.values.forEach { callback ->
                try {
                    callback(jsonLine)
                } catch (callbackException: Exception) {
                    println("[GlobalCliWrapper] 回调执行异常: ${callbackException.message}")
                }
            }
        }
    }
    
    /**
     * 智能匹配最佳的回调函数
     * 处理会话ID不匹配的情况
     */
    private fun findBestMatchingCallback(actualSessionId: String?, jsonLine: String): ((String) -> Unit)? {
        if (actualSessionId == null) return null
        
        // 尝试发送给默认会话
        val defaultCallback = sessionCallbacks["default"]
        if (defaultCallback != null) {
            println("[GlobalCliWrapper] 未找到会话 $actualSessionId 的回调，转发给默认会话")
            return defaultCallback
        }
        
        // 如果没有默认会话但有其他会话，尝试发送给第一个
        if (sessionCallbacks.isNotEmpty()) {
            val firstCallback = sessionCallbacks.values.first()
            val firstSessionId = sessionCallbacks.keys.first()
            println("[GlobalCliWrapper] 未找到匹配会话，转发给第一个活跃会话: $firstSessionId")
            return firstCallback
        }
        
        return null
    }
    
    /**
     * 从输出中提取会话ID
     * 这里需要根据Claude CLI的输出格式来解析
     */
    private fun extractSessionIdFromOutput(jsonLine: String): String? {
        return try {
            if (jsonLine.trim().startsWith("{")) {
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                val jsonObject = json.parseToJsonElement(jsonLine).jsonObject
                
                // 尝试从各种可能的字段中提取会话ID
                jsonObject["sessionId"]?.jsonPrimitive?.content
                    ?: jsonObject["session_id"]?.jsonPrimitive?.content
                    ?: jsonObject["message"]?.jsonObject?.get("sessionId")?.jsonPrimitive?.content
                    ?: jsonObject["message"]?.jsonObject?.get("session_id")?.jsonPrimitive?.content
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取当前活跃的会话数量
     */
    val activeSessionCount: Int
        get() = sessionCallbacks.size
    
    /**
     * 获取所有活跃的会话ID列表
     */
    val activeSessionIds: Set<String>
        get() = sessionCallbacks.keys.toSet()
    
    /**
     * 清理所有会话回调（通常在应用关闭时调用）
     */
    fun clearAllCallbacks() {
        sessionCallbacks.clear()
        println("[GlobalCliWrapper] 已清理所有会话回调")
    }
}