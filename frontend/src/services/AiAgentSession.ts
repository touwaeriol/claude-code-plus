/**
 * AI Agent 会话对象 - 基于 WebSocket RPC
 *
 * 架构原则: 一个 AiAgentSession 实例 = 一个 WebSocket 连接 = 一个统一会话
 */

import { resolveServerWsUrl } from '@/utils/serverUrl'
import { loggers } from '@/utils/logger'
import {
  parseRpcMessage,
  extractStreamEvent,
  isRpcStreamWrapper,
  isRpcCompleteWrapper,
  isRpcResultWrapper,
  isRpcErrorWrapper
} from '@/utils/rpcParser'
import type {
  RpcProvider,
  RpcContentBlock,
  RpcStreamEvent,
  RpcConnectOptions
} from '@/types/rpc'

const log = loggers.agent

// 重新导出类型，保持向后兼容
export type { RpcProvider, RpcContentBlock, RpcStreamEvent, RpcConnectOptions }

/** 连接选项（向后兼容别名） */
export type ConnectOptions = RpcConnectOptions

/** 流式事件（向后兼容别名） */
export type AgentStreamEvent = RpcStreamEvent

/** 内容块（向后兼容别名） */
export type ContentBlock = RpcContentBlock

type MessageHandler = (message: AgentStreamEvent) => void
type ErrorHandler = (error: Error) => void

export class AiAgentSession {
  private ws: WebSocket | null = null
  private _isConnected = false
  private sessionId: string | null = null
  private messageHandlers = new Set<MessageHandler>()
  private errorHandlers = new Set<ErrorHandler>()
  private pendingRequests = new Map<string, {
    resolve: (value: any) => void
    reject: (error: Error) => void
  }>()
  private requestIdCounter = 0
  private wsUrl: string

  constructor(wsUrl?: string) {
    if (wsUrl) {
      this.wsUrl = wsUrl
    } else {
      this.wsUrl = resolveServerWsUrl()
    }
    log.debug(`WebSocket URL: ${this.wsUrl}`)
  }

  get isConnected(): boolean {
    return this._isConnected
  }

  get currentSessionId(): string | null {
    return this.sessionId
  }

  /**
   * 连接到服务器并初始化会话
   */
  async connect(options?: ConnectOptions): Promise<string> {
    log.debug('connect: 开始连接', options?.model ? `model=${options.model}` : '')

    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.wsUrl)

      this.ws.onopen = async () => {
        log.debug('WebSocket 连接已建立')

        try {
          // 发送 connect RPC 请求
          const result = await this.sendRequest('connect', options) as { sessionId: string }
          this.sessionId = result.sessionId
          this._isConnected = true
          log.info(`会话已连接: ${this.sessionId}`)

          // 安全检查：确保 sessionId 已设置
          if (!this.sessionId) {
            const error = new Error('连接成功但未返回 sessionId')
            this.handleError(error)
            reject(error)
            return
          }
          resolve(this.sessionId)
        } catch (error) {
          this.handleError(error as Error)
          reject(error)
        }
      }

      this.ws.onmessage = (event) => {
        this.handleMessage(event.data)
      }

      this.ws.onerror = (error) => {
        log.error('WebSocket 错误:', error)
        const err = new Error('WebSocket connection failed')
        this.handleError(err)
        reject(err)
      }

      this.ws.onclose = () => {
        log.debug('WebSocket 连接已关闭')
        this._isConnected = false
        this.sessionId = null
      }
    })
  }

  /**
   * 发送消息查询 (纯文本)
   */
  async sendMessage(message: string): Promise<void> {
    if (!this._isConnected) {
      throw new Error('Session not connected')
    }

    // 发送 query 请求 (流式响应)
    // 注意: 后端期望 params 是 {message: "..."}
    await this.sendRequest('query', { message })
  }

  /**
   * 发送消息查询 (支持图片等富媒体内容)
   *
   * Content 格式:
   * - 文本: { type: 'text', text: '...' }
   * - 图片: { type: 'image', source: { type: 'base64', media_type: 'image/png', data: '...' } }
   * - 思维: { type: 'thinking', thinking: '...' }
   */
  async sendMessageWithContent(content: ContentBlock[]): Promise<void> {
    if (!this._isConnected) {
      throw new Error('Session not connected')
    }

    await this.sendRequest('queryWithContent', { content })
  }

  /**
   * 中断当前操作
   */
  async interrupt(): Promise<void> {
    if (!this._isConnected) {
      throw new Error('Session not connected')
    }

    await this.sendRequest('interrupt')
  }

  /**
   * 断开连接
   */
  async disconnect(): Promise<void> {
    if (this._isConnected) {
      await this.sendRequest('disconnect')
      this.ws?.close()
      this._isConnected = false
      this.sessionId = null
    }
  }

  /**
   * 设置模型
   */
  async setModel(model: string): Promise<void> {
    await this.sendRequest('setModel', model)
  }

  /**
   * 获取历史消息
   */
  async getHistory(): Promise<AgentStreamEvent[]> {
    const result = await this.sendRequest('getHistory') as { messages?: AgentStreamEvent[] }
    return result.messages || []
  }

  /**
   * 订阅消息事件
   */
  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler)
    return () => this.messageHandlers.delete(handler)
  }

  /**
   * 订阅错误事件
   */
  onError(handler: ErrorHandler): () => void {
    this.errorHandlers.add(handler)
    return () => this.errorHandlers.delete(handler)
  }

  /**
   * 发送 RPC 请求
   */
  private sendRequest(method: string, params?: unknown): Promise<unknown> {
    return new Promise((resolve, reject) => {
      const id = `req-${++this.requestIdCounter}`
      const request = { id, method, params }

      log.debug(`sendRequest: method=${method}, id=${id}`)

      this.pendingRequests.set(id, { resolve, reject })

      if (!this.ws) {
        const error = new Error('WebSocket 未初始化')
        reject(error)
        return
      }

      this.ws.send(JSON.stringify(request))
    })
  }

  /**
   * 处理服务器消息（使用类型守卫进行类型安全解析）
   */
  private handleMessage(data: string) {
    try {
      const raw: unknown = JSON.parse(data)
      const message = parseRpcMessage(raw)

      if (!message) {
        log.warn('⚠️ [AiAgentSession] 无法解析消息:', data.substring(0, 200))
        log.warn('⚠️ [AiAgentSession] 原始数据:', data)
        return
      }

      const messageType = 'type' in message ? message.type : ('result' in message ? 'result' : 'error')
      log.info('📨 [AiAgentSession] 收到消息:', {
        type: messageType,
        id: message.id,
        hasHandlers: this.messageHandlers.size > 0
      })

      // 处理流式数据
      if (isRpcStreamWrapper(message)) {
        const streamEvent = extractStreamEvent(message)
        if (streamEvent) {
          log.info('📤 [AiAgentSession] 转发流式事件:', {
            id: message.id,
            eventType: streamEvent.type,
            handlerCount: this.messageHandlers.size
          })
          this.messageHandlers.forEach(handler => {
            try {
              handler(streamEvent)
            } catch (error) {
              log.error('❌ [AiAgentSession] 消息处理器执行失败:', error, streamEvent)
            }
          })
        } else {
          log.warn('⚠️ [AiAgentSession] 无法提取流式事件:', message)
        }
        return
      }

      // 处理流完成
      if (isRpcCompleteWrapper(message)) {
        log.info(`✅ [AiAgentSession] 流完成, id=${message.id}`)
        const pending = this.pendingRequests.get(message.id)
        if (pending) {
          this.pendingRequests.delete(message.id)
          pending.resolve({ status: 'complete' })
        } else {
          log.warn(`⚠️ [AiAgentSession] 流完成但找不到对应的请求: id=${message.id}`)
        }
        return
      }

      // 处理 RPC 结果
      if (isRpcResultWrapper(message)) {
        const pending = this.pendingRequests.get(message.id)
        if (pending) {
          this.pendingRequests.delete(message.id)
          pending.resolve(message.result)
        }
        return
      }

      // 处理 RPC 错误
      if (isRpcErrorWrapper(message)) {
        const pending = this.pendingRequests.get(message.id)
        if (pending) {
          this.pendingRequests.delete(message.id)
          const error = new Error(message.error.message)
          this.handleError(error)
          pending.reject(error)
        }
        return
      }

      log.warn('⚠️ [AiAgentSession] 未处理的消息类型:', message)
    } catch (error) {
      log.error('❌ [AiAgentSession] 处理消息失败:', error, data)
      this.handleError(error as Error)
    }
  }

  /**
   * 处理错误
   */
  private handleError(error: Error) {
    this.errorHandlers.forEach(handler => {
      try {
        handler(error)
      } catch (err) {
        log.error('错误处理器执行失败:', err)
      }
    })
  }
}
