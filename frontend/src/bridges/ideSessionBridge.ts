import { watch, type WatchStopHandle } from 'vue'
import { ideaBridge } from '@/services/ideaBridge'
import localeService from '@/services/localeService'
import type { useSessionStore } from '@/stores/sessionStore'
import { ConnectionStatus } from '@/types/display'

export interface HostCommand {
  type: string
  payload?: Record<string, any> | null
}

interface SessionSummaryPayload {
  id: string
  title: string
  isGenerating: boolean
  isConnected: boolean  // 是否已连接（进行中会话）
}

interface SessionStatePayload {
  type: 'session:update'
  sessions: SessionSummaryPayload[]
  activeSessionId: string | null
}

type SessionStore = ReturnType<typeof useSessionStore>
export type HostCommandHandler = (command: HostCommand) => void

declare global {
  interface Window {
    __CLAUDE_IDE_HOST__?: {
      postSessionState?: (payload: string) => void
    }
    __CLAUDE_IDE_BRIDGE__?: {
      onHostCommand?: (command: HostCommand) => void
    }
  }
}

const hostCommandHandlers = new Set<HostCommandHandler>()
let activeConsumers = 0
let stopWatchHandle: WatchStopHandle | null = null
let defaultHandler: HostCommandHandler | null = null
let pendingState: SessionStatePayload | null = null
let flushTimer: number | null = null

function ensureGlobalBridge() {
  if (typeof window === 'undefined') return
  window.__CLAUDE_IDE_BRIDGE__ = window.__CLAUDE_IDE_BRIDGE__ || {}
  window.__CLAUDE_IDE_BRIDGE__!.onHostCommand = (command: HostCommand) => {
    notifyHostCommand(command)
  }
}

function notifyHostCommand(command: HostCommand) {
  hostCommandHandlers.forEach(handler => {
    try {
      handler(command)
    } catch (error) {
      console.error('[IDE Bridge] Host command handler failed:', error)
    }
  })
}

function postSessionState(payload: SessionStatePayload): boolean {
  if (typeof window === 'undefined') return false
  // 使用统一的 __IDEA_JCEF__ 桥接
  const jcef = window.__IDEA_JCEF__
  if (jcef?.session?.postState) {
    try {
      jcef.session.postState(JSON.stringify(payload))
      return true
    } catch (error) {
      console.warn('[IDE Bridge] Failed to post session state:', error)
    }
  }
  return false
}

function clearFlushTimer() {
  if (flushTimer !== null && typeof window !== 'undefined') {
    window.clearInterval(flushTimer)
    flushTimer = null
  }
}

function scheduleFlush() {
  if (typeof window === 'undefined') return
  if (flushTimer !== null) return
  flushTimer = window.setInterval(() => {
    if (pendingState && postSessionState(pendingState)) {
      pendingState = null
      clearFlushTimer()
    }
  }, 400)
}

function emitSessionState(payload: SessionStatePayload) {
  pendingState = payload
  if (!postSessionState(payload)) {
    scheduleFlush()
  } else {
    pendingState = null
    clearFlushTimer()
  }
}

function registerDefaultHandler(store: SessionStore) {
  if (defaultHandler) return
  defaultHandler = async (command: HostCommand) => {
    try {
      switch (command.type) {
        case 'switchSession': {
          const tabId = command.payload?.sessionId
          if (tabId) {
            await store.switchTab(tabId)
          }
          break
        }
        case 'createSession': {
          const tab = await store.createTab()
          // createTab 会自动切换到新 Tab，无需额外调用
          console.log('[IDE Bridge] Created new tab:', tab.tabId)
          break
        }
        case 'closeSession': {
          const tabId = command.payload?.sessionId
          if (tabId) {
            const tabs = resolveSessionList(store)
            // 如果只有一个会话，不允许关闭
            if (tabs.length <= 1) {
              console.warn('[IDE Bridge] Cannot close the last tab')
              break
            }
            // 如果关闭的是当前会话，先切换到其他会话
            const currentId = store.currentTabId
            if (tabId === currentId) {
              const otherTab = tabs.find((t: any) => t.tabId !== tabId)
              if (otherTab) {
                await store.switchTab(otherTab.tabId)
              }
            }
            // 关闭 Tab
            await store.closeTab(tabId)
          }
          break
        }
        case 'setLocale': {
          // IDEA 推送语言设置，前端应用并刷新页面
          const locale = command.payload?.locale
          if (locale) {
            const currentLocale = localeService.getLocale()
            // 只有语言不同时才刷新页面
            if (locale !== currentLocale) {
              console.log(`[IDE Bridge] Locale changed: ${currentLocale} -> ${locale}`)
              await localeService.setLocale(locale)
              // 刷新页面应用新语言
              window.location.reload()
            } else {
              console.log(`[IDE Bridge] Locale unchanged: ${locale}`)
            }
          }
          break
        }
        default:
          // 其他命令交给组件层处理
          break
      }
    } catch (error) {
      console.error(`[IDE Bridge] Failed to process host command ${command.type}:`, error)
    }
  }
  hostCommandHandlers.add(defaultHandler)
}

function resolveSessionList(store: SessionStore) {
  const list = (store.activeTabs as any)
  if (Array.isArray(list)) return list
  if (Array.isArray(list?.value)) return list.value
  return []
}

function startWatching(store: SessionStore) {
  const source = () => {
    const tabs = resolveSessionList(store).map((tab: any) => ({
      id: tab.tabId,
      title: tab.name?.value ?? tab.name ?? `会话 ${tab.tabId.slice(-6)}`,
      isGenerating: Boolean(tab.isGenerating?.value ?? tab.isGenerating),
      isConnected: (tab.connectionStatus?.value ?? tab.connectionStatus) === ConnectionStatus.CONNECTED ||
                   (tab.connectionStatus?.value ?? tab.connectionStatus) === ConnectionStatus.CONNECTING
    }))
    const activeTabId = store.currentTabId ?? null
    return {
      sessions: tabs,
      activeSessionId: activeTabId
    }
  }

  stopWatchHandle = watch(source, (snapshot) => {
    emitSessionState({
      type: 'session:update',
      sessions: snapshot.sessions,
      activeSessionId: snapshot.activeSessionId
    })
  }, { deep: true, immediate: true })
}

/**
 * 初始化 IDE 模式下的会话桥接。
 * 返回取消函数，在组件卸载时调用。
 */
export function setupIdeSessionBridge(sessionStore: SessionStore) {
  if (!ideaBridge.isInIde()) {
    return () => {}
  }
  activeConsumers += 1
  ensureGlobalBridge()

  if (activeConsumers === 1) {
    startWatching(sessionStore)
    registerDefaultHandler(sessionStore)
  }

  return () => {
    activeConsumers = Math.max(activeConsumers - 1, 0)
    if (activeConsumers === 0) {
      stopWatchHandle?.()
      stopWatchHandle = null
      if (defaultHandler) {
        hostCommandHandlers.delete(defaultHandler)
        defaultHandler = null
      }
      clearFlushTimer()
      pendingState = null
    }
  }
}

/**
 * 订阅 IDE 宿主命令（如 toggleHistory）。
 */
export function onIdeHostCommand(handler: HostCommandHandler) {
  hostCommandHandlers.add(handler)
  return () => hostCommandHandlers.delete(handler)
}

