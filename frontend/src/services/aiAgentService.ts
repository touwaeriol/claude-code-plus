/**
 * AI Agent 服务
 * 封装 WebSocket 会话生命周期管理。
 */

import {
  AiAgentSession,
  ConnectOptions as SessionConnectOptions,
  ContentBlock
} from './AiAgentSession'
import type { AgentStreamEvent } from './AiAgentSession'
import type {
  RpcCapabilities,
  RpcPermissionMode,
  RpcSetPermissionModeResult,
  RpcMessage
} from '@/types/rpc'

export type ConnectOptions = SessionConnectOptions

export type MessageHandler = (message: RpcMessage) => void

/** connect 返回结果 */
export interface ConnectResult {
  sessionId: string
  capabilities: RpcCapabilities | null
}

export class AiAgentService {
  // 会话管理 - sessionId -> AiAgentSession
  private sessions = new Map<string, AiAgentSession>()

  /**
   * 创建并连接到新会话
   *
   * @param options 连接选项
   * @param onMessage 消息处理回调
   * @returns 连接结果（sessionId + capabilities）
   */
  async connect(
    options: ConnectOptions = {},
    onMessage: MessageHandler
  ): Promise<ConnectResult> {
    const session = new AiAgentSession()

    // 订阅消息
    session.onMessage(onMessage)

    // 连接并获取会话ID
    const sessionId = await session.connect(options)

    // 保存会话实例
    this.sessions.set(sessionId, session)

    console.log(`🔌 会话已连接: ${sessionId}`, session.capabilities)
    return {
      sessionId,
      capabilities: session.capabilities
    }
  }

  /**
   * 发送消息 (纯文本，内部转为 stream-json 格式)
   *
   * @param sessionId 会话ID
   * @param message 用户消息内容
   */
  async sendMessage(sessionId: string, message: string): Promise<void> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    console.log(`📤 发送消息到会话 ${sessionId}: ${message.substring(0, 50)}...`)
    // 统一使用 stream-json 格式，为后续图片支持做准备
    await session.sendMessageWithContent([{ type: 'text', text: message }])
  }

  /**
   * 发送消息 (支持图片，RPC 格式)
   *
   * @param sessionId 会话ID
   * @param content 内容块数组，格式:
   *   - 文本: { type: 'text', text: '...' }
   *   - 图片: { type: 'image', source: { type: 'base64', media_type: 'image/png', data: '...' } }
   */
  async sendMessageWithContent(sessionId: string, content: ContentBlock[]): Promise<void> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    console.log(`📤 发送内容到会话 ${sessionId}: ${content.length} 个内容块`)
    await session.sendMessageWithContent(content)
  }

  /**
   * 中断当前操作
   *
   * @param sessionId 会话ID
   */
  async interrupt(sessionId: string): Promise<void> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    console.log(`⏸️ 中断会话: ${sessionId}`)
    await session.interrupt()
  }

  /**
   * 断开会话连接
   *
   * @param sessionId 会话ID
   */
  async disconnect(sessionId: string): Promise<void> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      console.warn(`会话不存在: ${sessionId}`)
      return
    }

    console.log(`🔌 断开会话: ${sessionId}`)
    await session.disconnect()
    this.sessions.delete(sessionId)
  }

  /**
   * 断开所有会话连接
   */
  async disconnectAll(): Promise<void> {
    console.log(`🔌 断开所有会话连接 (${this.sessions.size} 个)`)

    const disconnectPromises = Array.from(this.sessions.values()).map(session =>
      session.disconnect().catch(err => console.error('断开会话失败:', err))
    )

    await Promise.all(disconnectPromises)
    this.sessions.clear()
  }

  /**
   * 检查会话是否已连接
   *
   * @param sessionId 会话ID
   */
  isConnected(sessionId: string): boolean {
    const session = this.sessions.get(sessionId)
    return session?.isConnected ?? false
  }

  /**
   * 获取会话实例
   *
   * @param sessionId 会话ID
   * @returns AiAgentSession 实例，如果不存在则返回 undefined
   */
  getSession(sessionId: string): AiAgentSession | undefined {
    return this.sessions.get(sessionId)
  }

  /**
   * 获取活跃连接数
   */
  getActiveConnectionCount(): number {
    return this.sessions.size
  }

  /**
   * 设置模型
   *
   * @param sessionId 会话ID
   * @param model 模型名称
   */
  async setModel(sessionId: string, model: string): Promise<void> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    console.log(`🔧 设置模型: ${sessionId} -> ${model}`)
    await session.setModel(model)
  }

  /**
   * 获取会话历史
   *
   * @param sessionId 会话ID
   */
  async getHistory(sessionId: string): Promise<AgentStreamEvent[]> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    return await session.getHistory()
  }

  /**
   * 设置权限模式
   *
   * @param sessionId 会话ID
   * @param mode 权限模式
   */
  async setPermissionMode(sessionId: string, mode: RpcPermissionMode): Promise<RpcSetPermissionModeResult> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    console.log(`🔧 设置权限模式: ${sessionId} -> ${mode}`)
    return await session.setPermissionMode(mode)
  }

  /**
   * 获取会话能力信息
   *
   * @param sessionId 会话ID
   */
  getCapabilities(sessionId: string): RpcCapabilities | null {
    const session = this.sessions.get(sessionId)
    if (!session) {
      return null
    }
    return session.capabilities
  }
}

// 导出单例
export const aiAgentService = new AiAgentService()
