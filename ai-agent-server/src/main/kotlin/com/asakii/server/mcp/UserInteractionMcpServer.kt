package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.rpc.proto.AskUserQuestionRequest
import com.asakii.rpc.proto.QuestionItem as ProtoQuestionItem
import com.asakii.rpc.proto.QuestionOption as ProtoQuestionOption
import com.asakii.server.rpc.ClientCaller
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import mu.KotlinLogging

/**
 * 用户回答项（前端返回的数组元素）
 */
@Serializable
data class UserAnswerItem(
    val question: String,
    val header: String,
    val answer: String
)

/**
 * 权限行为类型
 */
@Serializable
enum class PermissionBehavior {
    @kotlinx.serialization.SerialName("allow")
    ALLOW,
    @kotlinx.serialization.SerialName("deny")
    DENY,
    @kotlinx.serialization.SerialName("ask")
    ASK
}

/**
 * 权限模式
 */
@Serializable
enum class PermissionMode {
    @kotlinx.serialization.SerialName("default")
    DEFAULT,
    @kotlinx.serialization.SerialName("acceptEdits")
    ACCEPT_EDITS,
    @kotlinx.serialization.SerialName("plan")
    PLAN,
    @kotlinx.serialization.SerialName("bypassPermissions")
    BYPASS_PERMISSIONS,
    @kotlinx.serialization.SerialName("dontAsk")
    DONT_ASK
}

/**
 * 权限更新目标
 */
@Serializable
enum class PermissionUpdateDestination {
    @kotlinx.serialization.SerialName("userSettings")
    USER_SETTINGS,
    @kotlinx.serialization.SerialName("projectSettings")
    PROJECT_SETTINGS,
    @kotlinx.serialization.SerialName("localSettings")
    LOCAL_SETTINGS,
    @kotlinx.serialization.SerialName("session")
    SESSION
}

/**
 * 权限更新类型
 */
@Serializable
enum class PermissionUpdateType {
    @kotlinx.serialization.SerialName("addRules")
    ADD_RULES,
    @kotlinx.serialization.SerialName("replaceRules")
    REPLACE_RULES,
    @kotlinx.serialization.SerialName("removeRules")
    REMOVE_RULES,
    @kotlinx.serialization.SerialName("setMode")
    SET_MODE,
    @kotlinx.serialization.SerialName("addDirectories")
    ADD_DIRECTORIES,
    @kotlinx.serialization.SerialName("removeDirectories")
    REMOVE_DIRECTORIES
}

/**
 * 权限规则值
 */
@Serializable
data class PermissionRuleValue(
    val toolName: String,
    val ruleContent: String? = null
)

/**
 * 权限更新配置
 */
@Serializable
data class PermissionUpdate(
    val type: PermissionUpdateType,
    val rules: List<PermissionRuleValue>? = null,
    val behavior: PermissionBehavior? = null,
    val mode: PermissionMode? = null,
    val directories: List<String>? = null,
    val destination: PermissionUpdateDestination? = null
)

/**
 * 授权响应数据类（前端 RequestPermission 回调的返回格式，与官方 SDK 保持一致）
 */
@Serializable
data class PermissionResponse(
    val approved: Boolean,
    val permissionUpdates: List<PermissionUpdate>? = null,  // 改为数组，与官方 SDK 保持一致
    val denyReason: String? = null
)

/**
 * AskUserQuestion 请求参数
 */
@Serializable
data class AskUserQuestionParams(
    val questions: List<QuestionItem>
)

/**
 * 问题项（兼容 Claude 格式）
 */
@Serializable
data class QuestionItem(
    val question: String,
    val header: String? = null,  // Claude 可能不传
    val options: List<OptionItem>? = null,  // 可能是对象数组
    val multiSelect: Boolean = false
)

/**
 * 选项项
 */
@Serializable
data class OptionItem(
    val label: String,
    val description: String = ""
)

/**
 * Claude 原生格式的问题项
 */
@Serializable
data class ClaudeQuestionItem(
    val question: String,
    val header: String? = null,
    val options: List<ClaudeOptionItem>? = null,
    val multiSelect: Boolean = false
)

/**
 * Claude 原生格式的选项（可能是字符串或对象）
 */
@Serializable
data class ClaudeOptionItem(
    val label: String,
    val description: String = ""
)

/**
 * 用户交互 MCP Server
 *
 * 提供需要用户交互的工具，如 AskUserQuestion。
 * 通过 ClientCaller 与前端通信，获取用户输入。
 */
private val mcpLogger = KotlinLogging.logger {}

@McpServerConfig(
    name = "user_interaction",
    version = "1.0.0",
    description = "用户交互工具服务器，提供向用户提问等功能"
)
class UserInteractionMcpServer : McpServerBase() {
    private var clientCaller: ClientCaller? = null

    /**
     * 提供该 MCP 服务器的系统提示词追加内容
     *
     * 告知 AI 如何正确使用 AskUserQuestion 工具与用户进行交互
     */
    override fun getSystemPromptAppendix(): String = """
        When you need clarification from the user, especially when presenting multiple options or choices, use the `mcp__user_interaction__AskUserQuestion` tool to ask questions. The user's response will be returned to you through this tool.
    """.trimIndent()

    companion object {
        /** AskUserQuestion 工具的 JSON Schema 定义 */
        val ASK_USER_QUESTION_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "questions" to mapOf(
                    "type" to "array",
                    "description" to "问题列表",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "question" to mapOf(
                                "type" to "string",
                                "description" to "问题内容"
                            ),
                            "header" to mapOf(
                                "type" to "string",
                                "description" to "问题标题/分类标签"
                            ),
                            "options" to mapOf(
                                "type" to "array",
                                "description" to "选项列表",
                                "items" to mapOf(
                                    "type" to "object",
                                    "properties" to mapOf(
                                        "label" to mapOf(
                                            "type" to "string",
                                            "description" to "选项显示文本"
                                        ),
                                        "description" to mapOf(
                                            "type" to "string",
                                            "description" to "选项描述（可选）"
                                        )
                                    ),
                                    "required" to listOf("label")
                                )
                            ),
                            "multiSelect" to mapOf(
                                "type" to "boolean",
                                "description" to "是否允许多选，默认 false"
                            )
                        ),
                        "required" to listOf("question", "header", "options")
                    )
                )
            ),
            "required" to listOf("questions")
        )

    }

    /**
     * 设置客户端调用器
     */
    fun setClientCaller(caller: ClientCaller) {
        this.clientCaller = caller
        mcpLogger.info { "✅ [UserInteractionMcpServer] ClientCaller 已设置" }
    }

    /**
     * 重写 callToolJson，直接从 JsonObject 反序列化为强类型
     */
    override suspend fun callToolJson(toolName: String, arguments: JsonObject): ToolResult {
        return when (toolName) {
            "AskUserQuestion" -> handleAskUserQuestionJson(arguments)
            else -> super.callToolJson(toolName, arguments)
        }
    }

    /**
     * 处理 AskUserQuestion（直接从 JsonObject 反序列化）
     *
     * 使用 Protobuf 序列化与前端通信
     */
    private suspend fun handleAskUserQuestionJson(arguments: JsonObject): ToolResult {
        val caller = clientCaller
            ?: return ToolResult.error("ClientCaller 未设置，无法与前端通信")

        mcpLogger.info { "📩 [AskUserQuestion] 收到工具调用，参数: $arguments" }

        return try {
            // 直接从 JsonObject 反序列化为强类型
            val normalized = normalizeQuestions(arguments)
            val params: AskUserQuestionParams = Json.decodeFromJsonElement(normalized)

            mcpLogger.info { "📤 [AskUserQuestion] 解析后的参数: ${params.questions.size} 个问题" }

            // 构建 Protobuf 请求
            val protoRequest = AskUserQuestionRequest.newBuilder().apply {
                params.questions.forEach { q ->
                    addQuestions(ProtoQuestionItem.newBuilder().apply {
                        question = q.question
                        q.header?.let { header = it }
                        q.options?.forEach { opt ->
                            addOptions(ProtoQuestionOption.newBuilder().apply {
                                label = opt.label
                                if (opt.description.isNotEmpty()) {
                                    description = opt.description
                                }
                            }.build())
                        }
                        multiSelect = q.multiSelect
                    }.build())
                }
            }.build()

            // 使用 Protobuf 类型化调用
            val protoResponse = caller.callAskUserQuestion(protoRequest)

            mcpLogger.info { "📥 [AskUserQuestion] 收到前端响应: ${protoResponse.answersCount} 个回答" }

            // 转换为 Map<问题, 回答>
            val answersMap: Map<String, String> = protoResponse.answersList.associate {
                it.question to it.answer
            }
            val content = Json.encodeToString(answersMap)

            mcpLogger.info { "✅ [AskUserQuestion] 完成，返回: $content" }
            ToolResult.success(content)

        } catch (e: Exception) {
            mcpLogger.error { "❌ [AskUserQuestion] 处理失败: ${e.message}" }
            e.printStackTrace()
            ToolResult.error("处理用户问题失败: ${e.message}")
        }
    }


    /**
     * 对字符串化的 questions 进行修正，确保为 JsonArray
     */
    private fun normalizeQuestions(arguments: JsonObject): JsonObject {
        val rawQuestions = arguments["questions"]
        if (rawQuestions is JsonPrimitive && rawQuestions.isString) {
            val content = rawQuestions.content
            if (content.startsWith("[") || content.startsWith("{")) {
                try {
                    val parsed = Json.parseToJsonElement(content)
                    if (parsed is JsonArray) {
                        return buildJsonObject {
                            arguments.forEach { (k, v) ->
                                if (k == "questions") put(k, parsed) else put(k, v)
                            }
                        }
                    }
                } catch (e: Exception) {
                    mcpLogger.warn { "⚠️ [AskUserQuestion] 无法从字符串解析 questions: " }
                }
            }
        }
        return arguments
    }

    override suspend fun onInitialize() {
        // 注册 AskUserQuestion 工具
        registerToolWithSchema(
            name = "AskUserQuestion",
            description = "向用户询问问题并获取选择。使用此工具在需要用户输入或确认时与用户交互。",
            inputSchema = ASK_USER_QUESTION_SCHEMA
        ) { arguments ->
            handleAskUserQuestion(arguments)
        }

        mcpLogger.info { "✅ [UserInteractionMcpServer] 初始化完成，已注册 AskUserQuestion 工具" }
    }

    /**
     * 处理 AskUserQuestion 工具调用
     *
     * 使用 Protobuf 序列化与前端通信
     */
    private suspend fun handleAskUserQuestion(arguments: Map<String, Any>): Any {
        val caller = clientCaller
            ?: return ToolResult.error("ClientCaller 未设置，无法与前端通信")

        mcpLogger.info { "📩 [AskUserQuestion] 收到工具调用，参数: $arguments" }

        // 调试：打印参数类型
        arguments.forEach { (key, value) ->
            mcpLogger.debug { "📦 参数 '$key' 类型: ${value?.let { it::class.qualifiedName } ?: "null"}, 值: $value" }
        }

        try {
            // 将 Map<String, Any> 转换为 JsonElement，再解析为类型化对象
            val paramsJson = anyToJsonElement(arguments)
            mcpLogger.debug { "📦 转换后的 JSON: $paramsJson" }
            val paramsJsonNormalized = normalizeQuestions(paramsJson.jsonObject)
            val params: AskUserQuestionParams = Json.decodeFromJsonElement(paramsJsonNormalized)

            mcpLogger.info { "📤 [AskUserQuestion] 解析后的参数: ${params.questions.size} 个问题" }

            // 构建 Protobuf 请求
            val protoRequest = AskUserQuestionRequest.newBuilder().apply {
                params.questions.forEach { q ->
                    addQuestions(ProtoQuestionItem.newBuilder().apply {
                        question = q.question
                        q.header?.let { header = it }
                        q.options?.forEach { opt ->
                            addOptions(ProtoQuestionOption.newBuilder().apply {
                                label = opt.label
                                if (opt.description.isNotEmpty()) {
                                    description = opt.description
                                }
                            }.build())
                        }
                        multiSelect = q.multiSelect
                    }.build())
                }
            }.build()

            // 使用 Protobuf 类型化调用
            val protoResponse = caller.callAskUserQuestion(protoRequest)

            mcpLogger.info { "📥 [AskUserQuestion] 收到前端响应: ${protoResponse.answersCount} 个回答" }

            // 转换为 Map<问题, 回答>
            val answersMap: Map<String, String> = protoResponse.answersList.associate {
                it.question to it.answer
            }

            // 序列化返回给 Claude
            val content = Json.encodeToString(answersMap)

            mcpLogger.info { "✅ [AskUserQuestion] 完成，返回: $content" }
            return content

        } catch (e: Exception) {
            mcpLogger.error { "❌ [AskUserQuestion] 处理失败: ${e.message}" }
            e.printStackTrace()
            return ToolResult.error("处理用户问题失败: ${e.message}")
        }
    }

    /**
     * 将 Any 类型递归转换为 JsonElement
     * 用于将 MCP 框架传入的 Map<String, Any> 转换为可序列化的 JsonElement
     */
    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> buildJsonObject {
                value.forEach { (k, v) ->
                    put(k.toString(), anyToJsonElement(v))
                }
            }
            is List<*> -> buildJsonArray {
                value.forEach { add(anyToJsonElement(it)) }
            }
            is Array<*> -> buildJsonArray {
                value.forEach { add(anyToJsonElement(it)) }
            }
            is Iterable<*> -> buildJsonArray {
                value.forEach { add(anyToJsonElement(it)) }
            }
            is Sequence<*> -> buildJsonArray {
                value.forEach { add(anyToJsonElement(it)) }
            }
            else -> {
                // 尝试处理其他可迭代类型或 JSON 字符串
                val str = value.toString()
                // 如果看起来像 JSON 数组或对象，尝试解析
                if (str.startsWith("[") || str.startsWith("{")) {
                    try {
                        Json.parseToJsonElement(str)
                    } catch (e: Exception) {
                        mcpLogger.warn { "⚠️ 无法解析为 JSON: $str, 类型: ${value::class.qualifiedName}" }
                        JsonPrimitive(str)
                    }
                } else {
                    mcpLogger.debug { "📦 未知类型转为字符串: ${value::class.qualifiedName}" }
                    JsonPrimitive(str)
                }
            }
        }
    }
}
