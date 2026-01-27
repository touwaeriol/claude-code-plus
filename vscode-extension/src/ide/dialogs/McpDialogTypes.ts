/**
 * MCP Dialog Types
 * 
 * Type definitions for MCP server configuration dialogs.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpDialogs.kt
 */

import { McpServerEntry, McpServerLevel } from '../settings/mcpModels';

/**
 * Dialog result indicating whether user confirmed or cancelled
 */
export interface DialogResult<T> {
    /** Whether the dialog was confirmed */
    confirmed: boolean;
    /** The result data (only valid if confirmed is true) */
    data?: T;
}

/**
 * Quick pick item with additional data
 */
export interface QuickPickItemWithData<T> {
    label: string;
    description?: string;
    detail?: string;
    picked?: boolean;
    data: T;
}

/**
 * Backend selection options
 */
export interface BackendSelection {
    all: boolean;
    claude: boolean;
    codex: boolean;
}

/**
 * Terminal shell configuration
 */
export interface TerminalShellConfig {
    defaultShell: string;
    availableShells: string[];
}

/**
 * Commit language option
 */
export interface CommitLanguageOption {
    code: string;
    label: string;
}

/**
 * Commit language options list
 */
export const COMMIT_LANGUAGE_OPTIONS: CommitLanguageOption[] = [
    { code: 'en', label: 'English' },
    { code: 'zh', label: '中文' },
    { code: 'ja', label: '日本語' },
    { code: 'ko', label: '한국어' },
    { code: 'auto', label: 'Auto (detect from system)' }
];

/**
 * All available shell types
 */
export const ALL_SHELL_TYPES = ['powershell', 'cmd', 'git-bash', 'wsl'];

/**
 * Codex features that can be disabled
 */
export const CODEX_FEATURES = [
    'shell_tool',
    'apply_patch_freeform',
    'unified_exec',
    'view_image_tool',
    'web_search_request',
    'skills'
];

/**
 * Built-in MCP server dialog configuration
 */
export interface BuiltInMcpDialogConfig {
    /** Server entry to edit */
    entry: McpServerEntry;
    /** Project path (for file chooser) */
    projectPath?: string;
}

/**
 * Custom MCP server dialog configuration
 */
export interface CustomMcpDialogConfig {
    /** Server entry to edit (null for new server) */
    entry?: McpServerEntry;
    /** Project path */
    projectPath?: string;
}

/**
 * MCP Server JSON configuration validation result
 */
export interface JsonValidationResult {
    valid: boolean;
    error?: string;
    serverName?: string;
    serverType?: 'http' | 'stdio';
    configSummary?: string;
}

/**
 * Dialog step for multi-step wizards
 */
export enum DialogStep {
    GENERAL = 'general',
    CLAUDE = 'claude',
    CODEX = 'codex',
    TERMINAL = 'terminal',
    FILE = 'file',
    GIT = 'git'
}

/**
 * Step action result
 */
export interface StepResult {
    /** Whether to continue to next step */
    continue: boolean;
    /** Whether to go back to previous step */
    back?: boolean;
    /** Updated entry data */
    entry?: Partial<McpServerEntry>;
}

/**
 * Input box options with validation
 */
export interface ValidatedInputOptions {
    title: string;
    prompt?: string;
    value?: string;
    placeholder?: string;
    validateInput?: (value: string) => string | undefined;
}

/**
 * Multi-select quick pick result
 */
export interface MultiSelectResult {
    selected: string[];
    cancelled: boolean;
}

/**
 * Tag item for displaying list of tools/features
 */
export interface TagItem {
    label: string;
    removable: boolean;
}
