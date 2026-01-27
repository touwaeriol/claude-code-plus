/**
 * VS Code 平台统一服务
 *
 * 封装所有 VS Code 平台操作，提供统一的接口
 * 便于维护、测试和后续扩展
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/IdeaPlatformService.kt
 */

import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { PathResolver } from '../util/pathResolver';

/**
 * 选择范围
 */
export interface SelectionRange {
    startOffset: number;
    endOffset: number;
}

/**
 * 通知类型
 */
export enum NotificationType {
    INFORMATION = 'information',
    WARNING = 'warning',
    ERROR = 'error',
}

/**
 * VS Code 平台服务
 */
export class VsCodePlatformService {
    private static instance: VsCodePlatformService | null = null;
    private readonly NOTIFICATION_PREFIX = 'Claude Code Plus';

    private constructor() {}

    /**
     * 获取单例实例
     */
    static getInstance(): VsCodePlatformService {
        if (!this.instance) {
            this.instance = new VsCodePlatformService();
        }
        return this.instance;
    }

    // ====== 文件操作 ======

    /**
     * 在编辑器中打开文件
     *
     * @param filePath 文件路径（支持绝对路径和相对路径）
     * @param options 打开选项
     * @returns true 表示成功打开
     */
    async openFile(
        filePath: string,
        options: {
            line?: number;
            column?: number;
            selectContent?: boolean;
            content?: string;
            selectionRange?: SelectionRange;
            focusEditor?: boolean;
        } = {}
    ): Promise<boolean> {
        const {
            line,
            column,
            selectContent = true,
            content,
            selectionRange,
            focusEditor = false,
        } = options;

        try {
            const uri = await this.findFile(filePath);
            if (!uri) {
                console.warn(`找不到文件: ${filePath}`);
                this.showWarning(`找不到文件: ${filePath}`);
                return false;
            }

            const document = await vscode.workspace.openTextDocument(uri);
            const editor = await vscode.window.showTextDocument(document, {
                preview: false,
                preserveFocus: !focusEditor,
            });

            if (selectionRange) {
                await this.selectRangeInEditor(editor, selectionRange);
            } else if (selectContent && content) {
                await this.selectTextInEditor(editor, content);
            } else if (line) {
                await this.navigateToLine(editor, line, column);
            }

            console.log(`成功打开文件: ${path.basename(filePath)}`);
            return true;
        } catch (e) {
            console.error(`打开文件失败: ${filePath}`, e);
            this.showError(`打开文件失败: ${e}`);
            return false;
        }
    }

    /**
     * 在编辑器中选择范围
     */
    private async selectRangeInEditor(editor: vscode.TextEditor, range: SelectionRange): Promise<void> {
        try {
            const document = editor.document;
            const length = document.getText().length;
            const start = Math.max(0, Math.min(range.startOffset, length));
            const end = Math.max(start, Math.min(range.endOffset, length));

            const startPosition = document.positionAt(start);
            const endPosition = document.positionAt(end);

            editor.selection = new vscode.Selection(startPosition, endPosition);
            editor.revealRange(
                new vscode.Range(startPosition, endPosition),
                vscode.TextEditorRevealType.InCenter
            );

            console.log(`成功根据范围选择文本: ${start}-${end}`);
        } catch (e) {
            console.warn('根据范围选择文本失败', e);
        }
    }

    /**
     * 显示文件差异对比
     *
     * @param filePath 文件路径
     * @param oldContent 原始内容
     * @param newContent 新内容
     * @param title 对比标题（可选）
     * @returns true 表示成功显示
     */
    async showDiff(
        filePath: string,
        oldContent: string,
        newContent: string,
        title?: string
    ): Promise<boolean> {
        try {
            const fileName = path.basename(filePath);
            const diffTitle = title || `文件变更: ${fileName}`;
            const languageId = this.getLanguageId(fileName);

            const oldDoc = await vscode.workspace.openTextDocument({
                content: oldContent,
                language: languageId,
            });
            const newDoc = await vscode.workspace.openTextDocument({
                content: newContent,
                language: languageId,
            });

            await vscode.commands.executeCommand(
                'vscode.diff',
                oldDoc.uri,
                newDoc.uri,
                diffTitle
            );

            console.log(`成功显示文件差异: ${fileName}`);
            return true;
        } catch (e) {
            console.error(`显示差异失败: ${filePath}`, e);
            this.showError(`显示文件差异失败: ${e}`);
            return false;
        }
    }

    /**
     * 查找文件
     *
     * @param filePath 文件路径（支持绝对路径和相对路径）
     * @returns 文件 URI 或 null
     */
    async findFile(filePath: string): Promise<vscode.Uri | null> {
        try {
            // 尝试作为绝对路径
            if (PathResolver.isAbsolutePath(filePath) && fs.existsSync(filePath)) {
                return vscode.Uri.file(filePath);
            }

            // 尝试作为项目相对路径
            const absolutePath = PathResolver.resolve(filePath);
            if (fs.existsSync(absolutePath)) {
                return vscode.Uri.file(absolutePath);
            }

            return null;
        } catch (e) {
            console.warn(`查找文件失败: ${filePath}`, e);
            return null;
        }
    }

    /**
     * 刷新文件系统，确保 VS Code 能立刻看到文件变化
     *
     * @param filePath 文件路径
     * @returns 是否刷新成功
     */
    async refreshFile(filePath: string): Promise<boolean> {
        try {
            const absolutePath = PathResolver.resolve(filePath);
            if (!fs.existsSync(absolutePath)) {
                return false;
            }

            const uri = vscode.Uri.file(absolutePath);

            // 如果文件已经在编辑器中打开，需要重新加载
            const openDocument = vscode.workspace.textDocuments.find(
                doc => doc.uri.fsPath === absolutePath
            );

            if (openDocument) {
                // 重新打开文档以刷新内容
                await vscode.commands.executeCommand('workbench.action.files.revert');
            }

            // 触发文件系统监视器刷新
            const watcher = vscode.workspace.createFileSystemWatcher(
                new vscode.RelativePattern(path.dirname(absolutePath), path.basename(absolutePath))
            );
            watcher.dispose();

            return true;
        } catch (e) {
            console.warn(`刷新文件失败: ${filePath}`, e);
            return false;
        }
    }

    /**
     * 保存指定文件的文档到磁盘
     *
     * @param filePath 文件路径
     * @returns true 表示保存成功或文件无需保存
     */
    async saveDocument(filePath: string): Promise<boolean> {
        try {
            const absolutePath = PathResolver.resolve(filePath);
            const document = vscode.workspace.textDocuments.find(
                doc => doc.uri.fsPath === absolutePath
            );

            if (!document) {
                return true; // 文档未打开，无需保存
            }

            if (document.isDirty) {
                await document.save();
                console.log(`已保存文档: ${filePath}`);
            }

            return true;
        } catch (e) {
            console.warn(`保存文档失败: ${filePath}`, e);
            return false;
        }
    }

    /**
     * 保存所有打开的文档
     */
    async saveAllDocuments(): Promise<boolean> {
        try {
            await vscode.workspace.saveAll();
            console.log('已保存所有文档');
            return true;
        } catch (e) {
            console.warn('保存所有文档失败', e);
            return false;
        }
    }

    // ====== 通知操作 ======

    /**
     * 显示信息通知
     */
    showInfo(message: string): void {
        this.showNotification(message, NotificationType.INFORMATION);
    }

    /**
     * 显示警告通知
     */
    showWarning(message: string): void {
        this.showNotification(message, NotificationType.WARNING);
    }

    /**
     * 显示错误通知
     */
    showError(message: string): void {
        this.showNotification(message, NotificationType.ERROR);
    }

    /**
     * 显示通知
     */
    showNotification(message: string, type: NotificationType): void {
        try {
            const fullMessage = `${this.NOTIFICATION_PREFIX}: ${message}`;

            switch (type) {
                case NotificationType.INFORMATION:
                    vscode.window.showInformationMessage(fullMessage);
                    break;
                case NotificationType.WARNING:
                    vscode.window.showWarningMessage(fullMessage);
                    break;
                case NotificationType.ERROR:
                    vscode.window.showErrorMessage(fullMessage);
                    break;
            }
        } catch (e) {
            console.warn(`显示通知失败: ${message}`, e);
        }
    }

    /**
     * 显示带选项的通知
     */
    async showNotificationWithOptions(
        message: string,
        type: NotificationType,
        ...items: string[]
    ): Promise<string | undefined> {
        try {
            const fullMessage = `${this.NOTIFICATION_PREFIX}: ${message}`;

            switch (type) {
                case NotificationType.INFORMATION:
                    return await vscode.window.showInformationMessage(fullMessage, ...items);
                case NotificationType.WARNING:
                    return await vscode.window.showWarningMessage(fullMessage, ...items);
                case NotificationType.ERROR:
                    return await vscode.window.showErrorMessage(fullMessage, ...items);
            }
        } catch (e) {
            console.warn(`显示通知失败: ${message}`, e);
            return undefined;
        }
    }

    // ====== 私有辅助方法 ======

    /**
     * 在编辑器中定位到指定行
     */
    private async navigateToLine(editor: vscode.TextEditor, line: number, column?: number): Promise<void> {
        try {
            const document = editor.document;
            if (document.lineCount === 0) {
                return;
            }

            const lineIndex = Math.max(0, Math.min(line - 1, document.lineCount - 1));
            let characterIndex = 0;

            if (column && column > 0) {
                const lineText = document.lineAt(lineIndex).text;
                characterIndex = Math.min(column - 1, lineText.length);
            }

            const position = new vscode.Position(lineIndex, characterIndex);
            editor.selection = new vscode.Selection(position, position);
            editor.revealRange(
                new vscode.Range(position, position),
                vscode.TextEditorRevealType.InCenter
            );

            console.log(`成功定位到第 ${line} 行`);
        } catch (e) {
            console.warn('定位失败', e);
        }
    }

    /**
     * 在编辑器中选择文本
     */
    private async selectTextInEditor(editor: vscode.TextEditor, content: string): Promise<void> {
        try {
            const document = editor.document;
            const documentText = document.getText();

            // 尝试在文档中查找内容
            const startIndex = documentText.indexOf(content);
            if (startIndex >= 0) {
                const endIndex = startIndex + content.length;
                const startPosition = document.positionAt(startIndex);
                const endPosition = document.positionAt(endIndex);

                editor.selection = new vscode.Selection(startPosition, endPosition);
                editor.revealRange(
                    new vscode.Range(startPosition, endPosition),
                    vscode.TextEditorRevealType.InCenter
                );

                console.log('成功选择文本内容');
            } else {
                console.warn('在文档中找不到指定内容');
            }
        } catch (e) {
            console.warn('选择文本失败', e);
        }
    }

    /**
     * 获取语言 ID
     */
    private getLanguageId(fileName: string): string {
        const ext = path.extname(fileName).toLowerCase();
        const langMap: Record<string, string> = {
            '.ts': 'typescript',
            '.tsx': 'typescriptreact',
            '.js': 'javascript',
            '.jsx': 'javascriptreact',
            '.json': 'json',
            '.md': 'markdown',
            '.py': 'python',
            '.java': 'java',
            '.kt': 'kotlin',
            '.go': 'go',
            '.rs': 'rust',
            '.c': 'c',
            '.cpp': 'cpp',
            '.cs': 'csharp',
            '.rb': 'ruby',
            '.php': 'php',
            '.swift': 'swift',
            '.html': 'html',
            '.css': 'css',
            '.scss': 'scss',
            '.vue': 'vue',
            '.yaml': 'yaml',
            '.yml': 'yaml',
            '.xml': 'xml',
            '.sql': 'sql',
            '.sh': 'shellscript',
        };
        return langMap[ext] || 'plaintext';
    }

    /**
     * 释放资源
     */
    dispose(): void {
        VsCodePlatformService.instance = null;
    }
}

/**
 * 获取平台服务实例
 */
export function getPlatformService(): VsCodePlatformService {
    return VsCodePlatformService.getInstance();
}
