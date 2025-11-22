/**
 * Claude 会话对象 - 基于 WebSocket RPC
 *
 * 架构原则: 一个 ClaudeSession 实例 = 一个 WebSocket 连接 = 一个 Claude 会话
 */

import { resolveServerWsUrl } from '@/utils/serverUrl'
import { loggers } from '@/utils/logger'

const log = loggers.claude

export interface Message {
  type: string
  content?: any
  timestamp?: number
}

export interface ConnectOptions {
  print?: boolean
  outputFormat?: string
  verbose?: boolean
  includePartialMessages?: boolean
  dangerouslySkipPermissions?: boolean
  allowDangerouslySkipPermissions?: boolean
  // 其他可选参数（保留向后兼容）
  model?: string
  permissionMode?: string
  maxTurns?: number
  systemPrompt?: string
  [key: string]: any
}

/**
 * 内容块类型 (stream-json 格式)
 *
 * 包含文本、图片、工具调用和工具结果
 */
export type ContentBlock = TextContent | ImageContent | ToolUseContent | ToolResultContent | ThinkingContent

export interface TextContent {
  type: 'text'
  text: string
}

export interface ImageContent {
  type: 'image'
  data: string      // base64 encoded
  mimeType: string  // e.g., 'image/png', 'image/jpeg'
}

export interface ToolUseContent {
  type: 'tool_use'
  id: string
  name: string
  input: Record<string, unknown>
}

export interface ToolResultContent {
  type: 'tool_result'
  tool_use_id: string
  content: string | any[]
  is_error?: boolean
}

export interface ThinkingContent {
  type: 'thinking'
  thinking: string
}

export interface RpcRequest {
  id: string
  method: string
  params?: any
}

export interface RpcResponse {
  id: string
  result?: any
  error?: string
}

export interface RpcStreamData {
  id: string
  type: 'stream'
  data: Message
}

export interface RpcStreamComplete {
  id: string
  type: 'complete'
}

type MessageHandler = (message: Message) => void
type ErrorHandler = (error: Error) => void

export class ClaudeSession {
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
   * 连接到服务器并初始化 Claude 会话
   */
  async connect(options?: ConnectOptions): Promise<string> {
    log.debug('connect: 开始连接', options?.model ? `model=${options.model}` : '')

    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.wsUrl)

      this.ws.onopen = async () => {
        log.debug('WebSocket 连接已建立')

        try {
          // 发送 connect RPC 请求
          const result = await this.sendRequest('connect', options)
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
   * 发送消息查询 (支持图片)
   *
   * Content 格式:
   * - 文本: { type: 'text', text: '...' }
   * - 图片: { type: 'image', data: 'base64...', mimeType: 'image/png' }
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
  async getHistory(): Promise<Message[]> {
    const result = await this.sendRequest('getHistory')
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
  private sendRequest(method: string, params?: any): Promise<any> {
    return new Promise((resolve, reject) => {
      const id = `req-${++this.requestIdCounter}`
      const request: RpcRequest = { id, method, params }

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
   * 处理服务器消息
   */
  private handleMessage(data: string) {
    try {
      const message = JSON.parse(data)

      // 处理流式数据
      if (message.type === 'stream') {
        const streamData = message as RpcStreamData
        this.messageHandlers.forEach(handler => handler(streamData.data))
        return
      }

      // 处理流完成
      if (message.type === 'complete') {
        const completeMsg = message as RpcStreamComplete
        log.debug(`handleMessage: 流完成, id=${completeMsg.id}`)
        const pending = this.pendingRequests.get(completeMsg.id)
        if (pending) {
          this.pendingRequests.delete(completeMsg.id)
          pending.resolve({ status: 'complete' })
        }
        return
      }

      // 处理 RPC 响应
      const response = message as RpcResponse
      const pending = this.pendingRequests.get(response.id)

      if (pending) {
        this.pendingRequests.delete(response.id)

        if (response.error) {
          const error = new Error(response.error)
          this.handleError(error)
          pending.reject(error)
        } else {
          pending.resolve(response.result)
        }
      }
    } catch (error) {
      log.error('处理消息失败:', error)
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

