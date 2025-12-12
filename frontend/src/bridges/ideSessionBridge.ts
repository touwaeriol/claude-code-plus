import { watch, type WatchStopHandle } from 'vue'
import { ideaBridge } from '@/services/ideaBridge'
import localeService from '@/services/localeService'
import type { useSessionStore } from '@/stores/sessionStore'
import { useToastStore } from '@/stores/toastStore'
import { ConnectionStatus } from '@/types/display'

export interface HostCommand {
  type: string
  payload?: Record<string, any> | null
}

interface SessionSummaryPayload {
  id: string
  title: string
  sessionId?: string | null  // 真实的会话 ID
  isGenerating: boolean
  isConnected: boolean   // 是否已连接
  isConnecting: boolean  // 是否正在连接中
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
  const jcef = (window as any).__IDEA_JCEF__
  // 调试：检查 JCEF bridge 状态
  console.log('[IDE Bridge] 🔍 Checking JCEF bridge:', {
    hasJcef: !!jcef,
    hasSession: !!jcef?.session,
    hasPostState: !!jcef?.session?.postState,
    jcefKeys: jcef ? Object.keys(jcef) : []
  })
  if (jcef?.session?.postState) {
    try {
      // 确保序列化的是纯数据，避免循环引用
      const cleanPayload = {
        type: payload.type,
        sessions: payload.sessions.map(s => ({
          id: s.id,
          title: s.title,
          sessionId: s.sessionId ?? null,
          isGenerating: s.isGenerating,
          isConnected: s.isConnected,
          isConnecting: s.isConnecting
        })),
        activeSessionId: payload.activeSessionId
      }
      console.log('[IDE Bridge] 📤 Posting session state:', cleanPayload.sessions.length, 'sessions, active:', cleanPayload.activeSessionId)
      jcef.session.postState(JSON.stringify(cleanPayload))
      return true
    } catch (error) {
      console.warn('[IDE Bridge] Failed to post session state:', error)
    }
  } else {
    console.warn('[IDE Bridge] ⚠️ JCEF session bridge not ready yet')
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

// 监听 JCEF 准备好的事件，立即发送 pending 状态
let jcefReadyListenerAdded = false
let cachedSessionStore: SessionStore | null = null

function ensureJcefReadyListener() {
  if (jcefReadyListenerAdded || typeof window === 'undefined') return
  jcefReadyListenerAdded = true
  window.addEventListener('idea:jcefReady', () => {
    console.log('[IDE Bridge] 🎉 idea:jcefReady event received')
    // 立即发送 pending 状态
    if (pendingState) {
      if (postSessionState(pendingState)) {
        pendingState = null
        clearFlushTimer()
      }
    }
    // 如果有缓存的 store，重新触发一次状态同步
    if (cachedSessionStore) {
      const snapshot = buildSessionSnapshot(cachedSessionStore)
      emitSessionState({
        type: 'session:update',
        sessions: snapshot.sessions,
        activeSessionId: snapshot.activeSessionId
      })
    }
  })
}

function emitSessionState(payload: SessionStatePayload) {
  pendingState = payload
  ensureJcefReadyListener()  // 确保监听 JCEF 准备好事件
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
        case 'renameSession': {
          // IDEA 发送的重命名命令
          const tabId = command.payload?.sessionId
          const newName = command.payload?.newName
          if (tabId && newName) {
            const tabs = resolveSessionList(store)
            const tab = tabs.find((t: any) => t.tabId === tabId)
            if (tab) {
              // 1. 立即更新 UI
              if (typeof tab.rename === 'function') {
                tab.rename(newName)
              } else if (tab.name) {
                // 兼容不同的 Tab 结构
                if (typeof tab.name === 'object' && 'value' in tab.name) {
                  tab.name.value = newName
                }
              }
              // 2. 发送 /rename 命令到后端
              const sessionId = tab.sessionId?.value ?? tab.sessionId
              const toastStore = useToastStore()
              if (sessionId) {
                const { aiAgentService } = await import('@/services/aiAgentService')
                aiAgentService.sendMessage(sessionId, `/rename ${newName}`)
                  .then(() => {
                    toastStore.success(`Rename success: "${newName}"`)
                  })
                  .catch(err => {
                    console.error('[IDE Bridge] 发送 /rename 命令失败:', err)
                    toastStore.error('Rename failed')
                  })
              } else {
                // 未连接时，UI 已更新，显示成功提示
                toastStore.success(`Rename success: "${newName}"`)
              }
              console.log(`[IDE Bridge] Renamed session ${tabId} to "${newName}"`)
            }
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

function buildSessionSnapshot(store: SessionStore) {
  const rawTabs = resolveSessionList(store)
  // 确保提取纯数据，避免 Vue 响应式对象的循环引用
  const tabs = rawTabs.map((tab: any) => {
    // 解包所有可能的响应式引用
    const tabId = typeof tab.tabId === 'object' ? tab.tabId?.value : tab.tabId
    const name = typeof tab.name === 'object' ? tab.name?.value : tab.name
    const sessionId = typeof tab.sessionId === 'object' ? tab.sessionId?.value : tab.sessionId
    const isGenerating = typeof tab.isGenerating === 'object' ? tab.isGenerating?.value : tab.isGenerating
    const connectionStatus = typeof tab.connectionState?.status === 'object'
      ? tab.connectionState?.status?.value
      : tab.connectionState?.status

    return {
      id: String(tabId || ''),
      title: String(name || `会话 ${String(tabId || '').slice(-6)}`),
      sessionId: sessionId ? String(sessionId) : null,
      isGenerating: Boolean(isGenerating),
      isConnected: connectionStatus === ConnectionStatus.CONNECTED,
      isConnecting: connectionStatus === ConnectionStatus.CONNECTING
    }
  })
  const activeTabId = store.currentTabId ?? null
  return {
    sessions: tabs,
    activeSessionId: activeTabId ? String(activeTabId) : null
  }
}

function startWatching(store: SessionStore) {
  // 缓存 store 引用，用于 JCEF 准备好时重新同步
  cachedSessionStore = store

  const source = () => buildSessionSnapshot(store)

  stopWatchHandle = watch(source, (snapshot) => {
    console.log('[IDE Bridge] 🔄 Session state changed:', snapshot.sessions.length, 'sessions')
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
