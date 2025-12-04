package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ToolResult
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.server.rpc.ClientCaller
import com.asakii.server.rpc.callTyped
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.logging.Logger

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
@McpServerConfig(
    name = "user_interaction",
    version = "1.0.0",
    description = "用户交互工具服务器，提供向用户提问等功能"
)
class UserInteractionMcpServer : McpServerBase() {

    private val logger = Logger.getLogger(javaClass.name)
    private var clientCaller: ClientCaller? = null

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
        logger.info("✅ [UserInteractionMcpServer] ClientCaller 已设置")
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

        logger.info("✅ [UserInteractionMcpServer] 初始化完成，已注册 AskUserQuestion 工具")
    }

    /**
     * 处理 AskUserQuestion 工具调用
     */
    private suspend fun handleAskUserQuestion(arguments: Map<String, Any>): Any {
        val caller = clientCaller
            ?: return ToolResult.error("ClientCaller 未设置，无法与前端通信")

        logger.info("📩 [AskUserQuestion] 收到工具调用，参数: $arguments")

        try {
            // 获取 questions 参数
            val questions = arguments["questions"]
                ?: return ToolResult.error("缺少 questions 参数")

            logger.info("📤 [AskUserQuestion] 调用前端 AskUserQuestion 方法")

            // 调用前端方法，获取类型化响应
            val answerItems: List<UserAnswerItem> = caller.callTyped(
                method = "AskUserQuestion",
                params = mapOf("questions" to questions)
            )

            logger.info("📥 [AskUserQuestion] 收到前端响应: $answerItems")

            // 转换为 Map<问题, 回答>
            val answersMap: Map<String, String> = answerItems.associate { it.question to it.answer }

            // 序列化返回给 Claude
            val content = Json.encodeToString(answersMap)

            logger.info("✅ [AskUserQuestion] 完成，返回: $content")
            return content

        } catch (e: Exception) {
            logger.severe("❌ [AskUserQuestion] 处理失败: ${e.message}")
            e.printStackTrace()
            return ToolResult.error("处理用户问题失败: ${e.message}")
        }
    }
}
