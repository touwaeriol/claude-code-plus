package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.adapter.CodexAppServerStreamAdapter
import com.asakii.ai.agent.sdk.adapter.UiStreamAdapter
import com.asakii.ai.agent.sdk.capabilities.AgentCapabilities
import com.asakii.ai.agent.sdk.capabilities.AiPermissionMode
import com.asakii.ai.agent.sdk.capabilities.CodexCapabilities
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.connect.normalize
import com.asakii.ai.agent.sdk.model.ImageContent
import com.asakii.ai.agent.sdk.model.TextContent
import com.asakii.ai.agent.sdk.model.UiError
import com.asakii.ai.agent.sdk.model.UiStreamEvent
import com.asakii.claude.agent.sdk.types.McpReconnectResponse
import com.asakii.claude.agent.sdk.types.McpServerStatusInfo
import com.asakii.codex.agent.sdk.ApprovalMode
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.SandboxMode
import com.asakii.codex.agent.sdk.ThreadOptions
import com.asakii.codex.agent.sdk.appserver.AppServerEvent
import com.asakii.codex.agent.sdk.appserver.McpServerStatus
import com.asakii.codex.agent.sdk.appserver.PatchChangeKind
import com.asakii.codex.agent.sdk.appserver.ReasoningSummary
import com.asakii.codex.agent.sdk.appserver.SandboxPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.UUID

internal class CodexAgentClientImpl(
    private val permissionRequester: PermissionRequester? = null,
    private val appServerFactory: CodexAppServerApiFactory = DefaultCodexAppServerApiFactory,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : UnifiedAgentClient {

    override val provider: AiAgentProvider = AiAgentProvider.CODEX

    private val eventFlow = MutableSharedFlow<UiStreamEvent>(extraBufferCapacity = 64)
    private val sendMutex = Mutex()
    private val streamAdapter = CodexAppServerStreamAdapter(sessionIdProvider = { threadId })
    private val uiAdapter = UiStreamAdapter()

    private var client: CodexAppServerApi? = null
    private var threadId: String? = null
    private var currentTurnId: String? = null
    private var currentThreadOptions: ThreadOptions? = null
    private var currentClientOptions: CodexClientOptions? = null
    private var approvalPolicy: ApprovalMode? = null
    private var currentPermissionMode: AiPermissionMode = AiPermissionMode.DEFAULT
    private var activeCancellationJob: Job? = null
    private var eventRelayJob: Job? = null
    private var internalEvents = MutableSharedFlow<AppServerEvent>(
        replay = 64,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val savedImages = mutableListOf<Path>()

    override suspend fun connect(options: AiAgentConnectOptions) {
        val normalized = options.normalize()
        require(normalized.provider == AiAgentProvider.CODEX) {
            "CodexAgentClientImpl only supports Codex provider"
        }

        val codexClientOptions = normalized.codexClientOptions ?: CodexClientOptions()
        val codexThreadOptions = normalized.codexThreadOptions ?: ThreadOptions()

        disconnect()

        // 构建 AppServer 级别配置（MCP、features），使 mcpServerStatus/list 可查询
        val appServerConfig = buildAppServerConfig(codexThreadOptions)
        val enrichedClientOptions = codexClientOptions.copy(
            appServerConfigOverrides = codexClientOptions.appServerConfigOverrides + appServerConfig
        )

        val newClient = appServerFactory.create(enrichedClientOptions, codexThreadOptions, scope)
        newClient.initialize(
            clientName = "claude-code-plus",
            clientTitle = "Claude Code Plus",
            clientVersion = "1.0.0"
        )

        client = newClient
        currentClientOptions = enrichedClientOptions
        startEventRelay(newClient)

        val thread = if (normalized.codexThreadId != null) {
            newClient.resumeThread(normalized.codexThreadId)
        } else {
            newClient.startThread(
                model = codexThreadOptions.model,
                cwd = codexThreadOptions.workingDirectory,
                approvalPolicy = codexThreadOptions.approvalPolicy?.wireValue,
                sandbox = codexThreadOptions.sandboxMode?.wireValue,
                config = buildThreadOnlyConfig(codexThreadOptions),
                developerInstructions = codexThreadOptions.developerInstructions
            )
        }

        threadId = thread.id
        currentThreadOptions = codexThreadOptions
        approvalPolicy = codexThreadOptions.approvalPolicy
        currentPermissionMode = codexThreadOptions.approvalPolicy.toPermissionMode()

        normalized.initialPrompt?.let {
            sendMessage(AgentMessageInput(text = it, sessionId = normalized.sessionId))
        }
    }

    override suspend fun sendMessage(input: AgentMessageInput) {
        val activeClient = client ?: error("Codex session not connected")
        val activeThreadId = threadId ?: error("Codex thread not available")
        val threadOptions = currentThreadOptions ?: ThreadOptions()

        sendMutex.withLock {
            val cancellationJob = Job(scope.coroutineContext.job)
            activeCancellationJob = cancellationJob
            try {
                val payload = buildInputPayload(input)
                val sandboxPolicy = buildSandboxPolicy(threadOptions)
                val effort = threadOptions.modelReasoningEffort?.wireValue
                val summary = threadOptions.modelReasoningSummary?.toReasoningSummaryOrNull()
                val turn = activeClient.startTurn(
                    threadId = activeThreadId,
                    message = payload.text,
                    images = payload.images,
                    cwd = threadOptions.workingDirectory,
                    model = threadOptions.model,
                    approvalPolicy = threadOptions.approvalPolicy?.wireValue,
                    sandboxPolicy = sandboxPolicy,
                    effort = effort,
                    summary = summary
                )
                currentTurnId = turn.id

                val turnEvents = buildTurnEventFlow(turn.id, activeThreadId)
                withContext(cancellationJob) {
                    turnEvents.collect { event ->
                        streamAdapter.convert(event).forEach { normalized ->
                            uiAdapter.convert(normalized).forEach { eventFlow.emit(it) }
                        }
                    }
                }
            } catch (t: Throwable) {
                eventFlow.emit(UiError("Codex session failed: ${t.message}"))
                throw t
            } finally {
                cancellationJob.cancel()
                activeCancellationJob = null
                currentTurnId = null
            }
        }
    }

    override fun streamEvents(): Flow<UiStreamEvent> = eventFlow.asSharedFlow()

    override suspend fun interrupt() {
        val activeClient = client ?: return
        val activeThreadId = threadId ?: return
        val turnId = currentTurnId
        if (turnId != null) {
            runCatching { activeClient.interruptTurn(activeThreadId, turnId) }
        }
        // Allow TurnCompleted(Interrupted) to flow through for queue processing.
        // Fallback to cancellation if the turn does not complete promptly.
        val job = activeCancellationJob
        if (job != null) {
            scope.launch {
                delay(3000)
                if (activeCancellationJob === job && job.isActive) {
                    job.cancel()
                }
            }
        }
    }

    override suspend fun disconnect() {
        activeCancellationJob?.cancelAndJoin()
        eventRelayJob?.cancelAndJoin()
        client?.close()
        clearSavedImages()
        client = null
        threadId = null
        currentTurnId = null
        currentThreadOptions = null
        currentClientOptions = null
        approvalPolicy = null
        currentPermissionMode = AiPermissionMode.DEFAULT
        eventRelayJob = null
        activeCancellationJob = null
    }

    override fun isConnected(): Boolean {
        return client != null && threadId != null
    }

    override fun getProviderSessionId(): String? = threadId

    override fun getCapabilities(): AgentCapabilities = CodexCapabilities

    override suspend fun setModel(model: String): String? {
        checkCapability(getCapabilities().canSwitchModel, "setModel")
        val options = currentThreadOptions ?: ThreadOptions()
        currentThreadOptions = options.copy(model = model)
        return model
    }

    override suspend fun setPermissionMode(mode: AiPermissionMode) {
        val caps = getCapabilities()
        checkCapability(caps.canSwitchPermissionMode, "setPermissionMode")
        require(mode in caps.supportedPermissionModes) {
            "Mode $mode is not supported. Supported: ${caps.supportedPermissionModes}"
        }
        currentPermissionMode = mode
        val mappedPolicy = mode.toApprovalMode()
        approvalPolicy = mappedPolicy
        val options = currentThreadOptions ?: ThreadOptions()
        currentThreadOptions = options.copy(approvalPolicy = mappedPolicy)
    }

    override suspend fun setSandboxMode(mode: SandboxMode) {
        val options = currentThreadOptions ?: ThreadOptions()
        currentThreadOptions = options.copy(sandboxMode = mode)
    }

    override fun getCurrentSandboxMode(): SandboxMode? {
        return currentThreadOptions?.sandboxMode
    }

    override suspend fun setMaxThinkingTokens(maxThinkingTokens: Int?) {
        throw UnsupportedOperationException(
            "setMaxThinkingTokens is not supported by ${provider.name}"
        )
    }

    override fun getCurrentPermissionMode(): AiPermissionMode? = currentPermissionMode

    override suspend fun getMcpStatus(): List<McpServerStatusInfo> {
        val activeClient = client ?: return emptyList()
        val statuses = fetchMcpServerStatuses(activeClient)
        return statuses.map { status ->
            McpServerStatusInfo(
                name = status.name,
                status = status.toDisplayStatus(),
                serverInfo = status.toServerInfoJson()
            )
        }
    }

    override suspend fun reconnectMcp(serverName: String): McpReconnectResponse {
        val activeClient = client ?: return McpReconnectResponse(
            success = false,
            serverName = serverName,
            status = null,
            toolsCount = 0,
            error = "Codex session not connected"
        )

        val activeThreadId = threadId
        val status = runCatching { fetchMcpServerStatuses(activeClient) }.getOrNull()
        val serverStatus = status?.firstOrNull { it.name == serverName }
        if (status != null && serverStatus == null) {
            return McpReconnectResponse(
                success = false,
                serverName = serverName,
                status = null,
                toolsCount = 0,
                error = "MCP server not found"
            )
        }

        if (serverStatus?.authStatus?.lowercase() == "notloggedin") {
            runCatching { activeClient.startMcpOauthLogin(name = serverName) }
        }

        return runCatching {
            activeCancellationJob?.cancel()
            sendMutex.withLock {
                restartAppServer(activeThreadId)
            }
            val refreshedClient = client ?: error("Codex session not connected")
            val refreshedStatus = fetchMcpServerStatuses(refreshedClient)
                .firstOrNull { it.name == serverName }
                ?: return McpReconnectResponse(
                    success = false,
                    serverName = serverName,
                    status = null,
                    toolsCount = 0,
                    error = "MCP server not found"
                )
            McpReconnectResponse(
                success = true,
                serverName = serverName,
                status = refreshedStatus.toDisplayStatus(),
                toolsCount = refreshedStatus.tools.size
            )
        }.getOrElse { throwable ->
            McpReconnectResponse(
                success = false,
                serverName = serverName,
                status = null,
                toolsCount = 0,
                error = throwable.message ?: "Unknown error"
            )
        }
    }

    override suspend fun startMcpOauthLogin(serverName: String): String? {
        val activeClient = client ?: return null
        val status = fetchMcpServerStatuses(activeClient).firstOrNull { it.name == serverName } ?: return null
        val authStatus = status.authStatus?.lowercase()
        if (authStatus != "notloggedin") return null

        return runCatching { activeClient.startMcpOauthLogin(name = serverName) }
            .getOrNull()
            ?.authorizationUrl
    }

    private fun buildInputPayload(input: AgentMessageInput): InputPayload {
        val images = input.content
            ?.filterIsInstance<ImageContent>()
            ?.map { persistImage(it) }
            ?: emptyList()

        val text = input.text ?: input.content
            ?.filterIsInstance<TextContent>()
            ?.map { it.text }
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")
            .orEmpty()

        if (text.isBlank() && images.isEmpty()) {
            error("text and content cannot both be null")
        }

        return InputPayload(text = text, images = images)
    }

    private fun buildSandboxPolicy(options: ThreadOptions): SandboxPolicy? {
        return when (options.sandboxMode) {
            SandboxMode.READ_ONLY -> SandboxPolicy.ReadOnly
            SandboxMode.DANGER_FULL_ACCESS -> SandboxPolicy.DangerFullAccess
            SandboxMode.WORKSPACE_WRITE -> SandboxPolicy.WorkspaceWrite(
                writableRoots = options.additionalDirectories,
                networkAccess = options.networkAccessEnabled ?: false
            )
            null -> null
        }
    }

    private fun String.toReasoningSummaryOrNull(): ReasoningSummary? = when (lowercase()) {
        "auto" -> ReasoningSummary.Auto
        "concise" -> ReasoningSummary.Concise
        "detailed" -> ReasoningSummary.Detailed
        "none" -> ReasoningSummary.None
        else -> null
    }

    private fun persistImage(block: ImageContent): String {
        val targetDir = resolveImageDirectory()
        val extension = imageFileExtension(block.mediaType)
        val fileName = "codex-image-${UUID.randomUUID()}$extension"
        val targetPath = targetDir.resolve(fileName)
        val bytes = decodeBase64(block.data)
        Files.write(targetPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        savedImages.add(targetPath)
        return targetPath.toAbsolutePath().toString()
    }

    private fun decodeBase64(data: String): ByteArray {
        val trimmed = data.trim()
        val payload = if (trimmed.startsWith("data:", ignoreCase = true)) {
            trimmed.substringAfter(',', trimmed)
        } else {
            trimmed
        }
        val normalized = payload.replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(normalized)
    }

    private fun resolveImageDirectory(): Path {
        val workspace = currentThreadOptions?.workingDirectory?.let { Path.of(it) }
        if (workspace != null) {
            val candidate = workspace.resolve(".claude-code-plus").resolve("codex-images")
            try {
                Files.createDirectories(candidate)
                return candidate
            } catch (_: Exception) {
                // Fall back to temp directory.
            }
        }
        val tmpRoot = System.getProperty("java.io.tmpdir")
        val tmpDir = Paths.get(tmpRoot, "claude-code-plus", "codex-images")
        Files.createDirectories(tmpDir)
        return tmpDir
    }

    private fun imageFileExtension(mediaType: String): String {
        val normalized = mediaType.lowercase().substringBefore(';').trim()
        return when (normalized) {
            "image/png" -> ".png"
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/bmp" -> ".bmp"
            "image/svg+xml" -> ".svg"
            else -> ".img"
        }
    }

    private fun clearSavedImages() {
        val iterator = savedImages.iterator()
        while (iterator.hasNext()) {
            val path = iterator.next()
            runCatching { Files.deleteIfExists(path) }
            iterator.remove()
        }
    }

    private data class InputPayload(
        val text: String,
        val images: List<String>
    )

    private fun startEventRelay(newClient: CodexAppServerApi) {
        internalEvents = MutableSharedFlow(
            replay = 64,
            extraBufferCapacity = 128,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        eventRelayJob?.cancel()
        eventRelayJob = scope.launch {
            newClient.events.collect { event ->
                internalEvents.emit(event)
                when (event) {
                    is AppServerEvent.CommandApprovalRequired -> {
                        launch { handleCommandApproval(event) }
                    }
                    is AppServerEvent.FileChangeApprovalRequired -> {
                        launch { handleFileChangeApproval(event) }
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun restartAppServer(threadIdToResume: String?) {
        // currentClientOptions 已包含 appServerConfigOverrides（在 connect() 中设置）
        val options = currentClientOptions ?: CodexClientOptions()
        val threadOptions = currentThreadOptions ?: ThreadOptions()

        activeCancellationJob?.cancelAndJoin()
        eventRelayJob?.cancelAndJoin()
        client?.close()

        val newClient = appServerFactory.create(options, threadOptions, scope)
        newClient.initialize(
            clientName = "claude-code-plus",
            clientTitle = "Claude Code Plus",
            clientVersion = "1.0.0"
        )

        client = newClient
        startEventRelay(newClient)

        val thread = if (threadIdToResume != null) {
            newClient.resumeThread(threadIdToResume)
        } else {
            newClient.startThread(
                model = threadOptions.model,
                cwd = threadOptions.workingDirectory,
                approvalPolicy = threadOptions.approvalPolicy?.wireValue,
                sandbox = threadOptions.sandboxMode?.wireValue,
                config = buildThreadOnlyConfig(threadOptions),
                developerInstructions = threadOptions.developerInstructions
            )
        }
        threadId = thread.id
        currentTurnId = null
    }

    private fun buildTurnEventFlow(turnId: String, threadId: String): Flow<AppServerEvent> = channelFlow {
        val job = scope.launch {
            var inTurn = false
            internalEvents.collect { event ->
                when (event) {
                    is AppServerEvent.TurnStarted -> {
                        if (event.threadId == threadId && event.turn.id == turnId) {
                            inTurn = true
                            send(event)
                        }
                    }
                    is AppServerEvent.TurnCompleted -> {
                        if (event.threadId == threadId && event.turn.id == turnId) {
                            inTurn = true
                            send(event)
                            close()
                        }
                    }
                    is AppServerEvent.ItemStarted -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            inTurn = true
                            send(event)
                        }
                    }
                    is AppServerEvent.ItemCompleted -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            inTurn = true
                            send(event)
                        }
                    }
                    is AppServerEvent.AgentMessageDelta -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            inTurn = true
                            send(event)
                        }
                    }
                    is AppServerEvent.ReasoningDelta -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            inTurn = true
                            send(event)
                        }
                    }
                    is AppServerEvent.CommandOutputDelta -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            inTurn = true
                            send(event)
                        }
                    }
                    is AppServerEvent.TokenUsageUpdated -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            send(event)
                        }
                    }
                    is AppServerEvent.CommandApprovalRequired -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            send(event)
                        }
                    }
                    is AppServerEvent.FileChangeApprovalRequired -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            send(event)
                        }
                    }
                    is AppServerEvent.Error -> {
                        if (event.threadId == threadId && event.turnId == turnId) {
                            send(event)
                            if (!event.willRetry) {
                                close()
                            }
                        }
                    }
                    else -> {
                        if (inTurn) {
                            send(event)
                        }
                    }
                }
            }
        }
        awaitClose { job.cancel() }
    }

    private suspend fun handleCommandApproval(event: AppServerEvent.CommandApprovalRequired) {
        val bypass = currentPermissionMode == AiPermissionMode.BYPASS_PERMISSIONS
        val decision = if (bypass) {
            null
        } else {
            permissionRequester?.requestPermission(
                PermissionRequest(
                    toolName = "Bash",
                    inputJson = buildJsonObject {
                        event.command?.let { put("command", it) }
                        event.cwd?.let { put("cwd", it) }
                        event.reason?.let { put("reason", it) }
                        event.proposedExecpolicyAmendment?.let { amendment ->
                            put("proposedExecpolicyAmendment", buildJsonObject {
                                put("command", buildJsonArray {
                                    amendment.command.forEach { add(it) }
                                })
                            })
                        }
                    },
                    toolUseId = event.requestId
                )
            )
        }

        val approveByDefault = bypass || approvalPolicy == ApprovalMode.NEVER
        val approved = decision?.approved ?: approveByDefault
        if (approved) {
            client?.acceptCommand(event.rawId, forSession = false)
        } else {
            client?.declineCommand(event.rawId)
        }
    }

    private suspend fun handleFileChangeApproval(event: AppServerEvent.FileChangeApprovalRequired) {
        val autoApprove = currentPermissionMode == AiPermissionMode.BYPASS_PERMISSIONS ||
            currentPermissionMode == AiPermissionMode.ACCEPT_EDITS ||
            approvalPolicy == ApprovalMode.NEVER
        val decision = if (autoApprove) {
            null
        } else {
            permissionRequester?.requestPermission(
                PermissionRequest(
                    toolName = "Edit",
                    inputJson = buildJsonObject {
                        put("changes", buildJsonArray {
                            event.changes.forEach { change ->
                                add(buildJsonObject {
                                    put("path", change.path)
                                    put("kind", change.kind.toKindString())
                                    put("diff", change.diff)
                                })
                            }
                        })
                        event.reason?.let { put("reason", it) }
                        event.grantRoot?.let { put("grantRoot", it) }
                    },
                    toolUseId = event.requestId
                )
            )
        }

        val approved = decision?.approved ?: autoApprove
        if (approved) {
            client?.acceptFileChange(event.rawId)
        } else {
            client?.declineFileChange(event.rawId)
        }
    }

    private fun PatchChangeKind.toKindString(): String = when (this) {
        is PatchChangeKind.Add -> "add"
        is PatchChangeKind.Delete -> "delete"
        is PatchChangeKind.Update -> "update"
    }

    /**
     * 构建 AppServer 级别配置（features 等）。
     * MCP 配置改为线程级别传递，避免 app-server 级别配置不生效的问题。
     *
     * @param threadOptions 线程选项
     * @return AppServer 配置覆盖，格式为 key -> value
     */
    private fun buildAppServerConfig(threadOptions: ThreadOptions): Map<String, String> {
        val config = mutableMapOf<String, String>()

        // Web search 配置
        threadOptions.webSearchEnabled?.let { enabled ->
            config["features.web_search_request"] = enabled.toString()
        }

        // threadConfigOverrides 中的 features 相关配置
        threadOptions.threadConfigOverrides.forEach { (key, value) ->
            if (key.startsWith("features.")) {
                config[key] = when (value) {
                    is JsonPrimitive -> value.content
                    else -> value.toString()
                }
            }
        }

        return config
    }

    /**
     * 构建 Thread 级别配置（包含 MCP 配置，排除 features）。
     *
     * @param threadOptions 线程选项
     * @return Thread 配置，如果为空则返回 null
     */
    private fun buildThreadOnlyConfig(threadOptions: ThreadOptions): Map<String, JsonElement>? {
        val config = mutableMapOf<String, JsonElement>()

        threadOptions.mcpServers.forEach { (serverName, url) ->
            config["mcp_servers.$serverName.url"] = JsonPrimitive(url)
        }

        threadOptions.threadConfigOverrides.forEach { (key, value) ->
            if (!key.startsWith("features.")) {
                config[key] = value
            }
        }

        return config.takeIf { it.isNotEmpty() }
    }

    private fun checkCapability(supported: Boolean, method: String) {
        if (!supported) {
            throw UnsupportedOperationException(
                "$method is not supported by ${provider.name}"
            )
        }
    }

    private fun AiPermissionMode.toApprovalMode(): ApprovalMode = when (this) {
        AiPermissionMode.BYPASS_PERMISSIONS -> ApprovalMode.NEVER
        AiPermissionMode.DEFAULT,
        AiPermissionMode.ACCEPT_EDITS,
        AiPermissionMode.PLAN -> ApprovalMode.ON_REQUEST
    }

    private fun ApprovalMode?.toPermissionMode(): AiPermissionMode = when (this) {
        ApprovalMode.NEVER -> AiPermissionMode.BYPASS_PERMISSIONS
        ApprovalMode.ON_REQUEST,
        ApprovalMode.ON_FAILURE,
        ApprovalMode.UNTRUSTED,
        null -> AiPermissionMode.DEFAULT
    }

    private suspend fun fetchMcpServerStatuses(activeClient: CodexAppServerApi): List<McpServerStatus> {
        val result = mutableListOf<McpServerStatus>()
        var cursor: String? = null
        do {
            val page = activeClient.listMcpServerStatus(cursor = cursor, limit = null)
            result.addAll(page.data)
            cursor = page.nextCursor?.takeIf { it.isNotBlank() }
        } while (cursor != null)
        return result
    }

    private fun McpServerStatus.toDisplayStatus(): String = when (authStatus?.lowercase()) {
        "notloggedin" -> "needs-auth"
        else -> "connected"
    }

    private fun McpServerStatus.toServerInfoJson(): JsonElement = buildJsonObject {
        authStatus?.let { put("authStatus", it) }
        put("toolsCount", tools.size)
        put("resourcesCount", resources.size)
        put("resourceTemplatesCount", resourceTemplates.size)
    }
}
