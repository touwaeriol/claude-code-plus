/**
 * Terminal MCP Module Exports
 */

export { TerminalMcpServer, TerminalMcpServerProvider } from './terminalMcpServer';

// Models
export * from './terminalModels';

// Managers
export { TerminalSessionManager } from './terminalSessionManager';
export { TerminalBackgroundManager, terminalBackgroundManager, type TerminalBackgroundResult } from './terminalBackgroundManager';

// Utilities
export { ShellResolver, type DetectedShell } from './shellResolver';
export { TerminalResultFormatter, type SessionInfo } from './terminalResultFormatter';
export { TerminalFeatureConfig, terminalFeatureConfig } from './terminalFeatureConfig';
export { TerminalToolSchemaManager, terminalToolSchemaManager, type ToolSchema, type ToolSchemaProperty } from './terminalToolSchemaManager';
