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

/**
 * Hook execution result.
 */
@Serializable
sealed class HookResult {
    @Serializable
    data class Allow(
        val modifiedInput: JsonElement? = null,
        val systemMessage: String? = null
    ) : HookResult()

    @Serializable
    data class Block(
        val reason: String,
        val systemMessage: String? = null
    ) : HookResult()

    @Serializable
    data class Modify(
        val modifiedInput: JsonElement,
        val systemMessage: String? = null
    ) : HookResult()
}

/**
 * Hook registry for managing hook callbacks.
 */
class HookRegistry {
    private val hooks = mutableMapOf<HookEvent, MutableList<HookMatcher>>()

    fun register(event: HookEvent, matcher: HookMatcher) {
        hooks.getOrPut(event) { mutableListOf() }.add(matcher)
    }

    fun unregister(event: HookEvent, matcher: HookMatcher) {
        hooks[event]?.remove(matcher)
    }

    fun getHooks(event: HookEvent): List<HookMatcher> {
        return hooks[event] ?: emptyList()
    }

    fun clear() {
        hooks.clear()
    }
}

/**
 * Hook execution environment providing context and utilities.
 */
data class HookExecutionEnvironment(
    val sessionId: String?,
    val toolName: String?,
    val input: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)