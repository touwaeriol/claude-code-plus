import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { ideaBridge } from '@/services/ideaBridge'
import type { Session } from '@/components/session/SessionList.vue'
import type { Message } from '@/types/message'

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<Session[]>([])
  const currentSessionId = ref<string | null>(null)
  const loading = ref(false)

  // 历史消息缓存 - sessionId -> messages
  const historyCache = ref<Map<string, Message[]>>(new Map())

  const currentSession = computed(() => {
    if (!currentSessionId.value) return null
    return sessions.value.find(s => s.id === currentSessionId.value) || null
  })

  /**
   * 加载会话列表
   */
  async function loadSessions() {
    loading.value = true
    try {
      console.log('📋 Loading sessions...')
      const response = await ideaBridge.query('session.list')

      if (response.success && response.data?.sessions) {
        sessions.value = response.data.sessions
        console.log(`✅ Loaded ${sessions.value.length} sessions`)

        // 如果没有当前会话但有会话列表,选择第一个
        if (!currentSessionId.value && sessions.value.length > 0) {
          currentSessionId.value = sessions.value[0].id
        }
      } else {
        console.warn('⚠️ Failed to load sessions:', response.error)
        sessions.value = []
      }
    } catch (error) {
      console.error('❌ Error loading sessions:', error)
      sessions.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 创建新会话
   */
  async function createSession(name?: string) {
    try {
      console.log('➕ Creating new session...')
      const response = await ideaBridge.query('session.create', { name })

      if (response.success && response.data?.session) {
        const newSession = response.data.session
        sessions.value.unshift(newSession)
        currentSessionId.value = newSession.id
        console.log('✅ Session created:', newSession.id)
        return newSession
      } else {
        console.error('❌ Failed to create session:', response.error)
        return null
      }
    } catch (error) {
      console.error('❌ Error creating session:', error)
      return null
    }
  }

  /**
   * 切换会话
   */
  async function switchSession(sessionId: string) {
    try {
      console.log('🔄 Switching to session:', sessionId)
      const response = await ideaBridge.query('session.switch', { sessionId })

      if (response.success) {
        currentSessionId.value = sessionId
        console.log('✅ Switched to session:', sessionId)
        return true
      } else {
        console.error('❌ Failed to switch session:', response.error)
        return false
      }
    } catch (error) {
      console.error('❌ Error switching session:', error)
      return false
    }
  }

  /**
   * 删除会话
   */
  async function deleteSession(sessionId: string) {
    try {
      console.log('🗑️ Deleting session:', sessionId)
      const response = await ideaBridge.query('session.delete', { sessionId })

      if (response.success) {
        // 从列表中移除
        const index = sessions.value.findIndex(s => s.id === sessionId)
        if (index !== -1) {
          sessions.value.splice(index, 1)
        }

        // 如果删除的是当前会话,切换到第一个会话
        if (currentSessionId.value === sessionId) {
          if (sessions.value.length > 0) {
            await switchSession(sessions.value[0].id)
          } else {
            currentSessionId.value = null
          }
        }

        console.log('✅ Session deleted:', sessionId)
        return true
      } else {
        console.error('❌ Failed to delete session:', response.error)
        return false
      }
    } catch (error) {
      console.error('❌ Error deleting session:', error)
      return false
    }
  }

  /**
   * 重命名会话
   */
  async function renameSession(sessionId: string, newName: string) {
    try {
      console.log('✏️ Renaming session:', sessionId, 'to', newName)
      const response = await ideaBridge.query('session.rename', { sessionId, name: newName })

      if (response.success) {
        // 更新本地列表
        const session = sessions.value.find(s => s.id === sessionId)
        if (session) {
          session.name = newName
        }
        console.log('✅ Session renamed:', sessionId)
        return true
      } else {
        console.error('❌ Failed to rename session:', response.error)
        return false
      }
    } catch (error) {
      console.error('❌ Error renaming session:', error)
      return false
    }
  }

  /**
   * 加载会话历史消息
   */
  async function loadSessionHistory(sessionId: string): Promise<Message[]> {
    // 检查缓存
    if (historyCache.value.has(sessionId)) {
      console.log('📋 Using cached history for session:', sessionId)
      return historyCache.value.get(sessionId)!
    }

    loading.value = true
    try {
      console.log('📡 Loading history for session:', sessionId)
      const response = await ideaBridge.query('session.getHistory', { sessionId })

      if (response.success && response.data) {
        const messages = (response.data.messages || []) as Message[]
        console.log(`✅ Loaded ${messages.length} messages from history`)

        // 缓存历史
        historyCache.value.set(sessionId, messages)
        return messages
      } else {
        console.error('Failed to load history:', response.error)
        return []
      }
    } catch (error) {
      console.error('Error loading history:', error)
      return []
    } finally {
      loading.value = false
    }
  }

  /**
   * 清除历史缓存
   */
  function clearHistoryCache(sessionId?: string) {
    if (sessionId) {
      historyCache.value.delete(sessionId)
      console.log('🗑️ Cleared history cache for session:', sessionId)
    } else {
      historyCache.value.clear()
      console.log('🗑️ Cleared all history cache')
    }
  }

  return {
    sessions,
    currentSessionId,
    currentSession,
    loading,
    loadSessions,
    createSession,
    switchSession,
    deleteSession,
    renameSession,
    loadSessionHistory,
    clearHistoryCache
  }
})
