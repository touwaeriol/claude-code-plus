/**
 * Custom MCP Server Dialog
 * 
 * Multi-step wizard for adding/editing custom MCP server configurations.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpDialogs.kt
 * 
 * VS Code Implementation:
 * - Uses vscode.window.createQuickPick() for multi-step wizard
 * - Uses vscode.window.showInputBox() for text input
 * - Supports JSON configuration editing
 */

import * as vscode from 'vscode';
import { McpServerEntry, McpServerLevel, createDefaultMcpServerEntry } from '../settings/mcpModels';
import { MCP_BACKEND_ALL } from '../settings/agentSettingsModels';
import {
    DialogResult,
    BackendSelection,
    QuickPickItemWithData,
    CustomMcpDialogConfig
} from './McpDialogTypes';
import {
    setToBackendSelection,
    backendSelectionToSet,
    showBackendSelection,
    showServerLevelSelection,
    validateMcpServerJson,
    formatJson,
    showConfirmation
} from './McpDialogHelpers';

/**
 * Menu action types for custom MCP dialog
 */
type CustomMenuAction = 
    | 'toggle_enabled'
    | 'edit_backends'
    | 'edit_json'
    | 'format_json'
    | 'paste_json'
    | 'edit_level'
    | 'edit_timeout'
    | 'edit_claude_instructions'
    | 'edit_codex_instructions'
    | 'clear_claude_instructions'
    | 'clear_codex_instructions'
    | 'save'
    | 'cancel';

/**
 * Show custom MCP server configuration dialog
 * 
 * @param config Dialog configuration
 * @returns Dialog result with new/updated entry or undefined if cancelled
 */
export async function showMcpServerDialog(
    config: CustomMcpDialogConfig
): Promise<DialogResult<McpServerEntry>> {
    const isNew = !config.entry;
    const entry = config.entry ? { ...config.entry } : createDefaultMcpServerEntry('');

    // Mutable state for editing
    let enabled = entry.enabled;
    let enabledBackends = new Set(entry.enabledBackends);
    let jsonConfig = entry.jsonConfig;
    let level = entry.level;
    let instructionsClaude = entry.instructionsClaude;
    let instructionsCodex = entry.instructionsCodex;
    let toolTimeoutSec = entry.toolTimeoutSec;

    // For new servers, start with JSON input
    if (isNew) {
        const initialJson = await showJsonConfigInput(jsonConfig);
        if (initialJson === undefined) {
            return { confirmed: false };
        }
        jsonConfig = initialJson;
    }

    // Main menu loop
    let continueLoop = true;
    while (continueLoop) {
        const action = await showCustomMainMenu(isNew, {
            enabled,
            enabledBackends,
            jsonConfig,
            level,
            instructionsClaude,
            instructionsCodex,
            toolTimeoutSec
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

            case 'edit_json': {
                const newJson = await showJsonConfigInput(jsonConfig);
                if (newJson !== undefined) {
                    jsonConfig = newJson;
                }
                break;
            }

            case 'format_json': {
                jsonConfig = formatJson(jsonConfig);
                vscode.window.showInformationMessage('JSON formatted');
                break;
            }

            case 'paste_json': {
                const clipboardContent = await vscode.env.clipboard.readText();
                if (clipboardContent) {
                    const validation = validateMcpServerJson(clipboardContent);
                    if (validation.valid) {
                        jsonConfig = formatJson(clipboardContent);
                        vscode.window.showInformationMessage('JSON pasted from clipboard');
                    } else {
                        vscode.window.showErrorMessage(`Invalid JSON: ${validation.error}`);
                    }
                } else {
                    vscode.window.showWarningMessage('Clipboard is empty');
                }
                break;
            }

            case 'edit_level': {
                const newLevel = await showServerLevelSelection(level);
                if (newLevel !== undefined) {
                    level = newLevel;
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

            case 'clear_claude_instructions':
                if (await showConfirmation('Clear Claude Code system prompt?')) {
                    instructionsClaude = '';
                }
                break;

            case 'clear_codex_instructions':
                if (await showConfirmation('Clear Codex system prompt?')) {
                    instructionsCodex = '';
                }
                break;

            case 'save': {
                // Validate JSON before saving
                const validation = validateMcpServerJson(jsonConfig);
                if (!validation.valid) {
                    vscode.window.showErrorMessage(`Invalid JSON: ${validation.error}`);
                    break;
                }

                // Build updated entry
                const updatedEntry: McpServerEntry = {
                    ...entry,
                    name: validation.serverName || 'unknown',
                    enabled,
                    enabledBackends,
                    level,
                    configSummary: validation.configSummary || '',
                    isBuiltIn: false,
                    jsonConfig,
                    instructions: '',
                    instructionsClaude: instructionsClaude.trim(),
                    instructionsCodex: instructionsCodex.trim(),
                    toolTimeoutSec
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
 * Show main configuration menu for custom MCP server
 */
async function showCustomMainMenu(
    isNew: boolean,
    state: {
        enabled: boolean;
        enabledBackends: Set<string>;
        jsonConfig: string;
        level: McpServerLevel;
        instructionsClaude: string;
        instructionsCodex: string;
        toolTimeoutSec: number;
    }
): Promise<CustomMenuAction | undefined> {
    const items: QuickPickItemWithData<CustomMenuAction>[] = [];

    // Validate current JSON
    const validation = validateMcpServerJson(state.jsonConfig);
    const serverName = validation.serverName || '(not configured)';
    const configStatus = validation.valid ? '$(check) Valid' : '$(error) Invalid';

    // Status indicator
    const enabledIcon = state.enabled ? '$(check)' : '$(x)';
    const backendsLabel = state.enabledBackends.has(MCP_BACKEND_ALL) 
        ? 'All' 
        : Array.from(state.enabledBackends).join(', ');
    const levelLabel = state.level === McpServerLevel.PROJECT ? 'Project' : 'Global';

    // JSON configuration section
    items.push({
        label: `$(json) JSON Configuration: ${configStatus}`,
        description: validation.valid ? `Server: ${serverName}` : validation.error,
        data: 'edit_json'
    });

    items.push({
        label: '$(symbol-misc) Format JSON',
        description: 'Pretty-print the JSON configuration',
        data: 'format_json'
    });

    items.push({
        label: '$(clippy) Paste from Clipboard',
        description: 'Replace JSON with clipboard content',
        data: 'paste_json'
    });

    // Separator
    items.push({
        label: '',
        kind: vscode.QuickPickItemKind.Separator,
        data: 'cancel'
    } as any);

    // General settings
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

    items.push({
        label: `$(globe) Server Level: ${levelLabel}`,
        description: 'Global: all projects | Project: current only',
        data: 'edit_level'
    });

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
        description: state.instructionsClaude ? `${state.instructionsClaude.length} chars` : '(empty)',
        data: 'edit_claude_instructions'
    });

    items.push({
        label: '$(trash) Clear Claude Code Instructions',
        description: 'Remove system prompt',
        data: 'clear_claude_instructions'
    });

    // Separator
    items.push({
        label: '',
        kind: vscode.QuickPickItemKind.Separator,
        data: 'cancel'
    } as any);

    // Codex section
    items.push({
        label: '$(edit) Edit Codex Instructions',
        description: state.instructionsCodex ? `${state.instructionsCodex.length} chars` : '(empty)',
        data: 'edit_codex_instructions'
    });

    items.push({
        label: '$(trash) Clear Codex Instructions',
        description: 'Remove system prompt',
        data: 'clear_codex_instructions'
    });

    // Separator
    items.push({
        label: '',
        kind: vscode.QuickPickItemKind.Separator,
        data: 'cancel'
    } as any);

    // Warning
    items.push({
        label: '$(warning) Warning: Only connect to trusted servers',
        description: 'MCP servers can execute code on your machine',
        data: 'cancel'
    });

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

    const quickPick = vscode.window.createQuickPick<QuickPickItemWithData<CustomMenuAction>>();
    quickPick.items = items;
    quickPick.title = isNew ? 'New MCP Server' : 'Edit MCP Server';
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
 * Show JSON configuration input
 */
async function showJsonConfigInput(currentValue: string): Promise<string | undefined> {
    const options: vscode.QuickPickItem[] = [
        { label: '$(edit) Edit in Input Box', description: 'Edit using input box' },
        { label: '$(file) Edit in New File', description: 'Open in a temporary file for editing' },
        { label: '$(symbol-snippet) Use Template: HTTP Server', description: 'Start with HTTP server template' },
        { label: '$(symbol-snippet) Use Template: Stdio Server', description: 'Start with stdio server template' },
        { label: '$(close) Cancel', description: 'Keep current value' }
    ];

    const selected = await vscode.window.showQuickPick(options, {
        title: 'JSON Configuration',
        placeHolder: 'Choose how to edit the JSON configuration'
    });

    if (!selected) {
        return undefined;
    }

    if (selected.label.includes('Edit in Input Box')) {
        return vscode.window.showInputBox({
            title: 'JSON Configuration',
            value: currentValue,
            prompt: 'Enter MCP server JSON configuration',
            ignoreFocusOut: true,
            validateInput: (value) => {
                if (!value.trim()) return undefined; // Allow empty for intermediate editing
                const validation = validateMcpServerJson(value);
                return validation.valid ? undefined : validation.error;
            }
        });
    }

    if (selected.label.includes('Edit in New File')) {
        // Create a temporary document for editing
        const doc = await vscode.workspace.openTextDocument({
            content: currentValue || getHttpTemplate(),
            language: 'json'
        });
        await vscode.window.showTextDocument(doc);
        
        // Show message to user
        const result = await vscode.window.showInformationMessage(
            'Edit the JSON and click "Use Content" when done, or "Cancel" to discard.',
            { modal: false },
            'Use Content',
            'Cancel'
        );

        if (result === 'Use Content') {
            const content = doc.getText();
            const validation = validateMcpServerJson(content);
            if (validation.valid) {
                return content;
            } else {
                vscode.window.showErrorMessage(`Invalid JSON: ${validation.error}`);
                return undefined;
            }
        }
        return undefined;
    }

    if (selected.label.includes('HTTP Server')) {
        return getHttpTemplate();
    }

    if (selected.label.includes('Stdio Server')) {
        return getStdioTemplate();
    }

    return undefined;
}

/**
 * Show instructions editor
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
 * Get HTTP server template
 */
function getHttpTemplate(): string {
    return JSON.stringify({
        "my-server": {
            "type": "http",
            "url": "https://example.com/mcp"
        }
    }, null, 2);
}

/**
 * Get Stdio server template
 */
function getStdioTemplate(): string {
    return JSON.stringify({
        "my-server": {
            "command": "npx",
            "args": ["-y", "@example/mcp-server"],
            "env": {}
        }
    }, null, 2);
}

/**
 * Quick action: Add new MCP server
 * Simplified flow for quickly adding a new server
 */
export async function quickAddMcpServer(): Promise<DialogResult<McpServerEntry>> {
    // Step 1: Choose server type
    const typeItems: vscode.QuickPickItem[] = [
        { label: '$(cloud) HTTP Server', description: 'Connect to a remote MCP server via HTTP' },
        { label: '$(terminal) Stdio Server', description: 'Run a local MCP server process' }
    ];

    const selectedType = await vscode.window.showQuickPick(typeItems, {
        title: 'New MCP Server',
        placeHolder: 'Select server type'
    });

    if (!selectedType) {
        return { confirmed: false };
    }

    const isHttp = selectedType.label.includes('HTTP');

    // Step 2: Get server name
    const serverName = await vscode.window.showInputBox({
        title: 'Server Name',
        placeHolder: 'Enter a unique name for this server',
        validateInput: (value) => {
            if (!value.trim()) return 'Server name is required';
            if (!/^[a-zA-Z0-9_-]+$/.test(value)) return 'Only letters, numbers, underscores and hyphens allowed';
            return undefined;
        }
    });

    if (!serverName) {
        return { confirmed: false };
    }

    // Step 3: Get URL or command
    let jsonConfig: string;
    
    if (isHttp) {
        const url = await vscode.window.showInputBox({
            title: 'Server URL',
            placeHolder: 'https://example.com/mcp',
            validateInput: (value) => {
                if (!value.trim()) return 'URL is required';
                try {
                    new URL(value);
                    return undefined;
                } catch {
                    return 'Invalid URL format';
                }
            }
        });

        if (!url) {
            return { confirmed: false };
        }

        jsonConfig = JSON.stringify({
            [serverName]: {
                type: 'http',
                url
            }
        }, null, 2);
    } else {
        const command = await vscode.window.showInputBox({
            title: 'Command',
            placeHolder: 'npx, node, python, etc.',
            validateInput: (value) => !value.trim() ? 'Command is required' : undefined
        });

        if (!command) {
            return { confirmed: false };
        }

        const argsInput = await vscode.window.showInputBox({
            title: 'Arguments (optional)',
            placeHolder: 'Enter arguments separated by spaces',
            value: ''
        });

        const args = argsInput ? argsInput.split(/\s+/).filter(a => a) : [];

        jsonConfig = JSON.stringify({
            [serverName]: {
                command,
                args
            }
        }, null, 2);
    }

    // Step 4: Choose level
    const levelItems: vscode.QuickPickItem[] = [
        { label: '$(globe) Global', description: 'Available in all projects' },
        { label: '$(folder) Project', description: 'Available only in current project' }
    ];

    const selectedLevel = await vscode.window.showQuickPick(levelItems, {
        title: 'Server Level',
        placeHolder: 'Where should this server be available?'
    });

    const level = selectedLevel?.label.includes('Project') ? McpServerLevel.PROJECT : McpServerLevel.GLOBAL;

    // Create entry
    const validation = validateMcpServerJson(jsonConfig);
    const entry: McpServerEntry = {
        ...createDefaultMcpServerEntry(serverName),
        name: serverName,
        enabled: true,
        enabledBackends: new Set([MCP_BACKEND_ALL]),
        level,
        configSummary: validation.configSummary || '',
        isBuiltIn: false,
        jsonConfig
    };

    return { confirmed: true, data: entry };
}
