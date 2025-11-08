/**
 * Claude 服务
 * 封装所有与 Claude SDK 交互的接口
 */

import { apiClient } from './apiClient'
import type { FrontendResponse } from '@/types/bridge'

export class ClaudeService {
  /**
   * 连接到 Claude
   */
  async connect(options?: any): Promise<FrontendResponse> {
    return apiClient.request('claude.connect', options)
  }

  /**
   * 发送消息给 Claude
   */
  async query(message: string): Promise<FrontendResponse> {
    return apiClient.request('claude.query', { message })
  }

  /**
   * 中断当前操作
   */
  async interrupt(): Promise<FrontendResponse> {
    return apiClient.request('claude.interrupt')
  }

  /**
   * 断开连接
   */
  async disconnect(): Promise<FrontendResponse> {
    return apiClient.request('claude.disconnect')
  }
}

// 导出单例
export const claudeService = new ClaudeService()
