package com.claudecodeplus.core.preprocessor

import com.claudecodeplus.sdk.ClaudeCodeSdkClient

/**
 * 消息预处理结果
 *
 * 表示消息预处理后的状态，决定消息是否需要继续发送给 Claude
 */
sealed class PreprocessResult {
    /**
     * 继续处理：将消息发送给 Claude
     * @param message 可能被修改过的消息内容
     */
    data class Continue(val message: String) : PreprocessResult()

    /**
     * 拦截处理：命令已完成，无需发送给 Claude
     * @param handled 是否已处理完成（默认 true）
     * @param feedback 可选的用户反馈消息，将作为 SYSTEM 消息显示给用户
     */
    data class Intercepted(
        val handled: Boolean = true,
        val feedback: String? = null
    ) : PreprocessResult()
}

/**
 * 消息预处理器接口
 *
 * 用于在消息发送给 Claude 之前进行预处理，支持：
 * - 斜杠命令拦截 (/model, /cost 等)
 * - 上下文引用解析 (@file:path)
 * - 宏展开
 * - 其他自定义转换
 *
 * 实现责任链模式，多个预处理器可以顺序执行
 */
interface MessagePreprocessor {
    /**
     * 预处理消息
     *
     * @param message 原始用户输入消息
     * @param client SDK 客户端实例，用于执行控制命令（如 setModel）
     * @param sessionId 当前会话 ID
     * @return 预处理结果，指示是否继续发送消息
     */
    suspend fun preprocess(
        message: String,
        client: ClaudeCodeSdkClient,
        sessionId: String
    ): PreprocessResult
}