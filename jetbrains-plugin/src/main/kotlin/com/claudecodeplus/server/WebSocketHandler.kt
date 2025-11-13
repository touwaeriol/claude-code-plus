package com.claudecodeplus.server

import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.server.services.ClaudeSessionManager
import com.intellij.openapi.project.Project
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.logging.Logger

/**
 * WebSocket 请求数据结构
 */
@Serializable
data class WebSocketRequest(
    val type: String,  // "query" | "interrupt"
    val data: JsonObject? = null
)

/**
 * WebSocket 响应数据结构
 */
@Serializable
data class WebSocketResponse(
    val type: String,  // "assistant" | "result" | "error"
    val message: JsonElement
)

/**
 * WebSocket 处理器
 *
 * 负责处理 WebSocket 连接和消息交互
 *
 * 架构设计：
 * 1. 每个会话独立的 WebSocket 连接（路由：/ws/sessions/{sessionId}）
 * 2. 双向通信：客户端发送 query/interrupt，服务端推送响应
 * 3. 自动资源管理：连接关闭时调用 ClaudeSessionManager.closeSession()
 * 4. 流式响应：实时推送 SDK 消息给客户端
 */
class WebSocketHandler(
    private val project: Project
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
        webSocket("/ws/sessions/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId == null) {
                logger.warning("⚠️ WebSocket 连接缺少 sessionId")
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing sessionId"))
                return@webSocket
            }

            logger.info("🔌 WebSocket 连接已建立: sessionId=$sessionId, remote=${call.request.local.remoteHost}")

            try {
                // ✅ 关键1：立即初始化 SDK 并连接
                logger.info("🎬 初始化 SDK 会话: $sessionId")
                try {
                    ClaudeSessionManager.initializeSession(sessionId, project)
                    logger.info("✅ SDK 会话初始化成功: $sessionId")
                } catch (e: Exception) {
                    logger.severe("❌ SDK 初始化失败: ${e.message}")
                    sendError("SDK 初始化失败: ${e.message}")
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "SDK initialization failed"))
                    return@webSocket
                }

                // ✅ 关键2：启动独立协程监听 SDK 消息流并推送到 WebSocket
                val messageListenerJob = launch {
                    try {
                        logger.info("👂 启动消息监听协程: $sessionId")
                        ClaudeSessionManager.observeSessionMessages(sessionId)
                            .collect { sdkMessage ->
                                // 收到一个 SDK 消息就推送一个到前端
                                val response = convertSdkMessage(sdkMessage)
                                sendResponse(response)
                            }
                    } catch (e: CancellationException) {
                        logger.info("⚠️ 消息监听协程被取消: $sessionId")
                    } catch (e: Exception) {
                        logger.severe("❌ 消息监听错误: ${e.message}")
                        sendError("消息监听错误: ${e.message}")
                    }
                }

                // ✅ 关键3：处理客户端消息（query 只发送不等待）
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            handleClientMessage(sessionId, text)
                        }
                        is Frame.Close -> {
                            logger.info("🔌 WebSocket 客户端主动关闭: $sessionId")
                        }
                        else -> {
                            logger.warning("⚠️ 收到不支持的帧类型: ${frame.frameType}")
                        }
                    }
                }

                // 取消消息监听协程
                messageListenerJob.cancel()

            } catch (e: CancellationException) {
                logger.info("⚠️ WebSocket 连接被取消: $sessionId")
            } catch (e: Exception) {
                logger.severe("❌ WebSocket 错误: sessionId=$sessionId, error=${e.message}")
                e.printStackTrace()

                // 发送错误消息给客户端
                sendError("WebSocket 错误: ${e.message}")
            } finally {
                // ✅ 关键：连接关闭时自动清理资源
                logger.info("🧹 WebSocket 连接关闭，清理会话资源: $sessionId")
                ClaudeSessionManager.closeSession(sessionId)
            }
        }
    }

    /**
     * 处理客户端消息
     */
    private suspend fun DefaultWebSocketServerSession.handleClientMessage(
        sessionId: String,
        text: String
    ) {
        try {
            logger.info("📨 收到客户端消息: sessionId=$sessionId, content=${text.take(100)}")

            val request = json.decodeFromString<WebSocketRequest>(text)

            when (request.type) {
                "query" -> handleQuery(sessionId, request)
                "interrupt" -> handleInterrupt(sessionId)
                else -> {
                    logger.warning("⚠️ 未知的消息类型: ${request.type}")
                    sendError("未知的消息类型: ${request.type}")
                }
            }
        } catch (e: Exception) {
            logger.severe("❌ 解析客户端消息失败: ${e.message}")
            e.printStackTrace()
            sendError("消息解析失败: ${e.message}")
        }
    }

    /**
     * 处理查询请求
     */
    private suspend fun DefaultWebSocketServerSession.handleQuery(
        sessionId: String,
        request: WebSocketRequest
    ) {
        val message = request.data?.get("message")?.jsonPrimitive?.content
        if (message == null) {
            logger.warning("⚠️ 缺少 message 字段")
            sendError("缺少 message 字段")
            return
        }

        logger.info("🚀 处理查询: sessionId=$sessionId, message=${message.take(50)}...")

        try {
            // ✅ 只发送消息，不等待响应
            // 响应会通过独立的消息监听协程推送到前端
            ClaudeSessionManager.sendMessageOnly(sessionId, message, project)

            logger.info("✅ 消息已发送: $sessionId")

            // 发送确认消息给客户端
            sendResponse(WebSocketResponse(
                type = "system",
                message = buildJsonObject {
                    put("subtype", "message_sent")
                    put("message", "消息已发送")
                }
            ))

        } catch (e: CancellationException) {
            logger.info("⚠️ 查询被取消: $sessionId")
            throw e
        } catch (e: Exception) {
            logger.severe("❌ 查询处理失败: sessionId=$sessionId, error=${e.message}")
            e.printStackTrace()
            sendError("查询处理失败: ${e.message}")
        }
    }

    /**
     * 处理中断请求
     */
    private suspend fun DefaultWebSocketServerSession.handleInterrupt(sessionId: String) {
        logger.info("⏸️ 处理中断: $sessionId")

        try {
            ClaudeSessionManager.interruptSession(sessionId)

            // 发送中断成功响应
            sendResponse(WebSocketResponse(
                type = "system",
                message = buildJsonObject {
                    put("subtype", "interrupted")
                    put("message", "操作已中断")
                }
            ))
        } catch (e: Exception) {
            logger.severe("❌ 中断失败: ${e.message}")
            sendError("中断失败: ${e.message}")
        }
    }

    /**
     * 转换 SDK 消息为 WebSocket 响应
     */
    private fun convertSdkMessage(message: Message): WebSocketResponse {
        return when (message) {
            is UserMessage -> WebSocketResponse(
                type = "user",
                message = buildJsonObject {
                    put("content", message.content)
                }
            )

            is AssistantMessage -> WebSocketResponse(
                type = "assistant",
                message = buildJsonObject {
                    put("content", json.encodeToJsonElement(message.content))
                    put("model", message.model)
                    put("isStreaming", true)
                }
            )

            is SystemMessage -> WebSocketResponse(
                type = "system",
                message = buildJsonObject {
                    put("subtype", message.subtype)
                    put("data", message.data)
                }
            )

            is ResultMessage -> WebSocketResponse(
                type = "result",
                message = buildJsonObject {
                    put("subtype", message.subtype)
                    put("duration_ms", message.durationMs)
                    put("is_error", message.isError)
                    put("num_turns", message.numTurns)
                    put("session_id", message.sessionId)
                    message.result?.let { put("result", it) }
                    message.usage?.let { put("usage", it) }  // ✅ 直接传递 JsonElement
                }
            )

            is StreamEvent -> WebSocketResponse(
                type = "stream_event",
                message = buildJsonObject {
                    put("uuid", message.uuid)
                    put("session_id", message.sessionId)
                    put("event", message.event)
                }
            )
        }
    }

    /**
     * 发送响应给客户端
     */
    private suspend fun DefaultWebSocketServerSession.sendResponse(response: WebSocketResponse) {
        try {
            val responseText = json.encodeToString(response)
            send(Frame.Text(responseText))
            logger.info("📤 发送响应: type=${response.type}")
        } catch (e: Exception) {
            logger.severe("❌ 发送响应失败: ${e.message}")
        }
    }

    /**
     * 发送错误消息给客户端
     */
    private suspend fun DefaultWebSocketServerSession.sendError(errorMessage: String) {
        sendResponse(WebSocketResponse(
            type = "error",
            message = buildJsonObject {
                put("error", errorMessage)
            }
        ))
    }
}
