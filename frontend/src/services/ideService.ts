/**
 * IDE 服务
 * 封装所有与 IDE 交互的接口
 */

import { apiClient } from './apiClient'
import type { FrontendResponse, IdeTheme } from '@/types/bridge'

/**
 * 打开文件选项
 */
export interface OpenFileOptions {
  /** 行号（1-based） */
  line?: number
  /** 列号（1-based） */
  column?: number
  /** 是否选择内容 */
  selectContent?: boolean
  /** 要选择的内容文本 */
  content?: string
  /** 选择范围起始位置（字符偏移量） */
  selectionStart?: number
  /** 选择范围结束位置（字符偏移量） */
  selectionEnd?: number
}

/**
 * 显示 Diff 选项
 */
export interface ShowDiffOptions {
  /** 文件路径 */
  filePath: string
  /** 旧内容 */
  oldContent: string
  /** 新内容 */
  newContent: string
  /** Diff 标题 */
  title?: string
  /** 是否从文件重建完整 Diff */
  rebuildFromFile?: boolean
  /** 编辑操作列表（用于重建） */
  edits?: Array<{
    oldString: string
    newString: string
    replaceAll: boolean
  }>
}

export class IdeService {
  /**
   * 获取 IDE 主题
   */
  async getTheme(): Promise<FrontendResponse<{ theme: IdeTheme }>> {
    return apiClient.request('ide.getTheme')
  }

  /**
   * 在 IDE 中打开文件
   *
   * @param filePath 文件路径
   * @param options 打开选项
   */
  async openFile(filePath: string, options?: OpenFileOptions): Promise<FrontendResponse> {
    return apiClient.request('ide.openFile', {
      filePath,
      ...options
    })
  }

  /**
   * 显示文件差异对比
   *
   * @param options Diff 选项
   */
  async showDiff(options: ShowDiffOptions): Promise<FrontendResponse> {
    return apiClient.request('ide.showDiff', options)
  }

  /**
   * 显示文件差异对比（简化版本，向后兼容）
   */
  async showDiffSimple(filePath: string, oldContent: string, newContent: string): Promise<FrontendResponse> {
    return this.showDiff({ filePath, oldContent, newContent })
  }

  /**
   * 搜索文件
   */
  async searchFiles(query: string, maxResults?: number): Promise<FrontendResponse> {
    return apiClient.request('ide.searchFiles', { query, maxResults: maxResults || 20 })
  }

  /**
   * 获取文件内容
   */
  async getFileContent(filePath: string, lineStart?: number, lineEnd?: number): Promise<FrontendResponse> {
    return apiClient.request('ide.getFileContent', { filePath, lineStart, lineEnd })
  }
}

// 导出单例
export const ideService = new IdeService()
