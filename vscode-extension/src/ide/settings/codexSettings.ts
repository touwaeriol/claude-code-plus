/**
 * Codex Settings Service
 * 
 * Manages Codex configuration using VS Code workspace settings
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/settings/CodexSettings.kt
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Model Provider enum
 */
export enum ModelProvider {
    OPENAI = 'OPENAI',
    AZURE = 'AZURE',
    ANTHROPIC = 'ANTHROPIC'
}

export const ModelProviderDisplayName: Record<ModelProvider, string> = {
    [ModelProvider.OPENAI]: 'OpenAI',
    [ModelProvider.AZURE]: 'Azure OpenAI',
    [ModelProvider.ANTHROPIC]: 'Anthropic'
};

/**
 * Sandbox Mode enum
 */
export enum SandboxMode {
    WORKSPACE_WRITE = 'WORKSPACE_WRITE',
    WORKSPACE_READ = 'WORKSPACE_READ',
    NONE = 'NONE'
}

export const SandboxModeDisplayName: Record<SandboxMode, string> = {
    [SandboxMode.WORKSPACE_WRITE]: 'Workspace Write',
    [SandboxMode.WORKSPACE_READ]: 'Workspace Read Only',
    [SandboxMode.NONE]: 'No Sandbox'
};

/**
 * Codex Settings interface
 */
export interface CodexSettingsState {
    binaryPath: string;
    modelProvider: string;
    sandboxMode: string;
    enabled: boolean;
    lastTestResult: string;
}

const DEFAULT_SETTINGS: CodexSettingsState = {
    binaryPath: '',
    modelProvider: ModelProvider.OPENAI,
    sandboxMode: SandboxMode.WORKSPACE_WRITE,
    enabled: false,
    lastTestResult: ''
};

const CONFIG_KEY = 'claudeCodePlus.codex';

/**
 * Codex Settings Service
 * 
 * Uses VS Code workspace configuration for persistence
 */
export class CodexSettings {
    private static instance: CodexSettings | undefined;
    private changeListeners: Set<(settings: CodexSettings) => void> = new Set();

    private constructor() {}

    static getInstance(): CodexSettings {
        if (!this.instance) {
            this.instance = new CodexSettings();
        }
        return this.instance;
    }

    /**
     * Get current settings state
     */
    getState(): CodexSettingsState {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        return {
            binaryPath: config.get('binaryPath', DEFAULT_SETTINGS.binaryPath),
            modelProvider: config.get('modelProvider', DEFAULT_SETTINGS.modelProvider),
            sandboxMode: config.get('sandboxMode', DEFAULT_SETTINGS.sandboxMode),
            enabled: config.get('enabled', DEFAULT_SETTINGS.enabled),
            lastTestResult: config.get('lastTestResult', DEFAULT_SETTINGS.lastTestResult)
        };
    }

    /**
     * Get binary path
     */
    get binaryPath(): string {
        return this.getState().binaryPath;
    }

    /**
     * Set binary path
     */
    async setBinaryPath(value: string): Promise<void> {
        await this.updateSetting('binaryPath', value);
    }

    /**
     * Get model provider enum
     */
    getModelProviderEnum(): ModelProvider {
        const value = this.getState().modelProvider;
        return ModelProvider[value as keyof typeof ModelProvider] ?? ModelProvider.OPENAI;
    }

    /**
     * Set model provider
     */
    async setModelProvider(provider: ModelProvider): Promise<void> {
        await this.updateSetting('modelProvider', provider);
    }

    /**
     * Get sandbox mode enum
     */
    getSandboxModeEnum(): SandboxMode {
        const value = this.getState().sandboxMode;
        return SandboxMode[value as keyof typeof SandboxMode] ?? SandboxMode.WORKSPACE_WRITE;
    }

    /**
     * Set sandbox mode
     */
    async setSandboxMode(mode: SandboxMode): Promise<void> {
        await this.updateSetting('sandboxMode', mode);
    }

    /**
     * Get enabled status
     */
    get enabled(): boolean {
        return this.getState().enabled;
    }

    /**
     * Set enabled status
     */
    async setEnabled(value: boolean): Promise<void> {
        await this.updateSetting('enabled', value);
    }

    /**
     * Get last test result
     */
    get lastTestResult(): string {
        return this.getState().lastTestResult;
    }

    /**
     * Set last test result
     */
    async setLastTestResult(value: string): Promise<void> {
        await this.updateSetting('lastTestResult', value);
    }

    /**
     * Check if Codex configuration is valid
     */
    isValid(): boolean {
        const binaryPath = this.binaryPath;
        if (!binaryPath) {
            return false;
        }

        try {
            const stats = fs.statSync(binaryPath);
            return stats.isFile();
        } catch {
            return false;
        }
    }

    /**
     * Get configuration summary
     */
    getSummary(): string {
        const state = this.getState();
        const lines = [
            `Binary: ${state.binaryPath || '(not set)'}`,
            `Provider: ${ModelProviderDisplayName[this.getModelProviderEnum()]}`,
            `Sandbox: ${SandboxModeDisplayName[this.getSandboxModeEnum()]}`,
            `Enabled: ${state.enabled}`
        ];
        if (state.lastTestResult) {
            lines.push(`Last Test: ${state.lastTestResult}`);
        }
        return lines.join('\n');
    }

    /**
     * Add change listener
     */
    addChangeListener(listener: (settings: CodexSettings) => void): void {
        this.changeListeners.add(listener);
    }

    /**
     * Remove change listener
     */
    removeChangeListener(listener: (settings: CodexSettings) => void): void {
        this.changeListeners.delete(listener);
    }

    /**
     * Update a setting and notify listeners
     */
    private async updateSetting<K extends keyof CodexSettingsState>(
        key: K,
        value: CodexSettingsState[K]
    ): Promise<void> {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        await config.update(key, value, vscode.ConfigurationTarget.Workspace);
        this.notifyChange();
    }

    /**
     * Notify all listeners of change
     */
    private notifyChange(): void {
        this.changeListeners.forEach(listener => listener(this));
    }
}

// Export singleton instance
export const codexSettings = CodexSettings.getInstance();
