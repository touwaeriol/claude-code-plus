/**
 * Read Tool Click Handler
 * Opens file when user clicks on Read tool card
 */

import * as vscode from 'vscode';
import * as path from 'path';
import { ToolClickHandler, ToolClickContext } from './toolClickHandler';

export class ReadToolHandler implements ToolClickHandler {
    canHandle(toolName: string): boolean {
        return toolName === 'Read' || toolName === 'mcp__jetbrains-file__ReadFile';
    }

    async handle(context: ToolClickContext): Promise<void> {
        const filePath = context.input.filePath as string || context.filePath;
        if (!filePath) {
            vscode.window.showWarningMessage('No file path specified');
            return;
        }

        const absolutePath = this.resolvePath(filePath);
        const uri = vscode.Uri.file(absolutePath);

        try {
            const doc = await vscode.workspace.openTextDocument(uri);
            await vscode.window.showTextDocument(doc);
        } catch (error) {
            vscode.window.showErrorMessage(`Failed to open file: ${filePath}`);
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
