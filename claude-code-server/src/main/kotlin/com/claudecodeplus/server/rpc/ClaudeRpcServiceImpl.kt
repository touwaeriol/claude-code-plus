package com.claudecodeplus.server.rpc

import com.claudecodeplus.rpc.api.ClaudeRpcService
import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.server.IdeActionBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.logging.Logger

/**
 * RPC 服务实现 - 每个 WebSocket 连接对应一个独立实例
 * 
 * 架构原则: 一个连接 = 一个会话 = 一个 ClaudeCodeSdkClient 实例
 */
class ClaudeRpcServiceImpl(
    private val ideActionBridge: IdeActionBridge
) : ClaudeRpcService {
    private val logger = Logger.getLogger(javaClass.name)
    private val sessionId = UUID.randomUUID().toString()
    private var claudeClient: ClaudeCodeSdkClient? = null
    private val messageHistory = mutableListOf<JsonObject>()
    
    override suspend fun connect(options: JsonObject?): JsonObject {
        logger.info("🔌 [RPC] 连接会话: $sessionId")

        try {
            // 构建 Claude 选项
            val claudeOptions = buildClaudeOptions(options)

            // 创建 Claude 客户端
            claudeClient = ClaudeCodeSdkClient(claudeOptions)
            claudeClient?.connect()

            logger.info("✅ [RPC] Claude 客户端已连接")

            // 注意: Claude CLI 不会在 connect 时自动输出 system/init 消息
            // system/init 消息只在第一次 query() 后才会输出
            logger.info("✅ [RPC] 连接完成,等待第一次 query 触发初始化")

            return buildJsonObject {
                put("sessionId", sessionId)
                put("model", claudeOptions.model)
                put("status", "connected")
            }
        } catch (e: Exception) {
            logger.severe("❌ [RPC] 连接失败: ${e.message}")
            throw e
        }
    }
    
    override fun query(message: String): Flow<JsonElement> {
        val client = claudeClient ?: throw IllegalStateException("Not connected")

        logger.info("📤 [RPC] 发送查询: ${message.take(50)}...")

        // 发送查询并返回流式响应
        return channelFlow {
            // 发送查询
            client.query(message)

            // 监听消息流,过滤掉系统消息,只转发响应消息
            client.getAllMessages().collect { msg ->
                // 过滤掉 SystemMessage,只转发 AssistantMessage 和 ResultMessage
                if (msg !is SystemMessage) {
                    // 将 Message 转换为 JsonElement
                    val jsonMsg = messageToJson(msg)
                    send(jsonMsg)

                    // 保存到历史
                    messageHistory.add(jsonMsg)

                    val msgType = when (msg) {
                        is UserMessage -> "user"
                        is AssistantMessage -> "assistant"
                        is ResultMessage -> "result"
                        is StreamEvent -> "stream_event"
                        is SystemMessage -> "system"
                    }
                    logger.info("📨 [RPC] 转发消息: type=$msgType")
                }
            }
        }
    }
    
    override suspend fun interrupt(): JsonObject {
        logger.info("⏸️ [RPC] 中断操作")
        
        claudeClient?.interrupt()
        
        return buildJsonObject {
            put("status", "interrupted")
        }
    }
    
    override suspend fun disconnect(): JsonObject {
        logger.info("🔌 [RPC] 断开会话: $sessionId")
        
        try {
            claudeClient?.disconnect()
            claudeClient = null
            
            return buildJsonObject {
                put("status", "disconnected")
            }
        } catch (e: Exception) {
            logger.warning("⚠️ [RPC] 断开时出错: ${e.message}")
            throw e
        }
    }
    
    override suspend fun setModel(model: String): JsonObject {
        logger.info("🔧 [RPC] 设置模型: $model")
        
        // 重新连接使用新模型
        disconnect()
        connect(buildJsonObject { put("model", model) })
        
        return buildJsonObject {
            put("model", model)
            put("status", "model_changed")
        }
    }
    
    override suspend fun getHistory(): JsonObject {
        logger.info("📜 [RPC] 获取历史消息: ${messageHistory.size} 条")
        
        return buildJsonObject {
            put("messages", JsonArray(messageHistory))
        }
    }
    
    /**
     * 构建 Claude 选项
     */
    private fun buildClaudeOptions(options: JsonObject?): ClaudeAgentOptions {
        val model = options?.get("model")?.jsonPrimitive?.content 
            ?: "claude-sonnet-4-5-20250929"
        
        val cwd = ideActionBridge.getProjectPath()?.let { java.nio.file.Path.of(it) }
        
        return ClaudeAgentOptions(
            model = model,
            cwd = cwd,
            debugStderr = true
        )
    }
    
    /**
     * 将 Message 转换为 JsonObject
     */
    private fun messageToJson(message: Message): JsonObject {
        return buildJsonObject {
            put("type", message::class.simpleName ?: "Unknown")
            put("timestamp", System.currentTimeMillis())
            // TODO: 添加更详细的消息内容序列化
        }
    }
}

