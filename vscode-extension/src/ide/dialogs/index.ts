/**
 * MCP Dialogs Module
 * 
 * VS Code implementation of MCP server configuration dialogs.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpDialogs.kt
 * 
 * This module provides:
 * - Built-in MCP server configuration dialog
 * - Custom MCP server add/edit dialog
 * - Delete confirmation dialog
 * - Helper functions for dialog interactions
 */

// Types
export type {
    DialogResult,
    QuickPickItemWithData,
    BackendSelection,
    TerminalShellConfig,
    CommitLanguageOption,
    BuiltInMcpDialogConfig,
    CustomMcpDialogConfig,
    JsonValidationResult,
    StepResult,
    ValidatedInputOptions,
    MultiSelectResult,
    TagItem
} from './McpDialogTypes';

export {
    DialogStep,
    COMMIT_LANGUAGE_OPTIONS,
    ALL_SHELL_TYPES,
    CODEX_FEATURES
} from './McpDialogTypes';

// Helpers
export {
    formatJson,
    validateMcpServerJson,
    backendSelectionToSet,
    setToBackendSelection,
    showBackendSelection,
    showValidatedInput,
    showTagMultiSelect,
    showConfirmation,
    showDeleteConfirmation,
    showShellTypeSelection,
    showCommitLanguageSelection,
    showServerLevelSelection,
    showTextAreaInput,
    showFolderPicker,
    showExternalRulesEditor,
    showDisabledToolsEditor,
    showCodexFeaturesEditor,
    showAutoApprovedToolsEditor,
    parseExternalRules,
    serializeExternalRules
} from './McpDialogHelpers';

// Dialogs
export { showBuiltInMcpServerDialog } from './BuiltInMcpServerDialog';
export { showMcpServerDialog, quickAddMcpServer } from './McpServerDialog';
