/**
 * Tab 会话管理 Composable（核心入口）
 *
 * 每个 Tab 实例独立持有：
 * - 自己的状态
 * - 自己的连接
 * - 自己的消息处理器
 *
 * 组合其他 Composables:
 * - useSessionTools: 工具调用管理
 * - useSessionStats: 统计管理
 * - useSessionPermissions: 权限管理
 * - useSessionMessages: 消息处理
 */

import { ref, reactive, computed } from 'vue'
import { aiAgentService } from '@/services/aiAgentService'
import type { ConnectOptions } from '@/services/aiAgentService'
import type { ContentBlock } from '@/types/message'
import { ConnectionStatus } from '@/types/display'
import type { RpcCapabilities, RpcPermissionMode, RpcMessage, RpcStreamEvent, RpcResultMessage } from '@/types/rpc'
import {
  isStreamEvent as isRpcStreamEvent,
  isResultMessage as isRpcResultMessage,
  isAssistantMessage as isRpcAssistantMessage,
  isUserMessage as isRpcUserMessage
} from '@/types/rpc'
import { mapRpcMessageToMessage } from '@/utils/rpcMappers'
import { useSessionTools, type SessionToolsInstance } from './useSessionTools'
import { useSessionStats, type SessionStatsInstance } from './useSessionStats'
import { useSessionPermissions, type SessionPermissionsInstance } from './useSessionPermissions'
import { useSessionMessages, type SessionMessagesInstance } from './useSessionMessages'
import { loggers } from '@/utils/logger'
import type { PendingPermissionRequest, PendingUserQuestion, PermissionResponse } from '@/types/permission'

const log = loggers.session

/**
 * UI 状态（用于切换会话时保存/恢复）
 */
export interface UIState {
  inputText: string
  contexts: any[]
  scrollPosition: number
}

/**
 * Tab 基础信息
 */
export interface TabInfo {
  tabId: string
  sessionId: string | null
  name: string
  createdAt: number
  updatedAt: number
  lastActiveAt: number
  order: number
}

/**
 * 连接配置
 */
export interface TabConnectOptions {
  model?: string
  thinkingEnabled?: boolean
  permissionMode?: RpcPermissionMode
  skipPermissions?: boolean
  continueConversation?: boolean
  resume?: string
}

/**
 * RPC 消息规范化结果类型
 */
export type NormalizedRpcMessage =
  | { kind: 'message'; data: any }
  | { kind: 'stream_event'; data: RpcStreamEvent }
  | { kind: 'result'; data: RpcResultMessage }

/**
 * Tab 会话管理 Composable
 *
 * 使用方式：
 * ```typescript
 * const tab = useSessionTab()
 * await tab.connect({ model: 'opus' })
 * tab.sendMessage([{ type: 'text', text: 'Hello' }])
 * ```
 */
export function useSessionTab(initialOrder: number = 0) {
  // ========== 组合其他 Composables ==========
  const tools: SessionToolsInstance = useSessionTools()
  const stats: SessionStatsInstance = useSessionStats()
  const permissions: SessionPermissionsInstance = useSessionPermissions()
  const messagesHandler: SessionMessagesInstance = useSessionMessages(tools, stats)

  // ========== Tab 基础信息 ==========
  const tabId = `tab-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`
  const sessionId = ref<string | null>(null)
  const name = ref('新会话')
  const createdAt = Date.now()
  const updatedAt = ref(createdAt)
  const lastActiveAt = ref(createdAt)
  const order = ref(initialOrder)

  // ========== 连接状态 ==========
  // 使用 reactive 对象而不是 ref，以便在 shallowRef 容器中也能被追踪
  const connectionState = reactive({
    status: ConnectionStatus.DISCONNECTED as ConnectionStatus,
    capabilities: null as RpcCapabilities | null,
    lastError: null as string | null
  })

  // ========== 连接设置（连接时确定，切换需要重连）==========
  const modelId = ref<string | null>(null)
  const thinkingEnabled = ref(true)
  const permissionMode = ref<RpcPermissionMode>('default')
  const skipPermissions = ref(false)

  // ========== UI 状态 ==========
  const uiState = reactive<UIState>({
    inputText: '',
    contexts: [],
    scrollPosition: 0
  })

  // ========== 计算属性 ==========

  /**
   * 是否已连接
   */
  const isConnected = computed(() => connectionState.status === ConnectionStatus.CONNECTED)

  /**
   * 是否正在连接
   */
  const isConnecting = computed(() => connectionState.status === ConnectionStatus.CONNECTING)

  /**
   * 是否有错误
   */
  const hasError = computed(() => connectionState.status === ConnectionStatus.ERROR)

  /**
   * 是否正在生成
   */
  const isGenerating = computed(() => messagesHandler.isGenerating.value)

  /**
   * Tab 信息
   */
  const tabInfo = computed<TabInfo>(() => ({
    tabId,
    sessionId: sessionId.value,
    name: name.value,
    createdAt,
    updatedAt: updatedAt.value,
    lastActiveAt: lastActiveAt.value,
    order: order.value
  }))

  // ========== 消息规范化 ==========

  /**
   * 规范化 RPC 消息
   */
  function normalizeRpcMessage(raw: RpcMessage): NormalizedRpcMessage | null {
    // 1. 先尝试识别 stream_event
    if (isRpcStreamEvent(raw)) {
      return { kind: 'stream_event', data: raw }
    }

    // 2. 尝试识别 result
    if (isRpcResultMessage(raw)) {
      return { kind: 'result', data: raw }
    }

    // 3. 尝试识别 assistant / user 消息
    if (isRpcAssistantMessage(raw) || isRpcUserMessage(raw)) {
      const mapped = mapRpcMessageToMessage(raw)
      if (!mapped) return null
      return { kind: 'message', data: mapped }
    }

    log.warn('[normalizeRpcMessage] 未识别的消息类型:', raw.type, raw)
    return null
  }

  /**
   * 处理来自后端的消息
   */
  function handleMessage(rawMessage: RpcMessage): void {
    const normalized = normalizeRpcMessage(rawMessage)
    if (!normalized) return

    // 生成状态门控
    if (!messagesHandler.isGenerating.value) {
      const isInterruptResult =
        normalized.kind === 'result' &&
        (normalized.data as any)?.subtype === 'interrupted'

      if (!isInterruptResult) {
        log.debug('[handleMessage] isGenerating=false，忽略消息:', normalized.kind)
        return
      }
    }

    // 根据消息类型分发处理
    switch (normalized.kind) {
      case 'stream_event':
        messagesHandler.handleStreamEvent(normalized.data)
        break

      case 'result':
        messagesHandler.handleResultMessage(normalized.data)
        break

      case 'message':
        messagesHandler.handleNormalMessage(normalized.data)
        break
    }

    // 更新活跃时间
    touch()
  }

  // ========== 连接管理 ==========

  // 重连配置
  const MAX_RECONNECT_ATTEMPTS = 3
  const RECONNECT_DELAY = 2000 // 2秒
  let reconnectAttempts = 0
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  /**
   * 连接到后端
   */
  async function connect(options: TabConnectOptions = {}): Promise<void> {
    if (connectionState.status === ConnectionStatus.CONNECTING) {
      log.warn(`[Tab ${tabId}] 正在连接中，请勿重复连接`)
      return
    }

    connectionState.status = ConnectionStatus.CONNECTING
    connectionState.lastError = null

    // 保存设置
    if (options.model) modelId.value = options.model
    if (options.thinkingEnabled !== undefined) thinkingEnabled.value = options.thinkingEnabled
    if (options.permissionMode) permissionMode.value = options.permissionMode
    if (options.skipPermissions !== undefined) skipPermissions.value = options.skipPermissions

    try {
      const connectOptions: ConnectOptions = {
        includePartialMessages: true,
        allowDangerouslySkipPermissions: true,
        model: modelId.value || undefined,
        thinkingEnabled: thinkingEnabled.value,
        permissionMode: permissionMode.value,
        dangerouslySkipPermissions: skipPermissions.value,
        continueConversation: options.continueConversation,
        resume: options.resume
      }

      const result = await aiAgentService.connect(connectOptions, handleMessage)

      sessionId.value = result.sessionId
      connectionState.capabilities = result.capabilities
      connectionState.status = ConnectionStatus.CONNECTED
      connectionState.lastError = null

      // 连接成功，重置重连计数
      reconnectAttempts = 0

      // 设置发送消息函数
      messagesHandler.setSendMessageFn(async (content: ContentBlock[]) => {
        if (!sessionId.value) {
          throw new Error('会话未连接')
        }
        await aiAgentService.sendMessageWithContent(
          sessionId.value,
          content as any
        )
      })

      // 注册双向 RPC 处理器
      registerRpcHandlers()

      log.info(`[Tab ${tabId}] 连接成功: sessionId=${result.sessionId}`)
    } catch (error) {
      connectionState.status = ConnectionStatus.ERROR
      connectionState.lastError = error instanceof Error ? error.message : String(error)
      log.error(`[Tab ${tabId}] 连接失败:`, error)

      // 自动重连
      scheduleReconnect(options)
    }
  }

  /**
   * 安排自动重连
   */
  function scheduleReconnect(options: TabConnectOptions): void {
    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      log.warn(`[Tab ${tabId}] 已达到最大重连次数 (${MAX_RECONNECT_ATTEMPTS})，停止重连`)
      return
    }

    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
    }

    reconnectAttempts++
    const delay = RECONNECT_DELAY * reconnectAttempts // 逐渐增加延迟

    log.info(`[Tab ${tabId}] 将在 ${delay}ms 后尝试第 ${reconnectAttempts} 次重连`)

    reconnectTimer = setTimeout(async () => {
      reconnectTimer = null
      connectionState.status = ConnectionStatus.DISCONNECTED // 重置状态以允许重连
      await connect(options)
    }, delay)
  }

  /**
   * 取消自动重连
   */
  function cancelReconnect(): void {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    reconnectAttempts = 0
  }

  /**
   * 断开连接
   */
  async function disconnect(): Promise<void> {
    // 取消自动重连
    cancelReconnect()

    if (sessionId.value) {
      try {
        await aiAgentService.disconnect(sessionId.value)
      } catch (error) {
        log.warn(`[Tab ${tabId}] 断开连接失败:`, error)
      }
    }

    sessionId.value = null
    connectionState.status = ConnectionStatus.DISCONNECTED

    // 取消所有待处理的权限和问题
    permissions.cancelAllPermissions('Tab disconnected')
    permissions.cancelAllQuestions('Tab disconnected')

    log.info(`[Tab ${tabId}] 已断开连接`)
  }

  /**
   * 重新连接
   */
  async function reconnect(options?: TabConnectOptions): Promise<void> {
    await disconnect()
    await connect(options || {
      model: modelId.value || undefined,
      thinkingEnabled: thinkingEnabled.value,
      permissionMode: permissionMode.value,
      skipPermissions: skipPermissions.value
    })
  }

  // ========== RPC 处理器注册 ==========

  /**
   * 注册双向 RPC 处理器
   */
  function registerRpcHandlers(): void {
    if (!sessionId.value) return

    // 注册 AskUserQuestion 处理器
    aiAgentService.register(sessionId.value, 'AskUserQuestion', async (params) => {
      log.info(`[Tab ${tabId}] 收到 AskUserQuestion 请求:`, params)

      return new Promise((resolve, reject) => {
        const questionId = `question-${Date.now()}`

        const question: Omit<PendingUserQuestion, 'createdAt'> = {
          id: questionId,
          sessionId: sessionId.value!,
          questions: params.questions || [],
          resolve: (answers: Record<string, string>) => {
            resolve({ answers })
          },
          reject
        }

        permissions.addUserQuestion(question)
      })
    })

    // 注册 RequestPermission 处理器
    aiAgentService.register(sessionId.value, 'RequestPermission', async (params) => {
      log.info(`[Tab ${tabId}] 收到 RequestPermission 请求:`, params)

      return new Promise((resolve, reject) => {
        const permissionId = `permission-${Date.now()}`

        // 查找匹配的工具调用 ID
        let matchedToolCallId: string | undefined
        if (params.toolUseId) {
          matchedToolCallId = params.toolUseId
        }

        const request: Omit<PendingPermissionRequest, 'createdAt'> = {
          id: permissionId,
          sessionId: sessionId.value!,
          toolName: params.toolName,
          input: params.input || {},
          matchedToolCallId,
          permissionSuggestions: params.permissionSuggestions,
          resolve: (response: PermissionResponse) => {
            resolve(response)
          },
          reject
        }

        permissions.addPermissionRequest(request)
      })
    })

    log.debug(`[Tab ${tabId}] RPC 处理器已注册`)
  }

  // ========== 消息发送 ==========

  /**
   * 发送消息
   */
  function sendMessage(message: { contexts: any[]; contents: ContentBlock[] }): void {
    messagesHandler.enqueueMessage(message)
    touch()
  }

  /**
   * 发送纯文本消息
   */
  function sendTextMessage(text: string): void {
    sendMessage({
      contexts: [],
      contents: [{ type: 'text', text }]
    })
  }

  /**
   * 中断当前操作
   */
  async function interrupt(): Promise<void> {
    if (!sessionId.value) {
      throw new Error('会话未连接')
    }

    await aiAgentService.interrupt(sessionId.value)
    messagesHandler.clearQueue()
    log.info(`[Tab ${tabId}] 中断请求已发送`)
  }

  // ========== 设置管理 ==========

  /**
   * 设置模型（需要重连才能生效）
   */
  async function setModel(model: string): Promise<void> {
    if (!sessionId.value) {
      modelId.value = model
      return
    }

    await aiAgentService.setModel(sessionId.value, model)
    modelId.value = model
    log.info(`[Tab ${tabId}] 模型已设置: ${model}`)
  }

  /**
   * 设置权限模式
   */
  async function setPermissionModeValue(mode: RpcPermissionMode): Promise<void> {
    if (!sessionId.value) {
      permissionMode.value = mode
      return
    }

    await aiAgentService.setPermissionMode(sessionId.value, mode)
    permissionMode.value = mode
    log.info(`[Tab ${tabId}] 权限模式已设置: ${mode}`)
  }

  // ========== 辅助方法 ==========

  /**
   * 更新活跃时间
   */
  function touch(): void {
    const now = Date.now()
    updatedAt.value = now
    lastActiveAt.value = now
  }

  /**
   * 重命名
   */
  function rename(newName: string): void {
    name.value = newName
    touch()
  }

  /**
   * 设置排序
   */
  function setOrder(newOrder: number): void {
    order.value = newOrder
  }

  /**
   * 保存 UI 状态
   */
  function saveUiState(state: Partial<UIState>): void {
    if (state.inputText !== undefined) uiState.inputText = state.inputText
    if (state.contexts !== undefined) uiState.contexts = state.contexts
    if (state.scrollPosition !== undefined) uiState.scrollPosition = state.scrollPosition
  }

  /**
   * 重置 Tab
   */
  function reset(): void {
    // 重置所有子 composables
    tools.reset()
    stats.reset()
    permissions.reset()
    messagesHandler.reset()

    // 重置 UI 状态
    uiState.inputText = ''
    uiState.contexts = []
    uiState.scrollPosition = 0

    // 重置错误状态
    connectionState.lastError = null

    log.debug(`[Tab ${tabId}] 已重置`)
  }

  // ========== 导出 ==========

  return {
    // Tab 标识
    tabId,

    // 基础信息（响应式）
    sessionId,
    name,
    order,
    updatedAt,
    lastActiveAt,

    // 连接状态（reactive 对象，支持响应式追踪）
    connectionState,
    // 为了向后兼容，提供直接访问的 getter
    get connectionStatus() { return connectionState.status },
    get capabilities() { return connectionState.capabilities },
    get lastError() { return connectionState.lastError },

    // 连接设置
    modelId,
    thinkingEnabled,
    permissionMode,
    skipPermissions,

    // UI 状态
    uiState,

    // 计算属性
    isConnected,
    isConnecting,
    hasError,
    isGenerating,
    tabInfo,

    // 子 composables（暴露以便直接访问）
    tools,
    stats,
    permissions,

    // 消息相关（直接暴露 messagesHandler 的状态）
    messages: messagesHandler.messages,
    displayItems: messagesHandler.displayItems,
    messageQueue: messagesHandler.messageQueue,

    // 连接管理
    connect,
    disconnect,
    reconnect,

    // 消息发送
    sendMessage,
    sendTextMessage,
    interrupt,

    // 队列管理
    editQueueMessage: messagesHandler.editQueueMessage,
    removeFromQueue: messagesHandler.removeFromQueue,
    clearQueue: messagesHandler.clearQueue,

    // 设置管理
    setModel,
    setPermissionMode: setPermissionModeValue,

    // 辅助方法
    touch,
    rename,
    setOrder,
    saveUiState,
    reset
  }
}

/**
 * useSessionTab 返回类型
 */
export type SessionTabInstance = ReturnType<typeof useSessionTab>
