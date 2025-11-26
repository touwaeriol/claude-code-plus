package com.asakii.server

import com.asakii.rpc.api.*
import com.asakii.server.rpc.AiAgentRpcServiceImpl
import com.asakii.server.tools.IdeTools
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicBoolean
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
            
            // 为每个连接创建独立的 RPC 服务实例
            val rpcService: AiAgentRpcService = AiAgentRpcServiceImpl(ideTools)
            
            // 请求队列：确保同一时间只处理一个请求
            val requestQueue = Channel<String>(Channel.UNLIMITED)
            val requestMutex = Mutex()
            val isProcessing = AtomicBoolean(false)  // 使用原子变量，可以在锁外检查
            
            // 启动请求处理协程（串行处理）
            val processor = launch {
                for (requestText in requestQueue) {
                    requestMutex.withLock {
                        // 检查是否是生成请求（query/queryWithContent）
                        val isGenerationRequest = try {
                            val request = json.decodeFromString<RpcRequest>(requestText)
                            request.method == "query" || request.method == "queryWithContent"
                        } catch (e: Exception) {
                            false
                        }
                        
                        // 注意：对于生成请求，isProcessing 标志已经在接收消息时设置
                        // 这里只需要处理请求，并在完成时清除标志
                        try {
                            handleRpcRequest(requestText, rpcService, requestMutex) {
                                if (isGenerationRequest) {
                                    isProcessing.set(false)
                                }
                            }
                        } catch (e: Exception) {
                            logger.severe("❌ 处理 RPC 请求时出错: ${e.message}")
                            e.printStackTrace()
                            if (isGenerationRequest) {
                                isProcessing.set(false)
                            }
                        }
                    }
                }
            }
            
            try {
                // 接收客户端消息并检查是否可以处理
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        
                        // 快速检查：如果是生成请求且正在处理，立即返回错误
                        var shouldEnqueue = true
                        try {
                            val request = json.decodeFromString<RpcRequest>(text)
                            val isGenerationRequest = request.method == "query" || request.method == "queryWithContent"
                            
                            if (isGenerationRequest) {
                                // 使用 compareAndSet 原子性地尝试设置处理标志
                                // 如果当前是 false，设置为 true 并返回 true（可以处理）
                                // 如果当前是 true，返回 false（已有请求在处理）
                                val canProcess = isProcessing.compareAndSet(false, true)
                                
                                if (!canProcess) {
                                    logger.warning("⚠️ 拒绝请求：上一个生成请求还在处理中，id=${request.id}")
                                    // 立即返回错误，不加入队列
                                    try {
                                        sendError(request.id, "上一个请求还在处理中，请等待完成后再发送新消息")
                                    } catch (e: Exception) {
                                        logger.severe("❌ 发送错误响应失败: ${e.message}")
                                        e.printStackTrace()
                                    }
                                    shouldEnqueue = false
                                } else {
                                    // 成功设置标志，请求可以加入队列
                                    logger.info("✅ 接受生成请求，id=${request.id}")
                                }
                            }
                        } catch (e: Exception) {
                            // 解析失败，继续加入队列，让处理器处理错误
                            logger.warning("⚠️ 解析请求失败，加入队列让处理器处理: ${e.message}")
                        }
                        
                        // 加入队列
                        if (shouldEnqueue) {
                            try {
                                requestQueue.trySend(text).getOrThrow()
                            } catch (e: Exception) {
                                logger.severe("❌ 无法将请求加入队列: ${e.message}")
                                e.printStackTrace()
                                // 如果加入队列失败且是生成请求，需要清除标志
                                try {
                                    val request = json.decodeFromString<RpcRequest>(text)
                                    if (request.method == "query" || request.method == "queryWithContent") {
                                        isProcessing.set(false)
                                    }
                                } catch (e2: Exception) {
                                    // 忽略解析错误
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warning("⚠️ WebSocket 错误: ${e.message}")
                e.printStackTrace()
            } finally {
                // 关闭队列
                requestQueue.close()
                processor.cancel()
                
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
     * 处理 RPC 请求
     */
    private suspend fun DefaultWebSocketServerSession.handleRpcRequest(
        requestText: String,
        rpcService: AiAgentRpcService,
        requestMutex: Mutex,
        onComplete: () -> Unit
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
                    onComplete()
                }
                
                "query" -> {
                    val params = request.params?.let { json.decodeFromJsonElement<QueryParams>(it) }
                        ?: throw IllegalArgumentException("Missing params")

                    // 发送流式响应（在请求处理协程中直接处理，不启动新的协程）
                    try {
                        logger.info("🚀 [WebSocket] 开始处理 query: id=${request.id}, message=${params.message.take(50)}...")
                        rpcService.query(params.message)
                            .catch { e ->
                                // CancellationException 应该被重新抛出，让协程取消机制处理
                                if (e is kotlinx.coroutines.CancellationException) {
                                    throw e
                                }
                                logger.severe("❌ [WebSocket] 查询错误: id=${request.id}, error=${e.message}")
                                e.printStackTrace()
                                sendError(request.id, e.message ?: "Query failed")
                            }
                            .collect { event ->
                                try {
                                val payload = json.encodeToJsonElement(RpcUiEvent.serializer(), event)
                                sendStreamData(request.id, payload)
                                } catch (e: Exception) {
                                    // CancellationException 应该被重新抛出
                                    if (e is kotlinx.coroutines.CancellationException) {
                                        throw e
                                    }
                                    logger.severe("❌ [WebSocket] 发送流式数据失败: id=${request.id}, error=${e.message}")
                                    e.printStackTrace()
                                    // 继续处理下一个事件，不中断整个流
                                }
                            }

                        // 流结束
                        logger.info("✅ [WebSocket] query 流结束: id=${request.id}")
                        sendStreamComplete(request.id)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // 正常取消，不需要记录错误
                        logger.info("ℹ️ [WebSocket] query 被取消: id=${request.id}")
                        throw e  // 重新抛出，让协程取消机制处理
                    } catch (e: Exception) {
                        logger.severe("❌ [WebSocket] query 处理异常: id=${request.id}, error=${e.message}")
                        e.printStackTrace()
                        sendError(request.id, e.message ?: "Query failed")
                    } finally {
                        onComplete()
                    }
                }

                "queryWithContent" -> {
                    val params = request.params?.let { json.decodeFromJsonElement<QueryWithContentParams>(it) }
                        ?: throw IllegalArgumentException("Missing params")

                    // 发送流式响应（在请求处理协程中直接处理，不启动新的协程）
                        var messageCount = 0
                        var hasResultMessage = false

                    try {
                        val contentPreview = params.content.take(1).joinToString { 
                            when (it) {
                                is com.asakii.rpc.api.RpcTextBlock -> "text:${it.text.take(30)}"
                                is com.asakii.rpc.api.RpcImageBlock -> "image"
                                else -> it::class.simpleName ?: "unknown"
                            }
                        }
                        logger.info("🚀 [WebSocket] 开始处理 queryWithContent: id=${request.id}, contentBlocks=${params.content.size}, preview=$contentPreview...")
                        logger.info("⏳ [WebSocket] 如果上一个请求还在处理，此请求将等待...")
                        rpcService.queryWithContent(params.content)
                            .catch { e ->
                                // CancellationException 应该被重新抛出，让协程取消机制处理
                                if (e is kotlinx.coroutines.CancellationException) {
                                    throw e
                                }
                                logger.severe("❌ [WebSocket] 带内容查询错误: id=${request.id}, error=${e.message}")
                                e.printStackTrace()
                                sendError(request.id, e.message ?: "Query failed")
                            }
                            .collect { event ->
                                try {
                                val payload = json.encodeToJsonElement(RpcUiEvent.serializer(), event)
                                messageCount++
                                val msgType = payload.jsonObject["type"]?.jsonPrimitive?.contentOrNull
                                    logger.info("📨 [WebSocket] 收到消息 #$messageCount: id=${request.id}, type=$msgType")

                                if (msgType == "result") {
                                    hasResultMessage = true
                                    logger.info("✅ [WebSocket] 收到 ResultMessage!")
                                }

                                sendStreamData(request.id, payload)
                                } catch (e: Exception) {
                                    // CancellationException 应该被重新抛出
                                    if (e is kotlinx.coroutines.CancellationException) {
                                        throw e
                                    }
                                    logger.severe("❌ [WebSocket] 发送流式数据失败: id=${request.id}, error=${e.message}")
                                    e.printStackTrace()
                                    // 继续处理下一个事件，不中断整个流
                                }
                            }

                        logger.info("📊 [WebSocket] 流结束: id=${request.id}, 共收到 $messageCount 条消息，hasResultMessage=$hasResultMessage")
                        // 流结束
                        sendStreamComplete(request.id)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // 正常取消，不需要记录错误
                        logger.info("ℹ️ [WebSocket] queryWithContent 被取消: id=${request.id}")
                        throw e  // 重新抛出，让协程取消机制处理
                    } catch (e: Exception) {
                        logger.severe("❌ [WebSocket] queryWithContent 处理异常: id=${request.id}, error=${e.message}")
                        e.printStackTrace()
                        sendError(request.id, e.message ?: "Query failed")
                    } finally {
                        onComplete()
                    }
                }

                "interrupt" -> {
                    val result = rpcService.interrupt()
                    val payload = json.encodeToJsonElement(RpcStatusResult.serializer(), result)
                    sendResponse(request.id, payload)
                    onComplete()
                }
                
                "disconnect" -> {
                    val result = rpcService.disconnect()
                    val payload = json.encodeToJsonElement(RpcStatusResult.serializer(), result)
                    sendResponse(request.id, payload)
                    onComplete()
                }
                
                "setModel" -> {
                    val model = (request.params as? JsonPrimitive)?.content
                        ?: throw IllegalArgumentException("Missing model parameter")
                    val result = rpcService.setModel(model)
                    val payload = json.encodeToJsonElement(RpcSetModelResult.serializer(), result)
                    sendResponse(request.id, payload)
                    onComplete()
                }
                
                "getHistory" -> {
                    val result = rpcService.getHistory()
                    val payload = json.encodeToJsonElement(RpcHistory.serializer(), result)
                    sendResponse(request.id, payload)
                    onComplete()
                }
                
                else -> {
                    sendError(request.id, "Unknown method: ${request.method}")
                    onComplete()
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
            onComplete()
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
        send(json.encodeToString(response))
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

