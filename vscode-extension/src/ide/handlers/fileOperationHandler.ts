/**
 * 文件操作处理器
 * 处理: openFile, showDiff, showMultiEditDiff, showEditPreviewDiff, showEditFullDiff, showMarkdown
 * 
 * 翻译自: jetbrains-plugin/.../handlers/FileOperationHandler.kt
 */

import * as vscode from 'vscode';
import {
  JetBrainsOpenFileRequest,
  JetBrainsShowDiffRequest,
  JetBrainsShowMultiEditDiffRequest,
  JetBrainsShowEditPreviewRequest,
  JetBrainsShowEditFullDiffRequest,
  JetBrainsShowMarkdownRequest,
  JetBrainsOperationResponse,
  JetBrainsEditOperation,
} from '@proto';
import { DiffContentHelper } from '../tools/diffContentHelper';

// 日志工具
const log = {
  info: (msg: string) => console.log(`[FileOperationHandler] ${msg}`),
  warn: (msg: string) => console.warn(`[FileOperationHandler] ${msg}`),
  error: (msg: string) => console.error(`[FileOperationHandler] ${msg}`),
};

export class FileOperationHandler {
  private diffContentHelper = new DiffContentHelper();

  /**
   * 处理打开文件请求
   */
  async handleOpenFile(request: JetBrainsOpenFileRequest): Promise<JetBrainsOperationResponse> {
    try {
      log.info(`📂 openFile: ${request.filePath}`);

      const uri = vscode.Uri.file(request.filePath);
      const document = await vscode.workspace.openTextDocument(uri);

      const options: vscode.TextDocumentShowOptions = {};

      // 处理行列定位
      if (request.line !== undefined && request.line > 0) {
        const line = request.line - 1; // 转换为 0-based
        const column = (request.column ?? 1) - 1;
        options.selection = new vscode.Range(line, column, line, column);
      }

      // 处理偏移量定位
      if (request.startOffset !== undefined) {
        const startPos = document.positionAt(request.startOffset);
        const endPos = request.endOffset !== undefined
          ? document.positionAt(request.endOffset)
          : startPos;
        options.selection = new vscode.Range(startPos, endPos);
      }

      await vscode.window.showTextDocument(document, options);

      return JetBrainsOperationResponse.create({
        success: true,
      });
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      log.error(`❌ openFile failed: ${error}`);
      return JetBrainsOperationResponse.create({
        success: false,
        error,
      });
    }
  }

  /**
   * 处理显示 Diff 请求
   */
  async handleShowDiff(request: JetBrainsShowDiffRequest): Promise<JetBrainsOperationResponse> {
    try {
      log.info(`📝 showDiff: ${request.filePath}`);

      const fileName = request.filePath.split(/[/\\]/).pop() || 'file';
      const title = request.title || `Diff: ${fileName}`;

      // 创建虚拟文档 URI
      const oldUri = vscode.Uri.parse(`diff-old:${fileName}?${encodeURIComponent(request.oldContent)}`);
      const newUri = vscode.Uri.parse(`diff-new:${fileName}?${encodeURIComponent(request.newContent)}`);

      // 使用 VS Code 内置 diff 命令
      await vscode.commands.executeCommand('vscode.diff', oldUri, newUri, title);

      return JetBrainsOperationResponse.create({
        success: true,
      });
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      log.error(`❌ showDiff failed: ${error}`);
      return JetBrainsOperationResponse.create({
        success: false,
        error,
      });
    }
  }

  /**
   * 处理多处编辑 Diff 请求
   */
  async handleShowMultiEditDiff(request: JetBrainsShowMultiEditDiffRequest): Promise<JetBrainsOperationResponse> {
    try {
      log.info(`📝 showMultiEditDiff: ${request.filePath} (${request.edits.length} edits)`);

      const fileName = request.filePath.split(/[/\\]/).pop() || 'file';
      
      // 获取当前文件内容
      let currentContent = request.currentContent;
      if (!currentContent) {
        const uri = vscode.Uri.file(request.filePath);
        const document = await vscode.workspace.openTextDocument(uri);
        currentContent = document.getText();
      }

      // 逆向重建修改前的内容
      const edits = request.edits.map(edit => ({
        oldString: edit.oldString,
        newString: edit.newString,
        replaceAll: edit.replaceAll,
      }));
      const oldContent = this.diffContentHelper.rebuildBeforeContent(currentContent, edits);

      const title = `Multi-Edit: ${fileName} (${request.edits.length} edits)`;

      // 显示 diff
      await this.showDiffInEditor(oldContent, currentContent, title, fileName);

      return JetBrainsOperationResponse.create({
        success: true,
      });
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      log.error(`❌ showMultiEditDiff failed: ${error}`);
      return JetBrainsOperationResponse.create({
        success: false,
        error,
      });
    }
  }

  /**
   * 处理编辑预览 Diff 请求
   */
  async handleShowEditPreviewDiff(request: JetBrainsShowEditPreviewRequest): Promise<JetBrainsOperationResponse> {
    try {
      log.info(`👀 showEditPreviewDiff: ${request.filePath} (${request.edits.length} edits)`);

      const fileName = request.filePath.split(/[/\\]/).pop() || 'file';
      
      // 读取当前文件内容
      const uri = vscode.Uri.file(request.filePath);
      const document = await vscode.workspace.openTextDocument(uri);
      const currentContent = document.getText();

      // 应用编辑得到新内容
      let newContent = currentContent;
      for (const edit of request.edits) {
        if (edit.replaceAll) {
          newContent = newContent.split(edit.oldString).join(edit.newString);
        } else {
          newContent = newContent.replace(edit.oldString, edit.newString);
        }
      }

      const title = request.title || `Edit Preview: ${fileName}`;

      await this.showDiffInEditor(currentContent, newContent, title, fileName);

      return JetBrainsOperationResponse.create({
        success: true,
      });
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      log.error(`❌ showEditPreviewDiff failed: ${error}`);
      return JetBrainsOperationResponse.create({
        success: false,
        error,
      });
    }
  }

  /**
   * 处理完整编辑 Diff 请求
   */
  async handleShowEditFullDiff(request: JetBrainsShowEditFullDiffRequest): Promise<JetBrainsOperationResponse> {
    try {
      log.info(`📝 showEditFullDiff: ${request.filePath}`);

      const fileName = request.filePath.split(/[/\\]/).pop() || 'file';
      
      // 获取原始内容（如果提供）或从文件读取
      let oldContent: string;
      if (request.originalContent) {
        oldContent = request.originalContent;
      } else {
        const uri = vscode.Uri.file(request.filePath);
        const document = await vscode.workspace.openTextDocument(uri);
        oldContent = document.getText();
      }

      // 应用编辑
      let newContent: string;
      if (request.replaceAll) {
        newContent = oldContent.split(request.oldString).join(request.newString);
      } else {
        newContent = oldContent.replace(request.oldString, request.newString);
      }

      const title = request.title || `Edit: ${fileName}`;

      await this.showDiffInEditor(oldContent, newContent, title, fileName);

      return JetBrainsOperationResponse.create({
        success: true,
      });
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      log.error(`❌ showEditFullDiff failed: ${error}`);
      return JetBrainsOperationResponse.create({
        success: false,
        error,
      });
    }
  }

  /**
   * 处理显示 Markdown 请求
   */
  async handleShowMarkdown(request: JetBrainsShowMarkdownRequest): Promise<JetBrainsOperationResponse> {
    try {
      const title = request.title || 'Plan Preview';
      log.info(`📄 showMarkdown: ${title}`);

      // 创建临时 Markdown 文件并在预览中打开
      const uri = vscode.Uri.parse(`untitled:${title}.md`);
      const document = await vscode.workspace.openTextDocument(uri);
      
      const edit = new vscode.WorkspaceEdit();
      edit.insert(uri, new vscode.Position(0, 0), request.content);
      await vscode.workspace.applyEdit(edit);

      // 打开 Markdown 预览
      await vscode.commands.executeCommand('markdown.showPreview', uri);

      return JetBrainsOperationResponse.create({
        success: true,
      });
    } catch (e) {
      const error = e instanceof Error ? e.message : 'Unknown error';
      log.error(`❌ showMarkdown failed: ${error}`);
      return JetBrainsOperationResponse.create({
        success: false,
        error,
      });
    }
  }

  /**
   * 在编辑器中显示 Diff（内部辅助方法）
   */
  private async showDiffInEditor(
    oldContent: string,
    newContent: string,
    title: string,
    fileName: string
  ): Promise<void> {
    // 使用 untitled 方案创建临时文档
    const timestamp = Date.now();
    const oldUri = vscode.Uri.parse(`untitled:${fileName}.before.${timestamp}`);
    const newUri = vscode.Uri.parse(`untitled:${fileName}.after.${timestamp}`);

    // 创建旧内容文档
    const oldDoc = await vscode.workspace.openTextDocument(oldUri);
    const oldEdit = new vscode.WorkspaceEdit();
    oldEdit.insert(oldUri, new vscode.Position(0, 0), oldContent);
    await vscode.workspace.applyEdit(oldEdit);

    // 创建新内容文档
    const newDoc = await vscode.workspace.openTextDocument(newUri);
    const newEdit = new vscode.WorkspaceEdit();
    newEdit.insert(newUri, new vscode.Position(0, 0), newContent);
    await vscode.workspace.applyEdit(newEdit);

    // 显示 diff
    await vscode.commands.executeCommand('vscode.diff', oldUri, newUri, title);
  }
}
