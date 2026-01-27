/**
 * Project Service Adapter
 * 
 * Implements ProjectService interface for VS Code
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/adapters/ProjectServiceAdapter.kt
 */

import * as vscode from 'vscode';
import * as path from 'path';

/**
 * Project Service interface
 */
export interface ProjectService {
    getProjectPath(): string;
    getProjectName(): string;
    getRelativePath(absolutePath: string): string;
    openFile(filePath: string, lineNumber?: number): Promise<void>;
    showSettings(settingsId?: string): Promise<void>;
}

/**
 * Project Service Adapter for VS Code
 */
export class ProjectServiceAdapter implements ProjectService {
    private projectPath: string;
    private projectName: string;
    
    constructor(projectPath?: string, projectName?: string) {
        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        this.projectPath = projectPath ?? workspaceFolder?.uri.fsPath ?? '';
        this.projectName = projectName ?? workspaceFolder?.name ?? 'Unknown';
    }
    
    getProjectPath(): string {
        return this.projectPath;
    }
    
    getProjectName(): string {
        return this.projectName;
    }
    
    getRelativePath(absolutePath: string): string {
        if (absolutePath.startsWith(this.projectPath)) {
            const relativePath = absolutePath.substring(this.projectPath.length);
            return relativePath.replace(/^[/\\]+/, '');
        }
        return absolutePath;
    }
    
    async openFile(filePath: string, lineNumber?: number): Promise<void> {
        try {
            const absolutePath = path.isAbsolute(filePath) 
                ? filePath 
                : path.join(this.projectPath, filePath);
            
            const uri = vscode.Uri.file(absolutePath);
            const document = await vscode.workspace.openTextDocument(uri);
            const editor = await vscode.window.showTextDocument(document);
            
            if (lineNumber !== undefined && lineNumber > 0) {
                const position = new vscode.Position(lineNumber - 1, 0);
                editor.selection = new vscode.Selection(position, position);
                editor.revealRange(
                    new vscode.Range(position, position),
                    vscode.TextEditorRevealType.InCenter
                );
            }
        } catch (error) {
            console.error('Failed to open file:', error);
            vscode.window.showErrorMessage(`Failed to open file: ${filePath}`);
        }
    }
    
    async showSettings(settingsId?: string): Promise<void> {
        if (settingsId) {
            await vscode.commands.executeCommand(
                'workbench.action.openSettings',
                `claudeCodePlus.${settingsId}`
            );
        } else {
            await vscode.commands.executeCommand(
                'workbench.action.openSettings',
                'claudeCodePlus'
            );
        }
    }
}

/**
 * Create project service adapter from current workspace
 */
export function createProjectServiceAdapter(): ProjectServiceAdapter {
    return new ProjectServiceAdapter();
}
