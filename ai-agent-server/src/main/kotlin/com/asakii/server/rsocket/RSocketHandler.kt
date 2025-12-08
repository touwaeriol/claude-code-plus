package com.asakii.server.rsocket

import com.asakii.rpc.api.AiAgentRpcService
import com.asakii.rpc.api.IdeTools
import com.asakii.rpc.api.RpcMessage as RpcMessageApi
import com.asakii.rpc.proto.*
import com.asakii.server.rpc.AiAgentRpcServiceImpl
import com.asakii.server.rpc.ClientCaller
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import com.asakii.server.logging.StandaloneLogging
import com.asakii.server.logging.asyncInfo
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * RSocket 路由处理器
 *
 * 使用 RSocket 的 Request-Response 和 Request-Stream 模式处理 RPC 调用。
 * 支持通过 requester 实现服务端调用客户端（反向调用）。
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
 * - agent.getHistorySessions: Request-Response
 *
 * 反向调用路由（服务端 -> 客户端）：
 * - client.call: Request-Response (通用调用)
 */
class RSocketHandler(
    private val ideTools: IdeTools
) {
    // 使用 ws.log 专用 logger
    private val wsLog = KotlinLogging.logger(StandaloneLogging.WS_LOGGER)
    private val json = Json { ignoreUnknownKeys = true }

    // 存储客户端 requester 的引用（用于反向调用）
    private var clientRequester: RSocket? = null

    /**
     * 创建 RSocket 请求处理器
     *
     * 注意：rsocket-kotlin 的 ConnectionAcceptor 不直接提供 requester，
     * 我们需要在首次连接时从客户端接收 requester 引用。
     * 当前实现暂时使用回调方式设置 requester。
     */
    fun createHandler(onRequesterAvailable: (RSocket) -> Unit = {}): RSocket {
        wsLog.info("🔌 [RSocket] 创建请求处理器")

        // 反向调用支持
        val pendingClientCalls = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
        val callIdCounter = AtomicInteger(0)

        // 创建 ClientCaller（初始时 requester 可能为空）
        val clientCaller = createClientCaller(pendingClientCalls, callIdCounter)

        // 为每个连接创建独立的 RPC 服务
        val rpcService: AiAgentRpcService = AiAgentRpcServiceImpl(ideTools, clientCaller)

        val handler = RSocketRequestHandler {
            // ==================== Request-Response ====================

            requestResponse { request ->
                val route = extractRoute(request)
                val dataBytes = request.data.readByteArray()
                wsLog.info("📨 [RSocket] ← Request-Response: $route")
                wsLog.debug("📨 [RSocket] ← Request data (${dataBytes.size} bytes)")

                val response = when (route) {
                    "agent.connect" -> handleConnect(dataBytes, rpcService)
                    "agent.interrupt" -> handleInterrupt(rpcService)
                    "agent.disconnect" -> handleDisconnect(rpcService)
                    "agent.setModel" -> handleSetModel(dataBytes, rpcService)
                    "agent.setPermissionMode" -> handleSetPermissionMode(dataBytes, rpcService)
                    "agent.getHistory" -> handleGetHistory(rpcService)
                    "agent.getHistorySessions" -> handleGetHistorySessions(dataBytes, rpcService)
                    else -> throw IllegalArgumentException("Unknown route: $route")
                }

                val responseBytes = response.data.readByteArray()
                wsLog.info("📨 [RSocket] → Response: $route (${responseBytes.size} bytes)")
                buildPayload { data(responseBytes) }
            }

            // ==================== Request-Stream ====================

            requestStream { request ->
                val route = extractRoute(request)
                val dataBytes = request.data.readByteArray()
                wsLog.info("📡 [RSocket] ← Request-Stream: $route")
                wsLog.debug("📡 [RSocket] ← Request data (${dataBytes.size} bytes)")

                when (route) {
                    "agent.query" -> handleQuery(dataBytes, rpcService)
                    "agent.queryWithContent" -> handleQueryWithContent(dataBytes, rpcService)
                    else -> throw IllegalArgumentException("Unknown route: $route")
                }
            }
        }

        // 监听连接关闭，自动清理 SDK 资源
        handler.coroutineContext[Job]?.invokeOnCompletion { cause ->
            wsLog.info("🔌 [RSocket] 连接关闭，自动清理资源 (cause: ${cause?.message ?: "正常关闭"})")
            runBlocking(Dispatchers.IO) {
                try {
                    rpcService.disconnect()
                    wsLog.info("✅ [RSocket] SDK 资源已清理")
                } catch (e: Exception) {
                    wsLog.warn("⚠️ [RSocket] 清理 SDK 资源时出错: ${e.message}")
                }
            }
        }

        return handler
    }

    /**
     * 设置客户端 requester（用于反向调用）
     *
     * 在 RSocket 连接建立后，通过此方法设置客户端引用
     */
    fun setClientRequester(requester: RSocket) {
        this.clientRequester = requester
        wsLog.info("🔗 [RSocket] 客户端 requester 已设置")
    }

    // ==================== Request-Response Handlers ====================

    private suspend fun handleConnect(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val options = if (dataBytes.isNotEmpty()) {
            val protoOptions = ConnectOptions.parseFrom(dataBytes)
            wsLog.debug("📥 [RSocket] connect options: provider=${protoOptions.provider}, model=${protoOptions.model}")
            protoOptions.toRpc()
        } else {
            wsLog.debug("📥 [RSocket] connect options: (default)")
            null
        }

        val result = rpcService.connect(options)
        wsLog.info("📤 [RSocket] connect result: sessionId=${result.sessionId}, provider=${result.provider}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleInterrupt(rpcService: AiAgentRpcService): Payload {
        wsLog.info("📥 [RSocket] interrupt request")
        val result = rpcService.interrupt()
        wsLog.info("📤 [RSocket] interrupt result: status=${result.status}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleDisconnect(rpcService: AiAgentRpcService): Payload {
        wsLog.info("📥 [RSocket] disconnect request")
        val result = rpcService.disconnect()
        wsLog.info("📤 [RSocket] disconnect result: status=${result.status}")
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

    private suspend fun handleGetHistory(rpcService: AiAgentRpcService): Payload {
        wsLog.info("📥 [RSocket] getHistory request")
        val result = rpcService.getHistory()
        wsLog.info("📤 [RSocket] getHistory result: messages=${result.messages.size}")
        return buildPayload { data(result.toProto().toByteArray()) }
    }

    private suspend fun handleGetHistorySessions(dataBytes: ByteArray, rpcService: AiAgentRpcService): Payload {
        val maxResults = if (dataBytes.isNotEmpty()) {
            GetHistorySessionsRequest.parseFrom(dataBytes).maxResults
        } else {
            50
        }
        wsLog.info("📥 [RSocket] getHistorySessions request: maxResults=$maxResults")
        val result = rpcService.getHistorySessions(maxResults)
        wsLog.info("📤 [RSocket] getHistorySessions result: sessions=${result.sessions.size}")
        return buildPayload { data(result.toProto().toByteArray()) }
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

        // 记录完整消息内容（格式化在日志线程执行）
        wsLog.asyncInfo { "📤 [RSocket] #$counter ($route) $msgType: ${formatRpcMessage(message)}" }

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
            "content=${formatRpcContentBlocks(message.message.content)}"
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
        is com.asakii.rpc.api.RpcCommandExecutionBlock -> "Command(cmd=${block.command}, output=${block.output})"
        is com.asakii.rpc.api.RpcFileChangeBlock -> "FileChange(changes=${block.changes})"
        is com.asakii.rpc.api.RpcMcpToolCallBlock -> "McpTool(server=${block.server}, tool=${block.tool})"
        is com.asakii.rpc.api.RpcWebSearchBlock -> "WebSearch(query=${block.query})"
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
     * 直接使用方法名作为 RSocket 路由，params 作为 JSON 数据
     */
    private fun createClientCaller(
        pendingClientCalls: ConcurrentHashMap<String, CompletableDeferred<JsonElement>>,
        callIdCounter: AtomicInteger
    ): ClientCaller {
        return object : ClientCaller {
            override suspend fun call(method: String, params: Any): JsonElement {
                val requester = clientRequester
                    ?: throw RuntimeException("Client requester not available")

                val callId = "srv-${callIdCounter.incrementAndGet()}"
                wsLog.info("📤 [RSocket] → 反向调用: route=$method, callId=$callId")

                // 将 params 转换为 JSON bytes
                val paramsJson = when (params) {
                    is JsonElement -> json.encodeToString(params)
                    else -> json.encodeToString(json.encodeToJsonElement(params))
                }
                val paramsBytes = paramsJson.toByteArray(Charsets.UTF_8)
                wsLog.debug("📤 [RSocket] → 反向调用 params: $paramsJson")

                // 创建等待响应的 Deferred
                val deferred = CompletableDeferred<JsonElement>()
                pendingClientCalls[callId] = deferred

                try {
                    // 直接用方法名作为路由
                    val routeMetadata = createRouteMetadata(method)
                    val metadataBuffer = Buffer().apply { write(routeMetadata) }
                    val dataBuffer = Buffer().apply { write(paramsBytes) }
                    val payload = buildPayload {
                        data(dataBuffer)
                        metadata(metadataBuffer)
                    }

                    val responsePayload = withTimeout(60_000) {
                        requester.requestResponse(payload)
                    }

                    // 响应直接是 JSON
                    val resultJson = responsePayload.data.readByteArray().toString(Charsets.UTF_8)
                    wsLog.info("📥 [RSocket] ← 反向调用成功: route=$method, callId=$callId")
                    wsLog.debug("📥 [RSocket] ← 反向调用 result: $resultJson")
                    return json.parseToJsonElement(resultJson)

                } catch (e: TimeoutCancellationException) {
                    wsLog.warn("📥 [RSocket] ← 反向调用超时: route=$method, callId=$callId")
                    throw RuntimeException("Client call timeout: $method")
                } catch (e: Exception) {
                    wsLog.warn("📥 [RSocket] ← 反向调用失败: route=$method, error=${e.message}")
                    throw RuntimeException("Client call failed: ${e.message}")
                } finally {
                    pendingClientCalls.remove(callId)
                }
            }
        }
    }
}
