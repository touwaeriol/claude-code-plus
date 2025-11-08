/**
 * SSE 事件服务
 * 负责接收后端推送的实时事件
 */

type EventHandler = (data: any) => void

export class EventService {
  private eventSource: EventSource | null = null
  private listeners = new Map<string, Set<EventHandler>>()
  private baseUrl: string = ''
  private reconnectAttempts = 0
  private readonly maxReconnectAttempts = 5
  private reconnectTimer: number | null = null

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl
  }

  /**
   * 连接到 SSE 事件流
   */
  connect() {
    if (this.eventSource) {
      console.warn('⚠️ EventSource already connected')
      return
    }

    try {
      console.log(`🔌 Connecting to SSE: ${this.baseUrl}/events`)
      this.eventSource = new EventSource(`${this.baseUrl}/events`)

      this.eventSource.onopen = () => {
        console.log('✅ SSE connected')
        this.reconnectAttempts = 0
        this.clearReconnectTimer()
      }

      this.eventSource.onerror = (error) => {
        console.error('❌ SSE error:', error)
        this.disconnect()
        this.scheduleReconnect()
      }

      // 监听所有事件类型
      this.setupEventListeners()
    } catch (error) {
      console.error('❌ Failed to create EventSource:', error)
      this.scheduleReconnect()
    }
  }

  /**
   * 设置事件监听器
   */
  private setupEventListeners() {
    if (!this.eventSource) return

    // 主题变化事件
    this.eventSource.addEventListener('theme', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.dispatchEvent('theme', data)
      } catch (error) {
        console.error('❌ Failed to parse theme event:', error)
      }
    })

    // Claude 消息事件
    this.eventSource.addEventListener('claude.message', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.dispatchEvent('claude.message', data)
      } catch (error) {
        console.error('❌ Failed to parse claude.message event:', error)
      }
    })

    // Claude 连接事件
    this.eventSource.addEventListener('claude.connected', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.dispatchEvent('claude.connected', data)
      } catch (error) {
        console.error('❌ Failed to parse claude.connected event:', error)
      }
    })

    // Claude 断开事件
    this.eventSource.addEventListener('claude.disconnected', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.dispatchEvent('claude.disconnected', data)
      } catch (error) {
        console.error('❌ Failed to parse claude.disconnected event:', error)
      }
    })

    // Claude 错误事件
    this.eventSource.addEventListener('claude.error', (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.dispatchEvent('claude.error', data)
      } catch (error) {
        console.error('❌ Failed to parse claude.error event:', error)
      }
    })
  }

  /**
   * 断开连接
   */
  disconnect() {
    if (this.eventSource) {
      this.eventSource.close()
      this.eventSource = null
      console.log('🔌 SSE disconnected')
    }
  }

  /**
   * 调度重连
   */
  private scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`❌ SSE reconnect failed after ${this.maxReconnectAttempts} attempts`)
      return
    }

    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000)
    this.reconnectAttempts++

    console.log(`🔄 Reconnecting SSE in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`)

    this.reconnectTimer = window.setTimeout(() => {
      this.connect()
    }, delay)
  }

  /**
   * 清除重连定时器
   */
  private clearReconnectTimer() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  /**
   * 分发事件给监听器
   */
  private dispatchEvent(type: string, data: any) {
    const handlers = this.listeners.get(type)
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(data)
        } catch (error) {
          console.error(`Error in event handler for ${type}:`, error)
        }
      })
    }
  }

  /**
   * 监听事件
   */
  on(eventType: string, handler: EventHandler): void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set())
    }
    this.listeners.get(eventType)!.add(handler)
  }

  /**
   * 取消监听
   */
  off(eventType: string, handler: EventHandler): void {
    this.listeners.get(eventType)?.delete(handler)
  }

  /**
   * 监听一次
   */
  once(eventType: string, handler: EventHandler): void {
    const wrappedHandler = (data: any) => {
      handler(data)
      this.off(eventType, wrappedHandler)
    }
    this.on(eventType, wrappedHandler)
  }
}
