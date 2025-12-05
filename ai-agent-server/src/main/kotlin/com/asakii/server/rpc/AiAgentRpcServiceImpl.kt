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
import com.asakii.claude.agent.sdk.types.PermissionResultAllow
import com.asakii.claude.agent.sdk.types.PermissionResultDeny
import com.asakii.claude.agent.sdk.types.PermissionUpdate as SdkPermissionUpdate
import com.asakii.claude.agent.sdk.types.PermissionUpdateDestination as SdkPermissionUpdateDestination
import com.asakii.claude.agent.sdk.types.PermissionUpdateType as SdkPermissionUpdateType
import com.asakii.claude.agent.sdk.types.PermissionBehavior as SdkPermissionBehavior
import com.asakii.claude.agent.sdk.types.PermissionRuleValue as SdkPermissionRuleValue
import com.asakii.claude.agent.sdk.types.CanUseTool
import com.asakii.claude.agent.sdk.types.ToolType
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.SandboxMode
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.rpc.api.*
import com.asakii.server.config.AiAgentServiceConfig
import com.asakii.server.mcp.PermissionResponse
import com.asakii.server.mcp.PermissionUpdate as McpPermissionUpdate
import com.asakii.server.mcp.PermissionUpdateDestination
import com.asakii.server.mcp.PermissionUpdateType as McpPermissionUpdateType
import com.asakii.server.mcp.PermissionBehavior as McpPermissionBehavior
import com.asakii.server.mcp.PermissionMode as McpPermissionMode
import com.asakii.server.mcp.PermissionRuleValue as McpPermissionRuleValue
import com.asakii.server.mcp.UserInteractionMcpServer
import com.asakii.server.settings.ClaudeSettingsLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger

/**
 * AI Agent 缁熶竴 RPC 鏈嶅姟瀹炵幇銆? *
 * 姣忎釜 WebSocket 杩炴帴瀵瑰簲璇ョ被鐨勪竴涓柊瀹炰緥锛屽疄渚嬪唴閮ㄧ淮鎶ょ粺涓€ SDK 瀹㈡埛绔互鍙? * 褰撳墠杩炴帴鐨勯厤缃笌鍘嗗彶浜嬩欢銆? */
class AiAgentRpcServiceImpl(
    private val ideTools: IdeTools,
    private val clientCaller: ClientCaller? = null,
    private val serviceConfig: AiAgentServiceConfig = AiAgentServiceConfig(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AiAgentRpcService {

    private val logger = Logger.getLogger(javaClass.name)
    private val sessionId = UUID.randomUUID().toString()
    private val messageHistory = mutableListOf<RpcMessage>()
    // 事件去重：使用 UUID 或事件内容 hash 来检测重复事件
    private val sentEventIds = mutableSetOf<String>()
    // 流式事件计数器，用于生成 UUID
    private var streamEventCounter = 0
    // 当前流式消息内容块索引（对应 Claude index）
    private var nextContentIndex = 0
    private val toolContentIndex = mutableMapOf<String, Int>()
    private var client: UnifiedAgentClient? = null
    private var currentProvider: AiAgentProvider = serviceConfig.defaultProvider
    private var lastConnectOptions: RpcConnectOptions? = null

    // 🔧 追踪当前 query 的完成状态，用于 interrupt 同步等待
    private var queryCompletion: CompletableDeferred<Unit>? = null

    // 用户交互 MCP Server（仅包含 AskUserQuestion，权限走 canUseTool 回调）
    private val userInteractionServer = UserInteractionMcpServer().apply {
        clientCaller?.let { setClientCaller(it) }
    }

    /**
     * 将 MCP 权限更新转换为 SDK 权限更新
     */
    private fun McpPermissionUpdate.toSdkPermissionUpdate(): SdkPermissionUpdate {
        return SdkPermissionUpdate(
            type = when (this.type) {
                McpPermissionUpdateType.ADD_RULES -> SdkPermissionUpdateType.ADD_RULES
                McpPermissionUpdateType.REPLACE_RULES -> SdkPermissionUpdateType.REPLACE_RULES
                McpPermissionUpdateType.REMOVE_RULES -> SdkPermissionUpdateType.REMOVE_RULES
                McpPermissionUpdateType.SET_MODE -> SdkPermissionUpdateType.SET_MODE
                McpPermissionUpdateType.ADD_DIRECTORIES -> SdkPermissionUpdateType.ADD_DIRECTORIES
                McpPermissionUpdateType.REMOVE_DIRECTORIES -> SdkPermissionUpdateType.REMOVE_DIRECTORIES
            },
            rules = this.rules?.map { rule ->
                SdkPermissionRuleValue(
                    toolName = rule.toolName,
                    ruleContent = rule.ruleContent
                )
            },
            behavior = this.behavior?.let { b ->
                when (b) {
                    McpPermissionBehavior.ALLOW -> SdkPermissionBehavior.ALLOW
                    McpPermissionBehavior.DENY -> SdkPermissionBehavior.DENY
                    McpPermissionBehavior.ASK -> SdkPermissionBehavior.ASK
                }
            },
            mode = this.mode?.let { m ->
                when (m) {
                    McpPermissionMode.DEFAULT -> PermissionMode.DEFAULT
                    McpPermissionMode.ACCEPT_EDITS -> PermissionMode.ACCEPT_EDITS
                    McpPermissionMode.PLAN -> PermissionMode.PLAN
                    McpPermissionMode.BYPASS_PERMISSIONS -> PermissionMode.BYPASS_PERMISSIONS
                    McpPermissionMode.DONT_ASK -> PermissionMode.DONT_ASK
                }
            },
            directories = this.directories,
            destination = this.destination?.let { d ->
                when (d) {
                    PermissionUpdateDestination.USER_SETTINGS -> SdkPermissionUpdateDestination.USER_SETTINGS
                    PermissionUpdateDestination.PROJECT_SETTINGS -> SdkPermissionUpdateDestination.PROJECT_SETTINGS
                    PermissionUpdateDestination.LOCAL_SETTINGS -> SdkPermissionUpdateDestination.LOCAL_SETTINGS
                    PermissionUpdateDestination.SESSION -> SdkPermissionUpdateDestination.SESSION
                }
            }
        )
    }
    
    // 鍚屾鎺у埗鐢卞墠绔礋璐ｏ紝鍚庣鐩存帴杞彂缁?SDK

    override suspend fun connect(options: RpcConnectOptions?): RpcConnectResult {
        logger.info("馃攲 [AI-Agent] 寤虹珛浼氳瘽: $sessionId")
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
            "鉁?[AI-Agent] 宸茶繛鎺?provider=${connectOptions.provider} model=${connectOptions.model ?: "default"}"
        )

                val capabilities = newClient.getCapabilities().toRpcCapabilities()

        val projectCwd = ideTools.getProjectPath().takeIf { it.isNotBlank() }

        return RpcConnectResult(
            sessionId = sessionId,
            provider = rpcProvider,
            model = connectOptions.model,
            status = RpcSessionStatus.CONNECTED,
            capabilities = capabilities,
            cwd = projectCwd
        )
    }

    override fun query(message: String): Flow<RpcMessage> =
        executeTurn { unifiedClient ->
            unifiedClient.sendMessage(
                AgentMessageInput(text = message, sessionId = sessionId)
            )
        }

    override fun queryWithContent(content: List<RpcContentBlock>): Flow<RpcMessage> =
        executeTurn { unifiedClient ->
                        val unifiedContent = content.mapNotNull { it.toUnifiedContentBlock() }
            unifiedClient.sendMessage(
                AgentMessageInput(content = unifiedContent, sessionId = sessionId)
            )
        }

    override suspend fun interrupt(): RpcStatusResult {
        logger.info("🔔 [AI-Agent] 中断当前回合（立即返回模式）")
        // 直接调用 SDK 的 interrupt，不再等待 query 流的完成信号
        client?.interrupt()
        logger.info("✅ [AI-Agent] interrupt 请求已提交，立即返回给前端")
        return RpcStatusResult(status = RpcSessionStatus.INTERRUPTED)
    }

    override suspend fun disconnect(): RpcStatusResult {
        logger.info("馃攲 [AI-Agent] 鏂紑浼氳瘽: $sessionId")
        disconnectInternal()
        return RpcStatusResult(status = RpcSessionStatus.DISCONNECTED)
    }

    override suspend fun setModel(model: String): RpcSetModelResult {
        logger.info("鈿欙笍 [AI-Agent] 鍒囨崲妯″瀷 -> $model")
        val base = lastConnectOptions ?: RpcConnectOptions()
        val updated = base.copy(model = model)
        connect(updated)
        return RpcSetModelResult(model = model)
    }

    override suspend fun setPermissionMode(mode: RpcPermissionMode): RpcSetPermissionModeResult {
        logger.info("鈿欙笍 [AI-Agent] 鍒囨崲鏉冮檺妯″紡 -> $mode")
        val activeClient = client ?: error("AI Agent 灏氭湭杩炴帴锛岃鍏堣皟鐢?connect()")

        // 灏?RPC 鏉冮檺妯″紡杞崲涓?SDK 鏉冮檺妯″紡
        val sdkMode = mode.toSdkPermissionModeInternal()
        activeClient.setPermissionMode(sdkMode)

        logger.info("鉁?[AI-Agent] 鏉冮檺妯″紡宸插垏鎹负: $mode")
        return RpcSetPermissionModeResult(mode = mode)
    }

    override suspend fun getHistory(): RpcHistory =
        RpcHistory(messages = messageHistory.toList())

        private fun executeTurn(block: suspend (UnifiedAgentClient) -> Unit): Flow<RpcMessage> {
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")

        return channelFlow {
            // 创建完成信号
            queryCompletion = CompletableDeferred()

            streamEventCounter = 0
            nextContentIndex = 0
            toolContentIndex.clear()
            logger.info("[executeTurn] start (sessionId=$sessionId)")

            val collectorReady = kotlinx.coroutines.CompletableDeferred<Unit>()

            val collector = launch {
                collectorReady.complete(Unit)

                var eventCount = 0
                try {
                    logger.info("[executeTurn] collecting streamEvents()")
                    activeClient.streamEvents().collect { event ->
                        eventCount++
                        try {
                            val eventType = event::class.simpleName
                            logger.info("[executeTurn] got stream event #$eventCount: $eventType")

                            val rpcEvent = event.toRpcMessage(currentProvider)

                            messageHistory.add(rpcEvent)

                            try {
                                val rpcProvider = currentProvider.toRpcProvider()
                                send(rpcEvent)
                                logger.info("[executeTurn] event #$eventCount ($eventType) sent")

                                when (event) {
                                    is UiMessageComplete -> {
                                        val stopEvent = wrapAsStreamEvent(RpcMessageStopEvent(), rpcProvider)
                                        messageHistory.add(stopEvent)
                                        send(stopEvent)
                                        logger.info("[executeTurn] appended message_stop")
                                    }

                                    is UiToolComplete -> {
                                        val resultBlock = event.result.toRpcContentBlock()
                                        val toolResultMessage = RpcUserMessage(
                                            message = RpcMessageContent(content = listOf(resultBlock)),
                                            parentToolUseId = event.toolId,
                                            provider = rpcProvider
                                        )
                                        messageHistory.add(toolResultMessage)
                                        send(toolResultMessage)
                                        logger.info("[executeTurn] tool result message sent toolId=${event.toolId}")
                                    }

                                    else -> {}
                                }
                            } catch (e: ClosedSendChannelException) {
                                logger.warning("[executeTurn] channel closed, stop collecting")
                                cancel()
                                return@collect
                            } catch (e: Exception) {
                                logger.severe("[executeTurn] send event failed #$eventCount: ${e.message}")
                                e.printStackTrace()
                            }

                            if (event is UiResultMessage) {
                                logger.info("[executeTurn] got result event, complete query and cancel collector")
                                // 收到 result 消息后立即标记完成，让 interrupt 的 await 能及时返回
                                queryCompletion?.complete(Unit)
                                cancel()
                            }
                            if (event is UiError) {
                                logger.severe("[executeTurn] got error event, complete query and cancel collector")
                                queryCompletion?.complete(Unit)
                                cancel()
                            }
                        } catch (e: Exception) {
                            logger.severe("[executeTurn] handle stream event error #$eventCount: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    logger.info("[executeTurn] streamEvents collected, total $eventCount")
                } catch (e: CancellationException) {
                    logger.info("[executeTurn] collector cancelled")
                    throw e
                } catch (e: Exception) {
                    logger.severe("[executeTurn] collect stream events error: ${e.message}")
                    e.printStackTrace()
                    throw e
                }
            }

            collectorReady.await()
            logger.info("[executeTurn] collector ready, run block")

            try {
                block(activeClient)
                logger.info("[executeTurn] block done")
            } catch (t: Throwable) {
                logger.severe("[executeTurn] block failed: ${t.message}")
                collector.cancel()
                throw t
            }

            try {
                collector.join()
                logger.info("[executeTurn] collector completed")
            } catch (_: CancellationException) {
                logger.info("[executeTurn] collector cancelled normally")
            }

            logger.info("[executeTurn] done (sessionId=$sessionId)")
        }.onCompletion {
            // 🔧 Flow 结束时标记完成，让 interrupt 的 await 返回
            queryCompletion?.complete(Unit)
            queryCompletion = null
            logger.info("[executeTurn] Flow completed, queryCompletion signaled")
        }
    }

    private suspend fun disconnectInternal() {
        try {
            client?.disconnect()
        } catch (t: Throwable) {
            logger.warning("鈿狅笍 [AI-Agent] 鏂紑瀹㈡埛绔椂鍑洪敊: ${t.message}")
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

                val permissionMode = options.permissionMode?.toSdkPermissionMode()
            ?: defaults.permissionMode?.let { it.toPermissionModeOrNull() }
            ?: PermissionMode.DEFAULT

        val metadataThinkingEnabled = metadata["thinkingEnabled"]?.toBooleanStrictOrNull()
        val thinkingEnabled = options.thinkingEnabled ?: metadataThinkingEnabled ?: true

        val claudeSettings = ClaudeSettingsLoader.loadMergedSettings(cwd)
        val maxThinkingTokens = ClaudeSettingsLoader.resolveMaxThinkingTokens(claudeSettings, thinkingEnabled)

        // 璁剧疆 output-format 涓?stream-json锛堥粯璁わ級
        val extraArgs = mutableMapOf<String, String?>(
            "output-format" to "stream-json"
        )

        // 注册 MCP Server（包含 AskUserQuestion 工具）
        val mcpServers = mapOf<String, Any>("user_interaction" to userInteractionServer)

        // canUseTool 回调：通过 RPC 调用前端获取用户授权（带 tool_use_id 和 permissionSuggestions）
        val canUseToolCallback: CanUseTool = { toolName, input, toolUseId, context ->
            logger.info("🔐 [canUseTool] 请求授权: toolName=$toolName, toolUseId=$toolUseId, suggestions=${context.suggestions.size}")
            val caller = clientCaller
            if (caller != null) {
                try {
                    val response: PermissionResponse = caller.callTyped(
                        method = "RequestPermission",
                        params = buildJsonObject {
                            put("toolName", toolName)
                            put("input", JsonObject(input))
                            toolUseId?.let { put("toolUseId", it) }
                            if (context.suggestions.isNotEmpty()) {
                                put("permissionSuggestions", Json.encodeToJsonElement(
                                    ListSerializer(SdkPermissionUpdate.serializer()),
                                    context.suggestions
                                ))
                            }
                        }
                    )
                    if (response.approved) {
                        // 转换权限更新为 SDK 格式
                        val sdkPermissionUpdates = response.permissionUpdates?.map { update ->
                            logger.info("📝 [canUseTool] 权限更新: type=${update.type}, destination=${update.destination}")
                            // 非会话级权限更新需要持久化（TODO: 实现持久化服务）
                            if (update.destination != PermissionUpdateDestination.SESSION) {
                                logger.info("⚠️ [canUseTool] 非会话级权限更新暂未实现持久化: ${update.destination}")
                            }
                            update.toSdkPermissionUpdate()
                        }
                        logger.info("✅ [canUseTool] 用户已授权: toolName=$toolName, toolUseId=$toolUseId, permissionUpdates=${sdkPermissionUpdates?.size ?: 0}")
                        PermissionResultAllow(
                            updatedInput = input,
                            updatedPermissions = sdkPermissionUpdates
                        )
                    } else {
                        val reason = response.denyReason ?: "用户拒绝授权"
                        logger.info("❌ [canUseTool] 用户拒绝授权: toolName=$toolName, toolUseId=$toolUseId, reason=$reason")
                        PermissionResultDeny(message = reason)
                    }
                } catch (e: Exception) {
                    logger.warning("⚠️ [canUseTool] 权限请求失败: toolName=$toolName, error=${e.message}")
                    PermissionResultDeny(message = "权限请求失败: ${e.message}")
                }
            } else {
                logger.info("⚠️ [canUseTool] 无 clientCaller，默认允许: toolName=$toolName")
                PermissionResultAllow(updatedInput = input)
            }
        }

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
            canUseTool = canUseToolCallback,
            continueConversation = options.continueConversation ?: false,
            resume = options.resumeSessionId,
            maxThinkingTokens = maxThinkingTokens,
            extraArgs = extraArgs,
            mcpServers = mcpServers
        )

        return ClaudeOverrides(options = claudeOptions)
    }

    private fun buildCodexOverrides(
        model: String?,
        options: RpcConnectOptions
    ): CodexOverrides {
        val codexDefaults = serviceConfig.codex

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

    private fun UiStreamEvent.toRpcMessage(provider: AiAgentProvider): RpcMessage {
        val rpcProvider = provider.toRpcProvider()

        return when (this) {
            is UiMessageStart -> {
                nextContentIndex = content?.size ?: 0
                toolContentIndex.clear()

                wrapAsStreamEvent(
                    RpcMessageStartEvent(
                        message = RpcMessageStartInfo(
                            id = messageId,
                            content = content?.map { it.toRpcContentBlock() }
                        )
                    ),
                    rpcProvider
                )
            }

            is UiTextDelta -> {
                val event = wrapAsStreamEvent(
                    RpcContentBlockDeltaEvent(
                        index = index,  // 使用传递的 index
                        delta = RpcTextDelta(text = text)
                    ),
                    rpcProvider
                )
                if (nextContentIndex <= index) nextContentIndex = index + 1
                event
            }

            is UiThinkingDelta -> {
                val event = wrapAsStreamEvent(
                    RpcContentBlockDeltaEvent(
                        index = index,  // 使用传递的 index
                        delta = RpcThinkingDelta(thinking = thinking)
                    ),
                    rpcProvider
                )
                if (nextContentIndex <= index) nextContentIndex = index + 1
                event
            }

            is UiTextStart -> {
                // 确保 nextContentIndex 与 index 同步
                if (nextContentIndex <= index) nextContentIndex = index + 1
                wrapAsStreamEvent(
                    RpcContentBlockStartEvent(
                        index = index,
                        contentBlock = RpcTextBlock(text = "")
                    ),
                    rpcProvider
                )
            }

            is UiThinkingStart -> {
                if (nextContentIndex <= index) nextContentIndex = index + 1
                wrapAsStreamEvent(
                    RpcContentBlockStartEvent(
                        index = index,
                        contentBlock = RpcThinkingBlock(thinking = "")
                    ),
                    rpcProvider
                )
            }

            is UiToolStart -> {
                val toolTypeEnum = ToolType.fromToolName(toolName)
                val index = toolContentIndex.getOrPut(toolId) { nextContentIndex++ }
                wrapAsStreamEvent(
                    RpcContentBlockStartEvent(
                        index = index,
                        contentBlock = RpcToolUseBlock(
                            id = toolId,
                            toolName = toolName,
                            toolType = toolTypeEnum.type,
                            input = inputPreview?.let { kotlinx.serialization.json.JsonPrimitive(it) },
                            status = RpcContentStatus.IN_PROGRESS
                        )
                    ),
                    rpcProvider
                )
            }

            is UiToolProgress -> {
                val index = toolContentIndex[toolId] ?: 0
                wrapAsStreamEvent(
                    RpcContentBlockDeltaEvent(
                        index = index,
                        delta = RpcInputJsonDelta(partialJson = outputPreview ?: "")
                    ),
                    rpcProvider
                )
            }

            is UiToolComplete -> {
                val index = toolContentIndex[toolId] ?: 0
                wrapAsStreamEvent(
                    RpcContentBlockStopEvent(index = index),
                    rpcProvider
                )
            }

            is UiMessageComplete -> wrapAsStreamEvent(
                RpcMessageDeltaEvent(usage = usage?.toRpcUsage()),
                rpcProvider
            )

            is UiUserMessage -> RpcUserMessage(
                message = RpcMessageContent(
                    content = content.map { it.toRpcContentBlock() }
                ),
                provider = rpcProvider
            )

            is UiAssistantMessage -> {
                println("🔍 [toRpcMessage] UiAssistantMessage: content.size=${content.size}")
                content.forEachIndexed { idx, block ->
                    println("🔍 [toRpcMessage] UiAssistantMessage content[$idx]: type=${block::class.simpleName}, ${if (block is ToolUseContent) "input=${block.input}" else ""}")
                }
                RpcAssistantMessage(
                    message = RpcMessageContent(
                        content = content.map { it.toRpcContentBlock() }
                    ),
                    provider = rpcProvider
                )
            }

            is UiResultMessage -> RpcResultMessage(
                subtype = subtype,  // 保留原始 subtype（如 "error_during_execution"）
                durationMs = durationMs,
                durationApiMs = durationApiMs,
                isError = isError,
                numTurns = numTurns,
                sessionId = sessionId,
                totalCostUsd = totalCostUsd,
                usage = usage,
                result = result,
                provider = rpcProvider
            )

            is UiError -> RpcErrorMessage(
                message = message,
                provider = rpcProvider
            )
        }
    }

    private fun wrapAsStreamEvent(event: RpcStreamEventData, provider: RpcProvider): RpcStreamEvent {
        streamEventCounter++
        return RpcStreamEvent(
            uuid = "evt-${sessionId.take(8)}-$streamEventCounter",
            sessionId = sessionId,
            event = event,
            provider = provider
        )
    }

    private fun UnifiedContentBlock.toRpcContentBlock(): RpcContentBlock = when (this) {
        is TextContent -> RpcTextBlock(text = text)
        is ImageContent -> RpcImageBlock(source = RpcImageSource(type = "base64", mediaType = mediaType, data = data))
        is ThinkingContent -> RpcThinkingBlock(thinking = thinking, signature = signature)
        is ToolUseContent -> {
            val toolTypeEnum = ToolType.fromToolName(name)
            logger.info("🔍 [toRpcContentBlock] ToolUseContent: id=$id, name=$name, inputType=${input?.javaClass?.simpleName}, input=${input?.toString()?.take(200)}")
            RpcToolUseBlock(
                id = id,
                toolName = name,
                toolType = toolTypeEnum.type,
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
     * 灏?RPC 鍐呭鍧楄浆鎹负缁熶竴鍐呭鍧楋紙鐢ㄤ簬鐢ㄦ埛杈撳叆锛?     */
    private fun RpcContentBlock.toUnifiedContentBlock(): UnifiedContentBlock? = when (this) {
        is RpcTextBlock -> TextContent(text = text)
        is RpcImageBlock -> source.data?.let { data ->
            ImageContent(data = data, mediaType = source.mediaType)
        }
        is RpcThinkingBlock -> ThinkingContent(thinking = thinking, signature = signature)
        // 浠ヤ笅绫诲瀷涓嶅簲璇ュ嚭鐜板湪鐢ㄦ埛杈撳叆涓紝杩斿洖 null
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

    // ==================== 鑳藉姏鐩稿叧杞崲鍑芥暟 ====================

    /**
     * 灏?SDK AgentCapabilities 杞崲涓?RPC RpcCapabilities
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
     * 灏?SDK PermissionMode 杞崲涓?RPC RpcPermissionMode
     */
    private fun SdkPermissionMode.toRpcPermissionMode(): RpcPermissionMode = when (this) {
        SdkPermissionMode.DEFAULT -> RpcPermissionMode.DEFAULT
        SdkPermissionMode.ACCEPT_EDITS -> RpcPermissionMode.ACCEPT_EDITS
        SdkPermissionMode.BYPASS_PERMISSIONS -> RpcPermissionMode.BYPASS_PERMISSIONS
        SdkPermissionMode.PLAN -> RpcPermissionMode.PLAN
        SdkPermissionMode.DONT_ASK -> RpcPermissionMode.DONT_ASK
    }

    /**
     * 灏?RPC RpcPermissionMode 杞崲涓?SDK PermissionMode锛堢敤浜?setPermissionMode锛?     */
    private fun RpcPermissionMode.toSdkPermissionModeInternal(): SdkPermissionMode = when (this) {
        RpcPermissionMode.DEFAULT -> SdkPermissionMode.DEFAULT
        RpcPermissionMode.ACCEPT_EDITS -> SdkPermissionMode.ACCEPT_EDITS
        RpcPermissionMode.BYPASS_PERMISSIONS -> SdkPermissionMode.BYPASS_PERMISSIONS
        RpcPermissionMode.PLAN -> SdkPermissionMode.PLAN
        RpcPermissionMode.DONT_ASK -> SdkPermissionMode.DONT_ASK
    }
}









