/*
 * SimpleClaudeMessage.kt
 *
 * 简化版的Claude CLI消息类型定义
 * 不使用复杂的自定义序列化器，而是基于字段判断的简单方式
 */

package com.claudecodeplus.ui.models

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * 简化的Claude消息基类
 * 包含所有可能的字段，通过字段存在性来判断消息类型
 */
@Serializable
data class SimpleClaudeMessage(
    // 通用字段
    val type: String? = null,
    val subtype: String? = null,
    val uuid: String? = null,
    val timestamp: String? = null,
    val sessionId: String? = null,
    val parentUuid: String? = null,
    val cwd: String? = null,
    val version: String? = null,
    val gitBranch: String? = null,
    val userType: String? = null,
    val isSidechain: Boolean? = null,
    val requestId: String? = null,

    // 消息内容相关
    val message: ClaudeMessageContent? = null,
    val toolUseResult: JsonObject? = null,

    // 系统初始化相关
    val apiKeySource: String? = null,
    val tools: List<String>? = null,
    val mcp_servers: List<JsonObject>? = null,
    val model: String? = null,
    val permissionMode: String? = null,

    // 结果消息相关
    val duration_ms: Long? = null,
    val duration_api_ms: Long? = null,
    val num_turns: Int? = null,
    val is_error: Boolean? = null,
    val result: String? = null,
    val total_cost_usd: Double? = null,
    val usage: ClaudeTokenUsageData? = null,

    // 摘要消息相关
    val summary: String? = null,
    val leafUuid: String? = null
) {
    /**
     * 根据字段判断消息类型
     */
    fun getMessageType(): MessageType {
        return when {
            // 用户消息: type="user" 或者 message.role="user"
            type == "user" || message?.role == "user" -> MessageType.USER

            // 助手消息: type="assistant" 或者 message.role="assistant"
            type == "assistant" || message?.role == "assistant" -> MessageType.ASSISTANT

            // 系统初始化: type="system" + subtype="init"
            type == "system" && subtype == "init" -> MessageType.SYSTEM_INIT

            // 结果消息: type="result"
            type == "result" -> MessageType.RESULT

            // 摘要消息: type="summary" 或者有summary字段
            type == "summary" || summary != null -> MessageType.SUMMARY

            // 未知类型
            else -> MessageType.UNKNOWN
        }
    }

    /**
     * 判断是否为有效的用户消息
     */
    fun isUserMessage(): Boolean {
        return getMessageType() == MessageType.USER && !message?.content.isNullOrBlank()
    }

    /**
     * 判断是否为有效的助手消息
     */
    fun isAssistantMessage(): Boolean {
        return getMessageType() == MessageType.ASSISTANT
    }

    /**
     * 判断是否为系统初始化消息
     */
    fun isSystemInitMessage(): Boolean {
        return getMessageType() == MessageType.SYSTEM_INIT
    }

    /**
     * 判断是否为结果汇总消息
     */
    fun isResultMessage(): Boolean {
        return getMessageType() == MessageType.RESULT
    }

    /**
     * 获取显示用的内容文本
     */
    fun getDisplayContent(): String {
        return when (getMessageType()) {
            MessageType.USER -> message?.content ?: ""
            MessageType.ASSISTANT -> extractAssistantContent()
            MessageType.SYSTEM_INIT -> "系统初始化: ${model ?: "未知模型"}"
            MessageType.RESULT -> "会话结束: $result"
            MessageType.SUMMARY -> summary ?: ""
            MessageType.UNKNOWN -> "未知消息类型: type=$type, subtype=$subtype"
        }
    }

    /**
     * 提取助手消息的文本内容
     */
    private fun extractAssistantContent(): String {
        val assistantMessage = message?.content
        if (!assistantMessage.isNullOrBlank()) {
            return assistantMessage
        }

        // 如果message.content是空的，可能content在message对象的其他字段中
        // 这里可以添加更复杂的提取逻辑
        return "[助手响应]"
    }
}

/**
 * 消息类型枚举
 */
enum class MessageType {
    USER,           // 用户消息
    ASSISTANT,      // 助手消息
    SYSTEM_INIT,    // 系统初始化
    RESULT,         // 结果汇总
    SUMMARY,        // 会话摘要
    UNKNOWN         // 未知类型
}

