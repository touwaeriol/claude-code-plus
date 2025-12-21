package com.asakii.server.rsocket

import com.asakii.rpc.api.RpcAssistantMessage
import com.asakii.rpc.api.RpcCapabilities
import com.asakii.rpc.api.RpcCommandExecutionBlock
import com.asakii.rpc.api.RpcCompactBoundaryMessage
import com.asakii.rpc.api.RpcSystemInitMessage
import com.asakii.rpc.api.RpcMcpServerInfo as RpcMcpServerInfoApi
import com.asakii.rpc.api.RpcCompactMetadata as RpcCompactMetadataApi
import com.asakii.rpc.api.RpcConnectOptions
import com.asakii.rpc.api.RpcConnectResult as RpcConnectResultApi
import com.asakii.rpc.api.RpcContentBlock as RpcContentBlockApi
import com.asakii.rpc.api.RpcContentBlockDeltaEvent
import com.asakii.rpc.api.RpcContentBlockStartEvent
import com.asakii.rpc.api.RpcContentBlockStopEvent
import com.asakii.rpc.api.RpcContentStatus as RpcContentStatusApi
import com.asakii.rpc.api.RpcDelta as RpcDeltaApi
import com.asakii.rpc.api.RpcErrorBlock
import com.asakii.rpc.api.RpcErrorMessage as RpcErrorMessageApi
import com.asakii.rpc.api.RpcFileChange as RpcFileChangeApi
import com.asakii.rpc.api.RpcFileChangeBlock
import com.asakii.rpc.api.RpcHistory as RpcHistoryApi
import com.asakii.rpc.api.RpcHistoryMetadata as RpcHistoryMetadataApi
import com.asakii.rpc.api.RpcHistoryResult as RpcHistoryResultApi
import com.asakii.rpc.api.RpcHistorySession as RpcHistorySessionApi
import com.asakii.rpc.api.RpcHistorySessionsResult as RpcHistorySessionsResultApi
import com.asakii.rpc.api.RpcTruncateHistoryResult as RpcTruncateHistoryResultApi
import com.asakii.rpc.api.RpcImageBlock
import com.asakii.rpc.api.RpcImageSource as RpcImageSourceApi
import com.asakii.rpc.api.RpcInputJsonDelta
import com.asakii.rpc.api.RpcMcpToolCallBlock
import com.asakii.rpc.api.RpcMessage as RpcMessageApi
import com.asakii.rpc.api.RpcMessageContent as RpcMessageContentApi
import com.asakii.rpc.api.RpcMessageDeltaEvent
import com.asakii.rpc.api.RpcMessageStartEvent
import com.asakii.rpc.api.RpcMessageStartInfo as RpcMessageStartInfoApi
import com.asakii.rpc.api.RpcMessageStopEvent
import com.asakii.rpc.api.RpcPermissionMode as RpcPermissionModeApi
import com.asakii.rpc.api.RpcProvider as RpcProviderApi
import com.asakii.rpc.api.RpcResultMessage as RpcResultMessageApi
import com.asakii.rpc.api.RpcSandboxMode as RpcSandboxModeApi
import com.asakii.rpc.api.RpcSessionStatus as RpcSessionStatusApi
import com.asakii.rpc.api.RpcSetModelResult as RpcSetModelResultApi
import com.asakii.rpc.api.RpcSetPermissionModeResult as RpcSetPermissionModeResultApi
import com.asakii.rpc.api.RpcSetMaxThinkingTokensResult as RpcSetMaxThinkingTokensResultApi
import com.asakii.rpc.api.RpcStatusResult as RpcStatusResultApi
import com.asakii.rpc.api.RpcStatusSystemMessage
import com.asakii.rpc.api.RpcStreamEvent as RpcStreamEventApi
import com.asakii.rpc.api.RpcStreamEventData as RpcStreamEventDataApi
import com.asakii.rpc.api.RpcTextBlock
import com.asakii.rpc.api.RpcTextDelta
import com.asakii.rpc.api.RpcThinkingBlock
import com.asakii.rpc.api.RpcThinkingDelta
import com.asakii.rpc.api.RpcTodoItem as RpcTodoItemApi
import com.asakii.rpc.api.RpcTodoListBlock
import com.asakii.rpc.api.RpcToolResultBlock
import com.asakii.rpc.api.RpcToolUseBlock
import com.asakii.rpc.api.RpcUnknownBlock
import com.asakii.rpc.api.RpcUsage as RpcUsageApi
import com.asakii.rpc.api.RpcUserMessage as RpcUserMessageApi
import com.asakii.rpc.api.RpcWebSearchBlock
import com.asakii.rpc.proto.*
import com.google.protobuf.ByteString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Protobuf 与 RpcModels 之间的转换工具
 *
 * 命名约定：
 * - toProto(): RpcModels -> Protobuf
 * - toRpc(): Protobuf -> RpcModels
 */
object ProtoConverter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ==================== Provider ====================

    fun RpcProviderApi?.toProto(): Provider = when (this) {
        RpcProviderApi.CLAUDE -> Provider.PROVIDER_CLAUDE
        RpcProviderApi.CODEX -> Provider.PROVIDER_CODEX
        null -> Provider.PROVIDER_UNSPECIFIED
    }

    fun Provider.toRpc(): RpcProviderApi? = when (this) {
        Provider.PROVIDER_CLAUDE -> RpcProviderApi.CLAUDE
        Provider.PROVIDER_CODEX -> RpcProviderApi.CODEX
        Provider.PROVIDER_UNSPECIFIED, Provider.UNRECOGNIZED -> null
    }

    // ==================== SessionStatus ====================

    fun RpcSessionStatusApi.toProto(): SessionStatus = when (this) {
        RpcSessionStatusApi.CONNECTED -> SessionStatus.SESSION_STATUS_CONNECTED
        RpcSessionStatusApi.DISCONNECTED -> SessionStatus.SESSION_STATUS_DISCONNECTED
        RpcSessionStatusApi.INTERRUPTED -> SessionStatus.SESSION_STATUS_INTERRUPTED
        RpcSessionStatusApi.MODEL_CHANGED -> SessionStatus.SESSION_STATUS_MODEL_CHANGED
    }

    fun SessionStatus.toRpc(): RpcSessionStatusApi = when (this) {
        SessionStatus.SESSION_STATUS_CONNECTED -> RpcSessionStatusApi.CONNECTED
        SessionStatus.SESSION_STATUS_DISCONNECTED -> RpcSessionStatusApi.DISCONNECTED
        SessionStatus.SESSION_STATUS_INTERRUPTED -> RpcSessionStatusApi.INTERRUPTED
        SessionStatus.SESSION_STATUS_MODEL_CHANGED -> RpcSessionStatusApi.MODEL_CHANGED
        SessionStatus.SESSION_STATUS_UNSPECIFIED, SessionStatus.UNRECOGNIZED -> RpcSessionStatusApi.DISCONNECTED
    }

    // ==================== ContentStatus ====================

    fun RpcContentStatusApi.toProto(): ContentStatus = when (this) {
        RpcContentStatusApi.IN_PROGRESS -> ContentStatus.CONTENT_STATUS_IN_PROGRESS
        RpcContentStatusApi.COMPLETED -> ContentStatus.CONTENT_STATUS_COMPLETED
        RpcContentStatusApi.FAILED -> ContentStatus.CONTENT_STATUS_FAILED
    }

    fun ContentStatus.toRpc(): RpcContentStatusApi = when (this) {
        ContentStatus.CONTENT_STATUS_IN_PROGRESS -> RpcContentStatusApi.IN_PROGRESS
        ContentStatus.CONTENT_STATUS_COMPLETED -> RpcContentStatusApi.COMPLETED
        ContentStatus.CONTENT_STATUS_FAILED -> RpcContentStatusApi.FAILED
        ContentStatus.CONTENT_STATUS_UNSPECIFIED, ContentStatus.UNRECOGNIZED -> RpcContentStatusApi.IN_PROGRESS
    }

    // ==================== PermissionMode ====================

    fun RpcPermissionModeApi.toProto(): PermissionMode = when (this) {
        RpcPermissionModeApi.DEFAULT -> PermissionMode.PERMISSION_MODE_DEFAULT
        RpcPermissionModeApi.BYPASS_PERMISSIONS -> PermissionMode.PERMISSION_MODE_BYPASS_PERMISSIONS
        RpcPermissionModeApi.ACCEPT_EDITS -> PermissionMode.PERMISSION_MODE_ACCEPT_EDITS
        RpcPermissionModeApi.PLAN -> PermissionMode.PERMISSION_MODE_PLAN
    }

    fun PermissionMode.toRpc(): RpcPermissionModeApi = when (this) {
        PermissionMode.PERMISSION_MODE_DEFAULT -> RpcPermissionModeApi.DEFAULT
        PermissionMode.PERMISSION_MODE_BYPASS_PERMISSIONS -> RpcPermissionModeApi.BYPASS_PERMISSIONS
        PermissionMode.PERMISSION_MODE_ACCEPT_EDITS -> RpcPermissionModeApi.ACCEPT_EDITS
        PermissionMode.PERMISSION_MODE_PLAN -> RpcPermissionModeApi.PLAN
        PermissionMode.PERMISSION_MODE_UNSPECIFIED, PermissionMode.UNRECOGNIZED -> RpcPermissionModeApi.DEFAULT
    }

    // ==================== SandboxMode ====================

    fun RpcSandboxModeApi.toProto(): SandboxMode = when (this) {
        RpcSandboxModeApi.READ_ONLY -> SandboxMode.SANDBOX_MODE_READ_ONLY
        RpcSandboxModeApi.WORKSPACE_WRITE -> SandboxMode.SANDBOX_MODE_WORKSPACE_WRITE
        RpcSandboxModeApi.DANGER_FULL_ACCESS -> SandboxMode.SANDBOX_MODE_DANGER_FULL_ACCESS
    }

    fun SandboxMode.toRpc(): RpcSandboxModeApi = when (this) {
        SandboxMode.SANDBOX_MODE_READ_ONLY -> RpcSandboxModeApi.READ_ONLY
        SandboxMode.SANDBOX_MODE_WORKSPACE_WRITE -> RpcSandboxModeApi.WORKSPACE_WRITE
        SandboxMode.SANDBOX_MODE_DANGER_FULL_ACCESS -> RpcSandboxModeApi.DANGER_FULL_ACCESS
        SandboxMode.SANDBOX_MODE_UNSPECIFIED, SandboxMode.UNRECOGNIZED -> RpcSandboxModeApi.READ_ONLY
    }

    // ==================== ConnectOptions ====================

    fun ConnectOptions.toRpc(): RpcConnectOptions = RpcConnectOptions(
        provider = if (hasProvider()) provider.toRpc() else null,
        model = if (hasModel()) model else null,
        systemPrompt = if (hasSystemPrompt()) systemPrompt else null,
        initialPrompt = if (hasInitialPrompt()) initialPrompt else null,
        sessionId = if (hasSessionId()) sessionId else null,
        resumeSessionId = if (hasResumeSessionId()) resumeSessionId else null,
        metadata = metadataMap,
        permissionMode = if (hasPermissionMode()) permissionMode.toRpc() else null,
        dangerouslySkipPermissions = if (hasDangerouslySkipPermissions()) dangerouslySkipPermissions else null,
        allowDangerouslySkipPermissions = if (hasAllowDangerouslySkipPermissions()) allowDangerouslySkipPermissions else null,
        includePartialMessages = if (hasIncludePartialMessages()) includePartialMessages else null,
        continueConversation = if (hasContinueConversation()) continueConversation else null,
        thinkingEnabled = if (hasThinkingEnabled()) thinkingEnabled else null,
        baseUrl = if (hasBaseUrl()) baseUrl else null,
        apiKey = if (hasApiKey()) apiKey else null,
        sandboxMode = if (hasSandboxMode()) sandboxMode.toRpc() else null,
        replayUserMessages = if (hasReplayUserMessages()) replayUserMessages else null
    )

    // ==================== ConnectResult ====================

    fun RpcConnectResultApi.toProto(): ConnectResult = connectResult {
        sessionId = this@toProto.sessionId
        provider = this@toProto.provider.toProto()
        status = this@toProto.status.toProto()
        this@toProto.model?.let { this.model = it }
        this@toProto.capabilities?.let { this.capabilities = it.toProto() }
        this@toProto.cwd?.let { this.cwd = it }
    }

    // ==================== Capabilities ====================

    fun RpcCapabilities.toProto(): Capabilities = capabilities {
        canInterrupt = this@toProto.canInterrupt
        canSwitchModel = this@toProto.canSwitchModel
        canSwitchPermissionMode = this@toProto.canSwitchPermissionMode
        supportedPermissionModes.addAll(this@toProto.supportedPermissionModes.map { it.toProto() })
        canSkipPermissions = this@toProto.canSkipPermissions
        canSendRichContent = this@toProto.canSendRichContent
        canThink = this@toProto.canThink
        canResumeSession = this@toProto.canResumeSession
        canRunInBackground = this@toProto.canRunInBackground
    }

    // ==================== StatusResult ====================

    fun RpcStatusResultApi.toProto(): StatusResult = statusResult {
        status = this@toProto.status.toProto()
    }

    // ==================== SetModelResult ====================

    fun RpcSetModelResultApi.toProto(): SetModelResult = setModelResult {
        status = SessionStatus.SESSION_STATUS_MODEL_CHANGED
        model = this@toProto.model
    }

    // ==================== SetPermissionModeResult ====================

    fun RpcSetPermissionModeResultApi.toProto(): SetPermissionModeResult = setPermissionModeResult {
        mode = this@toProto.mode.toProto()
        success = true
    }

    // ==================== SetMaxThinkingTokensResult ====================

    fun RpcSetMaxThinkingTokensResultApi.toProto(): com.asakii.rpc.proto.SetMaxThinkingTokensResult = com.asakii.rpc.proto.setMaxThinkingTokensResult {
        status = this@toProto.status.toProto()
        this@toProto.maxThinkingTokens?.let { maxThinkingTokens = it }
    }

    // ==================== History ====================

    fun RpcHistoryApi.toProto(): History = history {
        messages.addAll(this@toProto.messages.map { it.toProto() })
    }

    fun RpcHistorySessionsResultApi.toProto(): HistorySessionsResult = historySessionsResult {
        sessions.addAll(this@toProto.sessions.map { it.toProto() })
    }

    fun RpcHistorySessionApi.toProto(): HistorySession = historySession {
        sessionId = this@toProto.sessionId
        firstUserMessage = this@toProto.firstUserMessage
        timestamp = this@toProto.timestamp
        messageCount = this@toProto.messageCount
        projectPath = this@toProto.projectPath
        this@toProto.customTitle?.let { customTitle = it }
    }

    fun RpcHistoryMetadataApi.toProto(): com.asakii.rpc.proto.HistoryMetadata = com.asakii.rpc.proto.historyMetadata {
        totalLines = this@toProto.totalLines
        sessionId = this@toProto.sessionId
        projectPath = this@toProto.projectPath
        this@toProto.customTitle?.let { customTitle = it }
    }

    fun RpcHistoryResultApi.toProto(): com.asakii.rpc.proto.HistoryResult = com.asakii.rpc.proto.historyResult {
        messages.addAll(this@toProto.messages.map { it.toProto() })
        offset = this@toProto.offset
        count = this@toProto.count
        availableCount = this@toProto.availableCount
    }

    fun RpcTruncateHistoryResultApi.toProto(): com.asakii.rpc.proto.TruncateHistoryResult = com.asakii.rpc.proto.truncateHistoryResult {
        success = this@toProto.success
        remainingLines = this@toProto.remainingLines
        this@toProto.error?.let { error = it }
    }

    // ==================== RpcMessage -> Proto ====================

    fun RpcMessageApi.toProto(): RpcMessage = rpcMessage {
        provider = when (this@toProto) {
            is RpcUserMessageApi -> this@toProto.provider.toProto()
            is RpcAssistantMessage -> this@toProto.provider.toProto()
            is RpcResultMessageApi -> this@toProto.provider.toProto()
            is RpcStreamEventApi -> this@toProto.provider.toProto()
            is RpcErrorMessageApi -> this@toProto.provider.toProto()
            is RpcStatusSystemMessage -> this@toProto.provider.toProto()
            is RpcCompactBoundaryMessage -> this@toProto.provider.toProto()
            is RpcSystemInitMessage -> this@toProto.provider.toProto()
        }

        when (val msg = this@toProto) {
            is RpcUserMessageApi -> user = msg.toProtoUserMessage()
            is RpcAssistantMessage -> assistant = msg.toProtoAssistantMessage()
            is RpcResultMessageApi -> result = msg.toProtoResultMessage()
            is RpcStreamEventApi -> streamEvent = msg.toProtoStreamEvent()
            is RpcErrorMessageApi -> error = msg.toProtoErrorMessage()
            is RpcStatusSystemMessage -> statusSystem = msg.toProtoStatusSystemMessage()
            is RpcCompactBoundaryMessage -> compactBoundary = msg.toProtoCompactBoundaryMessage()
            is RpcSystemInitMessage -> systemInit = msg.toProtoSystemInitMessage()
        }
    }

    private fun RpcUserMessageApi.toProtoUserMessage(): UserMessage = userMessage {
        message = this@toProtoUserMessage.message.toProto()
        this@toProtoUserMessage.parentToolUseId?.let { parentToolUseId = it }
        this@toProtoUserMessage.isReplay?.let { isReplay = it }
        this@toProtoUserMessage.uuid?.let { uuid = it }
    }

    private fun RpcAssistantMessage.toProtoAssistantMessage(): AssistantMessage = assistantMessage {
        message = this@toProtoAssistantMessage.message.toProto()
        this@toProtoAssistantMessage.id?.let { id = it }
        this@toProtoAssistantMessage.parentToolUseId?.let { parentToolUseId = it }
        this@toProtoAssistantMessage.uuid?.let { uuid = it }
    }

    private fun RpcResultMessageApi.toProtoResultMessage(): ResultMessage = resultMessage {
        subtype = this@toProtoResultMessage.subtype
        this@toProtoResultMessage.durationMs?.let { durationMs = it }
        this@toProtoResultMessage.durationApiMs?.let { durationApiMs = it }
        isError = this@toProtoResultMessage.isError
        numTurns = this@toProtoResultMessage.numTurns
        this@toProtoResultMessage.sessionId?.let { sessionId = it }
        this@toProtoResultMessage.totalCostUsd?.let { totalCostUsd = it }
        this@toProtoResultMessage.usage?.let { usageJson = ByteString.copyFromUtf8(json.encodeToString(it)) }
        this@toProtoResultMessage.result?.let { result = it }
    }

    private fun RpcStreamEventApi.toProtoStreamEvent(): StreamEvent = streamEvent {
        uuid = this@toProtoStreamEvent.uuid
        sessionId = this@toProtoStreamEvent.sessionId
        event = this@toProtoStreamEvent.event.toProtoStreamEventData()
        this@toProtoStreamEvent.parentToolUseId?.let { parentToolUseId = it }
    }

    private fun RpcErrorMessageApi.toProtoErrorMessage(): ErrorMessage = errorMessage {
        errorMessage = this@toProtoErrorMessage.message
    }

    private fun RpcStatusSystemMessage.toProtoStatusSystemMessage(): StatusSystemMessage = statusSystemMessage {
        subtype = this@toProtoStatusSystemMessage.subtype
        this@toProtoStatusSystemMessage.status?.let { status = it }
        sessionId = this@toProtoStatusSystemMessage.sessionId
    }

    private fun RpcCompactBoundaryMessage.toProtoCompactBoundaryMessage(): CompactBoundaryMessage = compactBoundaryMessage {
        subtype = this@toProtoCompactBoundaryMessage.subtype
        sessionId = this@toProtoCompactBoundaryMessage.sessionId
        this@toProtoCompactBoundaryMessage.compactMetadata?.let { compactMetadata = it.toProtoCompactMetadata() }
    }

    private fun RpcCompactMetadataApi.toProtoCompactMetadata(): CompactMetadata = compactMetadata {
        this@toProtoCompactMetadata.trigger?.let { trigger = it }
        this@toProtoCompactMetadata.preTokens?.let { preTokens = it }
    }

    private fun RpcSystemInitMessage.toProtoSystemInitMessage(): SystemInitMessage = systemInitMessage {
        sessionId = this@toProtoSystemInitMessage.sessionId
        this@toProtoSystemInitMessage.cwd?.let { cwd = it }
        this@toProtoSystemInitMessage.model?.let { model = it }
        this@toProtoSystemInitMessage.permissionMode?.let { permissionMode = it }
        this@toProtoSystemInitMessage.apiKeySource?.let { apiKeySource = it }
        this@toProtoSystemInitMessage.tools?.let { tools.addAll(it) }
        this@toProtoSystemInitMessage.mcpServers?.let { servers ->
            mcpServers.addAll(servers.map { it.toProtoMcpServerInfo() })
        }
    }

    private fun RpcMcpServerInfoApi.toProtoMcpServerInfo(): McpServerInfo = mcpServerInfo {
        name = this@toProtoMcpServerInfo.name
        status = this@toProtoMcpServerInfo.status
    }

    // ==================== MessageContent ====================

    private fun RpcMessageContentApi.toProto(): MessageContent = messageContent {
        content.addAll(this@toProto.content.map { it.toProtoContentBlock() })
        this@toProto.model?.let { model = it }
    }

    // ==================== ContentBlock ====================

    fun RpcContentBlockApi.toProtoContentBlock(): ContentBlock = contentBlock {
        when (val block = this@toProtoContentBlock) {
            is RpcTextBlock -> text = textBlock { text = block.text }
            is RpcThinkingBlock -> thinking = thinkingBlock {
                thinking = block.thinking
                block.signature?.let { signature = it }
            }
            is RpcToolUseBlock -> toolUse = toolUseBlock {
                id = block.id
                toolName = block.toolName
                toolType = block.toolType
                block.input?.let { inputJson = ByteString.copyFromUtf8(json.encodeToString(it)) }
                status = block.status.toProto()
            }
            is RpcToolResultBlock -> toolResult = toolResultBlock {
                toolUseId = block.toolUseId
                block.content?.let { contentJson = ByteString.copyFromUtf8(json.encodeToString(it)) }
                isError = block.isError
                block.agentId?.let { agentId = it }
            }
            is RpcImageBlock -> image = imageBlock {
                source = imageSource {
                    type = block.source.type
                    mediaType = block.source.mediaType
                    block.source.data?.let { data = it }
                    block.source.url?.let { url = it }
                }
            }
            is RpcCommandExecutionBlock -> commandExecution = commandExecutionBlock {
                command = block.command
                block.output?.let { output = it }
                block.exitCode?.let { exitCode = it }
                status = block.status.toProto()
            }
            is RpcFileChangeBlock -> fileChange = fileChangeBlock {
                status = block.status.toProto()
                changes.addAll(block.changes.map { fileChange { path = it.path; kind = it.kind } })
            }
            is RpcMcpToolCallBlock -> mcpToolCall = mcpToolCallBlock {
                block.server?.let { server = it }
                block.tool?.let { tool = it }
                block.arguments?.let { argumentsJson = ByteString.copyFromUtf8(json.encodeToString(it)) }
                block.result?.let { resultJson = ByteString.copyFromUtf8(json.encodeToString(it)) }
                status = block.status.toProto()
            }
            is RpcWebSearchBlock -> webSearch = webSearchBlock { query = block.query }
            is RpcTodoListBlock -> todoList = todoListBlock {
                items.addAll(block.items.map { todoItem { text = it.text; completed = it.completed } })
            }
            is RpcErrorBlock -> error = errorBlock { message = block.message }
            is RpcUnknownBlock -> unknown = unknownBlock { type = block.type; data = block.data }
        }
    }

    fun ContentBlock.toRpc(): RpcContentBlockApi = when {
        hasText() -> RpcTextBlock(text = text.text)
        hasThinking() -> RpcThinkingBlock(
            thinking = thinking.thinking,
            signature = if (thinking.hasSignature()) thinking.signature else null
        )
        hasToolUse() -> RpcToolUseBlock(
            id = toolUse.id,
            toolName = toolUse.toolName,
            toolType = toolUse.toolType,
            input = if (toolUse.hasInputJson()) parseJsonElement(toolUse.inputJson) else null,
            status = toolUse.status.toRpc()
        )
        hasToolResult() -> RpcToolResultBlock(
            toolUseId = toolResult.toolUseId,
            content = if (toolResult.hasContentJson()) parseJsonElement(toolResult.contentJson) else null,
            isError = toolResult.isError,
            agentId = if (toolResult.hasAgentId()) toolResult.agentId else null
        )
        hasImage() -> RpcImageBlock(
            source = RpcImageSourceApi(
                type = image.source.type,
                mediaType = image.source.mediaType,
                data = if (image.source.hasData()) image.source.data else null,
                url = if (image.source.hasUrl()) image.source.url else null
            )
        )
        hasCommandExecution() -> RpcCommandExecutionBlock(
            command = commandExecution.command,
            output = if (commandExecution.hasOutput()) commandExecution.output else null,
            exitCode = if (commandExecution.hasExitCode()) commandExecution.exitCode else null,
            status = commandExecution.status.toRpc()
        )
        hasFileChange() -> RpcFileChangeBlock(
            status = fileChange.status.toRpc(),
            changes = fileChange.changesList.map { RpcFileChangeApi(path = it.path, kind = it.kind) }
        )
        hasMcpToolCall() -> RpcMcpToolCallBlock(
            server = if (mcpToolCall.hasServer()) mcpToolCall.server else null,
            tool = if (mcpToolCall.hasTool()) mcpToolCall.tool else null,
            arguments = if (mcpToolCall.hasArgumentsJson()) parseJsonElement(mcpToolCall.argumentsJson) else null,
            result = if (mcpToolCall.hasResultJson()) parseJsonElement(mcpToolCall.resultJson) else null,
            status = mcpToolCall.status.toRpc()
        )
        hasWebSearch() -> RpcWebSearchBlock(query = webSearch.query)
        hasTodoList() -> RpcTodoListBlock(
            items = todoList.itemsList.map { RpcTodoItemApi(text = it.text, completed = it.completed) }
        )
        hasError() -> RpcErrorBlock(message = error.message)
        hasUnknown() -> RpcUnknownBlock(type = unknown.type, data = unknown.data)
        else -> RpcUnknownBlock(type = "unknown", data = "")
    }

    // ==================== StreamEventData ====================

    private fun RpcStreamEventDataApi.toProtoStreamEventData(): StreamEventData = streamEventData {
        when (val evt = this@toProtoStreamEventData) {
            is RpcMessageStartEvent -> messageStart = messageStartEvent {
                evt.message?.let { messageInfo = it.toProtoMessageStartInfo() }
            }
            is RpcContentBlockStartEvent -> contentBlockStart = contentBlockStartEvent {
                index = evt.index
                contentBlock = evt.contentBlock.toProtoContentBlock()
            }
            is RpcContentBlockDeltaEvent -> contentBlockDelta = contentBlockDeltaEvent {
                index = evt.index
                delta = evt.delta.toProtoDelta()
            }
            is RpcContentBlockStopEvent -> contentBlockStop = contentBlockStopEvent {
                index = evt.index
            }
            is RpcMessageDeltaEvent -> messageDelta = messageDeltaEvent {
                evt.delta?.let { deltaJson = ByteString.copyFromUtf8(json.encodeToString(it)) }
                evt.usage?.let { usage = it.toProtoUsage() }
            }
            is RpcMessageStopEvent -> messageStop = messageStopEvent {}
        }
    }

    private fun RpcMessageStartInfoApi.toProtoMessageStartInfo(): MessageStartInfo = messageStartInfo {
        this@toProtoMessageStartInfo.id?.let { id = it }
        this@toProtoMessageStartInfo.model?.let { model = it }
        this@toProtoMessageStartInfo.content?.let { c -> content.addAll(c.map { it.toProtoContentBlock() }) }
    }

    // ==================== Delta ====================

    private fun RpcDeltaApi.toProtoDelta(): Delta = delta {
        when (val d = this@toProtoDelta) {
            is RpcTextDelta -> textDelta = textDelta { text = d.text }
            is RpcThinkingDelta -> thinkingDelta = thinkingDelta { thinking = d.thinking }
            is RpcInputJsonDelta -> inputJsonDelta = inputJsonDelta { partialJson = d.partialJson }
        }
    }

    // ==================== Usage ====================

    private fun RpcUsageApi.toProtoUsage(): Usage = usage {
        this@toProtoUsage.inputTokens?.let { inputTokens = it }
        this@toProtoUsage.outputTokens?.let { outputTokens = it }
        this@toProtoUsage.cachedInputTokens?.let { cachedInputTokens = it }
        this@toProtoUsage.cacheCreationTokens?.let { cacheCreationTokens = it }
        this@toProtoUsage.cacheReadTokens?.let { cacheReadTokens = it }
        this@toProtoUsage.provider?.let { provider = it.toProto() }
        this@toProtoUsage.raw?.let { rawJson = ByteString.copyFromUtf8(json.encodeToString(it)) }
    }

    // ==================== Helper ====================

    private fun parseJsonElement(bytes: ByteString): JsonElement? {
        return try {
            json.parseToJsonElement(bytes.toStringUtf8())
        } catch (e: Exception) {
            null
        }
    }
}
