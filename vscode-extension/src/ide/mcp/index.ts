/**
 * MCP Module Entry Point
 * 
 * Exports all MCP servers and registry for VS Code extension.
 */

import { mcpLogger } from '../../logging/logger';
import { agentSettingsService } from '../settings';

// Registry
export { 
    McpServerRegistry, 
    McpServerProvider, 
    McpServerConfig,
    McpServerBase,
    McpServerStatusInfo,
    McpToolInfo,
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
 * MCP servers are conditionally registered based on user settings.
 */
export async function initializeMcpServers(): Promise<void> {
    const { mcpRegistry } = await import('./mcpServerRegistry');
    const settings = agentSettingsService;
    
    mcpLogger.info('Initializing MCP servers...');

    // Git MCP - conditionally register based on settings
    if (settings.enableGitMcp) {
        try {
            const { GitMcpServerProvider } = await import('./git');
            mcpRegistry.registerProvider(new GitMcpServerProvider());
            mcpLogger.info('Registered Git MCP server');
        } catch (error) {
            mcpLogger.warn('Git MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('Git MCP server disabled by settings');
    }

    // Terminal MCP - conditionally register based on settings
    if (settings.enableTerminalMcp) {
        try {
            const { TerminalMcpServerProvider } = await import('./terminal');
            mcpRegistry.registerProvider(new TerminalMcpServerProvider());
            mcpLogger.info('Registered Terminal MCP server');
        } catch (error) {
            mcpLogger.warn('Terminal MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('Terminal MCP server disabled by settings');
    }

    // File MCP - conditionally register based on settings (enableJetBrainsFileMcp)
    if (settings.enableJetBrainsFileMcp) {
        try {
            const { FileMcpServerProvider } = await import('./file');
            mcpRegistry.registerProvider(new FileMcpServerProvider());
            mcpLogger.info('Registered File MCP server');
        } catch (error) {
            mcpLogger.warn('File MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('File MCP server disabled by settings');
    }

    // LSP MCP - conditionally register based on settings (enableJetBrainsMcp)
    if (settings.enableJetBrainsMcp) {
        try {
            const { LspMcpServerProvider } = await import('./lsp');
            mcpRegistry.registerProvider(new LspMcpServerProvider());
            mcpLogger.info('Registered LSP MCP server');
        } catch (error) {
            mcpLogger.warn('LSP MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('LSP MCP server disabled by settings');
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
