/**
 * IDEA 通信桥接服务（纯 HTTP 模式）
 * 负责前端与后端的 HTTP 通信
 */

import type { FrontendResponse, IdeEvent } from '@/types/bridge'

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

  // 从 window.__serverUrl 获取后端地址，或使用默认值
  private getBaseUrl(): string {
    return (window as any).__serverUrl || 'http://localhost:8765'
  }

  constructor() {
    this.setupEventListener()
    this.init()
  }

  /**
   * 初始化桥接服务
   */
  private async init() {
    // 简单标记为就绪
    this.isReady = true
    console.log('🌐 Bridge Mode: HTTP')
    console.log('🔗 Server URL:', this.getBaseUrl())
  }

  /**
   * 设置事件监听器
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
        headers: { 'Content-Type': 'application/json' },
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
  if (!_ideaBridge && typeof window !== 'undefined') {
    _ideaBridge = new IdeaBridgeService()
  }
  // 在构建时返回一个空对象，避免访问 window
  return _ideaBridge || ({} as IdeaBridgeService)
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

// ��� API - ʹ����������
export async function getTheme() {
  return getIdeaBridge().query('ide.getTheme')
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

export function onThemeChange(handler: EventHandler) {
  getIdeaBridge().on('ide.themeChanged', handler)
}

// Ϊ�������ݣ�Ҳ����������ʽ
export const ideService = {
  getTheme,
  openFile,
  showDiff,
  searchFiles,
  getFileContent,
  onThemeChange
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
