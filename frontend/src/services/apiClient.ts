/**
 * 统一的 HTTP API 客户端
 * 负责所有与后端的 HTTP 通信
 *
 * 架构升级：
 * - 保留旧的统一 POST /api/ 接口（向后兼容）
 * - 新增 RESTful API 方法（推荐使用）
 */

import type { FrontendResponse } from '@/types/bridge'

// 会话数据接口
export interface Session {
  id: string
  name: string
  createdAt: number
  updatedAt: number
  messageCount: number
}

// 消息接口
export interface Message {
  type: string
  content: any
  [key: string]: any
}

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
   * 统一的 API 请求方法（旧版，向后兼容）
   * @deprecated 推荐使用新的 RESTful API 方法
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

  // ==================== RESTful API 方法（新版） ====================

  /**
   * 获取会话列表
   */
  async getSessions(): Promise<Session[]> {
    try {
      const response = await fetch(`${this.baseUrl}/api/sessions`)
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      const data = await response.json()
      return data.sessions || []
    } catch (error) {
      console.error('❌ Failed to get sessions:', error)
      return []
    }
  }

  /**
   * 创建新会话
   */
  async createSession(name?: string): Promise<Session | null> {
    try {
      const response = await fetch(`${this.baseUrl}/api/sessions`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name })
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      return await response.json()
    } catch (error) {
      console.error('❌ Failed to create session:', error)
      return null
    }
  }

  /**
   * 获取会话历史消息
   */
  async getSessionHistory(sessionId: string): Promise<Message[]> {
    try {
      const response = await fetch(`${this.baseUrl}/api/sessions/${sessionId}/history`)
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      const data = await response.json()
      return data.messages || []
    } catch (error) {
      console.error('❌ Failed to get session history:', error)
      return []
    }
  }

  /**
   * 删除会话
   */
  async deleteSession(sessionId: string): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseUrl}/api/sessions/${sessionId}`, {
        method: 'DELETE'
      })
      return response.ok
    } catch (error) {
      console.error('❌ Failed to delete session:', error)
      return false
    }
  }

  /**
   * 重命名会话
   */
  async renameSession(sessionId: string, newName: string): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseUrl}/api/sessions/${sessionId}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ name: newName })
      })
      return response.ok
    } catch (error) {
      console.error('❌ Failed to rename session:', error)
      return false
    }
  }

  /**
   * 获取配置
   */
  async getConfig(): Promise<any> {
    try {
      const response = await fetch(`${this.baseUrl}/api/config`)
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('❌ Failed to get config:', error)
      return {}
    }
  }

  /**
   * 保存配置
   */
  async saveConfig(config: any): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseUrl}/api/config`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(config)
      })
      return response.ok
    } catch (error) {
      console.error('❌ Failed to save config:', error)
      return false
    }
  }

  /**
   * 获取 IDE 主题
   */
  async getTheme(): Promise<any> {
    try {
      const response = await fetch(`${this.baseUrl}/api/theme`)
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('❌ Failed to get theme:', error)
      return null
    }
  }

  /**
   * 获取项目路径
   */
  async getProjectPath(): Promise<string> {
    try {
      const response = await fetch(`${this.baseUrl}/api/project-path`)
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }
      const data = await response.json()
      return data.projectPath || ''
    } catch (error) {
      console.error('❌ Failed to get project path:', error)
      return ''
    }
  }

  // ==================== 通用方法 ====================

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
