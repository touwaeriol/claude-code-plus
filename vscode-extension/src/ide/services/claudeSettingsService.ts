/**
 * Claude Settings Service
 * 
 * Manages Claude-related configuration (API Key, model, etc.)
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/ClaudeSettingsService.kt
 */

import * as vscode from 'vscode';

/**
 * Claude settings state
 */
export interface ClaudeSettings {
    apiKey?: string;
    model: string;
    maxTokens: number;
    temperature: number;
    maxTurns: number;
    autoApproveTools: boolean;
    useDarkTheme: boolean;
}

const DEFAULT_SETTINGS: ClaudeSettings = {
    apiKey: undefined,
    model: 'claude-sonnet-4-5-20250929',
    maxTokens: 4096,
    temperature: 0.7,
    maxTurns: 10,
    autoApproveTools: false,
    useDarkTheme: true
};

const CONFIG_KEY = 'claudeCodePlus';

/**
 * Claude Settings Service
 * 
 * Uses VS Code workspace configuration for persistence
 */
export class ClaudeSettingsService {
    private static instance: ClaudeSettingsService | undefined;
    private changeListeners: Set<(settings: ClaudeSettings) => void> = new Set();
    
    private constructor() {}
    
    static getInstance(): ClaudeSettingsService {
        if (!this.instance) {
            this.instance = new ClaudeSettingsService();
        }
        return this.instance;
    }
    
    /**
     * Get current settings
     */
    getSettings(): ClaudeSettings {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        return {
            apiKey: config.get('apiKey', DEFAULT_SETTINGS.apiKey),
            model: config.get('model', DEFAULT_SETTINGS.model),
            maxTokens: config.get('maxTokens', DEFAULT_SETTINGS.maxTokens),
            temperature: config.get('temperature', DEFAULT_SETTINGS.temperature),
            maxTurns: config.get('maxTurns', DEFAULT_SETTINGS.maxTurns),
            autoApproveTools: config.get('autoApproveTools', DEFAULT_SETTINGS.autoApproveTools),
            useDarkTheme: config.get('useDarkTheme', DEFAULT_SETTINGS.useDarkTheme)
        };
    }
    
    /**
     * Update settings
     */
    async updateSettings(settings: Partial<ClaudeSettings>): Promise<void> {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        
        for (const [key, value] of Object.entries(settings)) {
            if (value !== undefined) {
                await config.update(key, value, vscode.ConfigurationTarget.Workspace);
            }
        }
        
        this.notifyChange();
    }
    
    /**
     * Reset to defaults
     */
    async resetToDefaults(): Promise<void> {
        await this.updateSettings(DEFAULT_SETTINGS);
    }
    
    /**
     * Get API Key
     */
    getApiKey(): string | undefined {
        return this.getSettings().apiKey;
    }
    
    /**
     * Set API Key
     */
    async setApiKey(apiKey: string | undefined): Promise<void> {
        await this.updateSettings({ apiKey });
    }
    
    /**
     * Get model
     */
    getModel(): string {
        return this.getSettings().model;
    }
    
    /**
     * Set model
     */
    async setModel(model: string): Promise<void> {
        await this.updateSettings({ model });
    }
    
    /**
     * Add change listener
     */
    addChangeListener(listener: (settings: ClaudeSettings) => void): void {
        this.changeListeners.add(listener);
    }
    
    /**
     * Remove change listener
     */
    removeChangeListener(listener: (settings: ClaudeSettings) => void): void {
        this.changeListeners.delete(listener);
    }
    
    /**
     * Notify all listeners
     */
    private notifyChange(): void {
        const settings = this.getSettings();
        this.changeListeners.forEach(listener => listener(settings));
    }
}

// Export singleton instance
export const claudeSettingsService = ClaudeSettingsService.getInstance();
