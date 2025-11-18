/**
 * Claude WebSocket RPC 客户端工具
 * 
 * 功能:
 * - WebSocket 连接管理
 * - RPC 请求/响应处理
 * - 流式消息处理
 * - 自动重连
 * - 错误处理
 */

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
  data: any
}

export interface RpcStreamComplete {
  id: string
  type: 'complete'
}

export type RpcMessage = RpcResponse | RpcStreamData | RpcStreamComplete

export interface ClaudeRpcClientOptions {
  /** WebSocket URL (默认自动检测) */
  url?: string
  /** 自动重连 (默认 true) */
  autoReconnect?: boolean
  /** 重连间隔 (毫秒, 默认 3000) */
  reconnectInterval?: number
  /** 最大重连次数 (默认 5) */
  maxReconnectAttempts?: number
  /** 连接超时 (毫秒, 默认 10000) */
  connectionTimeout?: number
}

export class ClaudeRpcClient {
  private ws: WebSocket | null = null
  private url: string
  private options: Required<ClaudeRpcClientOptions>
  private requestId = 0
  private pendingRequests = new Map<string, {
    resolve: (value: any) => void
    reject: (error: Error) => void
    onStream?: (data: any) => void
  }>()
  private reconnectAttempts = 0
  private reconnectTimer: number | null = null
  private isManualClose = false
  private connectionPromise: Promise<void> | null = null

  constructor(options: ClaudeRpcClientOptions = {}) {
    this.options = {
      url: options.url || this.detectWebSocketUrl(),
      autoReconnect: options.autoReconnect ?? true,
      reconnectInterval: options.reconnectInterval ?? 3000,
      maxReconnectAttempts: options.maxReconnectAttempts ?? 5,
      connectionTimeout: options.connectionTimeout ?? 10000
    }
    this.url = this.options.url
  }

  /**
   * 自动检测 WebSocket URL
   */
  private detectWebSocketUrl(): string {
    if (typeof window !== 'undefined') {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = window.location.host
      return `${protocol}//${host}/ws`
    }
    return 'ws://localhost:8080/ws'
  }

  /**
   * 连接到 WebSocket 服务器
   */
  async connect(): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) {
      return Promise.resolve()
    }

    if (this.connectionPromise) {
      return this.connectionPromise
    }

    this.connectionPromise = new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('WebSocket 连接超时'))
        this.ws?.close()
      }, this.options.connectionTimeout)

      this.ws = new WebSocket(this.url)

      this.ws.onopen = () => {
        clearTimeout(timeout)
        this.reconnectAttempts = 0
        this.connectionPromise = null
        console.log('[ClaudeRpcClient] WebSocket 已连接:', this.url)
        resolve()
      }

      this.ws.onmessage = (event) => {
        this.handleMessage(event.data)
      }

      this.ws.onerror = (error) => {
        clearTimeout(timeout)
        console.error('[ClaudeRpcClient] WebSocket 错误:', error)
      }

      this.ws.onclose = () => {
        clearTimeout(timeout)
        this.connectionPromise = null
        console.log('[ClaudeRpcClient] WebSocket 已断开')
        
        if (!this.isManualClose && this.options.autoReconnect) {
          this.scheduleReconnect()
        }
      }
    })

    return this.connectionPromise
  }

  /**
   * 处理接收到的消息
   */
  private handleMessage(data: string): void {
    try {
      const message: RpcMessage = JSON.parse(data)
      const pending = this.pendingRequests.get(message.id)

      if (!pending) {
        console.warn('[ClaudeRpcClient] 收到未知请求的响应:', message.id)
        return
      }

      if ('error' in message && message.error) {
        pending.reject(new Error(message.error))
        this.pendingRequests.delete(message.id)
      } else if ('type' in message) {
        if (message.type === 'stream' && pending.onStream) {
          pending.onStream(message.data)
        } else if (message.type === 'complete') {
          pending.resolve(undefined)
          this.pendingRequests.delete(message.id)
        }
      } else if ('result' in message) {
        pending.resolve(message.result)
        this.pendingRequests.delete(message.id)
      }
    } catch (error) {
      console.error('[ClaudeRpcClient] 解析消息失败:', error, data)
    }
  }

  /**
   * 调度重连
   */
  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) {
      console.error('[ClaudeRpcClient] 达到最大重连次数,停止重连')
      return
    }

    this.reconnectAttempts++
    console.log(`[ClaudeRpcClient] ${this.options.reconnectInterval}ms 后尝试第 ${this.reconnectAttempts} 次重连...`)

    this.reconnectTimer = window.setTimeout(() => {
      this.connect().catch(error => {
        console.error('[ClaudeRpcClient] 重连失败:', error)
      })
    }, this.options.reconnectInterval)
  }

  /**
   * 发送 RPC 请求
   */
  async request<T = any>(method: string, params?: any, onStream?: (data: any) => void): Promise<T> {
    await this.connect()

    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket 未连接')
    }

    const id = `req-${++this.requestId}`
    const request: RpcRequest = { id, method, params }

    return new Promise((resolve, reject) => {
      this.pendingRequests.set(id, { resolve, reject, onStream })

      try {
        this.ws!.send(JSON.stringify(request))
        console.log('[ClaudeRpcClient] 发送请求:', method, params)
      } catch (error) {
        this.pendingRequests.delete(id)
        reject(error)
      }
    })
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    this.isManualClose = true

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }

    if (this.ws) {
      this.ws.close()
      this.ws = null
    }

    this.pendingRequests.clear()
    this.connectionPromise = null
    console.log('[ClaudeRpcClient] 已断开连接')
  }

  /**
   * 检查连接状态
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN
  }

  /**
   * 获取连接状态
   */
  getReadyState(): number {
    return this.ws?.readyState ?? WebSocket.CLOSED
  }
}

