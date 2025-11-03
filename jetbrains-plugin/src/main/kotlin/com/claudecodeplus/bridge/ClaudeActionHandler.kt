package com.claudecodeplus.bridge

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.logging.Logger

/**
 * Claude 操作处理器
 * 负责处理与 Claude SDK 相关的所有操作
 */
class ClaudeActionHandler(
    private val project: Project,
    private val bridge: FrontendBridge,
    private val scope: CoroutineScope
) {
    private val logger = Logger.getLogger(javaClass.name)
    private var claudeClient: ClaudeCodeSdkClient? = null
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 处理 Claude 操作
     */
    fun handle(request: FrontendRequest): FrontendResponse {
        return when (request.action) {
            "claude.connect" -> handleConnect(request)
            "claude.query" -> handleQuery(request)
            "claude.interrupt" -> handleInterrupt()
            "claude.disconnect" -> handleDisconnect()
            else -> FrontendResponse(false, error = "Unknown Claude action: ${request.action}")
        }
    }

    /**
     * 连接 Claude
     */
    private fun handleConnect(request: FrontendRequest): FrontendResponse {
        try {
            // 解析选项
            val options = ClaudeAgentOptions(
                model = "claude-sonnet-4-5-20250929",
                cwd = project.basePath?.let { java.nio.file.Path.of(it) },
                debugStderr = true
            )

            // 创建客户端
            claudeClient = ClaudeCodeSdkClient(options)

            // 异步连接
            scope.launch {
                try {
                    claudeClient?.connect()
                    logger.info("✅ Claude connected successfully")

                    bridge.pushEvent(IdeEvent(
                        type = "claude.connected",
                        data = mapOf(
                            "sessionId" to JsonPrimitive(System.currentTimeMillis().toString())
                        )
                    ))
                } catch (e: Exception) {
                    logger.severe("❌ Failed to connect to Claude: ${e.message}")
                    e.printStackTrace()

                    bridge.pushEvent(IdeEvent(
                        type = "claude.error",
                        data = mapOf(
                            "error" to JsonPrimitive(e.message ?: "Connection failed")
                        )
                    ))
                }
            }

            return FrontendResponse(success = true)
        } catch (e: Exception) {
            logger.severe("❌ Failed to create Claude client: ${e.message}")
            return FrontendResponse(false, error = e.message)
        }
    }

    /**
     * 发送查询
     */
    private fun handleQuery(request: FrontendRequest): FrontendResponse {
        val client = claudeClient
        if (client == null || !client.isConnected()) {
            return FrontendResponse(false, error = "Claude not connected")
        }

        val messageData = request.data as? JsonObject
        val message = messageData?.get("message")?.jsonPrimitive?.content

        if (message == null) {
            return FrontendResponse(false, error = "Missing message")
        }

        // 异步发送并接收响应
        scope.launch {
            try {
                logger.info("📤 Sending message to Claude: $message")
                client.query(message)

                // 接收响应流
                client.receiveResponse()
                    .catch { e ->
                        logger.severe("❌ Error receiving response: ${e.message}")
                        e.printStackTrace()

                        bridge.pushEvent(IdeEvent(
                            type = "claude.error",
                            data = mapOf(
                                "error" to JsonPrimitive(e.message ?: "Unknown error")
                            )
                        ))
                    }
                    .collect { sdkMessage ->
                        logger.info("📨 Received message from Claude: ${sdkMessage::class.simpleName}")

                        // 转换消息并推送给前端
                        val messageJson = convertMessage(sdkMessage)

                        bridge.pushEvent(IdeEvent(
                            type = "claude.message",
                            data = mapOf("message" to messageJson)
                        ))
                    }
            } catch (e: Exception) {
                logger.severe("❌ Failed to send message: ${e.message}")
                e.printStackTrace()

                bridge.pushEvent(IdeEvent(
                    type = "claude.error",
                    data = mapOf(
                        "error" to JsonPrimitive(e.message ?: "Failed to send message")
                    )
                ))
            }
        }

        return FrontendResponse(success = true)
    }

    /**
     * 中断执行
     */
    private fun handleInterrupt(): FrontendResponse {
        scope.launch {
            try {
                claudeClient?.interrupt()
                logger.info("⏸️ Claude interrupted")
            } catch (e: Exception) {
                logger.severe("❌ Failed to interrupt: ${e.message}")
            }
        }
        return FrontendResponse(success = true)
    }

    /**
     * 断开连接
     */
    private fun handleDisconnect(): FrontendResponse {
        scope.launch {
            try {
                claudeClient?.disconnect()
                claudeClient = null
                logger.info("🔌 Claude disconnected")

                bridge.pushEvent(IdeEvent(
                    type = "claude.disconnected",
                    data = null
                ))
            } catch (e: Exception) {
                logger.severe("❌ Failed to disconnect: ${e.message}")
            }
        }
        return FrontendResponse(success = true)
    }

    /**
     * 转换 SDK 消息为 JSON
     */
    private fun convertMessage(message: Message): JsonElement {
        return when (message) {
            is UserMessage -> buildJsonObject {
                put("type", "user")
                put("content", message.content)
            }

            is AssistantMessage -> buildJsonObject {
                put("type", "assistant")
                put("content", json.encodeToJsonElement(message.content))
                put("model", message.model)
            }

            is SystemMessage -> buildJsonObject {
                put("type", "system")
                put("subtype", message.subtype)
                put("data", message.data)
            }

            is ResultMessage -> buildJsonObject {
                put("type", "result")
                put("subtype", message.subtype)
                put("duration_ms", message.durationMs)
                put("is_error", message.isError)
                put("num_turns", message.numTurns)
                put("session_id", message.sessionId)
                message.result?.let { put("result", it) }
            }

            is StreamEvent -> buildJsonObject {
                put("type", "stream_event")
                put("uuid", message.uuid)
                put("session_id", message.sessionId)
                put("event", message.event)
            }
        }
    }
}
