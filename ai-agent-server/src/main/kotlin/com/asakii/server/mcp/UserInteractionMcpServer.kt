package com.asakii.server.mcp

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.McpSystemPromptContext
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.rpc.proto.AskUserQuestionRequest
import com.asakii.rpc.proto.QuestionItem as ProtoQuestionItem
import com.asakii.rpc.proto.QuestionOption as ProtoQuestionOption
import com.asakii.server.mcp.schema.SchemaValidator
import com.asakii.server.mcp.schema.ValidationResult
import com.asakii.server.rpc.ClientCallerRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.asakii.logging.*

@Serializable
data class UserAnswerItem(
    val question: String,
    val header: String,
    val answer: String
)

@Serializable
enum class PermissionBehavior {
    @kotlinx.serialization.SerialName("allow")
    ALLOW,
    @kotlinx.serialization.SerialName("deny")
    DENY,
    @kotlinx.serialization.SerialName("ask")
    ASK
}

@Serializable
enum class PermissionMode {
    @kotlinx.serialization.SerialName("default")
    DEFAULT,
    @kotlinx.serialization.SerialName("acceptEdits")
    ACCEPT_EDITS,
    @kotlinx.serialization.SerialName("plan")
    PLAN,
    @kotlinx.serialization.SerialName("bypassPermissions")
    BYPASS_PERMISSIONS
}

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

@Serializable
data class PermissionRuleValue(
    val toolName: String,
    val ruleContent: String? = null
)

@Serializable
data class PermissionUpdate(
    val type: PermissionUpdateType,
    val rules: List<PermissionRuleValue>? = null,
    val behavior: PermissionBehavior? = null,
    val mode: PermissionMode? = null,
    val directories: List<String>? = null,
    val destination: PermissionUpdateDestination? = null
)

@Serializable
data class PermissionResponse(
    val approved: Boolean,
    val permissionUpdates: List<PermissionUpdate>? = null,
    val denyReason: String? = null
)

@Serializable
data class AskUserQuestionParams(
    val questions: List<QuestionItem>
)

@Serializable
data class QuestionItem(
    val question: String,
    val header: String? = null,
    val options: List<OptionItem>? = null,
    val multiSelect: Boolean = false
)

@Serializable
data class OptionItem(
    val label: String,
    val description: String = ""
)

@Serializable
data class ClaudeQuestionItem(
    val question: String,
    val header: String? = null,
    val options: List<ClaudeOptionItem>? = null,
    val multiSelect: Boolean = false
)

@Serializable
data class ClaudeOptionItem(
    val label: String,
    val description: String = ""
)

private val mcpLogger = getLogger("UserInteractionMcpServer")

@McpServerConfig(
    name = "user-interaction",
    version = "1.0.0",
    description = "用户交互工具服务器，提供向用户提问等功能"
)
class UserInteractionMcpServer : McpServerBase() {
    private var timeoutMs: Long? = null
    private var instructionsByBackend: Map<String, String>? = null

    override val timeout: Long?
        get() = timeoutMs

    override fun getAllowedTools(): List<String> = listOf("AskUserQuestion")

    override fun getSystemPromptAppendix(): String {
        val provider = McpSystemPromptContext.getProvider()
        val key = when (provider) {
            AiAgentProvider.CODEX -> "codex"
            AiAgentProvider.CLAUDE -> "claude"
            else -> null
        }
        val override = key?.let { instructionsByBackend?.get(it) }?.trim().orEmpty()
        return if (override.isNotBlank()) override else DEFAULT_INSTRUCTIONS
    }

    fun setInstructionsByBackend(instructions: Map<String, String>?) {
        instructionsByBackend = instructions?.mapKeys { it.key.trim().lowercase() }
    }

    companion object {
        val DEFAULT_INSTRUCTIONS = """
When you need clarification from the user, especially when presenting multiple options or choices, use the MCP server `user-interaction` tool `AskUserQuestion` to ask questions.
Tool identifiers may differ across providers. Do not assume a fixed prefix or delimiter; select the tool that matches this server + tool pair.
The user's response will be returned to you through the same tool.
        """.trimIndent()

        val ASK_USER_QUESTION_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            put(
                "description",
                "Ask the user questions and get their choices. Use this tool to interact with users when input or confirmation is needed."
            )
            putJsonObject("properties") {
                putJsonObject("questions") {
                    put("type", "array")
                    put("description", "List of questions")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("question") {
                                put("type", "string")
                                put("description", "Question content")
                            }
                            putJsonObject("header") {
                                put("type", "string")
                                put("description", "Question header/category label")
                            }
                            putJsonObject("options") {
                                put("type", "array")
                                put("description", "List of options")
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("label") {
                                            put("type", "string")
                                            put("description", "Option display text")
                                        }
                                        putJsonObject("description") {
                                            put("type", "string")
                                            put("description", "Option description (optional)")
                                        }
                                    }
                                    putJsonArray("required") { add("label") }
                                }
                            }
                            putJsonObject("multiSelect") {
                                put("type", "boolean")
                                put("description", "Allow multiple selections, default false")
                            }
                        }
                        putJsonArray("required") {
                            add("question")
                            add("header")
                            add("options")
                        }
                    }
                }
            }
            putJsonArray("required") { add("questions") }
        }
    }

    fun setTimeoutMs(timeoutMs: Long?) {
        this.timeoutMs = timeoutMs
    }

    override suspend fun callToolJson(toolName: String, arguments: JsonObject): ToolResult {
        return when (toolName) {
            "AskUserQuestion" -> handleAskUserQuestionJson(arguments)
            else -> super.callToolJson(toolName, arguments)
        }
    }

    private suspend fun handleAskUserQuestionJson(arguments: JsonObject): ToolResult {
        val connectId = currentConnectId()
        val caller = connectId?.let { ClientCallerRegistry.get(it) }
            ?: return ToolResult.error("无法获取 ClientCaller，connectId=$connectId")

        mcpLogger.info { "📩 [AskUserQuestion] 收到工具调用，参数: $arguments" }

        val validationResult = SchemaValidator.validateWithSchema(
            schema = ASK_USER_QUESTION_SCHEMA,
            arguments = arguments,
            customValidators = listOf(
                { args ->
                    val questions = args["questions"]
                    if (questions is JsonPrimitive && questions.isString) {
                        com.asakii.server.mcp.schema.ValidationError(
                            parameter = "questions",
                            message = "Parameter should be an array, not a string",
                            hint = "Pass the array directly without serializing it to a string"
                        )
                    } else null
                }
            )
        )

        if (validationResult is ValidationResult.Invalid) {
            val errorMsg = validationResult.formatMessage()
            mcpLogger.warn { "⚠️ [AskUserQuestion] 参数校验失败:\n$errorMsg" }
            return ToolResult.error(errorMsg)
        }

        return try {
            val normalized = normalizeQuestions(arguments)
            val params = Json.decodeFromJsonElement(AskUserQuestionParams.serializer(), normalized)

            mcpLogger.info { "📤 [AskUserQuestion] 解析后的参数: ${params.questions.size} 个问题" }

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

            val protoResponse = caller.callAskUserQuestion(protoRequest)

            mcpLogger.info { "📥 [AskUserQuestion] 收到前端响应: ${protoResponse.answersCount} 个回答" }

            // 构建回答映射
            val answersMap: Map<String, String> = protoResponse.answersList.associate {
                it.question to it.answer
            }

            // 生成 Markdown 格式的回复
            val content = buildString {
                appendLine("## User Answers")
                appendLine()
                params.questions.forEachIndexed { index, q ->
                    val answer = answersMap[q.question] ?: "(no answer)"
                    val header = q.header ?: "Question ${index + 1}"
                    appendLine("### $header")
                    appendLine("**Q:** ${q.question}")
                    appendLine("**A:** $answer")
                    appendLine()
                }
            }.trim()

            mcpLogger.info { "✅ [AskUserQuestion] 完成，返回:\n$content" }
            ToolResult.success(content)

        } catch (e: kotlinx.serialization.SerializationException) {
            mcpLogger.error { "❌ [AskUserQuestion] 参数格式错误: ${e.message}" }
            ToolResult.error("参数格式错误，请检查 questions 数组结构是否正确")
        } catch (e: Exception) {
            mcpLogger.error { "❌ [AskUserQuestion] 处理失败: ${e.message}" }
            e.printStackTrace()
            ToolResult.error("处理用户问题时发生错误，请稍后重试")
        }
    }

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
                    mcpLogger.warn { "⚠️ [AskUserQuestion] 无法从字符串解析 questions" }
                }
            }
        }
        return arguments
    }

    override suspend fun onInitialize() {
        registerToolWithSchema(
            name = "AskUserQuestion",
            description = "向用户询问问题并获取选择。使用此工具在需要用户输入或确认时与用户交互。",
            inputSchema = ASK_USER_QUESTION_SCHEMA
        ) { arguments ->
            handleAskUserQuestionJson(arguments)
        }

        mcpLogger.info { "✅ [UserInteractionMcpServer] 初始化完成，已注册 AskUserQuestion 工具" }
    }
}
