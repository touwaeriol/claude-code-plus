/**
 * Claude 服务
 * 封装所有与 Claude SDK 交互的接口
 *
 * 架构升级：
 * - 使用 WebSocket 进行会话交互（双向通信、自动资源管理）
 * - 移除旧的 HTTP API 调用（connect/disconnect 由 WebSocket 自动管理）
 */

import { websocketClient } from './websocketClient'
import type { WebSocketResponse } from './websocketClient'

export class ClaudeService {
  /**
   * 连接到指定会话
   *
   * @param sessionId 会话ID
   * @param onMessage 消息处理回调
   */
  async connect(
    sessionId: string,
    onMessage: (response: WebSocketResponse) => void
  ): Promise<void> {
    console.log(`🔌 连接到会话: ${sessionId}`)
    await websocketClient.connect(sessionId, onMessage)
  }

  /**
   * 发送消息给 Claude
   *
   * @param sessionId 会话ID
   * @param message 用户消息内容
   */
  sendMessage(sessionId: string, message: string): void {
    console.log(`📤 发送消息到会话 ${sessionId}: ${message.substring(0, 50)}...`)
    websocketClient.sendMessage(sessionId, message)
  }

  /**
   * 中断当前操作
   *
   * @param sessionId 会话ID
   */
  interrupt(sessionId: string): void {
    console.log(`⏸️ 中断会话: ${sessionId}`)
    websocketClient.interrupt(sessionId)
  }

  /**
   * 断开会话连接
   *
   * @param sessionId 会话ID
   */
  disconnect(sessionId: string): void {
    console.log(`🔌 断开会话: ${sessionId}`)
    websocketClient.disconnect(sessionId)
  }

  /**
   * 断开所有会话连接
   */
  disconnectAll(): void {
    console.log(`🔌 断开所有会话连接`)
    websocketClient.disconnectAll()
  }

  /**
   * 检查会话是否已连接
   *
   * @param sessionId 会话ID
   */
  isConnected(sessionId: string): boolean {
    return websocketClient.isConnected(sessionId)
  }

  /**
   * 获取活跃连接数
   */
  getActiveConnectionCount(): number {
    return websocketClient.getActiveConnectionCount()
  }
}

// 导出单例
export const claudeService = new ClaudeService()
