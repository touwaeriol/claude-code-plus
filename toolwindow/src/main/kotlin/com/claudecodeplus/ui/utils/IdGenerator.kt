package com.claudecodeplus.ui.utils

import java.util.UUID

/**
 * ID 生成工具类 - 全局唯一标识符管理
 * 
 * 为系统中各种实体提供唯一标识符生成功能。
 * 确保在分布式环境和并发场景下 ID 的唯一性。
 * 
 * ID 生成策略：
 * - 基于时间戳：提供时间顺序性，方便排序和查询
 * - 随机后缀：避免同一毫秒内的 ID 冲突
 * - 类型前缀：方便识别 ID 所属的实体类型
 * - UUID：用于需要全局唯一性的场景
 * 
 * 使用单例模式，确保全局统一的 ID 生成规则。
 */
object IdGenerator {
    
    /**
     * 生成消息 ID
     * 
     * 用于标识聊天中的每一条消息（用户消息和助手消息）。
     * 
     * 格式：{prefix}_{timestamp}_{random}
     * 示例：msg_1704067200000_456
     * 
     * 特性：
     * - 前缀可配置：通过 DefaultConfigs.MessageId.PREFIX 设置
     * - 时间戳可选：通过 DefaultConfigs.MessageId.TIMESTAMP_ENABLED 控制
     * - 随机后缀范围：0 到 DefaultConfigs.MessageId.RANDOM_SUFFIX_MAX
     * 
     * @return 消息的唯一标识符
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
     * 生成会话 ID
     * 
     * 用于标识一个完整的对话会话。
     * 使用 UUID 确保全局唯一性。
     * 
     * 格式：UUID v4
     * 示例：550e8400-e29b-41d4-a716-446655440000
     * 
     * 使用场景：
     * - 新建会话时生成
     * - 保存会话历史时作为文件名
     * - 恢复会话时作为索引
     * 
     * @return 会话的 UUID 标识符
     */
    fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * 生成工具调用 ID
     * 
     * 用于标识 AI 执行的每一次工具调用。
     * 这个 ID 用于关联工具调用请求和结果。
     * 
     * 格式：tool_{timestamp}_{random}
     * 示例：tool_1704067200000_789
     * 
     * 使用场景：
     * - 标识文件读写操作
     * - 标识命令执行
     * - 标识网络请求
     * - 关联工具结果和错误
     * 
     * @return 工具调用的唯一标识符
     */
    fun generateToolCallId(): String {
        return "tool_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * 生成上下文引用 ID
     * 
     * 用于标识添加到消息中的上下文引用。
     * 
     * 格式：ctx_{timestamp}_{random}
     * 示例：ctx_1704067200000_123
     * 
     * 上下文类型包括：
     * - 文件引用
     * - 网页引用
     * - 图片引用
     * - 终端输出
     * - Git 信息等
     * 
     * @return 上下文引用的唯一标识符
     */
    fun generateContextId(): String {
        return "ctx_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * 生成标签页 ID
     * 
     * 用于标识多标签对话中的每个标签页。
     * 
     * 格式：tab_{timestamp}
     * 示例：tab_1704067200000
     * 
     * 特点：
     * - 仅使用时间戳，无随机后缀
     * - 时间戳精度到毫秒，几乎不会重复
     * - 方便按创建时间排序
     * 
     * @return 标签页的唯一标识符
     */
    fun generateTabId(): String {
        return "tab_${System.currentTimeMillis()}"
    }
    
    /**
     * 生成项目 ID
     * 
     * 用于标识管理的项目。
     * 
     * 格式：proj_{uuid_prefix}
     * 示例：proj_e29b41d4
     * 
     * 特点：
     * - 使用 UUID 的前 8 位，保持简洁
     * - 足够保证在项目范围内的唯一性
     * - 前缀 "proj_" 方便识别
     * 
     * @return 项目的唯一标识符
     */
    fun generateProjectId(): String {
        return "proj_${UUID.randomUUID().toString().take(8)}"
    }
    
    /**
     * 生成唯一的短 ID（8位）
     * 
     * 用于需要简短 ID 的场景，如 URL 参数、显示编号等。
     * 
     * 格式：UUID 前 8 位
     * 示例：e29b41d4
     * 
     * 适用场景：
     * - 用户可见的编号
     * - URL 参数
     * - 短期临时标识
     * - 需要手动输入的场景
     * 
     * @return 8 位的短 ID
     */
    fun generateShortId(): String {
        return UUID.randomUUID().toString().take(8)
    }
    
    /**
     * 生成带前缀的自定义 ID
     * 
     * 提供灵活的 ID 生成机制，供特殊场景使用。
     * 
     * 示例：
     * - generateCustomId("event") -> "event_1704067200000_456"
     * - generateCustomId("temp", false, true) -> "temp_789"
     * - generateCustomId("seq", true, false) -> "seq_1704067200000"
     * 
     * 使用场景：
     * - 临时实体标识
     * - 特殊业务需求
     * - 扩展新的实体类型
     * 
     * @param prefix ID 前缀，用于标识类型
     * @param includeTimestamp 是否包含时间戳（默认 true）
     * @param includeSuffix 是否包含随机后缀（默认 true）
     * @return 根据参数生成的自定义 ID
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