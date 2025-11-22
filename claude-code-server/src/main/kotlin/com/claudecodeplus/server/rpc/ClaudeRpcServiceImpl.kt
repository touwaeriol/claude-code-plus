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

    // JSON 序列化实例
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun connect(options: JsonObject?): JsonObject {
        logger.info("🔌 [RPC] 连接会话: $sessionId")

        // 打印 connect 参数
        logger.info("📋 [RPC] connect 接收到的 options (JsonObject): ${options?.toString() ?: "null"}")
        if (options != null) {
            logger.info("📋 [RPC] connect options 详情:")
            options.entries.forEach { (key, value) ->
                logger.info("  - $key: ${value.toString().take(200)}")
            }
        }

        try {
            // 构建 Claude 选项
            val claudeOptions = buildClaudeOptions(options)
            
            // 打印构建后的 Claude 选项
            logger.info("📋 [RPC] buildClaudeOptions 结果:")
            logger.info("  - model: ${claudeOptions.model}")
            logger.info("  - permissionMode: ${claudeOptions.permissionMode}")
            logger.info("  - maxTurns: ${claudeOptions.maxTurns}")
            logger.info("  - systemPrompt: ${formatSystemPrompt(claudeOptions.systemPrompt)}")
            logger.info("  - dangerouslySkipPermissions: ${claudeOptions.dangerouslySkipPermissions}")
            logger.info("  - allowDangerouslySkipPermissions: ${claudeOptions.allowDangerouslySkipPermissions}")
            logger.info("  - allowedTools: ${claudeOptions.allowedTools}")

            // 创建 Claude 客户端
            claudeClient = ClaudeCodeSdkClient(claudeOptions)
            
            // 打印 SDK connect 调用前的参数
            logger.info("🚀 [RPC] 调用 SDK connect()，参数: prompt=null (SDK connect 只接收 prompt 参数)")
            logger.info("🚀 [RPC] SDK 客户端配置已在创建时传入: model=${claudeOptions.model}, permissionMode=${claudeOptions.permissionMode}")
            
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
            // 使用 receiveResponse() 而不是 getAllMessages()，因为前者会在 ResultMessage 后自动结束
            client.receiveResponse().collect { msg ->
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

    override fun queryWithContent(content: JsonArray): Flow<JsonElement> {
        val client = claudeClient ?: throw IllegalStateException("Not connected")

        logger.info("📤 [RPC] 发送带内容的查询: ${content.size} 个内容块")

        return channelFlow {
            // 将 JsonArray 转换为 List<UserInputContent>
            val contentList = content.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val type = obj["type"]?.toString()?.trim('"')

                when (type) {
                    "text" -> {
                        val text = obj["text"]?.toString()?.trim('"') ?: ""
                        TextInput(text)
                    }
                    "image" -> {
                        val data = obj["data"]?.toString()?.trim('"') ?: ""
                        val mimeType = obj["mimeType"]?.toString()?.trim('"') ?: "image/png"
                        ImageInput(data, mimeType)
                    }
                    else -> null
                }
            }

            // 发送查询
            client.query(contentList)

            // 监听消息流
            // 使用 receiveResponse() 而不是 getAllMessages()，因为前者会在 ResultMessage 后自动结束
            client.receiveResponse().collect { msg ->
                if (msg !is SystemMessage) {
                    val jsonMsg = messageToJson(msg)
                    send(jsonMsg)
                    messageHistory.add(jsonMsg)

                    val msgType = when (msg) {
                        is UserMessage -> "user"
                        is AssistantMessage -> "assistant"
                        is ResultMessage -> "result"
                        is StreamEvent -> "stream_event"
                        is SystemMessage -> "system"
                    }
                    logger.info("📨 [RPC] queryWithContent 转发消息: type=$msgType")
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
     *
     * 将前端通过 WebSocket 传入的 options(JsonObject)
     * 映射为 SDK ClaudeAgentOptions，包括权限相关参数
     */
    private fun buildClaudeOptions(options: JsonObject?): ClaudeAgentOptions {
        // 从前端配置中提取参数，不添加任何默认值（cwd 除外，由服务端指定）
        val model = options?.get("model")?.jsonPrimitive?.contentOrNull

        val maxTurns = options?.get("maxTurns")?.jsonPrimitive?.intOrNull

        val dangerouslySkipPermissions = options?.get("dangerouslySkipPermissions")?.jsonPrimitive?.booleanOrNull

        val allowDangerouslySkipPermissions = options?.get("allowDangerouslySkipPermissions")?.jsonPrimitive?.booleanOrNull

        val permissionModeStr = options?.get("permissionMode")?.jsonPrimitive?.contentOrNull
        val permissionMode = when (permissionModeStr) {
            "bypassPermissions" -> PermissionMode.BYPASS_PERMISSIONS
            "acceptEdits" -> PermissionMode.ACCEPT_EDITS
            "plan" -> PermissionMode.PLAN
            "default" -> PermissionMode.DEFAULT
            "dontAsk" -> PermissionMode.DONT_ASK
            else -> null
        }

        val continueConversation = options?.get("continueConversation")?.jsonPrimitive?.booleanOrNull
        val resumeSessionId = options?.get("resume")?.jsonPrimitive?.contentOrNull

        // 提取流式输出配置
        val includePartialMessages = options?.get("includePartialMessages")?.jsonPrimitive?.booleanOrNull

        // 提取 print, verbose, outputFormat 参数
        val print = options?.get("print")?.jsonPrimitive?.booleanOrNull
        val verbose = options?.get("verbose")?.jsonPrimitive?.booleanOrNull
        val outputFormat = options?.get("outputFormat")?.jsonPrimitive?.contentOrNull

        // 提取系统提示词
        val systemPromptStr = options?.get("systemPrompt")?.jsonPrimitive?.contentOrNull
        val systemPrompt: Any? = if (!systemPromptStr.isNullOrBlank()) {
            systemPromptStr
        } else {
            null
        }

        // 构建 extraArgs（用于 outputFormat）
        val extraArgs = mutableMapOf<String, String?>()
        outputFormat?.let {
            extraArgs["output-format"] = it
        }

        logger.info(
            "🔧 构建 Claude 配置: " +
                "model=$model, " +
                "maxTurns=$maxTurns, " +
                "permissionMode=$permissionModeStr, " +
                "dangerouslySkipPermissions=$dangerouslySkipPermissions, " +
                "allowDangerouslySkipPermissions=$allowDangerouslySkipPermissions, " +
                "includePartialMessages=$includePartialMessages, " +
                "print=$print, " +
                "verbose=$verbose, " +
                "outputFormat=$outputFormat, " +
                "systemPrompt=" + if (systemPrompt != null) "自定义" else "null"
        )

        // cwd 由服务端指定（从项目路径获取）
        val cwd = ideActionBridge.getProjectPath()?.let { java.nio.file.Path.of(it) }

        return ClaudeAgentOptions(
            model = model,
            cwd = cwd,  // 服务端指定
            debugStderr = true,  // 调试用，保留
            maxTurns = maxTurns,
            permissionMode = permissionMode,
            dangerouslySkipPermissions = dangerouslySkipPermissions,
            allowDangerouslySkipPermissions = allowDangerouslySkipPermissions,
            systemPrompt = systemPrompt,
            continueConversation = continueConversation ?: false,
            resume = resumeSessionId,
            includePartialMessages = includePartialMessages ?: false,
            print = print ?: false,
            verbose = verbose ?: false,
            extraArgs = extraArgs
        )
    }

    /**
     * 格式化 systemPrompt 用于日志输出
     */
    private fun formatSystemPrompt(systemPrompt: Any?): String {
        return when (systemPrompt) {
            is String -> {
                val truncated = if (systemPrompt.length > 100) {
                    systemPrompt.take(100) + "..."
                } else {
                    systemPrompt
                }
                "\"$truncated\""
            }
            null -> "null"
            else -> systemPrompt.toString().take(100)
        }
    }

    /**
     * 将 Message 转换为 JsonObject
     */
    private fun messageToJson(message: Message): JsonObject {
        return buildJsonObject {
            put("timestamp", System.currentTimeMillis())

            when (message) {
                is UserMessage -> {
                    put("type", "user")
                    put("content", message.content)
                    message.parentToolUseId?.let { put("parent_tool_use_id", it) }
                    put("session_id", message.sessionId)
                }

                is AssistantMessage -> {
                    put("type", "assistant")
                    // 序列化完整的 content 列表(包含文本和工具调用)
                    // 将 SpecificToolUse 转换为原生的 ToolUseBlock 格式
                    put("content", buildJsonArray {
                        message.content.forEach { block ->
                            when (block) {
                                is SpecificToolUse -> {
                                    // 手动构建 tool_use 格式
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", block.id)
                                        put("name", block.name)
                                        put("input", block.input)
                                    })
                                }
                                else -> {
                                    // 其他类型直接序列化
                                    add(json.encodeToJsonElement(block))
                                }
                            }
                        }
                    })
                    put("model", message.model)
                    message.tokenUsage?.let {
                        put("token_usage", json.encodeToJsonElement(it))
                    }
                }

                is SystemMessage -> {
                    put("type", "system")
                    put("subtype", message.subtype)
                    put("data", message.data)
                }

                is ResultMessage -> {
                    put("type", "result")
                    put("subtype", message.subtype)
                    put("duration_ms", message.durationMs)
                    put("duration_api_ms", message.durationApiMs)
                    put("is_error", message.isError)
                    put("num_turns", message.numTurns)
                    put("session_id", message.sessionId)
                    message.totalCostUsd?.let { put("total_cost_usd", it) }
                    message.usage?.let { put("usage", it) }
                    message.result?.let { put("result", it) }
                }

                is StreamEvent -> {
                    put("type", "stream_event")
                    put("uuid", message.uuid)
                    put("session_id", message.sessionId)
                    put("event", message.event)
                    message.parentToolUseId?.let { put("parent_tool_use_id", it) }
                }
            }
        }
    }
}

