package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.AiAgentStreamBridge
import com.asakii.ai.agent.sdk.capabilities.AgentCapabilities
import com.asakii.ai.agent.sdk.capabilities.ClaudeCapabilities
import com.asakii.ai.agent.sdk.capabilities.AiPermissionMode
import com.asakii.claude.agent.sdk.types.PermissionMode as ClaudePermissionMode
import com.asakii.ai.agent.sdk.connect.AiAgentConnectContext
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.normalize
import com.asakii.ai.agent.sdk.model.ImageContent
import com.asakii.ai.agent.sdk.model.TextContent
import com.asakii.ai.agent.sdk.model.UiError
import com.asakii.ai.agent.sdk.model.UiStreamEvent
import com.asakii.ai.agent.sdk.model.UnifiedContentBlock
import com.asakii.claude.agent.sdk.ClaudeCodeSdkClient
import com.asakii.claude.agent.sdk.types.ImageInput
import com.asakii.claude.agent.sdk.types.TextInput
import com.asakii.claude.agent.sdk.types.UserInputContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ClaudeAgentClientImpl(
    private val streamBridge: AiAgentStreamBridge = AiAgentStreamBridge(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UnifiedAgentClient {

    override val provider: AiAgentProvider = AiAgentProvider.CLAUDE

    // 不使用 replay，避免新一轮订阅立即拿到上一轮的 complete 事件而提前退出
    private val eventFlow = MutableSharedFlow<UiStreamEvent>(
        extraBufferCapacity = 64
    )
    private val sendMutex = Mutex()

    private var client: ClaudeCodeSdkClient? = null
    private var context: AiAgentConnectContext? = null
    private var currentPermissionMode: AiPermissionMode = AiPermissionMode.DEFAULT

    override suspend fun connect(options: AiAgentConnectOptions) {
        val normalized = options.normalize()
        require(normalized.provider == AiAgentProvider.CLAUDE) {
            "ClaudeAgentClientImpl 只能处理 Claude provider"
        }
        val claudeOptions = normalized.claudeOptions
            ?: throw IllegalArgumentException("Claude connect 需要 claudeOptions")

        val newClient = ClaudeCodeSdkClient(claudeOptions)
        newClient.connect()

        context = normalized
        client = newClient

        normalized.initialPrompt?.let {
            sendMessage(AgentMessageInput(text = it, sessionId = normalized.sessionId))
        }
    }

    override suspend fun sendMessage(input: AgentMessageInput) {
        val activeClient = client ?: error("Claude 客户端尚未连接")
        val currentContext = context ?: error("Claude 会话上下文为空")

        sendMutex.withLock {
            val sessionId = input.sessionId ?: currentContext.sessionId ?: "default"
            try {
                // 如果提供了富媒体内容，使用 content；否则使用纯文本
                if (!input.content.isNullOrEmpty()) {
                    val claudeContent = input.content.mapNotNull { it.toClaudeInput() }
                    logger.info("📤 [ClaudeAgentClientImpl] 发送富媒体消息: ${claudeContent.size} 个内容块")
                    activeClient.query(claudeContent, sessionId = sessionId)
                } else {
                    val text = input.text ?: error("text 和 content 不能同时为空")
                    logger.info("📤 [ClaudeAgentClientImpl] 发送消息: ${text.take(100)}...")
                    activeClient.query(text, sessionId = sessionId)
                }
                logger.info("✅ [ClaudeAgentClientImpl] 消息已发送，开始接收响应...")

                logger.info("🔄 [ClaudeAgentClientImpl] 开始收集 receiveResponse() 流")
                val flow = streamBridge.fromClaude(activeClient.receiveResponse())
                var eventCount = 0
                var lastEventType: String? = null
                flow.collect { event ->
                    eventCount++
                    val eventType = event::class.simpleName
                    lastEventType = eventType
                    logger.info("📨 [ClaudeAgentClientImpl] 收到事件 #$eventCount: $eventType")
                    
                    // 记录关键事件的详情
                    when (event) {
                        is com.asakii.ai.agent.sdk.model.UiMessageComplete -> {
                            logger.info("✅ [ClaudeAgentClientImpl] UiMessageComplete: usage=${event.usage}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiError -> {
                            logger.severe("❌ [ClaudeAgentClientImpl] UiError: ${event.message}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiToolComplete -> {
                            logger.info("🔧 [ClaudeAgentClientImpl] UiToolComplete: toolId=${event.toolId}, resultType=${event.result::class.simpleName}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiToolStart -> {
                            logger.info("🚀 [ClaudeAgentClientImpl] UiToolStart: toolId=${event.toolId}, toolName=${event.toolName}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiToolProgress -> {
                            logger.info("⏳ [ClaudeAgentClientImpl] UiToolProgress: toolId=${event.toolId}, status=${event.status}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiMessageStart -> {
                            logger.info("📝 [ClaudeAgentClientImpl] UiMessageStart: messageId=${event.messageId}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiTextDelta -> {
                            logger.info("📝 [ClaudeAgentClientImpl] UiTextDelta: textLength=${event.text.length}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiThinkingDelta -> {
                            logger.info("💭 [ClaudeAgentClientImpl] UiThinkingDelta: thinkingLength=${event.thinking.length}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiAssistantMessage -> {
                            logger.info("🤖 [ClaudeAgentClientImpl] UiAssistantMessage: contentBlocks=${event.content.size}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiResultMessage -> {
                            logger.info("📊 [ClaudeAgentClientImpl] UiResultMessage: duration=${event.durationMs}ms, turns=${event.numTurns}, resultPreview=${event.result?.take(80)}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiUserMessage -> {
                            logger.info("👤 [ClaudeAgentClientImpl] UiUserMessage: contentBlocks=${event.content.size}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiTextStart -> {
                            logger.info("📝 [ClaudeAgentClientImpl] UiTextStart: index=${event.index}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiThinkingStart -> {
                            logger.info("💭 [ClaudeAgentClientImpl] UiThinkingStart: index=${event.index}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiStatusSystem -> {
                            logger.info("📊 [ClaudeAgentClientImpl] UiStatusSystem: status=${event.status}, sessionId=${event.sessionId}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiCompactBoundary -> {
                            logger.info("📦 [ClaudeAgentClientImpl] UiCompactBoundary: trigger=${event.trigger}, preTokens=${event.preTokens}")
                        }
                        is com.asakii.ai.agent.sdk.model.UiSystemInit -> {
                            logger.info("🚀 [ClaudeAgentClientImpl] UiSystemInit: sessionId=${event.sessionId}, model=${event.model}")
                        }
                    }
                    
                    try {
                        eventFlow.emit(event)
                        logger.info("✅ [ClaudeAgentClientImpl] 事件 #$eventCount ($eventType) 已发送到 eventFlow")
                    } catch (e: Exception) {
                        logger.severe("❌ [ClaudeAgentClientImpl] 发送事件到 eventFlow 失败: ${e.message}")
                        e.printStackTrace()
                    }
                }
                logger.info("✅ [ClaudeAgentClientImpl] 响应接收完成，共 $eventCount 个事件，最后事件类型: $lastEventType")
            } catch (t: Throwable) {
                // 区分 CancellationException 和其他异常
                if (t is kotlinx.coroutines.CancellationException) {
                    logger.warning("⚠️ [ClaudeAgentClientImpl] 消息处理被取消: ${t.message}")
                    throw t  // 重新抛出 CancellationException，不记录为错误
                }
                logger.severe("❌ [ClaudeAgentClientImpl] 发送消息失败: ${t::class.simpleName}: ${t.message}")
                t.printStackTrace()
                eventFlow.emit(UiError("Claude 会话失败: ${t.message}"))
                throw t
            }
        }
    }

    /**
     * 将统一内容块转换为 Claude SDK 的输入格式
     */
    private fun UnifiedContentBlock.toClaudeInput(): UserInputContent? = when (this) {
        is TextContent -> TextInput(text)
        is ImageContent -> ImageInput.fromBase64(data = data, mimeType = mediaType)
        else -> null  // 忽略其他类型（tool_use 等不应该出现在用户输入中）
    }

    private val logger = java.util.logging.Logger.getLogger(ClaudeAgentClientImpl::class.java.name)

    override fun streamEvents(): Flow<UiStreamEvent> = eventFlow.asSharedFlow()

    override suspend fun interrupt() {
        client?.interrupt()
    }

    override suspend fun disconnect() {
        client?.disconnect()
        context = null
    }

    override fun isConnected(): Boolean {
        return client?.isConnected() == true
    }

    // ==================== 能力相关方法 ====================

    override fun getCapabilities(): AgentCapabilities = ClaudeCapabilities

    override suspend fun setModel(model: String): String? {
        checkCapability(getCapabilities().canSwitchModel, "setModel")
        return client?.setModel(model)
    }

    override suspend fun setPermissionMode(mode: AiPermissionMode) {
        val caps = getCapabilities()
        checkCapability(caps.canSwitchPermissionMode, "setPermissionMode")
        require(mode in caps.supportedPermissionModes) {
            "Mode $mode is not supported. Supported: ${caps.supportedPermissionModes}"
        }
        client?.setPermissionMode(mode.toClaudePermissionMode())
        currentPermissionMode = mode
        logger.info("✅ [ClaudeAgentClientImpl] 权限模式已切换为: $mode")
    }

    override suspend fun setMaxThinkingTokens(maxThinkingTokens: Int?) {
        checkCapability(getCapabilities().canThink, "setMaxThinkingTokens")
        client?.setMaxThinkingTokens(maxThinkingTokens)
        logger.info("✅ [ClaudeAgentClientImpl] 思考 token 上限已设置为: $maxThinkingTokens")
    }

    override fun getCurrentPermissionMode(): AiPermissionMode = currentPermissionMode

    /**
     * 获取 MCP 服务器状态
     */
    override suspend fun getMcpStatus() = client?.getMcpStatus() ?: emptyList()

    /**
     * 重连指定的 MCP 服务器
     */
    override suspend fun reconnectMcp(serverName: String) = client?.reconnectMcp(serverName)
        ?: com.asakii.claude.agent.sdk.types.McpReconnectResponse(
            success = false,
            serverName = serverName,
            status = null,
            toolsCount = 0,
            error = "Client not connected"
        )

    private fun checkCapability(supported: Boolean, method: String) {
        if (!supported) {
            throw UnsupportedOperationException(
                "$method is not supported by ${provider.name}"
            )
        }
    }

    /**
     * 将统一 AiPermissionMode 转换为 Claude SDK 的 PermissionMode 枚举
     */
    private fun AiPermissionMode.toClaudePermissionMode(): ClaudePermissionMode = when (this) {
        AiPermissionMode.DEFAULT -> ClaudePermissionMode.DEFAULT
        AiPermissionMode.ACCEPT_EDITS -> ClaudePermissionMode.ACCEPT_EDITS
        AiPermissionMode.BYPASS_PERMISSIONS -> ClaudePermissionMode.BYPASS_PERMISSIONS
        AiPermissionMode.PLAN -> ClaudePermissionMode.PLAN
    }
}

