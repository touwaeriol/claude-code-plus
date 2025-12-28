package com.asakii.codex.agent.sdk.appserver

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonElement
import java.io.Closeable
import java.nio.file.Path

/**
 * Codex App-Server 高层 API 客户端
 *
 * 提供与 codex app-server 交互的高层 API:
 * - 初始化握手
 * - 线程管理 (创建、恢复、列表)
 * - 回合管理 (开始、中断)
 * - 事件流处理
 * - 审批流程
 *
 * 使用示例:
 * ```kotlin
 * val client = CodexAppServerClient.create()
 * client.initialize()
 *
 * val thread = client.startThread()
 * val turn = client.startTurn(thread.id, "Hello, Codex!")
 *
 * client.events.collect { event ->
 *     when (event) {
 *         is AppServerEvent.AgentMessageDelta -> print(event.delta)
 *         is AppServerEvent.TurnCompleted -> println("\nDone!")
 *         // ...
 *     }
 * }
 *
 * client.close()
 * ```
 */
class CodexAppServerClient private constructor(
    private val process: CodexAppServerProcess,
    private val scope: CoroutineScope
) : Closeable {

    private val rpc = process.client
    private var initialized = false

    private val _events = MutableSharedFlow<AppServerEvent>(extraBufferCapacity = 100)
    val events: SharedFlow<AppServerEvent> = _events.asSharedFlow()

    private var eventProcessingJob: Job? = null

    init {
        startEventProcessing()
    }

    private fun startEventProcessing() {
        eventProcessingJob = scope.launch {
            // 处理通知事件
            launch {
                rpc.notifications.collect { notification ->
                    processNotification(notification)
                }
            }

            // 处理服务器请求 (审批)
            launch {
                rpc.serverRequests.collect { request ->
                    processServerRequest(request)
                }
            }
        }
    }

    private suspend fun processNotification(notification: JsonRpcNotification) {
        val event = when (notification.method) {
            "thread/started" -> {
                notification.params?.let {
                    AppServerEvent.ThreadStarted(
                        kotlinx.serialization.json.Json.decodeFromJsonElement(
                            ThreadInfo.serializer(),
                            it.jsonObject["thread"]!!
                        )
                    )
                }
            }
            "turn/started" -> {
                notification.params?.let {
                    AppServerEvent.TurnStarted(
                        kotlinx.serialization.json.Json.decodeFromJsonElement(
                            TurnInfo.serializer(),
                            it.jsonObject["turn"]!!
                        )
                    )
                }
            }
            "turn/completed" -> {
                notification.params?.let {
                    AppServerEvent.TurnCompleted(
                        kotlinx.serialization.json.Json.decodeFromJsonElement(
                            TurnInfo.serializer(),
                            it.jsonObject["turn"]!!
                        )
                    )
                }
            }
            "item/started" -> {
                notification.params?.let {
                    AppServerEvent.ItemStarted(
                        kotlinx.serialization.json.Json.decodeFromJsonElement(
                            ThreadItem.serializer(),
                            it.jsonObject["item"]!!
                        )
                    )
                }
            }
            "item/completed" -> {
                notification.params?.let {
                    AppServerEvent.ItemCompleted(
                        kotlinx.serialization.json.Json.decodeFromJsonElement(
                            ThreadItem.serializer(),
                            it.jsonObject["item"]!!
                        )
                    )
                }
            }
            "item/agentMessage/delta" -> {
                notification.params?.let { params ->
                    val obj = params.jsonObject
                    AppServerEvent.AgentMessageDelta(
                        itemId = obj["itemId"]?.jsonPrimitive?.content ?: "",
                        delta = obj["delta"]?.jsonPrimitive?.content ?: ""
                    )
                }
            }
            "item/reasoning/summaryTextDelta" -> {
                notification.params?.let { params ->
                    val obj = params.jsonObject
                    AppServerEvent.ReasoningDelta(
                        itemId = obj["itemId"]?.jsonPrimitive?.content ?: "",
                        summaryIndex = obj["summaryIndex"]?.jsonPrimitive?.int ?: 0,
                        delta = obj["delta"]?.jsonPrimitive?.content ?: ""
                    )
                }
            }
            "item/commandExecution/outputDelta" -> {
                notification.params?.let { params ->
                    val obj = params.jsonObject
                    AppServerEvent.CommandOutputDelta(
                        itemId = obj["itemId"]?.jsonPrimitive?.content ?: "",
                        delta = obj["delta"]?.jsonPrimitive?.content ?: ""
                    )
                }
            }
            "thread/tokenUsage/updated" -> {
                notification.params?.let { params ->
                    val obj = params.jsonObject
                    AppServerEvent.TokenUsageUpdated(
                        threadId = obj["threadId"]?.jsonPrimitive?.content ?: "",
                        usage = kotlinx.serialization.json.Json.decodeFromJsonElement(
                            TokenUsage.serializer(),
                            obj["usage"]!!
                        )
                    )
                }
            }
            "error" -> {
                notification.params?.let { params ->
                    val obj = params.jsonObject
                    AppServerEvent.Error(
                        message = obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content ?: "Unknown error"
                    )
                }
            }
            else -> null
        }

        event?.let { _events.emit(it) }
    }

    private suspend fun processServerRequest(request: ServerRequest) {
        val event = when (request) {
            is ServerRequest.CommandApproval -> {
                AppServerEvent.CommandApprovalRequired(
                    requestId = request.requestId,
                    itemId = request.params.itemId,
                    threadId = request.params.threadId,
                    turnId = request.params.turnId,
                    command = request.params.command,
                    cwd = request.params.cwd,
                    reason = request.params.reason,
                    risk = request.params.risk
                )
            }
            is ServerRequest.FileChangeApproval -> {
                AppServerEvent.FileChangeApprovalRequired(
                    requestId = request.requestId,
                    itemId = request.params.itemId,
                    threadId = request.params.threadId,
                    turnId = request.params.turnId,
                    changes = request.params.changes,
                    reason = request.params.reason
                )
            }
        }
        _events.emit(event)
    }

    // ============== 初始化 ==============

    /**
     * 初始化连接 (必须首先调用)
     */
    suspend fun initialize(
        clientName: String = "claude-code-plus",
        clientTitle: String = "Claude Code Plus",
        clientVersion: String = "1.0.0"
    ): InitializeResult {
        if (initialized) {
            throw CodexAppServerException("Already initialized")
        }

        val params = InitializeParams(
            clientInfo = ClientInfo(
                name = clientName,
                title = clientTitle,
                version = clientVersion
            )
        )

        val result: InitializeResult = rpc.request("initialize", params)
        rpc.notify("initialized")
        initialized = true

        return result
    }

    // ============== 线程管理 ==============

    /**
     * 创建新线程
     */
    suspend fun startThread(
        model: String? = null,
        cwd: String? = null,
        approvalPolicy: String? = null,
        sandbox: String? = null
    ): ThreadInfo {
        checkInitialized()

        val params = ThreadStartParams(
            model = model,
            cwd = cwd,
            approvalPolicy = approvalPolicy,
            sandbox = sandbox
        )

        val result: ThreadStartResult = rpc.request("thread/start", params)
        return result.thread
    }

    /**
     * 恢复已有线程
     */
    suspend fun resumeThread(threadId: String): ThreadInfo {
        checkInitialized()

        val params = ThreadResumeParams(threadId = threadId)
        val result: ThreadStartResult = rpc.request("thread/resume", params)
        return result.thread
    }

    /**
     * 归档线程
     */
    suspend fun archiveThread(threadId: String) {
        checkInitialized()

        val params = buildMap<String, Any> {
            put("threadId", threadId)
        }

        rpc.request<Unit>("thread/archive", params)
    }

    /**
     * 列出所有线程
     */
    suspend fun listThreads(
        cursor: String? = null,
        limit: Int? = null,
        modelProviders: List<String>? = null
    ): ThreadListResult {
        checkInitialized()

        val params = ThreadListParams(
            cursor = cursor,
            limit = limit,
            modelProviders = modelProviders
        )

        return rpc.request<ThreadListResult>("thread/list", params)
    }

    // ============== 回合管理 ==============

    /**
     * 开始新回合 (发送用户消息)
     */
    suspend fun startTurn(
        threadId: String,
        message: String,
        images: List<String> = emptyList(),
        cwd: String? = null,
        model: String? = null,
        approvalPolicy: String? = null
    ): TurnInfo {
        checkInitialized()

        val input = buildList {
            add(UserInput.Text(message))
            images.forEach { add(UserInput.LocalImage(it)) }
        }

        val params = TurnStartParams(
            threadId = threadId,
            input = input,
            cwd = cwd,
            model = model,
            approvalPolicy = approvalPolicy
        )

        val result: TurnStartResult = rpc.request("turn/start", params)
        return result.turn
    }

    /**
     * 中断当前回合
     */
    suspend fun interruptTurn(threadId: String, turnId: String) {
        checkInitialized()

        val params = TurnInterruptParams(
            threadId = threadId,
            turnId = turnId
        )

        rpc.request<Unit>("turn/interrupt", params)
    }

    // ============== 审批响应 ==============

    /**
     * 接受命令执行
     */
    suspend fun acceptCommand(requestId: String, forSession: Boolean = false) {
        val response = ApprovalResponse(
            decision = "accept",
            acceptSettings = if (forSession) AcceptSettings(forSession = true) else null
        )
        rpc.respondToServerRequest(requestId, response)
    }

    /**
     * 拒绝命令执行
     */
    suspend fun declineCommand(requestId: String) {
        val response = ApprovalResponse(decision = "decline")
        rpc.respondToServerRequest(requestId, response)
    }

    /**
     * 接受文件修改
     */
    suspend fun acceptFileChange(requestId: String) {
        val response = ApprovalResponse(decision = "accept")
        rpc.respondToServerRequest(requestId, response)
    }

    /**
     * 拒绝文件修改
     */
    suspend fun declineFileChange(requestId: String) {
        val response = ApprovalResponse(decision = "decline")
        rpc.respondToServerRequest(requestId, response)
    }

    // ============== 账户相关 ==============

    /**
     * 读取账户信息
     */
    suspend fun readAccount(refreshToken: Boolean = false): AccountReadResult {
        checkInitialized()

        val params = mapOf("refreshToken" to refreshToken)
        return rpc.request<AccountReadResult>("account/read", params)
    }

    // ============== 辅助方法 ==============

    private fun checkInitialized() {
        if (!initialized) {
            throw CodexAppServerException("Not initialized. Call initialize() first.")
        }
    }

    val isAlive: Boolean get() = process.isAlive

    override fun close() {
        eventProcessingJob?.cancel()
        process.close()
    }

    companion object {
        /**
         * 创建并启动 Codex App-Server 客户端
         */
        fun create(
            codexPath: Path? = null,
            workingDirectory: Path? = null,
            env: Map<String, String> = emptyMap(),
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        ): CodexAppServerClient {
            val process = CodexAppServerProcess.spawn(
                codexPath = codexPath,
                workingDirectory = workingDirectory,
                env = env,
                scope = scope
            )

            return CodexAppServerClient(process, scope)
        }
    }
}

// ============== 事件类型 ==============

/**
 * App-Server 事件
 */
sealed class AppServerEvent {
    // 线程事件
    data class ThreadStarted(val thread: ThreadInfo) : AppServerEvent()

    // 回合事件
    data class TurnStarted(val turn: TurnInfo) : AppServerEvent()
    data class TurnCompleted(val turn: TurnInfo) : AppServerEvent()

    // Item 事件
    data class ItemStarted(val item: ThreadItem) : AppServerEvent()
    data class ItemCompleted(val item: ThreadItem) : AppServerEvent()

    // 增量事件
    data class AgentMessageDelta(val itemId: String, val delta: String) : AppServerEvent()
    data class ReasoningDelta(val itemId: String, val summaryIndex: Int, val delta: String) : AppServerEvent()
    data class CommandOutputDelta(val itemId: String, val delta: String) : AppServerEvent()

    // 审批请求
    data class CommandApprovalRequired(
        val requestId: String,
        val itemId: String,
        val threadId: String,
        val turnId: String,
        val command: String,
        val cwd: String?,
        val reason: String?,
        val risk: String?
    ) : AppServerEvent()

    data class FileChangeApprovalRequired(
        val requestId: String,
        val itemId: String,
        val threadId: String,
        val turnId: String,
        val changes: List<FileChange>,
        val reason: String?
    ) : AppServerEvent()

    // 使用量
    data class TokenUsageUpdated(val threadId: String, val usage: TokenUsage) : AppServerEvent()

    // 错误
    data class Error(val message: String) : AppServerEvent()
}

// 添加 jsonPrimitive 和 jsonObject 扩展
private val JsonElement.jsonPrimitive get() = this as? kotlinx.serialization.json.JsonPrimitive
private val JsonElement.jsonObject get() = this as kotlinx.serialization.json.JsonObject
private val kotlinx.serialization.json.JsonPrimitive.int get() = this.content.toInt()
