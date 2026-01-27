/**
 * Callback module exports
 * Tool callback system for custom tool handling
 */

// Tool callback interface and types
export {
  type ToolCallback,
  type ToolCallbackResult,
  BaseToolCallback,
  FunctionToolCallback,
  createToolCallback,
  successResult,
  errorResult,
} from './toolCallback';

// Registry for managing callbacks
export {
  ToolCallbackRegistry,
  type RegistryLogger,
  getGlobalRegistry,
  resetGlobalRegistry,
} from './registry';
