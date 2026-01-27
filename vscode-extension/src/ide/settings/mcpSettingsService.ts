/**
 * MCP Settings Service
 * 
 * Manages MCP server configurations at global and project levels.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpSettingsService.kt
 */

import * as vscode from 'vscode';

const GLOBAL_CONFIG_KEY = 'claudeCodePlus.mcp.globalConfig';
const PROJECT_CONFIG_KEY = 'claudeCodePlus.mcp.projectConfig';

/**
 * MCP Settings Service
 * 
 * Manages global and project-level MCP configurations
 */
export class McpSettingsService {
    private static instance: McpSettingsService | undefined;
    private disposables: vscode.Disposable[] = [];

    private constructor() {
        // Listen for configuration changes
        this.disposables.push(
            vscode.workspace.onDidChangeConfiguration(e => {
                if (e.affectsConfiguration('claudeCodePlus.mcp')) {
                    // Notify listeners if needed
                }
            })
        );
    }

    static getInstance(): McpSettingsService {
        if (!this.instance) {
            this.instance = new McpSettingsService();
        }
        return this.instance;
    }

    /**
     * Get global MCP configuration
     */
    getGlobalConfig(): string {
        const config = vscode.workspace.getConfiguration('claudeCodePlus.mcp');
        return config.get<string>('globalConfig', '');
    }

    /**
     * Set global MCP configuration
     */
    async setGlobalConfig(value: string): Promise<void> {
        const config = vscode.workspace.getConfiguration('claudeCodePlus.mcp');
        await config.update('globalConfig', value, vscode.ConfigurationTarget.Global);
    }

    /**
     * Get project MCP configuration
     */
    getProjectConfig(): string {
        const config = vscode.workspace.getConfiguration('claudeCodePlus.mcp');
        return config.get<string>('projectConfig', '');
    }

    /**
     * Set project MCP configuration
     */
    async setProjectConfig(value: string): Promise<void> {
        const config = vscode.workspace.getConfiguration('claudeCodePlus.mcp');
        // Use workspace folder scope if available, otherwise global
        const target = vscode.workspace.workspaceFolders?.length
            ? vscode.ConfigurationTarget.WorkspaceFolder
            : vscode.ConfigurationTarget.Workspace;
        await config.update('projectConfig', value, target);
    }

    /**
     * Get merged MCP configuration
     * Priority: Project > Global
     */
    getMergedConfig(): string {
        const projectConfig = this.getProjectConfig();
        if (projectConfig && projectConfig.trim()) {
            return projectConfig;
        }

        const globalConfig = this.getGlobalConfig();
        if (globalConfig && globalConfig.trim()) {
            return globalConfig;
        }

        return '{}';
    }

    /**
     * Parse configuration as JSON
     */
    getGlobalConfigJson<T = unknown>(): T | null {
        try {
            const config = this.getGlobalConfig();
            return config ? JSON.parse(config) : null;
        } catch {
            return null;
        }
    }

    /**
     * Parse project configuration as JSON
     */
    getProjectConfigJson<T = unknown>(): T | null {
        try {
            const config = this.getProjectConfig();
            return config ? JSON.parse(config) : null;
        } catch {
            return null;
        }
    }

    /**
     * Parse merged configuration as JSON
     */
    getMergedConfigJson<T = unknown>(): T {
        try {
            const config = this.getMergedConfig();
            return JSON.parse(config);
        } catch {
            return {} as T;
        }
    }

    /**
     * Set global configuration from JSON object
     */
    async setGlobalConfigJson<T = unknown>(value: T): Promise<void> {
        await this.setGlobalConfig(JSON.stringify(value, null, 2));
    }

    /**
     * Set project configuration from JSON object
     */
    async setProjectConfigJson<T = unknown>(value: T): Promise<void> {
        await this.setProjectConfig(JSON.stringify(value, null, 2));
    }

    /**
     * Dispose resources
     */
    dispose(): void {
        this.disposables.forEach(d => d.dispose());
        this.disposables = [];
        McpSettingsService.instance = undefined;
    }
}

/**
 * Singleton instance
 */
export const mcpSettingsService = McpSettingsService.getInstance();

export default McpSettingsService;
