/**
 * MCP (Model Context Protocol) types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/McpTypes.kt
 */

import type { JsonValue, JsonObject } from './common';
import type { McpServerConfig } from './options';

/**
 * MCP tool definition based on Python SDK SdkMcpTool.
 */
export interface SdkMcpTool {
  name: string;
  description: string;
  /** JSON schema for input validation */
  inputSchema: JsonValue;
  // Note: handler is not serializable, handled separately in runtime
}

/**
 * MCP server connection status.
 */
export type McpServerStatus = 'CONNECTED' | 'DISCONNECTED' | 'CONNECTING' | 'ERROR';

/**
 * MCP server status enum values.
 */
export const McpServerStatuses = {
  CONNECTED: 'CONNECTED',
  DISCONNECTED: 'DISCONNECTED',
  CONNECTING: 'CONNECTING',
  ERROR: 'ERROR',
} as const;

/**
 * MCP server instance configuration.
 */
export interface McpServerInstance {
  name: string;
  config: McpServerConfig;
  tools?: SdkMcpTool[];
  status?: McpServerStatus;
}

/**
 * MCP resource information.
 */
export interface McpResource {
  uri: string;
  name: string;
  description?: string;
  mimeType?: string;
}

/**
 * MCP tool execution request.
 */
export interface McpToolRequest {
  serverName: string;
  toolName: string;
  arguments: JsonValue;
}

/**
 * MCP tool execution response.
 */
export interface McpToolResponse {
  success: boolean;
  result?: JsonValue;
  error?: string;
  metadata?: Record<string, JsonValue>;
}

/**
 * MCP prompt template.
 */
export interface McpPrompt {
  name: string;
  description?: string;
  arguments?: McpPromptArgument[];
}

/**
 * MCP prompt argument definition.
 */
export interface McpPromptArgument {
  name: string;
  description?: string;
  required?: boolean;
}

/**
 * MCP server capabilities.
 */
export interface McpServerCapabilities {
  logging?: McpLoggingCapability;
  prompts?: McpPromptsCapability;
  resources?: McpResourcesCapability;
  tools?: McpToolsCapability;
}

/**
 * MCP logging capability.
 */
export interface McpLoggingCapability {
  enabled?: boolean;
}

/**
 * MCP prompts capability.
 */
export interface McpPromptsCapability {
  listChanged?: boolean;
}

/**
 * MCP resources capability.
 */
export interface McpResourcesCapability {
  subscribe?: boolean;
  listChanged?: boolean;
}

/**
 * MCP tools capability.
 */
export interface McpToolsCapability {
  listChanged?: boolean;
}

/**
 * MCP client capabilities.
 */
export interface McpClientCapabilities {
  experimental?: Record<string, JsonValue>;
  sampling?: Record<string, JsonValue>;
}

/**
 * MCP initialization result.
 */
export interface McpInitializeResult {
  protocolVersion: string;
  capabilities: McpServerCapabilities;
  serverInfo: McpServerInfo;
}

/**
 * MCP server information.
 */
export interface McpServerInfo {
  name: string;
  version: string;
}

/**
 * MCP client information.
 */
export interface McpClientInfo {
  name?: string;
  version?: string;
}

/**
 * Default MCP client info.
 */
export const defaultMcpClientInfo: McpClientInfo = {
  name: 'claude-code-plus',
  version: '1.0.0',
};
