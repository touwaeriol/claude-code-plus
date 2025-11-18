package com.claudecodeplus.server

import com.claudecodeplus.rpc.api.ClaudeRpcService
import com.claudecodeplus.server.rpc.ClaudeRpcServiceImpl
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.logging.Logger

/**
 * WebSocket 处理器 - 基于简化 RPC 协议
 *
 * 架构设计：
 * 1. 一个 WebSocket 连接 = 一个 Claude 会话
 * 2. 双向通信：客户端发送 RPC 请求，服务端推送流式响应
 * 3. 自动资源管理：连接关闭时自动清理资源
 * 4. 流式响应：实时推送 SDK 消息给客户端
 */
class WebSocketHandler(
    private val ideActionBridge: IdeActionBridge
) {
    private val logger = Logger.getLogger(javaClass.name)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    /**
     * 配置 WebSocket 路由
     */
    fun Route.configureWebSocket() {
        webSocket("/ws") {
            logger.info("🔌 WebSocket 连接建立: ${call.request.local.remoteHost}")
            
            // 为每个连接创建独立的 RPC 服务实例
            val rpcService: ClaudeRpcService = ClaudeRpcServiceImpl(ideActionBridge)
            
            try {
                // 处理客户端消息
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        handleRpcRequest(text, rpcService)
                    }
                }
            } catch (e: Exception) {
                logger.warning("⚠️ WebSocket 错误: ${e.message}")
            } finally {
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
        rpcService: ClaudeRpcService
    ) {
        try {
            val request = json.decodeFromString<RpcRequest>(requestText)
            logger.info("📨 收到 RPC 请求: ${request.method}")
            
            when (request.method) {
                "connect" -> {
                    val options = request.params as? JsonObject
                    val result = rpcService.connect(options)
                    sendResponse(request.id, result)
                }
                
                "query" -> {
                    val paramsObj = request.params as? JsonObject
                        ?: throw IllegalArgumentException("Invalid params format")
                    val message = paramsObj["message"]?.jsonPrimitive?.content
                        ?: throw IllegalArgumentException("Missing message parameter")

                    // 发送流式响应
                    launch {
                        rpcService.query(message)
                            .catch { e ->
                                logger.severe("❌ 查询错误: ${e.message}")
                                sendError(request.id, e.message ?: "Query failed")
                            }
                            .collect { msg ->
                                sendStreamData(request.id, msg)
                            }

                        // 流结束
                        sendStreamComplete(request.id)
                    }
                }
                
                "interrupt" -> {
                    val result = rpcService.interrupt()
                    sendResponse(request.id, result)
                }
                
                "disconnect" -> {
                    val result = rpcService.disconnect()
                    sendResponse(request.id, result)
                }
                
                "setModel" -> {
                    val model = (request.params as? JsonPrimitive)?.content
                        ?: throw IllegalArgumentException("Missing model parameter")
                    val result = rpcService.setModel(model)
                    sendResponse(request.id, result)
                }
                
                "getHistory" -> {
                    val result = rpcService.getHistory()
                    sendResponse(request.id, result)
                }
                
                else -> {
                    sendError(request.id, "Unknown method: ${request.method}")
                }
            }
        } catch (e: Exception) {
            logger.severe("❌ 处理请求失败: ${e.message}")
            e.printStackTrace()
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
        val response = RpcStreamData(id = id, data = data)
        send(json.encodeToString(response))
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

