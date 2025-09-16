package com.claudecodeplus.sdk.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

/**
 * MCP stdio server configuration.
 */
@Serializable
data class McpStdioServerConfig(
    override val type: String = "stdio",
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
) : McpServerConfig

/**
 * MCP SSE server configuration.
 */
@Serializable
data class McpSSEServerConfig(
    override val type: String = "sse",
    val url: String,
    val headers: Map<String, String> = emptyMap()
) : McpServerConfig

/**
 * MCP HTTP server configuration.
 */
@Serializable
data class McpHttpServerConfig(
    override val type: String = "http",
    val url: String,
    val headers: Map<String, String> = emptyMap()
) : McpServerConfig

/**
 * Union type for MCP server configurations.
 */
sealed interface McpServerConfig {
    val type: String
}

/**
 * Claude Code SDK options.
 */
data class ClaudeCodeOptions(
    // Tool configuration
    val allowedTools: List<String> = emptyList(),
    val disallowedTools: List<String> = emptyList(),
    
    // System prompts
    val systemPrompt: String? = null,
    val appendSystemPrompt: String? = null,
    
    // MCP servers (can be McpServerConfig or McpServer instances)
    val mcpServers: Map<String, Any> = emptyMap(),
    
    // Permission settings
    val permissionMode: PermissionMode? = null,
    val permissionPromptToolName: String? = null,
    val canUseTool: CanUseTool? = null,
    
    // Session control
    val continueConversation: Boolean = false,
    val resume: String? = null,
    val maxTurns: Int? = null,
    
    // Model configuration
    val model: String? = null,
    
    // Environment
    val cwd: Path? = null,
    val settings: String? = null,
    val addDirs: List<Path> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    
    // Hook configurations
    val hooks: Map<HookEvent, List<HookMatcher>>? = null,
    
    // Extra CLI arguments
    val extraArgs: Map<String, String?> = emptyMap(),
    
    // Debug settings
    val debugStderr: Any? = null // File-like object for debug output
)

/**
 * Control request types for SDK protocol.
 */
@Serializable
sealed interface ControlRequest {
    val subtype: String
}

@Serializable
data class InterruptRequest(
    override val subtype: String = "interrupt"
) : ControlRequest

@Serializable
data class PermissionRequest(
    override val subtype: String = "can_use_tool",
    val toolName: String,
    val input: JsonElement,
    val permissionSuggestions: List<JsonElement>? = null,
    val blockedPath: String? = null
) : ControlRequest

@Serializable
data class InitializeRequest(
    override val subtype: String = "initialize",
    val hooks: Map<String, JsonElement>? = null
) : ControlRequest

@Serializable
data class SetPermissionModeRequest(
    override val subtype: String = "set_permission_mode",
    val mode: String
) : ControlRequest

@Serializable
data class HookCallbackRequest(
    override val subtype: String = "hook_callback",
    val callbackId: String,
    val input: JsonElement,
    val toolUseId: String? = null
) : ControlRequest

@Serializable
data class McpMessageRequest(
    override val subtype: String = "mcp_message",
    val serverName: String,
    val message: JsonElement
) : ControlRequest

/**
 * Control response types.
 */
@Serializable
data class ControlResponse(
    val subtype: String,
    val requestId: String,
    val response: JsonElement? = null,
    val error: String? = null
)