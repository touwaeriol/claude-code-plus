/**
 * Backend Settings Service
 * 
 * Manages Claude and Codex backend configurations
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/settings/BackendSettingsService.kt
 */

import * as vscode from 'vscode';
import { CodexSettings, codexSettings } from './codexSettings';

/**
 * Backend type enum
 */
export enum BackendType {
    CLAUDE = 'CLAUDE',
    CODEX = 'CODEX'
}

export const BackendTypeInfo: Record<BackendType, { displayName: string; icon: string }> = {
    [BackendType.CLAUDE]: { displayName: 'Claude', icon: '🤖' },
    [BackendType.CODEX]: { displayName: 'Codex', icon: '🔧' }
};

/**
 * Backend availability status
 */
export interface BackendAvailability {
    type: string;
    available: boolean;
    reason?: string;
}

/**
 * Backend config DTO (for frontend push)
 */
export interface BackendConfigDto {
    type: string;
    enabled: boolean;
    modelId?: string;
    modelProvider?: string;
    sandboxMode?: string;
    thinkingEnabled?: boolean;
    thinkingTokenBudget?: number;
    reasoningEffort?: string;
    reasoningSummary?: string;
}

/**
 * Backend settings state
 */
export interface BackendSettingsState {
    // Default backend type
    defaultBackend: string;
    
    // Claude config
    claudeEnabled: boolean;
    claudeModelId: string;
    claudeThinkingEnabled: boolean;
    claudeThinkingTokenBudget: number;
    claudeIncludePartialMessages: boolean;
    
    // Codex config
    codexEnabled: boolean;
    codexModelId: string;
    codexModelProvider: string;
    codexSandboxMode: string;
    codexReasoningEffort: string;
    codexReasoningSummary: string;
}

const DEFAULT_STATE: BackendSettingsState = {
    defaultBackend: BackendType.CLAUDE,
    claudeEnabled: true,
    claudeModelId: 'claude-sonnet-4-5-20250929',
    claudeThinkingEnabled: true,
    claudeThinkingTokenBudget: 8096,
    claudeIncludePartialMessages: true,
    codexEnabled: false,
    codexModelId: 'gpt-5.2-codex',
    codexModelProvider: 'openai',
    codexSandboxMode: 'workspace-write',
    codexReasoningEffort: 'xhigh',
    codexReasoningSummary: 'auto'
};

const CONFIG_KEY = 'claudeCodePlus.backend';

/**
 * Unified Backend Settings Service
 * 
 * Manages Claude and Codex backend configurations
 */
export class BackendSettingsService {
    private static instance: BackendSettingsService | undefined;
    private changeListeners: Set<(service: BackendSettingsService) => void> = new Set();

    private constructor() {}

    static getInstance(): BackendSettingsService {
        if (!this.instance) {
            this.instance = new BackendSettingsService();
        }
        return this.instance;
    }

    // ==================== State Management ====================

    /**
     * Get current state
     */
    getState(): BackendSettingsState {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        return {
            defaultBackend: config.get('defaultBackend', DEFAULT_STATE.defaultBackend),
            claudeEnabled: config.get('claudeEnabled', DEFAULT_STATE.claudeEnabled),
            claudeModelId: config.get('claudeModelId', DEFAULT_STATE.claudeModelId),
            claudeThinkingEnabled: config.get('claudeThinkingEnabled', DEFAULT_STATE.claudeThinkingEnabled),
            claudeThinkingTokenBudget: config.get('claudeThinkingTokenBudget', DEFAULT_STATE.claudeThinkingTokenBudget),
            claudeIncludePartialMessages: config.get('claudeIncludePartialMessages', DEFAULT_STATE.claudeIncludePartialMessages),
            codexEnabled: config.get('codexEnabled', DEFAULT_STATE.codexEnabled),
            codexModelId: config.get('codexModelId', DEFAULT_STATE.codexModelId),
            codexModelProvider: config.get('codexModelProvider', DEFAULT_STATE.codexModelProvider),
            codexSandboxMode: config.get('codexSandboxMode', DEFAULT_STATE.codexSandboxMode),
            codexReasoningEffort: config.get('codexReasoningEffort', DEFAULT_STATE.codexReasoningEffort),
            codexReasoningSummary: config.get('codexReasoningSummary', DEFAULT_STATE.codexReasoningSummary)
        };
    }

    // ==================== Listener Management ====================

    /**
     * Add change listener
     */
    addChangeListener(listener: (service: BackendSettingsService) => void): void {
        this.changeListeners.add(listener);
    }

    /**
     * Remove change listener
     */
    removeChangeListener(listener: (service: BackendSettingsService) => void): void {
        this.changeListeners.delete(listener);
    }

    /**
     * Notify all listeners
     */
    notifyChange(): void {
        this.changeListeners.forEach(listener => listener(this));
    }

    // ==================== Availability Detection ====================

    /**
     * Check if Claude backend is available
     */
    isClaudeAvailable(): boolean {
        return this.getState().claudeEnabled;
    }

    /**
     * Check if Codex backend is available
     */
    isCodexAvailable(): boolean {
        const state = this.getState();
        if (!state.codexEnabled) return false;
        return codexSettings.isValid();
    }

    /**
     * Get all backend availability status
     */
    getBackendAvailability(): BackendAvailability[] {
        const state = this.getState();
        const result: BackendAvailability[] = [];

        // Claude
        result.push({
            type: BackendType.CLAUDE.toLowerCase(),
            available: this.isClaudeAvailable(),
            reason: !state.claudeEnabled ? 'Claude is disabled' : undefined
        });

        // Codex
        let codexReason: string | undefined;
        if (!state.codexEnabled) {
            codexReason = 'Codex is disabled';
        } else if (!codexSettings.binaryPath) {
            codexReason = 'Codex binary path not configured';
        } else if (!codexSettings.isValid()) {
            codexReason = 'Codex binary not found or not executable';
        }

        result.push({
            type: BackendType.CODEX.toLowerCase(),
            available: this.isCodexAvailable(),
            reason: codexReason
        });

        return result;
    }

    /**
     * Get availability as JSON string
     */
    getBackendAvailabilityJson(): string {
        return JSON.stringify(this.getBackendAvailability(), null, 2);
    }

    // ==================== Config Getters ====================

    /**
     * Get default backend type
     */
    getDefaultBackend(): BackendType {
        const value = this.getState().defaultBackend;
        return BackendType[value as keyof typeof BackendType] ?? BackendType.CLAUDE;
    }

    /**
     * Set default backend type
     */
    async setDefaultBackend(type: BackendType): Promise<void> {
        await this.updateSetting('defaultBackend', type);
    }

    /**
     * Get Claude backend config DTO
     */
    getClaudeConfigDto(): BackendConfigDto {
        const state = this.getState();
        return {
            type: 'claude',
            enabled: state.claudeEnabled,
            modelId: state.claudeModelId,
            thinkingEnabled: state.claudeThinkingEnabled,
            thinkingTokenBudget: state.claudeThinkingTokenBudget
        };
    }

    /**
     * Get Codex backend config DTO
     */
    getCodexConfigDto(): BackendConfigDto {
        const state = this.getState();
        return {
            type: 'codex',
            enabled: state.codexEnabled,
            modelId: state.codexModelId,
            modelProvider: codexSettings.getState().modelProvider.toLowerCase(),
            sandboxMode: state.codexSandboxMode,
            reasoningEffort: state.codexReasoningEffort,
            reasoningSummary: state.codexReasoningSummary
        };
    }

    /**
     * Get all configs as JSON
     */
    getAllConfigsJson(): string {
        const configs = {
            defaultBackend: this.getState().defaultBackend.toLowerCase(),
            claude: this.getClaudeConfigDto(),
            codex: this.getCodexConfigDto(),
            availability: this.getBackendAvailability()
        };
        return JSON.stringify(configs, null, 2);
    }

    // ==================== Config Updates ====================

    /**
     * Update Claude config
     */
    async updateClaudeConfig(options: {
        enabled?: boolean;
        modelId?: string;
        thinkingEnabled?: boolean;
        thinkingTokenBudget?: number;
        includePartialMessages?: boolean;
    }): Promise<void> {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        
        if (options.enabled !== undefined) {
            await config.update('claudeEnabled', options.enabled, vscode.ConfigurationTarget.Workspace);
        }
        if (options.modelId !== undefined) {
            await config.update('claudeModelId', options.modelId, vscode.ConfigurationTarget.Workspace);
        }
        if (options.thinkingEnabled !== undefined) {
            await config.update('claudeThinkingEnabled', options.thinkingEnabled, vscode.ConfigurationTarget.Workspace);
        }
        if (options.thinkingTokenBudget !== undefined) {
            await config.update('claudeThinkingTokenBudget', options.thinkingTokenBudget, vscode.ConfigurationTarget.Workspace);
        }
        if (options.includePartialMessages !== undefined) {
            await config.update('claudeIncludePartialMessages', options.includePartialMessages, vscode.ConfigurationTarget.Workspace);
        }
        
        this.notifyChange();
    }

    /**
     * Update Codex config
     */
    async updateCodexConfig(options: {
        enabled?: boolean;
        modelId?: string;
        modelProvider?: string;
        sandboxMode?: string;
        reasoningEffort?: string;
        reasoningSummary?: string;
    }): Promise<void> {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        
        if (options.enabled !== undefined) {
            await config.update('codexEnabled', options.enabled, vscode.ConfigurationTarget.Workspace);
        }
        if (options.modelId !== undefined) {
            await config.update('codexModelId', options.modelId, vscode.ConfigurationTarget.Workspace);
        }
        if (options.modelProvider !== undefined) {
            await config.update('codexModelProvider', options.modelProvider, vscode.ConfigurationTarget.Workspace);
        }
        if (options.sandboxMode !== undefined) {
            await config.update('codexSandboxMode', options.sandboxMode, vscode.ConfigurationTarget.Workspace);
        }
        if (options.reasoningEffort !== undefined) {
            await config.update('codexReasoningEffort', options.reasoningEffort, vscode.ConfigurationTarget.Workspace);
        }
        if (options.reasoningSummary !== undefined) {
            await config.update('codexReasoningSummary', options.reasoningSummary, vscode.ConfigurationTarget.Workspace);
        }
        
        this.notifyChange();
    }

    // ==================== Convenience Properties ====================

    get defaultBackend(): string {
        return this.getState().defaultBackend;
    }

    get claudeEnabled(): boolean {
        return this.getState().claudeEnabled;
    }

    async setClaudeEnabled(value: boolean): Promise<void> {
        await this.updateClaudeConfig({ enabled: value });
    }

    get codexEnabled(): boolean {
        return this.getState().codexEnabled;
    }

    async setCodexEnabled(value: boolean): Promise<void> {
        await this.updateCodexConfig({ enabled: value });
    }

    // ==================== Private Helpers ====================

    private async updateSetting<K extends keyof BackendSettingsState>(
        key: K,
        value: BackendSettingsState[K]
    ): Promise<void> {
        const config = vscode.workspace.getConfiguration(CONFIG_KEY);
        await config.update(key, value, vscode.ConfigurationTarget.Workspace);
        this.notifyChange();
    }
}

// Export singleton instance
export const backendSettingsService = BackendSettingsService.getInstance();
