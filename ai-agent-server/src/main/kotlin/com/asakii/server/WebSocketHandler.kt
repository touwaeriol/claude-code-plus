package com.asakii.server

import com.asakii.rpc.api.*
import com.asakii.server.rpc.AiAgentRpcServiceImpl
import com.asakii.server.rpc.ClientCaller
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

/**
 * WebSocket 处理器 - 基于简化 RPC 协议
 *
 * 架构设计：
 * 1. 一个 WebSocket 连接 = 一个 AI Agent 会话
 * 2. 双向通信：客户端发送 RPC 请求，服务端推送流式响应
 * 3. 自动资源管理：连接关闭时自动清理资源
 * 4. 流式响应：实时推送 SDK 消息给客户端
 */
class WebSocketHandler(
    private val ideTools: IdeTools
) {
    private val logger = Logger.getLogger(javaClass.name)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
        encodeDefaults = true  // 确保序列化默认值（如 RpcStreamData.type）
        classDiscriminator = "type"
    }

    /**
     * 配置 WebSocket 路由
     */
    fun Route.configureWebSocket() {
        webSocket("/ws") {
            logger.info("🔌 WebSocket 连接建立: ${call.request.local.remoteHost}")

            // 双向 RPC 支持：跟踪服务器发起的请求
            val pendingClientCalls = ConcurrentHashMap<String, CompletableDeferred<JsonElement>>()
            val callIdCounter = AtomicInteger(0)

            // 创建 ClientCaller 实现，用于服务器调用前端
            val clientCaller = object : ClientCaller {
                override suspend fun call(method: String, params: Any): JsonElement {
                    val id = "srv-${callIdCounter.incrementAndGet()}"
                    val deferred = CompletableDeferred<JsonElement>()
                    pendingClientCalls[id] = deferred

                    // 构建请求消息
                    val request = buildJsonObject {
                        put("id", id)
                        put("method", method)
                        put("params", when (params) {
                            is JsonElement -> params
                            else -> Json.encodeToJsonElement(params.toString())
                        })
                    }

                    logger.info("📤 [双向RPC] 向客户端发送请求: id=$id, method=$method")
                    send(Frame.Text(json.encodeToString(request)))

                    // 等待响应（超时 5 分钟）
                    return try {
                        withTimeout(300_000) {
                            deferred.await()
                        }
                    } catch (e: Exception) {
                        pendingClientCalls.remove(id)
                        logger.severe("❌ [双向RPC] 等待客户端响应超时或失败: id=$id, error=${e.message}")
                        throw e
                    }
                }
            }

            // 为每个连接创建独立的 RPC 服务实例，传入 ClientCaller
            val rpcService: AiAgentRpcService = AiAgentRpcServiceImpl(ideTools, clientCaller)

            try {
                // 直接处理收到的消息，不做队列/同步检查
                // 同步由前端处理，后端直接转发给 SDK
                // 每个请求启动独立协程，避免 collect 阻塞消息接收
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()

                        // 检查是否是客户端对服务器请求的响应
                        try {
                            val jsonObj = json.parseToJsonElement(text).jsonObject
                            val id = jsonObj["id"]?.jsonPrimitive?.contentOrNull

                            if (id != null && id.startsWith("srv-")) {
                                // 这是客户端对服务器请求的响应
                                val deferred = pendingClientCalls.remove(id)
                                if (deferred != null) {
                                    val result = jsonObj["result"]
                                    val error = jsonObj["error"]?.jsonPrimitive?.contentOrNull

                                    if (error != null) {
                                        logger.warning("⚠️ [双向RPC] 客户端返回错误: id=$id, error=$error")
                                        deferred.completeExceptionally(Exception(error))
                                    } else if (result != null) {
                                        logger.info("📥 [双向RPC] 收到客户端响应: id=$id")
                                        deferred.complete(result)
                                    } else {
                                        deferred.complete(JsonNull)
                                    }
                                } else {
                                    logger.warning("⚠️ [双向RPC] 收到未知响应: id=$id")
                                }
                                continue
                            }
                        } catch (_: Exception) {
                            // 解析失败，当作普通请求处理
                        }

                        // 启动独立协程处理请求
                        launch {
                            handleRpcRequest(text, rpcService)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warning("⚠️ WebSocket 错误: ${e.message}")
                e.printStackTrace()
            } finally {
                // 清理未完成的调用
                pendingClientCalls.values.forEach { deferred ->
                    deferred.completeExceptionally(Exception("WebSocket 连接已关闭"))
                }
                pendingClientCalls.clear()

                // 连接关闭时自动断开 Claude 会话
                try {
                    rpcService.disconnect()
                    logger.info("🔌 WebSocket 连接关闭，会话已清理")
                } catch (e: Exception) {
                    logger.warning("⚠️ 清理会话时出错: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 处理 RPC 请求 - 直接转发给 SDK，不做同步控制
     */
    private suspend fun DefaultWebSocketServerSession.handleRpcRequest(
        requestText: String,
        rpcService: AiAgentRpcService
    ) {
        try {
            val request = json.decodeFromString<RpcRequest>(requestText)
            logger.info("📨 收到 RPC 请求: ${request.method}")

            when (request.method) {
                "connect" -> {
                    val options = request.params?.let { json.decodeFromJsonElement<RpcConnectOptions>(it) }
                    val result = rpcService.connect(options)
                    val payload = json.encodeToJsonElement(RpcConnectResult.serializer(), result)
                    sendResponse(request.id, payload)
                }

                "query" -> {
                    val params = request.params?.let { json.decodeFromJsonElement<QueryParams>(it) }
                        ?: throw IllegalArgumentException("Missing params")

                    var messageCount = 0

                    try {
                        logger.info("🚀 [WebSocket] 开始处理 query: id=${request.id}, message=${params.message.take(50)}...")
                        rpcService.query(params.message)
                            .catch { e ->
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                logger.severe("❌ [WebSocket] 查询错误: id=${request.id}, error=${e.message}")
                                e.printStackTrace()
                                sendError(request.id, e.message ?: "Query failed")
                            }
                            .collect { event ->
                                messageCount++
                                val payload = json.encodeToJsonElement(RpcMessage.serializer(), event)
                                sendStreamData(request.id, payload)
                            }

                        logger.info("✅ [WebSocket] query 流正常结束: id=${request.id}, 共收到 $messageCount 条消息")
                        // result 消息已经是流式响应的终止标记，不需要额外发送 complete
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        logger.info("ℹ️ [WebSocket] query 被用户取消: id=${request.id}")
                        throw e
                    } catch (e: Exception) {
                        logger.severe("❌ [WebSocket] query 处理异常: id=${request.id}, error=${e.message}")
                        e.printStackTrace()
                        sendError(request.id, e.message ?: "Query failed")
                    }
                }

                "queryWithContent" -> {
                    val params = request.params?.let { json.decodeFromJsonElement<QueryWithContentParams>(it) }
                        ?: throw IllegalArgumentException("Missing params")

                    var messageCount = 0

                    try {
                        val contentPreview = params.content.take(1).joinToString {
                            when (it) {
                                is com.asakii.rpc.api.RpcTextBlock -> "text:${it.text.take(30)}"
                                is com.asakii.rpc.api.RpcImageBlock -> "image"
                                else -> it::class.simpleName ?: "unknown"
                            }
                        }
                        logger.info("🚀 [WebSocket] 开始处理 queryWithContent: id=${request.id}, contentBlocks=${params.content.size}, preview=$contentPreview...")
                        rpcService.queryWithContent(params.content)
                            .catch { e ->
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                logger.severe("❌ [WebSocket] 带内容查询错误: id=${request.id}, error=${e.message}")
                                e.printStackTrace()
                                sendError(request.id, e.message ?: "Query failed")
                            }
                            .collect { event ->
                                messageCount++
                                val payload = json.encodeToJsonElement(RpcMessage.serializer(), event)
                                sendStreamData(request.id, payload)
                            }

                        logger.info("✅ [WebSocket] queryWithContent 流正常结束: id=${request.id}, 共收到 $messageCount 条消息")
                        // result 消息已经是流式响应的终止标记，不需要额外发送 complete
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        logger.info("ℹ️ [WebSocket] queryWithContent 被用户取消: id=${request.id}")
                        throw e
                    } catch (e: Exception) {
                        logger.severe("❌ [WebSocket] queryWithContent 处理异常: id=${request.id}, error=${e.message}")
                        e.printStackTrace()
                        sendError(request.id, e.message ?: "Query failed")
                    }
                }

                "interrupt" -> {
                    logger.info("🔔 [WebSocket] 开始处理 interrupt 请求: id=${request.id}")
                    try {
                        val result = rpcService.interrupt()
                        logger.info("🔔 [WebSocket] interrupt 完成，准备发送响应: id=${request.id}")
                        val payload = json.encodeToJsonElement(RpcStatusResult.serializer(), result)
                        sendResponse(request.id, payload)
                        logger.info("🔔 [WebSocket] interrupt 响应已发送: id=${request.id}")
                    } catch (e: Exception) {
                        logger.severe("❌ [WebSocket] interrupt 处理失败: id=${request.id}, error=${e.message}")
                        e.printStackTrace()
                        sendError(request.id, e.message ?: "Interrupt failed")
                    }
                }

                "disconnect" -> {
                    val result = rpcService.disconnect()
                    val payload = json.encodeToJsonElement(RpcStatusResult.serializer(), result)
                    sendResponse(request.id, payload)
                }

                "setModel" -> {
                    val model = (request.params as? JsonPrimitive)?.content
                        ?: throw IllegalArgumentException("Missing model parameter")
                    val result = rpcService.setModel(model)
                    val payload = json.encodeToJsonElement(RpcSetModelResult.serializer(), result)
                    sendResponse(request.id, payload)
                }

                "setPermissionMode" -> {
                    val params = request.params?.let { json.decodeFromJsonElement<SetPermissionModeParams>(it) }
                        ?: throw IllegalArgumentException("Missing params")
                    val result = rpcService.setPermissionMode(params.mode)
                    val payload = json.encodeToJsonElement(RpcSetPermissionModeResult.serializer(), result)
                    sendResponse(request.id, payload)
                }

                "getHistory" -> {
                    val result = rpcService.getHistory()
                    val payload = json.encodeToJsonElement(RpcHistory.serializer(), result)
                    sendResponse(request.id, payload)
                }

                "getHistorySessions" -> {
                    val maxResults = request.params?.jsonObject?.get("maxResults")?.jsonPrimitive?.intOrNull ?: 50
                    val result = rpcService.getHistorySessions(maxResults)
                    val payload = json.encodeToJsonElement(RpcHistorySessionsResult.serializer(), result)
                    sendResponse(request.id, payload)
                }

                else -> {
                    sendError(request.id, "Unknown method: ${request.method}")
                }
            }
        } catch (e: Exception) {
            logger.severe("❌ 处理请求失败: ${e.message}")
            e.printStackTrace()
            try {
                val request = json.decodeFromString<RpcRequest>(requestText)
                sendError(request.id, e.message ?: "Request failed")
            } catch (_: Exception) {
                // 如果无法解析请求，忽略错误
            }
        }
    }
    
    /**
     * 发送 RPC 响应
     */
    private suspend fun DefaultWebSocketServerSession.sendResponse(
        id: String,
        result: JsonElement
    ) {
        val response = RpcResponse(id = id, result = result)
        val jsonString = json.encodeToString(response)
        logger.info("📤 [WebSocket] 发送 RPC 响应: id=$id, preview=${jsonString.take(200)}...")
        send(jsonString)
    }

    /**
     * 发送流式数据
     */
    private suspend fun DefaultWebSocketServerSession.sendStreamData(
        id: String,
        data: JsonElement
    ) {
        try {
        val response = RpcStreamData(id = id, data = data)
        val jsonString = json.encodeToString(response)
            logger.info("📤 [WebSocket] 发送流式数据: id=$id, type=${data.jsonObject["type"]?.jsonPrimitive?.contentOrNull}, preview=${jsonString.take(200)}...")
        send(jsonString)
            logger.fine("✅ [WebSocket] 流式数据已发送: id=$id")
        } catch (e: Exception) {
            logger.severe("❌ [WebSocket] 发送流式数据失败: id=$id, error=${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * 发送流完成信号
     */
    private suspend fun DefaultWebSocketServerSession.sendStreamComplete(id: String) {
        val response = RpcStreamComplete(id = id)
        send(json.encodeToString(response))
    }

    /**
     * 发送错误响应
     */
    private suspend fun DefaultWebSocketServerSession.sendError(
        id: String,
        message: String
    ) {
        val response = RpcResponse(id = id, error = message)
        send(json.encodeToString(response))
    }
}

/**
 * RPC 请求
 */
@kotlinx.serialization.Serializable
data class RpcRequest(
    val id: String,
    val method: String,
    val params: JsonElement? = null
)

/**
 * RPC 响应
 */
@kotlinx.serialization.Serializable
data class RpcResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: String? = null
)

/**
 * RPC 流式数据
 */
@kotlinx.serialization.Serializable
data class RpcStreamData(
    val id: String,
    val type: String = "stream",
    val data: JsonElement
)

/**
 * RPC 流完成
 */
@kotlinx.serialization.Serializable
data class RpcStreamComplete(
    val id: String,
    val type: String = "complete"
)

@kotlinx.serialization.Serializable
data class QueryParams(
    val message: String
)

@kotlinx.serialization.Serializable
data class QueryWithContentParams(
    val content: List<RpcContentBlock>
)

@kotlinx.serialization.Serializable
data class SetPermissionModeParams(
    val mode: RpcPermissionMode
)

