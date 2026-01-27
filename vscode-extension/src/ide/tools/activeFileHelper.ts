/**
 * 活动文件处理辅助类
 * 负责获取当前编辑器中的文件信息
 * 
 * 翻译自: jetbrains-plugin/.../tools/ActiveFileHelper.kt
 */

import * as vscode from 'vscode';
import * as path from 'path';

// 日志工具
const log = {
  info: (msg: string) => console.log(`[ActiveFileHelper] ${msg}`),
  warn: (msg: string) => console.warn(`[ActiveFileHelper] ${msg}`),
};

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

export class ActiveFileHelper {
  constructor(private workspacePath: string) {}

  /**
   * 获取当前活动编辑器的文件信息
   */
  getActiveEditorFile(): ActiveFileInfo | null {
    try {
      const editor = vscode.window.activeTextEditor;
      if (!editor) {
        return null;
      }

      const document = editor.document;
      const absolutePath = document.uri.fsPath;
      const relativePath = this.calculateRelativePath(absolutePath);
      const fileName = path.basename(absolutePath);

      // 检查文件类型
      const fileType = this.determineFileType(document);

      // 图片和二进制文件：只返回路径，不获取内容
      if (fileType === 'image' || fileType === 'binary') {
        return {
          path: absolutePath,
          relativePath,
          name: fileName,
          fileType,
        };
      }

      // 检查是否是 Diff 编辑器
      if (this.isDiffEditor(editor)) {
        return this.handleDiffEditor(editor, absolutePath, relativePath, fileName);
      }

      // 处理普通文本编辑器
      return this.handleTextEditor(editor, absolutePath, relativePath, fileName, fileType);
    } catch (e) {
      log.warn(`Failed to get active editor file: ${e instanceof Error ? e.message : 'Unknown error'}`);
      return null;
    }
  }

  /**
   * 检查是否是 Diff 编辑器
   */
  private isDiffEditor(editor: vscode.TextEditor): boolean {
    const scheme = editor.document.uri.scheme;
    return scheme === 'diff' || scheme === 'git' || scheme === 'diff-old' || scheme === 'diff-new';
  }

  /**
   * 处理 Diff 编辑器
   */
  private handleDiffEditor(
    editor: vscode.TextEditor,
    absolutePath: string,
    relativePath: string,
    fileName: string
  ): ActiveFileInfo {
    // VS Code 的 diff 编辑器获取内容比较有限
    // 这里返回基本信息
    return {
      path: absolutePath,
      relativePath,
      name: fileName,
      fileType: 'diff',
      diffTitle: editor.document.uri.path,
    };
  }

  /**
   * 处理文本编辑器，获取光标位置和选区信息
   */
  private handleTextEditor(
    editor: vscode.TextEditor,
    absolutePath: string,
    relativePath: string,
    fileName: string,
    fileType: string
  ): ActiveFileInfo {
    const selection = editor.selection;
    const position = selection.active;

    // 光标位置（1-based）
    const line = position.line + 1;
    const column = position.character + 1;

    // 选区信息
    const hasSelection = !selection.isEmpty;
    let startLine: number | undefined;
    let startColumn: number | undefined;
    let endLine: number | undefined;
    let endColumn: number | undefined;
    let selectedContent: string | undefined;

    if (hasSelection) {
      startLine = selection.start.line + 1;
      startColumn = selection.start.character + 1;
      endLine = selection.end.line + 1;
      endColumn = selection.end.character + 1;
      selectedContent = editor.document.getText(selection);
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
      fileType,
    };
  }

  /**
   * 计算相对路径
   */
  calculateRelativePath(absolutePath: string): string {
    if (this.workspacePath && absolutePath.startsWith(this.workspacePath)) {
      let relative = absolutePath.slice(this.workspacePath.length);
      // 移除开头的路径分隔符
      if (relative.startsWith('/') || relative.startsWith('\\')) {
        relative = relative.slice(1);
      }
      return relative;
    }
    return absolutePath;
  }

  /**
   * 确定文件类型
   */
  private determineFileType(document: vscode.TextDocument): string {
    const extension = path.extname(document.fileName).toLowerCase().slice(1);

    // 常见图片扩展名
    const imageExtensions = new Set([
      'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'ico', 'tiff', 'tif',
    ]);

    // 常见二进制文件扩展名
    const binaryExtensions = new Set([
      'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx',
      'zip', 'tar', 'gz', 'rar', '7z',
      'exe', 'dll', 'so', 'dylib',
      'class', 'jar', 'war',
      'mp3', 'mp4', 'avi', 'mov', 'wav', 'flac',
      'ttf', 'otf', 'woff', 'woff2',
    ]);

    if (imageExtensions.has(extension)) {
      return 'image';
    }

    if (binaryExtensions.has(extension)) {
      return 'binary';
    }

    // VS Code 有语言 ID，可以用来判断
    if (document.languageId === 'binary' || document.languageId === 'hex') {
      return 'binary';
    }

    return 'text';
  }
}
