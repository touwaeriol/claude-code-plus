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
import com.asakii.claude.agent.sdk.exceptions.ClientNotConnectedException
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.server.rsocket.RSocketErrorCodes
import io.rsocket.kotlin.RSocketError
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
import com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.SandboxMode
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.rpc.api.*
import com.asakii.rpc.proto.RequestPermissionRequest
import com.asakii.rpc.proto.PermissionUpdate as ProtoPermissionUpdate
import com.asakii.rpc.proto.PermissionRuleValue as ProtoPermissionRuleValue
import com.asakii.rpc.proto.PermissionBehavior as ProtoPermissionBehavior
import com.asakii.rpc.proto.PermissionUpdateType as ProtoPermissionUpdateType
import com.asakii.rpc.proto.PermissionUpdateDestination as ProtoPermissionUpdateDestination
import com.asakii.rpc.proto.PermissionMode as ProtoPermissionMode
import com.asakii.server.config.AiAgentServiceConfig
import com.asakii.server.mcp.PermissionResponse
import com.asakii.server.mcp.PermissionUpdate as McpPermissionUpdate
import com.asakii.server.mcp.PermissionUpdateDestination
import com.asakii.server.mcp.PermissionUpdateType as McpPermissionUpdateType
import com.asakii.server.mcp.PermissionBehavior as McpPermissionBehavior
import com.asakii.server.mcp.PermissionMode as McpPermissionMode
import com.asakii.server.mcp.PermissionRuleValue as McpPermissionRuleValue
import com.asakii.server.mcp.UserInteractionMcpServer
import com.asakii.server.mcp.JetBrainsMcpServerProvider
import com.asakii.server.mcp.DefaultJetBrainsMcpServerProvider
import com.asakii.server.logging.StandaloneLogging
import com.asakii.server.logging.asyncInfo
import com.asakii.server.settings.ClaudeSettingsLoader
import mu.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Path
import java.util.UUID
import com.asakii.server.history.HistoryJsonlLoader

/**
 * AI Agent 缁熶竴 RPC 鏈嶅姟瀹炵幇銆? *
 * 姣忎釜 WebSocket 杩炴帴瀵瑰簲璇ョ被鐨勪竴涓柊瀹炰緥锛屽疄渚嬪唴閮ㄧ淮鎶ょ粺涓€ SDK 瀹㈡埛绔互鍙? * 褰撳墠杩炴帴鐨勯厤缃笌鍘嗗彶浜嬩欢銆? */
class AiAgentRpcServiceImpl(
    private val ideTools: IdeTools,
    private val clientCaller: ClientCaller? = null,
    private val jetBrainsMcpServerProvider: JetBrainsMcpServerProvider = DefaultJetBrainsMcpServerProvider,
    private val serviceConfigProvider: () -> AiAgentServiceConfig = { AiAgentServiceConfig() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : AiAgentRpcService {

    // 使用 server.log 专用 logger（SDK 日志）
    private val sdkLog = KotlinLogging.logger(StandaloneLogging.SDK_LOGGER)
    private val jsonPretty = Json { prettyPrint = true; ignoreUnknownKeys = true }
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
    private var currentProvider: AiAgentProvider = AiAgentProvider.CLAUDE  // 默认值，connect 时会根据配置更新
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

    // 连接超时时间（毫秒）- Claude CLI 启动可能需要一些时间
    private val connectTimeoutMs = 30_000L

    override suspend fun connect(options: RpcConnectOptions?): RpcConnectResult {
        sdkLog.info("🔌 [SDK] 建立会话: sessionId=$sessionId")
        val normalizedOptions = options ?: lastConnectOptions ?: RpcConnectOptions()
        sdkLog.debug("🔌 [SDK] 连接选项: provider=${normalizedOptions.provider}, model=${normalizedOptions.model}, permissionMode=${normalizedOptions.permissionMode}")

        val connectOptions = buildConnectOptions(normalizedOptions)
        currentProvider = connectOptions.provider

        disconnectInternal()

        sdkLog.info("🔌 [SDK] 创建 ${connectOptions.provider} 客户端...")
        val newClient = UnifiedAgentClientFactory.create(connectOptions.provider)

        // 添加超时保护，避免无限阻塞
        try {
            withTimeout(connectTimeoutMs) {
                newClient.connect(connectOptions)
            }
        } catch (e: TimeoutCancellationException) {
            sdkLog.error("❌ [SDK] 连接超时 (${connectTimeoutMs}ms)，请检查网络或 Claude CLI 状态")
            throw RuntimeException("连接超时：Claude CLI 未能在 ${connectTimeoutMs / 1000} 秒内启动", e)
        }
        client = newClient

        val rpcProvider = currentProvider.toRpcProvider()
        val resolvedSystemPrompt = (connectOptions.systemPrompt as? String?) ?: normalizedOptions.systemPrompt
        lastConnectOptions = normalizedOptions.copy(
            provider = rpcProvider,
            model = connectOptions.model,
            systemPrompt = resolvedSystemPrompt,
            metadata = connectOptions.metadata
        )

        sdkLog.info("✅ [SDK] 已连接: provider=${connectOptions.provider}, model=${connectOptions.model ?: "default"}")

        val capabilities = newClient.getCapabilities().toRpcCapabilities()
        sdkLog.debug("✅ [SDK] 能力: canInterrupt=${capabilities.canInterrupt}, canThink=${capabilities.canThink}")

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

    override fun query(message: String): Flow<RpcMessage> {
        sdkLog.info("📤 [SDK] query: message=${message.take(200)}${if (message.length > 200) "..." else ""}")
        return executeTurn { unifiedClient ->
            unifiedClient.sendMessage(
                AgentMessageInput(text = message, sessionId = sessionId)
            )
        }
    }

    override fun queryWithContent(content: List<RpcContentBlock>): Flow<RpcMessage> {
        sdkLog.info("📤 [SDK] queryWithContent: blocks=${content.size}")
        content.forEachIndexed { idx, block ->
            when (block) {
                is RpcTextBlock -> sdkLog.debug("📤 [SDK]   [$idx] TextBlock: ${block.text.take(100)}...")
                is RpcImageBlock -> sdkLog.debug("📤 [SDK]   [$idx] ImageBlock: ${block.source.mediaType}")
                else -> sdkLog.debug("📤 [SDK]   [$idx] ${block::class.simpleName}")
            }
        }
        return executeTurn { unifiedClient ->
            val unifiedContent = content.mapNotNull { it.toUnifiedContentBlock() }
            unifiedClient.sendMessage(
                AgentMessageInput(content = unifiedContent, sessionId = sessionId)
            )
        }
    }

    override suspend fun interrupt(): RpcStatusResult {
        sdkLog.info("⏹️ [SDK] 中断当前回合")
        // 直接调用 SDK 的 interrupt，不再等待 query 流的完成信号
        client?.interrupt()
        sdkLog.info("✅ [SDK] interrupt 请求已提交")
        return RpcStatusResult(status = RpcSessionStatus.INTERRUPTED)
    }

    override suspend fun runInBackground(): RpcStatusResult {
        sdkLog.info("🔄 [SDK] 将任务移到后台运行")
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")
        activeClient.runInBackground()
        sdkLog.info("✅ [SDK] runInBackground 请求已提交")
        return RpcStatusResult(status = RpcSessionStatus.CONNECTED)
    }

    override suspend fun setMaxThinkingTokens(maxThinkingTokens: Int?): RpcSetMaxThinkingTokensResult {
        sdkLog.info("🧠 [SDK] 设置思考 token 上限: $maxThinkingTokens")
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")
        activeClient.setMaxThinkingTokens(maxThinkingTokens)
        sdkLog.info("✅ [SDK] setMaxThinkingTokens 请求已提交: $maxThinkingTokens")
        return RpcSetMaxThinkingTokensResult(maxThinkingTokens = maxThinkingTokens)
    }

    override suspend fun disconnect(): RpcStatusResult {
        sdkLog.info("馃攲 [AI-Agent] 鏂紑浼氳瘽: $sessionId")
        disconnectInternal()
        return RpcStatusResult(status = RpcSessionStatus.DISCONNECTED)
    }

    override suspend fun setModel(model: String): RpcSetModelResult {
        sdkLog.info("鈿欙笍 [AI-Agent] 鍒囨崲妯″瀷 -> $model")
        val base = lastConnectOptions ?: RpcConnectOptions()
        val updated = base.copy(model = model)
        connect(updated)
        return RpcSetModelResult(model = model)
    }

    override suspend fun setPermissionMode(mode: RpcPermissionMode): RpcSetPermissionModeResult {
        sdkLog.info("鈿欙笍 [AI-Agent] 鍒囨崲鏉冮檺妯″紡 -> $mode")
        val activeClient = client ?: error("AI Agent 灏氭湭杩炴帴锛岃鍏堣皟鐢?connect()")

        // 灏?RPC 鏉冮檺妯″紡杞崲涓?SDK 鏉冮檺妯″紡
        val sdkMode = mode.toSdkPermissionModeInternal()
        activeClient.setPermissionMode(sdkMode)

        sdkLog.info("鉁?[AI-Agent] 鏉冮檺妯″紡宸插垏鎹负: $mode")
        return RpcSetPermissionModeResult(mode = mode)
    }

    override suspend fun getHistory(): RpcHistory =
        RpcHistory(messages = messageHistory.toList())

    override suspend fun getHistorySessions(maxResults: Int, offset: Int): RpcHistorySessionsResult {
        sdkLog.info("📋 [AI-Agent] 获取历史会话列表 (offset=$offset, maxResults=$maxResults)")
        val projectPath = ideTools.getProjectPath()
        sdkLog.info("📋 [AI-Agent] 项目路径: $projectPath")
        val sessions = ClaudeSessionScanner.scanHistorySessions(projectPath, maxResults, offset)
        sdkLog.info("📋 [AI-Agent] 找到 ${sessions.size} 个历史会话")
        return RpcHistorySessionsResult(
            sessions = sessions.map { meta ->
                // 为每个会话加载 customTitle（从 JSONL 文件尾部高效查找）
                val customTitle = HistoryJsonlLoader.findCustomTitle(meta.sessionId, meta.projectPath)
                sdkLog.info("📋 [AI-Agent] 会话 ${meta.sessionId.take(8)}... customTitle=${customTitle ?: "(无)"}")
                RpcHistorySession(
                    sessionId = meta.sessionId,
                    firstUserMessage = meta.firstUserMessage,
                    timestamp = meta.timestamp,
                    messageCount = meta.messageCount,
                    projectPath = meta.projectPath,
                    customTitle = customTitle
                )
            }
        )
    }

        private fun executeTurn(block: suspend (UnifiedAgentClient) -> Unit): Flow<RpcMessage> {
        // 检查客户端状态
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")

        return channelFlow {
            // 创建完成信号
            queryCompletion = CompletableDeferred()

            streamEventCounter = 0
            nextContentIndex = 0
            toolContentIndex.clear()
            sdkLog.info("[executeTurn] start (sessionId=$sessionId)")

            val collectorReady = kotlinx.coroutines.CompletableDeferred<Unit>()

            val collector = launch {
                collectorReady.complete(Unit)

                var eventCount = 0
                try {
                    sdkLog.info("[executeTurn] collecting streamEvents()")
                    activeClient.streamEvents().collect { event ->
                        eventCount++
                        try {
                            val eventType = event::class.simpleName
                            // 记录完整事件内容（格式化在日志线程执行）
                            sdkLog.asyncInfo { "[executeTurn] #$eventCount $eventType: ${formatUiEvent(event)}" }

                            val rpcEvent = event.toRpcMessage(currentProvider)

                            messageHistory.add(rpcEvent)

                            try {
                                val rpcProvider = currentProvider.toRpcProvider()
                                send(rpcEvent)
                                sdkLog.info("[executeTurn] event #$eventCount ($eventType) sent")

                                when (event) {
                                    is UiMessageComplete -> {
                                        val stopEvent = wrapAsStreamEvent(RpcMessageStopEvent(), rpcProvider)
                                        messageHistory.add(stopEvent)
                                        send(stopEvent)
                                        sdkLog.info("[executeTurn] appended message_stop")
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
                                        sdkLog.info("[executeTurn] tool result message sent toolId=${event.toolId}")
                                    }

                                    else -> {}
                                }
                            } catch (e: ClosedSendChannelException) {
                                sdkLog.warn("[executeTurn] channel closed, stop collecting")
                                cancel()
                                return@collect
                            } catch (e: Exception) {
                                sdkLog.error("[executeTurn] send event failed #$eventCount: ${e.message}")
                                e.printStackTrace()
                            }

                            if (event is UiResultMessage) {
                                sdkLog.info("[executeTurn] got result event, complete query and cancel collector")
                                // 收到 result 消息后立即标记完成，让 interrupt 的 await 能及时返回
                                queryCompletion?.complete(Unit)
                                cancel()
                            }
                            if (event is UiError) {
                                sdkLog.error("[executeTurn] got error event, complete query and cancel collector")
                                queryCompletion?.complete(Unit)
                                cancel()
                            }
                        } catch (e: Exception) {
                            sdkLog.error("[executeTurn] handle stream event error #$eventCount: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                    sdkLog.info("[executeTurn] streamEvents collected, total $eventCount")
                } catch (e: CancellationException) {
                    sdkLog.info("[executeTurn] collector cancelled")
                    throw e
                } catch (e: Exception) {
                    sdkLog.error("[executeTurn] collect stream events error: ${e.message}")
                    e.printStackTrace()
                    throw e
                }
            }

            collectorReady.await()
            sdkLog.info("[executeTurn] collector ready, run block")

            try {
                block(activeClient)
                sdkLog.info("[executeTurn] block done")
            } catch (e: ClientNotConnectedException) {
                // 客户端未连接异常，转换为自定义 RSocket 错误码
                sdkLog.warn("[executeTurn] 客户端未连接: ${e.message}")
                collector.cancel()
                throw RSocketError.Custom(RSocketErrorCodes.NOT_CONNECTED, e.message ?: "Client not connected")
            } catch (t: Throwable) {
                // 检查是否是包装的 ClientNotConnectedException
                if (t.cause is ClientNotConnectedException) {
                    sdkLog.warn("[executeTurn] 客户端未连接 (wrapped): ${t.cause?.message}")
                    collector.cancel()
                    throw RSocketError.Custom(RSocketErrorCodes.NOT_CONNECTED, t.cause?.message ?: "Client not connected")
                }
                sdkLog.error("[executeTurn] block failed: ${t.message}")
                collector.cancel()
                throw t
            }

            try {
                collector.join()
                sdkLog.info("[executeTurn] collector completed")
            } catch (_: CancellationException) {
                sdkLog.info("[executeTurn] collector cancelled normally")
            }

            sdkLog.info("[executeTurn] done (sessionId=$sessionId)")
        }.onCompletion {
            // 🔧 Flow 结束时标记完成，让 interrupt 的 await 返回
            queryCompletion?.complete(Unit)
            queryCompletion = null
            sdkLog.info("[executeTurn] Flow completed, queryCompletion signaled")
        }
    }

    override fun loadHistory(
        sessionId: String?,
        projectPath: String?,
        offset: Int,
        limit: Int
    ): RpcHistoryResult {
        val targetSession = sessionId ?: lastConnectOptions?.sessionId ?: this@AiAgentRpcServiceImpl.sessionId
        val project = projectPath?.takeIf { it.isNotBlank() } ?: ideTools.getProjectPath()

        // 获取可用的总消息数（快照）
        val availableCount = HistoryJsonlLoader.countLines(targetSession, project)

        // 加载历史消息（List<UiStreamEvent>）
        val historyEvents = HistoryJsonlLoader.loadHistoryMessages(targetSession, project, offset, limit)

        // 复用 toRpcMessage() 转换成 RpcMessage
        val rpcMessages = historyEvents.map { uiEvent ->
            uiEvent.toRpcMessage(currentProvider)
        }

        // 返回包装结果
        return RpcHistoryResult(
            messages = rpcMessages,
            offset = offset,
            count = rpcMessages.size,
            availableCount = availableCount
        )
    }

    override suspend fun getHistoryMetadata(
        sessionId: String?,
        projectPath: String?
    ): RpcHistoryMetadata {
        val targetSession = sessionId ?: lastConnectOptions?.sessionId ?: this.sessionId
        val project = projectPath?.takeIf { it.isNotBlank() } ?: ideTools.getProjectPath()
        val totalLines = HistoryJsonlLoader.countLines(targetSession, project)
        // 从文件尾部高效查找 custom-title（/rename 命令设置的自定义标题）
        val customTitle = HistoryJsonlLoader.findCustomTitle(targetSession, project)

        return RpcHistoryMetadata(
            totalLines = totalLines,
            sessionId = targetSession,
            projectPath = project,
            customTitle = customTitle
        )
    }

    override suspend fun truncateHistory(
        sessionId: String,
        messageUuid: String,
        projectPath: String
    ): RpcTruncateHistoryResult {
        sdkLog.info("✂️ [SDK] 截断历史: sessionId=$sessionId, messageUuid=$messageUuid, projectPath=$projectPath")
        return try {
            val remainingLines = HistoryJsonlLoader.truncateHistory(
                sessionId = sessionId,
                projectPath = projectPath,
                messageUuid = messageUuid
            )
            sdkLog.info("✅ [SDK] 历史截断成功: remainingLines=$remainingLines")
            RpcTruncateHistoryResult(
                success = true,
                remainingLines = remainingLines
            )
        } catch (e: Exception) {
            sdkLog.error("❌ [SDK] 历史截断失败: ${e.message}", e)
            RpcTruncateHistoryResult(
                success = false,
                remainingLines = -1,
                error = e.message ?: "Unknown error"
            )
        }
    }

    private suspend fun disconnectInternal() {
        try {
            client?.disconnect()
        } catch (t: Throwable) {
            sdkLog.warn("鈿狅笍 [AI-Agent] 鏂紑瀹㈡埛绔椂鍑洪敊: ${t.message}")
        } finally {
            client = null
        }
    }

    private fun buildConnectOptions(options: RpcConnectOptions): AiAgentConnectOptions {
        // 每次 connect 时调用 provider 获取最新配置
        val serviceConfig = serviceConfigProvider()
        sdkLog.info("🔧 [buildConnectOptions] 获取最新配置: enableUserInteractionMcp=${serviceConfig.claude.enableUserInteractionMcp}, enableJetBrainsMcp=${serviceConfig.claude.enableJetBrainsMcp}")

        val provider = options.provider.toSdkProvider(serviceConfig.defaultProvider)
        val model = options.model ?: serviceConfig.defaultModel
        val systemPrompt = options.systemPrompt ?: serviceConfig.defaultSystemPrompt
        val initialPrompt = options.initialPrompt
        val sessionHint = options.sessionId
        val resume = options.resumeSessionId
        val metadata = options.metadata.ifEmpty { emptyMap() }

        val claudeOverrides = buildClaudeOverrides(model, systemPrompt, options, metadata, serviceConfig)
        val codexOverrides = buildCodexOverrides(model, options, serviceConfig)

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
        metadata: Map<String, String>,
        serviceConfig: AiAgentServiceConfig
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

        // 注册 MCP Server（根据配置决定是否启用）
        val mcpServers = mutableMapOf<String, Any>()

        // 添加用户交互 MCP Server（如果启用）
        if (defaults.enableUserInteractionMcp) {
            mcpServers["user_interaction"] = userInteractionServer
            sdkLog.info("✅ [buildClaudeOverrides] 已添加 User Interaction MCP Server")
        } else {
            sdkLog.info("⏭️ [buildClaudeOverrides] User Interaction MCP Server 已禁用")
        }

        // 添加 JetBrains MCP Server（如果启用且可用）
        if (defaults.enableJetBrainsMcp) {
            jetBrainsMcpServerProvider.getServer()?.let { jetbrainsMcp ->
                mcpServers["jetbrains"] = jetbrainsMcp
                sdkLog.info("✅ [buildClaudeOverrides] 已添加 JetBrains MCP Server")
            }
        } else {
            sdkLog.info("⏭️ [buildClaudeOverrides] JetBrains MCP Server 已禁用")
        }

        // 添加 Context7 MCP Server（如果启用）
        if (defaults.enableContext7Mcp) {
            val context7Config = mutableMapOf<String, Any>(
                "type" to "http",
                "url" to "https://mcp.context7.com/mcp"
            )
            // 如果用户配置了 API Key，则添加到 headers 中
            defaults.context7ApiKey?.takeIf { it.isNotBlank() }?.let { apiKey ->
                context7Config["headers"] = mapOf("CONTEXT7_API_KEY" to apiKey)
                sdkLog.info("✅ [buildClaudeOverrides] 已添加 Context7 MCP Server (with API key)")
            } ?: run {
                sdkLog.info("✅ [buildClaudeOverrides] 已添加 Context7 MCP Server (without API key)")
            }
            mcpServers["context7"] = context7Config
        } else {
            sdkLog.info("⏭️ [buildClaudeOverrides] Context7 MCP Server 已禁用")
        }

        // 从 IdeTools 获取子代理定义（如 JetBrains 专用的代码探索代理）
        val agents = ideTools.getAgentDefinitions()
        if (agents.isNotEmpty()) {
            sdkLog.info("📦 [buildClaudeOverrides] 加载了 ${agents.size} 个自定义代理: ${agents.keys.joinToString()}")
        } else {
            sdkLog.warn("⚠️ [buildClaudeOverrides] 未加载到任何自定义代理 (ideTools类型=${ideTools::class.simpleName})")
        }

        // 收集所有 MCP 服务器的系统提示词追加内容
        // 使用 appendSystemPromptFile 追加，不会替换 Claude Code 默认提示词
        var mcpSystemPromptAppendix = buildMcpSystemPromptAppendix(mcpServers)

        // 如果启用了 Context7 MCP，追加其系统提示词
        if (defaults.enableContext7Mcp) {
            val context7Instructions = loadContext7Instructions()
            if (context7Instructions.isNotBlank()) {
                mcpSystemPromptAppendix = if (mcpSystemPromptAppendix.isNotBlank()) {
                    "$mcpSystemPromptAppendix\n\n$context7Instructions"
                } else {
                    context7Instructions
                }
                sdkLog.info("📝 [buildClaudeOverrides] 已追加 Context7 系统提示词")
            }
        }

        // canUseTool 回调：通过 RPC 调用前端获取用户授权（带 tool_use_id 和 permissionSuggestions）
        val canUseToolCallback: CanUseTool = { toolName, input, toolUseId, context ->
            sdkLog.info("🔐 [canUseTool] 请求授权: toolName=$toolName, toolUseId=$toolUseId, suggestions=${context.suggestions.size}")
            val caller = clientCaller
            if (caller != null) {
                try {
                    // 构建 Protobuf 请求
                    val protoRequest = RequestPermissionRequest.newBuilder().apply {
                        this.toolName = toolName
                        this.inputJson = com.google.protobuf.ByteString.copyFrom(
                            Json.encodeToString(JsonObject.serializer(), JsonObject(input)).toByteArray(Charsets.UTF_8)
                        )
                        toolUseId?.let { this.toolUseId = it }
                        context.suggestions.forEach { suggestion ->
                            addPermissionSuggestions(suggestion.toProtoPermissionUpdate())
                        }
                    }.build()

                    // 调用前端并解析 Protobuf 响应
                    val protoResponse = caller.callRequestPermission(protoRequest)

                    // 转换 Protobuf 响应为本地类型
                    val response = PermissionResponse(
                        approved = protoResponse.approved,
                        permissionUpdates = protoResponse.permissionUpdatesList.map { it.toMcpPermissionUpdate() },
                        denyReason = if (protoResponse.hasDenyReason()) protoResponse.denyReason else null
                    )
                    if (response.approved) {
                        // 转换权限更新为 SDK 格式
                        val sdkPermissionUpdates = response.permissionUpdates?.map { update ->
                            sdkLog.info("📝 [canUseTool] 权限更新: type=${update.type}, destination=${update.destination}")
                            // 非会话级权限更新需要持久化（TODO: 实现持久化服务）
                            if (update.destination != PermissionUpdateDestination.SESSION) {
                                sdkLog.info("⚠️ [canUseTool] 非会话级权限更新暂未实现持久化: ${update.destination}")
                            }
                            update.toSdkPermissionUpdate()
                        }
                        sdkLog.info("✅ [canUseTool] 用户已授权: toolName=$toolName, toolUseId=$toolUseId, permissionUpdates=${sdkPermissionUpdates?.size ?: 0}")
                        PermissionResultAllow(
                            updatedInput = input,
                            updatedPermissions = sdkPermissionUpdates
                        )
                    } else {
                        val reason = response.denyReason ?: "用户拒绝授权"
                        sdkLog.info("❌ [canUseTool] 用户拒绝授权: toolName=$toolName, toolUseId=$toolUseId, reason=$reason")
                        PermissionResultDeny(message = reason)
                    }
                } catch (e: Exception) {
                    sdkLog.warn("⚠️ [canUseTool] 权限请求失败: toolName=$toolName, error=${e.message}")
                    PermissionResultDeny(message = "权限请求失败: ${e.message}")
                }
            } else {
                sdkLog.info("⚠️ [canUseTool] 无 clientCaller，默认允许: toolName=$toolName")
                PermissionResultAllow(updatedInput = input)
            }
        }

        val claudeOptions = ClaudeAgentOptions(
            model = model,
            cwd = cwd,
            // systemPrompt 只在用户明确指定时才传递，null 则保留 Claude Code 默认提示词
            systemPrompt = systemPrompt,
            // MCP 追加内容通过 appendSystemPromptFile 传递，不会替换默认提示词
            appendSystemPromptFile = mcpSystemPromptAppendix.takeIf { it.isNotBlank() },
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
            replayUserMessages = options.replayUserMessages ?: false,
            maxThinkingTokens = maxThinkingTokens,
            extraArgs = extraArgs,
            // 动态收集所有 MCP 服务器声明的需要自动允许的工具
            allowedTools = buildMcpAllowedTools(mcpServers),
            mcpServers = mcpServers,
            // 自定义子代理定义（如 JetBrains 专用的代码探索代理）
            agents = agents.ifEmpty { null },
            // Node.js 可执行文件路径（用户配置 > 环境变量 > 默认 "node"）
            nodePath = defaults.nodePath,
            // Claude CLI settings.json 路径（用于加载环境变量等配置）
            settings = defaults.settings,
            // IDEA 文件同步 hooks（由 jetbrains-plugin 提供）
            hooks = defaults.ideaFileSyncHooks
        )

        return ClaudeOverrides(options = claudeOptions)
    }

    /**
     * 收集所有 MCP 服务器的系统提示词追加内容
     *
     * 遍历所有注册的 MCP 服务器，调用其 getSystemPromptAppendix() 方法，
     * 将所有非空的追加内容合并为一个字符串。
     *
     * @param mcpServers MCP 服务器映射（名称 -> 服务器实例）
     * @return 合并后的系统提示词追加内容
     */
    private fun buildMcpSystemPromptAppendix(mcpServers: Map<String, Any>): String {
        return mcpServers.values
            .filterIsInstance<com.asakii.claude.agent.sdk.mcp.McpServer>()
            .mapNotNull { server ->
                server.getSystemPromptAppendix()?.takeIf { it.isNotBlank() }
            }
            .joinToString("\n\n")
    }

    /**
     * 加载 Context7 MCP 的系统提示词
     *
     * @return Context7 系统提示词内容，加载失败返回空字符串
     */
    private fun loadContext7Instructions(): String {
        return try {
            val inputStream = javaClass.classLoader.getResourceAsStream("prompts/context7-mcp-instructions.md")
            inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            sdkLog.warn("⚠️ [loadContext7Instructions] 加载 Context7 提示词失败: ${e.message}")
            ""
        }
    }

    /**
     * 收集所有 MCP 服务器声明的需要自动允许的工具
     *
     * 遍历所有注册的 MCP 服务器，调用其 getAllowedTools() 方法，
     * 将工具名称转换为完整格式（mcp__{serverName}__{toolName}）后合并。
     *
     * @param mcpServers MCP 服务器映射（名称 -> 服务器实例）
     * @return 需要自动允许的工具列表（完整格式）
     */
    private fun buildMcpAllowedTools(mcpServers: Map<String, Any>): List<String> {
        return mcpServers.entries
            .mapNotNull { (serverName, server) ->
                (server as? com.asakii.claude.agent.sdk.mcp.McpServer)?.let { mcpServer ->
                    mcpServer.getAllowedTools().map { toolName ->
                        "mcp__${serverName}__$toolName"
                    }
                }
            }
            .flatten()
    }

    private fun buildCodexOverrides(
        model: String?,
        options: RpcConnectOptions,
        serviceConfig: AiAgentServiceConfig
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
                    rpcProvider,
                    parentToolUseId = parentToolUseId
                )
            }

            is UiToolProgress -> {
                val index = toolContentIndex[toolId] ?: 0
                wrapAsStreamEvent(
                    RpcContentBlockDeltaEvent(
                        index = index,
                        delta = RpcInputJsonDelta(partialJson = outputPreview ?: "")
                    ),
                    rpcProvider,
                    parentToolUseId = parentToolUseId
                )
            }

            is UiToolComplete -> {
                val index = toolContentIndex[toolId] ?: 0
                wrapAsStreamEvent(
                    RpcContentBlockStopEvent(index = index),
                    rpcProvider,
                    parentToolUseId = parentToolUseId
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
                provider = rpcProvider,
                isReplay = isReplay,
                parentToolUseId = parentToolUseId,
                uuid = uuid
            )

            is UiAssistantMessage -> {
                sdkLog.debug { "🔍 [toRpcMessage] UiAssistantMessage: content.size=${content.size}, parentToolUseId=$parentToolUseId, uuid=$uuid" }
                content.forEachIndexed { idx, block ->
                    sdkLog.debug { "🔍 [toRpcMessage] UiAssistantMessage content[$idx]: type=${block::class.simpleName}, ${if (block is ToolUseContent) "input=${block.input}" else ""}" }
                }
                RpcAssistantMessage(
                    id = id,
                    message = RpcMessageContent(
                        content = content.map { it.toRpcContentBlock() }
                    ),
                    provider = rpcProvider,
                    parentToolUseId = parentToolUseId,
                    uuid = uuid
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

            is UiStatusSystem -> RpcStatusSystemMessage(
                status = status,
                sessionId = sessionId,
                provider = rpcProvider
            )

            is UiCompactBoundary -> RpcCompactBoundaryMessage(
                sessionId = sessionId,
                compactMetadata = RpcCompactMetadata(
                    trigger = trigger,
                    preTokens = preTokens
                ),
                provider = rpcProvider
            )

            is UiSystemInit -> RpcSystemInitMessage(
                sessionId = sessionId,
                cwd = cwd,
                model = model,
                permissionMode = permissionMode,
                apiKeySource = apiKeySource,
                tools = tools,
                mcpServers = mcpServers?.map { RpcMcpServerInfo(it.name, it.status) },
                provider = rpcProvider
            )
        }
    }

    private fun wrapAsStreamEvent(
        event: RpcStreamEventData,
        provider: RpcProvider,
        parentToolUseId: String? = null
    ): RpcStreamEvent {
        streamEventCounter++
        return RpcStreamEvent(
            uuid = "evt-${sessionId.take(8)}-$streamEventCounter",
            sessionId = sessionId,
            event = event,
            parentToolUseId = parentToolUseId,
            provider = provider
        )
    }

    private fun UnifiedContentBlock.toRpcContentBlock(): RpcContentBlock = when (this) {
        is TextContent -> RpcTextBlock(text = text)
        is ImageContent -> RpcImageBlock(source = RpcImageSource(type = "base64", mediaType = mediaType, data = data))
        is ThinkingContent -> RpcThinkingBlock(thinking = thinking, signature = signature)
        is ToolUseContent -> {
            val toolTypeEnum = ToolType.fromToolName(name)
            sdkLog.info("🔍 [toRpcContentBlock] ToolUseContent: id=$id, name=$name, inputType=${input?.javaClass?.simpleName}, input=${input?.toString()?.take(200)}")
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
            isError = isError,
            agentId = agentId
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
        cacheCreationTokens = cacheCreationTokens,
        cacheReadTokens = cacheReadTokens,
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
        canResumeSession = canResumeSession,
        canRunInBackground = canRunInBackground
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

    // ==================== 日志格式化函数 ====================

    /**
     * 格式化 UiStreamEvent 为日志字符串（完整内容，不截断）
     */
    private fun formatUiEvent(event: UiStreamEvent): String = when (event) {
        is UiTextDelta -> "text=\"${event.text}\""
        is UiThinkingDelta -> "thinking=\"${event.thinking}\""
        is UiAssistantMessage -> "content=${formatContentBlocks(event.content)}"
        is UiUserMessage -> "content=${formatContentBlocks(event.content)}, isReplay=${event.isReplay}"
        is UiToolStart -> "toolId=${event.toolId}, toolName=${event.toolName}, toolType=${event.toolType}, inputPreview=${event.inputPreview}, parentToolUseId=${event.parentToolUseId}"
        is UiToolProgress -> "toolId=${event.toolId}, status=${event.status}, outputPreview=${event.outputPreview}, parentToolUseId=${event.parentToolUseId}"
        is UiToolComplete -> "toolId=${event.toolId}, result=${event.result}, parentToolUseId=${event.parentToolUseId}"
        is UiMessageStart -> "messageId=${event.messageId}, content=${event.content?.let { formatContentBlocks(it) }}"
        is UiMessageComplete -> "usage=${event.usage}"
        is UiResultMessage -> "subtype=${event.subtype}, isError=${event.isError}, numTurns=${event.numTurns}, result=${event.result}"
        is UiError -> "message=${event.message}"
        is UiTextStart -> "index=${event.index}"
        is UiThinkingStart -> "index=${event.index}"
        is UiStatusSystem -> "status=${event.status}, sessionId=${event.sessionId}"
        is UiCompactBoundary -> "sessionId=${event.sessionId}, trigger=${event.trigger}, preTokens=${event.preTokens}"
        is UiSystemInit -> "sessionId=${event.sessionId}, model=${event.model}, permissionMode=${event.permissionMode}"
    }

    /**
     * 格式化内容块列表（完整内容）
     */
    private fun formatContentBlocks(blocks: List<UnifiedContentBlock>): String {
        return blocks.joinToString("; ") { block ->
            when (block) {
                is TextContent -> "Text(\"${block.text}\")"
                is ThinkingContent -> "Thinking(\"${block.thinking}\")"
                is ImageContent -> "Image(mediaType=${block.mediaType}, dataLen=${block.data.length})"
                is ToolUseContent -> "ToolUse(id=${block.id}, name=${block.name}, input=${block.input})"
                is ToolResultContent -> "ToolResult(toolUseId=${block.toolUseId}, content=${block.content}, isError=${block.isError})"
                is CommandExecutionContent -> "Command(cmd=${block.command}, output=${block.output}, exitCode=${block.exitCode})"
                is FileChangeContent -> "FileChange(changes=${block.changes})"
                is McpToolCallContent -> "McpTool(server=${block.server}, tool=${block.tool}, args=${block.arguments}, result=${block.result})"
                is WebSearchContent -> "WebSearch(query=${block.query})"
                is TodoListContent -> "TodoList(items=${block.items})"
                is ErrorContent -> "Error(${block.message})"
            }
        }
    }
}

// ==================== Protobuf 转换扩展函数 ====================

/**
 * 将 SDK PermissionUpdate 转换为 Protobuf PermissionUpdate
 */
private fun SdkPermissionUpdate.toProtoPermissionUpdate(): ProtoPermissionUpdate {
    return ProtoPermissionUpdate.newBuilder().apply {
        type = when (this@toProtoPermissionUpdate.type) {
            SdkPermissionUpdateType.ADD_RULES -> ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_ADD_RULES
            SdkPermissionUpdateType.REPLACE_RULES -> ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_REPLACE_RULES
            SdkPermissionUpdateType.REMOVE_RULES -> ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_REMOVE_RULES
            SdkPermissionUpdateType.SET_MODE -> ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_SET_MODE
            SdkPermissionUpdateType.ADD_DIRECTORIES -> ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_ADD_DIRECTORIES
            SdkPermissionUpdateType.REMOVE_DIRECTORIES -> ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_REMOVE_DIRECTORIES
        }
        this@toProtoPermissionUpdate.rules?.forEach { rule ->
            addRules(ProtoPermissionRuleValue.newBuilder().apply {
                toolName = rule.toolName
                rule.ruleContent?.let { ruleContent = it }
            }.build())
        }
        this@toProtoPermissionUpdate.behavior?.let {
            behavior = when (it) {
                SdkPermissionBehavior.ALLOW -> ProtoPermissionBehavior.PERMISSION_BEHAVIOR_ALLOW
                SdkPermissionBehavior.DENY -> ProtoPermissionBehavior.PERMISSION_BEHAVIOR_DENY
                SdkPermissionBehavior.ASK -> ProtoPermissionBehavior.PERMISSION_BEHAVIOR_ASK
            }
        }
        this@toProtoPermissionUpdate.mode?.let {
            mode = when (it) {
                com.asakii.claude.agent.sdk.types.PermissionMode.DEFAULT -> ProtoPermissionMode.PERMISSION_MODE_DEFAULT
                com.asakii.claude.agent.sdk.types.PermissionMode.ACCEPT_EDITS -> ProtoPermissionMode.PERMISSION_MODE_ACCEPT_EDITS
                com.asakii.claude.agent.sdk.types.PermissionMode.PLAN -> ProtoPermissionMode.PERMISSION_MODE_PLAN
                com.asakii.claude.agent.sdk.types.PermissionMode.BYPASS_PERMISSIONS -> ProtoPermissionMode.PERMISSION_MODE_BYPASS_PERMISSIONS
                com.asakii.claude.agent.sdk.types.PermissionMode.DONT_ASK -> ProtoPermissionMode.PERMISSION_MODE_DONT_ASK
            }
        }
        this@toProtoPermissionUpdate.directories?.forEach { addDirectories(it) }
        this@toProtoPermissionUpdate.destination?.let {
            destination = when (it) {
                SdkPermissionUpdateDestination.USER_SETTINGS -> ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_USER_SETTINGS
                SdkPermissionUpdateDestination.PROJECT_SETTINGS -> ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_PROJECT_SETTINGS
                SdkPermissionUpdateDestination.LOCAL_SETTINGS -> ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_LOCAL_SETTINGS
                SdkPermissionUpdateDestination.SESSION -> ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_SESSION
            }
        }
    }.build()
}

/**
 * 将 Protobuf PermissionUpdate 转换为 MCP PermissionUpdate
 */
private fun ProtoPermissionUpdate.toMcpPermissionUpdate(): McpPermissionUpdate {
    return McpPermissionUpdate(
        type = when (type) {
            ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_ADD_RULES -> McpPermissionUpdateType.ADD_RULES
            ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_REPLACE_RULES -> McpPermissionUpdateType.REPLACE_RULES
            ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_REMOVE_RULES -> McpPermissionUpdateType.REMOVE_RULES
            ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_SET_MODE -> McpPermissionUpdateType.SET_MODE
            ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_ADD_DIRECTORIES -> McpPermissionUpdateType.ADD_DIRECTORIES
            ProtoPermissionUpdateType.PERMISSION_UPDATE_TYPE_REMOVE_DIRECTORIES -> McpPermissionUpdateType.REMOVE_DIRECTORIES
            else -> McpPermissionUpdateType.ADD_RULES
        },
        rules = rulesList.map { rule ->
            McpPermissionRuleValue(
                toolName = rule.toolName,
                ruleContent = if (rule.hasRuleContent()) rule.ruleContent else null
            )
        }.takeIf { it.isNotEmpty() },
        behavior = when (behavior) {
            ProtoPermissionBehavior.PERMISSION_BEHAVIOR_ALLOW -> McpPermissionBehavior.ALLOW
            ProtoPermissionBehavior.PERMISSION_BEHAVIOR_DENY -> McpPermissionBehavior.DENY
            ProtoPermissionBehavior.PERMISSION_BEHAVIOR_ASK -> McpPermissionBehavior.ASK
            else -> null
        },
        mode = when (mode) {
            ProtoPermissionMode.PERMISSION_MODE_DEFAULT -> McpPermissionMode.DEFAULT
            ProtoPermissionMode.PERMISSION_MODE_ACCEPT_EDITS -> McpPermissionMode.ACCEPT_EDITS
            ProtoPermissionMode.PERMISSION_MODE_PLAN -> McpPermissionMode.PLAN
            ProtoPermissionMode.PERMISSION_MODE_BYPASS_PERMISSIONS -> McpPermissionMode.BYPASS_PERMISSIONS
            ProtoPermissionMode.PERMISSION_MODE_DONT_ASK -> McpPermissionMode.DONT_ASK
            else -> null
        },
        directories = directoriesList.takeIf { it.isNotEmpty() },
        destination = when (destination) {
            ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_USER_SETTINGS -> PermissionUpdateDestination.USER_SETTINGS
            ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_PROJECT_SETTINGS -> PermissionUpdateDestination.PROJECT_SETTINGS
            ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_LOCAL_SETTINGS -> PermissionUpdateDestination.LOCAL_SETTINGS
            ProtoPermissionUpdateDestination.PERMISSION_UPDATE_DESTINATION_SESSION -> PermissionUpdateDestination.SESSION
            else -> null
        }
    )
}







