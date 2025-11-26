package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.AiAgentStreamBridge
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

                val flow = streamBridge.fromClaude(activeClient.receiveResponse())
                var eventCount = 0
                flow.collect { event ->
                    eventCount++
                    logger.info("📨 [ClaudeAgentClientImpl] 收到事件 #$eventCount: ${event::class.simpleName}")
                    eventFlow.emit(event)
                }
                logger.info("✅ [ClaudeAgentClientImpl] 响应接收完成，共 $eventCount 个事件")
            } catch (t: Throwable) {
                logger.severe("❌ [ClaudeAgentClientImpl] 发送消息失败: ${t.message}")
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
}

