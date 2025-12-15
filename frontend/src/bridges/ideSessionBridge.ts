import { watch, type WatchStopHandle } from 'vue'
import localeService from '@/services/localeService'
import type { useSessionStore } from '@/stores/sessionStore'
import { useToastStore } from '@/stores/toastStore'
import { ConnectionStatus } from '@/types/display'
import { jetbrainsRSocket, type SessionCommand, type SessionState } from '@/services/jetbrainsRSocket'

export interface HostCommand {
  type: string
  payload?: Record<string, any> | null
}

type SessionStore = ReturnType<typeof useSessionStore>
export type HostCommandHandler = (command: HostCommand) => void

declare global {
  interface Window {
    __CLAUDE_IDE_BRIDGE__?: {
      onHostCommand?: (command: HostCommand) => void
    }
  }
}

const hostCommandHandlers = new Set<HostCommandHandler>()
let activeConsumers = 0
let stopWatchHandle: WatchStopHandle | null = null
let defaultHandler: HostCommandHandler | null = null
let _cachedSessionStore: SessionStore | null = null
let removeSessionCommandListener: (() => void) | null = null

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

/**
 * 通过 RSocket 上报会话状态
 */
async function postSessionState(state: SessionState): Promise<boolean> {
  if (!jetbrainsRSocket.isConnected()) {
    console.log('[IDE Bridge] RSocket not connected, skipping state report')
    return false
  }

  try {
    return await jetbrainsRSocket.reportSessionState(state)
  } catch (error) {
    console.warn('[IDE Bridge] Failed to report session state:', error)
    return false
  }
}

/**
 * 处理后端推送的会话命令
 */
function handleSessionCommand(command: SessionCommand) {
  // 转换为 HostCommand 格式，保持兼容性
  const hostCommand: HostCommand = {
    type: command.type === 'switch' ? 'switchSession' :
          command.type === 'create' ? 'createSession' :
          command.type === 'close' ? 'closeSession' :
          command.type === 'rename' ? 'renameSession' :
          command.type === 'toggleHistory' ? 'toggleHistory' :
          command.type === 'setLocale' ? 'setLocale' :
          command.type === 'delete' ? 'deleteSession' :
          command.type === 'reset' ? 'resetSession' : command.type,
    payload: {
      sessionId: command.sessionId,
      newName: command.newName,
      locale: command.locale
    }
  }
  console.log('[IDE Bridge] Received session command:', hostCommand)
  notifyHostCommand(hostCommand)
}

/**
 * 发送会话状态到后端
 */
function emitSessionState(state: SessionState) {
  postSessionState(state)
}

function registerDefaultHandler(store: SessionStore) {
  if (defaultHandler) return
  defaultHandler = async (command: HostCommand) => {
    try {
      switch (command.type) {
        case 'switchSession': {
          const sessionId = command.payload?.sessionId
          if (sessionId) {
            // 先检查是否已有该会话的 Tab
            const tabs = resolveSessionList(store)
            const existingTab = tabs.find((t: any) =>
              t.tabId === sessionId || t.sessionId?.value === sessionId || t.sessionId === sessionId
            )
            if (existingTab) {
              // 已有 Tab，直接切换
              console.log('[IDE Bridge] Switching to existing tab:', existingTab.tabId)
              await store.switchTab(existingTab.tabId)
            } else {
              // 没有 Tab，加载历史会话
              console.log('[IDE Bridge] Resuming history session:', sessionId)
              const resumed = await store.resumeSession(sessionId)
              if (!resumed) {
                console.warn('[IDE Bridge] Failed to resume session:', sessionId)
              }
            }
          }
          break
        }
        case 'createSession': {
          const tab = await store.createTab()
          // createTab 会自动切换到新 Tab，无需额外调用
          console.log('[IDE Bridge] Created new tab:', tab.tabId)
          break
        }
        case 'resetSession': {
          // 重置/清空当前会话（不新建 Tab）
          const currentTabId = store.currentTabId
          if (currentTabId) {
            const tabs = resolveSessionList(store)
            const currentTab = tabs.find((t: any) => t.tabId === currentTabId)
            if (currentTab && typeof currentTab.reset === 'function') {
              await currentTab.reset()
              console.log('[IDE Bridge] Reset current tab:', currentTabId)
            } else {
              console.warn('[IDE Bridge] Current tab does not support reset')
            }
          }
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
            // 直接关闭 Tab，closeTab 内部会处理切换到前一个会话的逻辑
            await store.closeTab(tabId)
          }
          break
        }
        case 'deleteSession': {
          // 删除历史会话（后端已删除文件，前端只需关闭对应的 Tab）
          const sessionId = command.payload?.sessionId
          if (sessionId) {
            const tabs = resolveSessionList(store)
            // 查找匹配的 Tab（通过 sessionId 匹配）
            const matchingTab = tabs.find((t: any) => {
              const tabSessionId = typeof t.sessionId === 'object' ? t.sessionId?.value : t.sessionId
              return tabSessionId === sessionId
            })
            if (matchingTab) {
              // 如果只有一个会话，不关闭
              if (tabs.length <= 1) {
                console.warn('[IDE Bridge] Cannot delete the last tab, resetting instead')
                break
              }
              // 直接关闭 Tab，closeTab 内部会处理切换到前一个会话的逻辑
              await store.closeTab(matchingTab.tabId)
              console.log(`[IDE Bridge] Deleted session ${sessionId}`)
            } else {
              console.log(`[IDE Bridge] Session ${sessionId} not loaded as tab, nothing to close`)
            }
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
              // 2. 发送 /rename 命令到后端（通过 Tab 实例直接发送）
              const toastStore = useToastStore()
              if (tab.session?.isConnected && typeof tab.sendTextMessageDirect === 'function') {
                tab.sendTextMessageDirect(`/rename ${newName}`)
                  .then(() => {
                    toastStore.success(`Rename success: "${newName}"`)
                  })
                  .catch((err: Error) => {
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
        // toggleHistory 由 ModernChatView.vue 中的 onIdeHostCommand 监听器处理
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
  // 缓存 store 引用
  _cachedSessionStore = store

  // 注册 RSocket 会话命令监听器
  removeSessionCommandListener = jetbrainsRSocket.onSessionCommand(handleSessionCommand)

  const source = () => buildSessionSnapshot(store)

  stopWatchHandle = watch(source, (snapshot) => {
    console.log('[IDE Bridge] 🔄 Session state changed:', snapshot.sessions.length, 'sessions')
    emitSessionState(snapshot)
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
      removeSessionCommandListener?.()
      removeSessionCommandListener = null
      _cachedSessionStore = null
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
