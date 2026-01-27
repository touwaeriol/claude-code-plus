/**
 * MCP Server Base Class - Abstract base for MCP server implementations
 * 
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/mcp/McpServerBase.kt
 * 
 * Provides both decorator-based auto tool registration and manual tool registration.
 * Users can extend this class, use @McpTool decorator to mark tool methods, or manually register tools.
 * 
 * Usage example:
 * ```typescript
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

import {
    McpServer,
    ToolDefinition,
    ToolResult,
    ToolResultHelper,
    ParameterType,
    ParameterInfo,
    ToolHandlerBase,
    ToolHandlerWithParams,
    ToolHandlerWithSchema,
    JsonSchema,
    ContentItemHelper
} from './mcpServer';

import {
    getMcpServerConfig,
    getMcpToolMethods,
    getMcpToolMetadata,
    getAllToolParamMetadata,
    ToolParamMetadata
} from './decorators';

import {
    withToolUseContextAsync
} from './toolUseContext';

/**
 * Logger interface for MCP server
 */
export interface McpLogger {
    info(message: string): void;
    debug(message: string): void;
    warn(message: string): void;
    error(message: string, error?: Error): void;
}

/**
 * Default console logger
 */
const defaultLogger: McpLogger = {
    info: (msg) => console.log(`[MCP] ${msg}`),
    debug: (msg) => console.debug(`[MCP] ${msg}`),
    warn: (msg) => console.warn(`[MCP] ${msg}`),
    error: (msg, err) => console.error(`[MCP] ${msg}`, err)
};

/**
 * MCP Server Abstract Base Class
 * 
 * Provides decorator-based auto tool registration and manual tool registration.
 */
export abstract class McpServerBase implements McpServer {
    protected logger: McpLogger;
    private registeredTools: Map<string, ToolHandlerBase> = new Map();
    private initialized: boolean = false;

    // Get server config from decorator
    private serverConfig = getMcpServerConfig(Object.getPrototypeOf(this));

    // Implement McpServer interface
    readonly name: string;
    readonly version: string;
    readonly description: string;
    readonly timeout?: number | null;
    readonly resetTimeoutOnProgress?: boolean;
    readonly progressReportingEnabled?: boolean;

    constructor(logger?: McpLogger) {
        this.logger = logger ?? defaultLogger;
        
        // Initialize from decorator config or defaults
        const config = this.serverConfig;
        this.name = config?.name || this.constructor.name || 'unknown';
        this.version = config?.version || '1.0.0';
        this.description = config?.description || '';
    }

    /**
     * Ensure server is initialized
     */
    private async ensureInitialized(): Promise<void> {
        if (!this.initialized) {
            this.logger.info(`Initializing MCP Server: ${this.name}`);

            // Scan and register decorated tools
            this.scanAndRegisterDecoratedTools();

            // Call user-defined initialization
            await this.onInitialize();

            this.initialized = true;
            this.logger.info(`MCP Server '${this.name}' initialized, registered ${this.registeredTools.size} tools`);
        }
    }

    /**
     * User-overridable initialization method
     */
    protected async onInitialize(): Promise<void> {
        // Default empty implementation, subclasses can override for custom initialization
    }

    /**
     * Scan and register all methods with @McpTool decorator
     */
    private scanAndRegisterDecoratedTools(): void {
        const prototype = Object.getPrototypeOf(this);
        const toolMethods = getMcpToolMethods(prototype);

        for (const methodName of toolMethods) {
            const toolMeta = getMcpToolMetadata(prototype, methodName);
            if (toolMeta) {
                this.registerDecoratedTool(methodName, toolMeta.description);
            }
        }

        this.logger.info(`Scanned ${this.registeredTools.size} tools from decorators`);
    }

    /**
     * Register a decorated tool method
     */
    private registerDecoratedTool(methodName: string, description: string): void {
        const prototype = Object.getPrototypeOf(this);
        const method = (this as unknown as Record<string, Function>)[methodName];
        
        if (typeof method !== 'function') {
            this.logger.warn(`Method '${methodName}' not found on class`);
            return;
        }

        // Build parameter schema from decorators
        const parameterSchema = this.buildParameterSchemaFromDecorators(methodName);

        // Create tool handler
        const handler = new ToolHandlerWithParams(
            methodName,
            description,
            parameterSchema,
            async (arguments_) => {
                const result = await this.invokeDecoratedFunction(methodName, arguments_);
                return this.wrapToolResult(result);
            }
        );

        this.registeredTools.set(methodName, handler);
        this.logger.info(`Registered tool: ${methodName} - ${description}`);
    }

    /**
     * Build parameter schema from decorator metadata
     */
    private buildParameterSchemaFromDecorators(methodName: string): Record<string, ParameterInfo> | null {
        const prototype = Object.getPrototypeOf(this);
        const paramMetas = getAllToolParamMetadata(prototype, methodName);
        
        if (paramMetas.size === 0) {
            return null;
        }

        const schema: Record<string, ParameterInfo> = {};
        
        // Get parameter names from function signature
        const method = (this as unknown as Record<string, Function>)[methodName];
        const paramNames = this.getParameterNames(method);

        paramMetas.forEach((meta, index) => {
            const paramName = paramNames[index] || `param${index}`;
            schema[paramName] = {
                type: meta.type || ParameterType.STRING,
                description: meta.description
            };
        });

        return schema;
    }

    /**
     * Extract parameter names from function
     */
    private getParameterNames(fn: Function): string[] {
        const fnStr = fn.toString();
        const result = fnStr.match(/\(([^)]*)\)/);
        if (!result || !result[1]) {
            return [];
        }
        return result[1]
            .split(',')
            .map(param => param.trim().split(/[=:]/)[0].trim())
            .filter(name => name.length > 0);
    }

    /**
     * Invoke a decorated function with arguments
     */
    private async invokeDecoratedFunction(
        methodName: string,
        arguments_: Record<string, unknown>
    ): Promise<unknown> {
        const method = (this as unknown as Record<string, Function>)[methodName];
        const paramNames = this.getParameterNames(method);
        
        // Build argument array in correct order
        const args: unknown[] = paramNames.map(name => {
            const value = arguments_[name];
            return value;
        });

        try {
            return await method.apply(this, args);
        } catch (error) {
            this.logger.error(`Tool call failed: ${methodName}`, error instanceof Error ? error : undefined);
            throw error;
        }
    }

    /**
     * Wrap any result into ToolResult
     */
    protected wrapToolResult(result: unknown): ToolResult {
        if (this.isToolResult(result)) {
            return result;
        }
        if (result === null || result === undefined) {
            return ToolResultHelper.success('Operation completed');
        }
        if (typeof result === 'string') {
            return ToolResultHelper.success(result);
        }
        return ToolResultHelper.successJson(result);
    }

    /**
     * Type guard for ToolResult
     */
    private isToolResult(value: unknown): value is ToolResult {
        return (
            typeof value === 'object' &&
            value !== null &&
            'isError' in value &&
            'content' in value
        );
    }

    // ========================================================================
    // Manual Tool Registration
    // ========================================================================

    /**
     * Manually register a tool (for non-decorator scenarios)
     */
    protected registerTool(
        name: string,
        description: string,
        parameterSchema?: Record<string, ParameterInfo> | null,
        handler: (arguments_: Record<string, unknown>) => Promise<ToolResult> = async () => ToolResultHelper.success('OK')
    ): void {
        const toolHandler = new ToolHandlerWithParams(
            name,
            description,
            parameterSchema ?? null,
            handler
        );

        this.registeredTools.set(name, toolHandler);
        this.logger.info(`Manually registered tool: ${name} - ${description}`);
    }

    /**
     * Manually register a tool with parameter types (legacy compatibility)
     */
    protected registerToolWithTypes(
        name: string,
        description: string,
        parameterTypes?: Record<string, ParameterType> | null,
        handler: (arguments_: Record<string, unknown>) => Promise<ToolResult> = async () => ToolResultHelper.success('OK')
    ): void {
        const parameterSchema = parameterTypes
            ? Object.fromEntries(
                Object.entries(parameterTypes).map(([key, type]) => [
                    key,
                    { type } as ParameterInfo
                ])
            )
            : null;

        this.registerTool(name, description, parameterSchema, handler);
    }

    /**
     * Manually register a tool with full JSON schema
     * 
     * Usage example:
     * ```typescript
     * registerToolWithSchema(
     *     'AskUserQuestion',
     *     'Ask user a question',
     *     {
     *         type: 'object',
     *         properties: {
     *             questions: {
     *                 type: 'array',
     *                 description: 'List of questions',
     *                 items: {
     *                     type: 'object',
     *                     properties: {
     *                         question: { type: 'string' },
     *                         header: { type: 'string' },
     *                         options: { type: 'array' }
     *                     },
     *                     required: ['question', 'header', 'options']
     *                 }
     *             }
     *         },
     *         required: ['questions']
     *     },
     *     async (arguments_) => { ... }
     * );
     * ```
     */
    protected registerToolWithSchema(
        name: string,
        description: string,
        inputSchema: JsonSchema,
        handler: (arguments_: Record<string, unknown>) => Promise<ToolResult>
    ): void {
        const toolHandler = new ToolHandlerWithSchema(
            name,
            description,
            inputSchema,
            handler
        );

        this.registeredTools.set(name, toolHandler);
        this.logger.info(`Manually registered tool (full schema): ${name} - ${description}`);
    }

    /**
     * Register tool from schema (auto-extract description)
     * 
     * Schema should contain "description" field. If missing, uses empty string.
     * This method auto-extracts description from inputSchema to avoid duplication.
     * 
     * Usage example:
     * ```typescript
     * registerToolFromSchema('CodeSearch', codeSearchTool.getInputSchema(), async (arguments_) => {
     *     return codeSearchTool.execute(arguments_);
     * });
     * ```
     */
    protected registerToolFromSchema(
        name: string,
        inputSchema: JsonSchema,
        handler: (arguments_: Record<string, unknown>) => Promise<ToolResult>
    ): void {
        const description = typeof inputSchema.description === 'string'
            ? inputSchema.description
            : '';

        const toolHandler = new ToolHandlerWithSchema(
            name,
            description,
            inputSchema,
            handler
        );

        this.registeredTools.set(name, toolHandler);
        this.logger.info(`Registered tool (from schema): ${name} - ${description}`);
    }

    // ========================================================================
    // McpServer Interface Implementation
    // ========================================================================

    /**
     * Get system prompt appendix (override in subclasses)
     */
    getSystemPromptAppendix(): string | null {
        return null;
    }

    /**
     * Get list of auto-approved tools (override in subclasses)
     */
    getAllowedTools(): string[] {
        return [];
    }

    /**
     * List all available tools
     */
    async listTools(): Promise<ToolDefinition[]> {
        await this.ensureInitialized();

        return Array.from(this.registeredTools.values()).map(handler =>
            handler.toDefinition()
        );
    }

    /**
     * Call specified tool
     */
    async callTool(toolName: string, arguments_: Record<string, unknown>): Promise<ToolResult> {
        await this.ensureInitialized();

        const handler = this.registeredTools.get(toolName);
        if (!handler) {
            return ToolResultHelper.error(`Tool '${toolName}' not found`);
        }

        try {
            this.logger.info(`Calling tool: ${toolName}, arguments: ${JSON.stringify(arguments_)}`);
            return await handler.handler(arguments_);
        } catch (error) {
            this.logger.error(`Tool '${toolName}' execution failed`, error instanceof Error ? error : undefined);
            return ToolResultHelper.error(`Tool execution failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Call specified tool with context (toolUseId)
     * 
     * Uses ToolUseContext to pass toolUseId in async context,
     * tools can get it via currentToolUseId().
     */
    async callToolWithContext(
        toolName: string,
        arguments_: Record<string, unknown>,
        toolUseId?: string | null
    ): Promise<ToolResult> {
        await this.ensureInitialized();

        const handler = this.registeredTools.get(toolName);
        if (!handler) {
            return ToolResultHelper.error(`Tool '${toolName}' not found`);
        }

        try {
            this.logger.info(`Calling tool: ${toolName}, arguments: ${JSON.stringify(arguments_)}, toolUseId: ${toolUseId}`);
            return await withToolUseContextAsync(toolUseId, async () => {
                return handler.handler(arguments_);
            });
        } catch (error) {
            this.logger.error(`Tool '${toolName}' execution failed`, error instanceof Error ? error : undefined);
            return ToolResultHelper.error(`Tool execution failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Get tool statistics info
     */
    getToolsInfo(): Record<string, unknown> {
        return {
            server_name: this.name,
            server_version: this.version,
            description: this.description,
            tools_count: this.registeredTools.size,
            tools: Array.from(this.registeredTools.keys()),
            initialized: this.initialized
        };
    }

    /**
     * Check if a tool is registered
     */
    hasTool(toolName: string): boolean {
        return this.registeredTools.has(toolName);
    }

    /**
     * Get registered tool count
     */
    getToolCount(): number {
        return this.registeredTools.size;
    }

    /**
     * Force reinitialize (for testing or dynamic tool updates)
     */
    protected async reinitialize(): Promise<void> {
        this.registeredTools.clear();
        this.initialized = false;
        await this.ensureInitialized();
    }
}
