/**
 * IDE 工具 VS Code 实现
 * 
 * 翻译自: jetbrains-plugin/.../tools/IdeToolsImpl.kt
 * 
 * 提供 VS Code 环境下的 IDE 集成功能：
 * - 文件操作（打开、搜索、读取内容）
 * - Diff 显示
 * - 主题获取
 * - 语言环境
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { ActiveFileHelper } from './activeFileHelper';
import { DiffContentHelper, EditOperation } from './diffContentHelper';
import { ThemeManager, IdeTheme } from '../theme/themeManager';

// 日志工具
const log = {
  info: (msg: string) => console.log(`[IdeToolsImpl] ${msg}`),
  warn: (msg: string) => console.warn(`[IdeToolsImpl] ${msg}`),
  error: (msg: string) => console.error(`[IdeToolsImpl] ${msg}`),
};

/**
 * 文件信息
 */
export interface FileInfo {
  path: string;
}

/**
 * 活动文件信息
 */
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
  fileType?: string;
  // Diff 相关
  diffOldContent?: string;
  diffNewContent?: string;
  diffTitle?: string;
}

/**
 * Diff 请求
 */
export interface DiffRequest {
  filePath: string;
  oldContent: string;
  newContent: string;
  title?: string;
  rebuildFromFile?: boolean;
  edits?: EditOperation[];
}

/**
 * 字体数据
 */
export interface FontData {
  fontFamily: string;
  data: Buffer;
  mimeType: string;
}

export class IdeToolsImpl {
  private workspacePath: string;
  private diffContentHelper = new DiffContentHelper();
  private activeFileHelper: ActiveFileHelper;
  private themeManager: ThemeManager;
  private preferredLocaleKey = 'claudeCodePlus.locale';

  constructor(workspacePath?: string) {
    this.workspacePath = workspacePath || vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || '';
    this.activeFileHelper = new ActiveFileHelper(this.workspacePath);
    this.themeManager = new ThemeManager();
  }

  /**
   * 打开文件
   */
  async openFile(filePath: string, line = 0, column = 0): Promise<void> {
    if (!filePath) {
      throw new Error('File path cannot be empty');
    }

    try {
      const absolutePath = this.resolveAbsolutePath(filePath);
      const uri = vscode.Uri.file(absolutePath);
      const document = await vscode.workspace.openTextDocument(uri);

      const options: vscode.TextDocumentShowOptions = {};

      if (line > 0) {
        const lineNum = line - 1; // 转换为 0-based
        const colNum = Math.max(0, column - 1);
        options.selection = new vscode.Range(lineNum, colNum, lineNum, colNum);
      }

      await vscode.window.showTextDocument(document, options);
      log.info(`✅ Opened file: ${filePath} (line=${line}, column=${column})`);
    } catch (e) {
      log.error(`❌ Failed to open file: ${e instanceof Error ? e.message : 'Unknown error'}`);
      throw e;
    }
  }

  /**
   * 显示 Diff
   */
  async showDiff(request: DiffRequest): Promise<void> {
    if (!request.filePath) {
      throw new Error('File path cannot be empty');
    }

    try {
      const fileName = path.basename(request.filePath);

      let finalOldContent: string;
      let finalNewContent: string;
      let finalTitle: string;

      if (request.rebuildFromFile) {
        // 从文件重建 diff
        const absolutePath = this.resolveAbsolutePath(request.filePath);
        const currentContent = await fs.promises.readFile(absolutePath, 'utf-8');

        const edits = request.edits || [
          {
            oldString: request.oldContent,
            newString: request.newContent,
            replaceAll: false,
          },
        ];

        finalOldContent = this.diffContentHelper.rebuildBeforeContent(currentContent, edits);
        finalNewContent = currentContent;
        finalTitle = request.title || `File Changes: ${fileName} (${edits.length} edits)`;
      } else {
        finalOldContent = request.oldContent;
        finalNewContent = request.newContent;
        finalTitle = request.title || `File Diff: ${fileName}`;
      }

      // 创建临时文件用于 diff
      const timestamp = Date.now();
      const oldUri = vscode.Uri.parse(`untitled:${fileName}.before.${timestamp}`);
      const newUri = vscode.Uri.parse(`untitled:${fileName}.after.${timestamp}`);

      // 使用 vscode.diff 命令显示差异
      await vscode.commands.executeCommand('vscode.diff', oldUri, newUri, finalTitle);

      log.info(`✅ Showing diff for: ${request.filePath}`);
    } catch (e) {
      log.error(`❌ Failed to show diff: ${e instanceof Error ? e.message : 'Unknown error'}`);
      throw e;
    }
  }

  /**
   * 搜索文件
   */
  async searchFiles(query: string, maxResults = 50): Promise<FileInfo[]> {
    if (!query) {
      return [];
    }

    try {
      // 使用 VS Code 的 findFiles API
      const pattern = `**/*${query}*`;
      const files = await vscode.workspace.findFiles(pattern, '**/node_modules/**', maxResults);

      return files.map(file => ({
        path: file.fsPath,
      }));
    } catch (e) {
      log.warn(`Failed to search files: ${e instanceof Error ? e.message : 'Unknown error'}`);
      throw e;
    }
  }

  /**
   * 获取文件内容
   */
  async getFileContent(filePath: string, lineStart?: number, lineEnd?: number): Promise<string> {
    if (!filePath) {
      throw new Error('File path cannot be empty');
    }

    try {
      const absolutePath = this.resolveAbsolutePath(filePath);
      const content = await fs.promises.readFile(absolutePath, 'utf-8');

      if (lineStart !== undefined && lineEnd !== undefined) {
        const lines = content.split('\n');
        return lines.slice(Math.max(0, lineStart - 1), lineEnd).join('\n');
      }

      return content;
    } catch (e) {
      log.error(`Failed to get file content: ${e instanceof Error ? e.message : 'Unknown error'}`);
      throw e;
    }
  }

  /**
   * 获取最近打开的文件
   */
  getRecentFiles(maxResults = 10): FileInfo[] {
    try {
      const editors = vscode.window.visibleTextEditors;
      return editors.slice(0, maxResults).map(editor => ({
        path: editor.document.uri.fsPath,
      }));
    } catch (e) {
      log.warn(`Failed to get recent files: ${e instanceof Error ? e.message : 'Unknown error'}`);
      return [];
    }
  }

  /**
   * 获取主题
   */
  getTheme(): IdeTheme {
    return this.themeManager.getTheme();
  }

  /**
   * 获取项目路径
   */
  getProjectPath(): string {
    return this.workspacePath;
  }

  /**
   * 获取语言环境
   */
  getLocale(): string {
    // 检查用户偏好设置
    const config = vscode.workspace.getConfiguration('claudeCodePlus');
    const preferred = config.get<string>('locale');
    if (preferred) {
      return preferred;
    }

    // 使用 VS Code 语言
    return vscode.env.language;
  }

  /**
   * 设置语言环境
   */
  async setLocale(locale: string): Promise<void> {
    try {
      const config = vscode.workspace.getConfiguration('claudeCodePlus');
      await config.update('locale', locale, vscode.ConfigurationTarget.Global);
      log.info(`Locale preference set to: ${locale}`);
    } catch (e) {
      log.warn(`Failed to set locale preference: ${e instanceof Error ? e.message : 'Unknown error'}`);
      throw e;
    }
  }

  /**
   * 获取当前活动编辑器的文件信息
   */
  getActiveEditorFile(): ActiveFileInfo | null {
    return this.activeFileHelper.getActiveEditorFile();
  }

  /**
   * 检查是否在 IDE 环境中运行
   */
  hasIdeEnvironment(): boolean {
    return true; // VS Code 扩展总是在 IDE 环境中
  }

  /**
   * 在系统默认浏览器中打开 URL
   */
  async openUrl(url: string): Promise<void> {
    log.info(`[VSCode] Opening URL in browser: ${url}`);
    try {
      await vscode.env.openExternal(vscode.Uri.parse(url));
    } catch (e) {
      log.warn(`Failed to open URL: ${e instanceof Error ? e.message : 'Unknown error'}`);
      throw e;
    }
  }

  /**
   * 解析绝对路径
   */
  private resolveAbsolutePath(filePath: string): string {
    if (path.isAbsolute(filePath)) {
      return filePath;
    }
    return path.join(this.workspacePath, filePath);
  }
}
