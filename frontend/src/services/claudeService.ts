/**
 * Claude 服务
 * 封装所有与 Claude SDK 交互的接口
 *
 * 架构升级 v2:
 * - 使用新的 WebSocket RPC 架构 (一个连接 = 一个会话)
 * - 基于 ClaudeSession 封装,提供类型安全的 RPC 调用
 * - 移除旧的 HTTP API 和多会话 WebSocket 管理
 */

import { ClaudeSession, ConnectOptions as ClaudeSessionConnectOptions, Message } from './ClaudeSession'

export interface ConnectOptions extends ClaudeSessionConnectOptions {
  continueConversation?: boolean
  resume?: string
  includePartialMessages?: boolean  // 启用 StreamEvent，用于实时渲染
}

// 这里的 message 是 WebSocket RPC 的原始消息, 在 sessionStore 中再做归一化
export type MessageHandler = (message: any) => void

export class ClaudeService {
  // 会话管理 - sessionId -> ClaudeSession
  private sessions = new Map<string, ClaudeSession>()

  /**
   * 创建并连接到新会话
   *
   * @param options 连接选项
   * @param onMessage 消息处理回调
   * @returns 会话ID
   */
  async connect(
    options: ConnectOptions = {},
    onMessage: MessageHandler
  ): Promise<string> {
    const session = new ClaudeSession()

    // 订阅消息
    session.onMessage(onMessage)

    // 连接并获取会话ID
    const sessionId = await session.connect(options)

    // 保存会话实例
    this.sessions.set(sessionId, session)

    console.log(`🔌 会话已连接: ${sessionId}`)
    return sessionId
  }

  /**
   * 发送消息给 Claude (纯文本，内部转为 stream-json 格式)
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
   * 发送消息给 Claude (支持图片，stream-json 格式)
   *
   * @param sessionId 会话ID
   * @param content 内容块数组 [{ type: 'text', text: '...' }, { type: 'image', data: '...', mimeType: '...' }]
   */
  async sendMessageWithContent(sessionId: string, content: import('./ClaudeSession').ContentBlock[]): Promise<void> {
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
  async getHistory(sessionId: string): Promise<Message[]> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    return await session.getHistory()
  }
}

// 导出单例
export const claudeService = new ClaudeService()
