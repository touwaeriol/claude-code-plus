package com.asakii.server.rpc

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.McpSystemPromptContext
import com.asakii.ai.agent.sdk.capabilities.AgentCapabilities
import com.asakii.ai.agent.sdk.capabilities.AiPermissionMode as SdkPermissionMode
import com.asakii.ai.agent.sdk.client.AgentMessageInput
import com.asakii.ai.agent.sdk.client.PermissionDecision
import com.asakii.ai.agent.sdk.client.PermissionRequester
import com.asakii.ai.agent.sdk.client.UnifiedAgentClient
import com.asakii.ai.agent.sdk.client.UnifiedAgentClientFactory
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.ClaudeOverrides
import com.asakii.ai.agent.sdk.connect.CodexOverrides
import com.asakii.ai.agent.sdk.model.*
import com.asakii.claude.agent.sdk.exceptions.CLINotFoundException
import com.asakii.claude.agent.sdk.exceptions.ClientNotConnectedException
import com.asakii.claude.agent.sdk.exceptions.NodeNotFoundException
import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.claude.agent.sdk.types.McpHttpServerConfig
import com.asakii.claude.agent.sdk.types.McpServerConfig
import com.asakii.claude.agent.sdk.types.McpServerSpec
import com.asakii.claude.agent.sdk.types.McpStdioServerConfig
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
import com.asakii.codex.agent.sdk.ApprovalMode
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.ModelReasoningEffort
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
import com.asakii.server.mcp.McpHttpGateway
import com.asakii.server.mcp.UserInteractionMcpServer
import com.asakii.server.mcp.McpProviders
import com.asakii.server.mcp.McpServerWithConnectId
import com.asakii.server.services.FileContentCache
import com.asakii.logging.*
import com.asakii.server.settings.ClaudeSettingsLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
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
    private val mcpProviders: McpProviders = McpProviders.DEFAULT,
    private val mcpHttpGateway: McpHttpGateway? = null,  // 项目级 MCP HTTP 网关（仅 Codex 模式使用）
    private val serviceConfigProvider: () -> AiAgentServiceConfig = { AiAgentServiceConfig() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val connectId: String  // 由 RSocketHandler 传入，与 RSocket 连接生命周期绑定
) : AiAgentRpcService {

    // 使用 server.log 专用 logger（SDK 日志）
    private val sdkLog = getLogger("AiAgentRpcService")
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
    // connectId 现在通过构造函数传入，由 RSocketHandler 分配，与 RSocket 连接生命周期绑定

    // 🔧 追踪当前 query 的完成状态，用于 interrupt 同步等待
    private var queryCompletion: CompletableDeferred<Unit>? = null

    // 🔧 全局事件流（广播给所有订阅者，实现 Query/Result 分离）
    private val globalEventFlow = MutableSharedFlow<RpcMessage>(
        extraBufferCapacity = 64  // 缓冲区，避免阻塞发送者
    )

    // 用户交互 MCP Server（仅包含 AskUserQuestion，权限走 canUseTool 回调）
    private val userInteractionServer = UserInteractionMcpServer()

    private companion object {
        private val GLOBAL_MCP_PROVIDER = AiAgentProvider.CLAUDE
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
        sdkLog.info { "🔌 [SDK] 建立会话: sessionId=$sessionId" }
        var normalizedOptions = options ?: lastConnectOptions ?: RpcConnectOptions()
        sdkLog.debug { "🔌 [SDK] 连接选项: provider=${normalizedOptions.provider}, model=${normalizedOptions.model}, permissionMode=${normalizedOptions.permissionMode}" }

        // Codex sandbox 模式变更检测：如果 sandbox 模式改变，需要创建新线程而不是恢复旧线程
        // 因为 Codex 的 sandbox 参数只在 thread/start 时设置，resumeThread 不支持修改
        val lastSandbox = lastConnectOptions?.sandboxMode
        val newSandbox = normalizedOptions.sandboxMode
        if (lastSandbox != null && newSandbox != null && lastSandbox != newSandbox) {
            sdkLog.info { "🔄 [SDK] Codex sandbox 模式变更: $lastSandbox -> $newSandbox，清除 resumeSessionId 以创建新线程" }
            normalizedOptions = normalizedOptions.copy(resumeSessionId = null)
        }

        // connectId 现在由 RSocketHandler 分配，与 RSocket 连接生命周期绑定
        sdkLog.info { "[connect] connectId=$connectId (backend-assigned)" }

        disconnectInternal()

        val connectOptions = buildConnectOptions(normalizedOptions)

        // MCP 端点现在复用（不再清理），由 McpHttpGateway.registerServer(reuseExisting=true) 处理
        currentProvider = connectOptions.provider
        sdkLog.info { "[connect] provider=${connectOptions.provider}, model=${connectOptions.model ?: "default"}" }

        sdkLog.info { "🔌 [SDK] 创建 ${connectOptions.provider} 客户端..." }
        val permissionRequester = buildPermissionRequester()
        val newClient = UnifiedAgentClientFactory.create(connectOptions.provider, permissionRequester)
        sdkLog.info { "[connect] clientImpl=${newClient::class.qualifiedName}, clientProvider=${newClient.provider}" }

        // 添加超时保护，避免无限阻塞
        try {
            withTimeout(connectTimeoutMs) {
                newClient.connect(connectOptions)
            }
        } catch (e: NodeNotFoundException) {
            // Node.js 未找到或配置路径无效，转换为自定义 RSocket 错误码
            sdkLog.error { "❌ [SDK] Node.js 未找到: ${e.message}" }
            throw RSocketError.Custom(RSocketErrorCodes.NODE_NOT_FOUND, e.message ?: "Node.js not found")
        } catch (e: CLINotFoundException) {
            // Claude CLI 未找到，转换为自定义 RSocket 错误码
            sdkLog.error { "❌ [SDK] Claude CLI 未找到: ${e.message}" }
            throw RSocketError.Custom(RSocketErrorCodes.CLI_NOT_FOUND, e.message ?: "Claude CLI not found")
        } catch (e: TimeoutCancellationException) {
            val providerName = connectOptions.provider.name
            sdkLog.error { "❌ [SDK] 连接超时 (${connectTimeoutMs}ms)，请检查网络或 $providerName 状态" }
            throw RuntimeException("连接超时：$providerName 未能在 ${connectTimeoutMs / 1000} 秒内启动", e)
        }
        client = newClient
        sdkLog.info { "[connect] client connected: isConnected=${newClient.isConnected()}" }

        if (connectOptions.provider == AiAgentProvider.CODEX) {
            scope.launch {
                runCatching {
                    withTimeout(10_000L) { newClient.getMcpStatus() }
                }.onSuccess { statusList ->
                    val summary = statusList.joinToString { info -> "${info.name}:${info.status}" }
                    sdkLog.info { "[MCP] Codex status auto-check: ${statusList.size} server(s) -> $summary" }
                }.onFailure { e ->
                    sdkLog.warn { "[MCP] Codex status auto-check failed: ${e.message}" }
                }
            }
        }

        val rpcProvider = currentProvider.toRpcProvider()
        val providerSessionId = newClient.getProviderSessionId()?.takeIf { it.isNotBlank() }
        val resolvedSystemPrompt = (connectOptions.systemPrompt as? String?) ?: normalizedOptions.systemPrompt
        lastConnectOptions = normalizedOptions.copy(
            provider = rpcProvider,
            model = connectOptions.model,
            systemPrompt = resolvedSystemPrompt,
            metadata = connectOptions.metadata,
            // Codex connect 后已拿到 threadId，这里顺手记住，方便后续无参数 connect/reconnect 也能续上。
            resumeSessionId = providerSessionId ?: normalizedOptions.resumeSessionId
        )

        sdkLog.info { "✅ [SDK] 已连接: provider=${connectOptions.provider}, model=${connectOptions.model ?: "default"}" }

        // 设置当前 AI 会话 ID，用于终端默认会话关联
        mcpProviders.terminal.setCurrentAiSession(sessionId)

        val capabilities = newClient.getCapabilities().toRpcCapabilities()
        sdkLog.debug { "✅ [SDK] 能力: canInterrupt=${capabilities.canInterrupt}, canThink=${capabilities.canThink}" }

        val projectCwd = ideTools.getProjectPath().takeIf { it.isNotBlank() }

        return RpcConnectResult(
            // 对外暴露 provider 的真实会话 ID（Codex=threadId）；Claude 仍沿用内部 sessionId，后续靠 system_init 覆盖。
            sessionId = providerSessionId ?: sessionId,
            provider = rpcProvider,
            model = connectOptions.model,
            status = RpcSessionStatus.CONNECTED,
            capabilities = capabilities,
            cwd = projectCwd,
            // 返回后端分配的 connectId 供前端保存
            connectId = connectId
        )
    }

    override fun query(message: String): Flow<RpcMessage> {
        val clientName = client?.javaClass?.simpleName ?: "null"
        sdkLog.info { "[query] provider=$currentProvider, client=$clientName" }
        sdkLog.info { "📤 [SDK] query: message=${message.take(200)}${if (message.length > 200) "..." else ""}" }
        return executeTurn { unifiedClient ->
            unifiedClient.sendMessage(
                AgentMessageInput(text = message, sessionId = sessionId)
            )
        }
    }

    override fun queryWithContent(content: List<RpcContentBlock>): Flow<RpcMessage> {
        val clientName = client?.javaClass?.simpleName ?: "null"
        sdkLog.info { "[queryWithContent] provider=$currentProvider, client=$clientName" }
        sdkLog.info { "📤 [SDK] queryWithContent: blocks=${content.size}" }
        content.forEachIndexed { idx, block ->
            when (block) {
                is RpcTextBlock -> sdkLog.debug { "📤 [SDK]   [$idx] TextBlock: ${block.text.take(100)}..." }
                is RpcImageBlock -> sdkLog.debug { "📤 [SDK]   [$idx] ImageBlock: ${block.source.mediaType}" }
                else -> sdkLog.debug { "📤 [SDK]   [$idx] ${block::class.simpleName}" }
            }
        }
        return executeTurn { unifiedClient ->
            val unifiedContent = content.mapNotNull { it.toUnifiedContentBlock() }
            unifiedClient.sendMessage(
                AgentMessageInput(content = unifiedContent, sessionId = sessionId)
            )
        }
    }

    override fun subscribeGlobalEvents(): Flow<RpcMessage> {
        sdkLog.info { "📡 [subscribeGlobalEvents] 新订阅者加入全局事件流 (sessionId=$sessionId)" }
        return globalEventFlow.asSharedFlow()
            .onCompletion { cause ->
                if (cause == null) {
                    sdkLog.info { "📡 [subscribeGlobalEvents] 订阅者正常断开 (sessionId=$sessionId)" }
                } else {
                    sdkLog.warn { "📡 [subscribeGlobalEvents] 订阅者异常断开: ${cause.message}" }
                }
            }
    }

    override suspend fun interrupt(): RpcStatusResult {
        sdkLog.info { "⏹️ [SDK] 中断当前回合" }
        // 直接调用 SDK 的 interrupt，不再等待 query 流的完成信号
        client?.interrupt()
        sdkLog.info { "✅ [SDK] interrupt 请求已提交" }
        return RpcStatusResult(status = RpcSessionStatus.INTERRUPTED)
    }

    override suspend fun runInBackground(): RpcStatusResult {
        sdkLog.info { "🔄 [SDK] 将任务移到后台运行" }
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")
        activeClient.runInBackground()
        sdkLog.info { "✅ [SDK] runInBackground 请求已提交" }
        return RpcStatusResult(status = RpcSessionStatus.CONNECTED)
    }

    override suspend fun bashRunToBackground(taskId: String): RpcBashBackgroundResult {
        sdkLog.info { "🔄 [SDK] 将 Bash 命令移到后台运行: taskId=$taskId" }
        val activeClient = client ?: return RpcBashBackgroundResult(
            success = false,
            error = "AI Agent 尚未连接，请先调用 connect()"
        )
        return try {
            val result = activeClient.bashRunToBackground(taskId)
            sdkLog.info { "✅ [SDK] bashRunToBackground 成功: taskId=${result.taskId}, command=${result.command}" }
            RpcBashBackgroundResult(
                success = result.success,
                taskId = result.taskId,
                command = result.command
            )
        } catch (e: Exception) {
            sdkLog.warn { "❌ [SDK] bashRunToBackground 失败: ${e.message}" }
            RpcBashBackgroundResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    override suspend fun runToBackground(taskId: String?): RpcUnifiedBackgroundResult {
        sdkLog.info { "🔄 [SDK] 统一后台运行: taskId=${taskId ?: "all"}" }
        val activeClient = client ?: return RpcUnifiedBackgroundResult(
            success = false,
            error = "AI Agent 尚未连接，请先调用 connect()"
        )
        return try {
            val result = activeClient.runToBackground(taskId)
            if (taskId != null) {
                val typeInfo = if (result.isBash == true) "Bash" else "Agent"
                sdkLog.info { "✅ [SDK] runToBackground 成功: $typeInfo taskId=${result.taskId}" }
            } else {
                sdkLog.info { "✅ [SDK] runToBackground 批量成功: Bash=${result.bashCount}, Agent=${result.agentCount}" }
            }
            RpcUnifiedBackgroundResult(
                success = result.success,
                isBash = result.isBash,
                taskId = result.taskId,
                command = result.command,
                bashCount = result.bashCount,
                agentCount = result.agentCount,
                backgroundedBashIds = result.backgroundedBashIds,
                backgroundedAgentIds = result.backgroundedAgentIds,
                error = result.error
            )
        } catch (e: Exception) {
            sdkLog.warn { "❌ [SDK] runToBackground 失败: ${e.message}" }
            RpcUnifiedBackgroundResult(
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    override suspend fun setMaxThinkingTokens(maxThinkingTokens: Int?): RpcSetMaxThinkingTokensResult {
        sdkLog.info { "🧠 [SDK] 设置思考 token 上限: $maxThinkingTokens" }
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")
        activeClient.setMaxThinkingTokens(maxThinkingTokens)
        sdkLog.info { "✅ [SDK] setMaxThinkingTokens 请求已提交: $maxThinkingTokens" }
        return RpcSetMaxThinkingTokensResult(maxThinkingTokens = maxThinkingTokens)
    }

    override suspend fun disconnect(): RpcStatusResult {
        sdkLog.info("馃攲 [AI-Agent] 鏂紑浼氳瘽: $sessionId")
        disconnectInternal()
        return RpcStatusResult(status = RpcSessionStatus.DISCONNECTED)
    }

    override suspend fun setModel(model: String): RpcSetModelResult {
        sdkLog.info { "鈿欙笍 [AI-Agent] 鍒囨崲妯″瀷 -> $model" }
        val base = lastConnectOptions ?: RpcConnectOptions()
        val updated = base.copy(model = model)
        connect(updated)
        return RpcSetModelResult(model = model)
    }

    override suspend fun setPermissionMode(mode: RpcPermissionMode): RpcSetPermissionModeResult {
        sdkLog.info { "鈿欙笍 [AI-Agent] 鍒囨崲鏉冮檺妯″紡 -> $mode" }
        val activeClient = client ?: error("AI Agent 灏氭湭杩炴帴锛岃鍏堣皟鐢?connect()")

        // 灏?RPC 鏉冮檺妯″紡杞崲涓?SDK 鏉冮檺妯″紡
        val sdkMode = mode.toSdkPermissionModeInternal()
        activeClient.setPermissionMode(sdkMode)

        sdkLog.info("鉁?[AI-Agent] 鏉冮檺妯″紡宸插垏鎹负: $mode")
        return RpcSetPermissionModeResult(mode = mode)
    }

    override suspend fun setSandboxMode(mode: RpcSandboxMode): RpcSetSandboxModeResult {
        sdkLog.info("🔒 [AI-Agent] 切换沙箱模式 -> $mode")
        val activeClient = client ?: error("AI Agent 尚未连接，请先调用 connect()")

        // 将 RPC 沙箱模式转换为 SDK 沙箱模式
        val sdkMode = mode.toSdkSandboxMode()
        activeClient.setSandboxMode(sdkMode)

        // 同步更新 lastConnectOptions
        lastConnectOptions = lastConnectOptions?.copy(sandboxMode = mode)

        sdkLog.info("✅ [AI-Agent] 沙箱模式已切换为: $mode")
        return RpcSetSandboxModeResult(mode = mode)
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
        sdkLog.info("[executeTurn] start (sessionId=$sessionId, provider=$currentProvider, client=${activeClient::class.simpleName}, isConnected=${activeClient.isConnected()})")

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
                                globalEventFlow.emit(rpcEvent)  // 广播到全局事件流
                                sdkLog.info("[executeTurn] event #$eventCount ($eventType) sent")

                                when (event) {
                                    is UiToolStart -> {
                                        // 🔍 调试日志：确认 UiToolStart 事件已发送到前端
                                        sdkLog.info("🔧 [executeTurn] UiToolStart sent: toolId=${event.toolId}, toolName=${event.toolName}, toolType=${event.toolType}")
                                    }

                                    is UiMessageComplete -> {
                                        val stopEvent = wrapAsStreamEvent(RpcMessageStopEvent(), rpcProvider)
                                        messageHistory.add(stopEvent)
                                        send(stopEvent)
                                        globalEventFlow.emit(stopEvent)  // 广播到全局事件流
                                        sdkLog.info("[executeTurn] appended message_stop")
                                    }

                                    is UiToolComplete -> {
                                        // 🔍 调试日志：确认 result 的类型
                                        sdkLog.info("🔧 [UiToolComplete] toolId=${event.toolId}, result.type=${event.result::class.simpleName}")
                                        val resultBlock = event.result.toRpcContentBlock()
                                        // 🔍 调试日志：确认转换后的 RPC block 类型
                                        sdkLog.info("🔧 [UiToolComplete] resultBlock.type=${resultBlock::class.simpleName}, resultBlock=$resultBlock")
                                        val toolResultMessage = RpcUserMessage(
                                            message = RpcMessageContent(content = listOf(resultBlock)),
                                            // 使用 event.parentToolUseId（父工具调用 ID），而不是 event.toolId
                                            // 如果 parentToolUseId 为 null，表示这是顶层工具调用的结果，会被 processToolResults 正确处理
                                            parentToolUseId = event.parentToolUseId,
                                            provider = rpcProvider
                                        )
                                        messageHistory.add(toolResultMessage)
                                        send(toolResultMessage)
                                        globalEventFlow.emit(toolResultMessage)  // 广播到全局事件流
                                        sdkLog.info("[executeTurn] tool result message sent toolId=${event.toolId}, parentToolUseId=${event.parentToolUseId}")
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
            sdkLog.info("[executeTurn] collector ready, invoking sendMessage")

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
        limit: Int,
        leafUuid: String?
    ): RpcHistoryResult {
        val targetSession = sessionId ?: lastConnectOptions?.sessionId ?: this@AiAgentRpcServiceImpl.sessionId
        val project = projectPath?.takeIf { it.isNotBlank() } ?: ideTools.getProjectPath()

        // 获取可用的总消息数（快照）
        val availableCount = HistoryJsonlLoader.countLines(targetSession, project)

        // 加载历史消息（List<UiStreamEvent>），使用消息树算法（复刻 CLI 的 Nm 函数）
        val historyEvents = HistoryJsonlLoader.loadHistoryMessages(targetSession, project, offset, limit, leafUuid)

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
        // 仅断开 CLI 客户端
        // MCP 端点保持复用，只在 RSocket 断开时由 HttpApiServer 清理
        try {
            client?.disconnect()
        } catch (t: Throwable) {
            sdkLog.warn { "⚠️ [AI-Agent] 断开客户端时出错: ${t.message}" }
        } finally {
            client = null
        }
    }

    override suspend fun disposeSession(): RpcStatusResult {
        sdkLog.info { "🗑️ [SDK] 销毁会话: connectId=$connectId, sessionId=$sessionId" }

        // 先断开客户端（内部已清理 MCP 端点）
        disconnectInternal()

        // 清理 Terminal MCP session（仍使用 sessionId，因为终端是按 AI session 关联的）
        runCatching {
            mcpProviders.terminal.disposeSession(sessionId)
        }.onFailure { e ->
            sdkLog.warn { "[MCP] Failed to dispose terminal session: ${e.message}" }
        }

        sdkLog.info { "✅ [SDK] 会话已销毁: connectId=$connectId" }
        return RpcStatusResult(status = RpcSessionStatus.DISCONNECTED)
    }

    private fun buildPermissionRequester(): PermissionRequester? {
        val caller = clientCaller ?: return null
        return PermissionRequester { request ->
            try {
                val protoRequest = RequestPermissionRequest.newBuilder().apply {
                    toolName = request.toolName
                    inputJson = com.google.protobuf.ByteString.copyFrom(
                        Json.encodeToString(JsonElement.serializer(), request.inputJson).toByteArray(Charsets.UTF_8)
                    )
                    request.toolUseId?.let { toolUseId = it }
                }.build()

                val protoResponse = caller.callRequestPermission(protoRequest)
                PermissionDecision(
                    approved = protoResponse.approved,
                    denyReason = if (protoResponse.hasDenyReason()) protoResponse.denyReason else null
                )
            } catch (e: Exception) {
                sdkLog.warn("?? [PermissionRequester] Failed: ${e.message}")
                PermissionDecision(
                    approved = false,
                    denyReason = e.message ?: "Permission request failed"
                )
            }
        }
    }

    private suspend fun buildConnectOptions(options: RpcConnectOptions): AiAgentConnectOptions {
        // 每次 connect 时调用 provider 获取最新配置
        val serviceConfig = serviceConfigProvider()
        sdkLog.info(
            "🔧 [buildConnectOptions] MCP config: enableUserInteractionMcp=${serviceConfig.claude.enableUserInteractionMcp}, " +
                "enableJetBrainsMcp=${serviceConfig.claude.enableJetBrainsMcp}"
        )

        val provider = options.provider.toSdkProvider(serviceConfig.defaultProvider)
        val model = options.model  // 前端负责发送正确的 API 模型 ID，不做回退
        val systemPrompt = options.systemPrompt ?: serviceConfig.defaultSystemPrompt
        val initialPrompt = options.initialPrompt
        val sessionHint = options.sessionId
        val resume = options.resumeSessionId
        val metadata = options.metadata.ifEmpty { emptyMap() }
        val mcpSetup = prepareMcpSession(provider, options, serviceConfig)
        val claudeOverrides = buildClaudeOverrides(model, systemPrompt, options, metadata, serviceConfig, mcpSetup)
        val codexOverrides = buildCodexOverrides(model, options, serviceConfig, mcpSetup)

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

    private data class McpSessionSetup(
        val claudeServers: Map<String, McpServerSpec>,
        val mcpSystemPromptAppendix: String,
        val allowedTools: List<String>,
        val codexThreadConfigOverrides: Map<String, JsonElement>,
        /** 启用 MCP 时需要禁用的 Codex features（如 "shell_tool"） */
        val codexDisabledFeatures: List<String> = emptyList()
    )

    private suspend fun prepareMcpSession(
        provider: AiAgentProvider,
        options: RpcConnectOptions,
        serviceConfig: AiAgentServiceConfig
    ): McpSessionSetup = withContext(McpSystemPromptContext.asContextElement(provider)) {
        val defaults = serviceConfig.claude
        val fallbackBackends = serviceConfig.mcpEnabledBackends
        val connectId = this@AiAgentRpcServiceImpl.connectId
        sdkLog.info(
            "[MCP] prepareMcpSession: provider=$provider, connectId=$connectId, sessionId=$sessionId, fallbackBackends=${fallbackBackends.joinToString()}"
        )
        sdkLog.info(
            "[MCP] builtins enabled: userInteraction=${defaults.enableUserInteractionMcp} backends=${defaults.userInteractionMcpBackends.joinToString()}," +
                " jetbrains=${defaults.enableJetBrainsMcp} backends=${defaults.jetbrainsMcpBackends.joinToString()}," +
                " jetbrainsFile=${defaults.enableJetBrainsFileMcp} backends=${defaults.jetbrainsFileMcpBackends.joinToString()}," +
                " terminal=${defaults.enableTerminalMcp} backends=${defaults.terminalMcpBackends.joinToString()}," +
                " git=${defaults.enableGitMcp} backends=${defaults.gitMcpBackends.joinToString()}"
        )

        fun isProviderAllowed(allowed: Set<AiAgentProvider>?): Boolean {
            val resolved = allowed ?: fallbackBackends
            if (resolved.isEmpty()) return false
            return resolved.contains(provider)
        }
        val sessionServers = mutableMapOf<String, McpServer>()
        val globalServers = mutableMapOf<String, McpServer>()
        val claudeServers = mutableMapOf<String, McpServerSpec>()

        val userInteractionTimeoutMs = defaults.userInteractionMcpTimeoutSec
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(1000)
        userInteractionServer.setTimeoutMs(userInteractionTimeoutMs)
        userInteractionServer.setInstructionsByBackend(defaults.userInteractionInstructionsByBackend)

        if (defaults.enableUserInteractionMcp && isProviderAllowed(defaults.userInteractionMcpBackends)) {
            sessionServers["user_interaction"] = userInteractionServer
        }

        if (defaults.enableJetBrainsMcp && isProviderAllowed(defaults.jetbrainsMcpBackends)) {
            mcpProviders.jetBrains.getServer()?.let { globalServers["jetbrains-lsp"] = it }
        }

        sdkLog.info("[MCP] jetbrains-file check: enabled=${defaults.enableJetBrainsFileMcp}, backends=${defaults.jetbrainsFileMcpBackends}, providerAllowed=${isProviderAllowed(defaults.jetbrainsFileMcpBackends)}")
        if (defaults.enableJetBrainsFileMcp && isProviderAllowed(defaults.jetbrainsFileMcpBackends)) {
            val server = mcpProviders.jetBrainsFile.getServer()
            sdkLog.info("[MCP] jetbrains-file server: ${server?.let { "OK" } ?: "NULL"}")
            server?.let { globalServers["jetbrains-file"] = it }
        }

        if (defaults.enableTerminalMcp && isProviderAllowed(defaults.terminalMcpBackends)) {
            mcpProviders.terminal.getServerForSession(sessionId)?.let { sessionServers["jetbrains-terminal"] = it }
        }

        if (defaults.enableGitMcp && isProviderAllowed(defaults.gitMcpBackends)) {
            mcpProviders.git.getServer()?.let { globalServers["jetbrains_git"] = it }
        }

        // 注册 MCP 服务器的辅助函数
        // 根据 provider 选择注册方式：
        // - Claude: SDK 模式，直接传递 McpServer 实例（通过 McpServerWithConnectId 包装 connectId）
        // - Codex: HTTP 模式，注册到 McpHttpGateway（通过 X-MCP-Connect-Id header 传递 connectId）
        suspend fun registerMcpServer(name: String, server: McpServer, scope: String) {
            when (provider) {
                AiAgentProvider.CLAUDE -> {
                    // SDK 模式：创建包装器注入 connectId，直接传给 SDK
                    val wrappedServer = McpServerWithConnectId(server, connectId)
                    claudeServers[name] = wrappedServer
                    sdkLog.info("✅ [MCP] SDK mode registered (scope=$scope): $name (connectId=$connectId)")
                }
                AiAgentProvider.CODEX -> {
                    // HTTP 模式：注册到网关，通过 header 传递 connectId
                    val gateway = mcpHttpGateway
                        ?: throw IllegalStateException("McpHttpGateway is required for Codex mode")
                    val url = gateway.registerServer(name, server)
                    claudeServers[name] = McpHttpServerConfig(
                        url = url,
                        headers = mapOf(McpHttpGateway.HEADER_CONNECT_ID to connectId)
                    )
                    sdkLog.info("✅ [MCP] HTTP endpoint registered (scope=$scope): $name -> $url (connectId=$connectId)")
                }
            }
        }

        // 注册 global 和 session MCP 服务器
        globalServers.forEach { (name, server) ->
            registerMcpServer(name, server, "global")
        }
        sessionServers.forEach { (name, server) ->
            registerMcpServer(name, server, "session")
        }

        val internalServers = mutableMapOf<String, McpServer>().apply {
            putAll(globalServers)
            putAll(sessionServers)
        }

        for (mcpConfig in defaults.mcpServersConfig) {
            if (!mcpConfig.enabled) continue
            if (!isProviderAllowed(mcpConfig.enabledBackends)) continue

            val serverConfig: McpServerConfig = when (mcpConfig.type) {
                "http" -> McpHttpServerConfig(
                    url = mcpConfig.url ?: continue,
                    headers = mcpConfig.headers ?: emptyMap()
                )
                "stdio" -> McpStdioServerConfig(
                    command = mcpConfig.command ?: continue,
                    args = mcpConfig.args ?: emptyList(),
                    env = mcpConfig.env ?: emptyMap()
                )
                else -> {
                    sdkLog.warn("⚠️ [MCP] Unsupported server type: ${mcpConfig.type} for '${mcpConfig.name}'")
                    continue
                }
            }

            claudeServers[mcpConfig.name] = serverConfig
            sdkLog.info("✅ [MCP] Added server config: ${mcpConfig.name} (type=${mcpConfig.type})")
        }

        var mcpSystemPromptAppendix = buildMcpSystemPromptAppendix(internalServers)

        defaults.mcpInstructions?.takeIf { it.isNotBlank() }?.let { instructions ->
            mcpSystemPromptAppendix = if (mcpSystemPromptAppendix.isNotBlank()) {
                "$mcpSystemPromptAppendix\n\n$instructions"
            } else {
                instructions
            }
            sdkLog.info("📝 [MCP] Appended built-in MCP instructions")
        }

        val providerKey = provider.name.lowercase()
        val customInstructions = defaults.mcpServersConfig
            .filter { it.enabled && isProviderAllowed(it.enabledBackends) }
            .mapNotNull { config ->
                val byBackend = config.instructionsByBackend
                    ?.entries
                    ?.firstOrNull { it.key.equals(providerKey, ignoreCase = true) }
                    ?.value
                byBackend?.takeIf { it.isNotBlank() }
                    ?: config.instructions?.takeIf { it.isNotBlank() }
            }
        if (customInstructions.isNotEmpty()) {
            val customPrompt = customInstructions.joinToString("\n\n")
            mcpSystemPromptAppendix = if (mcpSystemPromptAppendix.isNotBlank()) {
                "$mcpSystemPromptAppendix\n\n$customPrompt"
            } else {
                customPrompt
            }
            sdkLog.info("📝 [MCP] Appended ${customInstructions.size} custom MCP instructions")
        }

        val allowedTools = buildMcpAllowedTools(internalServers)
        val codexThreadConfigOverrides = buildCodexMcpThreadConfigOverrides(claudeServers, internalServers)
        if (codexThreadConfigOverrides.isNotEmpty()) {
            sdkLog.info("[MCP] Codex thread config overrides keys: ${codexThreadConfigOverrides.keys.sorted().joinToString()}")
        }
        if (claudeServers.isNotEmpty()) {
            sdkLog.info("?? [MCP] Registered servers: ${claudeServers.keys.joinToString()}")
        }

        // 收集 Codex 禁用 features
        val codexDisabledFeatures = mutableListOf<String>()
        if (claudeServers.containsKey("jetbrains-terminal")) {
            codexDisabledFeatures.addAll(mcpProviders.terminal.getCodexDisabledFeatures())
        }
        if (codexDisabledFeatures.isNotEmpty()) {
            sdkLog.info("🚫 [MCP] Codex disabled features: ${codexDisabledFeatures.joinToString()}")
        }

        McpSessionSetup(
            claudeServers = claudeServers,
            mcpSystemPromptAppendix = mcpSystemPromptAppendix,
            allowedTools = allowedTools,
            codexThreadConfigOverrides = codexThreadConfigOverrides,
            codexDisabledFeatures = codexDisabledFeatures
        )
    }

    private fun buildClaudeOverrides(
        model: String?,
        systemPrompt: String?,
        options: RpcConnectOptions,
        metadata: Map<String, String>,
        serviceConfig: AiAgentServiceConfig,
        mcpSetup: McpSessionSetup
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

        val mcpServers = mcpSetup.claudeServers
        val mcpSystemPromptAppendix = mcpSetup.mcpSystemPromptAppendix

        val agents = ideTools.getAgentDefinitions()
        if (agents.isNotEmpty()) {
            sdkLog.info("📦 [buildClaudeOverrides] 加载了 ${agents.size} 个自定义代理: ${agents.keys.joinToString()}")
        } else {
            sdkLog.warn("⚠️ [buildClaudeOverrides] 未加载到任何自定义代理 (ideTools类型=${ideTools::class.simpleName})")
        }
        val disallowedTools = buildDisallowedBuiltinTools(mcpSetup).toMutableList()

        // 如果启用了 User Interaction MCP，禁用内置的 AskUserQuestion
        if (defaults.enableUserInteractionMcp && mcpSetup.claudeServers.containsKey("user_interaction")) {
            disallowedTools.add("AskUserQuestion")
            sdkLog.info("🚫 [buildClaudeOverrides] User Interaction MCP 已启用，禁用内置 AskUserQuestion")
        }

        if (disallowedTools.isNotEmpty()) {
            sdkLog.info("🚫 [buildClaudeOverrides] 禁用内置工具: $disallowedTools")
        }

        // canUseTool 回调：通过 RPC 调用前端获取用户授权（带 tool_use_id 和 permissionSuggestions）
        val canUseToolCallback: CanUseTool = { toolName, input, toolUseId, context ->
            sdkLog.info("🔐 [canUseTool] 请求授权: toolName=$toolName, toolUseId=$toolUseId, suggestions=${context.suggestions.size}")

            // 在 Edit/Write 工具执行前保存原始文件内容（用于后续显示 Diff）
            if (toolUseId != null && (toolName == "Edit" || toolName == "Write")) {
                val filePath = input["file_path"]?.jsonPrimitive?.contentOrNull
                    ?: input["path"]?.jsonPrimitive?.contentOrNull
                if (filePath != null) {
                    FileContentCache.saveOriginalContent(toolUseId, filePath)
                }
            }

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
            allowedTools = mcpSetup.allowedTools,
            // 禁用的内置工具（如启用 Terminal MCP 时禁用 Bash）
            disallowedTools = disallowedTools,
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
    private fun buildMcpSystemPromptAppendix(mcpServers: Map<String, McpServer>): String {
        return mcpServers.values
            .mapNotNull { server -> server.getSystemPromptAppendix()?.takeIf { it.isNotBlank() } }
            .joinToString("\n\n")
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
    private fun buildMcpAllowedTools(mcpServers: Map<String, McpServer>): List<String> {
        return mcpServers.entries
            .flatMap { (serverName, server) ->
                server.getAllowedTools().map { toolName ->
                    "mcp__${serverName}__$toolName"
                }
            }
    }

    /**
     * Extract HTTP MCP server URLs for thread-level config.
     * Note: Codex only supports HTTP MCP servers via thread config (not stdio).
     */
    private fun extractMcpHttpUrls(mcpServers: Map<String, McpServerSpec>): Map<String, String> {
        if (mcpServers.isEmpty()) return emptyMap()
        return mcpServers.mapNotNull { (name, server) ->
            when (server) {
                is McpHttpServerConfig -> name to server.url
                else -> null // stdio servers cannot be passed via thread config
            }
        }.toMap()
    }

    private fun buildCodexMcpThreadConfigOverrides(
        mcpServers: Map<String, McpServerSpec>,
        internalServers: Map<String, McpServer>
    ): Map<String, JsonElement> {
        if (mcpServers.isEmpty()) return emptyMap()

        val overrides = mutableMapOf<String, JsonElement>()
        fun applyTimeoutOverride(serverName: String, timeout: Long?) {
            // 当 timeout 为 null 或 <= 0 时，不设置 override，让 Codex 使用默认值（60秒）
            // 注意：Codex 不支持 0 作为"永不超时"，设置 0 会导致立即超时！
            if (timeout != null && timeout > 0) {
                // 将毫秒转换为秒（Codex 使用秒），最小 1 秒
                val timeoutSec = (timeout / 1000).coerceAtLeast(1)
                overrides["mcp_servers.$serverName.tool_timeout_sec"] = JsonPrimitive(timeoutSec)
            }
        }

        // 先处理内部 MCP 服务（即便被包装成 HTTP 配置也要保留超时设置）
        internalServers.forEach { (name, server) ->
            applyTimeoutOverride(name, server.timeout)
        }

        mcpServers.forEach { (name, server) ->
            if (server is McpServer) {
                applyTimeoutOverride(name, server.timeout)
            }
            when (server) {
                is McpHttpServerConfig -> {
                    overrides["mcp_servers.$name.url"] = JsonPrimitive(server.url)
                    server.headers.forEach { (headerName, headerValue) ->
                        overrides["mcp_servers.$name.http_headers.$headerName"] = JsonPrimitive(headerValue)
                    }
                }
                is McpStdioServerConfig -> {
                    overrides["mcp_servers.$name.command"] = JsonPrimitive(server.command)
                    if (server.args.isNotEmpty()) {
                        overrides["mcp_servers.$name.args"] = buildJsonArray {
                            server.args.forEach { add(it) }
                        }
                    }
                    if (server.env.isNotEmpty()) {
                        overrides["mcp_servers.$name.env"] = buildJsonObject {
                            server.env.forEach { (key, value) -> put(key, value) }
                        }
                    }
                }
                is McpServerConfig -> {
                    sdkLog.warn("[MCP] Unsupported MCP config for Codex: ${server.type} ($name)")
                }
                else -> {
                    sdkLog.warn("[MCP] Unsupported MCP spec for Codex: ${server::class.simpleName ?: "unknown"} ($name)")
                }
            }
        }
        return overrides
    }

    /**
     * 收集需要禁用的内置工具
     *
     * 遍历所有 MCP 服务器提供者，收集其声明的需要禁用的内置工具列表。
     * 例如：
     * - Terminal MCP 启用时可以禁用内置的 Bash 工具
     * - JetBrains MCP 启用时可以禁用内置的 Glob 和 Grep 工具
     *
     * @return 需要禁用的内置工具名称列表
     */
    private fun buildDisallowedBuiltinTools(mcpSetup: McpSessionSetup): List<String> {
        val disallowedTools = mutableListOf<String>()

        // 从 MCP 提供者获取需要禁用的工具
        if (mcpSetup.claudeServers.containsKey("jetbrains-lsp")) {
            disallowedTools.addAll(mcpProviders.jetBrains.getDisallowedBuiltinTools())
        }

        if (mcpSetup.claudeServers.containsKey("jetbrains-file")) {
            disallowedTools.addAll(mcpProviders.jetBrainsFile.getDisallowedBuiltinTools())
        }

        if (mcpSetup.claudeServers.containsKey("jetbrains-terminal")) {
            disallowedTools.addAll(mcpProviders.terminal.getDisallowedBuiltinTools())
        }

        return disallowedTools.distinct()
    }

    private fun buildCodexOverrides(
        model: String?,
        options: RpcConnectOptions,
        serviceConfig: AiAgentServiceConfig,
        mcpSetup: McpSessionSetup
    ): CodexOverrides {
        val codexDefaults = serviceConfig.codex

        // 构建完整的配置覆盖（包含 MCP 和 features）
        val fullConfigOverrides = buildMap<String, JsonElement> {
            putAll(mcpSetup.codexThreadConfigOverrides)
            // 禁用 MCP 替代的 Codex 内置工具
            mcpSetup.codexDisabledFeatures.forEach { feature ->
                put("features.$feature", JsonPrimitive(false))
            }
        }

        // Build app-server config overrides (features only).
        val appServerConfigOverrides = buildAppServerConfigOverrides(
            fullConfigOverrides,
            codexDefaults.webSearchEnabled
        )
        if (appServerConfigOverrides.isNotEmpty()) {
            sdkLog.info("[buildCodexOverrides] appServerConfigOverrides keys: ${appServerConfigOverrides.keys.sorted().joinToString()}")
        }

        // Build thread config overrides (exclude features; keep MCP in thread config).
        val threadConfigOverrides = fullConfigOverrides.filterKeys { key ->
            !key.startsWith("features.")
        }
        if (threadConfigOverrides.isNotEmpty()) {
            sdkLog.info("[buildCodexOverrides] threadConfigOverrides keys: ${threadConfigOverrides.keys.sorted().joinToString()}")
        }

        val mcpHttpUrls = extractMcpHttpUrls(mcpSetup.claudeServers)
        val clientOptions = CodexClientOptions(
            codexPathOverride = codexDefaults.binaryPath
                ?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it) },
            baseUrl = options.baseUrl ?: codexDefaults.baseUrl,
            apiKey = options.apiKey ?: codexDefaults.apiKey,
            appServerConfigOverrides = appServerConfigOverrides
        )

        val sandboxMode = options.sandboxMode?.toSdkSandboxMode()
            ?: codexDefaults.sandboxMode?.let {
                runCatching { SandboxMode.valueOf(it.uppercase()) }.getOrNull()
            }

        // Codex 永远使用 ON_REQUEST（始终发起权限请求）
        // 绕过权限由前端通过 skipPermissions 处理（自动批准）
        val approvalPolicy = ApprovalMode.ON_REQUEST

        val reasoningEffort = parseReasoningEffort(
            options.codexReasoningEffort ?: codexDefaults.defaultReasoningEffort
        )
        val reasoningSummary = normalizeReasoningSummary(
            options.codexReasoningSummary ?: codexDefaults.defaultReasoningSummary
        )

        val workingDirectory = ideTools.getProjectPath().takeIf { it.isNotBlank() }
        val codexPathLog = codexDefaults.binaryPath?.takeIf { it.isNotBlank() } ?: "auto"
        val apiKeyPresent = !(options.apiKey ?: codexDefaults.apiKey).isNullOrBlank()
        val baseUrlLog = options.baseUrl ?: codexDefaults.baseUrl ?: "default"
        val sandboxLog = sandboxMode?.name ?: "default"
        val approvalLog = approvalPolicy.name
        val effortLog = reasoningEffort?.wireValue ?: "default"
        val summaryLog = reasoningSummary ?: "default"
        val cwdLog = workingDirectory ?: "default"
        sdkLog.info("[buildCodexOverrides] model=${model ?: "default"}, codexPath=$codexPathLog, baseUrl=$baseUrlLog, apiKeyPresent=$apiKeyPresent, sandbox=$sandboxLog, approval=$approvalLog, effort=$effortLog, summary=$summaryLog, cwd=$cwdLog")

        val threadOptions = ThreadOptions(
            model = model,
            sandboxMode = sandboxMode,
            workingDirectory = workingDirectory,
            skipGitRepoCheck = true,
            modelReasoningEffort = reasoningEffort,
            modelReasoningSummary = reasoningSummary,
            webSearchEnabled = null,  // 移到 AppServer 级别
            approvalPolicy = approvalPolicy,
            developerInstructions = mcpSetup.mcpSystemPromptAppendix.takeIf { it.isNotBlank() },
            mcpServers = mcpHttpUrls,
            threadConfigOverrides = threadConfigOverrides
        )

        return CodexOverrides(
            clientOptions = clientOptions,
            threadOptions = threadOptions
        )
    }

    /**
     * Build app-server config overrides (features only).
     */
    private fun buildAppServerConfigOverrides(
        configOverrides: Map<String, JsonElement>,
        webSearchEnabled: Boolean?
    ): Map<String, String> {
        val config = mutableMapOf<String, String>()

        // Web search 配置
        webSearchEnabled?.let { enabled ->
            config["features.web_search_request"] = enabled.toString()
        }

        // configOverrides 中的 features 配置
        configOverrides.forEach { (key, value) ->
            if (key.startsWith("features.")) {
                config[key] = when (value) {
                    is JsonPrimitive -> value.content
                    else -> value.toString()
                }
            }
        }

        return config
    }

    private fun parseReasoningEffort(value: String?): ModelReasoningEffort? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { ModelReasoningEffort.valueOf(normalized.uppercase()) }.getOrNull()
    }

    private fun normalizeReasoningSummary(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when (normalized) {
            "auto", "concise", "detailed", "none" -> normalized
            else -> null
        }
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
                val index = toolContentIndex.getOrPut(toolId) { nextContentIndex++ }
                val resolvedInput = input ?: inputPreview?.let { kotlinx.serialization.json.JsonPrimitive(it) }
                val resolvedToolType = if (toolType.isNotBlank()) {
                    toolType
                } else {
                    ToolType.fromToolName(toolName).type
                }
                // 🔍 调试日志：确认 UiToolStart -> RpcContentBlockStartEvent 转换
                sdkLog.info("🔧 [toRpcMessage] UiToolStart -> content_block_start: toolId=$toolId, toolName=$toolName, toolType=$resolvedToolType, index=$index")
                wrapAsStreamEvent(
                    RpcContentBlockStartEvent(
                        index = index,
                        contentBlock = RpcToolUseBlock(
                            id = toolId,
                            toolName = toolName,
                            toolType = resolvedToolType,
                            input = resolvedInput,
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
        is ToolResultContent -> {
            sdkLog.info("🔧 [toRpcContentBlock] ToolResultContent: toolUseId=$toolUseId, isError=$isError, contentPreview=${content?.toString()?.take(100)}")
            RpcToolResultBlock(
                toolUseId = toolUseId,
                content = content,
                isError = isError,
                agentId = agentId
            )
        }
        // CommandExecutionContent, FileChangeContent, WebSearchContent 已删除
        // 统一使用 ToolUseContent + ToolResultContent
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
    }

    /**
     * 灏?RPC RpcPermissionMode 杞崲涓?SDK PermissionMode锛堢敤浜?setPermissionMode锛?     */
    private fun RpcPermissionMode.toSdkPermissionModeInternal(): SdkPermissionMode = when (this) {
        RpcPermissionMode.DEFAULT -> SdkPermissionMode.DEFAULT
        RpcPermissionMode.ACCEPT_EDITS -> SdkPermissionMode.ACCEPT_EDITS
        RpcPermissionMode.BYPASS_PERMISSIONS -> SdkPermissionMode.BYPASS_PERMISSIONS
        RpcPermissionMode.PLAN -> SdkPermissionMode.PLAN
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
        is UiToolStart -> "toolId=${event.toolId}, toolName=${event.toolName}, toolType=${event.toolType}, inputPreview=${event.inputPreview}, input=${event.input?.toString()?.take(200)}, parentToolUseId=${event.parentToolUseId}"
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
                // CommandExecutionContent, FileChangeContent, WebSearchContent 已删除
                is TodoListContent -> "TodoList(items=${block.items})"
                is ErrorContent -> "Error(${block.message})"
            }
        }
    }

    /**
     * 获取 MCP 服务器状态
     */
    override suspend fun getMcpStatus(): RpcMcpStatusResult {
        val currentClient = client ?: return RpcMcpStatusResult(servers = emptyList())

        sdkLog.info("[MCP] getMcpStatus: provider=$currentProvider")
        val statusList = currentClient.getMcpStatus()
        sdkLog.info("[MCP] getMcpStatus: ${statusList.size} server(s) -> ${statusList.map { it.name }.joinToString()}")
        return RpcMcpStatusResult(
            servers = statusList.map { info ->
                RpcMcpServerStatus(
                    name = info.name,
                    status = info.status,
                    serverInfo = info.serverInfo
                )
            }
        )
    }

    /**
     * 重连指定的 MCP 服务器
     */
    override suspend fun reconnectMcp(serverName: String): RpcReconnectMcpResult {
        val currentClient = client ?: return RpcReconnectMcpResult(
            success = false,
            serverName = serverName,
            status = null,
            toolsCount = 0,
            error = "Client not connected"
        )

        if (currentProvider == AiAgentProvider.CODEX) {
            val authUrl = runCatching { currentClient.startMcpOauthLogin(serverName) }.getOrNull()
            if (!authUrl.isNullOrBlank()) {
                val openResult = ideTools.openUrl(authUrl)
                if (openResult.isFailure) {
                    sdkLog.warn("Failed to open MCP auth URL: ${openResult.exceptionOrNull()?.message}")
                }
            }
        }

        return try {
            val result = currentClient.reconnectMcp(serverName)
            RpcReconnectMcpResult(
                success = result.success,
                serverName = result.serverName,
                status = result.status,
                toolsCount = result.toolsCount,
                error = result.error
            )
        } catch (e: Exception) {
            sdkLog.warn("Failed to reconnect MCP server $serverName: ${e.message}")
            RpcReconnectMcpResult(
                success = false,
                serverName = serverName,
                status = null,
                toolsCount = 0,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 获取指定 MCP 服务器的工具列表
     */
    override suspend fun getMcpTools(serverName: String?): RpcMcpToolsResult {
        val currentClient = client ?: return RpcMcpToolsResult(
            serverName = serverName,
            tools = emptyList(),
            count = 0
        )

        sdkLog.info("[MCP] getMcpTools: provider=$currentProvider, serverName=${serverName ?: "(all)"}")
        return try {
            val result = currentClient.getMcpTools(serverName)
            sdkLog.info("[MCP] getMcpTools: returned ${result.count} tool(s) (serverName=${result.serverName ?: "(all)"})")
            RpcMcpToolsResult(
                serverName = result.serverName,
                tools = result.tools.map { tool ->
                    RpcMcpToolInfo(
                        name = tool.name,
                        description = tool.description,
                        inputSchema = tool.inputSchema
                    )
                },
                count = result.count
            )
        } catch (e: Exception) {
            sdkLog.warn("Failed to get MCP tools for $serverName: ${e.message}")
            RpcMcpToolsResult(
                serverName = serverName,
                tools = emptyList(),
                count = 0
            )
        }
    }

    /**
     * 获取可用模型列表（内置 + 自定义）
     */
    override suspend fun getAvailableModels(): RpcAvailableModelsResult {
        val config = serviceConfigProvider()

        // 内置模型
        val builtInModels = listOf(
            RpcModelInfo(
                displayName = "Opus 4.5",
                modelId = "claude-opus-4-5-20251101",
                isBuiltIn = true
            ),
            RpcModelInfo(
                displayName = "Sonnet 4.5",
                modelId = "claude-sonnet-4-5-20250929",
                isBuiltIn = true
            ),
            RpcModelInfo(
                displayName = "Haiku 4.5",
                modelId = "claude-haiku-4-5-20251001",
                isBuiltIn = true
            )
        )

        // 自定义模型
        val customModels = config.customModels.map { model ->
            RpcModelInfo(
                displayName = model.displayName,
                modelId = model.modelId,
                isBuiltIn = false
            )
        }

        return RpcAvailableModelsResult(
            models = builtInModels + customModels,
            defaultModelId = config.defaultModel
        )
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
