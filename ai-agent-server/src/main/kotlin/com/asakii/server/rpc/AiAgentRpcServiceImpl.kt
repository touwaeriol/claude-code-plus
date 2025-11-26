package com.asakii.server.rpc

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.client.AgentMessageInput
import com.asakii.ai.agent.sdk.client.UnifiedAgentClient
import com.asakii.ai.agent.sdk.client.UnifiedAgentClientFactory
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.ai.agent.sdk.connect.CodexOverrides
import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.claude.agent.sdk.types.PermissionMode
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var client: UnifiedAgentClient? = null
    private var currentProvider: AiAgentProvider = serviceConfig.defaultProvider
    private var lastConnectOptions: RpcConnectOptions? = null
    
    /**
     * 会话级别的互斥锁
     * 
     * 保证同一会话在同一时刻只有一个协程在执行操作。
     * 用于防止并发执行导致的冲突，确保操作的原子性和顺序性。
     * 
     * 当前使用场景：
     * - query() 方法：通过 executeTurn() 使用此锁
     * - queryWithContent() 方法：通过 executeTurn() 使用此锁
     * 
     * 后续可能扩展：
     * - interrupt() 方法：可能需要加锁以确保与 query 的互斥
     * - setModel() 方法：可能需要加锁以确保与 query 的互斥
     * - 其他需要串行化的操作
     */
    private val sessionMutex = Mutex()

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

        return RpcConnectResult(
            sessionId = sessionId,
            provider = rpcProvider,
            model = connectOptions.model,
            status = RpcSessionStatus.CONNECTED
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

    override suspend fun getHistory(): RpcHistory =
        RpcHistory(messages = messageHistory.toList())

    private fun executeTurn(block: suspend (UnifiedAgentClient) -> Unit): Flow<RpcUiEvent> {
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")

        return channelFlow {
            // 使用会话级别的互斥锁，确保同一会话同一时刻只有一个协程在执行
            sessionMutex.withLock {
                logger.info("🔒 [executeTurn] 获取会话锁 (sessionId=$sessionId)，开始执行")
                
                // 使用 CompletableDeferred 确保 collector 已开始监听
                val collectorReady = kotlinx.coroutines.CompletableDeferred<Unit>()

                val collector = launch {
                    // 标记 collector 已准备好
                    collectorReady.complete(Unit)

                    try {
                        activeClient.streamEvents().collect { event ->
                            try {
                                logger.info("📨 [executeTurn] 收到流式事件: ${event::class.simpleName}")
                                val rpcEvent = event.toRpcEvent(currentProvider)
                                messageHistory.add(rpcEvent)
                                
                                // 尝试发送事件，如果 channel 已关闭则记录日志但不抛出异常
                                try {
                                    send(rpcEvent)
                                } catch (e: kotlinx.coroutines.channels.ClosedSendChannelException) {
                                    logger.warning("⚠️ [executeTurn] Channel 已关闭，无法发送事件: ${event::class.simpleName}")
                                    cancel() // 取消收集器
                                    return@collect
                                } catch (e: Exception) {
                                    logger.severe("❌ [executeTurn] 发送事件失败: ${e.message}")
                                    e.printStackTrace()
                                    // 不取消，继续尝试发送后续事件
                                }

                                if (event is UiMessageComplete || event is UiError) {
                                    logger.info("📨 [executeTurn] 收到结束事件，取消收集器")
                                    cancel()
                                }
                            } catch (e: Exception) {
                                logger.severe("❌ [executeTurn] 处理流式事件时出错: ${e.message}")
                                e.printStackTrace()
                                // 继续处理下一个事件
                            }
                        }
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
                    // ignore – turn finished normally
                    logger.info("ℹ️ [executeTurn] collector 正常取消")
                }
                
                logger.info("🔓 [executeTurn] 释放会话锁 (sessionId=$sessionId)，执行完成")
            }
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
        val resume = options.resumeSessionId ?: options.claude?.resume
        val metadata = options.metadata.ifEmpty { emptyMap() }

        val claudeOverrides = buildClaudeOverrides(model, systemPrompt, options.claude, metadata)
        val codexOverrides = buildCodexOverrides(model, options.codex)

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
        options: RpcClaudeOptions?,
        metadata: Map<String, String>
    ): ClaudeOverrides {
        val cwd = ideTools.getProjectPath().takeIf { it.isNotBlank() }?.let { Path.of(it) }
        val defaults = serviceConfig.claude

        val permissionMode = options?.permissionMode?.toSdkPermissionMode()
            ?: defaults.permissionMode?.let { it.toPermissionModeOrNull() }
            ?: PermissionMode.DEFAULT

        val metadataThinkingEnabled = metadata["thinkingEnabled"]?.toBooleanStrictOrNull()
        val thinkingEnabled = options?.thinkingEnabled ?: metadataThinkingEnabled ?: true

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
            dangerouslySkipPermissions = options?.dangerouslySkipPermissions
                ?: defaults.dangerouslySkipPermissions,
            allowDangerouslySkipPermissions = options?.allowDangerouslySkipPermissions
                ?: defaults.allowDangerouslySkipPermissions,
            includePartialMessages = options?.includePartialMessages
                ?: defaults.includePartialMessages,
            permissionMode = permissionMode,
            continueConversation = options?.continueConversation ?: false,
            resume = options?.resume,
            maxThinkingTokens = maxThinkingTokens,
            // 确保在使用 stream-json 时，如果启用了 print，也启用 verbose
            // 注意：print 和 verbose 默认都是 false，只有在明确设置时才启用
            extraArgs = extraArgs
        )

        return ClaudeOverrides(options = claudeOptions)
    }

    private fun buildCodexOverrides(
        model: String?,
        options: RpcCodexOptions?
    ): CodexOverrides {
        val codexDefaults = serviceConfig.codex

        val clientOptions = CodexClientOptions(
            baseUrl = options?.baseUrl ?: codexDefaults.baseUrl,
            apiKey = options?.apiKey ?: codexDefaults.apiKey
        )

        val sandboxMode = options?.sandboxMode?.toSdkSandboxMode()
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
                        .append("[Tool: ${block.name} #${block.id}]")
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
        is ToolUseContent -> RpcToolUseBlock(
            id = id,
            name = name,
            input = input,
            status = status.toRpcStatus()
        )
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
}
