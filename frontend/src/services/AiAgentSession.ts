/**
 * AI Agent 会话对象 - 基于 WebSocket RPC
 *
 * 架构原则: 一个 AiAgentSession 实例 = 一个 WebSocket 连接 = 一个统一会话
 */

import { resolveServerWsUrl } from '@/utils/serverUrl'
import { loggers } from '@/utils/logger'
import {
  parseRpcMessage,
  isRpcStreamWrapper,
  isRpcCompleteWrapper,
  isRpcResultWrapper,
  isRpcErrorWrapper,
  isRpcMessage
} from '@/utils/rpcParser'
import type {
  RpcProvider,
  RpcContentBlock,
  RpcStreamEvent,
  RpcConnectOptions,
  RpcCapabilities,
  RpcPermissionMode,
  RpcConnectResult,
  RpcSetPermissionModeResult,
  RpcMessage
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

type MessageHandler = (message: RpcMessage) => void
type ErrorHandler = (error: Error) => void

export class AiAgentSession {
  private ws: WebSocket | null = null
  private _isConnected = false
  private sessionId: string | null = null
  private _capabilities: RpcCapabilities | null = null
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
   * 获取当前 Agent 的能力声明
   */
  get capabilities(): RpcCapabilities | null {
    return this._capabilities
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
          const result = await this.sendRequest('connect', options) as RpcConnectResult
          this.sessionId = result.sessionId
          this._capabilities = result.capabilities || null
          this._isConnected = true
          log.info(`会话已连接: ${this.sessionId}`, this._capabilities ? `capabilities=${JSON.stringify(this._capabilities)}` : '')

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
   * 设置权限模式
   * @param mode 权限模式
   * @throws 如果当前 provider 不支持切换权限模式
   */
  async setPermissionMode(mode: RpcPermissionMode): Promise<RpcSetPermissionModeResult> {
    this.checkCapability('canSwitchPermissionMode', 'setPermissionMode')
    const result = await this.sendRequest('setPermissionMode', { mode }) as RpcSetPermissionModeResult
    log.info(`权限模式已切换为: ${result.mode}`)
    return result
  }

  /**
   * 检查能力是否支持
   * @param cap 能力名称
   * @param method 方法名称（用于错误消息）
   */
  private checkCapability(cap: keyof RpcCapabilities, method: string): void {
    if (!this._capabilities) {
      throw new Error(`${method}: 能力信息未加载，请先调用 connect()`)
    }
    if (!this._capabilities[cap]) {
      throw new Error(`${method}: 当前 provider 不支持此操作`)
    }
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
      const parsed = parseRpcMessage(raw)

      if (!parsed) {
        throw new Error('[AiAgentSession] 收到无效的 RPC 消息，未通过类型校验')
      }

      // ???????payload ? RpcMessage?type: stream_event/assistant/...?
      if (isRpcStreamWrapper(parsed)) {
        const payload = parsed.data
        const messageType = payload && typeof (payload as any).type === 'string' ? (payload as any).type : 'unknown'

        console.log(`[AiAgentSession] ??????: type=${messageType}, handlers=${this.messageHandlers.size}`)
        log.info('[AiAgentSession] ??????:', {
          type: messageType,
          id: parsed.id,
          hasHandlers: this.messageHandlers.size > 0
        })

        if (!isRpcMessage(payload)) {
          throw new Error('[AiAgentSession] stream 包装的 payload 不是合法 RpcMessage')
        }

        this.messageHandlers.forEach(handler => {
          try {
            handler(payload)
          } catch (error) {
            console.error('[AiAgentSession] ?????????', error, payload)
            log.error('[AiAgentSession] ?????????', error, payload)
          }
        })
        return
      }

      // ???
      if (isRpcCompleteWrapper(parsed)) {
        log.info(`[AiAgentSession] ??? id=${parsed.id}`)
        const pending = this.pendingRequests.get(parsed.id)
        if (pending) {
          this.pendingRequests.delete(parsed.id)
          pending.resolve({ status: 'complete' })
        } else {
          log.warn(`[AiAgentSession] ????????????: id=${parsed.id}`)
        }
        return
      }

      // RPC ??
      if (isRpcResultWrapper(parsed)) {
        const pending = this.pendingRequests.get(parsed.id)
        if (pending) {
          this.pendingRequests.delete(parsed.id)
          pending.resolve(parsed.result)
        }
        return
      }

      // RPC ??
      if (isRpcErrorWrapper(parsed)) {
        const pending = this.pendingRequests.get(parsed.id)
        if (pending) {
          this.pendingRequests.delete(parsed.id)
          const error = new Error(parsed.error.message)
          this.handleError(error)
          pending.reject(error)
        }
        return
      }

      // ?? RpcMessage?? wrapper?????? handler
      if (isRpcMessage(parsed)) {
        const messageType = parsed.type
        console.log(`[AiAgentSession] ??????: type=${messageType}, handlers=${this.messageHandlers.size}`)
        log.info('[AiAgentSession] ??????:', {
          type: messageType,
          id: (parsed as any).id,
          hasHandlers: this.messageHandlers.size > 0
        })
        this.messageHandlers.forEach(handler => {
          try {
            handler(parsed)
          } catch (error) {
            console.error('[AiAgentSession] ?????????', error, parsed)
            log.error('[AiAgentSession] ?????????', error, parsed)
          }
        })
        return
      }

      log.warn('[AiAgentSession] ????????:', parsed)
    } catch (error) {
      console.error('[AiAgentSession] 消息处理异常:', error, data)
      log.error('[AiAgentSession] 消息处理异常:', error, data)
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
