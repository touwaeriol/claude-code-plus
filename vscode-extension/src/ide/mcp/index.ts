/**
 * MCP Module Entry Point
 * 
 * Exports all MCP servers and registry for VS Code extension.
 */

import { mcpLogger } from '../../logging/logger';

// Registry
export { 
    McpServerRegistry, 
    McpServerProvider, 
    McpServerConfig,
    McpServerBase,
    mcpRegistry,
    createToolResult 
} from './mcpServerRegistry';

// Git MCP
export { GitMcpServer, GitMcpServerProvider } from './git';

// Terminal MCP
export { TerminalMcpServer, TerminalMcpServerProvider } from './terminal';
export * from './terminal';

// JSON Value Access Utilities
export * from './jsonValueAccess';

// MCP Tools
export * from './tools';

// File MCP
export { FileMcpServer, FileMcpServerProvider } from './file';

// LSP MCP
export { LspMcpServer, LspMcpServerProvider } from './lsp';

/**
 * Initialize all MCP servers
 * 
 * Call this during extension activation to register all MCP servers.
 */
export async function initializeMcpServers(): Promise<void> {
    const { mcpRegistry } = await import('./mcpServerRegistry');
    
    mcpLogger.info('Initializing MCP servers...');

    // Import and register each MCP server provider
    // These will be enabled once subagents complete implementation

    try {
        // Git MCP
        const { GitMcpServerProvider } = await import('./git');
        mcpRegistry.registerProvider(new GitMcpServerProvider());
        mcpLogger.info('Registered Git MCP server');
    } catch (error) {
        mcpLogger.warn('Git MCP server not available', error instanceof Error ? error : undefined);
    }

    try {
        // Terminal MCP
        const { TerminalMcpServerProvider } = await import('./terminal');
        mcpRegistry.registerProvider(new TerminalMcpServerProvider());
        mcpLogger.info('Registered Terminal MCP server');
    } catch (error) {
        mcpLogger.warn('Terminal MCP server not available', error instanceof Error ? error : undefined);
    }

    try {
        // File MCP
        const { FileMcpServerProvider } = await import('./file');
        mcpRegistry.registerProvider(new FileMcpServerProvider());
        mcpLogger.info('Registered File MCP server');
    } catch (error) {
        mcpLogger.warn('File MCP server not available', error instanceof Error ? error : undefined);
    }

    try {
        // LSP MCP
        const { LspMcpServerProvider } = await import('./lsp');
        mcpRegistry.registerProvider(new LspMcpServerProvider());
        mcpLogger.info('Registered LSP MCP server');
    } catch (error) {
        mcpLogger.warn('LSP MCP server not available', error instanceof Error ? error : undefined);
    }

    // Initialize all registered servers
    await mcpRegistry.initializeAll();
    
    mcpLogger.info('All MCP servers initialized');
}

/**
 * Dispose all MCP servers
 * 
 * Call this during extension deactivation.
 */
export function disposeMcpServers(): void {
    const { mcpRegistry } = require('./mcpServerRegistry');
    mcpRegistry.dispose();
    mcpLogger.info('All MCP servers disposed');
}

/**
 * Get list of tools that should be disallowed from Claude CLI
 * when MCP servers provide equivalent functionality.
 */
export function getDisallowedBuiltinTools(): string[] {
    const { mcpRegistry } = require('./mcpServerRegistry');
    return mcpRegistry.getDisallowedBuiltinTools();
}
