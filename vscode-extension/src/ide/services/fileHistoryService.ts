/**
 * 文件历史服务
 *
 * 基于文件系统和内存快照实现文件历史内容查询和回滚。
 * 通过时间戳查询指定时间点之前的文件内容，用于 Edit/Write 工具的 Diff 显示和回滚。
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/FileHistoryService.kt
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { PathResolver } from '../util/pathResolver';

/**
 * 回滚操作结果
 */
export interface RollbackResult {
    success: boolean;
    error?: string;
}

/**
 * 文件快照
 */
interface FileSnapshot {
    filePath: string;
    content: string;
    timestamp: number;
}

/**
 * 文件历史服务
 */
export class FileHistoryService {
    private static instance: FileHistoryService | null = null;

    /**
     * 文件快照存储
     * key: 文件路径, value: 快照数组（按时间戳排序）
     */
    private snapshots: Map<string, FileSnapshot[]> = new Map();

    /**
     * 每个文件保留的最大快照数量
     */
    private readonly maxSnapshotsPerFile = 50;

    private constructor() {}

    /**
     * 获取单例实例
     */
    static getInstance(): FileHistoryService {
        if (!this.instance) {
            this.instance = new FileHistoryService();
        }
        return this.instance;
    }

    /**
     * 保存文件快照
     * 在文件被修改前调用此方法保存原始内容
     *
     * @param filePath 文件绝对路径
     * @param content 文件内容（可选，如果不提供则从文件系统读取）
     */
    async saveSnapshot(filePath: string, content?: string): Promise<void> {
        try {
            const absolutePath = PathResolver.resolve(filePath);
            let fileContent = content;

            if (!fileContent) {
                if (fs.existsSync(absolutePath)) {
                    fileContent = fs.readFileSync(absolutePath, 'utf-8');
                } else {
                    // 新文件，内容为空
                    fileContent = '';
                }
            }

            const snapshot: FileSnapshot = {
                filePath: absolutePath,
                content: fileContent,
                timestamp: Date.now(),
            };

            let fileSnapshots = this.snapshots.get(absolutePath);
            if (!fileSnapshots) {
                fileSnapshots = [];
                this.snapshots.set(absolutePath, fileSnapshots);
            }

            fileSnapshots.push(snapshot);

            // 限制快照数量
            if (fileSnapshots.length > this.maxSnapshotsPerFile) {
                fileSnapshots.shift(); // 移除最旧的快照
            }

            console.log(`📸 [FileHistory] Snapshot saved for: ${filePath} (${fileContent.length} chars, total snapshots: ${fileSnapshots.length})`);
        } catch (e) {
            console.error(`❌ [FileHistory] Failed to save snapshot for ${filePath}: ${e}`);
        }
    }

    /**
     * 获取指定时间之前的文件内容
     *
     * @param filePath 文件绝对路径
     * @param beforeTimestamp 时间戳（毫秒），获取此时间之前的版本
     * @returns 历史内容，如果不存在或获取失败则返回 null
     */
    getContentBefore(filePath: string, beforeTimestamp: number): string | null {
        try {
            const absolutePath = PathResolver.resolve(filePath);
            const fileSnapshots = this.snapshots.get(absolutePath);

            if (!fileSnapshots || fileSnapshots.length === 0) {
                console.log(`📜 [FileHistory] No snapshots found for: ${filePath}`);
                return null;
            }

            // 找到时间戳小于 beforeTimestamp 的最近快照
            let targetSnapshot: FileSnapshot | null = null;
            for (let i = fileSnapshots.length - 1; i >= 0; i--) {
                if (fileSnapshots[i].timestamp < beforeTimestamp) {
                    targetSnapshot = fileSnapshots[i];
                    break;
                }
            }

            if (!targetSnapshot) {
                console.log(`📜 [FileHistory] No history content found before timestamp ${beforeTimestamp} for: ${filePath}`);
                return null;
            }

            console.log(`📜 [FileHistory] Found history content (${targetSnapshot.content.length} chars) before ${beforeTimestamp} for: ${filePath}`);
            return targetSnapshot.content;
        } catch (e) {
            console.error(`❌ [FileHistory] Failed to get history content for ${filePath}: ${e}`);
            return null;
        }
    }

    /**
     * 获取文件的最新快照内容
     *
     * @param filePath 文件绝对路径
     * @returns 最新快照内容，如果不存在则返回 null
     */
    getLatestSnapshot(filePath: string): string | null {
        try {
            const absolutePath = PathResolver.resolve(filePath);
            const fileSnapshots = this.snapshots.get(absolutePath);

            if (!fileSnapshots || fileSnapshots.length === 0) {
                return null;
            }

            return fileSnapshots[fileSnapshots.length - 1].content;
        } catch (e) {
            console.error(`❌ [FileHistory] Failed to get latest snapshot for ${filePath}: ${e}`);
            return null;
        }
    }

    /**
     * 回滚文件到指定时间戳之前的版本
     *
     * @param filePath 文件绝对路径
     * @param beforeTimestamp 时间戳（毫秒），回滚到此时间之前的版本
     * @returns RollbackResult 回滚结果
     */
    async rollbackToTimestamp(filePath: string, beforeTimestamp: number): Promise<RollbackResult> {
        console.log(`🔄 [FileHistory] Rollback request: file=${filePath}, beforeTs=${beforeTimestamp}`);

        try {
            const absolutePath = PathResolver.resolve(filePath);

            // 获取历史内容
            const historicalContent = this.getContentBefore(filePath, beforeTimestamp);

            if (historicalContent === null) {
                console.warn(`⚠️ [FileHistory] Rollback failed - no history found before timestamp ${beforeTimestamp} for: ${filePath}`);
                return {
                    success: false,
                    error: `No history found before timestamp ${beforeTimestamp}`,
                };
            }

            // 写回文件
            try {
                // 确保目录存在
                const dir = path.dirname(absolutePath);
                if (!fs.existsSync(dir)) {
                    fs.mkdirSync(dir, { recursive: true });
                }

                fs.writeFileSync(absolutePath, historicalContent, 'utf-8');

                // 刷新 VS Code 的文件缓存
                const uri = vscode.Uri.file(absolutePath);
                const document = vscode.workspace.textDocuments.find(doc => doc.uri.fsPath === absolutePath);
                if (document) {
                    // 如果文档已打开，重新加载它
                    const edit = new vscode.WorkspaceEdit();
                    const fullRange = new vscode.Range(
                        new vscode.Position(0, 0),
                        document.lineAt(document.lineCount - 1).range.end
                    );
                    edit.replace(uri, fullRange, historicalContent);
                    await vscode.workspace.applyEdit(edit);
                }

                console.log(`✅ [FileHistory] Rollback successful: ${filePath} (restored ${historicalContent.length} chars)`);
                return { success: true };
            } catch (writeError) {
                console.error(`❌ [FileHistory] Failed to write rollback content for ${filePath}:`, writeError);
                return {
                    success: false,
                    error: `Failed to write file: ${writeError}`,
                };
            }
        } catch (e) {
            console.error(`❌ [FileHistory] Rollback failed for ${filePath}:`, e);
            return {
                success: false,
                error: `Rollback failed: ${e}`,
            };
        }
    }

    /**
     * 删除文件（用于新建文件的回滚）
     *
     * @param filePath 文件绝对路径
     * @returns RollbackResult 删除结果
     */
    async deleteFile(filePath: string): Promise<RollbackResult> {
        console.log(`🗑️ [FileHistory] Delete file request (rollback new file): ${filePath}`);

        try {
            const absolutePath = PathResolver.resolve(filePath);

            if (!fs.existsSync(absolutePath)) {
                console.warn(`⚠️ [FileHistory] Delete failed - file not found: ${filePath}`);
                return {
                    success: false,
                    error: `File not found: ${filePath}`,
                };
            }

            try {
                fs.unlinkSync(absolutePath);

                // 关闭 VS Code 中打开的文档
                const uri = vscode.Uri.file(absolutePath);
                const document = vscode.workspace.textDocuments.find(doc => doc.uri.fsPath === absolutePath);
                if (document) {
                    // 关闭文档对应的编辑器
                    const editors = vscode.window.visibleTextEditors.filter(
                        editor => editor.document.uri.fsPath === absolutePath
                    );
                    for (const editor of editors) {
                        await vscode.commands.executeCommand('workbench.action.closeActiveEditor');
                    }
                }

                // 清理快照
                this.snapshots.delete(absolutePath);

                console.log(`✅ [FileHistory] File deleted successfully: ${filePath}`);
                return { success: true };
            } catch (deleteError) {
                console.error(`❌ [FileHistory] Failed to delete file: ${filePath}`, deleteError);
                return {
                    success: false,
                    error: `Failed to delete file: ${deleteError}`,
                };
            }
        } catch (e) {
            console.error(`❌ [FileHistory] Delete file failed: ${filePath}`, e);
            return {
                success: false,
                error: `Delete failed: ${e}`,
            };
        }
    }

    /**
     * 清理指定文件的所有快照
     *
     * @param filePath 文件绝对路径
     */
    clearSnapshots(filePath: string): void {
        const absolutePath = PathResolver.resolve(filePath);
        this.snapshots.delete(absolutePath);
        console.log(`🧹 [FileHistory] Cleared snapshots for: ${filePath}`);
    }

    /**
     * 清理所有快照
     */
    clearAllSnapshots(): void {
        this.snapshots.clear();
        console.log(`🧹 [FileHistory] Cleared all snapshots`);
    }

    /**
     * 获取文件的快照数量
     *
     * @param filePath 文件绝对路径
     */
    getSnapshotCount(filePath: string): number {
        const absolutePath = PathResolver.resolve(filePath);
        return this.snapshots.get(absolutePath)?.length ?? 0;
    }

    /**
     * 释放资源
     */
    dispose(): void {
        this.clearAllSnapshots();
        FileHistoryService.instance = null;
    }
}

/**
 * 获取文件历史服务实例
 */
export function getFileHistoryService(): FileHistoryService {
    return FileHistoryService.getInstance();
}
