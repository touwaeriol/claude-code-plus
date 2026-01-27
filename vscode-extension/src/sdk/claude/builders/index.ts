/**
 * Builders module exports
 * Configuration builders and DSL for Claude Agent SDK
 */

// Options builder
export {
  OptionsBuilder,
  optionsBuilder,
  addMcpServer,
  addSecurityHooksToOptions,
  addStatisticsHooksToOptions,
  addHookToOptions,
  addAllowedToolsToOptions,
  addMcpServerToolsToOptions,
  addMcpServerWildcardToolsToOptions,
} from './optionsBuilder';

// Hook builder
export {
  ToolCall,
  HookBuilder,
  type HookHandler,
  hookBuilder,
  securityHook,
  statisticsHook,
  loggingHook,
  rateLimitHook,
  toolFilterHook,
  mergeHooks,
} from './hookBuilder';

// MCP server builder
export {
  type ParameterType,
  type ParameterInfo,
  type ToolDefinition,
  type ContentItem,
  type ToolResult,
  type ToolHandler,
  type McpServer,
  ToolResultHelpers,
  simpleTool,
  simpleToolWithSchema,
  MultiToolServerBuilder,
  multiToolServer,
} from './mcpServerBuilder';
