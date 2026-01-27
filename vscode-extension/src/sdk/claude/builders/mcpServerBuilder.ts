/**
 * MCP Server Builder - Quick tool functions for creating simple single-tool servers
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/builders/McpServerBuilder.kt
 *
 * This provides a manual registration approach for users who don't want to use decorators.
 * For most cases, using an annotation/decorator-based approach is recommended.
 */

import type { JsonValue, JsonObject } from '../types/common';

/**
 * Parameter type enum for tool definitions.
 */
export type ParameterType = 'string' | 'number' | 'boolean' | 'array' | 'object';

/**
 * Parameter info with type and description.
 */
export interface ParameterInfo {
  type: ParameterType;
  description?: string;
  required?: boolean;
}

/**
 * Tool definition for MCP servers.
 */
export interface ToolDefinition {
  name: string;
  description: string;
  inputSchema: JsonObject;
}

/**
 * Content item types for tool results.
 */
export type ContentItem =
  | { type: 'text'; text: string }
  | { type: 'json'; data: JsonValue }
  | { type: 'binary'; data: ArrayBuffer; mimeType: string };

/**
 * Tool execution result.
 */
export interface ToolResult {
  content: ContentItem[];
  isError: boolean;
  metadata?: Record<string, JsonValue>;
}

/**
 * Helper functions for creating tool results.
 */
export const ToolResultHelpers = {
  /**
   * Create a success result with text content.
   */
  success(text: string, metadata?: Record<string, JsonValue>): ToolResult {
    return {
      content: [{ type: 'text', text }],
      isError: false,
      metadata,
    };
  },

  /**
   * Create a success result with JSON content.
   */
  successJson(data: JsonValue, metadata?: Record<string, JsonValue>): ToolResult {
    return {
      content: [{ type: 'json', data }],
      isError: false,
      metadata,
    };
  },

  /**
   * Create an error result.
   */
  error(message: string): ToolResult {
    return {
      content: [{ type: 'text', text: `Error: ${message}` }],
      isError: true,
    };
  },
};

/**
 * Tool handler function type.
 */
export type ToolHandler = (arguments_: JsonObject) => Promise<ToolResult>;

/**
 * MCP Server interface.
 */
export interface McpServer {
  readonly name: string;
  readonly version: string;
  readonly description: string;
  readonly timeout?: number;
  readonly resetTimeoutOnProgress?: boolean;
  readonly progressReportingEnabled?: boolean;

  listTools(): Promise<ToolDefinition[]>;
  callTool(toolName: string, arguments_: JsonObject): Promise<ToolResult>;
  getSystemPromptAppendix?(): string | null;
  getAllowedTools?(): string[];
}

/**
 * Simple tool server implementation.
 */
class SimpleToolServer implements McpServer {
  readonly name: string;
  readonly version: string = '1.0.0';
  readonly description: string;

  private readonly tool: {
    name: string;
    description: string;
    inputSchema: JsonObject;
    handler: ToolHandler;
  };

  constructor(
    name: string,
    description: string,
    handler: ToolHandler,
    inputSchema?: JsonObject
  ) {
    this.name = name;
    this.description = description || `Simple tool: ${name}`;
    this.tool = {
      name,
      description: this.description,
      inputSchema: inputSchema ?? { type: 'object', properties: {} },
      handler,
    };
  }

  async listTools(): Promise<ToolDefinition[]> {
    return [
      {
        name: this.tool.name,
        description: this.tool.description,
        inputSchema: this.tool.inputSchema,
      },
    ];
  }

  async callTool(toolName: string, arguments_: JsonObject): Promise<ToolResult> {
    if (toolName !== this.name) {
      return ToolResultHelpers.error(`Tool '${toolName}' not found`);
    }

    try {
      return await this.tool.handler(arguments_);
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      return ToolResultHelpers.error(`Tool execution failed: ${message}`);
    }
  }
}

/**
 * Create a simple single-tool server.
 *
 * @example
 * ```typescript
 * const echoServer = simpleTool('echo', 'Echoes the input', async (args) => {
 *   const message = args.message as string ?? 'No message';
 *   return ToolResultHelpers.success(`Echo: ${message}`);
 * });
 * ```
 */
export function simpleTool(
  name: string,
  description: string = '',
  handler: ToolHandler
): McpServer {
  return new SimpleToolServer(name, description, handler);
}

/**
 * Create a simple tool with schema.
 *
 * @example
 * ```typescript
 * const addServer = simpleToolWithSchema(
 *   'add',
 *   'Adds two numbers',
 *   {
 *     a: { type: 'number', description: 'First number' },
 *     b: { type: 'number', description: 'Second number' },
 *   },
 *   async (args) => {
 *     const a = args.a as number ?? 0;
 *     const b = args.b as number ?? 0;
 *     return ToolResultHelpers.success(`Result: ${a + b}`);
 *   }
 * );
 * ```
 */
export function simpleToolWithSchema(
  name: string,
  description: string,
  parameters: Record<string, ParameterInfo>,
  handler: ToolHandler
): McpServer {
  const inputSchema = buildInputSchema(parameters);
  return new SimpleToolServer(name, description, handler, inputSchema);
}

/**
 * Build JSON Schema from parameter info.
 */
function buildInputSchema(parameters: Record<string, ParameterInfo>): JsonObject {
  const properties: JsonObject = {};
  const required: string[] = [];

  for (const [name, info] of Object.entries(parameters)) {
    const prop: JsonObject = { type: info.type };
    if (info.description) {
      prop.description = info.description;
    }
    properties[name] = prop;

    if (info.required !== false) {
      required.push(name);
    }
  }

  return {
    type: 'object',
    properties,
    required,
  };
}

/**
 * Multi-tool server builder.
 */
export class MultiToolServerBuilder {
  private readonly serverName: string;
  private readonly serverDescription: string;
  private readonly serverVersion: string;
  private tools: Map<
    string,
    {
      definition: ToolDefinition;
      handler: ToolHandler;
    }
  > = new Map();

  constructor(name: string, description: string = '', version: string = '1.0.0') {
    this.serverName = name;
    this.serverDescription = description || `Multi-tool server: ${name}`;
    this.serverVersion = version;
  }

  /**
   * Add a tool to the server.
   */
  addTool(
    name: string,
    description: string,
    handler: ToolHandler,
    parameters?: Record<string, ParameterInfo>
  ): this {
    const inputSchema = parameters ? buildInputSchema(parameters) : { type: 'object', properties: {} };
    this.tools.set(name, {
      definition: { name, description, inputSchema },
      handler,
    });
    return this;
  }

  /**
   * Add a tool with raw JSON schema.
   */
  addToolWithSchema(
    name: string,
    description: string,
    inputSchema: JsonObject,
    handler: ToolHandler
  ): this {
    this.tools.set(name, {
      definition: { name, description, inputSchema },
      handler,
    });
    return this;
  }

  /**
   * Build the server.
   */
  build(): McpServer {
    const tools = this.tools;
    const serverName = this.serverName;
    const serverDescription = this.serverDescription;
    const serverVersion = this.serverVersion;

    return {
      name: serverName,
      description: serverDescription,
      version: serverVersion,

      async listTools(): Promise<ToolDefinition[]> {
        return Array.from(tools.values()).map((t) => t.definition);
      },

      async callTool(toolName: string, arguments_: JsonObject): Promise<ToolResult> {
        const tool = tools.get(toolName);
        if (!tool) {
          return ToolResultHelpers.error(`Tool '${toolName}' not found`);
        }

        try {
          return await tool.handler(arguments_);
        } catch (e) {
          const message = e instanceof Error ? e.message : String(e);
          return ToolResultHelpers.error(`Tool execution failed: ${message}`);
        }
      },
    };
  }
}

/**
 * Create a multi-tool server builder.
 *
 * @example
 * ```typescript
 * const server = multiToolServer('calculator', 'A simple calculator')
 *   .addTool('add', 'Add two numbers', async (args) => {
 *     return ToolResultHelpers.success(`${(args.a as number) + (args.b as number)}`);
 *   }, {
 *     a: { type: 'number', description: 'First number' },
 *     b: { type: 'number', description: 'Second number' },
 *   })
 *   .addTool('subtract', 'Subtract two numbers', async (args) => {
 *     return ToolResultHelpers.success(`${(args.a as number) - (args.b as number)}`);
 *   }, {
 *     a: { type: 'number', description: 'First number' },
 *     b: { type: 'number', description: 'Second number' },
 *   })
 *   .build();
 * ```
 */
export function multiToolServer(
  name: string,
  description: string = '',
  version: string = '1.0.0'
): MultiToolServerBuilder {
  return new MultiToolServerBuilder(name, description, version);
}
