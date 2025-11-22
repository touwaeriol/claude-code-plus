package com.claudecodeplus.sdk.types

import kotlinx.serialization.Serializable

/**
 * Permission modes for tool usage.
 */
enum class PermissionMode {
    DEFAULT,
    ACCEPT_EDITS,
    PLAN,
    BYPASS_PERMISSIONS,
    DONT_ASK
}

/**
 * Permission behavior types.
 */
enum class PermissionBehavior {
    ALLOW,
    DENY,
    ASK
}

/**
 * Permission update destination.
 */
enum class PermissionUpdateDestination {
    USER_SETTINGS,
    PROJECT_SETTINGS,
    LOCAL_SETTINGS,
    SESSION
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
enum class PermissionUpdateType {
    ADD_RULES,
    REPLACE_RULES,
    REMOVE_RULES,
    SET_MODE,
    ADD_DIRECTORIES,
    REMOVE_DIRECTORIES
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
 * Allow permission result.
 */
@Serializable
data class PermissionResultAllow(
    val behavior: String = "allow",
    val updatedInput: Map<String, @kotlinx.serialization.Contextual Any>? = null,
    val updatedPermissions: List<PermissionUpdate>? = null
)

/**
 * Deny permission result.
 */
@Serializable
data class PermissionResultDeny(
    val behavior: String = "deny",
    val message: String = "",
    val interrupt: Boolean = false
)

/**
 * Union type for permission results.
 */
sealed interface PermissionResult {
    val behavior: String
}

/**
 * Tool permission callback function type.
 */
typealias CanUseTool = suspend (toolName: String, input: Map<String, Any>, context: ToolPermissionContext) -> PermissionResult