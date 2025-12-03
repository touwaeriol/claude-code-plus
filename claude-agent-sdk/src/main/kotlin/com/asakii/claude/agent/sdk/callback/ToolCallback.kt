package com.asakii.claude.agent.sdk.callback

import kotlinx.serialization.json.JsonElement

/**
 * 工具回调接口 - 用于自定义工具的处理逻辑
 *
 * 当 Claude CLI 调用某个工具时，如果该工具已注册回调，
 * SDK 会调用回调而不是让 CLI 自动执行。
 *
 * 使用场景：
 * - AskUserQuestion: 需要前端交互，等待用户选择
 * - ExitPlanMode: 需要用户确认计划
 * - 其他需要自定义处理的工具
 *
 * 示例：
 * ```kotlin
 * class AskUserQuestionCallback : ToolCallback {
 *     override val toolName = "AskUserQuestion"
 *
 *     override suspend fun execute(toolId: String, input: JsonElement): ToolCallbackResult {
 *         // 调用前端显示问题，等待用户选择
 *         val answers = askFrontend(input)
 *         return ToolCallbackResult(
 *             content = "User answered: $answers",
 *             isError = false
 *         )
 *     }
 * }
 * ```
 */
interface ToolCallback {
    /**
     * 工具名称，必须与 Claude CLI 中的工具名称完全匹配
     * 例如："AskUserQuestion", "ExitPlanMode", "Read", "Write"
     */
    val toolName: String

    /**
     * 执行工具回调
     *
     * @param toolId 工具调用 ID（如 "toolu_xxx"），用于关联 tool_result
     * @param input 工具输入参数（JSON 格式）
     * @return 工具执行结果
     */
    suspend fun execute(toolId: String, input: JsonElement): ToolCallbackResult
}

/**
 * 工具回调执行结果
 *
 * @param content 返回给 Claude 的内容，将作为 tool_result 的 content
 * @param isError 是否为错误结果
 */
data class ToolCallbackResult(
    val content: String,
    val isError: Boolean = false
)
