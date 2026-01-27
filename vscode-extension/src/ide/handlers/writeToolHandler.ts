/**
 * Write Tool Click Handler
 * Shows diff when user clicks on Write tool card
 */

import * as vscode from 'vscode';
import * as path from 'path';
import { ToolClickHandler, ToolClickContext } from './toolClickHandler';

export class WriteToolHandler implements ToolClickHandler {
    canHandle(toolName: string): boolean {
        return toolName === 'Write' || toolName === 'mcp__jetbrains-file__WriteFile';
    }

    async handle(context: ToolClickContext): Promise<void> {
        const filePath = context.input.filePath as string || context.filePath;
        const newContent = context.input.content as string;
        
        if (!filePath) {
            vscode.window.showWarningMessage('No file path specified');
            return;
        }

        const absolutePath = this.resolvePath(filePath);
        const uri = vscode.Uri.file(absolutePath);
        const title = `Write: ${path.basename(filePath)}`;

        try {
            // Try to read existing content
            let oldContent = '';
            try {
                const doc = await vscode.workspace.openTextDocument(uri);
                oldContent = doc.getText();
            } catch {
                // File doesn't exist yet, show as new file
            }

            // Create virtual documents for diff
            const oldUri = vscode.Uri.parse(`untitled:${filePath}.before`);
            const newUri = vscode.Uri.parse(`untitled:${filePath}.after`);

            // Show diff
            await vscode.commands.executeCommand('vscode.diff', oldUri, newUri, title);
        } catch (error) {
            vscode.window.showErrorMessage(`Failed to show diff: ${error}`);
        }
    }

    private resolvePath(filePath: string): string {
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
