/**
 * VS Code IDE 集成 API 实现
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsApiImpl.kt
 *
 * 使用组合模式：
 * - vscodeApi.capabilities.isSupported()
 * - vscodeApi.file.openFile(...)
 * - vscodeApi.theme.get()
 * - vscodeApi.session.getState()
 * - vscodeApi.locale.get()
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { PathResolver } from '../util/pathResolver';

// ==================== 类型定义 ====================

export interface VsCodeCapabilities {
    supported: boolean;
    version: string;
}

export interface VsCodeOpenFileRequest {
    filePath: string;
    line?: number;
    column?: number;
    startOffset?: number;
    endOffset?: number;
}

export interface VsCodeShowDiffRequest {
    filePath: string;
    oldContent: string;
    newContent: string;
    title?: string;
}

export interface VsCodeEditOperation {
    oldString: string;
    newString: string;
    replaceAll: boolean;
}

export interface VsCodeShowMultiEditDiffRequest {
    filePath: string;
    edits: VsCodeEditOperation[];
    currentContent?: string;
}

export interface VsCodeShowEditPreviewRequest {
    filePath: string;
    edits: VsCodeEditOperation[];
    title?: string;
}

export interface VsCodeShowEditFullDiffRequest {
    filePath: string;
    oldString: string;
    newString: string;
    replaceAll: boolean;
    originalContent?: string;
    title?: string;
}

export interface VsCodeShowMarkdownRequest {
    content: string;
    title?: string;
}

export interface ActiveFileInfo {
    path: string;
    relativePath: string;
    name: string;
    line?: number;
    column?: number;
    hasSelection?: boolean;
    startLine?: number;
    startColumn?: number;
    endLine?: number;
    endColumn?: number;
    selectedContent?: string;
}

export interface VsCodeIdeTheme {
    background: string;
    foreground: string;
    borderColor: string;
    panelBackground: string;
    textFieldBackground: string;
    selectionBackground: string;
    selectionForeground: string;
    linkColor: string;
    errorColor: string;
    warningColor: string;
    successColor: string;
    separatorColor: string;
    hoverBackground: string;
    accentColor: string;
    infoBackground: string;
    codeBackground: string;
    secondaryForeground: string;
    fontFamily: string;
    fontSize: number;
    editorFontFamily: string;
    editorFontSize: number;
}

export interface VsCodeSessionInfo {
    id: string;
    name: string;
}

export interface VsCodeSessionState {
    sessions: VsCodeSessionInfo[];
    activeSessionId?: string;
}

export interface VsCodeSessionCommand {
    type: string;
    payload?: any;
}

// ==================== API 接口 ====================

export interface VsCodeCapabilitiesApi {
    isSupported(): boolean;
    get(): VsCodeCapabilities;
}

export interface VsCodeFileApi {
    openFile(request: VsCodeOpenFileRequest): Promise<void>;
    showDiff(request: VsCodeShowDiffRequest): Promise<void>;
    showMultiEditDiff(request: VsCodeShowMultiEditDiffRequest): Promise<void>;
    showEditPreviewDiff(request: VsCodeShowEditPreviewRequest): Promise<void>;
    showEditFullDiff(request: VsCodeShowEditFullDiffRequest): Promise<void>;
    showMarkdown(request: VsCodeShowMarkdownRequest): Promise<void>;
    getActiveFile(): ActiveFileInfo | null;
}

export interface VsCodeThemeApi {
    get(): VsCodeIdeTheme;
    addChangeListener(listener: (theme: VsCodeIdeTheme) => void): () => void;
}

export interface VsCodeSessionApi {
    receiveState(state: VsCodeSessionState): void;
    getState(): VsCodeSessionState | null;
    addStateListener(listener: (state: VsCodeSessionState) => void): () => void;
    sendCommand(command: VsCodeSessionCommand): void;
    addCommandListener(listener: (command: VsCodeSessionCommand) => void): () => void;
}

export interface VsCodeLocaleApi {
    get(): string;
    set(locale: string): void;
}

export interface VsCodeProjectApi {
    getPath(): string;
}

export interface VsCodeApi {
    capabilities: VsCodeCapabilitiesApi;
    file: VsCodeFileApi;
    theme: VsCodeThemeApi;
    session: VsCodeSessionApi;
    locale: VsCodeLocaleApi;
    project: VsCodeProjectApi;
}

// ==================== 实现 ====================

export class VsCodeApiImpl implements VsCodeApi {
    capabilities: VsCodeCapabilitiesApi;
    file: VsCodeFileApi;
    theme: VsCodeThemeApi;
    session: VsCodeSessionApi;
    locale: VsCodeLocaleApi;
    project: VsCodeProjectApi;

    constructor(private readonly context: vscode.ExtensionContext) {
        this.capabilities = new CapabilitiesApiImpl();
        this.file = new FileApiImpl();
        this.theme = new ThemeApiImpl(context);
        this.session = new SessionApiImpl();
        this.locale = new LocaleApiImpl(context);
        this.project = new ProjectApiImpl();
    }
}

// ========== 能力检测 API 实现 ==========

class CapabilitiesApiImpl implements VsCodeCapabilitiesApi {
    isSupported(): boolean {
        return true;
    }

    get(): VsCodeCapabilities {
        return { supported: true, version: '1.0' };
    }
}

// ========== 文件操作 API 实现 ==========

class FileApiImpl implements VsCodeFileApi {
    async openFile(request: VsCodeOpenFileRequest): Promise<void> {
        try {
            const absolutePath = PathResolver.resolve(request.filePath);
            const uri = vscode.Uri.file(absolutePath);

            const document = await vscode.workspace.openTextDocument(uri);
            const editor = await vscode.window.showTextDocument(document);

            // 确定跳转的目标行
            const targetLine = request.startOffset ?? request.line;
            if (targetLine && targetLine > 0) {
                const line = targetLine - 1; // 转为 0-based
                const column = (request.column ?? 1) - 1;
                const position = new vscode.Position(line, column);

                editor.selection = new vscode.Selection(position, position);
                editor.revealRange(
                    new vscode.Range(position, position),
                    vscode.TextEditorRevealType.InCenter
                );
            }

            // 如果有行范围参数，选中指定行范围
            if (request.startOffset && request.endOffset && request.endOffset >= request.startOffset) {
                const startLine = request.startOffset - 1;
                const endLine = request.endOffset - 1;
                const lineCount = document.lineCount;

                const safeStartLine = Math.max(0, Math.min(startLine, lineCount - 1));
                const safeEndLine = Math.max(0, Math.min(endLine, lineCount - 1));

                const startPosition = new vscode.Position(safeStartLine, 0);
                const endPosition = new vscode.Position(safeEndLine, document.lineAt(safeEndLine).text.length);

                editor.selection = new vscode.Selection(startPosition, endPosition);
                editor.revealRange(
                    new vscode.Range(startPosition, endPosition),
                    vscode.TextEditorRevealType.InCenter
                );

                console.log(`✅ [VsCodeApi.file] Opened with selection: ${request.filePath} (lines ${request.startOffset}-${request.endOffset})`);
            } else {
                console.log(`✅ [VsCodeApi.file] Opened: ${request.filePath}`);
            }
        } catch (e) {
            console.error(`❌ [VsCodeApi.file] Failed to open: ${e}`);
            throw e;
        }
    }

    async showDiff(request: VsCodeShowDiffRequest): Promise<void> {
        try {
            const fileName = path.basename(request.filePath);

            const leftUri = vscode.Uri.parse(`untitled:${fileName} (before)`);
            const rightUri = vscode.Uri.parse(`untitled:${fileName} (after)`);

            // 创建临时文档
            const leftDoc = await vscode.workspace.openTextDocument({ content: request.oldContent, language: this.getLanguageId(fileName) });
            const rightDoc = await vscode.workspace.openTextDocument({ content: request.newContent, language: this.getLanguageId(fileName) });

            await vscode.commands.executeCommand(
                'vscode.diff',
                leftDoc.uri,
                rightDoc.uri,
                request.title ?? `File Diff: ${fileName}`
            );

            console.log(`✅ [VsCodeApi.file] Showing diff: ${request.filePath}`);
        } catch (e) {
            console.error(`❌ [VsCodeApi.file] Failed to show diff: ${e}`);
            throw e;
        }
    }

    async showMultiEditDiff(request: VsCodeShowMultiEditDiffRequest): Promise<void> {
        try {
            const absolutePath = PathResolver.resolve(request.filePath);
            let currentContent = request.currentContent;

            if (!currentContent) {
                currentContent = fs.readFileSync(absolutePath, 'utf-8');
            }

            const beforeContent = this.rebuildBeforeContent(currentContent, request.edits);
            const fileName = path.basename(request.filePath);

            const leftDoc = await vscode.workspace.openTextDocument({ content: beforeContent, language: this.getLanguageId(fileName) });
            const rightDoc = await vscode.workspace.openTextDocument({ content: currentContent, language: this.getLanguageId(fileName) });

            await vscode.commands.executeCommand(
                'vscode.diff',
                leftDoc.uri,
                rightDoc.uri,
                `File Changes: ${fileName} (${request.edits.length} edits)`
            );

            console.log(`✅ [VsCodeApi.file] Showing multi-edit diff: ${request.filePath}`);
        } catch (e) {
            console.error(`❌ [VsCodeApi.file] Failed to show multi-edit diff: ${e}`);
            throw e;
        }
    }

    async showEditPreviewDiff(request: VsCodeShowEditPreviewRequest): Promise<void> {
        try {
            const absolutePath = PathResolver.resolve(request.filePath);
            let currentContent = '';

            try {
                const stat = fs.statSync(absolutePath);
                if (stat.isDirectory()) {
                    console.warn(`⚠️ [VsCodeApi.file] Cannot show preview diff for directory: ${request.filePath}`);
                    return;
                }
                currentContent = fs.readFileSync(absolutePath, 'utf-8');
            } catch {
                // 文件不存在
            }

            // 依次应用所有编辑操作得到预览后的内容
            let afterContent = currentContent;
            for (const edit of request.edits) {
                if (edit.replaceAll) {
                    afterContent = afterContent.split(edit.oldString).join(edit.newString);
                } else {
                    const index = afterContent.indexOf(edit.oldString);
                    if (index >= 0) {
                        afterContent = afterContent.substring(0, index) + edit.newString + afterContent.substring(index + edit.oldString.length);
                    } else {
                        console.warn(`⚠️ [VsCodeApi.file] oldString not found in file, skipping edit`);
                    }
                }
            }

            const fileName = path.basename(request.filePath);
            const leftDoc = await vscode.workspace.openTextDocument({ content: currentContent, language: this.getLanguageId(fileName) });
            const rightDoc = await vscode.workspace.openTextDocument({ content: afterContent, language: this.getLanguageId(fileName) });

            await vscode.commands.executeCommand(
                'vscode.diff',
                leftDoc.uri,
                rightDoc.uri,
                request.title ?? `Edit Preview: ${fileName}`
            );

            console.log(`✅ [VsCodeApi.file] Showing edit preview diff: ${request.filePath}`);
        } catch (e) {
            console.error(`❌ [VsCodeApi.file] Failed to show edit preview diff: ${e}`);
            throw e;
        }
    }

    async showEditFullDiff(request: VsCodeShowEditFullDiffRequest): Promise<void> {
        try {
            const absolutePath = PathResolver.resolve(request.filePath);
            const fileName = path.basename(request.filePath);

            let beforeContent: string;
            let afterContent: string;

            if (request.originalContent) {
                // 有缓存的原始内容：展示完整文件 Diff
                console.log(`✅ [VsCodeApi.file] Using cached original content for full file diff`);
                beforeContent = request.originalContent;

                // 计算修改后的内容
                if (request.replaceAll) {
                    afterContent = beforeContent.split(request.oldString).join(request.newString);
                } else {
                    const index = beforeContent.indexOf(request.oldString);
                    if (index >= 0) {
                        afterContent = beforeContent.substring(0, index) + request.newString + beforeContent.substring(index + request.oldString.length);
                    } else {
                        // oldString 不在原始内容中，使用当前文件内容
                        try {
                            afterContent = fs.readFileSync(absolutePath, 'utf-8');
                        } catch {
                            afterContent = '';
                        }
                    }
                }
            } else {
                // 没有缓存的原始内容：只展示编辑部分
                console.log(`⚠️ [VsCodeApi.file] No cached content, showing edit-only diff`);
                beforeContent = request.oldString;
                afterContent = request.newString;
            }

            const leftDoc = await vscode.workspace.openTextDocument({ content: beforeContent, language: this.getLanguageId(fileName) });
            const rightDoc = await vscode.workspace.openTextDocument({ content: afterContent, language: this.getLanguageId(fileName) });

            const diffTitle = request.originalContent
                ? (request.title ?? `Edit: ${fileName}`)
                : `${request.title ?? `Edit: ${fileName}`} (edit only)`;

            await vscode.commands.executeCommand(
                'vscode.diff',
                leftDoc.uri,
                rightDoc.uri,
                diffTitle
            );

            console.log(`✅ [VsCodeApi.file] Showing edit full diff: ${request.filePath}`);
        } catch (e) {
            console.error(`❌ [VsCodeApi.file] Failed to show edit full diff: ${e}`);
            throw e;
        }
    }

    async showMarkdown(request: VsCodeShowMarkdownRequest): Promise<void> {
        try {
            const fileName = request.title ? `${request.title}.md` : 'plan-preview.md';
            const doc = await vscode.workspace.openTextDocument({ content: request.content, language: 'markdown' });
            await vscode.window.showTextDocument(doc);

            // 打开 Markdown 预览
            await vscode.commands.executeCommand('markdown.showPreviewToSide');

            console.log(`✅ [VsCodeApi.file] Showing markdown: ${fileName}`);
        } catch (e) {
            console.error(`❌ [VsCodeApi.file] Failed to show markdown: ${e}`);
            throw e;
        }
    }

    getActiveFile(): ActiveFileInfo | null {
        try {
            const editor = vscode.window.activeTextEditor;
            if (!editor) return null;

            const document = editor.document;
            const absolutePath = document.uri.fsPath;
            const projectPath = PathResolver.getWorkspaceBasePath() || '';

            const relativePath = absolutePath.startsWith(projectPath)
                ? absolutePath.slice(projectPath.length).replace(/^[\/\\]/, '')
                : absolutePath;

            const fileName = path.basename(absolutePath);
            const position = editor.selection.active;
            const line = position.line + 1;
            const column = position.character + 1;

            const hasSelection = !editor.selection.isEmpty;
            let startLine: number | undefined;
            let startColumn: number | undefined;
            let endLine: number | undefined;
            let endColumn: number | undefined;
            let selectedContent: string | undefined;

            if (hasSelection) {
                startLine = editor.selection.start.line + 1;
                startColumn = editor.selection.start.character + 1;
                endLine = editor.selection.end.line + 1;
                endColumn = editor.selection.end.character + 1;
                selectedContent = document.getText(editor.selection);
            }

            return {
                path: absolutePath,
                relativePath,
                name: fileName,
                line,
                column,
                hasSelection,
                startLine,
                startColumn,
                endLine,
                endColumn,
                selectedContent,
            };
        } catch (e) {
            console.error(`❌ [VsCodeApi.file] Failed to get active file: ${e}`);
            return null;
        }
    }

    private rebuildBeforeContent(afterContent: string, edits: VsCodeEditOperation[]): string {
        let content = afterContent;

        // 逆序应用编辑操作来重建原始内容
        for (let i = edits.length - 1; i >= 0; i--) {
            const operation = edits[i];
            if (operation.replaceAll) {
                if (!content.includes(operation.newString)) {
                    throw new Error('Rebuild failed: newString not found (replace_all)');
                }
                content = content.split(operation.newString).join(operation.oldString);
            } else {
                const index = content.indexOf(operation.newString);
                if (index < 0) {
                    throw new Error('Rebuild failed: newString not found');
                }
                content = content.substring(0, index) + operation.oldString + content.substring(index + operation.newString.length);
            }
        }

        return content;
    }

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
            '.h': 'c',
            '.hpp': 'cpp',
            '.cs': 'csharp',
            '.rb': 'ruby',
            '.php': 'php',
            '.swift': 'swift',
            '.html': 'html',
            '.css': 'css',
            '.scss': 'scss',
            '.less': 'less',
            '.vue': 'vue',
            '.yaml': 'yaml',
            '.yml': 'yaml',
            '.xml': 'xml',
            '.sql': 'sql',
            '.sh': 'shellscript',
            '.bash': 'shellscript',
        };
        return langMap[ext] || 'plaintext';
    }
}

// ========== 主题 API 实现 ==========

class ThemeApiImpl implements VsCodeThemeApi {
    private changeListeners: ((theme: VsCodeIdeTheme) => void)[] = [];
    private disposable: vscode.Disposable;

    constructor(context: vscode.ExtensionContext) {
        // 监听主题变化
        this.disposable = vscode.window.onDidChangeActiveColorTheme(() => {
            const theme = this.get();
            this.changeListeners.forEach(listener => listener(theme));
        });
        context.subscriptions.push(this.disposable);
    }

    get(): VsCodeIdeTheme {
        const config = vscode.workspace.getConfiguration();
        const editorFontFamily = config.get<string>('editor.fontFamily') || 'Consolas';
        const editorFontSize = config.get<number>('editor.fontSize') || 14;

        // VS Code 主题颜色需要通过 CSS 变量获取，这里使用默认值
        // 实际颜色会在 webview 中通过 CSS 变量动态获取
        const isDark = vscode.window.activeColorTheme.kind === vscode.ColorThemeKind.Dark ||
                      vscode.window.activeColorTheme.kind === vscode.ColorThemeKind.HighContrastDark;

        return {
            background: isDark ? '#1e1e1e' : '#ffffff',
            foreground: isDark ? '#cccccc' : '#333333',
            borderColor: isDark ? '#454545' : '#e0e0e0',
            panelBackground: isDark ? '#252526' : '#f3f3f3',
            textFieldBackground: isDark ? '#3c3c3c' : '#ffffff',
            selectionBackground: isDark ? '#264f78' : '#add6ff',
            selectionForeground: isDark ? '#ffffff' : '#000000',
            linkColor: isDark ? '#3794ff' : '#006ab1',
            errorColor: isDark ? '#f48771' : '#e51400',
            warningColor: isDark ? '#cca700' : '#bf8803',
            successColor: isDark ? '#89d185' : '#388a34',
            separatorColor: isDark ? '#454545' : '#e0e0e0',
            hoverBackground: isDark ? '#2a2d2e' : '#f0f0f0',
            accentColor: isDark ? '#0e639c' : '#0066b8',
            infoBackground: isDark ? '#063b49' : '#d6ecf2',
            codeBackground: isDark ? '#2d2d2d' : '#f5f5f5',
            secondaryForeground: isDark ? '#a0a0a0' : '#717171',
            fontFamily: 'system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
            fontSize: 13,
            editorFontFamily,
            editorFontSize,
        };
    }

    addChangeListener(listener: (theme: VsCodeIdeTheme) => void): () => void {
        this.changeListeners.push(listener);
        return () => {
            const index = this.changeListeners.indexOf(listener);
            if (index > -1) {
                this.changeListeners.splice(index, 1);
            }
        };
    }
}

// ========== 会话管理 API 实现 ==========

class SessionApiImpl implements VsCodeSessionApi {
    private currentState: VsCodeSessionState | null = null;
    private stateListeners: ((state: VsCodeSessionState) => void)[] = [];
    private commandListeners: ((command: VsCodeSessionCommand) => void)[] = [];

    receiveState(state: VsCodeSessionState): void {
        this.currentState = state;
        console.log(`[VsCodeApi.session] Received state: ${state.sessions.length} sessions, active=${state.activeSessionId}`);
        this.stateListeners.forEach(listener => listener(state));
    }

    getState(): VsCodeSessionState | null {
        return this.currentState;
    }

    addStateListener(listener: (state: VsCodeSessionState) => void): () => void {
        this.stateListeners.push(listener);
        if (this.currentState) {
            listener(this.currentState);
        }
        return () => {
            const index = this.stateListeners.indexOf(listener);
            if (index > -1) {
                this.stateListeners.splice(index, 1);
            }
        };
    }

    sendCommand(command: VsCodeSessionCommand): void {
        console.log(`[VsCodeApi.session] Sending command: ${command.type}`);
        this.commandListeners.forEach(listener => listener(command));
    }

    addCommandListener(listener: (command: VsCodeSessionCommand) => void): () => void {
        this.commandListeners.push(listener);
        return () => {
            const index = this.commandListeners.indexOf(listener);
            if (index > -1) {
                this.commandListeners.splice(index, 1);
            }
        };
    }
}

// ========== 语言设置 API 实现 ==========

class LocaleApiImpl implements VsCodeLocaleApi {
    private readonly PREFERRED_LOCALE_KEY = 'asakii.locale';

    constructor(private readonly context: vscode.ExtensionContext) {}

    get(): string {
        const preferred = this.context.globalState.get<string>(this.PREFERRED_LOCALE_KEY);
        if (preferred) {
            return preferred;
        }

        // 使用 VS Code 的语言设置
        const vscodeLang = vscode.env.language;
        return vscodeLang || 'en-US';
    }

    set(locale: string): void {
        try {
            this.context.globalState.update(this.PREFERRED_LOCALE_KEY, locale);
            console.log(`[VsCodeApi.locale] Set to: ${locale}`);
        } catch (e) {
            console.warn(`[VsCodeApi.locale] Failed to set: ${e}`);
            throw e;
        }
    }
}

// ========== 项目信息 API 实现 ==========

class ProjectApiImpl implements VsCodeProjectApi {
    getPath(): string {
        return PathResolver.getWorkspaceBasePath() || '';
    }
}
