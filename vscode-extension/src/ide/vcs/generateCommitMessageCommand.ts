/**
 * Generate Commit Message Command
 * 
 * Source: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/vcs/GenerateCommitMessageAction.kt
 * 
 * VS Code command that triggers AI-powered commit message generation.
 * Replaces IDEA's AnAction for the commit message toolbar button.
 * 
 * Features:
 * - Integrates with GenerateCommitMessageService
 * - Shows progress notification during generation
 * - Supports both Claude and Codex backends
 * - Validates that changes exist before generation
 */

import * as vscode from 'vscode';
import { getLogger } from '../../logging/logger';
import { getCommitPanelAccessor } from './commitPanelAccessor';
import { GenerateCommitMessageService } from './generateCommitMessageService';

const logger = getLogger('GenerateCommitMessageCommand');

/**
 * Command ID for generate commit message
 */
export const GENERATE_COMMIT_MESSAGE_COMMAND = 'claudeCodePlus.generateCommitMessage';

/**
 * Backend type for commit message generation
 */
export type GenerateBackend = 'claude' | 'codex';

/**
 * Configuration for commit message generation
 */
export interface GenerateCommitMessageConfig {
    /** Whether the feature is enabled */
    enabled: boolean;
    /** Backend to use (claude or codex) */
    backend: GenerateBackend;
    /** Custom system prompt */
    systemPrompt?: string;
    /** Custom user prompt */
    userPrompt?: string;
}

/**
 * Get configuration from VS Code settings
 */
function getConfig(): GenerateCommitMessageConfig {
    const config = vscode.workspace.getConfiguration('claudeCodePlus');
    return {
        enabled: config.get<boolean>('git.generateCommitMessage.enabled', true),
        backend: config.get<GenerateBackend>('git.generateCommitMessage.backend', 'claude'),
        systemPrompt: config.get<string>('git.generateCommitMessage.systemPrompt'),
        userPrompt: config.get<string>('git.generateCommitMessage.userPrompt')
    };
}

/**
 * Check if the command should be enabled
 */
export function canExecuteGenerateCommitMessage(): boolean {
    const config = getConfig();
    if (!config.enabled) {
        return false;
    }

    const accessor = getCommitPanelAccessor();
    const selectedChanges = accessor.getSelectedChanges();
    const allChanges = accessor.getAllChanges();

    // Enable if there are selected changes or any changes at all
    return (selectedChanges && selectedChanges.length > 0) || allChanges.length > 0;
}

/**
 * Execute the generate commit message command
 */
export async function executeGenerateCommitMessageCommand(): Promise<void> {
    const config = getConfig();

    if (!config.enabled) {
        vscode.window.showWarningMessage('Commit message generation is disabled in settings.');
        return;
    }

    logger.info('Generate commit message command triggered', { backend: config.backend });

    const accessor = getCommitPanelAccessor();
    const selectedChanges = accessor.getSelectedChanges();
    const allChanges = accessor.getAllChanges();

    // Validate changes exist
    const hasChanges = (selectedChanges && selectedChanges.length > 0) || allChanges.length > 0;
    if (!hasChanges) {
        vscode.window.showWarningMessage('No changes to generate commit message for.');
        return;
    }

    // Show progress and generate
    await vscode.window.withProgress(
        {
            location: vscode.ProgressLocation.Notification,
            title: `Generating Commit Message (${config.backend})`,
            cancellable: true
        },
        async (progress, token) => {
            try {
                progress.report({ message: 'Analyzing changes...' });

                const service = GenerateCommitMessageService.getInstance();
                
                const result = await service.generateCommitMessage({
                    systemPrompt: config.systemPrompt,
                    userPrompt: config.userPrompt,
                    onProgress: (message) => {
                        progress.report({ message });
                    }
                });

                if (token.isCancellationRequested) {
                    logger.info('Commit message generation cancelled by user');
                    return;
                }

                if (!result.success) {
                    vscode.window.showErrorMessage(
                        `Failed to generate commit message: ${result.error}`
                    );
                    return;
                }

                // Message was already set by the service
                vscode.window.showInformationMessage(
                    'Commit message generated successfully'
                );

                logger.info('Commit message generated', { 
                    messageLength: result.message?.length,
                    backend: config.backend 
                });

            } catch (error) {
                const errorMessage = error instanceof Error ? error.message : String(error);
                logger.error('Failed to generate commit message', error instanceof Error ? error : new Error(errorMessage));
                vscode.window.showErrorMessage(
                    `Failed to generate commit message: ${errorMessage}`
                );
            }
        }
    );
}

/**
 * Register the generate commit message command
 */
export function registerGenerateCommitMessageCommand(context: vscode.ExtensionContext): void {
    // Register the command
    const command = vscode.commands.registerCommand(
        GENERATE_COMMIT_MESSAGE_COMMAND,
        executeGenerateCommitMessageCommand
    );

    context.subscriptions.push(command);

    logger.info('Generate commit message command registered');
}

/**
 * Create a SCM title action for the generate button
 * This adds a button to the Source Control title bar
 */
export function createScmTitleAction(): vscode.Disposable {
    // VS Code doesn't have direct API for SCM title buttons like IDEA's message toolbar
    // The button is added via package.json "menus" > "scm/title" contribution
    // This function returns a disposable for consistency

    return {
        dispose: () => {
            // No-op, as the menu contribution is declarative
        }
    };
}

/**
 * Command handler with icon selection based on backend
 */
export class GenerateCommitMessageCommandHandler {
    private static instance: GenerateCommitMessageCommandHandler | undefined;

    private constructor() {}

    static getInstance(): GenerateCommitMessageCommandHandler {
        if (!this.instance) {
            this.instance = new GenerateCommitMessageCommandHandler();
        }
        return this.instance;
    }

    /**
     * Get the icon ID based on current backend
     */
    getIconId(): string {
        const config = getConfig();
        return config.backend === 'codex' ? 'codex-ai' : 'claude-ai';
    }

    /**
     * Get the description based on current backend
     */
    getDescription(): string {
        const config = getConfig();
        return config.backend === 'codex'
            ? 'Use Codex AI to generate commit message based on selected changes'
            : 'Use Claude AI to generate commit message based on selected changes';
    }

    /**
     * Check if the command is available
     */
    isAvailable(): boolean {
        return canExecuteGenerateCommitMessage();
    }

    /**
     * Execute the command
     */
    async execute(): Promise<void> {
        await executeGenerateCommitMessageCommand();
    }
}

// Export for convenience
export function getGenerateCommitMessageHandler(): GenerateCommitMessageCommandHandler {
    return GenerateCommitMessageCommandHandler.getInstance();
}
