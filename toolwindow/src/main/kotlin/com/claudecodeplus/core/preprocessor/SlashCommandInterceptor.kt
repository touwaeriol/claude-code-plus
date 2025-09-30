package com.claudecodeplus.core.preprocessor

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.core.logging.logW
import com.claudecodeplus.sdk.ClaudeCodeSdkClient

/**
 * 斜杠命令拦截器
 *
 * 负责识别和处理以 / 开头的命令，当前支持：
 * - /model <模型名>: 切换 AI 模型
 *
 * 未来可扩展支持：
 * - /cost: 查询会话成本
 * - /clear: 清空会话历史
 * - /context: 管理上下文引用
 * - 等等...
 */
class SlashCommandInterceptor : MessagePreprocessor {
    // 注：不再在这里转换别名，直接传递给 Claude CLI
    // Claude CLI 会自己处理别名（opus, sonnet, haiku 等）

    override suspend fun preprocess(
        message: String,
        client: ClaudeCodeSdkClient,
        sessionId: String
    ): PreprocessResult {
        val trimmed = message.trim()

        // 只处理以 / 开头的命令
        if (!trimmed.startsWith("/")) {
            return PreprocessResult.Continue(message)
        }

        logD("检测到斜杠命令: $trimmed")

        // 解析命令和参数
        val parts = trimmed.split(Regex("\\s+"))
        val command = parts[0].substring(1).lowercase() // 去掉 / 并转小写
        val args = parts.drop(1)

        logI("解析命令: command=$command, args=$args")

        return when (command) {
            "model" -> handleModelCommand(args, client, sessionId)
            // 未来可扩展其他命令
            // "cost" -> handleCostCommand(args, client, sessionId)
            // "clear" -> handleClearCommand(args, client, sessionId)
            // "context" -> handleContextCommand(args, client, sessionId)
            else -> {
                // 不认识的命令，交给 Claude 处理（可能是 Claude CLI 自带的命令）
                logD("未知命令 /$command，交给 Claude 处理")
                PreprocessResult.Continue(message)
            }
        }
    }

    /**
     * 处理 /model 命令
     *
     * 用法: /model <模型名>
     * 示例:
     * - /model opus
     * - /model sonnet-4.5
     * - /model claude-haiku-4-20250514
     */
    private suspend fun handleModelCommand(
        args: List<String>,
        client: ClaudeCodeSdkClient,
        sessionId: String
    ): PreprocessResult {
        if (args.isEmpty()) {
            return PreprocessResult.Intercepted(
                feedback = buildString {
                    appendLine("❌ 用法: /model <模型名>")
                    appendLine()
                    appendLine("支持的模型别名:")
                    appendLine("  • opus       - Claude Opus 4 (最强推理能力)")
                    appendLine("  • sonnet     - Claude Sonnet 4")
                    appendLine("  • sonnet-4.5 - Claude Sonnet 4.5 (平衡性能)")
                    appendLine("  • haiku      - Claude Haiku 4 (最快速度)")
                    appendLine()
                    appendLine("也可以使用完整模型 ID，例如:")
                    appendLine("  /model claude-opus-4-20250514")
                }
            )
        }

        // 直接使用用户输入，不做别名转换
        // Claude CLI 会自己处理别名（opus, sonnet, haiku 等）
        val modelInput = args[0]  // 保持用户原始输入（包括大小写）

        logI("尝试切换模型: $modelInput")

        return try {
            // 调用 SDK 的 setModel 方法，直接传递用户输入
            client.setModel(modelInput)

            logI("模型切换成功: sessionId=$sessionId, model=$modelInput")

            PreprocessResult.Intercepted(
                feedback = "✅ 已切换到模型: **$modelInput**"
            )
        } catch (e: Exception) {
            logW("模型切换失败: sessionId=$sessionId, model=$modelInput, error=${e.message}")

            PreprocessResult.Intercepted(
                feedback = "❌ 切换模型失败: ${e.message}"
            )
        }
    }

    // 未来可以在这里添加更多命令处理方法
    // private suspend fun handleCostCommand(...) { ... }
    // private suspend fun handleClearCommand(...) { ... }
}