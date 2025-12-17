/**
 * 文件搜索服务
 * 提供文件搜索功能（空查询时返回项目根目录文件）
 */

import { resolveServerHttpUrl } from '@/utils/serverUrl'

export interface IndexedFileInfo {
  name: string
  relativePath: string
  absolutePath: string
  fileType: string
  size: number
  lastModified: number
}

/**
 * 文件搜索 API 响应类型
 */
export interface FileSearchResponse {
  success: boolean
  data?: IndexedFileInfo[]
  error?: string
  errorCode?: string  // 错误码：INDEXING 表示正在索引
}

/**
 * 文件搜索结果（包含错误状态）
 */
export interface FileSearchResult {
  files: IndexedFileInfo[]
  isIndexing: boolean  // 是否正在索引
  error?: string
}

/**
 * 类型守卫：检查对象是否为有效的 IndexedFileInfo
 */
export function isIndexedFileInfo(obj: unknown): obj is IndexedFileInfo {
  if (typeof obj !== 'object' || obj === null) return false
  const file = obj as Record<string, unknown>
  return (
    typeof file.name === 'string' &&
    typeof file.relativePath === 'string' &&
    typeof file.absolutePath === 'string' &&
    typeof file.fileType === 'string' &&
    typeof file.size === 'number' &&
    typeof file.lastModified === 'number'
  )
}

/**
 * 类型守卫：检查对象是否为有效的 FileSearchResponse
 */
export function isFileSearchResponse(obj: unknown): obj is FileSearchResponse {
  if (typeof obj !== 'object' || obj === null) return false
  const response = obj as Record<string, unknown>
  if (typeof response.success !== 'boolean') return false
  if (response.data !== undefined && response.data !== null && !Array.isArray(response.data)) return false
  // error 可以是 string、null 或 undefined
  if (response.error !== undefined && response.error !== null && typeof response.error !== 'string') return false
  return true
}

export class FileSearchService {
  private getApiUrl(path: string): string {
    const baseUrl = resolveServerHttpUrl()
    return `${baseUrl}${path}`
  }

  /**
   * 搜索文件（空查询时返回项目根目录文件）
   * @param query 搜索关键词（空字符串返回根目录文件）
   * @param maxResults 最大结果数（默认 10）
   * @returns 搜索结果，包含文件列表和索引状态
   */
  async searchFiles(query: string, maxResults: number = 10): Promise<FileSearchResult> {
    try {
      const response = await fetch(
        this.getApiUrl(`/api/files/search?query=${encodeURIComponent(query)}&maxResults=${maxResults}`)
      )

      if (!response.ok) {
        console.warn('文件搜索失败:', response.statusText)
        return { files: [], isIndexing: false, error: response.statusText }
      }

      const result: unknown = await response.json()

      // 使用类型守卫验证响应
      if (!isFileSearchResponse(result)) {
        console.warn('文件搜索响应格式无效:', result)
        return { files: [], isIndexing: false, error: '响应格式无效' }
      }

      // 检查是否是索引中状态
      if (result.errorCode === 'INDEXING') {
        return { files: [], isIndexing: true, error: result.error }
      }

      if (result.success && result.data) {
        // 过滤并验证每个文件对象
        return { files: result.data.filter(isIndexedFileInfo), isIndexing: false }
      }

      console.warn('文件搜索失败:', result.error)
      return { files: [], isIndexing: false, error: result.error }
    } catch (error) {
      console.error('文件搜索异常:', error)
      return { files: [], isIndexing: false, error: String(error) }
    }
  }

}

// 导出单例
export const fileSearchService = new FileSearchService()

