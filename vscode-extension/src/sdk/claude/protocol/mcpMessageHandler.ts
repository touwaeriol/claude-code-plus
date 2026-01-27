/**
 * MCP Message Handler
 * 
 * Handler for MCP (Model Context Protocol) messages.
 * Processes initialize, tools/list, tools/call, and notifications/initialized requests.
 */

/**
 * MCP tool definition.
 */
export interface McpToolDefinition {
  name: string;
  description: string;
  inputSchema: unknown;
}

/**
 * MCP tool result content item.
 */
export interface McpContentItem {
  type: 'text' | 'json' | 'binary';
  text?: string;
  data?: unknown;
  mimeType?: string;
}

/**
 * MCP tool result.
 */
export type McpToolResult =
  | { success: true; content: McpContentItem[]; metadata?: Record<string, unknown> }
  | { success: false; error: string; code: number };

/**
 * MCP Server interface.
 * Implementations should provide tool listing and tool execution capabilities.
 */
export interface McpServer {
  /** Server name */
  name: string;
  /** Server version */
  version: string;
  /** Server description */
  description?: string;

  /**
   * List available tools.
   */
  listTools(): Promise<McpToolDefinition[]>;

  /**
   * Call a tool with the given arguments.
   */
  callToolJson(toolName: string, args: Record<string, unknown>): Promise<McpToolResult>;
}

/**
 * Handler for MCP (Model Context Protocol) messages.
 * Processes initialize, tools/list, tools/call, and notifications/initialized requests.
 */
export class McpMessageHandler {
  /**
   * Handle MCP server method invocations.
   * Routes to appropriate handler based on the method name.
   *
   * @param server The MCP server instance
   * @param method The method being called (e.g., "initialize", "tools/list", "tools/call")
   * @param params The parameters for the method call
   * @param id The JSON-RPC request ID
   * @returns JSON-RPC formatted response
   */
  async handleMethod(
    server: McpServer,
    method: string | undefined,
    params: Record<string, unknown>,
    id: unknown
  ): Promise<unknown> {
    switch (method) {
      case 'initialize':
        return this.handleInitialize(server, id);
      case 'tools/list':
        return this.handleToolsList(server, id);
      case 'tools/call':
        return this.handleToolsCall(server, params, id);
      case 'notifications/initialized':
        return this.handleNotificationsInitialized();
      default:
        return this.handleMethodNotFound(method, id);
    }
  }

  private handleInitialize(server: McpServer, id: unknown): unknown {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: {
          tools: {},
        },
        serverInfo: {
          name: server.name,
          version: server.version,
          description: server.description,
        },
      },
    };
  }

  private async handleToolsList(server: McpServer, id: unknown): Promise<unknown> {
    const tools = await server.listTools();
    return {
      jsonrpc: '2.0',
      id,
      result: {
        tools: tools.map((tool) => ({
          name: tool.name,
          description: tool.description,
          inputSchema: tool.inputSchema,
        })),
      },
    };
  }

  private async handleToolsCall(
    server: McpServer,
    params: Record<string, unknown>,
    id: unknown
  ): Promise<unknown> {
    const toolName = params.name as string | undefined;

    if (!toolName) {
      return {
        jsonrpc: '2.0',
        id,
        error: {
          code: -32602,
          message: 'Missing required parameter: name',
        },
      };
    }

    const argumentsJson = (params.arguments as Record<string, unknown>) ?? {};

    console.log(`[McpMessageHandler] Calling tool: ${toolName}, args:`, argumentsJson);

    const result = await server.callToolJson(toolName, argumentsJson);

    if (result.success) {
      return this.buildSuccessResponse(result, id);
    } else {
      return this.buildErrorResponse(result as Extract<McpToolResult, { success: false }>, id);
    }
  }

  private buildSuccessResponse(
    result: Extract<McpToolResult, { success: true }>,
    id: unknown
  ): unknown {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        content: result.content.map((contentItem) => {
          switch (contentItem.type) {
            case 'text':
              return {
                type: 'text',
                text: contentItem.text,
              };
            case 'json':
              return {
                type: 'text',
                text: JSON.stringify(contentItem.data),
              };
            case 'binary':
              return {
                type: 'resource',
                mimeType: contentItem.mimeType,
                data:
                  contentItem.data instanceof Uint8Array
                    ? Buffer.from(contentItem.data).toString('base64')
                    : String(contentItem.data),
              };
            default:
              return {
                type: 'text',
                text: String(contentItem.text ?? contentItem.data ?? ''),
              };
          }
        }),
        ...(result.metadata && Object.keys(result.metadata).length > 0
          ? { meta: result.metadata }
          : {}),
      },
    };
  }

  private buildErrorResponse(
    result: Extract<McpToolResult, { success: false }>,
    id: unknown
  ): unknown {
    return {
      jsonrpc: '2.0',
      id,
      error: {
        code: result.code,
        message: result.error,
      },
    };
  }

  private handleNotificationsInitialized(): unknown {
    return {
      jsonrpc: '2.0',
      result: {},
    };
  }

  private handleMethodNotFound(method: string | undefined, id: unknown): unknown {
    return {
      jsonrpc: '2.0',
      id,
      error: {
        code: -32601,
        message: `Method '${method}' not found`,
      },
    };
  }

  /**
   * Build a JSON-RPC error response for server not found.
   */
  buildServerNotFoundError(serverName: string, messageId: unknown): unknown {
    return {
      jsonrpc: '2.0',
      id: messageId,
      error: {
        code: -32601,
        message: `Server '${serverName}' not found`,
      },
    };
  }

  /**
   * Build a JSON-RPC error response for internal errors.
   */
  buildInternalError(errorMessage: string, messageId: unknown): unknown {
    return {
      jsonrpc: '2.0',
      id: messageId,
      error: {
        code: -32603,
        message: errorMessage,
      },
    };
  }
}
