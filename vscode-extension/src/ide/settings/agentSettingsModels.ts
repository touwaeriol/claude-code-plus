/**
 * Agent Settings Models
 * 
 * Type definitions and enums for agent settings.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/AgentSettingsModels.kt
 */

/**
 * Default thinking level enum
 * 
 * Simplified to three core levels: Off, Think, Ultra
 */
export enum DefaultThinkingLevel {
    OFF = 'OFF',
    THINK = 'THINK',
    ULTRA = 'ULTRA'
}

export const DefaultThinkingLevelInfo: Record<DefaultThinkingLevel, { displayName: string; description: string }> = {
    [DefaultThinkingLevel.OFF]: { displayName: 'Off', description: 'Disable extended thinking' },
    [DefaultThinkingLevel.THINK]: { displayName: 'Think', description: 'Standard thinking for most tasks' },
    [DefaultThinkingLevel.ULTRA]: { displayName: 'Ultra', description: 'Deep thinking for complex tasks' }
};

/**
 * Get DefaultThinkingLevel from string name
 */
export function getDefaultThinkingLevelFromName(name: string | null | undefined): DefaultThinkingLevel | undefined {
    if (!name) return undefined;
    const normalized = name.toUpperCase();
    return Object.values(DefaultThinkingLevel).find(level => level === normalized);
}

/**
 * Thinking level configuration
 * 
 * Used to store full thinking level info, including preset and custom levels
 */
export interface ThinkingLevelConfig {
    /** Unique identifier: off, think, ultra, custom_xxx */
    id: string;
    /** Display name */
    name: string;
    /** Token count */
    tokens: number;
    /** Whether it's a custom level */
    isCustom: boolean;
}

/**
 * Generic option configuration
 * 
 * Used for dropdown options, supports dynamic return to frontend
 */
export interface OptionConfig {
    /** Unique identifier */
    id: string;
    /** Display label */
    label: string;
    /** Description (optional) */
    description: string;
    /** Whether it's the default value */
    isDefault: boolean;
}

/**
 * Custom model configuration
 * 
 * Used to store user-defined model information
 */
export interface CustomModelConfig {
    /** Unique identifier (e.g., "custom_xxx") */
    id: string;
    /** Display name (e.g., "My Custom Model") */
    displayName: string;
    /** Model ID (e.g., "claude-sonnet-4-5-20250929") */
    modelId: string;
}

/**
 * External path rule type
 */
export enum ExternalPathRuleType {
    INCLUDE = 'INCLUDE',  // Allow access
    EXCLUDE = 'EXCLUDE'   // Deny access
}

/**
 * External path access rule
 * 
 * Used to control which paths outside the project can be accessed
 */
export interface ExternalPathRule {
    /** Directory path */
    path: string;
    /** Rule type */
    type: ExternalPathRuleType;
}

/**
 * Unified model information class
 * 
 * Used to uniformly represent built-in and custom models
 */
export interface ModelInfo {
    /** Actual model ID */
    modelId: string;
    /** Display name */
    displayName: string;
    /** Whether it's a built-in model */
    isBuiltIn: boolean;
}

/**
 * AI Agent Provider enum
 */
export enum AiAgentProvider {
    CLAUDE = 'CLAUDE',
    CODEX = 'CODEX'
}

/**
 * MCP Backend constants
 */
export const MCP_BACKEND_ALL = 'all';
export const MCP_BACKEND_CLAUDE = 'claude';
export const MCP_BACKEND_CODEX = 'codex';

/**
 * Agent configuration data class
 */
export interface AgentConfig {
    name: string;
    description: string;
    prompt: string;
    tools: string[];
    /** Main AI's sub-agent selection hint */
    selectionHint: string;
}
