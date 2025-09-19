package com.claudecodeplus.sdk.types

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * MCP tool definition based on Python SDK SdkMcpTool.
 */
@Serializable
data class SdkMcpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonElement, // JSON schema for input validation
    // Note: handler is not serializable, handled separately in runtime
)

/**
 * MCP server instance configuration.
 */
@Serializable
data class McpServerInstance(
    val name: String,
    val config: McpServerConfig,
    val tools: List<SdkMcpTool> = emptyList(),
    val status: McpServerStatus = McpServerStatus.DISCONNECTED
)

/**
 * MCP server connection status.
 */
enum class McpServerStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR
}

/**
 * MCP resource information.
 */
@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val description: String? = null,
    val mimeType: String? = null
)

/**
 * MCP tool execution request.
 */
@Serializable
data class McpToolRequest(
    val serverName: String,
    val toolName: String,
    val arguments: JsonElement
)

/**
 * MCP tool execution response.
 */
@Serializable
data class McpToolResponse(
    val success: Boolean,
    val result: JsonElement? = null,
    val error: String? = null,
    val metadata: Map<String, JsonElement> = emptyMap()
)

/**
 * MCP prompt template.
 */
@Serializable
data class McpPrompt(
    val name: String,
    val description: String? = null,
    val arguments: List<McpPromptArgument> = emptyList()
)

/**
 * MCP prompt argument definition.
 */
@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = true
)

/**
 * MCP server capabilities.
 */
@Serializable
data class McpServerCapabilities(
    val logging: McpLoggingCapability? = null,
    val prompts: McpPromptsCapability? = null,
    val resources: McpResourcesCapability? = null,
    val tools: McpToolsCapability? = null
)

/**
 * MCP logging capability.
 */
@Serializable
data class McpLoggingCapability(
    val enabled: Boolean = false
)

/**
 * MCP prompts capability.
 */
@Serializable
data class McpPromptsCapability(
    val listChanged: Boolean = false
)

/**
 * MCP resources capability.
 */
@Serializable
data class McpResourcesCapability(
    val subscribe: Boolean = false,
    val listChanged: Boolean = false
)

/**
 * MCP tools capability.
 */
@Serializable
data class McpToolsCapability(
    val listChanged: Boolean = false
)

/**
 * MCP client capabilities.
 */
@Serializable
data class McpClientCapabilities(
    val experimental: Map<String, JsonElement> = emptyMap(),
    val sampling: Map<String, JsonElement> = emptyMap()
)

/**
 * MCP initialization result.
 */
@Serializable
data class McpInitializeResult(
    val protocolVersion: String,
    val capabilities: McpServerCapabilities,
    val serverInfo: McpServerInfo
)

/**
 * MCP server information.
 */
@Serializable
data class McpServerInfo(
    val name: String,
    val version: String
)

/**
 * MCP client information.
 */
@Serializable
data class McpClientInfo(
    val name: String = "claude-code-plus",
    val version: String = "1.0.0"
)