package com.claudecodeplus.core.preprocessor

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.sdk.ClaudeCodeSdkClient

/**
 * 消息预处理器链
 *
 * 实现责任链模式，按顺序执行多个预处理器：
 * 1. 每个预处理器可以选择：
 *    - Continue: 修改消息并传递给下一个预处理器
 *    - Intercepted: 拦截消息，终止处理链
 * 2. 所有预处理器通过后，消息将发送给 Claude
 *
 * 示例流程:
 * ```
 * 用户输入 "/model opus"
 *   ↓
 * SlashCommandInterceptor → Intercepted (执行 setModel)
 *   ↓
 * 返回 Intercepted，不再调用后续预处理器
 * ```
 *
 * ```
 * 用户输入 "帮我优化这段代码"
 *   ↓
 * SlashCommandInterceptor → Continue (不是命令)
 *   ↓
 * ContextReferenceResolver → Continue (没有 @file 引用)
 *   ↓
 * 返回 Continue，发送给 Claude
 * ```
 */
class MessagePreprocessorChain(
    private val preprocessors: List<MessagePreprocessor>
) {
    init {
        logI("创建消息预处理器链，包含 ${preprocessors.size} 个预处理器")
        preprocessors.forEachIndexed { index, preprocessor ->
            logD("  [$index] ${preprocessor::class.simpleName}")
        }
    }

    /**
     * 执行预处理器链
     *
     * @param message 原始用户消息
     * @param client SDK 客户端
     * @param sessionId 会话 ID
     * @return 最终处理结果
     */
    suspend fun process(
        message: String,
        client: ClaudeCodeSdkClient,
        sessionId: String
    ): PreprocessResult {
        var currentMessage = message

        logD("开始预处理消息: sessionId=$sessionId, message=${message.take(50)}...")

        for ((index, preprocessor) in preprocessors.withIndex()) {
            val preprocessorName = preprocessor::class.simpleName ?: "Unknown"
            logD("执行预处理器 [$index]: $preprocessorName")

            val result = preprocessor.preprocess(currentMessage, client, sessionId)

            when (result) {
                is PreprocessResult.Continue -> {
                    currentMessage = result.message
                    logD("  → Continue: message=${currentMessage.take(50)}...")
                    // 继续下一个预处理器
                }
                is PreprocessResult.Intercepted -> {
                    logI("  → Intercepted by $preprocessorName: feedback=${result.feedback?.take(50)}")
                    // 被拦截，立即返回，不再执行后续预处理器
                    return result
                }
            }
        }

        // 所有预处理器都通过，继续发送
        logI("预处理完成，消息将发送给 Claude: ${currentMessage.take(50)}...")
        return PreprocessResult.Continue(currentMessage)
    }

    companion object {
        /**
         * 创建默认预处理器链
         *
         * 默认包含：
         * 1. SlashCommandInterceptor - 处理 /model 等命令
         *
         * 未来可扩展：
         * 2. ContextReferenceResolver - 解析 @file:path 引用
         * 3. MacroExpander - 展开用户自定义宏
         * 4. TemplateProcessor - 处理消息模板
         */
        fun createDefault(): MessagePreprocessorChain {
            return MessagePreprocessorChain(
                listOf(
                    SlashCommandInterceptor(),
                    // 未来可添加更多预处理器：
                    // ContextReferenceResolver(),
                    // MacroExpander(),
                    // TemplateProcessor(),
                )
            )
        }

        /**
         * 创建空预处理器链（用于测试或禁用预处理）
         */
        fun empty(): MessagePreprocessorChain {
            return MessagePreprocessorChain(emptyList())
        }

        /**
         * 创建自定义预处理器链
         *
         * @param builder 构建器函数，用于配置预处理器
         */
        inline fun custom(builder: MutableList<MessagePreprocessor>.() -> Unit): MessagePreprocessorChain {
            val preprocessors = mutableListOf<MessagePreprocessor>()
            preprocessors.builder()
            return MessagePreprocessorChain(preprocessors)
        }
    }
}