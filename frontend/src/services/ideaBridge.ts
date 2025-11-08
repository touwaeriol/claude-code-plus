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
  private eventSource: EventSource | null = null

  constructor() {
    this.setupEventListener()
    // 延迟模式检测和初始化到 waitForReady()
  }

  /**
   * 检测运行模式
   */
  private detectMode() {
    // 检查多个标志来确定是否在 JCEF 环境中
    const hasIdeaBridge = window.ideaBridge && typeof window.ideaBridge.query === 'function'
    const hasJcefFlag = window.__jcefMode === true
    const hasBridgeReadyFlag = window.__bridgeReady === true
    
    console.log('🔍 Mode Detection:', { 
      hasIdeaBridge, 
      hasJcefFlag,
      hasBridgeReadyFlag,
      ideaBridge: window.ideaBridge 
    })
    
    if (hasIdeaBridge || hasJcefFlag || hasBridgeReadyFlag) {
      this.mode = BridgeMode.JCEF
      console.log('🔌 Bridge Mode: JCEF (Plugin)', { hasIdeaBridge, hasJcefFlag, hasBridgeReadyFlag })
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
      const checkBridgeReady = () => {
        // 多重检查：__bridgeReady 标志或 ideaBridge.isReady
        if (window.__bridgeReady || (window.ideaBridge && window.ideaBridge.isReady)) {
          console.log('✅ JCEF Bridge is ready')
          this.isReady = true
          resolve()
          return true
        }
        return false
      }

      // 立即检查
      if (checkBridgeReady()) return

      // 监听 bridge-ready 事件
      window.addEventListener('bridge-ready', () => {
        console.log('📢 bridge-ready event received')
        if (checkBridgeReady()) return
      }, { once: true })

      // 轮询检查（每 100ms 检查一次，最多检查 100 次 = 10 秒）
      let attempts = 0
      const maxAttempts = 100
      const pollInterval = setInterval(() => {
        attempts++
        console.log(`🔄 Polling for JCEF Bridge... (attempt ${attempts}/${maxAttempts})`)
        
        if (checkBridgeReady()) {
          clearInterval(pollInterval)
          return
        }
        
        if (attempts >= maxAttempts) {
          clearInterval(pollInterval)
          console.error('❌ JCEF Bridge not ready after 10s, falling back to HTTP mode')
          this.mode = BridgeMode.HTTP
          this.initHttpMode().then(resolve)
        }
      }, 100)
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

        // 连接 SSE（替换 WebSocket）
        this.connectSSE()
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
   * 连接 SSE（HTTP 模式）
   */
  private connectSSE() {
    if (this.mode !== BridgeMode.HTTP) return
    if (this.eventSource) return

    try {
      console.log(`🔌 Connecting to SSE: ${this.httpBaseUrl}/api/events`)
      this.eventSource = new EventSource(`${this.httpBaseUrl}/api/events`)

      this.eventSource.onopen = () => {
        console.log('✅ SSE connected')
      }

      // 监听主题事件
      this.eventSource.addEventListener('theme', (event) => {
        console.log('🎨 Theme event received via SSE')
        try {
          const theme = JSON.parse(event.data)
          this.emit('theme.changed', { theme })
        } catch (error) {
          console.error('❌ Failed to parse theme event:', error)
        }
      })

      // 监听其他 IDE 事件
      this.eventSource.addEventListener('ide-event', (event) => {
        try {
          const data = JSON.parse(event.data)
          this.emit(data.type, data.data)
        } catch (error) {
          console.error('❌ Failed to parse IDE event:', error)
        }
      })

      this.eventSource.onerror = (error) => {
        console.error('❌ SSE error:', error)
        this.eventSource?.close()
        this.eventSource = null

        // 5 秒后尝试重连
        setTimeout(() => {
          console.log('🔄 Reconnecting SSE...')
          this.connectSSE()
        }, 5000)
      }
    } catch (error) {
      console.error('❌ Failed to connect SSE:', error)
    }
  }


  /**
   * 等待桥接就绪
   */
  async waitForReady(): Promise<void> {
    if (this.isReady) return

    // 等待一小段时间确保早期标志已设置
    await new Promise(resolve => setTimeout(resolve, 50))

    // 检测模式
    this.detectMode()

    // 初始化
    await this.init()

    // 等待就绪
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
