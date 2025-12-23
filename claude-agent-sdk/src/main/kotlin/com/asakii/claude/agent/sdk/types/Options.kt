package com.asakii.claude.agent.sdk.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

/**
 * System prompt preset configuration.
 * Allows using pre-defined system prompts like "claude_code".
 */
@Serializable
data class SystemPromptPreset(
    val type: String = "preset",
    val preset: String = "claude_code",
    val append: String? = null
)

/**
 * Agent definition for programmatic subagents.
 * Agents can be defined inline in code using this structure.
 */
@Serializable
data class AgentDefinition(
    val description: String,
    val prompt: String,
    val tools: List<String>? = null,
    val model: String? = null // "sonnet" | "opus" | "haiku" | "inherit"
)

/**
 * Setting sources to load from filesystem.
 * Controls which configuration files are read.
 */
enum class SettingSource {
    @SerialName("user")
    USER,
    @SerialName("project")
    PROJECT,
    @SerialName("local")
    LOCAL
}

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
 * Used for connecting to MCP servers exposed via HTTP (including SSE-based servers).
 * Claude CLI uses "http" type to connect to MCP servers exposed via mcp{} Ktor plugin.
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
 * Claude Agent SDK options (formerly ClaudeAgentOptions).
 * Based on Python SDK v0.1.0 ClaudeAgentOptions.
 *
 * Breaking changes from previous versions:
 * - Renamed from ClaudeAgentOptions to ClaudeAgentOptions
 * - systemPrompt now supports String or SystemPromptPreset
 * - appendSystemPrompt merged into systemPrompt
 * - No default system prompt or settings loaded (explicit configuration required)
 */
data class ClaudeAgentOptions(
    // Tool configuration
    val allowedTools: List<String> = emptyList(),
    val disallowedTools: List<String> = emptyList(),

    // System prompt - unified field supporting string or preset
    // Use SystemPromptPreset(preset = "claude_code") for default Claude Code behavior
    val systemPrompt: Any? = null, // String | SystemPromptPreset | null

    // Append system prompt file - 追加系统提示词（用于 MCP 场景）
    // 使用 --append-system-prompt-file 参数，不会替换默认提示词
    val appendSystemPromptFile: String? = null,

    // MCP servers (can be McpServerConfig or McpServer instances)
    val mcpServers: Map<String, Any> = emptyMap(),

    // Permission settings
    val permissionMode: PermissionMode? = null,
    val dangerouslySkipPermissions: Boolean? = null,
    val allowDangerouslySkipPermissions: Boolean? = null,
    val permissionPromptToolName: String? = null,
    val canUseTool: CanUseTool? = null,

    // Session control
    val continueConversation: Boolean = false,
    val resume: String? = null,
    val forkSession: Boolean = false, // NEW: Fork session when resuming
    val replayUserMessages: Boolean = false, // NEW: Replay user messages when resuming session
    val noSessionPersistence: Boolean = false, // Disable session persistence (--no-session-persistence)
    val maxTurns: Int? = null,

    // Streaming configuration
    val includePartialMessages: Boolean = false, // NEW: Enable partial message streaming

    // Agent definitions - NEW: Programmatic subagents
    val agents: Map<String, AgentDefinition>? = null,

    // Setting sources - NEW: Control which settings files to load
    val settingSources: List<SettingSource>? = null,

    // Model configuration
    val model: String? = null,

    // Environment
    val cwd: Path? = null,
    val settings: String? = null,
    val addDirs: List<Path> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val user: String? = null,

    // CLI path - NEW: Specify custom Claude CLI binary path
    val cliPath: Path? = null,

    // Node.js path - Specify custom Node.js executable path
    // If not set, uses "node" from system PATH
    val nodePath: String? = null,

    // Hook configurations
    val hooks: Map<HookEvent, List<HookMatcher>>? = null,

    // Extra CLI arguments
    val extraArgs: Map<String, String?> = emptyMap(),
    val maxBufferSize: Int? = null, // Max bytes when buffering CLI stdout

    // Debug settings
    @Deprecated("Use stderr callback instead", ReplaceWith("stderr"))
    val debugStderr: Any? = null, // Deprecated: Use stderr callback
    val stderr: ((String) -> Unit)? = null, // NEW: Callback for stderr output

    // Advanced options
    val timeout: Long? = null, // Timeout in milliseconds
    val verbose: Boolean = false,
    val print: Boolean = false,
    val compact: Boolean = false,
    val maxTokens: Int? = null,
    val maxThinkingTokens: Int = 8000,
    val temperature: Double? = null,
    val topP: Double? = null,
    val stopSequences: List<String> = emptyList(),

    // Legacy streaming (consider using includePartialMessages instead)
    val stream: Boolean = false,
    val streamingCallback: ((String) -> Unit)? = null,

    // Chrome integration
    // When true, passes --chrome to CLI; when false, passes --no-chrome
    // null means use CLI default (respects user config)
    val chromeEnabled: Boolean? = null
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
data class RunInBackgroundRequest(
    override val subtype: String = "run_in_background"
) : ControlRequest

@Serializable
data class PermissionRequest(
    override val subtype: String = "can_use_tool",
    val toolName: String,
    val input: JsonElement,
    val permissionSuggestions: List<JsonElement>? = null,
    val blockedPath: String? = null,
    val toolUseId: String? = null,
    val agentId: String? = null
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
data class SetModelRequest(
    override val subtype: String = "set_model",
    val model: String?
) : ControlRequest

@Serializable
data class SetMaxThinkingTokensRequest(
    override val subtype: String = "set_max_thinking_tokens",
    @SerialName("max_thinking_tokens")
    val maxThinkingTokens: Int?  // null 表示禁用/使用默认值，0 也表示禁用
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

@Serializable
data class McpStatusRequest(
    override val subtype: String = "mcp_status"
) : ControlRequest

@Serializable
data class McpSetServersRequest(
    override val subtype: String = "mcp_set_servers",
    val servers: Map<String, McpStdioServerDto>
) : ControlRequest

/**
 * MCP stdio server configuration DTO for mcp_set_servers request.
 * This is the format expected by CLI's mcp_set_servers control command.
 */
@Serializable
data class McpStdioServerDto(
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

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

/**
 * MCP server status info returned by mcp_status control request.
 */
@Serializable
data class McpServerStatusInfo(
    val name: String,
    val status: String,  // "connected" | "failed" | "sdk"
    val serverInfo: JsonElement? = null
)

/**
 * Response from mcp_set_servers request.
 */
@Serializable
data class McpSetServersResponse(
    val added: List<String>,
    val removed: List<String>,
    val errors: Map<String, String>
)

/**
 * Response from mcp_reconnect request.
 */
@Serializable
data class McpReconnectResponse(
    /** Whether the reconnect was successful */
    val success: Boolean,
    /** The server name that was reconnected */
    val serverName: String,
    /** Server status after reconnect: "connected" | "failed" | "needs-auth" | etc. */
    val status: String?,
    /** Number of tools available after reconnect */
    val toolsCount: Int,
    /** Error message if reconnect failed */
    val error: String? = null
)

/**
 * MCP tool information.
 */
@Serializable
data class McpToolInfo(
    /** Tool name (original MCP tool name, e.g., "FileIndex") */
    val name: String,
    /** Tool description */
    val description: String,
    /** Input JSON Schema */
    val inputSchema: JsonElement? = null
)

/**
 * Response from mcp_tools request.
 */
@Serializable
data class McpToolsResponse(
    /** Server name filter (null if all servers) */
    val serverName: String?,
    /** List of tools */
    val tools: List<McpToolInfo>,
    /** Total count of tools */
    val count: Int
)

/**
 * Response from mcp_disable/mcp_enable request.
 */
@Serializable
data class McpDisableEnableResponse(
    /** Whether the operation was successful */
    val success: Boolean,
    /** The server name that was disabled/enabled */
    val serverName: String,
    /** Server status after operation: "disabled" | "connected" | "pending" | "failed" | etc. */
    val status: String?,
    /** Number of tools available (0 if disabled) */
    val toolsCount: Int,
    /** Error message if operation failed */
    val error: String? = null
)

/**
 * Chrome extension status - matches official /chrome command display format.
 *
 * Data sources:
 * 1. installed - Local file system check (Chrome Extensions directory)
 * 2. enabled/connected/mcpServerStatus - From mcp_status control command
 * 3. serverInfo - Full MCP server info (contains name, version, etc.)
 *
 * Official /chrome display format:
 * ```
 * Status: Connected/Disabled     ← connected field
 * Extension: Installed/Not detected  ← installed field
 * ```
 */
@Serializable
data class ChromeStatus(
    /** Whether the Chrome extension is installed (checks NativeMessagingHost config file) */
    val installed: Boolean,
    /** Whether Chrome integration is enabled by default (k1().claudeInChromeDefaultEnabled) */
    val enabled: Boolean,
    /** Whether the MCP server "claude-in-chrome" is currently connected */
    val connected: Boolean,
    /** MCP server status: "connected" | "failed" | "pending" | "needs-auth" | "disabled" | null */
    val mcpServerStatus: String? = null,
    /** Extension version (e.g., "1.0.36") when connected */
    val extensionVersion: String? = null
)