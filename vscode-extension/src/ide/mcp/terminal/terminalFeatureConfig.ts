/**
 * Terminal Feature Config
 * 
 * Shared configuration for Terminal MCP feature flags.
 * Used by both TerminalMcpServer and TerminalMcpServerProvider.
 * Translated from JetBrains plugin's TerminalFeatureConfig.kt
 */

import * as vscode from 'vscode';

/**
 * Terminal feature configuration
 */
export class TerminalFeatureConfig {
    private static instance: TerminalFeatureConfig | null = null;

    private constructor() {}

    /**
     * Get singleton instance
     */
    static getInstance(): TerminalFeatureConfig {
        if (!TerminalFeatureConfig.instance) {
            TerminalFeatureConfig.instance = new TerminalFeatureConfig();
        }
        return TerminalFeatureConfig.instance;
    }

    /**
     * Check if Terminal MCP is enabled
     */
    isTerminalMcpEnabled(): boolean {
        const config = vscode.workspace.getConfiguration('claudeCodePlus');
        return config.get<boolean>('terminal.enableMcp', true);
    }

    /**
     * Check if builtin Bash should be disabled
     */
    isBuiltinBashDisabled(): boolean {
        const config = vscode.workspace.getConfiguration('claudeCodePlus');
        return config.get<boolean>('terminal.disableBuiltinBash', false);
    }

    /**
     * Get list of builtin tools that should be disabled when Terminal MCP is enabled.
     * Currently disables the builtin "Bash" tool when terminalDisableBuiltinBash is true.
     * 
     * @returns List of tool names to disable (e.g., ["Bash"])
     */
    getDisallowedBuiltinTools(): string[] {
        if (this.isTerminalMcpEnabled() && this.isBuiltinBashDisabled()) {
            return ['Bash'];
        }
        return [];
    }

    /**
     * Get list of Codex features that should be disabled when Terminal MCP is enabled.
     * Currently disables "shell_tool" when terminalDisableBuiltinBash is true.
     * 
     * @returns List of feature names to disable (e.g., ["shell_tool"])
     */
    getCodexDisabledFeatures(): string[] {
        if (this.isTerminalMcpEnabled() && this.isBuiltinBashDisabled()) {
            return ['shell_tool'];
        }
        return [];
    }
}

// Export singleton instance for convenience
export const terminalFeatureConfig = TerminalFeatureConfig.getInstance();
