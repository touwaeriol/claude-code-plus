/**
 * Config Handler
 * Manages extension configuration
 */

import * as vscode from 'vscode';

export interface ClaudeCodePlusConfig {
    defaultBackendType: 'claude' | 'codex';
    defaultBypassPermissions: boolean;
    includePartialMessages: boolean;
    claude: {
        defaultModelId: string;
        defaultThinkingLevel: string;
        defaultThinkingTokens: number;
        defaultAutoCleanupContexts: boolean;
    };
    codex: {
        defaultModelId: string;
        reasoningEffort: string;
        reasoningSummary: string;
        sandboxMode: string;
        defaultAutoCleanupContexts: boolean;
    };
}

export class ConfigHandler {
    private static instance: ConfigHandler | null = null;
    private readonly configSection = 'claudeCodePlus';
    private disposables: vscode.Disposable[] = [];
    private changeListeners: Array<(config: ClaudeCodePlusConfig) => void> = [];

    private constructor() {
        // Listen for configuration changes
        this.disposables.push(
            vscode.workspace.onDidChangeConfiguration(e => {
                if (e.affectsConfiguration(this.configSection)) {
                    this.notifyListeners();
                }
            })
        );
    }

    static getInstance(): ConfigHandler {
        if (!ConfigHandler.instance) {
            ConfigHandler.instance = new ConfigHandler();
        }
        return ConfigHandler.instance;
    }

    /**
     * Get all configuration
     */
    getConfig(): ClaudeCodePlusConfig {
        const config = vscode.workspace.getConfiguration(this.configSection);
        
        return {
            defaultBackendType: config.get('defaultBackendType', 'claude'),
            defaultBypassPermissions: config.get('defaultBypassPermissions', false),
            includePartialMessages: config.get('includePartialMessages', true),
            claude: {
                defaultModelId: config.get('claude.defaultModelId', 'claude-opus-4-6'),
                defaultThinkingLevel: config.get('claude.defaultThinkingLevel', 'HIGH'),
                defaultThinkingTokens: config.get('claude.defaultThinkingTokens', 8192),
                defaultAutoCleanupContexts: config.get('claude.defaultAutoCleanupContexts', true),
            },
            codex: {
                defaultModelId: config.get('codex.defaultModelId', 'gpt-5.2-codex'),
                reasoningEffort: config.get('codex.reasoningEffort', 'medium'),
                reasoningSummary: config.get('codex.reasoningSummary', 'auto'),
                sandboxMode: config.get('codex.sandboxMode', 'workspace-write'),
                defaultAutoCleanupContexts: config.get('codex.defaultAutoCleanupContexts', true),
            }
        };
    }

    /**
     * Get a specific configuration value
     */
    get<T>(key: string, defaultValue: T): T {
        const config = vscode.workspace.getConfiguration(this.configSection);
        return config.get(key, defaultValue);
    }

    /**
     * Update a configuration value
     */
    async update(key: string, value: unknown, global: boolean = true): Promise<void> {
        const config = vscode.workspace.getConfiguration(this.configSection);
        const target = global ? vscode.ConfigurationTarget.Global : vscode.ConfigurationTarget.Workspace;
        await config.update(key, value, target);
    }

    /**
     * Add a change listener
     */
    onConfigChange(listener: (config: ClaudeCodePlusConfig) => void): vscode.Disposable {
        this.changeListeners.push(listener);
        return {
            dispose: () => {
                const index = this.changeListeners.indexOf(listener);
                if (index >= 0) {
                    this.changeListeners.splice(index, 1);
                }
            }
        };
    }

    private notifyListeners(): void {
        const config = this.getConfig();
        for (const listener of this.changeListeners) {
            listener(config);
        }
    }

    dispose(): void {
        for (const d of this.disposables) {
            d.dispose();
        }
        this.disposables = [];
        this.changeListeners = [];
        ConfigHandler.instance = null;
    }
}

export const configHandler = ConfigHandler.getInstance();
