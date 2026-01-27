/**
 * MCP Dialog Helpers
 * 
 * Utility functions for MCP server configuration dialogs.
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpDialogs.kt
 */

import * as vscode from 'vscode';
import { MCP_BACKEND_ALL, MCP_BACKEND_CLAUDE, MCP_BACKEND_CODEX } from '../settings/agentSettingsModels';
import { McpServerEntry, McpServerLevel } from '../settings/mcpModels';
import {
    JsonValidationResult,
    BackendSelection,
    QuickPickItemWithData,
    MultiSelectResult,
    ValidatedInputOptions,
    ALL_SHELL_TYPES,
    COMMIT_LANGUAGE_OPTIONS,
    CODEX_FEATURES
} from './McpDialogTypes';

/**
 * Format JSON string with pretty print
 */
export function formatJson(jsonText: string): string {
    try {
        const parsed = JSON.parse(jsonText.trim());
        return JSON.stringify(parsed, null, 2);
    } catch {
        return jsonText;
    }
}

/**
 * Validate MCP server JSON configuration
 */
export function validateMcpServerJson(jsonText: string): JsonValidationResult {
    if (!jsonText.trim()) {
        return { valid: false, error: 'JSON configuration cannot be empty' };
    }

    try {
        const parsed = JSON.parse(jsonText.trim());

        if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
            return { valid: false, error: 'Configuration must be a JSON object' };
        }

        const serverNames = Object.keys(parsed);
        if (serverNames.length === 0) {
            return { valid: false, error: 'Configuration must contain at least one server' };
        }

        for (const serverName of serverNames) {
            if (!serverName.trim()) {
                return { valid: false, error: 'Server name cannot be empty' };
            }

            const serverConfig = parsed[serverName];
            if (typeof serverConfig !== 'object' || serverConfig === null) {
                return { valid: false, error: `Server '${serverName}' configuration must be an object` };
            }

            const hasCommand = 'command' in serverConfig;
            const hasUrl = 'url' in serverConfig;
            const serverType = serverConfig.type?.toString();

            if (serverType === 'http' && !hasUrl) {
                return { valid: false, error: `HTTP server '${serverName}' must have 'url' field` };
            }

            if (serverType !== 'http' && !hasCommand) {
                return { valid: false, error: `Server '${serverName}' must have 'command' field` };
            }

            if (hasCommand) {
                const command = serverConfig.command?.toString();
                if (!command?.trim()) {
                    return { valid: false, error: `Server '${serverName}': 'command' cannot be empty` };
                }
            }

            if (hasUrl) {
                const url = serverConfig.url?.toString();
                if (!url?.trim()) {
                    return { valid: false, error: `Server '${serverName}': 'url' cannot be empty` };
                }
            }

            if ('args' in serverConfig && !Array.isArray(serverConfig.args)) {
                return { valid: false, error: `Server '${serverName}': 'args' must be an array` };
            }

            if ('env' in serverConfig && (typeof serverConfig.env !== 'object' || serverConfig.env === null || Array.isArray(serverConfig.env))) {
                return { valid: false, error: `Server '${serverName}': 'env' must be an object` };
            }
        }

        // Extract server info for summary
        const firstName = serverNames[0];
        const firstConfig = parsed[firstName];
        const isHttp = firstConfig.type === 'http';
        const summary = isHttp
            ? `http: ${firstConfig.url || ''}`
            : `command: ${firstConfig.command || ''}`;

        return {
            valid: true,
            serverName: firstName,
            serverType: isHttp ? 'http' : 'stdio',
            configSummary: summary
        };
    } catch (e) {
        return { valid: false, error: `Invalid JSON: ${e instanceof Error ? e.message : String(e)}` };
    }
}

/**
 * Convert backend selection to Set
 */
export function backendSelectionToSet(selection: BackendSelection): Set<string> {
    if (selection.all) {
        return new Set([MCP_BACKEND_ALL]);
    }
    const result = new Set<string>();
    if (selection.claude) result.add(MCP_BACKEND_CLAUDE);
    if (selection.codex) result.add(MCP_BACKEND_CODEX);
    return result;
}

/**
 * Convert Set to backend selection
 */
export function setToBackendSelection(backends: Set<string>): BackendSelection {
    const keys = Array.from(backends).map(k => k.trim().toLowerCase());
    const isAll = keys.includes(MCP_BACKEND_ALL);
    return {
        all: isAll,
        claude: !isAll && keys.includes(MCP_BACKEND_CLAUDE),
        codex: !isAll && keys.includes(MCP_BACKEND_CODEX)
    };
}

/**
 * Show backend selection quick pick
 */
export async function showBackendSelection(current: BackendSelection): Promise<BackendSelection | undefined> {
    const items: QuickPickItemWithData<string>[] = [
        { label: 'All', description: 'Enable for all backends', picked: current.all, data: 'all' },
        { label: 'Claude Code', description: 'Enable for Claude Code only', picked: !current.all && current.claude, data: 'claude' },
        { label: 'Codex', description: 'Enable for Codex only', picked: !current.all && current.codex, data: 'codex' }
    ];

    const quickPick = vscode.window.createQuickPick<QuickPickItemWithData<string>>();
    quickPick.items = items;
    quickPick.canSelectMany = true;
    quickPick.title = 'Select Enabled Backends';
    quickPick.placeholder = 'Select which backends can use this MCP server';

    // Pre-select items based on current selection
    quickPick.selectedItems = items.filter(item => item.picked);

    return new Promise((resolve) => {
        quickPick.onDidAccept(() => {
            const selected = quickPick.selectedItems;
            quickPick.hide();

            if (selected.length === 0) {
                resolve(undefined);
                return;
            }

            const hasAll = selected.some(item => item.data === 'all');
            if (hasAll) {
                resolve({ all: true, claude: false, codex: false });
            } else {
                resolve({
                    all: false,
                    claude: selected.some(item => item.data === 'claude'),
                    codex: selected.some(item => item.data === 'codex')
                });
            }
        });

        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve(undefined);
        });

        quickPick.show();
    });
}

/**
 * Show validated input box
 */
export async function showValidatedInput(options: ValidatedInputOptions): Promise<string | undefined> {
    return vscode.window.showInputBox({
        title: options.title,
        prompt: options.prompt,
        value: options.value,
        placeHolder: options.placeholder,
        validateInput: options.validateInput
    });
}

/**
 * Show multi-select quick pick for tags (tools/features)
 */
export async function showTagMultiSelect(
    title: string,
    placeholder: string,
    allItems: string[],
    selectedItems: string[]
): Promise<MultiSelectResult> {
    const items: vscode.QuickPickItem[] = allItems.map(item => ({
        label: item,
        picked: selectedItems.includes(item)
    }));

    const quickPick = vscode.window.createQuickPick();
    quickPick.items = items;
    quickPick.canSelectMany = true;
    quickPick.title = title;
    quickPick.placeholder = placeholder;
    quickPick.selectedItems = items.filter(item => item.picked);

    return new Promise((resolve) => {
        quickPick.onDidAccept(() => {
            const selected = quickPick.selectedItems.map(item => item.label);
            quickPick.hide();
            resolve({ selected, cancelled: false });
        });

        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve({ selected: [], cancelled: true });
        });

        quickPick.show();
    });
}

/**
 * Show confirmation dialog
 */
export async function showConfirmation(
    message: string,
    confirmLabel: string = 'Yes',
    cancelLabel: string = 'No'
): Promise<boolean> {
    const result = await vscode.window.showInformationMessage(
        message,
        { modal: true },
        confirmLabel,
        cancelLabel
    );
    return result === confirmLabel;
}

/**
 * Show delete confirmation dialog
 */
export async function showDeleteConfirmation(serverName: string): Promise<boolean> {
    return showConfirmation(
        `Are you sure you want to delete MCP server "${serverName}"?`,
        'Delete',
        'Cancel'
    );
}

/**
 * Show shell type selection
 */
export async function showShellTypeSelection(
    currentShells: string[],
    defaultShell: string
): Promise<{ shells: string[]; defaultShell: string } | undefined> {
    // First, select available shells
    const shellItems: vscode.QuickPickItem[] = ALL_SHELL_TYPES.map(shell => ({
        label: shell,
        picked: currentShells.length === 0 || currentShells.includes(shell)
    }));

    const quickPick = vscode.window.createQuickPick();
    quickPick.items = shellItems;
    quickPick.canSelectMany = true;
    quickPick.title = 'Select Available Shell Types';
    quickPick.placeholder = 'Select which shell types are available';
    quickPick.selectedItems = shellItems.filter(item => item.picked);

    const selectedShells = await new Promise<string[] | undefined>((resolve) => {
        quickPick.onDidAccept(() => {
            const selected = quickPick.selectedItems.map(item => item.label);
            quickPick.hide();
            resolve(selected.length > 0 ? selected : undefined);
        });

        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve(undefined);
        });

        quickPick.show();
    });

    if (!selectedShells) {
        return undefined;
    }

    // Then, select default shell from available shells
    const defaultShellItems: vscode.QuickPickItem[] = selectedShells.map(shell => ({
        label: shell,
        description: shell === defaultShell ? '(current default)' : undefined
    }));

    const selectedDefault = await vscode.window.showQuickPick(defaultShellItems, {
        title: 'Select Default Shell',
        placeHolder: 'Select the default shell type'
    });

    if (!selectedDefault) {
        return undefined;
    }

    return {
        shells: selectedShells,
        defaultShell: selectedDefault.label
    };
}

/**
 * Show commit language selection
 */
export async function showCommitLanguageSelection(currentLanguage: string): Promise<string | undefined> {
    const items: vscode.QuickPickItem[] = COMMIT_LANGUAGE_OPTIONS.map(opt => ({
        label: opt.label,
        description: opt.code === currentLanguage ? '(current)' : undefined,
        detail: opt.code
    }));

    const selected = await vscode.window.showQuickPick(items, {
        title: 'Select Commit Message Language',
        placeHolder: 'AI will generate commit messages in this language'
    });

    if (!selected) {
        return undefined;
    }

    return COMMIT_LANGUAGE_OPTIONS.find(opt => opt.label === selected.label)?.code;
}

/**
 * Show server level selection
 */
export async function showServerLevelSelection(currentLevel: McpServerLevel): Promise<McpServerLevel | undefined> {
    const items: QuickPickItemWithData<McpServerLevel>[] = [
        {
            label: 'Global',
            description: 'Available in all projects',
            picked: currentLevel !== McpServerLevel.PROJECT,
            data: McpServerLevel.GLOBAL
        },
        {
            label: 'Project',
            description: 'Available only in current project',
            picked: currentLevel === McpServerLevel.PROJECT,
            data: McpServerLevel.PROJECT
        }
    ];

    const selected = await vscode.window.showQuickPick(items, {
        title: 'Select Server Level',
        placeHolder: 'Choose where this MCP server should be available'
    });

    return selected?.data;
}

/**
 * Show text area input using multi-line input box
 */
export async function showTextAreaInput(
    title: string,
    currentValue: string,
    placeholder?: string
): Promise<string | undefined> {
    // VS Code doesn't have native multi-line input, use input box with instruction
    const result = await vscode.window.showInputBox({
        title,
        value: currentValue,
        placeHolder: placeholder || 'Enter text (use \\n for newlines)',
        prompt: 'For multi-line text, edit in settings or use \\n for newlines'
    });

    return result;
}

/**
 * Show folder picker for external access rules
 */
export async function showFolderPicker(): Promise<string | undefined> {
    const result = await vscode.window.showOpenDialog({
        canSelectFiles: false,
        canSelectFolders: true,
        canSelectMany: false,
        title: 'Select External Access Folder'
    });

    return result?.[0]?.fsPath;
}

/**
 * Show external access rules editor
 */
export async function showExternalRulesEditor(currentRules: string[]): Promise<string[] | undefined> {
    const quickPick = vscode.window.createQuickPick();
    quickPick.title = 'External Access Rules';
    quickPick.placeholder = 'Manage external access paths (use buttons below)';
    
    const updateItems = () => {
        quickPick.items = [
            { label: '$(add) Add Path', description: 'Add a new external access path' },
            { label: '$(trash) Remove Selected', description: 'Remove the selected path' },
            { label: '$(check) Done', description: 'Save and close' },
            { label: '', kind: vscode.QuickPickItemKind.Separator },
            ...currentRules.map(rule => ({ label: rule, description: 'External path' }))
        ];
    };

    updateItems();

    return new Promise((resolve) => {
        quickPick.onDidAccept(async () => {
            const selected = quickPick.selectedItems[0];
            if (!selected) return;

            if (selected.label === '$(add) Add Path') {
                const newPath = await showFolderPicker();
                if (newPath && !currentRules.includes(newPath)) {
                    currentRules.push(newPath);
                    updateItems();
                }
                quickPick.show();
            } else if (selected.label === '$(trash) Remove Selected') {
                // Show selection to remove
                const toRemove = await vscode.window.showQuickPick(
                    currentRules.map(rule => ({ label: rule })),
                    { title: 'Select path to remove', placeHolder: 'Choose a path to remove' }
                );
                if (toRemove) {
                    const index = currentRules.indexOf(toRemove.label);
                    if (index >= 0) {
                        currentRules.splice(index, 1);
                        updateItems();
                    }
                }
                quickPick.show();
            } else if (selected.label === '$(check) Done') {
                quickPick.hide();
                resolve([...currentRules]);
            }
        });

        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve(undefined);
        });

        quickPick.show();
    });
}

/**
 * Show disabled tools editor
 */
export async function showDisabledToolsEditor(
    title: string,
    currentTools: string[],
    defaultTools: string[]
): Promise<string[] | undefined> {
    const quickPick = vscode.window.createQuickPick();
    quickPick.title = title;
    quickPick.placeholder = 'Manage disabled tools';
    
    const tools = [...currentTools];

    const updateItems = () => {
        quickPick.items = [
            { label: '$(add) Add Tool', description: 'Add a tool to disable' },
            { label: '$(trash) Remove Tool', description: 'Remove a tool from the list' },
            { label: '$(sync) Reset to Default', description: 'Reset to default disabled tools' },
            { label: '$(check) Done', description: 'Save and close' },
            { label: '', kind: vscode.QuickPickItemKind.Separator },
            ...tools.map(tool => ({ label: tool, description: 'Disabled tool' }))
        ];
    };

    updateItems();

    return new Promise((resolve) => {
        quickPick.onDidAccept(async () => {
            const selected = quickPick.selectedItems[0];
            if (!selected) return;

            if (selected.label === '$(add) Add Tool') {
                const newTool = await vscode.window.showInputBox({
                    title: 'Add Disabled Tool',
                    placeHolder: 'Enter tool name to disable'
                });
                if (newTool && !tools.includes(newTool)) {
                    tools.push(newTool);
                    updateItems();
                }
                quickPick.show();
            } else if (selected.label === '$(trash) Remove Tool') {
                if (tools.length > 0) {
                    const toRemove = await vscode.window.showQuickPick(
                        tools.map(tool => ({ label: tool })),
                        { title: 'Select tool to remove' }
                    );
                    if (toRemove) {
                        const index = tools.indexOf(toRemove.label);
                        if (index >= 0) {
                            tools.splice(index, 1);
                            updateItems();
                        }
                    }
                }
                quickPick.show();
            } else if (selected.label === '$(sync) Reset to Default') {
                tools.length = 0;
                tools.push(...defaultTools);
                updateItems();
                quickPick.show();
            } else if (selected.label === '$(check) Done') {
                quickPick.hide();
                resolve([...tools]);
            }
        });

        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve(undefined);
        });

        quickPick.show();
    });
}

/**
 * Show Codex features editor
 */
export async function showCodexFeaturesEditor(
    currentFeatures: string[],
    defaultFeatures: string[]
): Promise<string[] | undefined> {
    const quickPick = vscode.window.createQuickPick();
    quickPick.title = 'Disabled Codex Features';
    quickPick.placeholder = 'Manage disabled Codex features';
    
    const features = [...currentFeatures];

    const updateItems = () => {
        quickPick.items = [
            { label: '$(add) Add Feature', description: `Available: ${CODEX_FEATURES.join(', ')}` },
            { label: '$(trash) Remove Feature', description: 'Remove a feature from the list' },
            { label: '$(sync) Reset to Default', description: 'Reset to default disabled features' },
            { label: '$(check) Done', description: 'Save and close' },
            { label: '', kind: vscode.QuickPickItemKind.Separator },
            ...features.map(feature => ({ label: feature, description: 'Disabled feature' }))
        ];
    };

    updateItems();

    return new Promise((resolve) => {
        quickPick.onDidAccept(async () => {
            const selected = quickPick.selectedItems[0];
            if (!selected) return;

            if (selected.label === '$(add) Add Feature') {
                // Show available features to select
                const availableFeatures = CODEX_FEATURES.filter(f => !features.includes(f));
                if (availableFeatures.length > 0) {
                    const newFeature = await vscode.window.showQuickPick(
                        availableFeatures.map(f => ({ label: f })),
                        { title: 'Select feature to disable' }
                    );
                    if (newFeature && !features.includes(newFeature.label)) {
                        features.push(newFeature.label);
                        updateItems();
                    }
                } else {
                    vscode.window.showInformationMessage('All available features are already disabled');
                }
                quickPick.show();
            } else if (selected.label === '$(trash) Remove Feature') {
                if (features.length > 0) {
                    const toRemove = await vscode.window.showQuickPick(
                        features.map(f => ({ label: f })),
                        { title: 'Select feature to remove' }
                    );
                    if (toRemove) {
                        const index = features.indexOf(toRemove.label);
                        if (index >= 0) {
                            features.splice(index, 1);
                            updateItems();
                        }
                    }
                }
                quickPick.show();
            } else if (selected.label === '$(sync) Reset to Default') {
                features.length = 0;
                features.push(...defaultFeatures);
                updateItems();
                quickPick.show();
            } else if (selected.label === '$(check) Done') {
                quickPick.hide();
                resolve([...features]);
            }
        });

        quickPick.onDidHide(() => {
            quickPick.dispose();
            resolve(undefined);
        });

        quickPick.show();
    });
}

/**
 * Show auto-approved tools editor
 */
export async function showAutoApprovedToolsEditor(
    currentTools: string[],
    defaultTools: string[]
): Promise<string[] | undefined> {
    return showDisabledToolsEditor(
        'Auto-Approved Tools (Codex)',
        currentTools,
        defaultTools
    );
}

/**
 * Parse external rules from JSON string
 */
export function parseExternalRules(jsonString: string): string[] {
    try {
        const parsed = JSON.parse(jsonString);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

/**
 * Serialize external rules to JSON string
 */
export function serializeExternalRules(rules: string[]): string {
    return JSON.stringify(rules);
}
