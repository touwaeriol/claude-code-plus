/**
 * MCP Module Entry Point - Claude Agent SDK
 * 
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/mcp/
 * 
 * This module provides the MCP (Model Context Protocol) server implementation
 * for the Claude Agent SDK. It includes:
 * 
 * - McpServer interface: Base interface for all MCP servers
 * - McpServerBase class: Abstract base class with auto tool registration
 * - Decorators: TypeScript decorators for tool and server configuration
 * - ToolUseContext: Async context for passing toolUseId in call chain
 * 
 * Usage example:
 * ```typescript
 * import {
 *     McpServerBase,
 *     McpServerConfig,
 *     McpTool,
 *     ToolParam,
 *     ToolResultHelper,
 *     ParameterType
 * } from '@/sdk/claude/mcp';
 * 
 * @McpServerConfig({
 *     name: 'calculator',
 *     version: '1.0.0',
 *     description: 'Math calculation tool server'
 * })
 * class CalculatorServer extends McpServerBase {
 *     @McpTool('Calculate the sum of two numbers')
 *     async add(
 *         @ToolParam('First number') a: number,
 *         @ToolParam('Second number') b: number
 *     ): Promise<number> {
 *         return a + b;
 *     }
 * }
 * ```
 */

// ============================================================================
// MCP Server Interface and Types
// ============================================================================

export {
    // Enums
    ParameterType,
    
    // Interfaces
    ParameterInfo,
    JsonSchema,
    ToolDefinition,
    ContentItem,
    ToolResult,
    ToolResultSuccess,
    ToolResultError,
    McpServer,
    ToolHandler,
    ToolHandlerBase,
    
    // Helper classes
    ToolHandlerWithParams,
    ToolHandlerWithSchema,
    
    // Helper objects
    ToolDefinitionHelper,
    ContentItemHelper,
    ToolResultHelper
} from './mcpServer';

// ============================================================================
// MCP Server Base Class
// ============================================================================

export {
    McpServerBase,
    McpLogger
} from './mcpServerBase';

// ============================================================================
// Tool Use Context
// ============================================================================

export {
    ToolUseContextData,
    withToolUseContext,
    withToolUseContextAsync,
    withToolUseContextFull,
    currentToolUseId,
    currentToolUseContext,
    isInToolUseContext,
    getToolUseContextExtra
} from './toolUseContext';

// ============================================================================
// Decorators
// ============================================================================

export {
    // Server config
    McpServerConfig,
    McpServerConfigOptions,
    getMcpServerConfig,
    
    // Tool decorator
    McpTool,
    McpToolMetadata,
    getMcpToolMetadata,
    getMcpToolMethods,
    
    // Parameter decorator
    ToolParam,
    ToolParamMetadata,
    getToolParamMetadata,
    getAllToolParamMetadata,
    
    // Tool group
    ToolGroup,
    ToolGroupMetadata,
    getToolGroupMetadata,
    
    // Permission
    RequiresPermission,
    RequiresPermissionMetadata,
    PermissionStrategy,
    getRequiresPermissionMetadata,
    
    // Deprecated
    DeprecatedTool,
    DeprecatedToolMetadata,
    getDeprecatedToolMetadata,
    
    // Rate limit
    RateLimit,
    RateLimitMetadata,
    getRateLimitMetadata,
    
    // Experimental
    ExperimentalTool,
    ExperimentalToolMetadata,
    getExperimentalToolMetadata
} from './decorators';
