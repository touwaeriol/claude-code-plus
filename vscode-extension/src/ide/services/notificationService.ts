/**
 * Notification Service
 * 
 * Uses VS Code Notification API for background task notifications
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/NotificationService.kt
 */

import * as vscode from 'vscode';

/**
 * Notification Service
 * 
 * Handles notifications for Claude Code Plus
 */
export class NotificationService {
    private static instance: NotificationService | undefined;
    
    private constructor() {}
    
    static getInstance(): NotificationService {
        if (!this.instance) {
            this.instance = new NotificationService();
        }
        return this.instance;
    }
    
    /**
     * Show information notification
     */
    showInfo(title: string, content?: string): void {
        const message = content ? `${title}: ${content}` : title;
        vscode.window.showInformationMessage(message);
    }
    
    /**
     * Show warning notification
     */
    showWarning(title: string, content?: string): void {
        const message = content ? `${title}: ${content}` : title;
        vscode.window.showWarningMessage(message);
    }
    
    /**
     * Show error notification
     */
    showError(title: string, content?: string): void {
        const message = content ? `${title}: ${content}` : title;
        vscode.window.showErrorMessage(message);
    }
    
    /**
     * Show tool approval notification
     */
    async notifyToolApprovalNeeded(
        toolName: string,
        onApprove: () => void,
        onReject: () => void
    ): Promise<void> {
        const result = await vscode.window.showInformationMessage(
            `Claude requests to execute tool: ${toolName}`,
            'Approve',
            'Reject'
        );
        
        if (result === 'Approve') {
            onApprove();
        } else if (result === 'Reject') {
            onReject();
        }
    }
    
    /**
     * Show file edited notification
     */
    notifyFileEdited(filePath: string): void {
        this.showInfo('File Edited', filePath);
    }
    
    /**
     * Show task completed notification
     */
    notifyTaskCompleted(taskName: string): void {
        this.showInfo('Task Completed', taskName);
    }
    
    /**
     * Show task failed notification
     */
    notifyTaskFailed(taskName: string, error: string): void {
        this.showError('Task Failed', `${taskName}: ${error}`);
    }
    
    /**
     * Show progress notification with cancel option
     */
    async withProgress<T>(
        title: string,
        task: (
            progress: vscode.Progress<{ message?: string; increment?: number }>,
            token: vscode.CancellationToken
        ) => Promise<T>
    ): Promise<T> {
        return vscode.window.withProgress(
            {
                location: vscode.ProgressLocation.Notification,
                title,
                cancellable: true
            },
            task
        );
    }
    
    /**
     * Show status bar message
     */
    showStatusBarMessage(message: string, timeoutMs: number = 3000): vscode.Disposable {
        return vscode.window.setStatusBarMessage(message, timeoutMs);
    }
}

// Export singleton instance
export const notificationService = NotificationService.getInstance();
