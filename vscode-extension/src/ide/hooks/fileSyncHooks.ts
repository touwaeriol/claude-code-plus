/**
 * File Sync Hooks
 * 
 * Provides PRE_TOOL_USE and POST_TOOL_USE hooks for synchronizing
 * file state between VS Code and disk.
 * 
 * - PRE_TOOL_USE: Save modified files to disk (ensures Claude reads/edits latest content)
 * - POST_TOOL_USE: Refresh disk files to VS Code (ensures VS Code shows Claude's modifications)
 * 
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/hooks/IdeaFileSyncHooks.kt
 */

import * as vscode from 'vscode';
import * as path from 'path';

/**
 * Tool type enum (matching SDK types)
 */
export enum ToolType {
    READ = 'Read',
    WRITE = 'Write',
    EDIT = 'Edit',
    MULTI_EDIT = 'MultiEdit',
    NOTEBOOK_EDIT = 'NotebookEdit'
}

/**
 * Hook event types
 */
export enum HookEvent {
    PRE_TOOL_USE = 'PRE_TOOL_USE',
    POST_TOOL_USE = 'POST_TOOL_USE'
}

/**
 * Tool call information for hooks
 */
export interface HookToolCall {
    toolName: string;
    input: Record<string, unknown>;
}

/**
 * Hook handler function type
 */
export type HookHandler = (toolCall: HookToolCall) => Promise<void>;

/**
 * File sync tool configuration
 */
interface FileSyncToolConfig {
    toolType: ToolType;
    needSaveBeforeUse: boolean;
    needRefreshAfterUse: boolean;
    filePathParam: string;
}

/**
 * Tool configurations for file sync
 */
const FILE_SYNC_TOOLS: FileSyncToolConfig[] = [
    { toolType: ToolType.READ, needSaveBeforeUse: true, needRefreshAfterUse: false, filePathParam: 'file_path' },
    { toolType: ToolType.WRITE, needSaveBeforeUse: true, needRefreshAfterUse: true, filePathParam: 'file_path' },
    { toolType: ToolType.EDIT, needSaveBeforeUse: true, needRefreshAfterUse: true, filePathParam: 'file_path' },
    { toolType: ToolType.MULTI_EDIT, needSaveBeforeUse: true, needRefreshAfterUse: true, filePathParam: 'file_path' },
    { toolType: ToolType.NOTEBOOK_EDIT, needSaveBeforeUse: true, needRefreshAfterUse: true, filePathParam: 'notebook_path' }
];

/**
 * File Sync Hooks
 */
export class FileSyncHooks {
    private static instance: FileSyncHooks | undefined;
    
    private constructor() {}
    
    static getInstance(): FileSyncHooks {
        if (!this.instance) {
            this.instance = new FileSyncHooks();
        }
        return this.instance;
    }
    
    /**
     * Get tools that need save before use (regex pattern)
     */
    getPreMatcher(): string {
        return FILE_SYNC_TOOLS
            .filter(t => t.needSaveBeforeUse)
            .map(t => t.toolType)
            .join('|');
    }
    
    /**
     * Get tools that need refresh after use (regex pattern)
     */
    getPostMatcher(): string {
        return FILE_SYNC_TOOLS
            .filter(t => t.needRefreshAfterUse)
            .map(t => t.toolType)
            .join('|');
    }
    
    /**
     * Get tool config by name
     */
    private getToolConfig(toolName: string): FileSyncToolConfig | undefined {
        return FILE_SYNC_TOOLS.find(t => t.toolType === toolName);
    }
    
    /**
     * Extract file path from tool call
     */
    private extractFilePath(toolCall: HookToolCall): string | undefined {
        const config = this.getToolConfig(toolCall.toolName);
        if (!config) return undefined;
        
        const filePath = toolCall.input[config.filePathParam] as string | undefined;
        return filePath && filePath.trim() ? filePath.trim() : undefined;
    }
    
    /**
     * Create pre-tool-use handler
     * Saves VS Code documents to disk before Claude reads/edits
     */
    createPreToolUseHandler(): HookHandler {
        return async (toolCall: HookToolCall) => {
            const filePath = this.extractFilePath(toolCall);
            if (!filePath) return;
            
            console.log(`📥 [PRE] ${toolCall.toolName}: Saving file to disk: ${filePath}`);
            await this.saveDocument(filePath);
        };
    }
    
    /**
     * Create post-tool-use handler
     * Refreshes disk files to VS Code after Claude modifications
     */
    createPostToolUseHandler(): HookHandler {
        return async (toolCall: HookToolCall) => {
            const filePath = this.extractFilePath(toolCall);
            if (!filePath) return;
            
            console.log(`📤 [POST] ${toolCall.toolName}: Refreshing file to VS Code: ${filePath}`);
            await this.refreshFile(filePath);
            console.log(`✅ [POST] ${toolCall.toolName}: File refreshed`);
        };
    }
    
    /**
     * Save document to disk
     */
    private async saveDocument(filePath: string): Promise<void> {
        try {
            const absolutePath = this.resolveAbsolutePath(filePath);
            const uri = vscode.Uri.file(absolutePath);
            
            // Find open document
            const document = vscode.workspace.textDocuments.find(
                doc => doc.uri.fsPath === uri.fsPath
            );
            
            if (document && document.isDirty) {
                await document.save();
            }
        } catch (error) {
            console.error('Failed to save document:', error);
        }
    }
    
    /**
     * Refresh file from disk
     */
    private async refreshFile(filePath: string): Promise<void> {
        try {
            const absolutePath = this.resolveAbsolutePath(filePath);
            const uri = vscode.Uri.file(absolutePath);
            
            // Find open document and refresh it
            const document = vscode.workspace.textDocuments.find(
                doc => doc.uri.fsPath === uri.fsPath
            );
            
            if (document) {
                // VS Code doesn't have a direct refresh API
                // We can re-open the document to refresh its content
                const editor = vscode.window.visibleTextEditors.find(
                    e => e.document.uri.fsPath === uri.fsPath
                );
                
                if (editor) {
                    // Force refresh by reverting
                    await vscode.commands.executeCommand('workbench.action.files.revert');
                }
            }
        } catch (error) {
            console.error('Failed to refresh file:', error);
        }
    }
    
    /**
     * Resolve absolute path
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
export const fileSyncHooks = FileSyncHooks.getInstance();
