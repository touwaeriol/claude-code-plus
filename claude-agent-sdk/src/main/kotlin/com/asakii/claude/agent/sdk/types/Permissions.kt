package com.asakii.claude.agent.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Permission modes for tool usage.
 */
@Serializable
enum class PermissionMode {
    @SerialName("default") DEFAULT,
    @SerialName("acceptEdits") ACCEPT_EDITS,
    @SerialName("plan") PLAN,
    @SerialName("bypassPermissions") BYPASS_PERMISSIONS
}

/**
 * Permission behavior types.
 */
@Serializable
enum class PermissionBehavior(val value: String) {
    @SerialName("allow") ALLOW("allow"),
    @SerialName("deny") DENY("deny"),
    @SerialName("ask") ASK("ask")
}

/**
 * Permission update destination.
 */
@Serializable
enum class PermissionUpdateDestination {
    @SerialName("userSettings") USER_SETTINGS,
    @SerialName("projectSettings") PROJECT_SETTINGS,
    @SerialName("localSettings") LOCAL_SETTINGS,
    @SerialName("session") SESSION
}

/**
 * Permission rule value.
 */
@Serializable
data class PermissionRuleValue(
    val toolName: String,
    val ruleContent: String? = null
)

/**
 * Permission update configuration.
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
 * Permission update types.
 */
@Serializable
enum class PermissionUpdateType {
    @SerialName("addRules") ADD_RULES,
    @SerialName("replaceRules") REPLACE_RULES,
    @SerialName("removeRules") REMOVE_RULES,
    @SerialName("setMode") SET_MODE,
    @SerialName("addDirectories") ADD_DIRECTORIES,
    @SerialName("removeDirectories") REMOVE_DIRECTORIES
}

/**
 * Context information for tool permission callbacks.
 */
data class ToolPermissionContext(
    @kotlinx.serialization.Contextual
    val signal: Any? = null, // Future: abort signal support
    val suggestions: List<PermissionUpdate> = emptyList() // Permission suggestions from CLI
)

/**
 * Union type for permission results.
 */
sealed interface PermissionResult {
    val behavior: PermissionBehavior
}

/**
 * Allow permission result.
 */
@Serializable
data class PermissionResultAllow(
    override val behavior: PermissionBehavior = PermissionBehavior.ALLOW,
    val updatedInput: Map<String, JsonElement>? = null,
    val updatedPermissions: List<PermissionUpdate>? = null
) : PermissionResult

/**
 * Deny permission result.
 */
@Serializable
data class PermissionResultDeny(
    override val behavior: PermissionBehavior = PermissionBehavior.DENY,
    val message: String = "",
    val interrupt: Boolean = false
) : PermissionResult

/**
 * Tool permission callback function type.
 * @param toolName 工具名称
 * @param input 工具输入参数（JSON 对象）
 * @param toolUseId 工具调用 ID（用于精确关联 UI）
 * @param context 权限上下文
 */
typealias CanUseTool = suspend (toolName: String, input: Map<String, JsonElement>, toolUseId: String?, context: ToolPermissionContext) -> PermissionResult