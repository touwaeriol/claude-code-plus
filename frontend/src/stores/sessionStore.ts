import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { apiClient } from '@/services/apiClient'
import { claudeService } from '@/services/claudeService'
import { mapWebSocketResponseToMessages } from '@/services/messageMapper'
import { isSystemMessageWithModelId } from '@/utils/typeGuards'
import type { Session } from '@/services/apiClient'
import type { Message } from '@/types/message'
import type { WebSocketResponse } from '@/services/websocketClient'

export const useSessionStore = defineStore('session', () => {
  const sessions = ref<Session[]>([])
  const currentSessionId = ref<string | null>(null)
  const loading = ref(false)

  // 历史消息缓存 - sessionId -> messages
  const historyCache = ref<Map<string, Message[]>>(new Map())

  // 会话消息存储 - sessionId -> messages（实时消息）
  const sessionMessages = ref<Map<string, Message[]>>(new Map())

  // 当前会话实际使用的模型ID（从 system init / model_changed 消息中提取）
  const currentModelId = ref<string | null>(null)
  const sessionModelIds = ref<Map<string, string>>(new Map())

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
      console.log('📋 加载会话列表...')
      const sessionList = await apiClient.getSessions()

      sessions.value = sessionList
      console.log(`✅ 加载了 ${sessions.value.length} 个会话`)

      // 如果没有当前会话但有会话列表,选择第一个
      if (!currentSessionId.value && sessions.value.length > 0) {
        await switchSession(sessions.value[0].id)
      }
    } catch (error) {
      console.error('❌ 加载会话列表失败:', error)
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
      console.log('➕ 创建新会话...')
      const newSession = await apiClient.createSession(name)

      if (newSession) {
        sessions.value.unshift(newSession)
        // 切换到新会话（会自动建立 WebSocket 连接）
        await switchSession(newSession.id)
        console.log('✅ 会话已创建:', newSession.id)
        return newSession
      } else {
        console.error('❌ 创建会话失败')
        return null
      }
    } catch (error) {
      console.error('❌ 创建会话异常:', error)
      return null
    }
  }

  /**
   * 切换会话
   *
   * 关键流程：
   * 1. 断开旧会话的 WebSocket 连接
   * 2. 加载新会话的历史消息
   * 3. 建立新会话的 WebSocket 连接
   * 4. 设置消息处理回调
   */
  async function switchSession(sessionId: string) {
    try {
      console.log('🔄 切换到会话:', sessionId)

      // 1. 断开旧会话的 WebSocket 连接
      if (currentSessionId.value && currentSessionId.value !== sessionId) {
        claudeService.disconnect(currentSessionId.value)
      }

      // 2. 加载新会话的历史消息（如果未加载）
      if (!sessionMessages.value.has(sessionId)) {
        const history = await loadSessionHistory(sessionId)
        // 创建新数组副本,确保响应式更新
        sessionMessages.value.set(sessionId, [...history])
      }

      // 3. 切换当前会话
      currentSessionId.value = sessionId

      // 恢复该会话已知的模型ID（如果有）
      currentModelId.value = sessionModelIds.value.get(sessionId) ?? null

      // 4. 建立新会话的 WebSocket 连接
      await claudeService.connect(sessionId, (response: WebSocketResponse) => {
        handleWebSocketMessage(sessionId, response)
      })

      console.log('✅ 已切换到会话:', sessionId)
      return true
    } catch (error) {
      console.error('❌ 切换会话失败:', error)
      return false
    }
  }

  /**
   * 处理 WebSocket 消息
   *
   * 使用类型守卫实现类型安全的消息处理
   */
  function handleWebSocketMessage(sessionId: string, response: WebSocketResponse) {
    console.log(`📨 收到会话 ${sessionId} 的消息: ${response.type}`)

    if (sessionId !== currentSessionId.value) {
      console.log('⚠️ 忽略非当前会话的消息')
      return
    }

    // ✅ 类型安全：提取系统消息中的模型ID（init / model_changed）
    if (isSystemMessageWithModelId(response)) {
      // response.message 现在是 { subtype: 'init' | 'model_changed', data?: any }
      try {
        const data = response.message.data
        const modelId: unknown = data?.model ?? data?.model_id

        if (typeof modelId === 'string' && modelId.length > 0) {
          sessionModelIds.value.set(sessionId, modelId)
          currentModelId.value = modelId
          console.log('🤖 更新实际模型ID:', sessionId, modelId)
        }
      } catch (e) {
        console.error('❌ 解析系统消息中的模型ID失败:', e)
      }
    }

    const uiMessages = mapWebSocketResponseToMessages(response)
    if (!uiMessages.length) {
      return
    }

    uiMessages.forEach(msg => addMessage(sessionId, msg))
  }

  /**
   * 添加消息到指定会话
   *
   * 注意:必须创建新数组以触发 Vue 响应式更新
   */
  function addMessage(sessionId: string, message: Message) {
    const currentMessages = sessionMessages.value.get(sessionId) || []
    const newMessages = [...currentMessages, message]
    sessionMessages.value.set(sessionId, newMessages)

    console.log(`💬 会话 ${sessionId} 添加消息，当前共 ${newMessages.length} 条`)
  }

  /**
   * 删除会话
   */
  async function deleteSession(sessionId: string) {
    try {
      console.log('🗑️ 删除会话:', sessionId)

      const success = await apiClient.deleteSession(sessionId)

      if (success) {
        // 断开 WebSocket 连接
        claudeService.disconnect(sessionId)

        // 从列表中移除
        const index = sessions.value.findIndex(s => s.id === sessionId)
        if (index !== -1) {
          sessions.value.splice(index, 1)
        }

        // 清除消息缓存
        sessionMessages.value.delete(sessionId)
        historyCache.value.delete(sessionId)

        // 如果删除的是当前会话,切换到第一个会话
        if (currentSessionId.value === sessionId) {
          if (sessions.value.length > 0) {
            await switchSession(sessions.value[0].id)
          } else {
            currentSessionId.value = null
          }
        }

        console.log('✅ 会话已删除:', sessionId)
        return true
      } else {
        console.error('❌ 删除会话失败')
        return false
      }
    } catch (error) {
      console.error('❌ 删除会话异常:', error)
      return false
    }
  }

  /**
   * 重命名会话
   */
  async function renameSession(sessionId: string, newName: string) {
    try {
      console.log('✏️ 重命名会话:', sessionId, '→', newName)

      const success = await apiClient.renameSession(sessionId, newName)

      if (success) {
        // 更新本地列表
        const session = sessions.value.find(s => s.id === sessionId)
        if (session) {
          session.name = newName
        }
        console.log('✅ 会话已重命名:', sessionId)
        return true
      } else {
        console.error('❌ 重命名会话失败')
        return false
      }
    } catch (error) {
      console.error('❌ 重命名会话异常:', error)
      return false
    }
  }

  /**
   * 加载会话历史消息
   */
  async function loadSessionHistory(sessionId: string): Promise<Message[]> {
    // 检查缓存
    if (historyCache.value.has(sessionId)) {
      console.log('📋 使用缓存的历史消息:', sessionId)
      return historyCache.value.get(sessionId)!
    }

    loading.value = true
    try {
      console.log('📡 加载历史消息:', sessionId)
      const messages = await apiClient.getSessionHistory(sessionId)

      console.log(`✅ 加载了 ${messages.length} 条历史消息`)

      // 缓存历史
      historyCache.value.set(sessionId, messages)
      return messages
    } catch (error) {
      console.error('❌ 加载历史消息失败:', error)
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

  /**
   * 获取当前会话的消息
   */
  const currentMessages = computed(() => {
    if (!currentSessionId.value) return []
    return sessionMessages.value.get(currentSessionId.value) || []
  })

  return {
    sessions,
    currentSessionId,
    currentSession,
    currentMessages,
    currentModelId,
    loading,
    loadSessions,
    createSession,
    switchSession,
    deleteSession,
    renameSession,
    loadSessionHistory,
    clearHistoryCache,
    addMessage,
    handleWebSocketMessage
  }
})
