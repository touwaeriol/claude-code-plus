/**
 * MCP Server Interface - Base interface for all MCP servers
 * 
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/mcp/McpServer.kt
 * 
 * Provides standard interface for creating custom MCP tool servers,
 * supporting tool listing and tool execution.
 */

/**
 * Parameter types for tool definitions
 */
export enum ParameterType {
    STRING = 'string',
    NUMBER = 'number',
    BOOLEAN = 'boolean',
    ARRAY = 'array',
    OBJECT = 'object'
}

/**
 * Parameter information - contains type and description
 */
export interface ParameterInfo {
    type: ParameterType;
    description?: string;
}

/**
 * JSON Schema for tool input
 */
export interface JsonSchema {
    type: string;
    properties?: Record<string, JsonSchema>;
    items?: JsonSchema;
    required?: string[];
    description?: string;
    enum?: (string | number | boolean)[];
    default?: unknown;
    [key: string]: unknown;
}

/**
 * Tool definition
 */
export interface ToolDefinition {
    name: string;
    description: string;
    inputSchema: JsonSchema;
}

/**
 * Tool definition helper functions
 */
export const ToolDefinitionHelper = {
    /**
     * Create simple tool definition with no parameters
     */
    simple(name: string, description: string): ToolDefinition {
        return {
            name,
            description,
            inputSchema: {
                type: 'object',
                properties: {}
            }
        };
    },

    /**
     * Create tool definition with parameter types (legacy compatibility)
     */
    withParameters(
        name: string,
        description: string,
        parameters: Record<string, ParameterType>
    ): ToolDefinition {
        const parameterInfo: Record<string, ParameterInfo> = {};
        for (const [key, type] of Object.entries(parameters)) {
            parameterInfo[key] = { type };
        }
        return this.withParameterInfo(name, description, parameterInfo);
    },

    /**
     * Create tool definition with parameter info (supports descriptions)
     */
    withParameterInfo(
        name: string,
        description: string,
        parameters: Record<string, ParameterInfo>
    ): ToolDefinition {
        const properties: Record<string, JsonSchema> = {};
        const required: string[] = [];

        for (const [paramName, paramInfo] of Object.entries(parameters)) {
            const schema: JsonSchema = {
                type: paramInfo.type
            };
            if (paramInfo.description) {
                schema.description = paramInfo.description;
            }
            properties[paramName] = schema;
            required.push(paramName);
        }

        return {
            name,
            description,
            inputSchema: {
                type: 'object',
                properties,
                required
            }
        };
    }
};

/**
 * Content item types
 */
export type ContentItem =
    | { type: 'text'; text: string }
    | { type: 'json'; data: unknown }
    | { type: 'binary'; data: Uint8Array; mimeType: string };

/**
 * Content item helper functions
 */
export const ContentItemHelper = {
    text(content: string): ContentItem {
        return { type: 'text', text: content };
    },
    json(data: unknown): ContentItem {
        return { type: 'json', data };
    },
    binary(data: Uint8Array, mimeType: string): ContentItem {
        return { type: 'binary', data, mimeType };
    }
};

/**
 * Tool execution result
 */
export type ToolResult = ToolResultSuccess | ToolResultError;

/**
 * Success result
 */
export interface ToolResultSuccess {
    isError: false;
    content: ContentItem[];
    metadata?: Record<string, unknown>;
}

/**
 * Error result
 */
export interface ToolResultError {
    isError: true;
    error: string;
    code?: number;
    content: ContentItem[];
}

/**
 * Tool result helper functions
 */
export const ToolResultHelper = {
    /**
     * Create success result with text content
     */
    success(text: string, metadata?: Record<string, unknown>): ToolResult {
        return {
            isError: false,
            content: [ContentItemHelper.text(text)],
            metadata
        };
    },

    /**
     * Create success result with JSON data
     */
    successJson(data: unknown, metadata?: Record<string, unknown>): ToolResult {
        return {
            isError: false,
            content: [ContentItemHelper.json(data)],
            metadata
        };
    },

    /**
     * Create success result with content items
     */
    successWithContent(content: ContentItem[], metadata?: Record<string, unknown>): ToolResult {
        return {
            isError: false,
            content,
            metadata
        };
    },

    /**
     * Create error result
     */
    error(message: string, code: number = -1): ToolResult {
        return {
            isError: true,
            error: message,
            code,
            content: [ContentItemHelper.text(`Error: ${message}`)]
        };
    }
};

/**
 * Tool handler function type
 */
export type ToolHandler = (arguments_: Record<string, unknown>) => Promise<ToolResult>;

/**
 * Internal tool handler interface
 */
export interface ToolHandlerBase {
    name: string;
    description: string;
    handler: ToolHandler;
    toDefinition(): ToolDefinition;
}

/**
 * Internal tool handler with parameter info
 */
export class ToolHandlerWithParams implements ToolHandlerBase {
    constructor(
        public readonly name: string,
        public readonly description: string,
        public readonly parameterSchema: Record<string, ParameterInfo> | null,
        public readonly handler: ToolHandler
    ) {}

    toDefinition(): ToolDefinition {
        if (this.parameterSchema) {
            return ToolDefinitionHelper.withParameterInfo(
                this.name,
                this.description,
                this.parameterSchema
            );
        }
        return ToolDefinitionHelper.simple(this.name, this.description);
    }
}

/**
 * Internal tool handler with full JSON schema
 */
export class ToolHandlerWithSchema implements ToolHandlerBase {
    constructor(
        public readonly name: string,
        public readonly description: string,
        public readonly inputSchema: JsonSchema,
        public readonly handler: ToolHandler
    ) {}

    toDefinition(): ToolDefinition {
        return {
            name: this.name,
            description: this.description,
            inputSchema: this.inputSchema
        };
    }
}

/**
 * MCP Server Interface - Base interface for all MCP servers
 */
export interface McpServer {
    /**
     * Server name, used as identifier
     */
    readonly name: string;

    /**
     * Server version
     */
    readonly version: string;

    /**
     * Server description
     */
    readonly description: string;

    /**
     * Tool call timeout in milliseconds
     * - null/undefined, 0, or negative: infinite timeout (for tools requiring user interaction)
     * - positive: specified timeout
     */
    readonly timeout?: number | null;

    /**
     * Whether to reset timeout timer when receiving progress reports
     * 
     * When true, timeout timer resets as long as MCP tool keeps reporting progress.
     * This allows long-running operations (like user interaction) to not timeout.
     * 
     * Reference: https://github.com/anthropics/claude-code/issues/470
     */
    readonly resetTimeoutOnProgress?: boolean;

    /**
     * Whether progress reporting is enabled
     * 
     * When true, MCP server should periodically report progress during long operations.
     * Used with resetTimeoutOnProgress to prevent timeouts.
     */
    readonly progressReportingEnabled?: boolean;

    /**
     * Get system prompt appendix for this MCP server
     * 
     * MCP servers can provide additional system prompts through this method,
     * which will be appended to the main system prompt to guide AI on how to
     * correctly use the tools provided by this server.
     * 
     * @returns System prompt appendix content, null means no additional prompt
     */
    getSystemPromptAppendix(): string | null;

    /**
     * Get list of tools that should be auto-approved
     * 
     * Tool names returned will be added to Claude CLI's --allowed-tools parameter,
     * these tools will automatically get permission without user confirmation.
     * 
     * Note: Return short tool names (e.g., "AskUserQuestion"),
     * system will automatically add mcp__{serverName}__ prefix.
     * 
     * @returns List of auto-approved tool names, default empty list
     */
    getAllowedTools(): string[];

    /**
     * List all available tools
     */
    listTools(): Promise<ToolDefinition[]>;

    /**
     * Call specified tool (Map arguments version, for legacy compatibility)
     */
    callTool(toolName: string, arguments_: Record<string, unknown>): Promise<ToolResult>;

    /**
     * Call specified tool with toolUseId
     * 
     * toolUseId is passed through async context, tools can get it via currentToolUseId().
     * Mainly used for file operation tools to record file history for showing Diff.
     * 
     * @param toolName Tool name
     * @param arguments_ Tool arguments
     * @param toolUseId Tool use ID (optional)
     * @returns Tool execution result
     */
    callToolWithContext(
        toolName: string,
        arguments_: Record<string, unknown>,
        toolUseId?: string | null
    ): Promise<ToolResult>;
}
