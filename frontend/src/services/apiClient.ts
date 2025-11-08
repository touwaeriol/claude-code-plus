/**
 * 统一的 HTTP API 客户端
 * 负责所有与后端的 HTTP 通信
 */

import type { FrontendResponse } from '@/types/bridge'

export class ApiClient {
  private baseUrl: string = ''

  constructor() {
    this.detectBaseUrl()
  }

  /**
   * 自动检测后端服务 URL
   */
  private async detectBaseUrl() {
    // 尝试从当前页面 URL 获取
    if (window.location.host) {
      this.baseUrl = `${window.location.protocol}//${window.location.host}`
      console.log(`✅ API Base URL detected: ${this.baseUrl}`)
      return
    }

    // 降级到默认端口
    this.baseUrl = 'http://localhost:8765'
    console.log(`⚠️ Using default API Base URL: ${this.baseUrl}`)
  }

  /**
   * 统一的 API 请求方法
   */
  async request<T = any>(
    action: string,
    data?: any
  ): Promise<FrontendResponse<T>> {
    try {
      const response = await fetch(`${this.baseUrl}/api/`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          action,
          data: data || {}
        })
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const result: FrontendResponse<T> = await response.json()
      return result
    } catch (error) {
      console.error(`❌ API request failed [${action}]:`, error)
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error'
      }
    }
  }

  /**
   * 获取服务器健康状态
   */
  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseUrl}/health`)
      return response.ok
    } catch (error) {
      return false
    }
  }

  /**
   * 获取当前 Base URL
   */
  getBaseUrl(): string {
    return this.baseUrl
  }
}

// 导出单例
export const apiClient = new ApiClient()
