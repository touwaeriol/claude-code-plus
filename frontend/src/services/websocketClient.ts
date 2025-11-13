/**
 * WebSocket 客户端
 *
 * 负责管理与后端的 WebSocket 连接，提供消息发送和接收功能
 *
 * 架构设计：
 * - 每个会话独立的 WebSocket 连接
 * - 自动重连机制
 * - 消息队列（连接建立前的消息缓存）
 * - 事件监听器管理
 */

import type { ContentBlock } from '@/types/message'

interface WebSocketMessage {
  type: 'query' | 'interrupt'
  data?: any
}

// ===== 类型安全的 WebSocket 响应定义 =====

/**
 * 用户消息响应
 */
export interface UserMessageResponse {
  type: 'user'
  message: {
    content: string
  }
}

/**
 * AI 助手消息响应
 */
export interface AssistantMessageResponse {
  type: 'assistant'
  message: {
    content: ContentBlock[]
    model: string
    isStreaming: boolean
  }
}

/**
 * 系统消息响应
 */
export interface SystemMessageResponse {
  type: 'system'
  message: {
    subtype: 'init' | 'message_sent' | 'model_changed' | string
    data?: any
    message?: string
  }
}

/**
 * 结果消息响应（会话结束）
 */
export interface ResultMessageResponse {
  type: 'result'
  message: {
    subtype: string
    duration_ms: number
    is_error: boolean
    num_turns: number
    session_id: string
    result?: any
    usage?: {
      input_tokens: number
      output_tokens: number
      cache_creation_input_tokens?: number
      cache_read_input_tokens?: number
    }
  }
}

/**
 * 流事件响应
 */
export interface StreamEventResponse {
  type: 'stream_event'
  message: {
    uuid: string
    session_id: string
    event: any
  }
}

/**
 * 错误消息响应
 */
export interface ErrorMessageResponse {
  type: 'error'
  message: {
    error: string
  }
}

/**
 * WebSocket 响应联合类型
 *
 * 使用判别联合类型（Discriminated Union）实现类型安全
 * TypeScript 会根据 type 字段自动推断具体类型
 */
export type WebSocketResponse =
  | UserMessageResponse
  | AssistantMessageResponse
  | SystemMessageResponse
  | ResultMessageResponse
  | StreamEventResponse
  | ErrorMessageResponse

export type MessageHandler = (response: WebSocketResponse) => void
type ErrorHandler = (error: Event) => void
type CloseHandler = () => void

/**
 * WebSocket 连接管理器
 */
class WebSocketConnection {
  private ws: WebSocket | null = null
  private sessionId: string
  private url: string
  private messageHandlers: Set<MessageHandler> = new Set()
  private errorHandlers: Set<ErrorHandler> = new Set()
  private closeHandlers: Set<CloseHandler> = new Set()
  private messageQueue: WebSocketMessage[] = []
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  private reconnectDelay = 1000
  private isManualClose = false

  constructor(sessionId: string, baseUrl: string) {
    this.sessionId = sessionId
    this.url = `${baseUrl}/ws/sessions/${sessionId}`
  }

  /**
   * 建立连接
   */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.url)

        this.ws.onopen = () => {
          console.log(`🔌 WebSocket 连接已建立: ${this.sessionId}`)
          this.reconnectAttempts = 0

          // 发送队列中的消息
          this.flushMessageQueue()

          resolve()
        }

        this.ws.onmessage = (event) => {
          try {
            const response: WebSocketResponse = JSON.parse(event.data)
            console.log(`📨 收到 WebSocket 消息: ${response.type}`)

            // 分发给所有处理器
            this.messageHandlers.forEach(handler => {
              try {
                handler(response)
              } catch (e) {
                console.error('消息处理器错误:', e)
              }
            })
          } catch (e) {
            console.error('解析 WebSocket 消息失败:', e)
          }
        }

        this.ws.onerror = (error) => {
          console.error(`❌ WebSocket 错误: ${this.sessionId}`, error)

          // 分发错误事件
          this.errorHandlers.forEach(handler => {
            try {
              handler(error)
            } catch (e) {
              console.error('错误处理器异常:', e)
            }
          })

          reject(error)
        }

        this.ws.onclose = () => {
          console.log(`🔌 WebSocket 连接已关闭: ${this.sessionId}`)

          // 分发关闭事件
          this.closeHandlers.forEach(handler => {
            try {
              handler()
            } catch (e) {
              console.error('关闭处理器异常:', e)
            }
          })

          // 自动重连（非手动关闭）
          if (!this.isManualClose && this.reconnectAttempts < this.maxReconnectAttempts) {
            this.scheduleReconnect()
          }
        }
      } catch (e) {
        reject(e)
      }
    })
  }

  /**
   * 发送消息
   */
  send(message: WebSocketMessage): void {
    if (this.isConnected()) {
      this.ws!.send(JSON.stringify(message))
      console.log(`📤 发送 WebSocket 消息: ${message.type}`)
    } else {
      console.warn('⚠️ WebSocket 未连接，消息已加入队列')
      this.messageQueue.push(message)
    }
  }

  /**
   * 关闭连接
   */
  close(): void {
    this.isManualClose = true
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }

  /**
   * 添加消息处理器
   */
  onMessage(handler: MessageHandler): void {
    this.messageHandlers.add(handler)
  }

  /**
   * 移除消息处理器
   */
  offMessage(handler: MessageHandler): void {
    this.messageHandlers.delete(handler)
  }

  /**
   * 添加错误处理器
   */
  onError(handler: ErrorHandler): void {
    this.errorHandlers.add(handler)
  }

  /**
   * 添加关闭处理器
   */
  onClose(handler: CloseHandler): void {
    this.closeHandlers.add(handler)
  }

  /**
   * 检查连接状态
   */
  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }

  /**
   * 发送队列中的消息
   */
  private flushMessageQueue(): void {
    if (this.messageQueue.length > 0) {
      console.log(`📬 发送队列中的 ${this.messageQueue.length} 条消息`)
      this.messageQueue.forEach(msg => this.send(msg))
      this.messageQueue = []
    }
  }

  /**
   * 计划重连
   */
  private scheduleReconnect(): void {
    this.reconnectAttempts++
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1)

    console.log(`⏱️ 计划重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})，延迟 ${delay}ms`)

    setTimeout(() => {
      if (!this.isManualClose) {
        console.log(`🔄 尝试重连: ${this.sessionId}`)
        this.connect().catch(e => {
          console.error('重连失败:', e)
        })
      }
    }, delay)
  }
}

/**
 * WebSocket 客户端管理器
 *
 * 管理多个会话的 WebSocket 连接
 */
class WebSocketClient {
  private connections = new Map<string, WebSocketConnection>()
  private baseUrl: string

  constructor() {
    // 从当前页面 URL 推断 WebSocket 地址
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.hostname
    const port = window.location.port

    this.baseUrl = `${protocol}//${host}${port ? ':' + port : ''}`
  }

  /**
   * 连接到指定会话
   */
  async connect(sessionId: string, onMessage: MessageHandler): Promise<void> {
    // 如果已存在连接，先关闭
    if (this.connections.has(sessionId)) {
      this.disconnect(sessionId)
    }

    // 创建新连接
    const connection = new WebSocketConnection(sessionId, this.baseUrl)
    connection.onMessage(onMessage)
    this.connections.set(sessionId, connection)

    // 建立连接
    await connection.connect()
  }

  /**
   * 发送查询消息
   */
  sendMessage(sessionId: string, message: string): void {
    const connection = this.connections.get(sessionId)
    if (!connection) {
      throw new Error(`会话 ${sessionId} 未连接`)
    }

    connection.send({
      type: 'query',
      data: { message }
    })
  }

  /**
   * 发送中断命令
   */
  interrupt(sessionId: string): void {
    const connection = this.connections.get(sessionId)
    if (!connection) {
      throw new Error(`会话 ${sessionId} 未连接`)
    }

    connection.send({
      type: 'interrupt'
    })
  }

  /**
   * 断开指定会话的连接
   */
  disconnect(sessionId: string): void {
    const connection = this.connections.get(sessionId)
    if (connection) {
      connection.close()
      this.connections.delete(sessionId)
      console.log(`🔌 断开 WebSocket 连接: ${sessionId}`)
    }
  }

  /**
   * 断开所有连接
   */
  disconnectAll(): void {
    this.connections.forEach((connection, sessionId) => {
      connection.close()
      console.log(`🔌 断开 WebSocket 连接: ${sessionId}`)
    })
    this.connections.clear()
  }

  /**
   * 检查连接状态
   */
  isConnected(sessionId: string): boolean {
    const connection = this.connections.get(sessionId)
    return connection ? connection.isConnected() : false
  }

  /**
   * 获取活跃连接数
   */
  getActiveConnectionCount(): number {
    return this.connections.size
  }
}

// 导出单例
export const websocketClient = new WebSocketClient()

// 导出类型
export type {
  WebSocketResponse,
  UserMessageResponse,
  AssistantMessageResponse,
  SystemMessageResponse,
  ResultMessageResponse,
  StreamEventResponse,
  ErrorMessageResponse,
  MessageHandler
}
