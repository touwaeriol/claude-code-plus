/**
 * MCP Models
 * 
 * Type definitions for MCP server configurations.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpModels.kt
 */

import { MCP_BACKEND_ALL } from './agentSettingsModels';

/**
 * MCP Server Level
 */
export enum McpServerLevel {
    BUILTIN = 'BUILTIN',   // Built-in
    GLOBAL = 'GLOBAL',     // Global
    PROJECT = 'PROJECT'    // Project
}

/**
 * MCP Server Entry
 */
export interface McpServerEntry {
    /** Server name */
    name: string;
    /** Whether enabled */
    enabled: boolean;
    /** Enabled backends (e.g., 'all', 'claude', 'codex') */
    enabledBackends: Set<string>;
    /** Server level */
    level: McpServerLevel;
    /** Config summary for display */
    configSummary: string;
    /** Whether it's a built-in server */
    isBuiltIn: boolean;
    /** JSON configuration */
    jsonConfig: string;
    /** System instructions (common) */
    instructions: string;
    /** System instructions for Claude */
    instructionsClaude: string;
    /** System instructions for Codex */
    instructionsCodex: string;
    /** API key */
    apiKey: string;
    /** Disabled Claude Code built-in tools when this MCP is enabled */
    disabledTools: string[];
    /** Disabled Codex features when this MCP is enabled (e.g., "shell_tool") */
    codexDisabledFeatures: string[];
    /** Default system instructions (for built-in MCP, read-only) */
    defaultInstructions: string;
    /** Whether it has an associated disable tools toggle */
    hasDisableToolsToggle: boolean;
    /** JetBrains Terminal MCP: max output lines */
    terminalMaxOutputLines: number;
    /** JetBrains Terminal MCP: max output chars */
    terminalMaxOutputChars: number;
    /** JetBrains Terminal MCP: default shell (empty = system default) */
    terminalDefaultShell: string;
    /** JetBrains Terminal MCP: available shells (comma-separated) */
    terminalAvailableShells: string;
    /** JetBrains Terminal MCP: TerminalRead default timeout (seconds) */
    terminalReadTimeout: number;
    /** Tool invocation timeout (seconds), minimum 1, default 60 */
    toolTimeoutSec: number;
    /** JetBrains File MCP: allow external file access */
    fileAllowExternal: boolean;
    /** JetBrains File MCP: external path rules (JSON serialized) */
    fileExternalRules: string;
    /** Git MCP: Commit message language (en, zh, ja, ko, auto) */
    gitCommitLanguage: string;
    /** Codex mode auto-approved MCP tools (no user confirmation needed) */
    codexAutoApprovedTools: string[];
    /** Default auto-approved tools (for built-in MCP, read-only) */
    defaultAutoApprovedTools: string[];
    /** Default disabled Claude Code built-in tools (for built-in MCP, read-only) */
    defaultDisabledTools: string[];
    /** Default disabled Codex features (for built-in MCP, read-only) */
    defaultCodexDisabledFeatures: string[];
}

/**
 * Create a default MCP server entry
 */
export function createDefaultMcpServerEntry(name: string): McpServerEntry {
    return {
        name,
        enabled: true,
        enabledBackends: new Set([MCP_BACKEND_ALL]),
        level: McpServerLevel.GLOBAL,
        configSummary: '',
        isBuiltIn: false,
        jsonConfig: '',
        instructions: '',
        instructionsClaude: '',
        instructionsCodex: '',
        apiKey: '',
        disabledTools: [],
        codexDisabledFeatures: [],
        defaultInstructions: '',
        hasDisableToolsToggle: false,
        terminalMaxOutputLines: 500,
        terminalMaxOutputChars: 50000,
        terminalDefaultShell: '',
        terminalAvailableShells: '',
        terminalReadTimeout: 30,
        toolTimeoutSec: 60,
        fileAllowExternal: true,
        fileExternalRules: '[]',
        gitCommitLanguage: 'en',
        codexAutoApprovedTools: [],
        defaultAutoApprovedTools: [],
        defaultDisabledTools: [],
        defaultCodexDisabledFeatures: []
    };
}

/**
 * Convert McpServerEntry to a plain object for JSON serialization
 */
export function mcpServerEntryToJson(entry: McpServerEntry): Record<string, unknown> {
    return {
        ...entry,
        enabledBackends: Array.from(entry.enabledBackends)
    };
}

/**
 * Create McpServerEntry from a plain object (JSON deserialization)
 */
export function mcpServerEntryFromJson(json: Record<string, unknown>): McpServerEntry {
    const defaultEntry = createDefaultMcpServerEntry(json.name as string || '');
    return {
        ...defaultEntry,
        ...json,
        enabledBackends: new Set((json.enabledBackends as string[]) || [MCP_BACKEND_ALL])
    };
}
