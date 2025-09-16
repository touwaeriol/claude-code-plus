package com.claudecodeplus.sdk.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Supported hook event types.
 */
enum class HookEvent {
    PRE_TOOL_USE,
    POST_TOOL_USE,
    USER_PROMPT_SUBMIT,
    STOP,
    SUBAGENT_STOP,
    PRE_COMPACT
}

/**
 * Hook JSON output format.
 */
@Serializable
data class HookJSONOutput(
    val decision: String? = null, // "block" to block the action
    val systemMessage: String? = null, // System message not visible to Claude
    val hookSpecificOutput: JsonElement? = null // Hook-specific output
)

/**
 * Context information for hook callbacks.
 */
data class HookContext(
    val signal: Any? = null // Future: abort signal support
)

/**
 * Hook callback function type.
 */
typealias HookCallback = suspend (input: Map<String, Any>, toolUseId: String?, context: HookContext) -> HookJSONOutput

/**
 * Hook matcher configuration.
 */
data class HookMatcher(
    val matcher: String? = null, // Matcher pattern (e.g., "Bash" or "Write|Edit")
    val hooks: List<HookCallback> = emptyList() // List of callback functions
)