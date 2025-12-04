/**
 * IDEA JCEF 桥接服务
 * 统一的 IDEA JCEF 桥接类型定义和检测
 */

import type { IdeTheme } from '@/types/bridge'

// ====== Payload 类型定义 ======

export interface OpenFilePayload {
  filePath: string
  startLine?: number
  endLine?: number
  startOffset?: number
  endOffset?: number
}

export interface ShowDiffPayload {
  filePath: string
  oldContent: string
  newContent: string
  title?: string
}

export interface ShowMultiEditDiffPayload {
  filePath: string
  edits: Array<{
    oldString: string
    newString: string
    replaceAll?: boolean
  }>
  currentContent?: string
}

// ====== IDEA JCEF 桥接接口 ======

export interface IdeaJcefBridge {
  toolShow: {
    openFile(payload: OpenFilePayload): void
    showDiff(payload: ShowDiffPayload): void
    showMultiEditDiff(payload: ShowMultiEditDiffPayload): void
  }
  theme: {
    push(theme: IdeTheme): void
    getCurrent(): IdeTheme | null
    onChange: ((theme: IdeTheme) => void) | null
  }
  session: {
    postState(payload: string): void
  }
}

// ====== 全局类型扩展 ======

declare global {
  interface Window {
    __IDEA_JCEF__?: IdeaJcefBridge
    __themeBridge?: IdeaJcefBridge['theme']
    __IDEA_TOOLS__?: IdeaJcefBridge['toolShow']
  }
}

// ====== 检测和获取函数 ======

/**
 * 检测是否在 IDEA JCEF 环境中
 */
export function isIdeaJcefAvailable(): boolean {
  return typeof window !== 'undefined' && !!window.__IDEA_JCEF__
}

/**
 * 获取 IDEA JCEF 桥接对象
 */
export function getIdeaJcef(): IdeaJcefBridge | null {
  return window.__IDEA_JCEF__ ?? null
}

/**
 * 检测工具展示 API 是否可用
 */
export function isToolShowAvailable(): boolean {
  return isIdeaJcefAvailable() && !!window.__IDEA_JCEF__?.toolShow
}

/**
 * 获取工具展示 API
 */
export function getToolShowApi(): IdeaJcefBridge['toolShow'] | null {
  return window.__IDEA_JCEF__?.toolShow ?? null
}

/**
 * 检测主题 API 是否可用
 */
export function isThemeApiAvailable(): boolean {
  return isIdeaJcefAvailable() && !!window.__IDEA_JCEF__?.theme
}

/**
 * 获取主题 API
 */
export function getThemeApi(): IdeaJcefBridge['theme'] | null {
  return window.__IDEA_JCEF__?.theme ?? null
}
