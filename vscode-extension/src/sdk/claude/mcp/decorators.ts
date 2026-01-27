/**
 * MCP Decorators - TypeScript decorators for MCP server and tool definitions
 * 
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/mcp/annotations/McpAnnotations.kt
 * 
 * These decorators provide metadata for MCP servers and tools,
 * enabling automatic tool registration and schema generation.
 * 
 * Usage:
 * ```typescript
 * @McpServerConfig({
 *     name: 'calculator',
 *     version: '1.0.0',
 *     description: 'Math calculation tool server'
 * })
 * class CalculatorServer extends McpServerBaseDecorated {
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

import { ParameterType } from './mcpServer';

// Metadata keys for storing decorator information
const MCP_SERVER_CONFIG_KEY = Symbol('mcp:server:config');
const MCP_TOOL_KEY = Symbol('mcp:tool');
const TOOL_PARAM_KEY = Symbol('mcp:tool:param');
const TOOL_GROUP_KEY = Symbol('mcp:tool:group');
const REQUIRES_PERMISSION_KEY = Symbol('mcp:tool:permission');
const DEPRECATED_TOOL_KEY = Symbol('mcp:tool:deprecated');
const RATE_LIMIT_KEY = Symbol('mcp:tool:rateLimit');
const EXPERIMENTAL_TOOL_KEY = Symbol('mcp:tool:experimental');

/**
 * Metadata storage for classes without reflect-metadata
 */
const classMetadata = new WeakMap<object, Map<symbol, unknown>>();
const methodMetadata = new WeakMap<object, Map<string, Map<symbol, unknown>>>();
const parameterMetadata = new WeakMap<object, Map<string, Map<number, Map<symbol, unknown>>>>();

/**
 * Helper to get or create metadata map for a class
 */
function getClassMetadata(target: object): Map<symbol, unknown> {
    let meta = classMetadata.get(target);
    if (!meta) {
        meta = new Map();
        classMetadata.set(target, meta);
    }
    return meta;
}

/**
 * Helper to get or create metadata map for a method
 */
function getMethodMetadata(target: object, methodName: string): Map<symbol, unknown> {
    let methods = methodMetadata.get(target);
    if (!methods) {
        methods = new Map();
        methodMetadata.set(target, methods);
    }
    let meta = methods.get(methodName);
    if (!meta) {
        meta = new Map();
        methods.set(methodName, meta);
    }
    return meta;
}

/**
 * Helper to get or create metadata map for a parameter
 */
function getParameterMetadata(target: object, methodName: string, paramIndex: number): Map<symbol, unknown> {
    let methods = parameterMetadata.get(target);
    if (!methods) {
        methods = new Map();
        parameterMetadata.set(target, methods);
    }
    let params = methods.get(methodName);
    if (!params) {
        params = new Map();
        methods.set(methodName, params);
    }
    let meta = params.get(paramIndex);
    if (!meta) {
        meta = new Map();
        params.set(paramIndex, meta);
    }
    return meta;
}

// ============================================================================
// MCP Server Config
// ============================================================================

/**
 * MCP server configuration options
 */
export interface McpServerConfigOptions {
    /**
     * Server name, empty means use class name
     */
    name?: string;
    /**
     * Server version
     */
    version?: string;
    /**
     * Server description
     */
    description?: string;
}

/**
 * MCP Server Configuration decorator
 * 
 * Use to configure MCP server properties.
 * 
 * @param options Server configuration options
 */
export function McpServerConfig(options: McpServerConfigOptions = {}): ClassDecorator {
    return function(target: Function) {
        const meta = getClassMetadata(target.prototype);
        meta.set(MCP_SERVER_CONFIG_KEY, {
            name: options.name || '',
            version: options.version || '1.0.0',
            description: options.description || ''
        });
    };
}

/**
 * Get MCP server config from a class
 */
export function getMcpServerConfig(target: object): McpServerConfigOptions | undefined {
    const meta = classMetadata.get(target);
    return meta?.get(MCP_SERVER_CONFIG_KEY) as McpServerConfigOptions | undefined;
}

// ============================================================================
// MCP Tool
// ============================================================================

/**
 * MCP Tool metadata
 */
export interface McpToolMetadata {
    description: string;
}

/**
 * MCP Tool decorator
 * 
 * Mark a method as an MCP tool, auto-registered as callable tool.
 * 
 * @param description Tool description to help AI understand tool purpose
 */
export function McpTool(description: string): MethodDecorator {
    return function(target: object, propertyKey: string | symbol, descriptor: PropertyDescriptor) {
        const methodName = String(propertyKey);
        const meta = getMethodMetadata(target, methodName);
        meta.set(MCP_TOOL_KEY, { description });
        
        // Track which methods are tools
        const classMeta = getClassMetadata(target);
        let tools = classMeta.get(MCP_TOOL_KEY) as string[] | undefined;
        if (!tools) {
            tools = [];
            classMeta.set(MCP_TOOL_KEY, tools);
        }
        if (!tools.includes(methodName)) {
            tools.push(methodName);
        }
    };
}

/**
 * Get MCP tool metadata from a method
 */
export function getMcpToolMetadata(target: object, methodName: string): McpToolMetadata | undefined {
    const methods = methodMetadata.get(target);
    const meta = methods?.get(methodName);
    return meta?.get(MCP_TOOL_KEY) as McpToolMetadata | undefined;
}

/**
 * Get all MCP tool method names from a class
 */
export function getMcpToolMethods(target: object): string[] {
    const meta = classMetadata.get(target);
    return (meta?.get(MCP_TOOL_KEY) as string[] | undefined) ?? [];
}

// ============================================================================
// Tool Parameter
// ============================================================================

/**
 * Tool parameter metadata
 */
export interface ToolParamMetadata {
    description: string;
    type?: ParameterType;
}

/**
 * Tool Parameter decorator
 * 
 * Describe method parameters for tool schema generation.
 * 
 * @param description Parameter description including constraints, defaults, examples
 * @param type Optional parameter type (auto-detected if not provided)
 */
export function ToolParam(description: string, type?: ParameterType): ParameterDecorator {
    return function(target: object, propertyKey: string | symbol | undefined, parameterIndex: number) {
        if (propertyKey === undefined) return;
        const methodName = String(propertyKey);
        const meta = getParameterMetadata(target, methodName, parameterIndex);
        meta.set(TOOL_PARAM_KEY, { description, type });
    };
}

/**
 * Get tool parameter metadata
 */
export function getToolParamMetadata(target: object, methodName: string, paramIndex: number): ToolParamMetadata | undefined {
    const methods = parameterMetadata.get(target);
    const params = methods?.get(methodName);
    const meta = params?.get(paramIndex);
    return meta?.get(TOOL_PARAM_KEY) as ToolParamMetadata | undefined;
}

/**
 * Get all tool parameter metadata for a method
 */
export function getAllToolParamMetadata(target: object, methodName: string): Map<number, ToolParamMetadata> {
    const result = new Map<number, ToolParamMetadata>();
    const methods = parameterMetadata.get(target);
    const params = methods?.get(methodName);
    if (params) {
        params.forEach((meta, index) => {
            const paramMeta = meta.get(TOOL_PARAM_KEY) as ToolParamMetadata | undefined;
            if (paramMeta) {
                result.set(index, paramMeta);
            }
        });
    }
    return result;
}

// ============================================================================
// Tool Group
// ============================================================================

/**
 * Tool group metadata
 */
export interface ToolGroupMetadata {
    group: string;
    description?: string;
}

/**
 * Tool Group decorator
 * 
 * Group related tools for management and documentation.
 * 
 * @param group Group name
 * @param description Group description
 */
export function ToolGroup(group: string, description?: string): MethodDecorator {
    return function(target: object, propertyKey: string | symbol, descriptor: PropertyDescriptor) {
        const methodName = String(propertyKey);
        const meta = getMethodMetadata(target, methodName);
        meta.set(TOOL_GROUP_KEY, { group, description });
    };
}

/**
 * Get tool group metadata
 */
export function getToolGroupMetadata(target: object, methodName: string): ToolGroupMetadata | undefined {
    const methods = methodMetadata.get(target);
    const meta = methods?.get(methodName);
    return meta?.get(TOOL_GROUP_KEY) as ToolGroupMetadata | undefined;
}

// ============================================================================
// Requires Permission
// ============================================================================

/**
 * Permission check strategy
 */
export enum PermissionStrategy {
    /**
     * All permissions required
     */
    ALL_REQUIRED = 'ALL_REQUIRED',
    /**
     * Any one permission is enough
     */
    ANY_REQUIRED = 'ANY_REQUIRED',
    /**
     * Documentation only, not enforced
     */
    DOCUMENTATION_ONLY = 'DOCUMENTATION_ONLY'
}

/**
 * Permission metadata
 */
export interface RequiresPermissionMetadata {
    permissions: string[];
    strategy: PermissionStrategy;
}

/**
 * Requires Permission decorator
 * 
 * Mark required permission level for a tool.
 * 
 * @param permissions Required permission list
 * @param strategy Permission check strategy
 */
export function RequiresPermission(
    permissions: string[],
    strategy: PermissionStrategy = PermissionStrategy.ALL_REQUIRED
): MethodDecorator {
    return function(target: object, propertyKey: string | symbol, descriptor: PropertyDescriptor) {
        const methodName = String(propertyKey);
        const meta = getMethodMetadata(target, methodName);
        meta.set(REQUIRES_PERMISSION_KEY, { permissions, strategy });
    };
}

/**
 * Get permission metadata
 */
export function getRequiresPermissionMetadata(target: object, methodName: string): RequiresPermissionMetadata | undefined {
    const methods = methodMetadata.get(target);
    const meta = methods?.get(methodName);
    return meta?.get(REQUIRES_PERMISSION_KEY) as RequiresPermissionMetadata | undefined;
}

// ============================================================================
// Deprecated Tool
// ============================================================================

/**
 * Deprecated tool metadata
 */
export interface DeprecatedToolMetadata {
    reason: string;
    replaceWith?: string;
    removeInVersion?: string;
}

/**
 * Deprecated Tool decorator
 * 
 * Mark deprecated tool methods.
 * 
 * @param reason Deprecation reason
 * @param replaceWith Replacement suggestion
 * @param removeInVersion Version when tool will be removed
 */
export function DeprecatedTool(
    reason: string,
    replaceWith?: string,
    removeInVersion?: string
): MethodDecorator {
    return function(target: object, propertyKey: string | symbol, descriptor: PropertyDescriptor) {
        const methodName = String(propertyKey);
        const meta = getMethodMetadata(target, methodName);
        meta.set(DEPRECATED_TOOL_KEY, { reason, replaceWith, removeInVersion });
    };
}

/**
 * Get deprecated tool metadata
 */
export function getDeprecatedToolMetadata(target: object, methodName: string): DeprecatedToolMetadata | undefined {
    const methods = methodMetadata.get(target);
    const meta = methods?.get(methodName);
    return meta?.get(DEPRECATED_TOOL_KEY) as DeprecatedToolMetadata | undefined;
}

// ============================================================================
// Rate Limit
// ============================================================================

/**
 * Rate limit metadata
 */
export interface RateLimitMetadata {
    maxCallsPerMinute: number;
    maxCallsPerHour: number;
}

/**
 * Rate Limit decorator
 * 
 * Limit tool call frequency.
 * 
 * @param maxCallsPerMinute Max calls per minute (default 60)
 * @param maxCallsPerHour Max calls per hour (default 3600)
 */
export function RateLimit(
    maxCallsPerMinute: number = 60,
    maxCallsPerHour: number = 3600
): MethodDecorator {
    return function(target: object, propertyKey: string | symbol, descriptor: PropertyDescriptor) {
        const methodName = String(propertyKey);
        const meta = getMethodMetadata(target, methodName);
        meta.set(RATE_LIMIT_KEY, { maxCallsPerMinute, maxCallsPerHour });
    };
}

/**
 * Get rate limit metadata
 */
export function getRateLimitMetadata(target: object, methodName: string): RateLimitMetadata | undefined {
    const methods = methodMetadata.get(target);
    const meta = methods?.get(methodName);
    return meta?.get(RATE_LIMIT_KEY) as RateLimitMetadata | undefined;
}

// ============================================================================
// Experimental Tool
// ============================================================================

/**
 * Experimental tool metadata
 */
export interface ExperimentalToolMetadata {
    message: string;
}

/**
 * Experimental Tool decorator
 * 
 * Mark experimental or unstable tools.
 * 
 * @param message Experimental notice (default: "This tool is experimental, API may change")
 */
export function ExperimentalTool(
    message: string = 'This tool is experimental, API may change'
): MethodDecorator {
    return function(target: object, propertyKey: string | symbol, descriptor: PropertyDescriptor) {
        const methodName = String(propertyKey);
        const meta = getMethodMetadata(target, methodName);
        meta.set(EXPERIMENTAL_TOOL_KEY, { message });
    };
}

/**
 * Get experimental tool metadata
 */
export function getExperimentalToolMetadata(target: object, methodName: string): ExperimentalToolMetadata | undefined {
    const methods = methodMetadata.get(target);
    const meta = methods?.get(methodName);
    return meta?.get(EXPERIMENTAL_TOOL_KEY) as ExperimentalToolMetadata | undefined;
}
