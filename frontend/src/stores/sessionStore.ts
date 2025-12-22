/**
 * Session Store（简化版）
 *
 * 架构变化：从中心化管理变为 Tab 自治
 * - 每个 Tab 管理自己的状态、连接、消息处理
 * - Store 只负责 Tab 列表管理
 * - 切换 Tab 就是切换引用，不需要 ID 查找
 *
 * 使用方式：
 * ```typescript
 * const store = useSessionStore()
 * await store.createTab()
 * store.currentTab?.sendMessage({ contexts: [], contents: [...] })
 * ```
 */

import { ref, computed, shallowRef, watch } from 'vue'
import { defineStore, storeToRefs } from 'pinia'
import { i18n } from '@/i18n'
import { useSessionTab, type SessionTabInstance, type TabConnectOptions } from '@/composables/useSessionTab'
import { getModelCapability, getDefaultModelId } from '@/constants/models'
import type { RpcPermissionMode } from '@/types/rpc'
import { ConnectionStatus } from '@/types/display'
import { loggers } from '@/utils/logger'
import { HISTORY_INITIAL_LOAD } from '@/constants/messageWindow'
import { aiAgentService } from '@/services/aiAgentService'
import { useSettingsStore } from '@/stores/settingsStore'

const log = loggers.session

// 重新导出类型
export { ConnectionStatus } from '@/types/display'
export type { SessionTabInstance } from '@/composables/useSessionTab'

/**
 * 获取默认会话设置（动态获取，避免硬编码）
 */
function getDefaultSessionSettings() {
  const defaultModelId = getDefaultModelId()
  const defaultModelCapability = getModelCapability(defaultModelId)
  return {
    modelId: defaultModelCapability.modelId,
    thinkingLevel: 8096, // Ultra - 默认思考等级（与 IDEA 设置保持一致）
    permissionMode: 'default' as RpcPermissionMode,
    skipPermissions: false
  }
}

/**
 * Session Store
 *
 * 职责：
 * - 管理 Tab 列表（tabs）
 * - 管理当前 Tab（currentTab）
 * - 提供创建/切换/关闭 Tab 的方法
 */
export const useSessionStore = defineStore('session', () => {
  // ========== Tab 列表管理 ==========

  /**
   * Tab 列表
   * 使用 shallowRef 避免深度响应式（Tab 内部已经是响应式的）
   */
  const tabs = shallowRef<SessionTabInstance[]>([])

  /**
   * 当前 Tab
   */
  const currentTab = shallowRef<SessionTabInstance | null>(null)

  /**
   * 当前连接状态（独立 ref，解决 shallowRef 内部状态追踪问题）
   */
  const currentConnectionState = ref<ConnectionStatus>(ConnectionStatus.DISCONNECTED)

  /**
   * displayItems 版本号（解决 shallowRef 内部 reactive 数组变化追踪问题）
   */
  const displayItemsVersion = ref(0)

  /**
   * 加载状态
   */
  const loading = ref(false)

  // ========== 计算属性 ==========

  /**
   * 当前 Tab ID
   */
  const currentTabId = computed(() => currentTab.value?.tabId ?? null)

  /**
   * 当前 Session ID（用于后端通信）
   */
  const currentSessionId = computed(() => currentTab.value?.sessionId.value ?? null)

  /**
   * 活跃的 Tab 列表（按 order 排序）
   */
  const activeTabs = computed(() => {
    return [...tabs.value].sort((a, b) => a.order.value - b.order.value)
  })

  /**
   * Tab 数量
   */
  const tabCount = computed(() => tabs.value.length)

  /**
   * 是否有 Tab
   */
  const hasTabs = computed(() => tabs.value.length > 0)

  /**
   * 当前连接状态
   * 使用独立的 ref 而不是从 Tab 内部获取，解决 shallowRef 追踪问题
   */
  const currentConnectionStatus = computed(() => currentConnectionState.value)

  /**
   * 当前是否正在生成
   */
  const currentIsGenerating = computed(() =>
    currentTab.value?.isGenerating.value ?? false
  )

  /**
   * 当前历史分页加载中
   */
  const currentIsLoadingHistory = computed(() =>
    currentTab.value?.historyState.loading ?? false
  )

  /**
   * 历史是否还有更多
   */
  const currentHasMoreHistory = computed(() =>
    currentTab.value?.historyState.hasMore ?? false
  )

  /**
   * 当前历史状态
   */
  const currentHistoryState = computed(() =>
    currentTab.value?.historyState ?? null
  )

  /**
   * 当前 displayItems
   * 依赖 displayItemsVersion 确保在数组内容变化时重新计算
   */
  const currentDisplayItems = computed(() => {
    // 依赖版本号，强制在 displayItems 变化时重新计算
    void displayItemsVersion.value
    return currentTab.value?.displayItems ?? []
  })

  // 监听 displayItems 变化，递增版本号触发 computed 更新
  watch(
    () => currentTab.value?.displayItems.length,
    () => {
      displayItemsVersion.value++
    },
    { immediate: true }
  )

  // 监听 settingsStore.skipPermissions 变化，同步到当前 Tab
  // 这样 settingsStore 不需要导入 sessionStore，避免循环依赖
  const settingsStore = useSettingsStore()
  const { settings } = storeToRefs(settingsStore)
  watch(
    () => settings.value.skipPermissions,
    (newValue) => {
      if (currentTab.value && newValue !== undefined) {
        currentTab.value.setPendingSetting('skipPermissions', newValue)
        log.info(`[SessionStore] 同步 skipPermissions 到当前会话: ${newValue}`)
      }
    }
  )

  /**
   * 当前消息列表
   */
  const currentMessages = computed(() =>
    currentTab.value?.messages ?? []
  )

  /**
   * 当前会话设置
   */
  const currentSessionSettings = computed(() => {
    if (!currentTab.value) return null
    return {
      modelId: currentTab.value.modelId.value,
      thinkingEnabled: currentTab.value.thinkingEnabled.value,
      permissionMode: currentTab.value.permissionMode.value,
      skipPermissions: currentTab.value.skipPermissions.value
    }
  })

  /**
   * 当前错误信息
   */
  const currentLastError = computed(() =>
    currentTab.value?.connectionState.lastError ?? null
  )

  /**
   * 清除当前错误
   */
  function clearCurrentError(): void {
    if (currentTab.value) {
      currentTab.value.connectionState.lastError = null
    }
  }

  /**
   * 通知 displayItems 已更新
   * 递增版本号，触发 computed 重新计算
   */
  function notifyDisplayItemsChanged(): void {
    displayItemsVersion.value++
  }

  // ========== Tab 创建 ==========

  /**
   * 创建新 Tab
   *
   * @param name Tab 名称
   * @param options 连接选项
   */
  async function createTab(name?: string, options?: TabConnectOptions): Promise<SessionTabInstance> {
    // 计算新的 order
    const maxOrder = tabs.value.length > 0
      ? Math.max(...tabs.value.map(t => t.order.value))
      : -1

    // 创建 Tab 实例
    const tab = useSessionTab(maxOrder + 1)

    // 设置名称
    const shortTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    tab.name.value = name || i18n.global.t('session.defaultName', { time: shortTime })

    // 添加到列表（shallowRef 需要替换整个数组才能触发响应式）
    tabs.value = [...tabs.value, tab]

    // 切换到新 Tab
    currentTab.value = tab
    currentConnectionState.value = ConnectionStatus.DISCONNECTED

    log.info(`[SessionStore] 创建 Tab: ${tab.tabId}`)

    // 获取连接设置（使用 IDEA 默认设置，而非从当前会话复制）
    const globalSettings = settingsStore.settings
    const defaultSettings = getDefaultSessionSettings()
    // 使用 getModelCapability 支持内置和自定义模型
    const modelCapability = getModelCapability(globalSettings.model)
    const connectOptions: TabConnectOptions = options || {
      model: modelCapability.modelId || defaultSettings.modelId,
      thinkingLevel: globalSettings.maxThinkingTokens ?? defaultSettings.thinkingLevel,
      permissionMode: (globalSettings.permissionMode as RpcPermissionMode) || defaultSettings.permissionMode,
      skipPermissions: globalSettings.skipPermissions ?? defaultSettings.skipPermissions
    }

    log.info(`[SessionStore] 新 Tab 使用全局设置:`, connectOptions)

    // 延迟连接：保存初始连接配置，等首次发送时再 connect
    tab.setInitialConnectOptions(connectOptions)
    currentConnectionState.value = tab.connectionState.status

    return tab
  }

  /**
   * 创建新会话（别名）
   */
  async function createSession(name?: string): Promise<SessionTabInstance> {
    return createTab(name)
  }

  /**
   * 开始新会话（别名）
   */
  async function startNewSession(name?: string): Promise<SessionTabInstance> {
    return createTab(name)
  }

  /**
   * 恢复会话
   *
   * @param externalSessionId 后端会话 ID
   * @param name 会话名称
   */
  const HISTORY_TAIL_LIMIT = HISTORY_INITIAL_LOAD

  async function resumeSession(
    externalSessionId: string,
    name?: string,
    projectPath?: string,
    _messageCount?: number
  ): Promise<SessionTabInstance | null> {
    if (!externalSessionId) return null

    // 如果只有一个初始占位 Tab 且尚未连接，直接移除以避免多余标签
    if (tabs.value.length === 1) {
      const onlyTab = tabs.value[0]
      const isUnconnectedPlaceholder =
        onlyTab.sessionId.value === null &&
        onlyTab.connectionState.status !== ConnectionStatus.CONNECTED &&
        onlyTab.connectionState.status !== ConnectionStatus.CONNECTING &&
        onlyTab.messages.length === 0 &&
        onlyTab.displayItems.length === 0
      if (isUnconnectedPlaceholder) {
        tabs.value = []
        currentTab.value = null
      }
    }

    // 检查是否已有此会话的 Tab
    const existingTab = tabs.value.find(t => t.sessionId.value === externalSessionId)
    if (existingTab) {
      currentTab.value = existingTab
      return existingTab
    }

    // 创建新 Tab 并恢复会话
    const resumeLabel = externalSessionId.slice(-8) || externalSessionId
    const tab = await createTab(name || `历史会话 ${resumeLabel}`, {
      continueConversation: true,
      resumeSessionId: externalSessionId
    })
    // 先把 sessionId 显示为历史 ID，方便悬停/复制（连接成功后会覆盖成新的 sessionId）
    tab.sessionId.value = externalSessionId
    // 设置项目路径（用于编辑重发等功能）
    if (projectPath) {
      tab.projectPath.value = projectPath
    }

    // 异步后台加载历史，避免阻塞 UI，默认取尾部 TAIL_LIMIT
    // 使用 offset=-1 表示从尾部加载（避免 messageCount 统计不准确导致越界）
    const historyPromise = tab.loadHistory({
      sessionId: externalSessionId,
      projectPath,
      offset: -1,  // offset < 0 表示从尾部加载
      limit: HISTORY_TAIL_LIMIT
    }).catch(error => {
      log.warn(`[SessionStore] 加载历史失败: ${externalSessionId}`, error)
    })
    ;(tab as any).__historyPromise = historyPromise

    // 如果没有传入 name，异步获取 customTitle 并更新 tab 名称
    if (!name && projectPath) {
      aiAgentService.getHistoryMetadata({
        sessionId: externalSessionId,
        projectPath
      }).then(metadata => {
        if (metadata.customTitle) {
          tab.rename(metadata.customTitle)
          log.info(`[SessionStore] 使用 customTitle 作为 tab 名称: ${metadata.customTitle}`)
        }
      }).catch(error => {
        log.warn(`[SessionStore] 获取 customTitle 失败: ${externalSessionId}`, error)
      })
    }

    return tab
  }

  // ========== Tab 切换 ==========

  /**
   * 切换到指定 Tab
   *
   * @param tabOrId Tab 实例或 tabId
   */
  async function switchTab(tabOrId: SessionTabInstance | string): Promise<void> {
    const tab = typeof tabOrId === 'string'
      ? tabs.value.find(t => t.tabId === tabOrId)
      : tabOrId

    if (!tab) {
      log.warn(`[SessionStore] Tab 不存在: ${tabOrId}`)
      return
    }

    // 保存当前 Tab 的 UI 状态（如果有）
    // currentTab.value?.saveUiState({ ... })

    // 切换
    currentTab.value = tab
    // 同步连接状态
    currentConnectionState.value = tab.connectionState.status
    tab.touch()

    log.debug(`[SessionStore] 切换到 Tab: ${tab.tabId}`)
  }

  /**
   * 切换会话（别名，兼容旧 API）
   */
  async function switchSession(id: string): Promise<void> {
    await switchTab(id)
  }

  // ========== Tab 关闭 ==========

  /**
   * 关闭 Tab
   *
   * @param tabOrId Tab 实例或 tabId
   */
  async function closeTab(tabOrId: SessionTabInstance | string): Promise<boolean> {
    const tab = typeof tabOrId === 'string'
      ? tabs.value.find(t => t.tabId === tabOrId)
      : tabOrId

    if (!tab) {
      log.warn(`[SessionStore] Tab 不存在: ${tabOrId}`)
      return false
    }

    // 如果关闭的是当前 Tab，先确定要切换到哪个 Tab
    let nextTab: SessionTabInstance | null = null
    if (currentTab.value === tab) {
      // 按 order 排序的列表中找到当前 Tab 的位置
      const sortedTabs = [...tabs.value].sort((a, b) => a.order.value - b.order.value)
      const currentIndex = sortedTabs.findIndex(t => t === tab)

      // 优先选择前一个 Tab（往前最近的），否则选择后一个
      if (currentIndex > 0) {
        nextTab = sortedTabs[currentIndex - 1]
      } else if (sortedTabs.length > 1) {
        nextTab = sortedTabs[currentIndex + 1]
      }
    }

    // 断开连接（不等待，后端会自动检测连接关闭并清理资源）
    tab.disconnect()

    // 从列表移除（shallowRef 需要替换整个数组才能触发响应式）
    tabs.value = tabs.value.filter(t => t !== tab)

    // 切换到确定的下一个 Tab
    if (currentTab.value === tab) {
      currentTab.value = nextTab
      if (nextTab) {
        currentConnectionState.value = nextTab.connectionState.status
      }
    }

    log.info(`[SessionStore] 关闭 Tab: ${tab.tabId}`)
    return true
  }

  /**
   * 删除会话（别名，兼容旧 API）
   */
  async function deleteSession(id: string): Promise<boolean> {
    return closeTab(id)
  }

  // ========== Tab 排序 ==========

  /**
   * 更新 Tab 排序
   *
   * @param newOrder 新的 tabId 顺序数组
   */
  function updateTabOrder(newOrder: string[]): void {
    newOrder.forEach((tabId, index) => {
      const tab = tabs.value.find(t => t.tabId === tabId)
      if (tab) {
        tab.setOrder(index)
      }
    })
    log.debug(`[SessionStore] 更新 Tab 排序`)
  }

  /**
   * 重置当前 Tab 为空的新会话
   *
   * 与 createTab 不同，这个方法不会创建新 Tab，而是清空当前 Tab 的所有状态
   */
  async function resetCurrentTab(): Promise<void> {
    const tab = currentTab.value
    if (!tab) {
      log.warn(`[SessionStore] 没有当前 Tab，无法重置`)
      return
    }

    // 1. 断开连接（同步，不等待）
    tab.disconnect()

    // 2. 重置所有状态
    tab.reset()

    // 3. 重置 sessionId
    tab.sessionId.value = null

    // 4. 重新设置名称
    const shortTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    tab.name.value = i18n.global.t('session.defaultName', { time: shortTime })

    // 5. 保留之前的设置，设置初始连接选项
    const defaultSettings = getDefaultSessionSettings()
    const connectOptions: TabConnectOptions = {
      model: tab.modelId.value || defaultSettings.modelId,
      thinkingLevel: tab.thinkingLevel.value ?? defaultSettings.thinkingLevel,
      permissionMode: tab.permissionMode.value || defaultSettings.permissionMode,
      skipPermissions: tab.skipPermissions.value ?? defaultSettings.skipPermissions
    }
    tab.setInitialConnectOptions(connectOptions)

    // 6. 同步连接状态
    currentConnectionState.value = tab.connectionState.status

    log.info(`[SessionStore] 已重置当前 Tab: ${tab.tabId}`)
  }

  // ========== 向后兼容的方法 ==========

  /**
   * 发送消息（通过当前 Tab）
   */
  async function sendMessage(message: string): Promise<void> {
    if (!currentTab.value) {
      throw new Error('当前没有活跃的会话')
    }
    currentTab.value.sendTextMessage(message)
  }

  /**
   * 发送带内容的消息（通过当前 Tab）
   */
  function enqueueMessage(message: { contexts: any[]; contents: any[] }): void {
    if (!currentTab.value) {
      console.error('[SessionStore] enqueueMessage: 没有活跃会话')
      return
    }
    currentTab.value.sendMessage(message)
  }

  /**
   * 中断（通过当前 Tab）
   */
  async function interrupt(): Promise<void> {
    if (!currentTab.value) {
      throw new Error('当前没有活跃的会话')
    }
    await currentTab.value.interrupt()
  }

  /**
   * 后台运行（通过当前 Tab）
   */
  async function runInBackground(): Promise<void> {
    if (!currentTab.value) {
      throw new Error('当前没有活跃的会话')
    }
    await currentTab.value.runInBackground()
  }

  /**
   * 设置模型（通过当前 Tab）
   */
  async function setModel(model: string): Promise<void> {
    if (!currentTab.value) {
      throw new Error('当前没有活跃的会话')
    }
    await currentTab.value.setModel(model)
  }

  /**
   * 设置权限模式（通过当前 Tab）
   */
  async function setPermissionMode(mode: RpcPermissionMode): Promise<void> {
    if (!currentTab.value) {
      throw new Error('当前没有活跃的会话')
    }
    await currentTab.value.setPermissionMode(mode)
  }

  /**
   * 仅更新本地 UI 的权限模式状态，不调用后端 RPC
   * 用于当 SDK 会自行处理模式切换的场景（如权限建议中的 setMode）
   */
  function setLocalPermissionMode(mode: RpcPermissionMode): void {
    if (!currentTab.value) {
      console.warn('当前没有活跃的会话，无法设置本地权限模式')
      return
    }
    currentTab.value.setLocalPermissionMode(mode)
  }

  /**
   * 更新当前会话设置
   */
  function updateCurrentSessionSettings(settings: Partial<{
    modelId: string
    thinkingEnabled: boolean
    permissionMode: RpcPermissionMode
    skipPermissions: boolean
  }>): void {
    if (!currentTab.value) return

    if (settings.modelId !== undefined) currentTab.value.modelId.value = settings.modelId
    if (settings.thinkingEnabled !== undefined) currentTab.value.thinkingEnabled.value = settings.thinkingEnabled
    if (settings.permissionMode !== undefined) currentTab.value.permissionMode.value = settings.permissionMode
    if (settings.skipPermissions !== undefined) currentTab.value.skipPermissions.value = settings.skipPermissions
  }

  /**
   * 重命名会话
   */
  async function renameSession(id: string, newName: string): Promise<boolean> {
    const tab = tabs.value.find(t => t.tabId === id || t.sessionId.value === id)
    if (tab) {
      tab.rename(newName)
      return true
    }
    return false
  }

  // ========== 权限相关（通过当前 Tab） ==========

  /**
   * 获取当前待处理的问题
   */
  function getCurrentPendingQuestions() {
    return currentTab.value?.permissions.pendingQuestionList.value ?? []
  }

  /**
   * 回答问题
   */
  function answerQuestion(questionId: string, answers: Record<string, string>): boolean {
    return currentTab.value?.permissions.answerQuestion(questionId, answers) ?? false
  }

  /**
   * 取消问题
   */
  function cancelQuestion(questionId: string): boolean {
    return currentTab.value?.permissions.cancelQuestion(questionId) ?? false
  }

  /**
   * 获取当前待处理的权限请求
   */
  function getCurrentPendingPermissions() {
    return currentTab.value?.permissions.pendingPermissionList.value ?? []
  }

  /**
   * 获取工具调用对应的权限请求
   */
  function getPermissionForToolCall(toolCallId: string) {
    return currentTab.value?.permissions.getPermissionForToolCall(toolCallId)
  }

  /**
   * 响应权限请求
   */
  function respondPermission(permissionId: string, response: any): boolean {
    return currentTab.value?.permissions.respondPermission(permissionId, response) ?? false
  }

  /**
   * 取消权限请求
   */
  function cancelPermission(permissionId: string): boolean {
    return currentTab.value?.permissions.cancelPermission(permissionId) ?? false
  }

  // ========== 统计相关（通过当前 Tab） ==========

  /**
   * 开始请求追踪
   */
  function startRequestTracking(_sessionId: string, userMessageId: string, streamingMessageId?: string): void {
    currentTab.value?.stats.startRequestTracking(userMessageId)
    if (streamingMessageId) {
      currentTab.value?.stats.setStreamingMessageId(streamingMessageId)
    }
  }

  /**
   * 添加 Token 使用量
   */
  function addTokenUsage(_sessionId: string, inputTokens: number, outputTokens: number): void {
    currentTab.value?.stats.addTokenUsage(inputTokens, outputTokens)
  }

  /**
   * 获取请求统计
   */
  function getRequestStats(_sessionId: string) {
    return currentTab.value?.stats.getCurrentTracker()
  }

  // ========== 消息队列相关（通过当前 Tab） ==========

  /**
   * 消息队列
   */
  const messageQueue = computed(() => currentTab.value?.messageQueue.value ?? [])

  /**
   * 编辑队列消息
   */
  function editQueueMessage(id: string) {
    return currentTab.value?.editQueueMessage(id) ?? null
  }

  /**
   * 从队列移除消息
   */
  function removeFromQueue(id: string): boolean {
    return currentTab.value?.removeFromQueue(id) ?? false
  }

  /**
   * 清空队列
   */
  function clearQueue(): void {
    currentTab.value?.clearQueue()
  }

  /**
   * 顶部滚动加载更多历史
   */
  async function loadMoreHistory(): Promise<void> {
    if (!currentTab.value) return
    await currentTab.value.loadMoreHistory()
  }

  // ========== 导出 ==========

  return {
    // Tab 管理
    tabs,
    currentTab,
    currentTabId,
    currentSessionId,
    activeTabs,
    tabCount,
    hasTabs,
    loading,

    // 当前 Tab 状态
    currentConnectionStatus,
    currentIsGenerating,
    currentIsLoadingHistory,
    currentHasMoreHistory,
    currentHistoryState,
    currentDisplayItems,
    currentMessages,
    currentSessionSettings,
    currentLastError,
    clearCurrentError,
    notifyDisplayItemsChanged,

    // Tab 操作
    createTab,
    createSession,
    startNewSession,
    resumeSession,
    switchTab,
    switchSession,
    closeTab,
    deleteSession,
    updateTabOrder,
    renameSession,
    resetCurrentTab,

    // 消息操作（向后兼容）
    sendMessage,
    enqueueMessage,
    interrupt,
    runInBackground,
    setModel,
    setPermissionMode,
    setLocalPermissionMode,
    updateCurrentSessionSettings,

    // 权限相关
    getCurrentPendingQuestions,
    answerQuestion,
    cancelQuestion,
    getCurrentPendingPermissions,
    getPermissionForToolCall,
    respondPermission,
    cancelPermission,

    // 统计相关
    startRequestTracking,
    addTokenUsage,
    getRequestStats,

    // 消息队列
    messageQueue,
    editQueueMessage,
    removeFromQueue,
    clearQueue,

    // 历史分页
    loadMoreHistory
  }
})
