/**
 * File History Handler
 * Handles file history and rollback operations
 */

import * as vscode from 'vscode';
import * as fs from 'fs/promises';
import * as path from 'path';
import { historyStore } from './historyStore';

export interface FileHistoryEntry {
    timestamp: number;
    filePath: string;
    content: string;
    toolUseId?: string;
}

export interface RollbackResult {
    success: boolean;
    filePath: string;
    error?: string;
}

export class FileHistoryHandler {
    private static instance: FileHistoryHandler | null = null;

    private constructor() {}

    static getInstance(): FileHistoryHandler {
        if (!FileHistoryHandler.instance) {
            FileHistoryHandler.instance = new FileHistoryHandler();
        }
        return FileHistoryHandler.instance;
    }

    /**
     * Get file modification history
     */
    async getHistory(filePath: string): Promise<FileHistoryEntry[]> {
        return historyStore.getHistory(filePath);
    }

    /**
     * Rollback file to a specific timestamp
     */
    async rollbackToTimestamp(filePath: string, timestamp: number): Promise<RollbackResult> {
        try {
            const content = historyStore.getSnapshotContent(filePath, timestamp);
            if (content === undefined) {
                return {
                    success: false,
                    filePath,
                    error: `No snapshot found for timestamp ${timestamp}`
                };
            }

            const absolutePath = this.resolvePath(filePath);
            await fs.writeFile(absolutePath, content, 'utf-8');

            return { success: true, filePath };
        } catch (error) {
            return {
                success: false,
                filePath,
                error: error instanceof Error ? error.message : String(error)
            };
        }
    }

    /**
     * Batch rollback multiple files
     */
    async batchRollback(
        files: Array<{ filePath: string; timestamp: number }>
    ): Promise<RollbackResult[]> {
        const results: RollbackResult[] = [];

        for (const { filePath, timestamp } of files) {
            const result = await this.rollbackToTimestamp(filePath, timestamp);
            results.push(result);
        }

        return results;
    }

    /**
     * Get the original content before a specific timestamp
     */
    getOriginalContent(filePath: string, beforeTimestamp: number): string | undefined {
        return historyStore.getSnapshotBefore(filePath, beforeTimestamp);
    }

    /**
     * Check if file can be rolled back
     */
    canRollback(filePath: string): boolean {
        const history = historyStore.getHistory(filePath);
        return history.length > 0;
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

export const fileHistoryHandler = FileHistoryHandler.getInstance();
