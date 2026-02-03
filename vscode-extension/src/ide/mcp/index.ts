/**
 * MCP Module Entry Point
 * 
 * Exports all MCP servers and registry for VS Code extension.
 */

import { mcpLogger } from '../../logging/logger';
import { McpConfigurable } from '../settings/configurables/McpConfigurable';

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

// User Interaction MCP
export { UserInteractionMcpServer, UserInteractionMcpServerProvider } from './userInteraction';

/**
 * Initialize all MCP servers
 * 
 * Call this during extension activation to register all MCP servers.
 * MCP servers are conditionally registered based on user settings.
 * 
 * IMPORTANT: Uses McpConfigurable to read settings from claudeCodePlus.mcp.*
 * This ensures consistency with getMcpServersFromSettings() which also uses McpConfigurable.
 */
export async function initializeMcpServers(): Promise<void> {
    const { mcpRegistry } = await import('./mcpServerRegistry');
    
    mcpLogger.info('Initializing MCP servers...');
    
    // Log all MCP settings for debugging
    mcpLogger.info(`MCP Settings: git=${McpConfigurable.getGitEnabled()}, terminal=${McpConfigurable.getTerminalEnabled()}, vscodeFile=${McpConfigurable.getVscodeFileEnabled()}, vscodeLsp=${McpConfigurable.getVscodeLspEnabled()}, userInteraction=${McpConfigurable.getUserInteractionEnabled()}, context7=${McpConfigurable.getContext7Enabled()}`);

    // Git MCP - conditionally register based on settings
    if (McpConfigurable.getGitEnabled()) {
        try {
            const { GitMcpServerProvider } = await import('./git');
            const provider = new GitMcpServerProvider();
            await provider.initialize();
            mcpRegistry.registerProvider(provider);
            mcpLogger.info('Registered Git MCP server');
        } catch (error) {
            mcpLogger.warn('Git MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('Git MCP server disabled by settings');
    }

    // Terminal MCP - conditionally register based on settings
    if (McpConfigurable.getTerminalEnabled()) {
        try {
            const { TerminalMcpServerProvider } = await import('./terminal');
            const provider = new TerminalMcpServerProvider();
            await provider.initialize();
            mcpRegistry.registerProvider(provider);
            mcpLogger.info('Registered Terminal MCP server');
        } catch (error) {
            mcpLogger.warn('Terminal MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('Terminal MCP server disabled by settings');
    }

    // File MCP - conditionally register based on settings (VS Code File)
    if (McpConfigurable.getVscodeFileEnabled()) {
        try {
            const { FileMcpServerProvider } = await import('./file');
            const provider = new FileMcpServerProvider();
            await provider.initialize();
            mcpRegistry.registerProvider(provider);
            mcpLogger.info('Registered File MCP server');
        } catch (error) {
            mcpLogger.warn('File MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('File MCP server disabled by settings');
    }

    // LSP MCP - conditionally register based on settings (VS Code LSP)
    if (McpConfigurable.getVscodeLspEnabled()) {
        try {
            const { LspMcpServerProvider } = await import('./lsp');
            const provider = new LspMcpServerProvider();
            await provider.initialize();
            mcpRegistry.registerProvider(provider);
            mcpLogger.info('Registered LSP MCP server');
        } catch (error) {
            mcpLogger.warn('LSP MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('LSP MCP server disabled by settings');
    }

    // User Interaction MCP - conditionally register based on settings
    if (McpConfigurable.getUserInteractionEnabled()) {
        try {
            const { UserInteractionMcpServerProvider } = await import('./userInteraction');
            const provider = new UserInteractionMcpServerProvider();
            await provider.initialize();
            mcpRegistry.registerProvider(provider);
            mcpLogger.info('Registered User Interaction MCP server');
        } catch (error) {
            mcpLogger.warn('User Interaction MCP server not available', error instanceof Error ? error : undefined);
        }
    } else {
        mcpLogger.info('User Interaction MCP server disabled by settings');
    }

    // Context7 MCP - External HTTP server, no local registration needed
    // Configuration is passed through getMcpServersFromSettings() -> buildMcpConfig()
    if (McpConfigurable.getContext7Enabled()) {
        mcpLogger.info('Context7 MCP enabled (external HTTP server, will be configured via --mcp-config)');
    } else {
        mcpLogger.info('Context7 MCP server disabled by settings');
    }

    // Initialize all registered servers
    await mcpRegistry.initializeAll();
    
    const registeredProviders = mcpRegistry.getAllProviders();
    mcpLogger.info(`All MCP servers initialized. Registered providers: ${registeredProviders.length} [${registeredProviders.map(p => p.name).join(', ')}]`);
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
