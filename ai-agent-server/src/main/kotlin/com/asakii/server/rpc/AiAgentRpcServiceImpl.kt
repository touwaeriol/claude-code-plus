package com.asakii.server.rpc

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.capabilities.AgentCapabilities
import com.asakii.ai.agent.sdk.capabilities.AiPermissionMode as SdkPermissionMode
import com.asakii.ai.agent.sdk.client.AgentMessageInput
import com.asakii.ai.agent.sdk.client.UnifiedAgentClient
import com.asakii.ai.agent.sdk.client.UnifiedAgentClientFactory
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.ai.agent.sdk.connect.CodexOverrides
import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.claude.agent.sdk.types.PermissionMode
import com.asakii.claude.agent.sdk.types.ToolType
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.SandboxMode
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.rpc.api.*
import com.asakii.server.config.AiAgentServiceConfig
import com.asakii.server.settings.ClaudeSettingsLoader
import com.asakii.server.tools.IdeTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger

/**
 * AI Agent 统一 RPC 服务实现。
 *
 * 每个 WebSocket 连接对应该类的一个新实例，实例内部维护统一 SDK 客户端以及
 * 当前连接的配置与历史事件。
 */
class AiAgentRpcServiceImpl(
    private val ideTools: IdeTools,
    private val serviceConfig: AiAgentServiceConfig = AiAgentServiceConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AiAgentRpcService {

    private val logger = Logger.getLogger(javaClass.name)
    private val sessionId = UUID.randomUUID().toString()
    private val messageHistory = mutableListOf<RpcUiEvent>()
    // 🔧 事件去重：使用 UUID 或事件内容 hash 来检测重复事件
    private val sentEventIds = mutableSetOf<String>()
    private var client: UnifiedAgentClient? = null
    private var currentProvider: AiAgentProvider = serviceConfig.defaultProvider
    private var lastConnectOptions: RpcConnectOptions? = null
    
    // 同步控制由前端负责，后端直接转发给 SDK

    override suspend fun connect(options: RpcConnectOptions?): RpcConnectResult {
        logger.info("🔌 [AI-Agent] 建立会话: $sessionId")
        val normalizedOptions = options ?: lastConnectOptions ?: RpcConnectOptions()

        val connectOptions = buildConnectOptions(normalizedOptions)
        currentProvider = connectOptions.provider

        disconnectInternal()

        val newClient = UnifiedAgentClientFactory.create(connectOptions.provider)
        newClient.connect(connectOptions)
        client = newClient

        val rpcProvider = currentProvider.toRpcProvider()
        val resolvedSystemPrompt = (connectOptions.systemPrompt as? String?) ?: normalizedOptions.systemPrompt
        lastConnectOptions = normalizedOptions.copy(
            provider = rpcProvider,
            model = connectOptions.model,
            systemPrompt = resolvedSystemPrompt,
            metadata = connectOptions.metadata
        )

        logger.info(
            "✅ [AI-Agent] 已连接 provider=${connectOptions.provider} model=${connectOptions.model ?: "default"}"
        )

        // 获取并转换能力信息
        val capabilities = newClient.getCapabilities().toRpcCapabilities()

        return RpcConnectResult(
            sessionId = sessionId,
            provider = rpcProvider,
            model = connectOptions.model,
            status = RpcSessionStatus.CONNECTED,
            capabilities = capabilities
        )
    }

    override fun query(message: String): Flow<RpcUiEvent> =
        executeTurn { unifiedClient ->
            unifiedClient.sendMessage(
                AgentMessageInput(text = message, sessionId = sessionId)
            )
        }

    override fun queryWithContent(content: List<RpcContentBlock>): Flow<RpcUiEvent> =
        executeTurn { unifiedClient ->
            // 将 RPC 内容块转换为统一内容块
            val unifiedContent = content.mapNotNull { it.toUnifiedContentBlock() }
            unifiedClient.sendMessage(
                AgentMessageInput(content = unifiedContent, sessionId = sessionId)
            )
        }

    override suspend fun interrupt(): RpcStatusResult {
        logger.info("⏸️ [AI-Agent] 中断当前回合")
        client?.interrupt()
        return RpcStatusResult(status = RpcSessionStatus.INTERRUPTED)
    }

    override suspend fun disconnect(): RpcStatusResult {
        logger.info("🔌 [AI-Agent] 断开会话: $sessionId")
        disconnectInternal()
        return RpcStatusResult(status = RpcSessionStatus.DISCONNECTED)
    }

    override suspend fun setModel(model: String): RpcSetModelResult {
        logger.info("⚙️ [AI-Agent] 切换模型 -> $model")
        val base = lastConnectOptions ?: RpcConnectOptions()
        val updated = base.copy(model = model)
        connect(updated)
        return RpcSetModelResult(model = model)
    }

    override suspend fun setPermissionMode(mode: RpcPermissionMode): RpcSetPermissionModeResult {
        logger.info("⚙️ [AI-Agent] 切换权限模式 -> $mode")
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")

        // 将 RPC 权限模式转换为 SDK 权限模式
        val sdkMode = mode.toSdkPermissionModeInternal()
        activeClient.setPermissionMode(sdkMode)

        logger.info("✅ [AI-Agent] 权限模式已切换为: $mode")
        return RpcSetPermissionModeResult(mode = mode)
    }

    override suspend fun getHistory(): RpcHistory =
        RpcHistory(messages = messageHistory.toList())

    private fun executeTurn(block: suspend (UnifiedAgentClient) -> Unit): Flow<RpcUiEvent> {
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")

        return channelFlow {
            // 🔧 每次执行新回合时，清空事件去重集合
            sentEventIds.clear()
            logger.info("🚀 [executeTurn] 开始执行 (sessionId=$sessionId)")

            // 使用 CompletableDeferred 确保 collector 已开始监听
            val collectorReady = kotlinx.coroutines.CompletableDeferred<Unit>()

            val collector = launch {
                // 标记 collector 已准备好
                collectorReady.complete(Unit)

                var eventCount = 0
                try {
                    logger.info("🔄 [executeTurn] 开始收集 streamEvents() 流")
                    activeClient.streamEvents().collect { event ->
                        eventCount++
                        try {
                            val eventType = event::class.simpleName
                            logger.info("📨 [executeTurn] 收到流式事件 #$eventCount: $eventType")

                            // 记录关键事件详情
                            when (event) {
                                is UiMessageComplete -> {
                                    logger.info("✅ [executeTurn] UiMessageComplete: usage=${event.usage}")
                                }
                                is UiResultMessage -> {
                                    logger.info("🏁 [executeTurn] UiResultMessage: duration=${event.durationMs}ms isError=${event.isError}")
                                }
                                is UiUserMessage -> {
                                    logger.info("👤 [executeTurn] UiUserMessage: contentBlocks=${event.content.size}")
                                }
                                is UiError -> {
                                    logger.severe("❌ [executeTurn] UiError: ${event.message}")
                                }
                                is UiToolComplete -> {
                                    logger.info("🔧 [executeTurn] UiToolComplete: toolId=${event.toolId}, resultType=${event.result::class.simpleName}")
                                }
                                is UiToolStart -> {
                                    logger.info("🚀 [executeTurn] UiToolStart: toolId=${event.toolId}, toolName=${event.toolName}")
                                }
                                is UiToolProgress -> {
                                    logger.info("⏳ [executeTurn] UiToolProgress: toolId=${event.toolId}, status=${event.status}")
                                }
                                is UiMessageStart -> {
                                    logger.info("📝 [executeTurn] UiMessageStart: messageId=${event.messageId}")
                                }
                                is UiTextDelta -> {
                                    logger.info("📝 [executeTurn] UiTextDelta: textLength=${event.text.length}")
                                }
                                is UiThinkingDelta -> {
                                    logger.info("💭 [executeTurn] UiThinkingDelta: thinkingLength=${event.thinking.length}")
                                }
                                is UiAssistantMessage -> {
                                    logger.info("🤖 [executeTurn] UiAssistantMessage: contentBlocks=${event.content.size}")
                                }
                            }

                            val rpcEvent = event.toRpcEvent(currentProvider)

                            // 🔧 事件去重：生成事件唯一标识
                            val eventId = when (event) {
                                is UiTextDelta -> "text_${event.text.hashCode()}_${eventCount}"
                                is UiThinkingDelta -> "thinking_${event.thinking.hashCode()}_${eventCount}"
                                is UiMessageStart -> "msg_start_${event.messageId}"
                                is UiMessageComplete -> "msg_complete_${eventCount}"
                                is UiToolStart -> "tool_start_${event.toolId}"
                                is UiToolComplete -> "tool_complete_${event.toolId}"
                                is UiToolProgress -> "tool_progress_${event.toolId}_${eventCount}"
                                is UiAssistantMessage -> "assistant_${eventCount}"
                                is UiUserMessage -> "user_${eventCount}"
                                is UiResultMessage -> "result_${eventCount}"
                                is UiError -> "error_${event.message.hashCode()}_${eventCount}"
                                else -> "unknown_${eventCount}"
                            }

                            // 检查是否已发送过相同的事件
                            if (sentEventIds.contains(eventId)) {
                                logger.warning("⚠️ [executeTurn] 检测到重复事件，跳过: eventId=$eventId, type=$eventType")
                                return@collect
                            }
                            sentEventIds.add(eventId)

                            messageHistory.add(rpcEvent)

                            // 尝试发送事件
                            try {
                                send(rpcEvent)
                                logger.info("✅ [executeTurn] 事件 #$eventCount ($eventType) 已发送")
                            } catch (e: kotlinx.coroutines.channels.ClosedSendChannelException) {
                                logger.warning("⚠️ [executeTurn] Channel 已关闭，停止收集")
                                cancel()
                                return@collect
                            } catch (e: Exception) {
                                logger.severe("❌ [executeTurn] 发送事件失败 #$eventCount: ${e.message}")
                                e.printStackTrace()
                            }

                            // 🔧 关键：收到 result 或 error 后立即停止收集
                            if (event is UiResultMessage) {
                                logger.info("🏁 [executeTurn] 收到 result 事件，停止收集器")
                                cancel()
                            }
                            if (event is UiError) {
                                logger.severe("❌ [executeTurn] 收到错误事件，取消收集器")
                                cancel()
                            }
                        } catch (e: Exception) {
                            logger.severe("❌ [executeTurn] 处理流式事件时出错 #$eventCount: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    logger.info("📊 [executeTurn] streamEvents() 流收集完成，共 $eventCount 个事件")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    logger.info("ℹ️ [executeTurn] 收集器被取消")
                    throw e
                } catch (e: Exception) {
                    logger.severe("❌ [executeTurn] 收集流式事件时出错: ${e.message}")
                    e.printStackTrace()
                    throw e
                }
            }

            // 等待 collector 准备好再执行 block
            collectorReady.await()
            logger.info("✅ [executeTurn] collector 已准备好，开始执行 block")

            try {
                block(activeClient)
                logger.info("✅ [executeTurn] block 执行完成")
            } catch (t: Throwable) {
                logger.severe("❌ [executeTurn] block 执行失败: ${t.message}")
                collector.cancel()
                throw t
            }

            try {
                collector.join()
                logger.info("✅ [executeTurn] collector 已完成")
            } catch (_: CancellationException) {
                logger.info("ℹ️ [executeTurn] collector 正常取消")
            }

            logger.info("🏁 [executeTurn] 执行完成 (sessionId=$sessionId)")
        }
    }

    private suspend fun disconnectInternal() {
        try {
            client?.disconnect()
        } catch (t: Throwable) {
            logger.warning("⚠️ [AI-Agent] 断开客户端时出错: ${t.message}")
        } finally {
            client = null
        }
    }

    private fun buildConnectOptions(options: RpcConnectOptions): AiAgentConnectOptions {
        val provider = options.provider.toSdkProvider(serviceConfig.defaultProvider)
        val model = options.model ?: serviceConfig.defaultModel
        val systemPrompt = options.systemPrompt ?: serviceConfig.defaultSystemPrompt
        val initialPrompt = options.initialPrompt
        val sessionHint = options.sessionId
        val resume = options.resumeSessionId
        val metadata = options.metadata.ifEmpty { emptyMap() }

        // 从顶层 options 读取配置（统一扁平结构）
        val claudeOverrides = buildClaudeOverrides(model, systemPrompt, options, metadata)
        val codexOverrides = buildCodexOverrides(model, options)

        return AiAgentConnectOptions(
            provider = provider,
            model = model,
            systemPrompt = systemPrompt,
            initialPrompt = initialPrompt,
            sessionId = sessionHint,
            resumeSessionId = resume,
            metadata = metadata,
            claude = claudeOverrides,
            codex = codexOverrides
        )
    }

    private fun buildClaudeOverrides(
        model: String?,
        systemPrompt: String?,
        options: RpcConnectOptions,
        metadata: Map<String, String>
    ): ClaudeOverrides {
        val cwd = ideTools.getProjectPath().takeIf { it.isNotBlank() }?.let { Path.of(it) }
        val defaults = serviceConfig.claude

        // 从顶层 options 读取配置（统一扁平结构）
        val permissionMode = options.permissionMode?.toSdkPermissionMode()
            ?: defaults.permissionMode?.let { it.toPermissionModeOrNull() }
            ?: PermissionMode.DEFAULT

        val metadataThinkingEnabled = metadata["thinkingEnabled"]?.toBooleanStrictOrNull()
        val thinkingEnabled = options.thinkingEnabled ?: metadataThinkingEnabled ?: true

        val claudeSettings = ClaudeSettingsLoader.loadMergedSettings(cwd)
        val maxThinkingTokens = ClaudeSettingsLoader.resolveMaxThinkingTokens(claudeSettings, thinkingEnabled)

        // 设置 output-format 为 stream-json（默认）
        val extraArgs = mutableMapOf<String, String?>(
            "output-format" to "stream-json"
        )

        val claudeOptions = ClaudeAgentOptions(
            model = model,
            cwd = cwd,
            systemPrompt = systemPrompt,
            dangerouslySkipPermissions = options.dangerouslySkipPermissions
                ?: defaults.dangerouslySkipPermissions,
            allowDangerouslySkipPermissions = options.allowDangerouslySkipPermissions
                ?: defaults.allowDangerouslySkipPermissions,
            includePartialMessages = options.includePartialMessages
                ?: defaults.includePartialMessages,
            permissionMode = permissionMode,
            continueConversation = options.continueConversation ?: false,
            resume = options.resumeSessionId,  // 使用统一的 resumeSessionId
            maxThinkingTokens = maxThinkingTokens,
            extraArgs = extraArgs
        )

        return ClaudeOverrides(options = claudeOptions)
    }

    private fun buildCodexOverrides(
        model: String?,
        options: RpcConnectOptions
    ): CodexOverrides {
        val codexDefaults = serviceConfig.codex

        // 从顶层 options 读取配置（统一扁平结构）
        val clientOptions = CodexClientOptions(
            baseUrl = options.baseUrl ?: codexDefaults.baseUrl,
            apiKey = options.apiKey ?: codexDefaults.apiKey
        )

        val sandboxMode = options.sandboxMode?.toSdkSandboxMode()
            ?: codexDefaults.sandboxMode?.let {
                runCatching { SandboxMode.valueOf(it.uppercase()) }.getOrNull()
            }

        val threadOptions = ThreadOptions(
            model = model,
            sandboxMode = sandboxMode
        )

        return CodexOverrides(
            clientOptions = clientOptions,
            threadOptions = threadOptions
        )
    }

    private fun flattenContentBlocks(blocks: List<RpcContentBlock>): String {
        if (blocks.isEmpty()) return ""
        val builder = StringBuilder()
        blocks.forEach { block ->
            when (block) {
                is RpcTextBlock -> builder.append(block.text)
                is RpcThinkingBlock -> {
                    builder.appendLine()
                        .append("[Thinking]")
                        .appendLine()
                        .append(block.thinking)
                }
                is RpcImageBlock -> {
                    builder.appendLine()
                        .append("[Image attachment: ")
                        .append(block.source.mediaType)
                        .append("]")
                }
                is RpcToolUseBlock -> {
                    builder.appendLine()
                        .append("[Tool: ${block.toolName} #${block.id}]")
                    block.input?.let { builder.appendLine(it.toString()) }
                }
                is RpcToolResultBlock -> {
                    builder.appendLine()
                        .append("[Tool Result: ${block.toolUseId}]")
                    block.content?.let { builder.appendLine(it.toString()) }
                }
                else -> {
                    builder.appendLine()
                        .append("[${block::class.simpleName ?: "block"}]")
                }
            }
            builder.appendLine()
        }
        return builder.toString().trim()
    }

    private fun UiStreamEvent.toRpcEvent(provider: AiAgentProvider): RpcUiEvent = when (this) {
        is UiMessageStart -> RpcMessageStart(
            messageId = messageId,
            content = content?.map { it.toRpcContentBlock() },
            provider = provider.toRpcProvider()
        )
        is UiTextDelta -> RpcTextDelta(text = text, provider = provider.toRpcProvider())
        is UiThinkingDelta -> RpcThinkingDelta(thinking = thinking, provider = provider.toRpcProvider())
        is UiToolStart -> RpcToolStart(
            toolId = toolId,
            toolName = toolName,
            toolType = toolType,  // 类型标识: "CLAUDE_READ", "CLAUDE_WRITE", "MCP" 等
            inputPreview = inputPreview,
            provider = provider.toRpcProvider()
        )
        is UiToolProgress -> RpcToolProgress(
            toolId = toolId,
            status = status.toRpcStatus(),
            outputPreview = outputPreview,
            provider = provider.toRpcProvider()
        )
        is UiToolComplete -> RpcToolComplete(
            toolId = toolId,
            result = result.toRpcContentBlock(),
            provider = provider.toRpcProvider()
        )
        is UiMessageComplete -> RpcMessageComplete(
            usage = usage?.toRpcUsage(),
            provider = provider.toRpcProvider()
        )
        is UiResultMessage -> RpcResultMessage(
            durationMs = durationMs,
            durationApiMs = durationApiMs,
            isError = isError,
            numTurns = numTurns,
            sessionId = sessionId,
            totalCostUsd = totalCostUsd,
            usage = usage,
            result = result,
            provider = provider.toRpcProvider()
        )
        is UiUserMessage -> RpcUserMessage(
            content = content.map { it.toRpcContentBlock() },
            provider = provider.toRpcProvider()
        )
        is UiError -> RpcError(
            message = message,
            provider = provider.toRpcProvider()
        )
        is UiAssistantMessage -> RpcAssistantMessage(
            content = content.map { it.toRpcContentBlock() },
            provider = provider.toRpcProvider()
        )
    }

    private fun UnifiedContentBlock.toRpcContentBlock(): RpcContentBlock = when (this) {
        is TextContent -> RpcTextBlock(text = text)
        is ImageContent -> RpcImageBlock(source = RpcImageSource(type = "base64", mediaType = mediaType, data = data))
        is ThinkingContent -> RpcThinkingBlock(thinking = thinking, signature = signature)
        is ToolUseContent -> {
            val toolTypeEnum = ToolType.fromToolName(name)
            RpcToolUseBlock(
                id = id,
                toolName = name,           // 显示名称
                toolType = toolTypeEnum.type,  // 类型标识: "CLAUDE_READ" 等
                input = input,
                status = status.toRpcStatus()
            )
        }
        is ToolResultContent -> RpcToolResultBlock(
            toolUseId = toolUseId,
            content = content,
            isError = isError
        )
        is CommandExecutionContent -> RpcCommandExecutionBlock(
            command = command,
            output = output,
            exitCode = exitCode,
            status = status.toRpcStatus()
        )
        is FileChangeContent -> RpcFileChangeBlock(
            status = status.toRpcStatus(),
            changes = changes.map { RpcFileChange(path = it.path, kind = it.kind) }
        )
        is McpToolCallContent -> RpcMcpToolCallBlock(
            server = server,
            tool = tool,
            arguments = arguments,
            result = result,
            status = status.toRpcStatus()
        )
        is WebSearchContent -> RpcWebSearchBlock(query = query)
        is TodoListContent -> RpcTodoListBlock(
            items = items.map { RpcTodoItem(text = it.text, completed = it.completed) }
        )
        is ErrorContent -> RpcErrorBlock(message = message)
    }

    private fun UnifiedUsage.toRpcUsage(): RpcUsage = RpcUsage(
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        cachedInputTokens = cachedInputTokens,
        provider = provider.toRpcProvider(),
        raw = raw
    )

    private fun ContentStatus.toRpcStatus(): RpcContentStatus = when (this) {
        ContentStatus.IN_PROGRESS -> RpcContentStatus.IN_PROGRESS
        ContentStatus.COMPLETED -> RpcContentStatus.COMPLETED
        ContentStatus.FAILED -> RpcContentStatus.FAILED
    }

    private fun RpcPermissionMode.toSdkPermissionMode(): PermissionMode = when (this) {
        RpcPermissionMode.DEFAULT -> PermissionMode.DEFAULT
        RpcPermissionMode.BYPASS_PERMISSIONS -> PermissionMode.BYPASS_PERMISSIONS
        RpcPermissionMode.ACCEPT_EDITS -> PermissionMode.ACCEPT_EDITS
        RpcPermissionMode.PLAN -> PermissionMode.PLAN
        RpcPermissionMode.DONT_ASK -> PermissionMode.DONT_ASK
    }

    private fun RpcSandboxMode.toSdkSandboxMode(): SandboxMode = when (this) {
        RpcSandboxMode.READ_ONLY -> SandboxMode.READ_ONLY
        RpcSandboxMode.WORKSPACE_WRITE -> SandboxMode.WORKSPACE_WRITE
        RpcSandboxMode.DANGER_FULL_ACCESS -> SandboxMode.DANGER_FULL_ACCESS
    }

    private fun RpcProvider?.toSdkProvider(default: AiAgentProvider): AiAgentProvider = when (this) {
        RpcProvider.CLAUDE -> AiAgentProvider.CLAUDE
        RpcProvider.CODEX -> AiAgentProvider.CODEX
        null -> default
    }

    private fun AiAgentProvider.toRpcProvider(): RpcProvider = when (this) {
        AiAgentProvider.CLAUDE -> RpcProvider.CLAUDE
        AiAgentProvider.CODEX -> RpcProvider.CODEX
    }

    private fun String.toPermissionModeOrNull(): PermissionMode? = when (this) {
        "bypassPermissions" -> PermissionMode.BYPASS_PERMISSIONS
        "acceptEdits" -> PermissionMode.ACCEPT_EDITS
        "plan" -> PermissionMode.PLAN
        "dontAsk" -> PermissionMode.DONT_ASK
        else -> PermissionMode.DEFAULT
    }

    /**
     * 将 RPC 内容块转换为统一内容块（用于用户输入）
     */
    private fun RpcContentBlock.toUnifiedContentBlock(): UnifiedContentBlock? = when (this) {
        is RpcTextBlock -> TextContent(text = text)
        is RpcImageBlock -> source.data?.let { data ->
            ImageContent(data = data, mediaType = source.mediaType)
        }
        is RpcThinkingBlock -> ThinkingContent(thinking = thinking, signature = signature)
        // 以下类型不应该出现在用户输入中，返回 null
        is RpcToolUseBlock,
        is RpcToolResultBlock,
        is RpcCommandExecutionBlock,
        is RpcFileChangeBlock,
        is RpcMcpToolCallBlock,
        is RpcWebSearchBlock,
        is RpcTodoListBlock,
        is RpcErrorBlock,
        is RpcUnknownBlock -> null
    }

    // ==================== 能力相关转换函数 ====================

    /**
     * 将 SDK AgentCapabilities 转换为 RPC RpcCapabilities
     */
    private fun AgentCapabilities.toRpcCapabilities(): RpcCapabilities = RpcCapabilities(
        canInterrupt = canInterrupt,
        canSwitchModel = canSwitchModel,
        canSwitchPermissionMode = canSwitchPermissionMode,
        supportedPermissionModes = supportedPermissionModes.map { it.toRpcPermissionMode() },
        canSkipPermissions = canSkipPermissions,
        canSendRichContent = canSendRichContent,
        canThink = canThink,
        canResumeSession = canResumeSession
    )

    /**
     * 将 SDK PermissionMode 转换为 RPC RpcPermissionMode
     */
    private fun SdkPermissionMode.toRpcPermissionMode(): RpcPermissionMode = when (this) {
        SdkPermissionMode.DEFAULT -> RpcPermissionMode.DEFAULT
        SdkPermissionMode.ACCEPT_EDITS -> RpcPermissionMode.ACCEPT_EDITS
        SdkPermissionMode.BYPASS_PERMISSIONS -> RpcPermissionMode.BYPASS_PERMISSIONS
        SdkPermissionMode.PLAN -> RpcPermissionMode.PLAN
        SdkPermissionMode.DONT_ASK -> RpcPermissionMode.DONT_ASK
    }

    /**
     * 将 RPC RpcPermissionMode 转换为 SDK PermissionMode（用于 setPermissionMode）
     */
    private fun RpcPermissionMode.toSdkPermissionModeInternal(): SdkPermissionMode = when (this) {
        RpcPermissionMode.DEFAULT -> SdkPermissionMode.DEFAULT
        RpcPermissionMode.ACCEPT_EDITS -> SdkPermissionMode.ACCEPT_EDITS
        RpcPermissionMode.BYPASS_PERMISSIONS -> SdkPermissionMode.BYPASS_PERMISSIONS
        RpcPermissionMode.PLAN -> SdkPermissionMode.PLAN
        RpcPermissionMode.DONT_ASK -> SdkPermissionMode.DONT_ASK
    }
}
