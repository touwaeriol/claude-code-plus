/**
 * Edit Tool Click Handler
 * Shows diff when user clicks on Edit tool card
 */

import * as vscode from 'vscode';
import * as path from 'path';
import { ToolClickHandler, ToolClickContext } from './toolClickHandler';

export class EditToolHandler implements ToolClickHandler {
    canHandle(toolName: string): boolean {
        return toolName === 'Edit' || toolName === 'mcp__jetbrains-file__EditFile';
    }

    async handle(context: ToolClickContext): Promise<void> {
        const filePath = context.input.filePath as string || context.filePath;
        const oldString = context.input.oldString as string;
        const newString = context.input.newString as string;
        
        if (!filePath) {
            vscode.window.showWarningMessage('No file path specified');
            return;
        }

        const absolutePath = this.resolvePath(filePath);
        const uri = vscode.Uri.file(absolutePath);
        const title = `Edit: ${path.basename(filePath)}`;

        try {
            const doc = await vscode.workspace.openTextDocument(uri);
            const currentContent = doc.getText();
            
            // Apply the edit to get new content
            const newContent = currentContent.replace(oldString, newString);

            // Show diff using VS Code's diff command
            await vscode.commands.executeCommand('vscode.diff', uri, uri, title);
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
