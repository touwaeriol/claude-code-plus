/**
 * Notification Service
 * Provides VS Code notifications
 */

import * as vscode from 'vscode';

export type NotificationAction = {
    title: string;
    callback: () => void | Promise<void>;
};

export class NotificationService {
    private static instance: NotificationService | null = null;

    private constructor() {}

    static getInstance(): NotificationService {
        if (!NotificationService.instance) {
            NotificationService.instance = new NotificationService();
        }
        return NotificationService.instance;
    }

    /**
     * Show information notification
     */
    info(message: string): void {
        vscode.window.showInformationMessage(message);
    }

    /**
     * Show warning notification
     */
    warn(message: string): void {
        vscode.window.showWarningMessage(message);
    }

    /**
     * Show error notification
     */
    error(message: string): void {
        vscode.window.showErrorMessage(message);
    }

    /**
     * Show information with action buttons
     */
    async infoWithActions(message: string, actions: NotificationAction[]): Promise<void> {
        const titles = actions.map(a => a.title);
        const result = await vscode.window.showInformationMessage(message, ...titles);
        
        if (result) {
            const action = actions.find(a => a.title === result);
            if (action) {
                await action.callback();
            }
        }
    }

    /**
     * Show warning with action buttons
     */
    async warnWithActions(message: string, actions: NotificationAction[]): Promise<void> {
        const titles = actions.map(a => a.title);
        const result = await vscode.window.showWarningMessage(message, ...titles);
        
        if (result) {
            const action = actions.find(a => a.title === result);
            if (action) {
                await action.callback();
            }
        }
    }

    /**
     * Show error with action buttons
     */
    async errorWithActions(message: string, actions: NotificationAction[]): Promise<void> {
        const titles = actions.map(a => a.title);
        const result = await vscode.window.showErrorMessage(message, ...titles);
        
        if (result) {
            const action = actions.find(a => a.title === result);
            if (action) {
                await action.callback();
            }
        }
    }

    /**
     * Show progress notification
     */
    async withProgress<T>(
        title: string,
        task: (progress: vscode.Progress<{ message?: string; increment?: number }>) => Promise<T>
    ): Promise<T> {
        return vscode.window.withProgress(
            {
                location: vscode.ProgressLocation.Notification,
                title,
                cancellable: false
            },
            task
        );
    }
}

export const notificationService = NotificationService.getInstance();
