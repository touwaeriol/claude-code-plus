/**
 * i18n Module - Internationalization Support
 * 
 * Provides localization support for Claude Code Plus VS Code extension.
 * 
 * Usage:
 * ```typescript
 * import { ClaudeCodePlusBundle, McpBundle, McpInstructions } from './i18n';
 * 
 * // Get a message with parameter substitution
 * const msg = ClaudeCodePlusBundle.message('dialog.edit.builtin.mcp', 'MyServer');
 * // Result: "Edit MyServer"
 * 
 * // Get MCP message
 * const desc = McpBundle.message('mcp.jetbrainsIde.description');
 * // Result: "Code search, file indexing"
 * 
 * // Load MCP instructions
 * McpInstructions.initialize(context);
 * const instructions = McpInstructions.load('jetbrains-git');
 * ```
 * 
 * Supported locales: en (English), zh_CN (Simplified Chinese), ja (Japanese), ko (Korean)
 */

// Types
export type { Locale, MessageDictionary, LocaleMessages, MessageBundle } from './types';

// Base Bundle
export { BaseBundle, detectLocale } from './baseBundle';

// Claude Code Plus Bundle
export {
    ClaudeCodePlusBundle,
    message,
    type ClaudeCodePlusMessageKey,
} from './claudeCodePlusBundle';

// MCP Bundle
export {
    McpBundle,
    mcpMessage,
    type McpMessageKey,
} from './mcpBundle';

// MCP Instructions
export {
    McpInstructions,
    McpNames,
    loadMcpInstructions,
    type McpName,
} from './mcpInstructions';
