/**
 * MCP Server Registry - VS Code Extension
 * 
 * Manages MCP server instances and their lifecycle.
 * Translated from JetBrains plugin's MCP server management.
 */

import * as vscode from 'vscode';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';

/**
 * MCP Server Provider interface
 */
export interface McpServerProvider {
    name: string;
    getServer(): McpServer;
    getDisallowedBuiltinTools?(): string[];
    dispose?(): void;
}

/**
 * MCP Server Status Info
 */
export interface McpServerStatusInfo {
    name: string;
    status: 'connected' | 'disconnected' | 'error' | 'unknown';
    serverInfo?: string;
    toolsCount: number;
}

/**
 * MCP Tool Info
 */
export interface McpToolInfo {
    name: string;
    description: string;
    serverName: string;
    inputSchema?: Record<string, unknown>;
}

/**
 * MCP Server Registry
 * 
 * Centralized management for all MCP servers in the VS Code extension.
 */
export class McpServerRegistry {
    private static instance: McpServerRegistry | null = null;
    private providers: Map<string, McpServerProvider> = new Map();
    private initialized: boolean = false;

    private constructor() {}

    /**
     * Get singleton instance
     */
    static getInstance(): McpServerRegistry {
        if (!McpServerRegistry.instance) {
            McpServerRegistry.instance = new McpServerRegistry();
        }
        return McpServerRegistry.instance;
    }

    /**
     * Register an MCP server provider
     */
    registerProvider(provider: McpServerProvider): void {
        if (this.providers.has(provider.name)) {
            console.warn(`[MCP Registry] Provider ${provider.name} already registered, replacing...`);
        }
        this.providers.set(provider.name, provider);
        console.log(`[MCP Registry] Registered provider: ${provider.name}`);
    }

    /**
     * Unregister an MCP server provider
     */
    unregisterProvider(name: string): void {
        const provider = this.providers.get(name);
        if (provider) {
            provider.dispose?.();
            this.providers.delete(name);
            console.log(`[MCP Registry] Unregistered provider: ${name}`);
        }
    }

    /**
     * Get a registered provider by name
     */
    getProvider(name: string): McpServerProvider | undefined {
        return this.providers.get(name);
    }

    /**
     * Get all registered providers
     */
    getAllProviders(): McpServerProvider[] {
        return Array.from(this.providers.values());
    }

    /**
     * Get all MCP servers
     */
    getAllServers(): McpServer[] {
        return this.getAllProviders().map(p => p.getServer());
    }

    /**
     * Get all disallowed builtin tools from all providers
     */
    getDisallowedBuiltinTools(): string[] {
        const tools: string[] = [];
        for (const provider of this.providers.values()) {
            const disallowed = provider.getDisallowedBuiltinTools?.() ?? [];
            tools.push(...disallowed);
        }
        return [...new Set(tools)]; // Remove duplicates
    }

    /**
     * Get status of all MCP servers
     */
    async getMcpStatus(): Promise<McpServerStatusInfo[]> {
        const statusList: McpServerStatusInfo[] = [];
        
        for (const [name, provider] of this.providers) {
            try {
                const server = provider.getServer();
                // MCP servers are always "connected" if registered
                const tools = await this.getServerTools(name);
                statusList.push({
                    name,
                    status: 'connected',
                    serverInfo: `VS Code IDE MCP Server`,
                    toolsCount: tools.length
                });
            } catch (error) {
                statusList.push({
                    name,
                    status: 'error',
                    serverInfo: error instanceof Error ? error.message : 'Unknown error',
                    toolsCount: 0
                });
            }
        }
        
        return statusList;
    }

    /**
     * Get tools from a specific server
     */
    private async getServerTools(serverName: string): Promise<McpToolInfo[]> {
        const provider = this.providers.get(serverName);
        if (!provider) {
            return [];
        }
        
        try {
            const server = provider.getServer();
            // The MCP SDK server has a _registeredTools map that we can access
            // through the server's internal state
            const tools: McpToolInfo[] = [];
            
            // Access the server's registered tools through reflection
            // The McpServer from @modelcontextprotocol/sdk stores tools internally
            const serverAny = server as any;
            if (serverAny._registeredTools && typeof serverAny._registeredTools === 'object') {
                for (const [toolName, toolData] of Object.entries(serverAny._registeredTools)) {
                    const data = toolData as { description?: string; inputSchema?: unknown };
                    tools.push({
                        name: toolName,
                        description: data.description || '',
                        serverName,
                        inputSchema: data.inputSchema as Record<string, unknown> | undefined
                    });
                }
            }
            
            return tools;
        } catch (error) {
            console.error(`[MCP Registry] Failed to get tools from ${serverName}:`, error);
            return [];
        }
    }

    /**
     * Get all MCP tools, optionally filtered by server name
     */
    async getMcpTools(serverName?: string): Promise<{ serverName?: string; tools: McpToolInfo[]; count: number }> {
        if (serverName) {
            const tools = await this.getServerTools(serverName);
            return { serverName, tools, count: tools.length };
        }
        
        // Get tools from all servers
        const allTools: McpToolInfo[] = [];
        for (const [name] of this.providers) {
            const serverTools = await this.getServerTools(name);
            allTools.push(...serverTools);
        }
        
        return { tools: allTools, count: allTools.length };
    }

    /**
     * Reconnect a specific MCP server
     * 
     * For VS Code extension, this reinitializes the server.
     */
    async reconnectMcp(serverName: string): Promise<{
        success: boolean;
        serverName: string;
        status?: string;
        toolsCount: number;
        error?: string;
    }> {
        const provider = this.providers.get(serverName);
        if (!provider) {
            return {
                success: false,
                serverName,
                toolsCount: 0,
                error: `Server '${serverName}' not found`
            };
        }
        
        try {
            // For VS Code MCP servers, "reconnect" means re-fetching the server
            // and ensuring it's ready
            const server = provider.getServer();
            const tools = await this.getServerTools(serverName);
            
            console.log(`[MCP Registry] Reconnected server: ${serverName}, tools: ${tools.length}`);
            
            return {
                success: true,
                serverName,
                status: 'connected',
                toolsCount: tools.length
            };
        } catch (error) {
            console.error(`[MCP Registry] Failed to reconnect ${serverName}:`, error);
            return {
                success: false,
                serverName,
                toolsCount: 0,
                error: error instanceof Error ? error.message : 'Unknown error'
            };
        }
    }

    /**
     * Initialize all registered MCP servers
     */
    async initializeAll(): Promise<void> {
        if (this.initialized) {
            console.log('[MCP Registry] Already initialized');
            return;
        }

        console.log(`[MCP Registry] Initializing ${this.providers.size} MCP servers...`);
        
        for (const [name, provider] of this.providers) {
            try {
                const server = provider.getServer();
                // Note: MCP SDK servers initialize when transport connects
                console.log(`[MCP Registry] Prepared server: ${name}`);
            } catch (error) {
                console.error(`[MCP Registry] Failed to initialize ${name}:`, error);
            }
        }

        this.initialized = true;
        console.log('[MCP Registry] All MCP servers initialized');
    }

    /**
     * Dispose all MCP servers
     */
    dispose(): void {
        console.log('[MCP Registry] Disposing all MCP servers...');
        for (const [name, provider] of this.providers) {
            try {
                provider.dispose?.();
                console.log(`[MCP Registry] Disposed: ${name}`);
            } catch (error) {
                console.error(`[MCP Registry] Failed to dispose ${name}:`, error);
            }
        }
        this.providers.clear();
        this.initialized = false;
        McpServerRegistry.instance = null;
    }
}

/**
 * MCP Server Configuration
 */
export interface McpServerConfig {
    name: string;
    version: string;
    description: string;
}

/**
 * Base class for MCP server implementations
 */
export abstract class McpServerBase {
    protected server: McpServer;
    protected config: McpServerConfig;

    constructor(config: McpServerConfig) {
        this.config = config;
        this.server = new McpServer({
            name: config.name,
            version: config.version
        });
    }

    /**
     * Get the MCP server instance
     */
    getServer(): McpServer {
        return this.server;
    }

    /**
     * Get system prompt appendix for this MCP server
     */
    abstract getSystemPromptAppendix(): string;

    /**
     * Get list of auto-approved tools
     */
    abstract getAllowedTools(): string[];

    /**
     * Initialize the server (register tools)
     */
    abstract initialize(): Promise<void>;

    /**
     * Dispose resources
     */
    dispose(): void {
        // Base implementation - override if needed
    }
}

/**
 * Helper to create MCP tool result
 */
export function createToolResult(content: string | object, isError: boolean = false): {
    content: Array<{ type: 'text'; text: string }>;
    isError?: boolean;
} {
    const text = typeof content === 'string' ? content : JSON.stringify(content, null, 2);
    return {
        content: [{ type: 'text', text }],
        ...(isError ? { isError: true } : {})
    };
}

/**
 * Export singleton instance
 */
export const mcpRegistry = McpServerRegistry.getInstance();
