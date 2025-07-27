package com.claudecodeplus.ui.utils

import java.util.UUID

/**
 * ID生成工具类
 * 提供统一的ID生成功能
 */
object IdGenerator {
    
    /**
     * 生成消息ID
     * 格式：msg_时间戳_随机数
     */
    fun generateMessageId(): String {
        return buildString {
            append(DefaultConfigs.MessageId.PREFIX)
            append("_")
            if (DefaultConfigs.MessageId.TIMESTAMP_ENABLED) {
                append(System.currentTimeMillis())
                append("_")
            }
            append((0..DefaultConfigs.MessageId.RANDOM_SUFFIX_MAX).random())
        }
    }
    
    /**
     * 生成会话ID
     * 使用UUID
     */
    fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * 生成工具调用ID
     * 格式：tool_时间戳_随机数
     */
    fun generateToolCallId(): String {
        return "tool_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * 生成上下文引用ID
     * 格式：ctx_时间戳_随机数
     */
    fun generateContextId(): String {
        return "ctx_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * 生成标签页ID
     * 格式：tab_时间戳
     */
    fun generateTabId(): String {
        return "tab_${System.currentTimeMillis()}"
    }
    
    /**
     * 生成项目ID
     * 格式：proj_UUID
     */
    fun generateProjectId(): String {
        return "proj_${UUID.randomUUID().toString().take(8)}"
    }
    
    /**
     * 生成唯一的短ID（8位）
     * 用于需要简短ID的场景
     */
    fun generateShortId(): String {
        return UUID.randomUUID().toString().take(8)
    }
    
    /**
     * 生成带前缀的ID
     * @param prefix ID前缀
     * @param includeTimestamp 是否包含时间戳
     * @param includeSuffix 是否包含随机后缀
     */
    fun generateCustomId(
        prefix: String,
        includeTimestamp: Boolean = true,
        includeSuffix: Boolean = true
    ): String {
        return buildString {
            append(prefix)
            if (includeTimestamp) {
                append("_")
                append(System.currentTimeMillis())
            }
            if (includeSuffix) {
                append("_")
                append((0..999).random())
            }
        }
    }
}