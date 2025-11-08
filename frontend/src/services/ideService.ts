/**
 * IDE 服务
 * 封装所有与 IDE 交互的接口
 */

import { apiClient } from './apiClient'
import type { FrontendResponse, IdeTheme } from '@/types/bridge'

export class IdeService {
  /**
   * 获取 IDE 主题
   */
  async getTheme(): Promise<FrontendResponse<{ theme: IdeTheme }>> {
    return apiClient.request('ide.getTheme')
  }

  /**
   * 在 IDE 中打开文件
   */
  async openFile(filePath: string, line?: number, column?: number): Promise<FrontendResponse> {
    return apiClient.request('ide.openFile', { filePath, line, column })
  }

  /**
   * 显示文件差异对比
   */
  async showDiff(filePath: string, oldContent: string, newContent: string): Promise<FrontendResponse> {
    return apiClient.request('ide.showDiff', { filePath, oldContent, newContent })
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
