/**
 * IDEA 通信桥接服务
 * 负责前端与 Kotlin 后端的双向通信
 *
 * 支持两种模式：
 * 1. JCEF Bridge 模式（插件内）- 使用 window.ideaBridge
 * 2. HTTP API 模式（浏览器）- 使用 fetch + WebSocket
 */

import type { FrontendResponse, IdeEvent } from '@/types/bridge'

type EventHandler = (data: any) => void

/**
 * 通信模式枚举
 */
enum BridgeMode {
  JCEF = 'jcef',      // 插件内 JCEF Bridge
  HTTP = 'http'       // 浏览器 HTTP API
}

class IdeaBridgeService {
  private listeners = new Map<string, Set<EventHandler>>()
  private isReady = false
  private mode: BridgeMode = BridgeMode.HTTP

  // HTTP 模式配置
  private readonly httpBaseUrl = 'http://localhost:8765'
  private readonly wsUrl = 'ws://localhost:8766'
  private ws: WebSocket | null = null
  private wsReconnectTimer: number | null = null
  private wsReconnectAttempts = 0
  private readonly maxReconnectAttempts = 5

  constructor() {
    this.detectMode()
    this.setupEventListener()
    this.init()
  }

  /**
   * 检测运行模式
   */
  private detectMode() {
    if (window.ideaBridge && typeof window.ideaBridge.query === 'function') {
      this.mode = BridgeMode.JCEF
      console.log('🔌 Bridge Mode: JCEF (Plugin)')
    } else {
      this.mode = BridgeMode.HTTP
      console.log('🌐 Bridge Mode: HTTP (Browser)')
    }
  }

  /**
   * 初始化桥接服务
   */
  private async init() {
    if (this.mode === BridgeMode.JCEF) {
      await this.initJcefMode()
    } else {
      await this.initHttpMode()
    }
  }

  /**
   * 初始化 JCEF 模式
   */
  private async initJcefMode() {
    return new Promise<void>((resolve) => {
      if (window.__bridgeReady) {
        this.isReady = true
        resolve()
      } else {
        window.addEventListener('bridge-ready', () => {
          this.isReady = true
          resolve()
        }, { once: true })

        // 超时检查
        setTimeout(() => {
          if (!this.isReady) {
            console.warn('⚠️ JCEF Bridge not ready after 5s, falling back to HTTP mode')
            this.mode = BridgeMode.HTTP
            this.initHttpMode().then(resolve)
          }
        }, 5000)
      }
    })
  }

  /**
   * 初始化 HTTP 模式
   */
  private async initHttpMode() {
    // 测试 HTTP API 连通性
    try {
      const response = await fetch(`${this.httpBaseUrl}/api/`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'test.ping', data: {} })
      })

      if (response.ok) {
        console.log('✅ HTTP API connected')
        this.isReady = true

        // 连接 WebSocket
        this.connectWebSocket()
      } else {
        throw new Error(`HTTP API returned ${response.status}`)
      }
    } catch (error) {
      console.error('❌ Failed to connect to HTTP API:', error)
      console.warn('⚠️ Make sure the plugin is running and HTTP server is started')
      // 即使失败也标记为就绪，让用户看到错误提示
      this.isReady = true
    }
  }

  /**
   * 连接 WebSocket（HTTP 模式）
   */
  private connectWebSocket() {
    if (this.mode !== BridgeMode.HTTP) return
    if (this.ws && this.ws.readyState === WebSocket.OPEN) return

    try {
      console.log(`🔌 Connecting to WebSocket: ${this.wsUrl}`)
      this.ws = new WebSocket(this.wsUrl)

      this.ws.onopen = () => {
        console.log('✅ WebSocket connected')
        this.wsReconnectAttempts = 0

        // 清除重连定时器
        if (this.wsReconnectTimer) {
          clearTimeout(this.wsReconnectTimer)
          this.wsReconnectTimer = null
        }
      }

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data)

          // 处理批量消息（数组）
          if (Array.isArray(data)) {
            // 使用 requestAnimationFrame 批量处理，避免阻塞渲染
            requestAnimationFrame(() => {
              data.forEach((ideEvent: IdeEvent) => {
                this.dispatchEvent(ideEvent)
              })
            })
          } else {
            // 单条消息
            this.dispatchEvent(data as IdeEvent)
          }
        } catch (error) {
          console.error('❌ Failed to parse WebSocket message:', error)
        }
      }

      this.ws.onerror = (error) => {
        console.error('❌ WebSocket error:', error)
      }

      this.ws.onclose = () => {
        console.warn('⚠️ WebSocket disconnected')
        this.ws = null

        // 自动重连
        this.scheduleReconnect()
      }
    } catch (error) {
      console.error('❌ Failed to create WebSocket:', error)
      this.scheduleReconnect()
    }
  }

  /**
   * 调度 WebSocket 重连
   */
  private scheduleReconnect() {
    if (this.wsReconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`❌ WebSocket reconnect failed after ${this.maxReconnectAttempts} attempts`)
      return
    }

    const delay = Math.min(1000 * Math.pow(2, this.wsReconnectAttempts), 30000)
    this.wsReconnectAttempts++

    console.log(`🔄 Reconnecting WebSocket in ${delay}ms (attempt ${this.wsReconnectAttempts}/${this.maxReconnectAttempts})`)

    this.wsReconnectTimer = window.setTimeout(() => {
      this.connectWebSocket()
    }, delay)
  }

  /**
   * 等待桥接就绪
   */
  async waitForReady(): Promise<void> {
    if (this.isReady) return

    return new Promise((resolve) => {
      const checkInterval = setInterval(() => {
        if (this.isReady) {
          clearInterval(checkInterval)
          resolve()
        }
      }, 100)

      // 超时保护
      setTimeout(() => {
        clearInterval(checkInterval)
        console.warn('⚠️ Bridge ready timeout')
        resolve()
      }, 10000)
    })
  }

  /**
   * 设置事件监听器（JCEF 模式）
   */
  private setupEventListener() {
    window.addEventListener('ide-event', ((event: CustomEvent<IdeEvent>) => {
      const { type, data } = event.detail
      this.dispatchEvent({ type, data })
    }) as EventListener)
  }

  /**
   * 分发事件给监听器
   */
  private dispatchEvent(event: IdeEvent) {
    const handlers = this.listeners.get(event.type)
    if (handlers) {
      handlers.forEach(handler => {
        try {
          handler(event.data)
        } catch (error) {
          console.error(`Error in event handler for ${event.type}:`, error)
        }
      })
    }
  }

  /**
   * 调用后端 API（自动选择模式）
   */
  async query(action: string, data?: any): Promise<FrontendResponse> {
    await this.waitForReady()

    if (this.mode === BridgeMode.JCEF) {
      return this.queryViaJcef(action, data)
    } else {
      return this.queryViaHttp(action, data)
    }
  }

  /**
   * 通过 JCEF Bridge 调用
   */
  private async queryViaJcef(action: string, data?: any): Promise<FrontendResponse> {
    try {
      const response = await window.ideaBridge.query(action, data)
      return response
    } catch (error) {
      console.error(`JCEF Bridge query failed for ${action}:`, error)
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      }
    }
  }

  /**
   * 通过 HTTP API 调用
   */
  private async queryViaHttp(action: string, data?: any): Promise<FrontendResponse> {
    try {
      const response = await fetch(`${this.httpBaseUrl}/api/`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action, data })
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`)
      }

      const result: FrontendResponse = await response.json()
      return result
    } catch (error) {
      console.error(`HTTP API query failed for ${action}:`, error)
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Network error'
      }
    }
  }

  /**
   * 获取当前模式
   */
  getMode(): string {
    return this.mode
  }

  /**
   * 检查是否已就绪
   */
  checkReady(): boolean {
    return this.isReady
  }

  /**
   * 获取服务器 URL
   */
  getServerUrl(): string {
    if (this.mode === BridgeMode.JCEF) {
      // JCEF 模式下，从 window.location 获取
      return `${window.location.protocol}//${window.location.host}`
    } else {
      // HTTP 模式下，使用配置的 URL
      return this.httpBaseUrl
    }
  }

  /**
   * 获取服务器端口
   */
  getServerPort(): string {
    if (this.mode === BridgeMode.JCEF) {
      return window.location.port || '80'
    } else {
      return new URL(this.httpBaseUrl).port || '8765'
    }
  }

  /**
   * 监听后端事件
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

// 导出单例
export const ideaBridge = new IdeaBridgeService()

// 便捷 API
export const ideService = {
  async getTheme() {
    return ideaBridge.query('ide.getTheme')
  },

  async openFile(filePath: string, line?: number, column?: number) {
    return ideaBridge.query('ide.openFile', { filePath, line, column })
  },

  async showDiff(filePath: string, oldContent: string, newContent: string) {
    return ideaBridge.query('ide.showDiff', { filePath, oldContent, newContent })
  },

  async searchFiles(query: string, maxResults?: number) {
    return ideaBridge.query('ide.searchFiles', { query, maxResults: maxResults || 20 })
  },

  async getFileContent(filePath: string, lineStart?: number, lineEnd?: number) {
    return ideaBridge.query('ide.getFileContent', { filePath, lineStart, lineEnd })
  },

  onThemeChange(handler: EventHandler) {
    ideaBridge.on('ide.themeChanged', handler)
  }
}

export const claudeService = {
  async connect(options?: any) {
    return ideaBridge.query('claude.connect', options)
  },

  async query(message: string) {
    return ideaBridge.query('claude.query', { message })
  },

  async interrupt() {
    return ideaBridge.query('claude.interrupt')
  },

  async disconnect() {
    return ideaBridge.query('claude.disconnect')
  },

  onMessage(handler: EventHandler) {
    ideaBridge.on('claude.message', handler)
  },

  onConnected(handler: EventHandler) {
    ideaBridge.on('claude.connected', handler)
  },

  onDisconnected(handler: EventHandler) {
    ideaBridge.on('claude.disconnected', handler)
  },

  onError(handler: (error: string) => void) {
    ideaBridge.on('claude.error', (data) => handler(data.error))
  }
}
