/**
 * Claude Agent SDK Protocol Module
 * 
 * This module provides the protocol layer for communicating with Claude CLI.
 * 
 * Key components:
 * - ControlProtocol: Handles bidirectional communication with CLI
 * - MessageParser: Parses raw JSON messages into typed objects
 * - ToolTypeParser: Converts generic tool uses to specific types
 * - McpMessageHandler: Handles MCP server messages
 * 
 * Control commands:
 * - mcp_reconnect, mcp_disable, mcp_enable: MCP control (official CLI built-in)
 */

// Export all models
export * from './models';

// Export control protocol
export {
  ControlProtocol,
  ControlProtocolException,
  type Transport,
  type ControlProtocolOptions,
} from './controlProtocol';

// Export message parser
export { MessageParser, MessageParsingException } from './messageParser';

// Export tool type parser
export {
  ToolTypeParser,
  ToolType,
  getToolTypeFromName,
  type SpecificToolUse,
  type BashToolUse,
  type BashOutputToolUse,
  type KillShellToolUse,
  type EditToolUse,
  type EditOperation,
  type MultiEditToolUse,
  type ReadToolUse,
  type WriteToolUse,
  type GlobToolUse,
  type GrepToolUse,
  type WebFetchToolUse,
  type WebSearchToolUse,
  type TodoItem,
  type TodoWriteToolUse,
  type TaskToolUse,
  type NotebookEditToolUse,
  type ExitPlanModeToolUse,
  type McpToolUse,
  type ListMcpResourcesToolUse,
  type ReadMcpResourceToolUse,
  type SkillToolUse,
  type UnknownToolUse,
} from './toolTypeParser';

// Export MCP message handler
export {
  McpMessageHandler,
  type McpServer,
  type McpToolDefinition,
  type McpContentItem,
  type McpToolResult,
} from './mcpMessageHandler';
