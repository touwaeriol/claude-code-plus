/**
 * Built-in MCP Server Dialog
 * 
 * Multi-step wizard for editing built-in MCP server configurations.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpDialogs.kt
 * 
 * VS Code Implementation:
 * - Uses vscode.window.createQuickPick() for multi-step wizard
 * - Uses vscode.window.showInputBox() for text input
 * - Implements tabbed interface as sequential steps
 */

import * as vscode from 'vscode';
import { McpServerEntry } from '../settings/mcpModels';
import { MCP_BACKEND_ALL } from '../settings/agentSettingsModels';
import {
    DialogResult,
    BackendSelection,
    QuickPickItemWithData,
    ALL_SHELL_TYPES,
    COMMIT_LANGUAGE_OPTIONS,
    BuiltInMcpDialogConfig
} from './McpDialogTypes';
import {
    setToBackendSelection,
    backendSelectionToSet,
    showBackendSelection,
    showShellTypeSelection,
    showCommitLanguageSelection,
    showExternalRulesEditor,
    showDisabledToolsEditor,
    showCodexFeaturesEditor,
    showAutoApprovedToolsEditor,
    parseExternalRules,
    serializeExternalRules,
    showConfirmation
} from './McpDialogHelpers';

/**
 * Menu action types for the main menu
 */
type MenuAction = 
    | 'toggle_enabled'
    | 'edit_backends'
    | 'edit_api_key'
    | 'edit_terminal_config'
    | 'edit_file_config'
    | 'edit_git_config'
    | 'edit_timeout'
    | 'edit_claude_instructions'
    | 'edit_codex_instructions'
    | 'edit_disabled_tools'
    | 'edit_codex_features'
    | 'edit_auto_approved_tools'
    | 'reset_claude_instructions'
    | 'reset_codex_instructions'
    | 'save'
    | 'cancel';

/**
 * Show built-in MCP server configuration dialog
 * 
 * @param config Dialog configuration
 * @returns Dialog result with updated entry or undefined if cancelled
 */
export async function showBuiltInMcpServerDialog(
    config: BuiltInMcpDialogConfig
): Promise<DialogResult<McpServerEntry>> {
    const entry = { ...config.entry };
    
    // Mutable state for editing
    let enabled = entry.enabled;
    let enabledBackends = new Set(entry.enabledBackends);
    let apiKey = entry.apiKey;
    let instructionsClaude = entry.instructionsClaude || entry.defaultInstructions;
    let instructionsCodex = entry.instructionsCodex || entry.defaultInstructions;
    let disabledTools = [...entry.disabledTools];
    let codexDisabledFeatures = [...entry.codexDisabledFeatures];
    let codexAutoApprovedTools = [...entry.codexAutoApprovedTools];
    let toolTimeoutSec = entry.toolTimeoutSec;
    
    // Terminal MCP specific
    let terminalMaxOutputLines = entry.terminalMaxOutputLines;
    let terminalMaxOutputChars = entry.terminalMaxOutputChars;
    let terminalDefaultShell = entry.terminalDefaultShell;
    let terminalAvailableShells = entry.terminalAvailableShells;
    let terminalReadTimeout = entry.terminalReadTimeout;
    
    // File MCP specific
    let fileAllowExternal = entry.fileAllowExternal;
    let fileExternalRules = parseExternalRules(entry.fileExternalRules);
    
    // Git MCP specific
    let gitCommitLanguage = entry.gitCommitLanguage || 'en';

    // Main menu loop
    let continueLoop = true;
    while (continueLoop) {
        const action = await showMainMenu(entry, {
            enabled,
            enabledBackends,
            apiKey,
            instructionsClaude,
            instructionsCodex,
            disabledTools,
            codexDisabledFeatures,
            codexAutoApprovedTools,
            toolTimeoutSec,
            terminalDefaultShell,
            terminalAvailableShells,
            fileAllowExternal,
            gitCommitLanguage
        });

        switch (action) {
            case 'toggle_enabled':
                enabled = !enabled;
                break;

            case 'edit_backends': {
                const selection = await showBackendSelection(setToBackendSelection(enabledBackends));
                if (selection) {
                    enabledBackends = backendSelectionToSet(selection);
                }
                break;
            }

            case 'edit_api_key': {
                const newKey = await vscode.window.showInputBox({
                    title: 'API Key',
                    value: apiKey,
                    placeHolder: 'Enter API key (optional)',
                    prompt: 'API key for authenticated access'
                });
                if (newKey !== undefined) {
                    apiKey = newKey;
                }
                break;
            }

            case 'edit_terminal_config': {
                await editTerminalConfig({
                    maxOutputLines: terminalMaxOutputLines,
                    maxOutputChars: terminalMaxOutputChars,
                    defaultShell: terminalDefaultShell,
                    availableShells: terminalAvailableShells,
                    readTimeout: terminalReadTimeout
                }, (config) => {
                    terminalMaxOutputLines = config.maxOutputLines;
                    terminalMaxOutputChars = config.maxOutputChars;
                    terminalDefaultShell = config.defaultShell;
                    terminalAvailableShells = config.availableShells;
                    terminalReadTimeout = config.readTimeout;
                });
                break;
            }

            case 'edit_file_config': {
                await editFileConfig({
                    allowExternal: fileAllowExternal,
                    externalRules: fileExternalRules
                }, (config) => {
                    fileAllowExternal = config.allowExternal;
                    fileExternalRules = config.externalRules;
                });
                break;
            }

            case 'edit_git_config': {
                const newLang = await showCommitLanguageSelection(gitCommitLanguage);
                if (newLang) {
                    gitCommitLanguage = newLang;
                }
                break;
            }

            case 'edit_timeout': {
                const newTimeout = await vscode.window.showInputBox({
                    title: 'Tool Call Timeout',
                    value: toolTimeoutSec.toString(),
                    placeHolder: 'Enter timeout in seconds (min 1)',
                    validateInput: (value) => {
                        const num = parseInt(value, 10);
                        if (isNaN(num) || num < 1) {
                            return 'Timeout must be at least 1 second';
                        }
                        return undefined;
                    }
                });
                if (newTimeout !== undefined) {
                    toolTimeoutSec = Math.max(1, parseInt(newTimeout, 10));
                }
                break;
            }

            case 'edit_claude_instructions': {
                const newInstructions = await showInstructionsEditor(
                    'Claude Code System Prompt',
                    instructionsClaude
                );
                if (newInstructions !== undefined) {
                    instructionsClaude = newInstructions;
                }
                break;
            }

            case 'edit_codex_instructions': {
                const newInstructions = await showInstructionsEditor(
                    'Codex System Prompt',
                    instructionsCodex
                );
                if (newInstructions !== undefined) {
                    instructionsCodex = newInstructions;
                }
                break;
            }

            case 'reset_claude_instructions':
                if (await showConfirmation('Reset Claude Code system prompt to default?')) {
                    instructionsClaude = entry.defaultInstructions;
                }
                break;

            case 'reset_codex_instructions':
                if (await showConfirmation('Reset Codex system prompt to default?')) {
                    instructionsCodex = entry.defaultInstructions;
                }
                break;

            case 'edit_disabled_tools': {
                const result = await showDisabledToolsEditor(
                    'Disabled Tools (Claude Code)',
                    disabledTools,
                    entry.defaultDisabledTools
                );
                if (result) {
                    disabledTools = result;
                }
                break;
            }

            case 'edit_codex_features': {
                const result = await showCodexFeaturesEditor(
                    codexDisabledFeatures,
                    entry.defaultCodexDisabledFeatures
                );
                if (result) {
                    codexDisabledFeatures = result;
                }
                break;
            }

            case 'edit_auto_approved_tools': {
                const result = await showAutoApprovedToolsEditor(
                    codexAutoApprovedTools,
                    entry.defaultAutoApprovedTools
                );
                if (result) {
                    codexAutoApprovedTools = result;
                }
                break;
            }

            case 'save': {
                // Build updated entry
                const customClaudeInstructions = instructionsClaude.trim() === entry.defaultInstructions.trim() 
                    ? '' 
                    : instructionsClaude;
                const customCodexInstructions = instructionsCodex.trim() === entry.defaultInstructions.trim()
                    ? ''
                    : instructionsCodex;

                const updatedEntry: McpServerEntry = {
                    ...entry,
                    enabled,
                    enabledBackends,
                    instructions: '',
                    instructionsClaude: customClaudeInstructions,
                    instructionsCodex: customCodexInstructions,
                    apiKey: entry.name === 'Context7 MCP' ? apiKey : entry.apiKey,
                    disabledTools,
                    codexDisabledFeatures,
                    codexAutoApprovedTools,
                    toolTimeoutSec,
                    terminalMaxOutputLines,
                    terminalMaxOutputChars,
                    terminalDefaultShell,
                    terminalAvailableShells,
                    terminalReadTimeout,
                    fileAllowExternal,
                    fileExternalRules: serializeExternalRules(fileExternalRules),
                    gitCommitLanguage
                };

                return { confirmed: true, data: updatedEntry };
            }

            case 'cancel':
            case undefined:
                continueLoop = false;
                break;
        }
    }

    return { confirmed: false };
}

/**
 * Show main configuration menu
 */
async function showMainMenu(
    entry: McpServerEntry,
    state: {
        enabled: boolean;
        enabledBackends: Set<string>;
        apiKey: string;
        instructionsClaude: string;
        instructionsCodex: string;
        disabledTools: string[];
        codexDisabledFeatures: string[];
        codexAutoApprovedTools: string[];
        toolTimeoutSec: number;
        terminalDefaultShell: string;
        terminalAvailableShells: string;
        fileAllowExternal: boolean;
        gitCommitLanguage: string;
    }
): Promise<MenuAction | undefined> {
    const items: QuickPickItemWithData<MenuAction>[] = [];

    // Status indicator
    const enabledIcon = state.enabled ? '$(check)' : '$(x)';
    const backendsLabel = state.enabledBackends.has(MCP_BACKEND_ALL) 
        ? 'All' 
        : Array.from(state.enabledBackends).join(', ');

    // General section
    items.push({
        label: `${enabledIcon} Enabled: ${state.enabled ? 'Yes' : 'No'}`,
        description: 'Toggle server enabled state',
        data: 'toggle_enabled'
    });

    items.push({
        label: `$(server) Enabled Backends: ${backendsLabel}`,
        description: 'Select which backends can use this server',
        data: 'edit_backends'
    });

    // Context7 MCP - API Key
    if (entry.name === 'Context7 MCP') {
        items.push({
            label: `$(key) API Key: ${state.apiKey ? '****' : '(not set)'}`,
            description: 'Set API key for authenticated access',
            data: 'edit_api_key'
        });
    }

    // Terminal MCP specific options
    if (entry.name === 'JetBrains Terminal MCP') {
        const shellsDisplay = state.terminalAvailableShells || 'all';
        items.push({
            label: `$(terminal) Terminal Config`,
            description: `Shell: ${state.terminalDefaultShell || 'default'}, Available: ${shellsDisplay}`,
            data: 'edit_terminal_config'
        });
    }

    // File MCP specific options
    if (entry.name === 'JetBrains File MCP') {
        items.push({
            label: `$(file) File Config`,
            description: `External access: ${state.fileAllowExternal ? 'Allowed' : 'Denied'}`,
            data: 'edit_file_config'
        });
    }

    // Git MCP specific options
    if (entry.name === 'JetBrains Git MCP') {
        const langLabel = COMMIT_LANGUAGE_OPTIONS.find(o => o.code === state.gitCommitLanguage)?.label || state.gitCommitLanguage;
        items.push({
            label: `$(git-commit) Commit Language: ${langLabel}`,
            description: 'Set commit message language',
            data: 'edit_git_config'
        });
    }

    // Timeout
    items.push({
        label: `$(clock) Tool Timeout: ${state.toolTimeoutSec}s`,
        description: 'Set tool call timeout',
        data: 'edit_timeout'
    });

    // Separator
    items.push({
        label: '',
        kind: vscode.QuickPickItemKind.Separator,
        data: 'cancel'
    } as any);

    // Claude Code section
    items.push({
        label: '$(edit) Edit Claude Code Instructions',
        description: 'Customize system prompt for Claude Code',
        data: 'edit_claude_instructions'
    });

    items.push({
        label: '$(sync) Reset Claude Code Instructions',
        description: 'Reset to default system prompt',
        data: 'reset_claude_instructions'
    });

    // Disabled tools (if applicable)
    if (entry.defaultDisabledTools.length > 0 || entry.hasDisableToolsToggle) {
        items.push({
            label: `$(x) Disabled Tools: ${state.disabledTools.length} items`,
            description: 'Manage disabled Claude Code tools',
            data: 'edit_disabled_tools'
        });
    }

    // Separator
    items.push({
        label: '',
        kind: vscode.QuickPickItemKind.Separator,
        data: 'cancel'
    } as any);

    // Codex section
    items.push({
        label: '$(edit) Edit Codex Instructions',
        description: 'Customize system prompt for Codex',
        data: 'edit_codex_instructions'
    });

    items.push({
        label: '$(sync) Reset Codex Instructions',
        description: 'Reset to default system prompt',
        data: 'reset_codex_instructions'
    });

    // Codex features (if applicable)
    if (entry.defaultCodexDisabledFeatures.length > 0) {
        items.push({
            label: `$(x) Disabled Features: ${state.codexDisabledFeatures.length} items`,
            description: 'Manage disabled Codex features',
            data: 'edit_codex_features'
        });
    }

    // Auto-approved tools (if applicable)
    if (entry.defaultAutoApprovedTools.length > 0) {
        items.push({
            label: `$(verified) Auto-Approved Tools: ${state.codexAutoApprovedTools.length} items`,
            description: 'Manage Codex auto-approved tools',
            data: 'edit_auto_approved_tools'
        });
    }

    // Separator
    items.push({
        label: '',
        kind: vscode.QuickPickItemKind.Separator,
        data: 'cancel'
    } as any);

    // Actions
    items.push({
        label: '$(save) Save',
        description: 'Save configuration and close',
        data: 'save'
    });

    items.push({
        label: '$(close) Cancel',
        description: 'Discard changes and close',
        data: 'cancel'
    });

    const quickPick = vscode.window.createQuickPick<QuickPickItemWithData<MenuAction>>();
    quickPick.items = items;
    quickPick.title = `Configure ${entry.name}`;
    quickPick.placeholder = 'Select an option to configure';

    return new Promise((resolve) => {
        quickPick.onDidAccept(() => {
            const selected = quickPick.selectedItems[0];
            quickPick.hide();
            resolve(selected?.data);
        });

        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve(undefined);
        });

        quickPick.show();
    });
}

/**
 * Show instructions editor using input box
 * For complex multi-line editing, we show a message suggesting to use settings
 */
async function showInstructionsEditor(
    title: string,
    currentValue: string
): Promise<string | undefined> {
    const options: vscode.QuickPickItem[] = [
        { label: '$(edit) Edit in Input Box', description: 'Edit using input box (single line)' },
        { label: '$(file) Edit in New File', description: 'Open in a temporary file for multi-line editing' },
        { label: '$(clipboard) Paste from Clipboard', description: 'Replace with clipboard content' },
        { label: '$(close) Cancel', description: 'Keep current value' }
    ];

    const selected = await vscode.window.showQuickPick(options, {
        title,
        placeHolder: 'Choose how to edit the system prompt'
    });

    if (!selected) {
        return undefined;
    }

    if (selected.label.includes('Edit in Input Box')) {
        return vscode.window.showInputBox({
            title,
            value: currentValue.replace(/\n/g, '\\n'),
            prompt: 'Edit system prompt (use \\n for newlines)',
            ignoreFocusOut: true
        }).then(value => value?.replace(/\\n/g, '\n'));
    }

    if (selected.label.includes('Edit in New File')) {
        // Create a temporary document for editing
        const doc = await vscode.workspace.openTextDocument({
            content: currentValue,
            language: 'plaintext'
        });
        await vscode.window.showTextDocument(doc);
        
        // Show message to user
        const result = await vscode.window.showInformationMessage(
            'Edit the content and click "Use Content" when done, or "Cancel" to discard.',
            { modal: false },
            'Use Content',
            'Cancel'
        );

        if (result === 'Use Content') {
            return doc.getText();
        }
        return undefined;
    }

    if (selected.label.includes('Paste from Clipboard')) {
        const clipboardContent = await vscode.env.clipboard.readText();
        if (clipboardContent) {
            const confirm = await showConfirmation(
                `Replace current content with clipboard content (${clipboardContent.length} characters)?`
            );
            if (confirm) {
                return clipboardContent;
            }
        } else {
            vscode.window.showWarningMessage('Clipboard is empty');
        }
    }

    return undefined;
}

/**
 * Terminal configuration state
 */
interface TerminalConfigState {
    maxOutputLines: number;
    maxOutputChars: number;
    defaultShell: string;
    availableShells: string;
    readTimeout: number;
}

/**
 * Edit terminal configuration
 */
async function editTerminalConfig(
    state: TerminalConfigState,
    onUpdate: (state: TerminalConfigState) => void
): Promise<void> {
    const items: vscode.QuickPickItem[] = [
        { label: `$(symbol-number) Max Output Lines: ${state.maxOutputLines}`, description: 'Maximum lines to return' },
        { label: `$(symbol-number) Max Output Chars: ${state.maxOutputChars}`, description: 'Maximum characters to return' },
        { label: `$(terminal) Default Shell: ${state.defaultShell || 'system default'}`, description: 'Default shell type' },
        { label: `$(list-flat) Available Shells: ${state.availableShells || 'all'}`, description: 'Configure available shells' },
        { label: `$(clock) Read Timeout: ${state.readTimeout}s`, description: 'Default read timeout' },
        { label: '$(check) Done', description: 'Return to main menu' }
    ];

    const selected = await vscode.window.showQuickPick(items, {
        title: 'Terminal Configuration',
        placeHolder: 'Select an option to configure'
    });

    if (!selected || selected.label.includes('Done')) {
        return;
    }

    if (selected.label.includes('Max Output Lines')) {
        const value = await vscode.window.showInputBox({
            title: 'Max Output Lines',
            value: state.maxOutputLines.toString(),
            validateInput: (v) => {
                const num = parseInt(v, 10);
                return isNaN(num) || num < 1 ? 'Must be a positive number' : undefined;
            }
        });
        if (value !== undefined) {
            state.maxOutputLines = parseInt(value, 10);
            onUpdate(state);
        }
    } else if (selected.label.includes('Max Output Chars')) {
        const value = await vscode.window.showInputBox({
            title: 'Max Output Characters',
            value: state.maxOutputChars.toString(),
            validateInput: (v) => {
                const num = parseInt(v, 10);
                return isNaN(num) || num < 1 ? 'Must be a positive number' : undefined;
            }
        });
        if (value !== undefined) {
            state.maxOutputChars = parseInt(value, 10);
            onUpdate(state);
        }
    } else if (selected.label.includes('Default Shell') || selected.label.includes('Available Shells')) {
        const currentShells = state.availableShells 
            ? state.availableShells.split(',').map(s => s.trim()).filter(s => s)
            : ALL_SHELL_TYPES;
        
        const result = await showShellTypeSelection(currentShells, state.defaultShell);
        if (result) {
            state.availableShells = result.shells.length === ALL_SHELL_TYPES.length 
                ? '' 
                : result.shells.join(',');
            state.defaultShell = result.defaultShell;
            onUpdate(state);
        }
    } else if (selected.label.includes('Read Timeout')) {
        const value = await vscode.window.showInputBox({
            title: 'Read Timeout (seconds)',
            value: state.readTimeout.toString(),
            validateInput: (v) => {
                const num = parseInt(v, 10);
                return isNaN(num) || num < 1 ? 'Must be a positive number' : undefined;
            }
        });
        if (value !== undefined) {
            state.readTimeout = parseInt(value, 10);
            onUpdate(state);
        }
    }

    // Continue editing
    await editTerminalConfig(state, onUpdate);
}

/**
 * File configuration state
 */
interface FileConfigState {
    allowExternal: boolean;
    externalRules: string[];
}

/**
 * Edit file configuration
 */
async function editFileConfig(
    state: FileConfigState,
    onUpdate: (state: FileConfigState) => void
): Promise<void> {
    const items: vscode.QuickPickItem[] = [
        { 
            label: `$(shield) Allow External Access: ${state.allowExternal ? 'Yes' : 'No'}`, 
            description: 'Toggle external file access' 
        },
        { 
            label: `$(folder) External Access Rules: ${state.externalRules.length} paths`, 
            description: 'Manage allowed external paths' 
        },
        { label: '$(check) Done', description: 'Return to main menu' }
    ];

    const selected = await vscode.window.showQuickPick(items, {
        title: 'File MCP Configuration',
        placeHolder: 'Select an option to configure'
    });

    if (!selected || selected.label.includes('Done')) {
        return;
    }

    if (selected.label.includes('Allow External Access')) {
        state.allowExternal = !state.allowExternal;
        onUpdate(state);
    } else if (selected.label.includes('External Access Rules')) {
        const result = await showExternalRulesEditor(state.externalRules);
        if (result) {
            state.externalRules = result;
            onUpdate(state);
        }
    }

    // Continue editing
    await editFileConfig(state, onUpdate);
}
