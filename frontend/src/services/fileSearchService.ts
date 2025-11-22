/**
 * 文件搜索服务
 * 提供文件搜索和最近文件获取功能
 */

export interface IndexedFileInfo {
  name: string
  relativePath: string
  absolutePath: string
  fileType: string
  size: number
  lastModified: number
}

export class FileSearchService {
  /**
   * 搜索文件
   * @param query 搜索关键词
   * @param maxResults 最大结果数（默认 10）
   */
  async searchFiles(query: string, maxResults: number = 10): Promise<IndexedFileInfo[]> {
    try {
      const response = await fetch(
        `/api/files/search?query=${encodeURIComponent(query)}&maxResults=${maxResults}`
      )

      if (!response.ok) {
        console.warn('文件搜索失败:', response.statusText)
        return []
      }

      const result = await response.json()

      if (result.success && result.data) {
        return result.data
      }

      console.warn('文件搜索失败:', result.error)
      return []
    } catch (error) {
      console.error('文件搜索异常:', error)
      return []
    }
  }

  /**
   * 获取最近打开的文件
   * @param maxResults 最大结果数（默认 10）
   */
  async getRecentFiles(maxResults: number = 10): Promise<IndexedFileInfo[]> {
    try {
      const response = await fetch(
        `/api/files/recent?maxResults=${maxResults}`
      )

      if (!response.ok) {
        console.warn('获取最近文件失败:', response.statusText)
        return []
      }

      const result = await response.json()

      if (result.success && result.data) {
        return result.data
      }

      console.warn('获取最近文件失败:', result.error)
      return []
    } catch (error) {
      console.error('获取最近文件异常:', error)
      return []
    }
  }
}

// 导出单例
export const fileSearchService = new FileSearchService()

