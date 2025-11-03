/**
 * IDEA 通信桥接服务
 * 负责前端与 Kotlin 后端的双向通信
 */

import type { FrontendResponse, IdeEvent } from '@/types/bridge'

type EventHandler = (data: any) => void

class IdeaBridgeService {
  private listeners = new Map<string, Set<EventHandler>>()
  private isReady = false

  constructor() {
    this.setupEventListener()
    this.checkBridgeReady()
  }

  /**
   * 等待桥接就绪
   */
  async waitForReady(): Promise<void> {
    if (this.isReady) return

    return new Promise((resolve) => {
      if (window.__bridgeReady) {
        this.isReady = true
        resolve()
      } else {
        window.addEventListener('bridge-ready', () => {
          this.isReady = true
          resolve()
        }, { once: true })
      }
    })
  }

  /**
   * 检查桥接是否已就绪
   */
  private checkBridgeReady() {
    if (window.__bridgeReady) {
      this.isReady = true
    }
  }

  /**
   * 设置事件监听器
   */
  private setupEventListener() {
    window.addEventListener('ide-event', ((event: CustomEvent<IdeEvent>) => {
      const { type, data } = event.detail
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
    }) as EventListener)
  }

  /**
   * 调用后端 API
   */
  async query(action: string, data?: any): Promise<FrontendResponse> {
    await this.waitForReady()

    try {
      const response = await window.ideaBridge.query(action, data)
      return response
    } catch (error) {
      console.error(`Bridge query failed for ${action}:`, error)
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      }
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
