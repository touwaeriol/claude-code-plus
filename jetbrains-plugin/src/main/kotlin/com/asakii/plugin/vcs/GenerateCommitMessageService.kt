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
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.plugin.mcp.GitMcpServerImpl
import com.asakii.settings.AgentSettingsService
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
     * 生成 commit message
     */
    fun generateCommitMessage(indicator: ProgressIndicator) {
        try {
            indicator.text = "Starting Claude..."

            runBlocking {
                callClaudeWithMcp(indicator)
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate commit message" }
            showNotification("Error: ${e.message}", NotificationType.ERROR)
        }
    }

    private suspend fun callClaudeWithMcp(indicator: ProgressIndicator) {
        val settings = AgentSettingsService.getInstance()
        val projectPath = project.basePath

        try {
            val client = UnifiedAgentClientFactory.create(AiAgentProvider.CLAUDE)

            // 创建 Git MCP 服务器实例
            val gitMcpServer = GitMcpServerImpl(project)

            val claudeOptions = ClaudeAgentOptions(
                nodePath = settings.nodePath.takeIf { it.isNotBlank() },
                cwd = projectPath?.let { Paths.get(it) },
                systemPrompt = SYSTEM_PROMPT,
                dangerouslySkipPermissions = true,
                allowDangerouslySkipPermissions = true,
                includePartialMessages = true,
                // 限制可用工具
                allowedTools = listOf(
                    "mcp__jetbrains_git__GetVcsChanges",
                    "mcp__jetbrains_git__SetCommitMessage",
                    "Read"
                ),
                // 注册 Git MCP 服务器
                mcpServers = mapOf("jetbrains_git" to gitMcpServer),
                extraArgs = mapOf("output-format" to "stream-json")
            )

            val connectOptions = AiAgentConnectOptions(
                provider = AiAgentProvider.CLAUDE,
                model = settings.defaultModelId.takeIf { it.isNotBlank() },
                claude = ClaudeOverrides(options = claudeOptions)
            )

            indicator.text = "Connecting to Claude..."
            withTimeout(30_000) {
                client.connect(connectOptions)
            }

            indicator.text = "Analyzing changes..."

            var success = false
            var toolCallCount = 0

            try {
                withTimeout(120_000) {  // 2 minutes timeout for tool calls
                    client.sendMessage(AgentMessageInput(text = USER_PROMPT))
                    client.streamEvents().collect { event ->
                        when (event) {
                            is UiToolStart -> {
                                toolCallCount++
                                indicator.text = "Calling ${event.toolName}..."
                                logger.info { "Tool call started: ${event.toolName}" }
                            }
                            is UiToolComplete -> {
                                logger.info { "Tool call completed: ${event.toolId}" }
                                // 检查是否是 SetCommitMessage 工具调用成功
                                if (event.result.type == "tool_result") {
                                    val toolName = event.toolId
                                    if (toolName.contains("SetCommitMessage", ignoreCase = true)) {
                                        success = true
                                        indicator.text = "Commit message set!"
                                    }
                                }
                            }
                            is UiResultMessage -> {
                                logger.info { "Result: subtype=${event.subtype}, isError=${event.isError}" }
                                if (!event.isError && toolCallCount > 0) {
                                    success = true
                                }
                            }
                            is UiError -> {
                                logger.error { "Claude error: ${event.message}" }
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
                } catch (e: Exception) {
                    logger.debug { "Disconnect error: ${e.message}" }
                }
            }

            if (success) {
                showNotification("Commit message generated successfully", NotificationType.INFORMATION)
            } else if (toolCallCount == 0) {
                showNotification("No tools were called. Please try again.", NotificationType.WARNING)
            }

        } catch (e: Exception) {
            logger.error(e) { "Claude call failed" }
            showNotification("Error: ${e.message}", NotificationType.ERROR)
        }
    }

    private fun showNotification(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Claude Code Plus Notifications")
            .createNotification(content, type)
            .notify(project)
    }

    companion object {
        private val SYSTEM_PROMPT = """
You are a commit message generator integrated with JetBrains IDE.

Available tools:
- mcp__jetbrains_git__GetVcsChanges: Get uncommitted file changes with diff content
- mcp__jetbrains_git__SetCommitMessage: Set the commit message in IDE's commit panel
- Read: Read file content to understand code context and analyze changes in detail

Your task:
1. Call GetVcsChanges(selectedOnly=true, includeDiff=true) to get code changes
2. If the diff is unclear or you need more context, use Read tool to examine the full file content
3. Analyze the changes, understand the purpose and impact
4. Generate a commit message following conventional commits format
5. Call SetCommitMessage to fill the message into IDE's commit panel

Commit message rules:
- Format: type(scope): description
- Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore
- First line: max 50 characters, imperative mood (add, fix, update)
- Body (optional): explain WHAT changed and WHY

IMPORTANT: You MUST call SetCommitMessage tool to set the result. Do NOT output text directly.
""".trimIndent()

        private val USER_PROMPT = """
Generate a commit message for the selected code changes.

Steps:
1. Call GetVcsChanges(selectedOnly=true, includeDiff=true) to get changes
2. If needed, use Read tool to understand the code context better
3. Analyze and generate an appropriate commit message
4. Call SetCommitMessage to fill the commit panel

Use tools only - do not output the commit message as text.
""".trimIndent()
    }
}
