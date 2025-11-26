import { watch, type WatchStopHandle } from 'vue'
import { ideaBridge } from '@/services/ideaBridge'
import type { useSessionStore } from '@/stores/sessionStore'

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
  const host = window.__CLAUDE_IDE_HOST__
  if (host?.postSessionState) {
    try {
      host.postSessionState(JSON.stringify(payload))
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
          const sessionId = command.payload?.sessionId
          if (sessionId) {
            await store.switchSession(sessionId)
          }
          break
        }
        case 'createSession': {
          const createFn = store.startNewSession ?? store.createSession
          const session = await createFn?.()
          if (session?.id) {
            await store.switchSession(session.id)
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
    const sessions = resolveSessionList(store).map((session: any) => ({
      id: session.id,
      title: session.name || `会话 ${session.id.slice(-6)}`,
      isGenerating: Boolean(session.isGenerating),
      isConnected: session.connectionStatus === 'CONNECTED' || session.connectionStatus === 'CONNECTING'
    }))
    const activeSessionId = (store.currentSessionId as any)?.value ?? store.currentSessionId ?? null
    return {
      sessions,
      activeSessionId
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

