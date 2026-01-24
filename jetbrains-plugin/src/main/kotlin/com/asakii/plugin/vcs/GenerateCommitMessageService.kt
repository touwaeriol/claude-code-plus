package com.asakii.plugin.vcs

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.client.AgentMessageInput
import com.asakii.ai.agent.sdk.client.UnifiedAgentClientFactory
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.ai.agent.sdk.connect.CodexOverrides
import com.asakii.ai.agent.sdk.model.UiResultMessage
import com.asakii.ai.agent.sdk.model.UiError
import com.asakii.ai.agent.sdk.model.UiToolStart
import com.asakii.ai.agent.sdk.model.UiToolComplete
import com.asakii.ai.agent.sdk.model.UiAssistantMessage
import com.asakii.ai.agent.sdk.model.TextContent
import com.asakii.ai.agent.sdk.model.ThinkingContent
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.claude.agent.sdk.types.McpServerSpec
import com.asakii.codex.agent.sdk.ApprovalMode
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.ModelReasoningEffort
import com.asakii.codex.agent.sdk.SandboxMode
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.plugin.mcp.GitMcpServerImpl
import com.asakii.server.HttpServerProjectService
import com.asakii.server.mcp.McpHttpGateway
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.GitGenerateDefaults
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import com.asakii.logging.*
import java.nio.file.Paths
import java.util.UUID

private val logger = getLogger("GenerateCommitMessageService")

/**
 * Generate Commit Message Service
 *
 * 使用 Claude/Codex 通过 MCP 工具分析代码变更并生成 commit message
 */
@Service(Service.Level.PROJECT)
class GenerateCommitMessageService(private val project: Project) {

    /**
     * 生成 commit message（后台任务模式）
     */
    fun generateCommitMessage(indicator: ProgressIndicator) {
        try {
            indicator.text = "Starting Git Generate..."

            runBlocking {
                callGitGenerate(indicator)
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to generate commit message" }
            showNotification("Error: ${e.message}", NotificationType.ERROR)
        }
    }

    private suspend fun callGitGenerate(indicator: ProgressIndicator) {
        val settings = AgentSettingsService.getInstance()
        val projectPath = project.basePath
        val provider = settings.getGitGenerateBackendProvider()

        var codexConnectId: String? = null

        try {
            val client = UnifiedAgentClientFactory.create(provider)

            // 创建 Git MCP 服务器实例
            val gitMcpServer = GitMcpServerImpl(project)

            // 获取配置的提示词
            val configuredSystemPrompt = settings.effectiveGitGenerateSystemPrompt
            val configuredUserPrompt = settings.effectiveGitGenerateUserPrompt

            // Git Generate 固定使用默认工具集
            val configuredTools = GitGenerateDefaults.TOOLS

            // 获取配置的模型（带 fallback）
            val modelId = settings.effectiveGitGenerateModelId

            val connectOptions = when (provider) {
                AiAgentProvider.CODEX -> {
                    val connectId = "git-generate-${UUID.randomUUID()}"
                    codexConnectId = connectId

                    // 从 HttpServerProjectService 获取项目级 MCP HTTP 网关
                    val mcpGateway = project.getService(HttpServerProjectService::class.java)
                        .getMcpHttpGateway()
                        ?: throw IllegalStateException("McpHttpGateway not initialized for project: ${project.name}")
                    
                    val mcpUrl = mcpGateway.registerServer(
                        "jetbrains_git",
                        gitMcpServer
                    )
                    val codexPath = settings.codexPath.takeIf { it.isNotBlank() }
                        ?.let { Paths.get(it) }
                        ?: AgentSettingsService.detectCodexPath().trim().takeIf { it.isNotBlank() }
                            ?.let { Paths.get(it) }
                    val clientOptions = CodexClientOptions(
                        codexPathOverride = codexPath
                    )
                    val reasoningEffort = runCatching {
                        ModelReasoningEffort.valueOf(
                            settings.effectiveGitGenerateCodexReasoningEffort.uppercase()
                        )
                    }.getOrNull()
                    val threadOptions = ThreadOptions(
                        model = modelId,
                        sandboxMode = SandboxMode.READ_ONLY,
                        workingDirectory = projectPath,
                        skipGitRepoCheck = true,
                        modelReasoningEffort = reasoningEffort,
                        approvalPolicy = ApprovalMode.NEVER,
                        developerInstructions = configuredSystemPrompt,
                        mcpServers = mapOf("jetbrains_git" to mcpUrl),
                    )
                    AiAgentConnectOptions(
                        provider = AiAgentProvider.CODEX,
                        model = modelId,
                        codex = CodexOverrides(
                            clientOptions = clientOptions,
                            threadOptions = threadOptions
                        )
                    )
                }
                else -> {
                    val claudeOptions = ClaudeAgentOptions(
                        nodePath = settings.nodePath.takeIf { it.isNotBlank() },
                        cwd = projectPath?.let { Paths.get(it) },
                        systemPrompt = configuredSystemPrompt,
                        dangerouslySkipPermissions = true,
                        allowDangerouslySkipPermissions = true,
                        includePartialMessages = true,
                        allowedTools = configuredTools,
                        mcpServers = mapOf<String, McpServerSpec>("jetbrains_git" to gitMcpServer),
                        extraArgs = mapOf("output-format" to "stream-json"),
                        noSessionPersistence = !settings.gitGenerateSaveSession,
                        maxThinkingTokens = settings.effectiveGitGenerateClaudeThinkingTokens
                    )

                    AiAgentConnectOptions(
                        provider = AiAgentProvider.CLAUDE,
                        model = modelId,
                        claude = ClaudeOverrides(options = claudeOptions)
                    )
                }
            }

            indicator.text = "Connecting to ${provider.name.lowercase()}..."
            logger.info { "Connecting to ${provider.name.lowercase()} with model: $modelId" }

            withTimeout(30_000) {
                client.connect(connectOptions)
            }

            indicator.text = "Analyzing changes..."

            var success = false
            var toolCallCount = 0
            val steps = mutableListOf<String>()
            val toolIdToName = mutableMapOf<String, String>()  // toolId -> toolName 映射

            // 更新详情显示
            fun updateDetails(step: String) {
                steps.add(step)
                indicator.text2 = steps.takeLast(2).joinToString(" → ")
            }

            try {
                withTimeout(120_000) {  // 2 minutes timeout for tool calls
                    // 重要：必须先启动 collector 再发送消息！
                    // 因为 eventFlow 是 SharedFlow(replay=0)，如果先发送消息，
                    // collector 订阅时之前的事件已经丢失了
                    coroutineScope {
                        val collectorReady = kotlinx.coroutines.CompletableDeferred<Unit>()

                        val collector = launch {
                            collectorReady.complete(Unit)  // 标记 collector 已准备好
                            try {
                                client.streamEvents().collect { event ->
                                    // 检查是否已取消
                                    if (indicator.isCanceled) {
                                        logger.info { "Generation cancelled by user" }
                                        cancel()
                                        return@collect
                                    }

                                    when (event) {
                                        is UiAssistantMessage -> {
                                            // 捕获 AI 的思考过程和工具调用参数
                                            for (content in event.content) {
                                                when (content) {
                                                    is ThinkingContent -> {
                                                        val thinking = content.thinking.take(50).replace("\n", " ")
                                                        if (thinking.isNotBlank()) {
                                                            updateDetails("💭 $thinking...")
                                                            logger.debug { "Thinking: ${content.thinking.take(100)}" }
                                                        }
                                                    }
                                                    is TextContent -> {
                                                        val text = content.text.take(50).replace("\n", " ")
                                                        if (text.isNotBlank()) {
                                                            indicator.text = text
                                                            updateDetails("📝 $text...")
                                                            logger.debug { "Text: ${content.text.take(100)}" }
                                                        }
                                                    }
                                                    else -> {} // 忽略其他内容类型（如 ToolUseContent）
                                                }
                                            }
                                        }
                                        is UiToolStart -> {
                                            toolCallCount++
                                            toolIdToName[event.toolId] = event.toolName  // 记录映射
                                            val shortName = event.toolName.replace("mcp__jetbrains_git__", "")
                                            indicator.text = "Calling $shortName..."
                                            updateDetails("🔧 $shortName")
                                            logger.info { "Tool call started: ${event.toolName} (toolId=${event.toolId})" }
                                        }
                                        is UiToolComplete -> {
                                            val toolName = toolIdToName[event.toolId] ?: event.toolId
                                            logger.info { "Tool call completed: $toolName (toolId=${event.toolId})" }

                                            val isSuccess = event.result.type == "tool_result"

                                            if (isSuccess) {
                                                if (toolName.contains("SetCommitMessage", ignoreCase = true)) {
                                                    success = true
                                                    indicator.text = "Commit message set!"
                                                    updateDetails("✅ Message set")
                                                    logger.info { "SetCommitMessage completed successfully, ending collector" }
                                                    cancel()  // SetCommitMessage 成功后主动结束
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
                                            indicator.text = if (success) "Done!" else "Completed"
                                            indicator.text2 = if (success) "Commit message generated" else "Check commit panel"
                                            logger.info { "Query completed, cancelling collector" }
                                            cancel()  // 主动取消收集器
                                        }
                                        is UiError -> {
                                            logger.error { "${provider.name} error: ${event.message}" }
                                            updateDetails("❌ Error")
                                            showNotification("Error: ${event.message}", NotificationType.ERROR)
                                            cancel()  // 出错时也取消
                                        }
                                        else -> {
                                            // 忽略其他事件
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                logger.info { "Collector cancelled normally" }
                                throw e  // 必须重新抛出 CancellationException
                            }
                        }

                        // 等待 collector 准备好后再发送消息
                        collectorReady.await()
                        logger.info { "Collector ready, sending message..." }
                        client.sendMessage(AgentMessageInput(text = configuredUserPrompt))

                        collector.join()
                    }
                }
            } finally {
                try {
                    client.disconnect()
                } catch (e: Exception) {
                    logger.debug { "Disconnect error: ${e.message}" }
                }
                // MCP 端点全局共享，无需手动清理
            }

            if (success) {
                showNotification("Commit message generated successfully", NotificationType.INFORMATION)
            } else if (toolCallCount == 0) {
                showNotification("No tools were called. Please try again.", NotificationType.WARNING)
            }

        } catch (e: Exception) {
            logger.error(e) { "${provider.name} call failed" }
            showNotification("Error: ${e.message}", NotificationType.ERROR)
        }
    }

    private fun showNotification(content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Claude Code Plus Notifications")
            .createNotification(content, type)
            .notify(project)
    }

}
