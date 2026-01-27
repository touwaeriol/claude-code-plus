/**
 * VS Code IDE Integration
 * 
 * VS Code implementation of IdeIntegration interface
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/adapters/IdeaIdeIntegration.kt
 */

import * as vscode from 'vscode';
import * as path from 'path';
import { IdeIntegration, NotificationType } from './ideIntegration';
import { LegacyToolCall } from '../types';
import { ToolConstants } from '../types/toolConstants';

/**
 * VS Code IDE Integration implementation
 */
export class VscodeIdeIntegration implements IdeIntegration {
    private static instance: VscodeIdeIntegration | undefined;
    
    private constructor() {}
    
    static getInstance(): VscodeIdeIntegration {
        if (!this.instance) {
            this.instance = new VscodeIdeIntegration();
        }
        return this.instance;
    }
    
    /**
     * Handle tool click event
     */
    async handleToolClick(toolCall: LegacyToolCall): Promise<boolean> {
        const { name, input } = toolCall;
        
        try {
            switch (name) {
                case ToolConstants.READ:
                case ToolConstants.WRITE:
                case ToolConstants.EDIT:
                case ToolConstants.MULTI_EDIT: {
                    const filePath = input?.file_path as string ?? input?.path as string;
                    if (filePath) {
                        return await this.openFile(filePath);
                    }
                    break;
                }
                
                case ToolConstants.NOTEBOOK_EDIT: {
                    const notebookPath = input?.notebook_path as string;
                    if (notebookPath) {
                        return await this.openFile(notebookPath);
                    }
                    break;
                }
                
                default:
                    // Unknown tool type, do nothing
                    return false;
            }
        } catch (error) {
            console.error('Failed to handle tool click:', error);
            return false;
        }
        
        return false;
    }
    
    /**
     * Open file in editor
     */
    async openFile(filePath: string, line?: number, column?: number): Promise<boolean> {
        try {
            const absolutePath = this.resolveAbsolutePath(filePath);
            const uri = vscode.Uri.file(absolutePath);
            
            const document = await vscode.workspace.openTextDocument(uri);
            const editor = await vscode.window.showTextDocument(document);
            
            // Navigate to specific line/column if provided
            if (line !== undefined && line > 0) {
                const position = new vscode.Position(
                    line - 1, // Convert to 0-based
                    (column ?? 1) - 1
                );
                editor.selection = new vscode.Selection(position, position);
                editor.revealRange(
                    new vscode.Range(position, position),
                    vscode.TextEditorRevealType.InCenter
                );
            }
            
            return true;
        } catch (error) {
            console.error('Failed to open file:', error);
            return false;
        }
    }
    
    /**
     * Show file diff
     */
    async showDiff(filePath: string, oldContent: string, newContent: string): Promise<boolean> {
        try {
            const fileName = path.basename(filePath);
            
            // Create virtual documents for diff
            const oldUri = vscode.Uri.parse(`untitled:${fileName}.old`);
            const newUri = vscode.Uri.parse(`untitled:${fileName}.new`);
            
            // Show diff
            await vscode.commands.executeCommand(
                'vscode.diff',
                oldUri,
                newUri,
                `${fileName}: Original ↔ Modified`
            );
            
            return true;
        } catch (error) {
            console.error('Failed to show diff:', error);
            return false;
        }
    }
    
    /**
     * Show notification
     */
    showNotification(message: string, type: NotificationType): void {
        switch (type) {
            case NotificationType.INFO:
                vscode.window.showInformationMessage(message);
                break;
            case NotificationType.WARNING:
                vscode.window.showWarningMessage(message);
                break;
            case NotificationType.ERROR:
                vscode.window.showErrorMessage(message);
                break;
        }
    }
    
    /**
     * Check if VS Code integration is supported
     */
    isSupported(): boolean {
        return true; // Always supported in VS Code
    }
    
    /**
     * Get IDE locale
     */
    getIdeLocale(): string {
        return vscode.env.language || 'en';
    }
    
    /**
     * Resolve absolute path from relative path
     */
    private resolveAbsolutePath(filePath: string): string {
        if (path.isAbsolute(filePath)) {
            return filePath;
        }
        
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (workspaceFolder) {
            return path.join(workspaceFolder.uri.fsPath, filePath);
        }
        
        return filePath;
    }
}

// Export singleton instance
export const vscodeIdeIntegration = VscodeIdeIntegration.getInstance();
