/**
 * IDEA 通信桥接服务（纯 HTTP 模式）
 * 负责前端与后端的 HTTP 通信
 */

import type { FrontendResponse, IdeEvent } from '@/types/bridge'
import { withServerToken } from '@/utils/serverAuth'

type EventHandler = (data: any) => void

/**
 * IDE 集成选项接口
 */
export interface OpenFileOptions {
  line?: number
  endLine?: number
  column?: number
  selectContent?: boolean
  content?: string
  selectionStart?: number
  selectionEnd?: number
}

export interface ShowDiffOptions {
  filePath: string
  oldContent: string
  newContent: string
  title?: string
  rebuildFromFile?: boolean
  edits?: Array<{
    oldString: string
    newString: string
    replaceAll: boolean
  }>
}

class IdeaBridgeService {
  private listeners = new Map<string, Set<EventHandler>>()
  private isReady = false
  private mode: 'ide' | 'browser' = 'browser'

  // 获取基础 URL：
  // - IDE 插件模式：使用后端注入的 window.__serverUrl（随机端口）
  // - 浏览器开发模式：固定指向 http://localhost:8765（StandaloneServer 默认端口）
  private getBaseUrl(): string {
    if (typeof window === 'undefined') {
      // 构建时 / SSR 场景：返回空字符串，避免报错
      return ''
    }

    const anyWindow = window as any

    // IDEA 插件模式：后端注入 __serverUrl
    if (anyWindow.__serverUrl) {
      return anyWindow.__serverUrl as string
    }

    // IDEA 模式但 __serverUrl 尚未注入：使用当前 origin（页面就是从后端加载的）
    if (anyWindow.__IDEA_MODE__) {
      return window.location.origin
    }

    // 浏览器开发模式：前端跑在 Vite (通常 5173)，后端独立跑在 8765
    // 这里直接固定到 localhost:8765，方便本地开发
    if (import.meta.env.DEV) {
      return 'http://localhost:8765'
    }

    // 兜底：使用当前 origin（用于将来可能的同源部署）
    return window.location.origin
  }

  private detectMode(): 'ide' | 'browser' {
    if (typeof window === 'undefined') {
      return 'browser' // 构建时默认值
    }
    // 检测 IDEA 插件环境：__IDEA_MODE__ - 由后端 HTML 注入的标记
    const anyWindow = window as any
    return anyWindow.__IDEA_MODE__ ? 'ide' : 'browser'
  }

  private refreshMode(): 'ide' | 'browser' {
    this.mode = this.detectMode()
    return this.mode
  }

  constructor() {
    // 正常初始化，方法内部会做安全检查
    this.setupEventListener()
    this.init()
  }

  /**
   * 初始化桥接服务
   */
  private async init() {
    // 只在浏览器环境初始化
    if (typeof window === 'undefined') {
      return // 构建时跳过初始化
    }
    this.refreshMode()
    // 简单标记为就绪
    this.isReady = true
    console.log('🌐 Bridge Mode: HTTP')
    console.log('🔗 Server URL:', this.getBaseUrl())
  }

  /**
   * 设置事件监听器
   */
  private setupEventListener() {
    // 只在浏览器环境设置监听器
    if (typeof window === 'undefined') {
      return // 构建时跳过
    }
    const handler = (event: Event) => {
      const customEvent = event as CustomEvent<IdeEvent>
      const { type, data } = customEvent.detail
      this.dispatchEvent({ type, data })
    }
    window.addEventListener('ide-event', handler)
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
      }, 5000)
    })
  }

  /**
   * 调用后端 API（HTTP 模式）
   */
  async query(action: string, data?: any): Promise<FrontendResponse> {
    await this.waitForReady()

    try {
      const response = await fetch(`${this.getBaseUrl()}/api/`, {
        method: 'POST',
        headers: withServerToken({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ action, data })
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      return await response.json()
    } catch (error) {
      console.error(`HTTP query failed for ${action}:`, error)
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Unknown error'
      }
    }
  }

  /**
   * 获取服务器 URL
   */
  getServerUrl(): string {
    return this.getBaseUrl()
  }

  /**
   * 获取运行模式
   */
  getMode(): 'ide' | 'browser' {
    return this.refreshMode()
  }

  /**
   * 是否运行在 IDE 模式
   */
  isInIde(): boolean {
    return this.refreshMode() === 'ide'
  }

  /**
   * 是否运行在浏览器模式
   */
  isInBrowser(): boolean {
    return this.refreshMode() === 'browser'
  }

  /**
   * 当前桥接是否就绪
   */
  checkReady(): boolean {
    return this.isReady
  }

  /**
   * 获取服务器端口
   */
  getServerPort(): string {
    try {
      const url = new URL(this.getBaseUrl())
      return url.port || '80'
    } catch {
      return '8765'
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
}

// 延迟初始化单例
let _ideaBridge: IdeaBridgeService | null = null

function getIdeaBridge(): IdeaBridgeService {
  // 懒加载初始化：只在第一次使用时创建实例
  if (!_ideaBridge) {
    _ideaBridge = new IdeaBridgeService()
  }
  return _ideaBridge
}

// 导出单例访问器对象
export const ideaBridge = {
  query: (action: string, data?: any) => getIdeaBridge().query(action, data),
  getServerUrl: () => getIdeaBridge().getServerUrl(),
  getMode: () => getIdeaBridge().getMode(),
  isInIde: () => getIdeaBridge().isInIde(),
  isInBrowser: () => getIdeaBridge().isInBrowser(),
  checkReady: () => getIdeaBridge().checkReady(),
  getServerPort: () => getIdeaBridge().getServerPort(),
  on: (eventType: string, handler: EventHandler) => getIdeaBridge().on(eventType, handler),
  off: (eventType: string, handler: EventHandler) => getIdeaBridge().off(eventType, handler),
  waitForReady: () => getIdeaBridge().waitForReady()
}

export async function openFile(filePath: string, options?: OpenFileOptions) {
  return getIdeaBridge().query('ide.openFile', { filePath, ...options })
}

export async function showDiff(options: ShowDiffOptions) {
  return getIdeaBridge().query('ide.showDiff', options)
}

export async function searchFiles(query: string, maxResults?: number) {
  return getIdeaBridge().query('ide.searchFiles', { query, maxResults: maxResults || 20 })
}

export async function getFileContent(filePath: string, lineStart?: number, lineEnd?: number) {
  return getIdeaBridge().query('ide.getFileContent', { filePath, lineStart, lineEnd })
}

export async function getLocale() {
  return getIdeaBridge().query('ide.getLocale')
}

export async function setLocale(locale: string) {
  return getIdeaBridge().query('ide.setLocale', locale)
}

export async function detectNode() {
  return getIdeaBridge().query('node.detect')
}

export async function openUrl(url: string) {
  // IDE 模式：通过后端 BrowserUtil 打开系统浏览器
  // 浏览器模式：回退到 window.open
  if (getIdeaBridge().isInIde()) {
    return getIdeaBridge().query('ide.openUrl', { url })
  } else {
    window.open(url, '_blank')
    return { success: true }
  }
}

// 注：getFileHistoryContent 已迁移到 RSocket 实现
// 参见 jetbrainsRSocket.ts 的 getFileHistoryContent 方法

export const aiAgentBridgeService = {
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
