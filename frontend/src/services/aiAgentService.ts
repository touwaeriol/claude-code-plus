/**
 * AI Agent 服务
 * 封装 RSocket + Protobuf 会话生命周期管理。
 *
 * 迁移说明：已从 WebSocket JSON-RPC 迁移到 RSocket + Protobuf
 */

import {
  RSocketSession,
  ConnectOptions as SessionConnectOptions,
  ContentBlock
} from './rsocket'
import type { AgentStreamEvent } from './rsocket'
import type { HistorySessionMetadata } from '@/types/session'
import type {
  RpcCapabilities,
  RpcPermissionMode,
  RpcSetPermissionModeResult,
  RpcMessage
} from '@/types/rpc'

export type ConnectOptions = SessionConnectOptions

export type MessageHandler = (message: RpcMessage) => void

/**
 * 服务器请求处理器类型（双向 RPC）
 */
export type ServerRequestHandler = (params: any) => Promise<any>

/** connect 返回结果 */
export interface ConnectResult {
  sessionId: string
  capabilities: RpcCapabilities | null
}

export class AiAgentService {
  // 会话管理 - sessionId -> RSocketSession
  private sessions = new Map<string, RSocketSession>()
  // 用于历史查询的后台会话（无 UI 绑定）
  private historySession: RSocketSession | null = null

  private async ensureHistoryTransport(): Promise<RSocketSession> {
    const active = this.sessions.values().next().value as RSocketSession | undefined
    if (active) return active

    if (!this.historySession) {
      this.historySession = new RSocketSession()
      await this.historySession.connect({})
    } else if (!this.historySession.isConnected) {
      await this.historySession.connect({})
    }
    return this.historySession
  }

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
    const session = new RSocketSession()

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
   * 重连会话（复用 WebSocket）
   * 只发送 disconnect + connect RPC，不关闭 WebSocket
   *
   * @param sessionId 当前会话ID
   * @param options 连接选项
   * @returns 新的会话ID
   */
  async reconnectSession(
    sessionId: string,
    options?: ConnectOptions
  ): Promise<{ sessionId: string; capabilities: RpcCapabilities | null }> {
    const session = this.sessions.get(sessionId)
    if (!session) {
      throw new Error(`会话不存在: ${sessionId}`)
    }

    console.log(`🔄 重连会话: ${sessionId}`)

    const newSessionId = await session.reconnectSession(options)

    // 更新 sessions map（如果 sessionId 变化）
    if (newSessionId !== sessionId) {
      this.sessions.delete(sessionId)
      this.sessions.set(newSessionId, session)
    }

    return {
      sessionId: newSessionId,
      capabilities: session.capabilities
    }
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
   * @returns RSocketSession 实例，如果不存在则返回 undefined
   */
  getSession(sessionId: string): RSocketSession | undefined {
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

  /**
   * 注册服务器请求处理器（双向 RPC）
   *
   * 用于处理服务器主动发起的请求，如 AskUserQuestion。
   *
   * @param sessionId 会话ID
   * @param method 方法名（如 'AskUserQuestion'）
   * @param handler 处理函数
   * @returns 取消注册的函数，失败时返回空函数
   *
   * @example
   * aiAgentService.register(sessionId, 'AskUserQuestion', async (params) => {
   *   const answers = await showQuestionDialog(params.questions)
   *   return { answers }
   * })
   */
  register(
    sessionId: string,
    method: string,
    handler: ServerRequestHandler
  ): () => void {
    const session = this.sessions.get(sessionId)
    if (!session) {
      console.warn(`[aiAgentService] 注册处理器失败，会话不存在: ${sessionId}`)
      return () => {}
    }

    console.log(`🔧 注册服务器请求处理器: ${sessionId} -> ${method}`)
    return session.register(method, handler)
  }

  /**
   * 批量注册服务器请求处理器
   *
   * @param sessionId 会话ID
   * @param handlers 处理器映射 { method: handler }
   * @returns 取消所有注册的函数
   */
  registerAll(
    sessionId: string,
    handlers: Record<string, ServerRequestHandler>
  ): () => void {
    const unregisterFns: Array<() => void> = []

    for (const [method, handler] of Object.entries(handlers)) {
      unregisterFns.push(this.register(sessionId, method, handler))
    }

    return () => {
      unregisterFns.forEach(fn => fn())
    }
  }

  /**
   * 获取项目的历史会话列表
   *
   * @param maxResults 最大结果数（默认 50）
   * @returns 历史会话列表
   */
  async getHistorySessions(maxResults: number = 50): Promise<HistorySessionMetadata[]> {
    try {
      const session = await this.ensureHistoryTransport()
      console.log(`📋 获取历史会话列表 (maxResults=${maxResults})`)
      return await session.getHistorySessions(maxResults)
    } catch (error) {
      console.warn('[aiAgentService] 获取历史会话列表失败:', error)
      return []
    }
  }

  /**
   * 流式拉取历史消息（用于历史会话回放）
   */
  streamHistory(
    params: { sessionId?: string; projectPath?: string; offset?: number; limit?: number },
    handlers: {
      onMessage: (message: RpcMessage) => void
      onError?: (error: Error) => void
      onComplete?: () => void
    },
    transportSessionId?: string
  ): () => void {
    const session = transportSessionId ? this.sessions.get(transportSessionId) : undefined
    if (session) {
      return session.loadHistory(params, handlers)
    }

    // 无显式会话时，使用后台会话（异步创建）
    let cancel: (() => void) | null = null
    this.ensureHistoryTransport()
      .then(s => {
        cancel = s.loadHistory(params, handlers)
      })
      .catch(err => handlers.onError?.(err as Error))
    return () => cancel?.()
  }
}

// 导出单例
export const aiAgentService = new AiAgentService()
