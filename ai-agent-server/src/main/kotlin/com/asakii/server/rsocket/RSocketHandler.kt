package com.asakii.server.rsocket

import com.asakii.rpc.api.AiAgentRpcService
import com.asakii.rpc.api.IdeTools
import com.asakii.rpc.api.RpcMessage as RpcMessageApi
import com.asakii.rpc.proto.*
import com.asakii.server.mcp.McpHttpGateway
import com.asakii.server.mcp.McpProviders
import com.asakii.server.rpc.AiAgentRpcServiceImpl
import com.asakii.server.rpc.ClientCaller
import com.asakii.server.rpc.ClientCallerRegistry
import com.asakii.server.rsocket.ProtoConverter.toProto
import com.asakii.server.rsocket.ProtoConverter.toRpc
import com.google.protobuf.ByteString
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.write
import io.rsocket.kotlin.RSocket
import io.rsocket.kotlin.RSocketRequestHandler
import io.rsocket.kotlin.payload.Payload
import io.rsocket.kotlin.payload.buildPayload
import io.rsocket.kotlin.payload.data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import com.asakii.logging.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * RSocket 路由处理器
 *
 * 使用 RSocket 的 Request-Response 和 Request-Stream 模式处理 RPC 调用。
 * 支持通过 requester 实现服务端调用客户端（反向调用）。
 *
 * 重要：每个 RSocket 连接必须创建一个独立的 RSocketHandler 实例！
 * 所有状态（clientRequester、rpcService、MCP Server）都是连接级别的，
 * 绝对不能在多个连接之间共享。
 *
 * 路由表（客户端 -> 服务端）：
 * - agent.connect: Request-Response
 * - agent.query: Request-Stream
 * - agent.queryWithContent: Request-Stream
 * - agent.interrupt: Request-Response
 * - agent.disconnect: Request-Response
 * - agent.setModel: Request-Response
 * - agent.setPermissionMode: Request-Response
 * - agent.getHistory: Request-Response
 *
 * 反向调用路由（服务端 -> 客户端）：
 * - client.call: Request-Response (通用调用)
 */
class RSocketHandler(
    private val ideTools: IdeTools,
    private val clientRequester: RSocket,  // 必须在构造时传入，确保每个连接独立
    private val connectId: String,  // 由 HttpApiServer 分配的永久连接标识，用于 MCP 路由
    private val mcpHttpGateway: McpHttpGateway? = null,  // 项目级 MCP HTTP 网关（仅 Codex 模式使用）
    private val mcpProviders: McpProviders = McpProviders.DEFAULT,  // All MCP Server Providers
    private val serviceConfigProvider: () -> com.asakii.server.config.AiAgentServiceConfig = { com.asakii.server.config.AiAgentServiceConfig() }  // 服务配置提供者（每次 connect 时获取最新配置）
) {
    // 使用 SLF4J logger（IDEA 环境自动输出到 idea.log）
    private val wsLog = getLogger("RSocketHandler")

    /**
     * 创建 RSocket 请求处理器
     *
     * 每个连接创建独立的 handler，包含：
     * - 独立的 RPC 服务实例
     * - 独立的 MCP Server
     * - 独立的 ClientCaller（用于反向调用）
     *
     * 连接关闭时自动清理所有资源。
     */
    fun createHandler(): RSocket {
        wsLog.info { "🔌 [RSocket] [$connectId] 创建请求处理器" }

        // 反向调用支持
        val callIdCounter = AtomicInteger(0)

        // 创建 ClientCaller（初始时 requester 可能为空）
        val clientCaller = createClientCaller(callIdCounter)

        // 为每个连接创建独立的 RPC 服务（传递 connectId、MCP Providers 和服务配置提供者）
        val rpcService: AiAgentRpcService = AiAgentRpcServiceImpl(
            ideTools = ideTools,
            clientCaller = clientCaller,
            mcpProviders = mcpProviders,
            mcpHttpGateway = mcpHttpGateway,
            serviceConfigProvider = serviceConfigProvider,
            connectId = connectId
        )

        val handler = RSocketRequestHandler {
            // ==================== Request-Response ====================

            requestResponse { request ->
                val route = extractRoute(request)
                val dataBytes = request.data.readByteArray()
                wsLog.info { "📨 [RSocket] ← Request-Response: $route" }
                wsLog.debug { "📨 [RSocket] ← Request data (${dataBytes.size} bytes)" }

                val response = when (route) {
                    "agent.connect" -> handleConnect(dataBytes, rpcService)
                    "agent.interrupt" -> handleInterrupt(rpcService)
                    "agent.runInBackground" -> handleRunInBackground(rpcService)
                    "agent.bashRunToBackground" -> handleBashRunToBackground(dataBytes, rpcService)
                    "agent.runToBackground" -> handleRunToBackground(dataBytes, rpcService)
                    "agent.setMaxThinkingTokens" -> handleSetMaxThinkingTokens(dataBytes, rpcService)
                    "agent.disconnect" -> handleDisconnect(rpcService)
                    "agent.setModel" -> handleSetModel(dataBytes, rpcService)
                    "agent.setPermissionMode" -> handleSetPermissionMode(dataBytes, rpcService)
                    "agent.setSandboxMode" -> handleSetSandboxMode(dataBytes, rpcService)
                    "agent.getHistory" -> handleGetHistory(rpcService)
                    "agent.truncateHistory" -> handleTruncateHistory(dataBytes, rpcService)
                    "agent.hasIdeEnvironment" -> handleHasIdeEnvironment()
                    "agent.getMcpStatus" -> handleGetMcpStatus(rpcService)
                    "agent.reconnectMcp" -> handleReconnectMcp(dataBytes, rpcService)
                    "agent.getMcpTools" -> handleGetMcpTools(dataBytes, rpcService)
                    "agent.disposeSession" -> handleDisposeSession(rpcService)
                    else -> throw IllegalArgumentException("Unknown route: $route")
                }

                val responseBytes = response.data.readByteArray()
                wsLog.info { "📨 [RSocket] → Response: $route (${responseBytes.size} bytes)" }
                buildPayload { data(responseBytes) }
            }

            // ==================== Request-Stream ====================

            requestStream { request ->
                val route = extractRoute(request)
                val dataBytes = request.data.readByteArray()
                wsLog.info { "📡 [RSocket] ← Request-Stream: $route" }
                wsLog.debug { "📡 [RSocket] ← Request data (${dataBytes.size} bytes)" }

                when (route) {
                    "agent.query" -> handleQuery(dataBytes, rpcService)
                    "agent.queryWithContent" -> handleQueryWithContent(dataBytes, rpcService)
                    "agent.events" -> handleGlobalEvents(rpcService)
                    else -> throw IllegalArgumentException("Unknown route: $route")
                }
            }
        }

        // 注册 ClientCaller 到全局注册表，绑定到 handler 的生命周期
        // 当 handler 关闭时，ClientCallerRegistry 会自动移除此条目
        ClientCallerRegistry.register(connectId, clientCaller, handler)

        // 监听连接关闭，自动清理 SDK 资源（非阻塞）
        handler.coroutineContext[Job]?.invokeOnCompletion { cause ->
            wsLog.info("🔌 [RSocket] [$connectId] 连接关闭，自动清理资源 (cause: ${cause?.message ?: "正常关闭"})")
            // 使用独立的协程作用域进行异步清理，避免阻塞回调
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    withTimeout(10000) { // 10秒超时
                        rpcService.disconnect()
                    }
                    wsLog.info("✅ [RSocket] [$connectId] SDK 资源已清理")
                } catch (e: Exception) {
                    wsLog.warn { "⚠️ [RSocket] [$connectId] 清理 SDK 资源时出错: ${e.message}" }
                }
                // 注意：ClientCaller 的清理由 ClientCallerRegistry 的 invokeOnCompletion 自动处理
            }
        }

        wsLog.info("✅ [RSocket] [$connectId] Handler 创建完成，clientRequester 已绑定")
        return handler
    }

    // ==================== Request-Response Handlers ====================

    private suspend fun handleConnect(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val options = if (dataBytes.isNotEmpty()) {
            val protoOptions = ConnectOptions.parseFrom(dataBytes)
            wsLog.debug { "📥 [RSocket] connect options: provider=${protoOptions.provider}, model=${protoOptions.model}" }
            protoOptions.toRpc()
        } else {
            wsLog.debug { "📥 [RSocket] connect options: (default)" }
            null
        }

        val result = rpcService.connect(options)
        wsLog.info { "📤 [RSocket] connect result: sessionId=${result.sessionId}, provider=${result.provider}" }
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleInterrupt(rpcService: AiAgentRpcService): Payload {
        wsLog.info { "📥 [RSocket] interrupt request" }
        val result = rpcService.interrupt()
        wsLog.info { "📤 [RSocket] interrupt result: status=${result.status}" }
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleRunInBackground(rpcService: AiAgentRpcService): Payload {
        wsLog.info { "📥 [RSocket] runInBackground request" }
        val result = rpcService.runInBackground()
        wsLog.info { "📤 [RSocket] runInBackground result: status=${result.status}" }
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleBashRunToBackground(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = BashRunToBackgroundRequest.parseFrom(dataBytes)
        wsLog.info { "📥 [RSocket] bashRunToBackground request: taskId=${req.taskId}" }
        val result = rpcService.bashRunToBackground(req.taskId)
        wsLog.info { "📤 [RSocket] bashRunToBackground result: success=${result.success}, taskId=${result.taskId}" }
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleRunToBackground(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = RunToBackgroundRequest.parseFrom(dataBytes)
        val taskId = if (req.hasTaskId()) req.taskId else null
        wsLog.info { "📥 [RSocket] runToBackground request: taskId=${taskId ?: "all"}" }
        val result = rpcService.runToBackground(taskId)
        if (taskId != null) {
            val typeInfo = if (result.isBash == true) "Bash" else "Agent"
            wsLog.info { "📤 [RSocket] runToBackground result: $typeInfo success=${result.success}, taskId=${result.taskId}" }
        } else {
            wsLog.info { "📤 [RSocket] runToBackground batch result: success=${result.success}, bash=${result.bashCount}, agent=${result.agentCount}" }
        }
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleSetMaxThinkingTokens(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = com.asakii.rpc.proto.SetMaxThinkingTokensRequest.parseFrom(dataBytes)
        val maxThinkingTokens = if (req.hasMaxThinkingTokens()) req.maxThinkingTokens else null
        wsLog.info { "📥 [RSocket] setMaxThinkingTokens request: maxThinkingTokens=$maxThinkingTokens" }
        val result = rpcService.setMaxThinkingTokens(maxThinkingTokens)
        wsLog.info("📤 [RSocket] setMaxThinkingTokens result: maxThinkingTokens=${result.maxThinkingTokens}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleDisconnect(rpcService: AiAgentRpcService): Payload {
        wsLog.info("📥 [RSocket] disconnect request")
        val result = rpcService.disconnect()
        wsLog.info("📤 [RSocket] disconnect result: status=${result.status}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleDisposeSession(rpcService: AiAgentRpcService): Payload {
        wsLog.info("🗑️ [RSocket] disposeSession request")
        val result = rpcService.disposeSession()
        wsLog.info("📤 [RSocket] disposeSession result: status=${result.status}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleSetModel(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = SetModelRequest.parseFrom(dataBytes)
        wsLog.info("📥 [RSocket] setModel request: model=${req.model}")
        val result = rpcService.setModel(req.model)
        wsLog.info("📤 [RSocket] setModel result: model=${result.model}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleSetPermissionMode(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = SetPermissionModeRequest.parseFrom(dataBytes)
        wsLog.info("📥 [RSocket] setPermissionMode request: mode=${req.mode}")
        val result = rpcService.setPermissionMode(req.mode.toRpc())
        wsLog.info("📤 [RSocket] setPermissionMode result: mode=${result.mode}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleSetSandboxMode(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = SetSandboxModeRequest.parseFrom(dataBytes)
        wsLog.info("📥 [RSocket] setSandboxMode request: mode=${req.mode}")
        val result = rpcService.setSandboxMode(req.mode.toRpc())
        wsLog.info("📤 [RSocket] setSandboxMode result: mode=${result.mode}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleGetHistory(rpcService: AiAgentRpcService): Payload {
        wsLog.info("?? [RSocket] getHistory request")
        val result = rpcService.getHistory()
        wsLog.info("?? [RSocket] getHistory result: messages=${result.messages.size}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleTruncateHistory(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = TruncateHistoryRequest.parseFrom(dataBytes)
        wsLog.info("✂️ [RSocket] truncateHistory request: sessionId=${req.sessionId}, messageUuid=${req.messageUuid}")
        val result = rpcService.truncateHistory(req.sessionId, req.messageUuid, req.projectPath)
        wsLog.info("✂️ [RSocket] truncateHistory result: success=${result.success}, remainingLines=${result.remainingLines}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private fun handleHasIdeEnvironment(): Payload {
        val hasIde = ideTools.hasIdeEnvironment()
        wsLog.info("🖥️ [RSocket] hasIdeEnvironment: $hasIde")
        val response = HasIdeEnvironmentResponse.newBuilder()
            .setHasIde(hasIde)
            .build()
        return buildPayload { data(response.toByteArray()) }
    }

    private suspend fun handleGetMcpStatus(rpcService: AiAgentRpcService): Payload {
        wsLog.info("🔌 [RSocket] getMcpStatus request")
        val result = rpcService.getMcpStatus()
        wsLog.info("📤 [RSocket] getMcpStatus result: ${result.servers.size} servers")
        val response = McpStatusResult.newBuilder().apply {
            result.servers.forEach { server ->
                addServers(McpServerStatus.newBuilder()
                    .setName(server.name)
                    .setStatus(server.status)
                    .apply {
                        server.serverInfo?.let { setServerInfo(it.toString()) }
                    }
                    .build())
            }
        }.build()
        return buildPayload { data(response.toByteArray()) }
    }

    private suspend fun handleReconnectMcp(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = ReconnectMcpRequest.parseFrom(dataBytes)
        wsLog.info("🔌 [RSocket] reconnectMcp request: serverName=${req.serverName}")
        val result = rpcService.reconnectMcp(req.serverName)
        wsLog.info("📤 [RSocket] reconnectMcp result: success=${result.success}, status=${result.status}, toolsCount=${result.toolsCount}")
        val response = ReconnectMcpResult.newBuilder().apply {
            success = result.success
            serverName = result.serverName
            result.status?.let { status = it }
            toolsCount = result.toolsCount
            result.error?.let { error = it }
        }.build()
        return buildPayload { data(response.toByteArray()) }
    }

    private suspend fun handleGetMcpTools(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val req = GetMcpToolsRequest.parseFrom(dataBytes)
        val serverName = if (req.hasServerName()) req.serverName else null
        wsLog.info("🔧 [RSocket] getMcpTools request: serverName=$serverName")
        val result = rpcService.getMcpTools(serverName)
        wsLog.info("📤 [RSocket] getMcpTools result: ${result.count} tools")
        val response = GetMcpToolsResult.newBuilder().apply {
            result.serverName?.let { this.serverName = it }
            result.tools.forEach { tool ->
                addTools(McpToolInfo.newBuilder().apply {
                    name = tool.name
                    description = tool.description
                    tool.inputSchema?.let { inputSchema = it.toString() }
                }.build())
            }
            count = result.count
        }.build()
        return buildPayload { data(response.toByteArray()) }
    }

    // ==================== Request-Stream Handlers ====================

    private var streamMessageCounter = 0

    private fun handleQuery(dataBytes: ByteArray, rpcService: AiAgentRpcService): Flow<Payload> {
        val req = QueryRequest.parseFrom(dataBytes)
        wsLog.info("📥 [RSocket] query request: message=${req.message.take(100)}...")
        streamMessageCounter = 0

        return rpcService.query(req.message)
            .mapToPayloadWithLogging("query")
            .catch { e ->
                wsLog.error("❌ [RSocket] query 错误: ${e.message}")
                throw e
            }
    }

    private fun handleQueryWithContent(dataBytes: ByteArray, rpcService: AiAgentRpcService): Flow<Payload> {
        val req = QueryWithContentRequest.parseFrom(dataBytes)
        val contentBlocks = req.contentList.map { it.toRpc() }
        wsLog.info("📥 [RSocket] queryWithContent request: blocks=${contentBlocks.size}")
        streamMessageCounter = 0

        return rpcService.queryWithContent(contentBlocks)
            .mapToPayloadWithLogging("queryWithContent")
            .catch { e ->
                wsLog.error("❌ [RSocket] queryWithContent 错误: ${e.message}")
                throw e
            }
    }

    /**
     * 处理全局事件流订阅
     * 
     * 返回一个持续的事件流，不会自动结束。
     * 所有 SDK 事件都会通过此流推送给订阅者。
     */
    private fun handleGlobalEvents(rpcService: AiAgentRpcService): Flow<Payload> {
        wsLog.info("📡 [RSocket] 全局事件流订阅 [$connectId]")
        
        return rpcService.subscribeGlobalEvents()
            .mapToPayloadWithLogging("events")
            .catch { e ->
                wsLog.error("❌ [RSocket] 全局事件流错误: ${e.message}")
                throw e
            }
    }


    // ==================== Helper Methods ====================

    /**
     * 从 Payload metadata 中提取路由信息
     */
    private fun extractRoute(payload: Payload): String {
        val metadata = payload.metadata ?: throw IllegalArgumentException("Missing metadata")
        val metadataBytes = metadata.readByteArray()
        if (metadataBytes.isEmpty()) {
            throw IllegalArgumentException("Empty metadata")
        }

        // RSocket routing metadata: [length:1byte][route:N bytes]
        val length = metadataBytes[0].toInt() and 0xFF
        return String(metadataBytes, 1, length, Charsets.UTF_8)
    }

    /**
     * 将 RpcMessage Flow 转换为 Payload Flow（不带日志）
     */
    private fun Flow<RpcMessageApi>.mapToPayload(): Flow<Payload> = map { message ->
        val protoMessage = message.toProto()
        buildPayload { data(protoMessage.toByteArray()) }
    }

    /**
     * 将 RpcMessage Flow 转换为 Payload Flow（带日志）
     */
    private fun Flow<RpcMessageApi>.mapToPayloadWithLogging(route: String): Flow<Payload> = map { message ->
        streamMessageCounter++
        val msgType = message::class.simpleName ?: "Unknown"
        val counter = streamMessageCounter  // 捕获当前计数器值

        // 记录完整消息内容
        wsLog.info { "📤 [RSocket] #$counter ($route) $msgType: ${formatRpcMessage(message)}" }

        val protoMessage = message.toProto()
        buildPayload { data(protoMessage.toByteArray()) }
    }

    /**
     * 格式化 RpcMessage 为日志字符串（完整内容，不截断）
     */
    private fun formatRpcMessage(message: RpcMessageApi): String = when (message) {
        is com.asakii.rpc.api.RpcStreamEvent -> {
            "event=${formatStreamEventData(message.event)}"
        }
        is com.asakii.rpc.api.RpcAssistantMessage -> {
            "content=${formatRpcContentBlocks(message.message.content)}, parentToolUseId=${message.parentToolUseId}"
        }
        is com.asakii.rpc.api.RpcUserMessage -> {
            "content=${formatRpcContentBlocks(message.message.content)}, parentToolUseId=${message.parentToolUseId}"
        }
        is com.asakii.rpc.api.RpcResultMessage -> {
            "subtype=${message.subtype}, isError=${message.isError}, numTurns=${message.numTurns}, result=${message.result}"
        }
        is com.asakii.rpc.api.RpcErrorMessage -> {
            "error=${message.message}"
        }
        else -> message.toString()
    }

    /**
     * 格式化流式事件数据
     */
    private fun formatStreamEventData(event: com.asakii.rpc.api.RpcStreamEventData): String = when (event) {
        is com.asakii.rpc.api.RpcContentBlockDeltaEvent -> {
            "delta=${formatDelta(event.delta)}, index=${event.index}"
        }
        is com.asakii.rpc.api.RpcContentBlockStartEvent -> {
            "block=${formatRpcContentBlock(event.contentBlock)}, index=${event.index}"
        }
        is com.asakii.rpc.api.RpcContentBlockStopEvent -> {
            "index=${event.index}"
        }
        is com.asakii.rpc.api.RpcMessageStartEvent -> {
            "message=${event.message}"
        }
        is com.asakii.rpc.api.RpcMessageDeltaEvent -> {
            "delta=${event.delta}, usage=${event.usage}"
        }
        is com.asakii.rpc.api.RpcMessageStopEvent -> {
            "(stop)"
        }
    }

    /**
     * 格式化 Delta
     */
    private fun formatDelta(delta: com.asakii.rpc.api.RpcDelta): String = when (delta) {
        is com.asakii.rpc.api.RpcTextDelta -> "text=\"${delta.text}\""
        is com.asakii.rpc.api.RpcThinkingDelta -> "thinking=\"${delta.thinking}\""
        is com.asakii.rpc.api.RpcInputJsonDelta -> "json=\"${delta.partialJson}\""
    }

    /**
     * 格式化内容块列表
     */
    private fun formatRpcContentBlocks(blocks: List<com.asakii.rpc.api.RpcContentBlock>?): String {
        return blocks?.joinToString("; ") { formatRpcContentBlock(it) } ?: "(empty)"
    }

    /**
     * 格式化单个内容块
     */
    private fun formatRpcContentBlock(block: com.asakii.rpc.api.RpcContentBlock): String = when (block) {
        is com.asakii.rpc.api.RpcTextBlock -> "Text(\"${block.text}\")"
        is com.asakii.rpc.api.RpcThinkingBlock -> "Thinking(\"${block.thinking}\")"
        is com.asakii.rpc.api.RpcImageBlock -> "Image(mediaType=${block.source.mediaType}, dataLen=${block.source.data?.length})"
        is com.asakii.rpc.api.RpcToolUseBlock -> "ToolUse(id=${block.id}, name=${block.toolName}, input=${block.input})"
        is com.asakii.rpc.api.RpcToolResultBlock -> "ToolResult(toolUseId=${block.toolUseId}, content=${block.content}, isError=${block.isError})"
        is com.asakii.rpc.api.RpcTodoListBlock -> "TodoList(items=${block.items})"
        is com.asakii.rpc.api.RpcErrorBlock -> "Error(${block.message})"
        is com.asakii.rpc.api.RpcUnknownBlock -> "Unknown(type=${block.type})"
    }

    /**
     * 创建路由元数据
     */
    private fun createRouteMetadata(route: String): ByteArray {
        val routeBytes = route.toByteArray(Charsets.UTF_8)
        val metadata = ByteArray(1 + routeBytes.size)
        metadata[0] = routeBytes.size.toByte()
        System.arraycopy(routeBytes, 0, metadata, 1, routeBytes.size)
        return metadata
    }

    /**
     * 创建 ClientCaller（用于服务器向客户端发起请求）
     *
     * 使用 Protobuf 序列化，通过 client.call 路由发送类型化请求。
     *
     * 注意：clientRequester 在构造时已经传入，保证每个连接使用独立的 requester。
     */
    private fun createClientCaller(
        callIdCounter: AtomicInteger
    ): ClientCaller {
        // 直接使用构造时传入的 clientRequester，不再需要检查 null
        val requester = clientRequester

        return object : ClientCaller {
            override suspend fun callAskUserQuestion(request: AskUserQuestionRequest): AskUserQuestionResponse {
                val callId = "srv-${callIdCounter.incrementAndGet()}"
                wsLog.info("📤 [RSocket] [$connectId] → AskUserQuestion(Proto): callId=$callId, questions=${request.questionsCount}")

                try {
                    // 构建 ServerCallRequest
                    val serverRequest = ServerCallRequest.newBuilder()
                        .setCallId(callId)
                        .setMethod("AskUserQuestion")
                        .setAskUserQuestion(request)
                        .build()

                    val routeMetadata = createRouteMetadata("client.call")
                    val metadataBuffer = Buffer().apply { write(routeMetadata) }
                    val dataBuffer = Buffer().apply { write(serverRequest.toByteArray()) }
                    val payload = buildPayload {
                        data(dataBuffer)
                        metadata(metadataBuffer)
                    }

                    val responsePayload = requester.requestResponse(payload) // 永久等待用户响应

                    // 解析 ServerCallResponse
                    val responseBytes = responsePayload.data.readByteArray()
                    val serverResponse = ServerCallResponse.parseFrom(responseBytes)

                    if (!serverResponse.success) {
                        val errorMsg = serverResponse.error.ifEmpty { "Unknown error" }
                        wsLog.warn("📥 [RSocket] ← AskUserQuestion 失败: callId=$callId, error=$errorMsg")
                        throw RuntimeException("AskUserQuestion failed: $errorMsg")
                    }

                    if (!serverResponse.hasAskUserQuestion()) {
                        throw RuntimeException("AskUserQuestion response missing askUserQuestion field")
                    }

                    wsLog.info("📥 [RSocket] [$connectId] ← AskUserQuestion 成功: callId=$callId, answers=${serverResponse.askUserQuestion.answersCount}")
                    return serverResponse.askUserQuestion

                } catch (e: Exception) {
                    wsLog.warn("📥 [RSocket] [$connectId] ← AskUserQuestion 失败: callId=$callId, error=${e.message}")
                    throw RuntimeException("AskUserQuestion failed: ${e.message}")
                }
            }

            override suspend fun callRequestPermission(request: RequestPermissionRequest): RequestPermissionResponse {
                val callId = "srv-${callIdCounter.incrementAndGet()}"
                wsLog.info("📤 [RSocket] [$connectId] → RequestPermission(Proto): callId=$callId, toolName=${request.toolName}")

                try {
                    // 构建 ServerCallRequest
                    val serverRequest = ServerCallRequest.newBuilder()
                        .setCallId(callId)
                        .setMethod("RequestPermission")
                        .setRequestPermission(request)
                        .build()

                    val routeMetadata = createRouteMetadata("client.call")
                    val metadataBuffer = Buffer().apply { write(routeMetadata) }
                    val dataBuffer = Buffer().apply { write(serverRequest.toByteArray()) }
                    val payload = buildPayload {
                        data(dataBuffer)
                        metadata(metadataBuffer)
                    }

                    val responsePayload = requester.requestResponse(payload) // 永久等待用户响应

                    // 解析 ServerCallResponse
                    val responseBytes = responsePayload.data.readByteArray()
                    val serverResponse = ServerCallResponse.parseFrom(responseBytes)

                    if (!serverResponse.success) {
                        val errorMsg = serverResponse.error.ifEmpty { "Unknown error" }
                        wsLog.warn("📥 [RSocket] ← RequestPermission 失败: callId=$callId, error=$errorMsg")
                        throw RuntimeException("RequestPermission failed: $errorMsg")
                    }

                    if (!serverResponse.hasRequestPermission()) {
                        throw RuntimeException("RequestPermission response missing requestPermission field")
                    }

                    wsLog.info("📥 [RSocket] [$connectId] ← RequestPermission 成功: callId=$callId, approved=${serverResponse.requestPermission.approved}")
                    return serverResponse.requestPermission

                } catch (e: Exception) {
                    wsLog.warn("📥 [RSocket] [$connectId] ← RequestPermission 失败: callId=$callId, error=${e.message}")
                    throw RuntimeException("RequestPermission failed: ${e.message}")
                }
            }
        }
    }
}
