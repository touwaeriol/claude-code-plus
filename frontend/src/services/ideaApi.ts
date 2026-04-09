/**
 * IDE integration API (IDEA compatibility layer).
 *
 * Transport:
 * - RSocket: IDE ops + bidirectional notifications (/ide-rsocket)
 */

import { ideaRSocket } from './ideaRSocket'
import { setLocale, normalizeLocale } from '@/i18n'

// ========== 类型定义 ==========

export interface IdeaCapabilities {
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

/** 完整文件 Diff 请求（修改前后对比） */
export interface ShowEditFullDiffRequest {
  filePath: string
  oldString: string
  newString: string
  replaceAll?: boolean
  title?: string
  originalContent?: string  // 缓存的原始文件内容（如果有）
}

// ========== 环境检测 ==========

/**
 * 检测是否在 IDE 环境中运行
 * IDE 环境特征：window.__IDE_MODE__ = true（由 IDEA 宿主注入）
 */
export function isIdeEnvironment(): boolean {
  if (typeof window === 'undefined') return false
  return (window as any).__IDE_MODE__ === true
}

// ========== IDE 操作 API ==========

/**
 * IDE 桥接服务类
 * 使用 RSocket + Protobuf 与后端 IDE 集成通信
 */
class IdeaBridgeService {
  private enabled: boolean = false

  /**
   * 初始化桥接服务
   * 1. 检测是否在 IDE 宿主环境
   * 2. 建立 RSocket 连接
   * 3. 同步 IDE 语言设置到前端
   */
  async init(): Promise<boolean> {
    if (!isIdeEnvironment()) return false

    // 建立 RSocket 连接
    this.enabled = await ideaRSocket.connect()
    if (this.enabled) {
      console.log('[IdeaBridge] Initialized with RSocket')
      
      // 同步 IDEA 语言设置到前端
      await this.syncLocaleFromIde()
    }
    return this.enabled
  }
  
  /**
   * 从 IDEA 同步语言设置
   */
  private async syncLocaleFromIde(): Promise<void> {
    try {
      const ideLocale = await ideaRSocket.getLocale()
      if (ideLocale) {
        const normalizedLocale = normalizeLocale(ideLocale)
        setLocale(normalizedLocale)
        console.log(`[IdeaBridge] Synced locale from IDE: ${ideLocale} -> ${normalizedLocale}`)
      }
    } catch (error) {
      console.warn('[IdeaBridge] Failed to sync locale from IDE:', error)
    }
  }

  /**
   * 检查是否已启用
   */
  isEnabled(): boolean {
    return this.enabled && ideaRSocket.isConnected()
  }

  /**
   * 打开文件（RSocket）
   */
  async openFile(request: OpenFileRequest): Promise<boolean> {
    if (!this.enabled) return false
    return ideaRSocket.openFile(request)
  }

  /**
   * 显示 Diff（RSocket）
   */
  async showDiff(request: ShowDiffRequest): Promise<boolean> {
    if (!this.enabled) return false
    return ideaRSocket.showDiff(request)
  }

  /**
   * 显示多编辑 Diff（RSocket）
   */
  async showMultiEditDiff(request: ShowMultiEditDiffRequest): Promise<boolean> {
    if (!this.enabled) return false
    return ideaRSocket.showMultiEditDiff(request)
  }

  /**
   * 显示编辑预览 Diff（权限请求时使用）
   */
  async showEditPreviewDiff(request: ShowEditPreviewRequest): Promise<boolean> {
    if (!this.enabled) return false
    return ideaRSocket.showEditPreviewDiff(request)
  }

  /**
   * 显示 Markdown 内容（计划预览）
   */
  async showMarkdown(request: ShowMarkdownRequest): Promise<boolean> {
    if (!this.enabled) return false
    return ideaRSocket.showMarkdown(request)
  }

  /**
   * 显示完整文件 Diff（修改前后对比）
   */
  async showEditFullDiff(request: ShowEditFullDiffRequest): Promise<boolean> {
    if (!this.enabled) return false
    return ideaRSocket.showEditFullDiff(request)
  }

  /**
   * 获取缓存的原始文件内容（通过 RSocket，使用 LocalHistory Label）
   * @param toolUseId 工具调用 ID
   * @returns 原始内容，如果不存在则返回 null
   */
  async getOriginalContent(toolUseId: string): Promise<string | null> {
    if (!this.enabled) return null
    return ideaRSocket.getOriginalContent(toolUseId)
  }

  /**
   * 获取项目路径（RSocket）
   */
  async getProjectPath(): Promise<string | null> {
    if (!this.enabled) return null
    return ideaRSocket.getProjectPath()
  }

  /**
   * 获取 IDE 主题（RSocket）
   */
  async getTheme(): Promise<any | null> {
    if (!this.enabled) return null
    return ideaRSocket.getTheme()
  }

  /**
   * 获取语言设置（RSocket）
   */
  async getLocale(): Promise<string> {
    if (!this.enabled) return 'en-US'
    return ideaRSocket.getLocale()
  }

  /**
   * 设置语言（RSocket）
   */
  async setLocale(locale: string): Promise<boolean> {
    if (!this.enabled) return false
    return ideaRSocket.setLocale(locale)
  }

  /**
   * 添加主题变化监听器
   */
  onThemeChange(handler: (theme: any) => void): () => void {
    return ideaRSocket.onThemeChange(handler)
  }

  /**
   * 添加会话命令监听器
   */
  onSessionCommand(handler: (command: any) => void): () => void {
    return ideaRSocket.onSessionCommand(handler)
  }
}

// ========== 单例导出 ==========

export const ideaBridgeService = new IdeaBridgeService()

/**
 * 初始化 IDE 集成
 * 应该在应用启动时调用
 */
export async function initIdeaIntegration(): Promise<boolean> {
  return ideaBridgeService.init()
}
