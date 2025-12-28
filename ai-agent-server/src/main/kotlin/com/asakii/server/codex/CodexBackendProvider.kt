package com.asakii.server.codex

import com.asakii.codex.agent.sdk.appserver.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Codex 后端提供者
 *
 * 使用 codex app-server 模式提供完整的 Codex 功能：
 * 1. 管理 Codex app-server 进程生命周期
 * 2. 管理 JSON-RPC 2.0 通信
 * 3. 提供统一的会话管理接口
 * 4. 事件映射和转发
 * 5. 处理 Approval 请求回调
 *
 * 参考：external/openai-codex/codex-rs/app-server/README.md
 */
class CodexBackendProvider(
    private val workingDirectory: String,
    private val codexPath: String? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {

    private val logger = LoggerFactory.getLogger(CodexBackendProvider::class.java)

    // App-Server 客户端
    private var appServerClient: CodexAppServerClient? = null

    // 运行状态
    private val isRunning = AtomicBoolean(false)

    // 会话管理：threadId -> ThreadState
    private val threads = ConcurrentHashMap<String, ThreadState>()

    // 事件流：用于向外部发送事件
    private val _events = MutableSharedFlow<CodexEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<CodexEvent> = _events.asSharedFlow()

    // Approval 请求回调
    private val _approvalRequests = Channel<ApprovalRequest>(Channel.UNLIMITED)
    val approvalRequests: Flow<ApprovalRequest> = _approvalRequests.receiveAsFlow()

    // 事件处理任务
    private var eventProcessingJob: Job? = null

    /**
     * 会话状态
     */
    data class ThreadState(
        val threadId: String,
        val config: ThreadConfig,
        var isActive: Boolean = true,
        var currentTurnId: String? = null
    )

    /**
     * 会话配置
     */
    data class ThreadConfig(
        val model: String? = null,
        val cwd: String? = null,
        val approvalPolicy: String? = null,  // "never", "unlessTrusted", "always"
        val sandbox: String? = null  // "readOnly", "workspaceWrite", "dangerFullAccess"
    )

    /**
     * Codex 事件（统一格式）
     */
    sealed class CodexEvent {
        data class ThreadCreated(val threadId: String, val thread: ThreadInfo) : CodexEvent()
        data class ThreadResumed(val threadId: String, val thread: ThreadInfo) : CodexEvent()
        data class ThreadArchived(val threadId: String) : CodexEvent()

        data class TurnStarted(val threadId: String, val turnId: String, val turn: TurnInfo) : CodexEvent()
        data class TurnCompleted(val threadId: String, val turnId: String, val turn: TurnInfo) : CodexEvent()
        data class TurnInterrupted(val threadId: String, val turnId: String) : CodexEvent()
        data class TurnError(val threadId: String, val turnId: String, val error: String) : CodexEvent()

        data class ItemStarted(val item: ThreadItem) : CodexEvent()
        data class ItemCompleted(val item: ThreadItem) : CodexEvent()

        data class StreamingContent(
            val threadId: String,
            val itemId: String,
            val contentType: String,  // "text", "reasoning", "command_output"
            val content: String
        ) : CodexEvent()

        data class CommandApprovalRequired(
            val requestId: String,
            val threadId: String,
            val turnId: String,
            val command: String,
            val cwd: String?,
            val reason: String?,
            val risk: String?
        ) : CodexEvent()

        data class FileChangeApprovalRequired(
            val requestId: String,
            val threadId: String,
            val turnId: String,
            val changes: List<FileChange>,
            val reason: String?
        ) : CodexEvent()

        data class TokenUsage(val threadId: String, val usage: com.asakii.codex.agent.sdk.appserver.TokenUsage) : CodexEvent()

        data class Error(val message: String, val cause: Throwable? = null) : CodexEvent()
    }

    /**
     * Approval 请求 (保持向后兼容)
     */
    data class ApprovalRequest(
        val threadId: String,
        val turnId: String,
        val requestId: String,
        val type: ApprovalType,
        val command: String? = null,
        val changes: List<FileChange>? = null,
        val reason: String? = null
    )

    enum class ApprovalType {
        COMMAND, FILE_CHANGE
    }

    /**
     * 启动后端
     */
    suspend fun start() {
        if (isRunning.getAndSet(true)) {
            throw IllegalStateException("CodexBackendProvider is already running")
        }

        logger.info("Starting Codex backend provider (app-server mode)...")

        try {
            // 1. 启动 App-Server 客户端
            val codexPathObj = codexPath?.let { Paths.get(it) }
            val workingDirPath = Paths.get(workingDirectory)

            appServerClient = CodexAppServerClient.create(
                codexPath = codexPathObj,
                workingDirectory = workingDirPath,
                scope = scope
            )

            logger.info("Codex app-server process started")

            // 2. 执行初始化握手
            val initResult = appServerClient!!.initialize(
                clientName = "claude-code-plus",
                clientTitle = "Claude Code Plus",
                clientVersion = "1.0.0"
            )

            logger.info("Codex app-server initialized, userAgent: ${initResult.userAgent}")

            // 3. 启动事件监听
            eventProcessingJob = scope.launch {
                listenToAppServerEvents()
            }

            logger.info("Codex backend provider started successfully")

        } catch (e: Exception) {
            isRunning.set(false)
            appServerClient?.close()
            appServerClient = null
            logger.error("Failed to start Codex backend provider", e)
            throw e
        }
    }

    /**
     * 停止后端
     */
    suspend fun stop() {
        if (!isRunning.getAndSet(false)) {
            logger.warn("CodexBackendProvider is not running")
            return
        }

        logger.info("Stopping Codex backend provider...")

        try {
            // 1. 取消事件处理
            eventProcessingJob?.cancel()

            // 2. 关闭 App-Server 客户端
            appServerClient?.close()
            appServerClient = null

            // 3. 清理状态
            threads.clear()
            _approvalRequests.close()

            logger.info("Codex backend provider stopped successfully")

        } catch (e: Exception) {
            logger.error("Error while stopping Codex backend provider", e)
            throw e
        }
    }

    /**
     * 创建新线程（会话）
     */
    suspend fun createThread(config: ThreadConfig = ThreadConfig()): String {
        ensureRunning()

        logger.info("Creating new thread with config: $config")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        val thread = client.startThread(
            model = config.model,
            cwd = config.cwd ?: workingDirectory,
            approvalPolicy = config.approvalPolicy,
            sandbox = config.sandbox
        )

        // 保存线程状态
        threads[thread.id] = ThreadState(
            threadId = thread.id,
            config = config,
            isActive = true
        )

        // 发送事件
        _events.emit(CodexEvent.ThreadCreated(thread.id, thread))

        logger.info("Thread created successfully: ${thread.id}")

        return thread.id
    }

    /**
     * 恢复线程
     */
    suspend fun resumeThread(threadId: String): ThreadInfo {
        ensureRunning()

        logger.info("Resuming thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        val thread = client.resumeThread(threadId)

        // 更新线程状态
        val existing = threads[threadId]
        if (existing != null) {
            existing.isActive = true
        } else {
            threads[threadId] = ThreadState(
                threadId = threadId,
                config = ThreadConfig(),
                isActive = true
            )
        }

        // 发送事件
        _events.emit(CodexEvent.ThreadResumed(threadId, thread))

        logger.info("Thread resumed successfully: $threadId")

        return thread
    }

    /**
     * 归档线程
     */
    suspend fun archiveThread(threadId: String) {
        ensureRunning()

        logger.info("Archiving thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        client.archiveThread(threadId)

        // 更新本地状态
        threads[threadId]?.isActive = false

        // 发送事件
        _events.emit(CodexEvent.ThreadArchived(threadId))

        logger.info("Thread archived successfully: $threadId")
    }

    /**
     * 列出所有线程
     */
    suspend fun listThreads(
        cursor: String? = null,
        limit: Int? = null
    ): ThreadListResult {
        ensureRunning()

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")
        return client.listThreads(cursor = cursor, limit = limit)
    }

    /**
     * 开始新的对话轮次
     */
    suspend fun startTurn(
        threadId: String,
        input: String,
        images: List<String> = emptyList(),
        cwd: String? = null,
        model: String? = null
    ): String {
        ensureRunning()

        val thread = threads[threadId]
            ?: throw IllegalArgumentException("Thread not found: $threadId")

        if (!thread.isActive) {
            throw IllegalStateException("Thread is not active: $threadId")
        }

        logger.info("Starting turn for thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        val turn = client.startTurn(
            threadId = threadId,
            message = input,
            images = images,
            cwd = cwd ?: workingDirectory,
            model = model
        )

        // 更新线程状态
        thread.currentTurnId = turn.id

        // 发送事件
        _events.emit(CodexEvent.TurnStarted(threadId, turn.id, turn))

        logger.info("Turn started successfully: ${turn.id} for thread: $threadId")

        return turn.id
    }

    /**
     * 中断当前轮次
     */
    suspend fun interruptTurn(threadId: String) {
        ensureRunning()

        val thread = threads[threadId]
            ?: throw IllegalArgumentException("Thread not found: $threadId")

        val turnId = thread.currentTurnId
            ?: throw IllegalStateException("No active turn for thread: $threadId")

        logger.info("Interrupting turn: $turnId for thread: $threadId")

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        client.interruptTurn(threadId, turnId)

        // 发送事件
        _events.emit(CodexEvent.TurnInterrupted(threadId, turnId))

        logger.info("Turn interrupted successfully: $turnId")
    }

    /**
     * 响应命令审批
     */
    suspend fun respondToCommandApproval(requestId: String, approved: Boolean, forSession: Boolean = false) {
        ensureRunning()

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        if (approved) {
            client.acceptCommand(requestId, forSession)
        } else {
            client.declineCommand(requestId)
        }

        logger.info("Command approval response sent: $requestId, approved=$approved")
    }

    /**
     * 响应文件修改审批
     */
    suspend fun respondToFileChangeApproval(requestId: String, approved: Boolean) {
        ensureRunning()

        val client = appServerClient ?: throw IllegalStateException("App-server client not available")

        if (approved) {
            client.acceptFileChange(requestId)
        } else {
            client.declineFileChange(requestId)
        }

        logger.info("File change approval response sent: $requestId, approved=$approved")
    }

    /**
     * 获取线程状态
     */
    fun getThreadState(threadId: String): ThreadState? {
        return threads[threadId]
    }

    /**
     * 获取所有活跃线程
     */
    fun getActiveThreads(): List<ThreadState> {
        return threads.values.filter { it.isActive }
    }

    /**
     * 监听 App-Server 事件并转换为统一格式
     */
    private suspend fun listenToAppServerEvents() {
        val client = appServerClient ?: return

        try {
            client.events.collect { event ->
                try {
                    mapAndEmitEvent(event)
                } catch (e: Exception) {
                    logger.error("Error processing App-Server event", e)
                    _events.emit(CodexEvent.Error("Failed to process event", e))
                }
            }
        } catch (e: Exception) {
            logger.error("Error in event listener", e)
            _events.emit(CodexEvent.Error("Event listener failed", e))
        }
    }

    /**
     * 将 App-Server 事件映射为统一格式
     */
    private suspend fun mapAndEmitEvent(event: AppServerEvent) {
        when (event) {
            is AppServerEvent.ThreadStarted -> {
                _events.emit(CodexEvent.ThreadCreated(event.thread.id, event.thread))
            }

            is AppServerEvent.TurnStarted -> {
                val threadId = threads.entries.find { it.value.currentTurnId == event.turn.id }?.key
                if (threadId != null) {
                    _events.emit(CodexEvent.TurnStarted(threadId, event.turn.id, event.turn))
                }
            }

            is AppServerEvent.TurnCompleted -> {
                val threadId = threads.entries.find { it.value.currentTurnId == event.turn.id }?.key
                if (threadId != null) {
                    threads[threadId]?.currentTurnId = null
                    _events.emit(CodexEvent.TurnCompleted(threadId, event.turn.id, event.turn))
                }
            }

            is AppServerEvent.ItemStarted -> {
                _events.emit(CodexEvent.ItemStarted(event.item))
            }

            is AppServerEvent.ItemCompleted -> {
                _events.emit(CodexEvent.ItemCompleted(event.item))
            }

            is AppServerEvent.AgentMessageDelta -> {
                _events.emit(
                    CodexEvent.StreamingContent(
                        threadId = "",  // 从 itemId 无法直接获取 threadId
                        itemId = event.itemId,
                        contentType = "text",
                        content = event.delta
                    )
                )
            }

            is AppServerEvent.ReasoningDelta -> {
                _events.emit(
                    CodexEvent.StreamingContent(
                        threadId = "",
                        itemId = event.itemId,
                        contentType = "reasoning",
                        content = event.delta
                    )
                )
            }

            is AppServerEvent.CommandOutputDelta -> {
                _events.emit(
                    CodexEvent.StreamingContent(
                        threadId = "",
                        itemId = event.itemId,
                        contentType = "command_output",
                        content = event.delta
                    )
                )
            }

            is AppServerEvent.CommandApprovalRequired -> {
                _events.emit(
                    CodexEvent.CommandApprovalRequired(
                        requestId = event.requestId,
                        threadId = event.threadId,
                        turnId = event.turnId,
                        command = event.command,
                        cwd = event.cwd,
                        reason = event.reason,
                        risk = event.risk
                    )
                )

                // 同时发送到 approvalRequests channel (向后兼容)
                _approvalRequests.send(
                    ApprovalRequest(
                        threadId = event.threadId,
                        turnId = event.turnId,
                        requestId = event.requestId,
                        type = ApprovalType.COMMAND,
                        command = event.command,
                        reason = event.reason
                    )
                )
            }

            is AppServerEvent.FileChangeApprovalRequired -> {
                _events.emit(
                    CodexEvent.FileChangeApprovalRequired(
                        requestId = event.requestId,
                        threadId = event.threadId,
                        turnId = event.turnId,
                        changes = event.changes,
                        reason = event.reason
                    )
                )

                // 同时发送到 approvalRequests channel (向后兼容)
                _approvalRequests.send(
                    ApprovalRequest(
                        threadId = event.threadId,
                        turnId = event.turnId,
                        requestId = event.requestId,
                        type = ApprovalType.FILE_CHANGE,
                        changes = event.changes,
                        reason = event.reason
                    )
                )
            }

            is AppServerEvent.TokenUsageUpdated -> {
                _events.emit(CodexEvent.TokenUsage(event.threadId, event.usage))
            }

            is AppServerEvent.Error -> {
                _events.emit(CodexEvent.Error(event.message))
            }
        }
    }

    /**
     * 确保后端正在运行
     */
    private fun ensureRunning() {
        if (!isRunning.get()) {
            throw IllegalStateException("CodexBackendProvider is not running")
        }
    }

    /**
     * 检查后端是否正在运行
     */
    val running: Boolean get() = isRunning.get()
}
