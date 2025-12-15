package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.AiAgentStreamBridge
import com.asakii.ai.agent.sdk.capabilities.AgentCapabilities
import com.asakii.ai.agent.sdk.capabilities.CodexCapabilities
import com.asakii.ai.agent.sdk.capabilities.AiPermissionMode
import com.asakii.ai.agent.sdk.connect.AiAgentConnectContext
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.normalize
import com.asakii.ai.agent.sdk.model.UiError
import com.asakii.ai.agent.sdk.model.UiStreamEvent
import com.asakii.codex.agent.sdk.CodexClient
import com.asakii.codex.agent.sdk.CodexSession
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.codex.agent.sdk.TurnOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CodexAgentClientImpl(
    private val streamBridge: AiAgentStreamBridge = AiAgentStreamBridge(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UnifiedAgentClient {

    override val provider: AiAgentProvider = AiAgentProvider.CODEX

    private val eventFlow = MutableSharedFlow<UiStreamEvent>(extraBufferCapacity = 64)
    private val sendMutex = Mutex()

    private var client: CodexClient? = null
    private var session: CodexSession? = null
    private var context: AiAgentConnectContext? = null
    private var activeCancellationJob: Job? = null

    override suspend fun connect(options: AiAgentConnectOptions) {
        val normalized = options.normalize()
        require(normalized.provider == AiAgentProvider.CODEX) {
            "CodexAgentClientImpl 只能处理 Codex provider"
        }

        val codexClientOptions = normalized.codexClientOptions ?: CodexClientOptions()
        val codexThreadOptions = normalized.codexThreadOptions ?: ThreadOptions()

        val newClient = CodexClient(codexClientOptions)
        val newSession = if (normalized.codexThreadId != null) {
            newClient.resumeThread(normalized.codexThreadId, codexThreadOptions)
        } else {
            newClient.startThread(codexThreadOptions)
        }

        client = newClient
        session = newSession
        context = normalized

        normalized.initialPrompt?.let {
            sendMessage(AgentMessageInput(text = it, sessionId = normalized.sessionId))
        }
    }

    override suspend fun sendMessage(input: AgentMessageInput) {
        val activeSession = session ?: error("Codex 会话尚未连接")

        sendMutex.withLock {
            val cancellationJob = Job(scope.coroutineContext.job)
            activeCancellationJob = cancellationJob
            try {
                // Codex 目前只支持纯文本，将内容块拍平成文本
                val text = input.text ?: input.content?.joinToString("\n") { block ->
                    when (block) {
                        is com.asakii.ai.agent.sdk.model.TextContent -> block.text
                        is com.asakii.ai.agent.sdk.model.ImageContent -> "[Image: ${block.mediaType}]"
                        else -> "[${block.type}]"
                    }
                } ?: error("text 和 content 不能同时为空")

                val turn = activeSession.runStreamed(
                    text,
                    turnOptions = TurnOptions(cancellation = cancellationJob)
                )
                val flow = streamBridge.fromCodex(turn.events)
                flow.collect { eventFlow.emit(it) }
            } catch (t: Throwable) {
                eventFlow.emit(UiError("Codex 会话失败: ${t.message}"))
                throw t
            } finally {
                activeCancellationJob = null
            }
        }
    }

    override fun streamEvents(): Flow<UiStreamEvent> = eventFlow.asSharedFlow()

    override suspend fun interrupt() {
        activeCancellationJob?.cancel()
    }

    override suspend fun runInBackground() {
        throw UnsupportedOperationException(
            "runInBackground is not supported by ${provider.name}"
        )
    }

    override suspend fun disconnect() {
        activeCancellationJob?.cancel()
        client = null
        session = null
        context = null
    }

    override fun isConnected(): Boolean {
        return client != null && session != null
    }

    // ==================== 能力相关方法 ====================

    override fun getCapabilities(): AgentCapabilities = CodexCapabilities

    override suspend fun setModel(model: String): String? {
        throw UnsupportedOperationException(
            "setModel is not supported by ${provider.name}"
        )
    }

    override suspend fun setPermissionMode(mode: AiPermissionMode) {
        throw UnsupportedOperationException(
            "setPermissionMode is not supported by ${provider.name}"
        )
    }

    override fun getCurrentPermissionMode(): AiPermissionMode? = null
}

