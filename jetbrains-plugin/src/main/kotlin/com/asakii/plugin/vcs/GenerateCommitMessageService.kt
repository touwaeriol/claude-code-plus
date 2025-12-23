package com.asakii.plugin.vcs

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.client.AgentMessageInput
import com.asakii.ai.agent.sdk.client.UnifiedAgentClientFactory
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.ai.agent.sdk.model.UiResultMessage
import com.asakii.ai.agent.sdk.model.UiError
import com.asakii.ai.agent.sdk.model.UiToolStart
import com.asakii.ai.agent.sdk.model.UiToolComplete
import com.asakii.ai.agent.sdk.model.UiAssistantMessage
import com.asakii.ai.agent.sdk.model.TextContent
import com.asakii.ai.agent.sdk.model.ThinkingContent
import com.asakii.ai.agent.sdk.model.ToolUseContent
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.plugin.mcp.GitMcpServerImpl
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.GitGenerateDefaults
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

/**
 * Generate Commit Message Service
 *
 * 使用 Claude AI 通过 MCP 工具分析代码变更并生成 commit message
 */
@Service(Service.Level.PROJECT)
class GenerateCommitMessageService(private val project: Project) {

    /**
     * 生成 commit message（简单模式，使用 ProgressIndicator）
     */
    fun generateCommitMessage(indicator: ProgressIndicator) {
        try {
            indicator.text = "Starting Claude..."

            runBlocking {
                callClaudeWithMcp(indicator, null)
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate commit message" }
            showNotification("Error: ${e.message}", NotificationType.ERROR)
        }
    }

    /**
     * 生成 commit message（详细模式，使用进度对话框）
     */
    fun generateCommitMessageWithDialog(dialog: GitGenerateProgressDialog) {
        try {
            dialog.updateStatus("Starting Claude...")
            dialog.appendLog("🚀 Starting commit message generation...")

            runBlocking {
                callClaudeWithMcp(null, dialog)
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate commit message" }
            dialog.appendError(e.message ?: "Unknown error")
            dialog.markComplete(false)
        }
    }

    private suspend fun callClaudeWithMcp(indicator: ProgressIndicator?, dialog: GitGenerateProgressDialog?) {
        val settings = AgentSettingsService.getInstance()
        val projectPath = project.basePath

        // 辅助函数：更新状态
        fun updateStatus(text: String) {
            indicator?.text = text
            dialog?.updateStatus(text)
        }

        try {
            val client = UnifiedAgentClientFactory.create(AiAgentProvider.CLAUDE)

            // 创建 Git MCP 服务器实例
            val gitMcpServer = GitMcpServerImpl(project)

            // 获取配置的提示词和工具列表
            val configuredSystemPrompt = settings.gitGenerateSystemPrompt.ifBlank { GitGenerateDefaults.SYSTEM_PROMPT }
            val configuredUserPrompt = settings.gitGenerateUserPrompt.ifBlank { GitGenerateDefaults.USER_PROMPT }
            val configuredTools = settings.getGitGenerateTools().takeIf { it.isNotEmpty() } ?: GitGenerateDefaults.TOOLS

            dialog?.appendLog("📋 Configured tools: ${configuredTools.size}")

            val claudeOptions = ClaudeAgentOptions(
                nodePath = settings.nodePath.takeIf { it.isNotBlank() },
                cwd = projectPath?.let { Paths.get(it) },
                systemPrompt = configuredSystemPrompt,
                dangerouslySkipPermissions = true,
                allowDangerouslySkipPermissions = true,
                includePartialMessages = true,
                // 使用配置的工具列表
                allowedTools = configuredTools,
                // 注册 Git MCP 服务器
                mcpServers = mapOf("jetbrains_git" to gitMcpServer),
                extraArgs = mapOf("output-format" to "stream-json"),
                // 会话持久化控制：saveSession=false 时不保存会话
                noSessionPersistence = !settings.gitGenerateSaveSession
            )

            val connectOptions = AiAgentConnectOptions(
                provider = AiAgentProvider.CLAUDE,
                model = settings.defaultModelId.takeIf { it.isNotBlank() },
                claude = ClaudeOverrides(options = claudeOptions)
            )

            updateStatus("Connecting to Claude...")
            dialog?.appendLog("🔌 Connecting to Claude...")

            withTimeout(30_000) {
                client.connect(connectOptions)
            }

            updateStatus("Analyzing changes...")
            dialog?.appendLog("✅ Connected successfully")
            dialog?.appendLog("")

            var success = false
            var toolCallCount = 0
            var shouldAbort = false
            val steps = mutableListOf<String>()
            val currentToolParams = mutableMapOf<String, String>()  // 记录工具参数

            // 更新详情显示（仅用于 indicator 模式）
            fun updateDetails(step: String) {
                steps.add(step)
                indicator?.text2 = steps.takeLast(2).joinToString(" → ")
            }

            try {
                withTimeout(120_000) {  // 2 minutes timeout for tool calls
                    client.sendMessage(AgentMessageInput(text = configuredUserPrompt))
                    client.streamEvents().collect { event ->
                        // 检查对话框是否已取消
                        if (dialog?.isCancelled() == true) {
                            shouldAbort = true
                            logger.info { "Generation cancelled by user" }
                        }

                        // 如果已经完成，跳过后续事件处理
                        if (shouldAbort) return@collect

                        when (event) {
                            is UiAssistantMessage -> {
                                // 捕获 AI 的思考过程和工具调用参数
                                for (content in event.content) {
                                    when (content) {
                                        is ThinkingContent -> {
                                            val thinking = content.thinking.take(50).replace("\n", " ")
                                            if (thinking.isNotBlank()) {
                                                updateDetails("💭 $thinking...")
                                                dialog?.appendThinking(content.thinking)
                                                logger.debug { "Thinking: ${content.thinking.take(100)}" }
                                            }
                                        }
                                        is TextContent -> {
                                            val text = content.text.take(50).replace("\n", " ")
                                            if (text.isNotBlank()) {
                                                updateDetails("📝 $text...")
                                                dialog?.appendLog("📝 ${content.text}")
                                                logger.debug { "Text: ${content.text.take(100)}" }
                                            }
                                        }
                                        is ToolUseContent -> {
                                            // 记录工具调用参数，用于后续显示
                                            currentToolParams[content.id] = content.input.toString()
                                        }
                                        else -> {}
                                    }
                                }
                            }
                            is UiToolStart -> {
                                toolCallCount++
                                val shortName = event.toolName.replace("mcp__jetbrains_git__", "")
                                updateStatus("Calling $shortName...")
                                updateDetails("🔧 $shortName")

                                // 在对话框中显示工具调用
                                val params = currentToolParams[event.toolId]
                                dialog?.appendToolStart(event.toolName, params)

                                logger.info { "Tool call started: ${event.toolName}" }
                            }
                            is UiToolComplete -> {
                                logger.info { "Tool call completed: ${event.toolId}" }

                                val isSuccess = event.result.type == "tool_result"
                                val toolName = event.toolId

                                // 提取结果内容（用于对话框显示）
                                val resultContent = try {
                                    event.result.content?.toString()?.take(500)
                                } catch (e: Exception) { null }

                                dialog?.appendToolComplete(toolName, isSuccess, resultContent)

                                if (isSuccess) {
                                    if (toolName.contains("SetCommitMessage", ignoreCase = true)) {
                                        success = true
                                        updateStatus("Commit message set!")
                                        updateDetails("✅ Message set")
                                        dialog?.appendLog("")
                                        dialog?.appendLog("✅ Commit message has been set in the commit panel")
                                        logger.info { "SetCommitMessage completed successfully" }
                                    } else if (toolName.contains("GetVcsChanges", ignoreCase = true)) {
                                        updateDetails("✅ Changes loaded")
                                    }
                                }
                            }
                            is UiResultMessage -> {
                                logger.info { "Result: subtype=${event.subtype}, isError=${event.isError}, numTurns=${event.numTurns}" }
                                if (!event.isError && toolCallCount > 0) {
                                    success = true
                                }
                                shouldAbort = true
                                updateStatus(if (success) "Done!" else "Completed")
                                indicator?.text2 = if (success) "Commit message generated" else "Check commit panel"
                                dialog?.markComplete(success)
                                logger.info { "Query completed, ending session" }
                            }
                            is UiError -> {
                                logger.error { "Claude error: ${event.message}" }
                                updateDetails("❌ Error")
                                dialog?.appendError(event.message)
                                showNotification("Error: ${event.message}", NotificationType.ERROR)
                            }
                            else -> {
                                // 忽略其他事件
                            }
                        }
                    }
                }
            } finally {
                try {
                    client.disconnect()
                    dialog?.appendLog("")
                    dialog?.appendLog("🔌 Disconnected from Claude")
                } catch (e: Exception) {
                    logger.debug { "Disconnect error: ${e.message}" }
                }
            }

            if (success) {
                showNotification("Commit message generated successfully", NotificationType.INFORMATION)
            } else if (toolCallCount == 0) {
                showNotification("No tools were called. Please try again.", NotificationType.WARNING)
                dialog?.appendLog("⚠️ No tools were called. Please try again.")
            }

        } catch (e: Exception) {
            logger.error(e) { "Claude call failed" }
            showNotification("Error: ${e.message}", NotificationType.ERROR)
            dialog?.appendError(e.message ?: "Unknown error")
            dialog?.markComplete(false)
        }
    }

    private fun showNotification(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Claude Code Plus Notifications")
            .createNotification(content, type)
            .notify(project)
    }

}
