/**
 * JetBrains IDE 集成 API
 *
 * 负责：
 * 1. 检测是否在 IDE 环境中运行
 * 2. 检测后端是否支持 JetBrains 集成
 * 3. 提供 IDE 操作 API（打开文件、显示 Diff 等）
 *
 * 通信方式：
 * - HTTP: 能力检测（/api/jetbrains/capabilities）
 * - RSocket: 所有 IDE 操作和双向通信（/jetbrains-rsocket）
 */

import { resolveServerHttpUrl } from '@/utils/serverUrl'
import { jetbrainsRSocket } from './jetbrainsRSocket'

// ========== 类型定义 ==========

export interface JetBrainsCapabilities {
  supported: boolean
  version: string
}

export interface OpenFileRequest {
  filePath: string
  line?: number
  column?: number
  startOffset?: number
  endOffset?: number
}

export interface ShowDiffRequest {
  filePath: string
  oldContent: string
  newContent: string
  title?: string
}

export interface EditOperation {
  oldString: string
  newString: string
  replaceAll?: boolean
}

export interface ShowMultiEditDiffRequest {
  filePath: string
  edits: EditOperation[]
  currentContent?: string
}

/** 编辑预览请求（权限请求时使用） */
export interface ShowEditPreviewRequest {
  filePath: string
  edits: EditOperation[]
  title?: string
}

/** Markdown 显示请求（计划预览） */
export interface ShowMarkdownRequest {
  content: string
  title?: string
}

// ========== 环境检测 ==========

/**
 * 检测是否在 IDE 环境中运行
 * IDE 环境特征：window.__IDEA_MODE__ = true（由后端注入）
 */
export function isIdeEnvironment(): boolean {
  if (typeof window === 'undefined') return false
  return (window as any).__IDEA_MODE__ === true
}

/**
 * 检测后端是否支持 JetBrains 集成
 */
export async function checkJetBrainsCapabilities(): Promise<JetBrainsCapabilities> {
  try {
    const baseUrl = resolveServerHttpUrl()
    const response = await fetch(`${baseUrl}/api/jetbrains/capabilities`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json'
      }
    })

    if (!response.ok) {
      console.warn('[JetBrainsApi] Capabilities check failed:', response.status)
      return { supported: false, version: '' }
    }

    const data = await response.json()
    console.log('[JetBrainsApi] Capabilities:', data)
    return {
      supported: data.supported ?? false,
      version: data.version ?? ''
    }
  } catch (error) {
    console.warn('[JetBrainsApi] Failed to check capabilities:', error)
    return { supported: false, version: '' }
  }
}

/**
 * 检测是否应该启用 JetBrains 集成
 * 条件：IDE 环境 + 后端支持
 */
export async function shouldEnableJetBrainsIntegration(): Promise<boolean> {
  if (!isIdeEnvironment()) {
    console.log('[JetBrainsApi] Not in IDE environment')
    return false
  }

  const capabilities = await checkJetBrainsCapabilities()
  if (!capabilities.supported) {
    console.log('[JetBrainsApi] Backend does not support JetBrains integration')
    return false
  }

  console.log('[JetBrainsApi] JetBrains integration enabled')
  return true
}

// ========== IDE 操作 API ==========

/**
 * JetBrains 桥接服务类
 * 使用 RSocket + Protobuf 与后端 IDE 集成通信
 */
class JetBrainsBridgeService {
  private enabled: boolean = false

  /**
   * 初始化桥接服务
   * 1. 检测环境和后端能力（HTTP）
   * 2. 建立 RSocket 连接
   */
  async init(): Promise<boolean> {
    // 先检测是否应该启用
    const shouldEnable = await shouldEnableJetBrainsIntegration()
    if (!shouldEnable) {
      return false
    }

    // 建立 RSocket 连接
    this.enabled = await jetbrainsRSocket.connect()
    if (this.enabled) {
      console.log('[JetBrainsBridge] Initialized with RSocket')
    }
    return this.enabled
  }

  /**
   * 检查是否已启用
   */
  isEnabled(): boolean {
    return this.enabled && jetbrainsRSocket.isConnected()
  }

  /**
   * 打开文件（RSocket）
   */
  async openFile(request: OpenFileRequest): Promise<boolean> {
    if (!this.enabled) return false
    return jetbrainsRSocket.openFile(request)
  }

  /**
   * 显示 Diff（RSocket）
   */
  async showDiff(request: ShowDiffRequest): Promise<boolean> {
    if (!this.enabled) return false
    return jetbrainsRSocket.showDiff(request)
  }

  /**
   * 显示多编辑 Diff（RSocket）
   */
  async showMultiEditDiff(request: ShowMultiEditDiffRequest): Promise<boolean> {
    if (!this.enabled) return false
    return jetbrainsRSocket.showMultiEditDiff(request)
  }

  /**
   * 显示编辑预览 Diff（权限请求时使用）
   */
  async showEditPreviewDiff(request: ShowEditPreviewRequest): Promise<boolean> {
    if (!this.enabled) return false
    return jetbrainsRSocket.showEditPreviewDiff(request)
  }

  /**
   * 显示 Markdown 内容（计划预览）
   */
  async showMarkdown(request: ShowMarkdownRequest): Promise<boolean> {
    if (!this.enabled) return false
    return jetbrainsRSocket.showMarkdown(request)
  }

  /**
   * 获取项目路径（RSocket）
   */
  async getProjectPath(): Promise<string | null> {
    if (!this.enabled) return null
    return jetbrainsRSocket.getProjectPath()
  }

  /**
   * 获取 IDE 主题（RSocket）
   */
  async getTheme(): Promise<any | null> {
    if (!this.enabled) return null
    return jetbrainsRSocket.getTheme()
  }

  /**
   * 获取语言设置（RSocket）
   */
  async getLocale(): Promise<string> {
    if (!this.enabled) return 'en-US'
    return jetbrainsRSocket.getLocale()
  }

  /**
   * 设置语言（RSocket）
   */
  async setLocale(locale: string): Promise<boolean> {
    if (!this.enabled) return false
    return jetbrainsRSocket.setLocale(locale)
  }

  /**
   * 添加主题变化监听器
   */
  onThemeChange(handler: (theme: any) => void): () => void {
    return jetbrainsRSocket.onThemeChange(handler)
  }

  /**
   * 添加会话命令监听器
   */
  onSessionCommand(handler: (command: any) => void): () => void {
    return jetbrainsRSocket.onSessionCommand(handler)
  }
}

// ========== 单例导出 ==========

export const jetbrainsBridge = new JetBrainsBridgeService()

/**
 * 初始化 JetBrains 集成
 * 应该在应用启动时调用
 */
export async function initJetBrainsIntegration(): Promise<boolean> {
  return jetbrainsBridge.init()
}
