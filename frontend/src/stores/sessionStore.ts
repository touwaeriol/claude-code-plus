import { ref, computed, reactive } from 'vue'
import { defineStore } from 'pinia'
import { i18n } from '@/i18n'
import { aiAgentService } from '@/services/aiAgentService'
import type { ConnectOptions } from '@/services/aiAgentService'
import type { AgentStreamEvent } from '@/services/AiAgentSession'
import type { Message, ContentBlock, ToolUseBlock, ToolResultBlock, ToolUseContent } from '@/types/message'
import type { SessionState, PendingMessage } from '@/types/session'
import { convertToDisplayItems, convertMessageToDisplayItems, createToolCall } from '@/utils/displayItemConverter'
import { ConnectionStatus, ToolCallStatus } from '@/types/display'
import type { DisplayItem, AssistantText, ThinkingContent } from '@/types/display'
import { isUserMessage as isDisplayUserMessage } from '@/types/display'
import { isToolUseBlock, isTextBlock } from '@/utils/contentBlockUtils'
import type { TextBlock } from '@/types/message'
import { loggers } from '@/utils/logger'
import { ideService } from '@/services/ideaBridge'
import { ideaBridge } from '@/services/ideaBridge'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'
import type { ClaudeReadToolCall, ClaudeWriteToolCall, ClaudeEditToolCall, ClaudeMultiEditToolCall, ToolCall } from '@/types/display'
import { buildUserMessageContent } from '@/utils/userMessageBuilder'
import { MODEL_CAPABILITIES, BaseModel } from '@/constants/models'
import type { RpcPermissionMode } from '@/types/rpc'
import type {
  PendingPermissionRequest,
  PendingUserQuestion,
  PermissionUpdate,
  PermissionResponse,
  SessionPermissionRule,
  PermissionBehavior
} from '@/types/permission'
import {
  isAssistantMessage as isRpcAssistantMessage,
  isResultMessage as isRpcResultMessage,
  isStreamEvent as isRpcStreamEvent,
  isUserMessage as isRpcUserMessage
} from '@/types/rpc'
import type { RpcMessage, RpcResultMessage, RpcStreamEvent } from '@/types/rpc'
import { mapRpcContentBlock, mapRpcMessageToMessage } from '@/utils/rpcMappers'

const log = loggers.session

/**
 * 会话信息（向后兼容）
 * @deprecated 使用 SessionState 代替
 */
export interface Session {
  id: string
  name: string
  createdAt: number
  updatedAt: number
  model?: string
}

/**
 * RPC 消息规范化结果类型
 */
export type NormalizedRpcMessage =
  | { kind: 'message'; data: Message }
  | { kind: 'stream_event'; data: RpcStreamEvent }
  | { kind: 'result'; data: RpcResultMessage }

/**
 * 工具调用状态（向后兼容）
 * @deprecated 使用 ToolCall 代替
 */
export interface ToolCallState {
  id: string
  name: string
  status: 'running' | 'success' | 'failed'
  result?: any
  startTime: number
  endTime?: number
}

// 重新导出 ConnectionStatus
export { ConnectionStatus } from '@/types/display'

export const useSessionStore = defineStore('session', () => {
  // 新的状态管理：使用 Map<sessionId, SessionState>
  const sessions = reactive(new Map<string, SessionState>())
  const currentSessionId = ref<string | null>(null)
  const loading = ref(false)

  // 消息队列（待发送消息）
  const messageQueue = ref<any[]>([])

  // 向后兼容：保留旧的接口
  const currentModelId = ref<string | null>(null)
  const sessionModelIds = ref<Map<string, string>>(new Map())
  const connectionStatuses = ref<Map<string, ConnectionStatus>>(new Map())
  const toolCallsMap = ref<Map<string, ToolCallState>>(new Map())
  const externalSessionIndex = reactive(new Map<string, string>())
  
  // 存储每个工具调用块的累积 JSON 字符串（用于 input_json_delta 增量更新）
  const toolInputJsonAccumulator = reactive(new Map<string, string>())

  // 记录上次实际应用到后端的设置（用于延迟同步）
  const lastAppliedSettings = ref<{
    modelId: string
    thinkingEnabled: boolean
    permissionMode: string
    skipPermissions: boolean
  } | null>(null)

  // 全局 Map 已迁移到 SessionState 对象中
  // - requestTracker -> session.requestTracker
  // - pendingQuestions -> session.pendingQuestions
  // - pendingPermissions -> session.pendingPermissions
  // - sessionPermissionRules -> session.permissionRules
  // - sessionPermissionDirectories -> session.permissionDirectories

  function buildConnectOptions(overrides: Partial<ConnectOptions> = {}): ConnectOptions {
    // dangerouslySkipPermissions 由调用方通过 overrides 传入，不再硬编码
    return {
      includePartialMessages: true,
      allowDangerouslySkipPermissions: true,
      ...overrides
    }
  }

  function createSessionState(
    sessionId: string,
    sessionName: string,
    settings: {
      modelId: string | null
      thinkingEnabled: boolean
      permissionMode: RpcPermissionMode
      skipPermissions: boolean
    }
  ): SessionState {
    const now = Date.now()
    // 计算新的order：当前最大order + 1，如果没有session则从0开始
    const maxOrder = sessions.size > 0
      ? Math.max(...Array.from(sessions.values()).map(s => s.order))
      : -1
    return reactive({
      id: sessionId,
      name: sessionName,
      createdAt: now,
      updatedAt: now,
      lastActiveAt: now,
      order: maxOrder + 1,  // 新创建的session排在最后
      messages: [],
      displayItems: [],
      pendingToolCalls: new Map(),
      connectionStatus: ConnectionStatus.CONNECTED,
      modelId: settings.modelId,
      thinkingEnabled: settings.thinkingEnabled,
      permissionMode: settings.permissionMode,
      skipPermissions: settings.skipPermissions,
      session: null,
      capabilities: null,
      isGenerating: false,
      uiState: {
        inputText: '',
        contexts: [],
        scrollPosition: 0
      },
      toolInputJsonAccumulator: new Map(),
      lastError: null,
      // 会话级状态（原来是全局 Map）
      pendingQuestions: new Map(),
      pendingPermissions: new Map(),
      permissionRules: [],
      permissionDirectories: [],
      requestTracker: null
    })
  }

  // 当前会话
  const currentSession = computed(() => {
    if (!currentSessionId.value) return null
    return sessions.get(currentSessionId.value) || null
  })

  // 当前会话的消息（向后兼容）
  const currentMessages = computed(() => {
    const session = currentSession.value
    return session ? session.messages : []
  })

  // 当前会话的 displayItems
  const currentDisplayItems = computed(() => {
    const session = currentSession.value
    return session ? session.displayItems : []
  })

  // 当前会话的连接状态
  const currentConnectionStatus = computed(() => {
    const session = currentSession.value
    return session ? session.connectionStatus : ConnectionStatus.DISCONNECTED
  })

  // 当前会话的设置（响应式 getter）
  const currentSessionSettings = computed(() => {
    const session = currentSession.value
    if (!session) return null
    return {
      modelId: session.modelId,
      thinkingEnabled: session.thinkingEnabled,
      permissionMode: session.permissionMode,
      skipPermissions: session.skipPermissions
    }
  })

  /**
   * 更新当前会话的设置（不触发后端同步，延迟到发送消息时）
   */
  function updateCurrentSessionSettings(settings: Partial<{
    modelId: string
    thinkingEnabled: boolean
    permissionMode: RpcPermissionMode
    skipPermissions: boolean
  }>) {
    const session = currentSession.value
    if (!session) return

    if (settings.modelId !== undefined) session.modelId = settings.modelId
    if (settings.thinkingEnabled !== undefined) session.thinkingEnabled = settings.thinkingEnabled
    if (settings.permissionMode !== undefined) session.permissionMode = settings.permissionMode
    if (settings.skipPermissions !== undefined) session.skipPermissions = settings.skipPermissions

    log.debug('[updateCurrentSessionSettings] 更新会话设置:', settings)
  }

  // 活跃的会话（显示在 Tab 上）
  // 显示所有已创建的会话，按order排序（支持手动拖拽调整顺序）
  const activeTabs = computed(() => {
    return Array.from(sessions.values())
      .sort((a, b) => a.order - b.order)
  })

  // 所有会话列表（按创建时间倒序）
  const allSessions = computed(() => {
    return Array.from(sessions.values())
      .sort((a, b) => b.lastActiveAt - a.lastActiveAt)
  })

  function getSessionState(sessionId: string | null | undefined): SessionState | null {
    if (!sessionId) return null
    return (sessions.get(sessionId) as SessionState | undefined) || null
  }

  function resolveSessionIdentifier(externalId: string | null | undefined): string | null {
    if (!externalId) return null
    if (sessions.has(externalId)) {
      return externalId
    }
    return externalSessionIndex.get(externalId) ?? null
  }

  function linkExternalSessionId(externalId: string, internalId: string) {
    if (!externalId) return
    externalSessionIndex.set(externalId, internalId)
  }

  function unlinkExternalSessionId(internalId: string) {
    for (const [externalId, mappedId] of externalSessionIndex.entries()) {
      if (mappedId === internalId) {
        externalSessionIndex.delete(externalId)
      }
    }
  }

  function touchSession(sessionId: string) {
    const session = sessions.get(sessionId)
    if (!session) return
    const now = Date.now()
    session.updatedAt = now
    session.lastActiveAt = now
  }

  function setSessionGenerating(sessionId: string, generating: boolean) {
    const session = sessions.get(sessionId)
    if (!session) return
    session.isGenerating = generating
    touchSession(sessionId)
  }

  // 会话数据由后端 SDK 管理，前端不需要持久化

  // 默认会话设置常量
  const DEFAULT_SESSION_SETTINGS: {
    modelId: string
    thinkingEnabled: boolean
    permissionMode: RpcPermissionMode
    skipPermissions: boolean
  } = {
    modelId: MODEL_CAPABILITIES[BaseModel.OPUS_45].modelId,
    thinkingEnabled: MODEL_CAPABILITIES[BaseModel.OPUS_45].defaultThinkingEnabled,
    permissionMode: 'default' as RpcPermissionMode,
    skipPermissions: false  // 默认不跳过权限检查，需要用户授权
  }

  /**
   * 创建新会话
   */
  async function createSession(name?: string) {
    try {
      log.info('创建新会话...')

      // 从当前会话复制设置（如果存在），否则使用默认值
      const currentSettings = currentSessionSettings.value
      const initialSettings = currentSettings ? {
        modelId: currentSettings.modelId || DEFAULT_SESSION_SETTINGS.modelId,
        thinkingEnabled: currentSettings.thinkingEnabled,
        permissionMode: currentSettings.permissionMode,
        skipPermissions: currentSettings.skipPermissions
      } : DEFAULT_SESSION_SETTINGS

      const options = buildConnectOptions({
        model: initialSettings.modelId,
        thinkingEnabled: initialSettings.thinkingEnabled,
        permissionMode: initialSettings.permissionMode,
        dangerouslySkipPermissions: initialSettings.skipPermissions
      })

      // 设置连接状态
      connectionStatuses.value.set('pending', ConnectionStatus.CONNECTING)

      // 使用 aiAgentService 创建会话
      const connectResult = await aiAgentService.connect(options, (rawMessage: RpcMessage) => {
        const normalized = normalizeRpcMessage(rawMessage)
        if (normalized) {
          handleMessage(connectResult.sessionId, normalized)
        }
      })
      const sessionId = connectResult.sessionId

      // 使用短时间格式：HH:mm
      const shortTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
      const newSessionState = createSessionState(
        sessionId,
        name || i18n.global.t('session.defaultName', { time: shortTime }),
        initialSettings
      )

      // 添加到 sessions Map
      sessions.set(sessionId, newSessionState)

      // 设置连接状态（向后兼容）
      connectionStatuses.value.delete('pending')
      connectionStatuses.value.set(sessionId, ConnectionStatus.CONNECTED)

      // 切换到新会话
      currentSessionId.value = sessionId
      sessionModelIds.value.set(sessionId, initialSettings.modelId)
      currentModelId.value = initialSettings.modelId

      // 初始化 lastAppliedSettings
      lastAppliedSettings.value = {
        modelId: initialSettings.modelId,
        thinkingEnabled: initialSettings.thinkingEnabled,
        permissionMode: initialSettings.permissionMode,
        skipPermissions: initialSettings.skipPermissions
      }

      // 注册双向 RPC 处理器
      registerAskUserQuestionHandler(sessionId)
      registerPermissionHandler(sessionId)

      log.info(`会话已创建: ${sessionId}, model=${initialSettings.modelId}, thinking=${initialSettings.thinkingEnabled}`)
      return newSessionState
    } catch (error) {
      log.error('创建会话异常:', error)
      connectionStatuses.value.delete('pending')
      return null
    }
  }

  async function startNewSession(name?: string) {
    return createSession(name)
  }

  async function resumeSession(externalSessionId: string, name?: string) {
    if (!externalSessionId) return null

    const existingInternalId = resolveSessionIdentifier(externalSessionId)
    if (existingInternalId) {
      await switchSession(existingInternalId)
      return getSessionState(existingInternalId)
    }

    try {
      log.info(`恢复历史会话: ${externalSessionId}`)

      // 从当前会话复制设置（如果存在），否则使用默认值
      const currentSettings = currentSessionSettings.value
      const initialSettings = currentSettings ? {
        modelId: currentSettings.modelId || DEFAULT_SESSION_SETTINGS.modelId,
        thinkingEnabled: currentSettings.thinkingEnabled,
        permissionMode: currentSettings.permissionMode,
        skipPermissions: currentSettings.skipPermissions
      } : DEFAULT_SESSION_SETTINGS

      const options = buildConnectOptions({
        model: initialSettings.modelId,
        thinkingEnabled: initialSettings.thinkingEnabled,
        permissionMode: initialSettings.permissionMode,
        dangerouslySkipPermissions: initialSettings.skipPermissions,
        continueConversation: true,
        resume: externalSessionId
      })

      connectionStatuses.value.set('pending', ConnectionStatus.CONNECTING)
      const connectResult = await aiAgentService.connect(options, (rawMessage: RpcMessage) => {
        const normalized = normalizeRpcMessage(rawMessage)
        if (normalized) {
          handleMessage(connectResult.sessionId, normalized)
        }
      })
      const sessionId = connectResult.sessionId

      const resumeLabel = externalSessionId.slice(-8) || externalSessionId
      const resumedSessionState = createSessionState(
        sessionId,
        name || `历史会话 ${resumeLabel}`,
        initialSettings
      )

      sessions.set(sessionId, resumedSessionState)
      connectionStatuses.value.delete('pending')
      connectionStatuses.value.set(sessionId, ConnectionStatus.CONNECTED)

      sessionModelIds.value.set(sessionId, initialSettings.modelId)
      currentModelId.value = initialSettings.modelId
      currentSessionId.value = sessionId

      // 初始化 lastAppliedSettings
      lastAppliedSettings.value = {
        modelId: initialSettings.modelId,
        thinkingEnabled: initialSettings.thinkingEnabled,
        permissionMode: initialSettings.permissionMode,
        skipPermissions: initialSettings.skipPermissions
      }

      // 注册双向 RPC 处理器
      registerAskUserQuestionHandler(sessionId)
      registerPermissionHandler(sessionId)

      linkExternalSessionId(externalSessionId, sessionId)
      log.info(`历史会话已恢复: ${sessionId}, model=${initialSettings.modelId}, thinking=${initialSettings.thinkingEnabled}`)
      return resumedSessionState
    } catch (error) {
      log.error('恢复会话异常:', error)
      connectionStatuses.value.delete('pending')
      return null
    }
  }

  /**
   * 切换会话
   */
  async function switchSession(sessionId: string) {
    try {
      const session = getSessionState(sessionId)
      if (!session) {
        log.warn(`会话不存在: ${sessionId}`)
        return false
      }

      currentSessionId.value = sessionId
      currentModelId.value = sessionModelIds.value.get(sessionId) ?? session.modelId ?? null
      touchSession(sessionId)

      log.debug(`已切换到会话: ${sessionId}`)
      return true
    } catch (error) {
      log.error('切换会话失败:', error)
      return false
    }
  }

  /**
   * 将 WebSocket 收到的原始消息转换为前端使用的规范化结构
   *
   * @returns NormalizedRpcMessage | null
   */
  function normalizeRpcMessage(raw: RpcMessage): NormalizedRpcMessage | null {
    log.debug('🔍 [normalizeRpcMessage] 收到原始消息:', {
      type: raw.type,
      provider: raw.provider,
      keys: Object.keys(raw as unknown as Record<string, unknown>),
      preview: JSON.stringify(raw).substring(0, 200)
    })

    if (isRpcStreamEvent(raw)) {
      log.debug('✅ [normalizeRpcMessage] 识别为 stream_event')
      return { kind: 'stream_event', data: raw }
    }

    if (isRpcResultMessage(raw)) {
      log.debug('✅ [normalizeRpcMessage] 识别为 result')
      return { kind: 'result', data: raw }
    }

    if (isRpcAssistantMessage(raw) || isRpcUserMessage(raw)) {
      const mapped = mapRpcMessageToMessage(raw)
      if (!mapped) {
        log.warn('⚠️ [normalizeRpcMessage] 消息内容为空，跳过', raw.type)
        return null
      }
      return { kind: 'message', data: mapped }
    }

    log.warn('⚠️ [normalizeRpcMessage] 未识别的消息类型:', raw.type, raw)
    return null
  }

  /**
   * 处理规范化后的消息
   */
  function handleMessage(sessionId: string, normalized: NormalizedRpcMessage) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      return
    }

    // 生成状态门控：仅当 isGenerating=true 时才处理 query 的响应
    // 例外：允许打断响应（result.subtype === 'interrupted'）穿透以便关闭生成状态并渲染系统提示
    if (!sessionState.isGenerating) {
      const isInterruptResult =
        normalized.kind === 'result' &&
        (normalized.data as any)?.subtype === 'interrupted'

      if (!isInterruptResult) {
        log.debug(`[handleMessage] isGenerating=false，忽略消息: kind=${normalized.kind}`)
        return
      }
    }

    // 根据消息类型分发处理
    switch (normalized.kind) {
      case 'stream_event':
        handleStreamEvent(sessionId, normalized.data)
        return

      case 'result':
        handleResultMessage(sessionId, normalized.data)
        return

      case 'message':
        handleNormalMessage(sessionId, sessionState, normalized.data)
        return
    }
  }

  /**
   * 处理普通消息（assistant/user 消息）
   *
   * 简化后的处理策略：
   * - stream_event 负责增量组装消息
   * - 完整消息与最新流式消息 ID 相同 → 忽略（流式已组装完成）
   * - 完整消息 ID 不同 → 添加新消息
   * - user 消息（包含 tool_result）：更新对应的 tool_use 状态
   */
  function handleNormalMessage(sessionId: string, sessionState: SessionState, message: Message) {
    // 🔍 打印完整消息内容用于调试
    log.debug('🔍 [handleNormalMessage]', {
      role: message.role,
      id: message.id,
      contentLength: message.content.length,
      contentTypes: message.content.map(b => b.type),
      isGenerating: sessionState.isGenerating
    })

    // 确保消息有 id 字段
    if (!message.id) {
      const streamingId = message.role === 'assistant' ? getCurrentStreamingMessageId(sessionId) : null
      message.id = streamingId || generateMessageId(message.role)
    }

    // ✅ 简化后的 assistant 消息处理逻辑
    if (message.role === 'assistant') {
      // 获取最新的流式消息
      const latestStreamingMessage = findStreamingAssistantMessage(sessionState)

      // 情况 1：存在流式消息且 ID 相同 → 忽略（流式已组装完成）
      if (latestStreamingMessage && latestStreamingMessage.id === message.id) {
        log.debug('⏭️ 忽略同 ID 的完整消息（流式已组装）', {
          messageId: message.id
        })
        return
      }

      // 情况 2：ID 不同或无流式消息 → 添加新消息
      log.debug('➕ 添加新 assistant 消息', {
        messageId: message.id,
        contentLength: message.content.length
      })
      addMessage(sessionId, message)
      touchSession(sessionId)
      return
    }

    // 处理 user 消息
    if (message.role === 'user') {
      // 检查消息内容类型
      const hasToolResult = message.content.some((block: ContentBlock) => block.type === 'tool_result')
      const hasToolUse = message.content.some((block: ContentBlock) => block.type === 'tool_use')
      const hasText = message.content.some((block: ContentBlock) => block.type === 'text')

      // 1. tool_result 消息：只更新工具状态，不添加新的 displayItem
      if (hasToolResult) {
        log.debug('📥 处理 tool_result 消息')
        processToolResults(sessionState, message.content)
        touchSession(sessionId)
        return
      }

      // 2. 纯 tool_use 的 user 消息：忽略
      // （tool_use 已经通过 stream_event 的 content_block_start 处理了）
      if (hasToolUse && !hasText) {
        log.debug('⏭️ 忽略纯 tool_use 的 user 消息')
        return
      }

      // 3. 文本类型的 user 消息（如中断提示）
      if (hasText) {
        // 检查是否是中断消息
        const textBlock = message.content.find((block: ContentBlock) => block.type === 'text') as { text?: string } | undefined
        const text = textBlock?.text || ''
        if (text.includes('[Request interrupted') || text.includes('interrupted')) {
          log.debug('⏭️ 忽略中断相关的 user 消息，由 result 消息处理')
          return
        }
      }

      // 4. 普通 user 消息：检查是否已存在（避免重复）
      const existingUserMsg = sessionState.messages.find(m => m.id === message.id)
      if (existingUserMsg) {
        log.debug('⏭️ 忽略重复的 user 消息:', message.id)
        return
      }

      // 添加新的 user 消息
      addMessage(sessionId, message)
      touchSession(sessionId)
    }
  }

  /**
   * 生成消息 ID
   */
  function generateMessageId(role: string): string {
    return `${role}-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`
  }

  /**
   * 处理 tool_result 内容块
   */
  function processToolResults(sessionState: SessionState, content: ContentBlock[]) {
    const toolResults = content.filter((block): block is ToolResultBlock => block.type === 'tool_result')

    let hasUpdates = false
    for (const result of toolResults) {
      const toolCall = sessionState.pendingToolCalls.get(result.tool_use_id)
      if (toolCall) {
        const wasSuccess = !result.is_error
        toolCall.status = result.is_error ? ToolCallStatus.FAILED : ToolCallStatus.SUCCESS
        toolCall.endTime = Date.now()
        // 直接使用后端格式，保留 is_error 字段
        toolCall.result = {
          type: result.type,
          tool_use_id: result.tool_use_id,
          content: result.content as string | unknown[],
          is_error: result.is_error
        }
        hasUpdates = true

        log.debug('📥 更新工具结果:', {
          toolUseId: result.tool_use_id,
          status: toolCall.status,
          hasResult: !!toolCall.result
        })

        // 在 IDEA 环境下，工具调用成功后自动执行 IDEA 操作
        if (wasSuccess && ideaBridge.isInIde()) {
          executeIdeActionForTool(toolCall)
        }
      } else {
        log.warn('⚠️ 找不到对应的工具调用:', result.tool_use_id)
      }
    }

    // 🔑 强制触发 Vue 响应式更新
    // displayItems 中的 toolCall 对象是响应式的，但需要触发数组变化检测
    if (hasUpdates) {
      sessionState.displayItems = [...sessionState.displayItems]
    }
  }

  /**
   * 为工具调用执行对应的 IDEA 操作
   */
  async function executeIdeActionForTool(toolCall: any) {
    try {
      const toolType = toolCall.toolType

      switch (toolType) {
        case CLAUDE_TOOL_TYPE.READ: {
          const readCall = toolCall as ClaudeReadToolCall
          const filePath = readCall.input.file_path || readCall.input.path || ''
          if (!filePath) break

          const viewRange = readCall.input.view_range
          let startLine: number | undefined
          let endLine: number | undefined

          if (Array.isArray(viewRange) && viewRange.length >= 2) {
            startLine = viewRange[0]
            endLine = viewRange[1]
          } else if (readCall.input.offset !== undefined) {
            startLine = readCall.input.offset
            if (readCall.input.limit !== undefined) {
              endLine = startLine + readCall.input.limit - 1
            }
          }

          await ideService.openFile(filePath, {
            line: startLine,
            endLine: endLine,
            selectContent: true
          })
          log.debug(`[executeIdeActionForTool] READ: 打开文件 ${filePath}，行号 ${startLine}`)
          break
        }

        case CLAUDE_TOOL_TYPE.WRITE: {
          const writeCall = toolCall as ClaudeWriteToolCall
          const filePath = writeCall.input.file_path || writeCall.input.path || ''
          if (!filePath) break

          await ideService.openFile(filePath)
          log.debug(`[executeIdeActionForTool] WRITE: 打开文件 ${filePath}`)
          break
        }

        case CLAUDE_TOOL_TYPE.EDIT: {
          const editCall = toolCall as ClaudeEditToolCall
          const filePath = editCall.input.file_path || ''
          if (!filePath) break

          await ideService.showDiff({
            filePath,
            oldContent: editCall.input.old_string || '',
            newContent: editCall.input.new_string || '',
            rebuildFromFile: true,
            edits: [{
              oldString: editCall.input.old_string || '',
              newString: editCall.input.new_string || '',
              replaceAll: editCall.input.replace_all || false
            }]
          })
          log.debug(`[executeIdeActionForTool] EDIT: 显示 Diff ${filePath}`)
          break
        }

        case CLAUDE_TOOL_TYPE.MULTI_EDIT: {
          const multiEditCall = toolCall as ClaudeMultiEditToolCall
          const filePath = multiEditCall.input.file_path || ''
          if (!filePath) break

          const edits = multiEditCall.input.edits || []
          if (edits.length === 0) break

          await ideService.showDiff({
            filePath,
            oldContent: edits[0]?.old_string || '',
            newContent: edits[0]?.new_string || '',
            rebuildFromFile: true,
            title: `文件变更: ${filePath} (${edits.length} 处修改)`,
            edits: edits.map(edit => ({
              oldString: edit.old_string || '',
              newString: edit.new_string || '',
              replaceAll: edit.replace_all || false
            }))
          })
          log.debug(`[executeIdeActionForTool] MULTI_EDIT: 显示 Diff ${filePath}，${edits.length} 处修改`)
          break
        }

        default:
          // 其他工具类型不需要自动执行 IDEA 操作
          break
      }
    } catch (error) {
      log.warn(`[executeIdeActionForTool] 执行 IDEA 操作失败: ${error}`)
    }
  }

  /**
   * 添加消息到指定会话
   *
   * 注意: 必须创建新数组以触发 Vue 响应式更新
   */
  function addMessage(sessionId: string, message: Message) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`会话 ${sessionId} 不存在`)
      return
    }

    const newMessages = [...sessionState.messages, message]
    sessionState.messages = newMessages

    // ✅ 增量更新：只转换新消息并追加
    const newDisplayItems = convertMessageToDisplayItems(message, sessionState.pendingToolCalls)
    sessionState.displayItems.push(...newDisplayItems)

    log.debug(`添加消息到会话 ${sessionId}, 共 ${newMessages.length} 条`)
    touchSession(sessionId)
  }

  /**
   * 替换占位符消息
   *
   * @param sessionId 会话ID
   * @param message 新消息
   * @returns 是否成功替换
   */
  // @ts-expect-error 保留供将来使用
  function _replacePlaceholderMessage(sessionId: string, message: Message): boolean {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      return false
    }

    // 优先通过 ID 匹配
    let placeholderIndex = sessionState.messages.findIndex(m =>
      m.role === 'assistant' && m.id === message.id
    )

    // 如果没找到，查找最后一个空的占位符消息
    if (placeholderIndex === -1) {
      for (let i = sessionState.messages.length - 1; i >= 0; i--) {
        const m = sessionState.messages[i]
        if (m.role === 'assistant' && m.id.startsWith('assistant-placeholder-')) {
          placeholderIndex = i
          break
        }
      }
    }

    if (placeholderIndex !== -1) {
      const placeholder = sessionState.messages[placeholderIndex]

      // 智能合并内容：如果占位符通过 stream event 已经构建了内容，应该保留
      const getTextLength = (content: ContentBlock[]) => {
        return content.reduce((total, block) => {
          if (block.type === 'text' && 'text' in block) {
            return total + (block.text?.length || 0)
          }
          return total
        }, 0)
      }

      const placeholderTextLength = getTextLength(placeholder.content || [])
      const newMessageTextLength = getTextLength(message.content || [])
      const placeholderContentLength = placeholder.content?.length || 0
      const newMessageContentLength = message.content?.length || 0

      let mergedContent = message.content

      // 如果占位符的文本内容更长，说明是通过 stream event 构建的，应该保留
      if (placeholderTextLength > 0 && placeholderTextLength > newMessageTextLength) {
        mergedContent = placeholder.content
      } else if (placeholderTextLength > 0 && newMessageTextLength > 0 && placeholderTextLength <= newMessageTextLength && placeholderContentLength !== newMessageContentLength) {
        // 如果新消息的文本更长或相等，但 block 数量不同，尝试合并（去重）
        
        // 使用新消息的内容为基础，补充占位符中可能缺失的内容
        const existingIds = new Set<string>()
        const merged = [...message.content]

        // 收集新消息中的 tool_use id
        message.content.forEach((block: ContentBlock) => {
          if (isToolUseBlock(block)) {
            existingIds.add(block.id)
          }
        })

        // 添加占位符中有但新消息中没有的 tool_use
        placeholder.content.forEach((block: ContentBlock) => {
          if (isToolUseBlock(block) && !existingIds.has(block.id)) {
            merged.push(block)
          }
        })
        
        mergedContent = merged
      }
      
      const newMessages = [...sessionState.messages]
      newMessages[placeholderIndex] = {
        ...message,
        content: mergedContent
      }
      sessionState.messages = newMessages

      // ✅ 去重：不重建 displayItems，避免重复显示
      // 流式事件已经创建了 displayItems，这里只需要确保消息 ID 正确
      // 如果占位符 ID 和新消息 ID 不同，需要更新 displayItems 中的 ID
      if (placeholder.id !== message.id) {
        sessionState.displayItems.forEach(item => {
          if (item.id.startsWith(placeholder.id)) {
            item.id = item.id.replace(placeholder.id, message.id)
          }
        })
      }

      // 注意：不在这里设置 isGenerating，只在 handleResultMessage 中设置
      touchSession(sessionId)
      return true
    }

    return false
  }

  /**
   * 判断两个消息是否应该合并
   *
   * 合并条件:
   * 1. 都是 assistant 消息
   * 2. 时间戳接近 (5秒内)
   * 3. 新消息的 content 更完整 (包含 tool_result)
   */
  function shouldMergeMessages(oldMsg: Message, newMsg: Message): boolean {
    // 只合并 assistant 消息
    if (oldMsg.role !== 'assistant' || newMsg.role !== 'assistant') {
      return false
    }

    // 时间戳接近 (5秒内)
    const timeDiff = Math.abs(newMsg.timestamp - oldMsg.timestamp)
    if (timeDiff > 5000) {
      return false
    }

    // 新消息的 content 块数量 >= 旧消息 (说明有新内容)
    if (newMsg.content.length < oldMsg.content.length) {
      return false
    }

    // 检查是否包含相同的 tool_use (通过 id 匹配)
    const oldToolUseIds = oldMsg.content
      .filter(isToolUseBlock)
      .map(b => b.id)

    const newToolUseIds = newMsg.content
      .filter(isToolUseBlock)
      .map(b => b.id)

    // 新消息必须包含旧消息的所有 tool_use
    const hasAllToolUses = oldToolUseIds.every(id => newToolUseIds.includes(id))

    return hasAllToolUses && oldToolUseIds.length > 0
  }

  /**
   * 合并两个 assistant 消息
   * 使用新消息的完整内容 (包含 tool_result)
   */
  function mergeAssistantMessages(oldMsg: Message, newMsg: Message): Message {
    const merged: Message = {
      ...oldMsg,
      content: newMsg.content,  // 使用新消息的完整 content
      timestamp: newMsg.timestamp
    }
    // 保留 isStreaming 标记（如果存在）
    if (newMsg.isStreaming !== undefined) {
      merged.isStreaming = newMsg.isStreaming
    }
    return merged
  }

  /**
   * 合并或添加消息
   * 智能判断是更新现有消息还是添加新消息
   */
  // @ts-expect-error 保留供将来使用
  function _mergeOrAddMessage(sessionId: string, newMessage: Message) {
    // ✅ 只从 SessionState 读取和更新
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`会话 ${sessionId} 不存在`)
      return
    }

    // ✅ 检查消息是否已存在（避免流式事件和 RPC 消息重复）
    const existingMessage = sessionState.messages.find(m => m.id === newMessage.id)
    if (existingMessage) {
      log.debug(`消息 ${newMessage.id} 已存在，跳过添加`)
      return
    }

    // 查找最近的消息
    const lastIndex = sessionState.messages.length - 1
    const lastMessage = lastIndex >= 0 ? sessionState.messages[lastIndex] : null

    if (lastMessage && shouldMergeMessages(lastMessage, newMessage)) {
      // 合并消息
      const mergedMessage = mergeAssistantMessages(lastMessage, newMessage)
      const newMessages = [...sessionState.messages]
      newMessages[lastIndex] = mergedMessage
      sessionState.messages = newMessages
      // 🔧 修复：不要完全重建 displayItems，而是增量更新该消息的 displayItems
      // 这样不会覆盖之前流式创建的 thinking/text displayItems
      syncDisplayItemsForMessage(mergedMessage, sessionState)
      log.debug(`合并 assistant 消息, tool数量: ${newMessage.content.length}`)
    } else {
      // 添加新消息
      addMessage(sessionId, newMessage)
      log.debug(`添加新消息, role: ${newMessage.role}`)
    }

    // 注意：不在这里设置 isGenerating，只在 handleResultMessage 中设置
    touchSession(sessionId)
  }

  /**
   * 处理 StreamEvent，实现实时渲染
   *
   * 直接解析和处理 stream event 数据，不依赖外部模块
   */
  function handleStreamEvent(sessionId: string, streamEventData: RpcStreamEvent) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`handleStreamEvent: 会话 ${sessionId} 不存在`)
      return
    }

    // 生成状态门控：仅当 isGenerating=true 时处理流事件
    if (!sessionState.isGenerating) {
      log.debug(`handleStreamEvent: 会话 ${sessionId} isGenerating=false，忽略流式事件`)
      return
    }

    // 🔧 修复：如果请求已完成（比如被打断），忽略延迟到达的流式事件
    if (!sessionState.requestTracker) {
      log.debug(`handleStreamEvent: 会话 ${sessionId} 无活动请求，忽略延迟的流式事件`)
      return
    }

    const event = streamEventData.event
    if (!event) {
      log.warn('❌ [handleStreamEvent] 无效的 event 数据:', streamEventData)
      return
    }

    const eventType = event.type
    log.debug(`[handleStreamEvent] 处理事件: ${eventType}`)

    // 更新 token 使用量
    if (eventType === 'message_delta' && 'usage' in event && event.usage) {
      const usage = event.usage as { input_tokens?: number; output_tokens?: number; inputTokens?: number; outputTokens?: number }
      setTokenUsage(
        sessionId,
        usage.input_tokens ?? usage.inputTokens ?? 0,
        usage.output_tokens ?? usage.outputTokens ?? 0
      )
    }

    // 处理不同类型的事件
    switch (eventType) {
      case 'message_start': {
        // message_start 只负责初始化 Message 对象
        // displayItems 由后续的 content_block_start/delta/stop 事件创建和更新
        const contentBlocks = (event.message?.content ?? []).map(mapRpcContentBlock).filter((b): b is ContentBlock => !!b)
        const existingStreaming = findStreamingAssistantMessage(sessionState)
        const previousId = existingStreaming?.id
        const messageId = event.message?.id || previousId || `assistant-${Date.now()}`

        log.debug('📩 [message_start]', {
          messageId,
          previousId,
          hasExistingStreaming: !!existingStreaming,
          initialContentLength: contentBlocks.length
        })

        if (existingStreaming && previousId && previousId !== messageId) {
          // 结束上一条流式消息，开始新消息
          existingStreaming.isStreaming = false
          // ❌ 不调用 syncDisplayItemsForMessage，避免重复创建 displayItems

          const newMessage: Message = {
            id: messageId,
            role: 'assistant',
            timestamp: Date.now(),
            content: [],
            isStreaming: true
          }
          sessionState.messages.push(newMessage)
          updateStreamingMessageId(sessionId, messageId)
          // 合并初始内容（如果有的话）
          if (contentBlocks.length > 0) {
            mergeInitialAssistantContent(newMessage, contentBlocks)
          }
          // ❌ 不调用 syncDisplayItemsForMessage，让 content_block_start 来创建 displayItems
        } else {
          const targetMessage = ensureStreamingAssistantMessage(sessionId, sessionState)
          // 将占位消息 id 更新为后端真实 id
          if (targetMessage.id !== messageId) {
            updateStreamingMessageId(sessionId, messageId)
            targetMessage.id = messageId
          }
          targetMessage.isStreaming = true
          // 合并初始内容（如果有的话）
          if (contentBlocks.length > 0) {
            mergeInitialAssistantContent(targetMessage, contentBlocks)
          }
          // ❌ 不调用 syncDisplayItemsForMessage，让 content_block_start 来创建 displayItems
        }

        setSessionGenerating(sessionId, true)
        touchSession(sessionId)
        break
      }

      case 'message_stop': {
        const streamingMessage = findStreamingAssistantMessage(sessionState)
        if (streamingMessage) {
          streamingMessage.isStreaming = false
          // ❌ 不调用 syncDisplayItemsForMessage
          // displayItems 已经通过 content_block_start/delta/stop 事件创建和更新
          // 这里只需要标记消息流式状态结束
        }
        // 注意：不在这里设置 isGenerating = false
        // isGenerating 只在 handleResultMessage() 中设置为 false（收到 result 消息时）
        touchSession(sessionId)
        break
      }

      case 'content_block_start': {
        const message = ensureStreamingAssistantMessage(sessionId, sessionState)
        const contentBlock = mapRpcContentBlock(event.content_block)
        const blockIndex = event.index

        if (contentBlock) {
          // 1. 添加到 message.content
          while (message.content.length < blockIndex) {
            message.content.push({ type: 'text', text: '' } as any)
          }
          if (message.content.length === blockIndex) {
            message.content.push(contentBlock)
          } else {
            message.content[blockIndex] = contentBlock
          }

          // 2. 直接创建 DisplayItem 并 push（内容为空）
          if (contentBlock.type === 'text') {
            const displayId = `${message.id}-text-${blockIndex}`
            // 检查是否已存在
            if (!sessionState.displayItems.find(item => item.id === displayId)) {
              sessionState.displayItems.push({
                displayType: 'assistantText' as const,
                id: displayId,
                content: '', // 初始为空
                timestamp: message.timestamp,
                isLastInMessage: false,
                stats: undefined
              })
            }
          } else if (contentBlock.type === 'thinking') {
            const displayId = `${message.id}-thinking-${blockIndex}`
            if (!sessionState.displayItems.find(item => item.id === displayId)) {
              sessionState.displayItems.push({
                displayType: 'thinking' as const,
                id: displayId,
                content: '', // 初始为空
                signature: contentBlock.signature,
                timestamp: message.timestamp
              })
            }
          } else if (contentBlock.type === 'tool_use' && contentBlock.id) {
            // ⚠️ tool_use 的 input 是 JSON，必须等累加完成后才能使用
            // 这里只初始化累加器，不创建 DisplayItem
            // 等 content_block_stop 时 JSON 解析完成后再创建
            toolInputJsonAccumulator.set(contentBlock.id, '')
            registerToolCall(contentBlock as ToolUseBlock)
            // åŒæ—¶åˆ›å»ºå·¥å…·è°ƒç”¨çš„å±•ç¤ºå¯¹è±¡ï¼Œä¾¿äºŽç«‹å³æ˜¾ç¤ºå·¥å…·å¡ç‰‡å’Œæƒé™ UI
            const __existingToolItem = sessionState.displayItems.find(
              item => item.displayType === 'toolCall' && item.id === contentBlock.id
            )
            if (!__existingToolItem) {
              const __toolCall = createToolCall(contentBlock as unknown as ToolUseContent, sessionState.pendingToolCalls)
              sessionState.displayItems.push(__toolCall)
            }
          }
        }
        break
      }

      case 'content_block_delta': {
        const message = ensureStreamingAssistantMessage(sessionId, sessionState)
        const index = event.index
        const delta = event.delta

        if (index >= 0 && index < message.content.length && delta) {
          const contentBlock = message.content[index]

          // 根据 delta.type 判断处理方式
          switch (delta.type) {
            case 'text_delta':
              // ✅ 实时渲染：累加并立即更新 DisplayItem
              if (contentBlock.type === 'text') {
                contentBlock.text += delta.text
                updateTextDisplayItemIncrementally(message, index, contentBlock.text, sessionState)
              }
              break

            case 'thinking_delta':
              // ✅ 实时渲染：累加并立即更新 DisplayItem
              if (contentBlock.type === 'thinking') {
                contentBlock.thinking += delta.thinking
                updateThinkingDisplayItemIncrementally(message, index, contentBlock.thinking, sessionState)
              }
              break

            case 'input_json_delta':
              // ⚠️ 只累加 JSON 片段，不更新 displayItems
              // 等 content_block_stop 时 JSON 解析完成后再创建 DisplayItem
              if (contentBlock.type === 'tool_use') {
                const accumulated = toolInputJsonAccumulator.get(contentBlock.id) || ''
                const newAccumulated = accumulated + delta.partial_json
                toolInputJsonAccumulator.set(contentBlock.id, newAccumulated)
                // 尝试解析到 message.content，但不更新 displayItems
                try {
                  contentBlock.input = JSON.parse(newAccumulated)
                } catch {
                  // JSON 不完整，继续累加
                }
              }
              break

            default:
              // 处理 signature_delta（类型定义可能未包含）
              if ((delta as any).type === 'signature_delta' && contentBlock.type === 'thinking') {
                const sigDelta = delta as any
                if (sigDelta.signature) {
                  contentBlock.signature = sigDelta.signature
                  // 更新对应 displayItem 的 signature
                  const displayItem = sessionState.displayItems.find(
                    item => item.id === `${message.id}-thinking-${index}` && item.displayType === 'thinking'
                  ) as ThinkingContent | undefined
                  if (displayItem) {
                    displayItem.signature = sigDelta.signature
                  }
                }
              }
              break
          }
        }
        break
      }

      case 'content_block_stop': {
        const message = findStreamingAssistantMessage(sessionState)
        if (message && event.index >= 0 && event.index < message.content.length) {
          const block = message.content[event.index]

          if (block.type === 'tool_use') {
            const toolUseBlock = block as ToolUseBlock

            log.debug('📦 content_block_stop (tool_use):', {
              id: toolUseBlock.id,
              toolName: toolUseBlock.toolName,
              hasInput: !!toolUseBlock.input,
              inputKeys: toolUseBlock.input ? Object.keys(toolUseBlock.input) : []
            })

            // ✅ JSON 解析完成，现在更新 DisplayItem
            const existingDisplayItem = sessionState.displayItems.find(
              item => item.id === toolUseBlock.id && item.displayType === 'toolCall'
            ) as ToolCall | undefined

            if (!existingDisplayItem) {
              // 创建新的 DisplayItem
              const toolCall = createToolCall(toolUseBlock as ToolUseContent, sessionState.pendingToolCalls)
              sessionState.displayItems.push(toolCall)
            } else {
              // 更新已存在的 DisplayItem 的 input
              existingDisplayItem.input = toolUseBlock.input as Record<string, unknown> || existingDisplayItem.input
            }

            // 同时更新 pendingToolCalls
            const pendingToolCall = sessionState.pendingToolCalls.get(toolUseBlock.id)
            if (pendingToolCall) {
              pendingToolCall.input = toolUseBlock.input || pendingToolCall.input
            }

            // 🔑 强制触发 Vue 响应式更新
            sessionState.displayItems = [...sessionState.displayItems]
          }
        }
        break
      }
    }
  }

  /**
   * 查找当前处于 streaming 状态的 assistant 消息
   */
  function findStreamingAssistantMessage(sessionState: SessionState): Message | null {
    const streamingId = sessionState.requestTracker?.currentStreamingMessageId
    if (streamingId) {
      const matched = [...sessionState.messages].reverse().find(msg => msg.id === streamingId && msg.role === 'assistant')
      if (matched) return matched
    }

    for (let i = sessionState.messages.length - 1; i >= 0; i--) {
      const msg = sessionState.messages[i]
      if (msg.role === 'assistant' && msg.isStreaming) {
        return msg
      }
    }
    return null
  }

  /**
   * 确保存在一个用于流式渲染的 assistant 消息，必要时创建占位并同步展示
   */
  function ensureStreamingAssistantMessage(sessionId: string, sessionState: SessionState): Message {
    const existing = findStreamingAssistantMessage(sessionState)
    if (existing) return existing

    const placeholderId = sessionState.requestTracker?.currentStreamingMessageId || `assistant-${Date.now()}`
    const newMessage: Message = {
      id: placeholderId,
      role: 'assistant',
      timestamp: Date.now(),
      content: [],
      isStreaming: true
    }
    sessionState.messages.push(newMessage)
    const items = convertMessageToDisplayItems(newMessage, sessionState.pendingToolCalls)
    sessionState.displayItems.push(...items)
    return newMessage
  }

  /**
   * 合并 message_start 内置的初始内容，避免重复创建新消息
   */
  function mergeInitialAssistantContent(target: Message, initialBlocks: ContentBlock[]) {
    if (initialBlocks.length === 0) return
    if (target.content.length === 0) {
      target.content = [...initialBlocks]
      return
    }

    initialBlocks.forEach((block, idx) => {
      const existing = target.content[idx]
      if (!existing) {
        target.content[idx] = block
        return
      }

      if (existing.type === 'text' && block.type === 'text' && existing.text.trim() === '') {
        existing.text = block.text
      } else if (existing.type === 'thinking' && block.type === 'thinking' && (existing.thinking || '') === '') {
        existing.thinking = block.thinking
        existing.signature = existing.signature ?? block.signature
      }
    })
  }

  /**
   * 将最终的 assistant 消息内容合并到现有的流式消息中，避免重复新增消息
   */
  // @ts-expect-error 保留供将来使用
  function mergeAssistantMessage(target: Message, incoming: Message) {
    const merged: ContentBlock[] = [...target.content]

    incoming.content.forEach(block => {
      if (block.type === 'tool_use') {
        const idx = merged.findIndex(
          item => item.type === 'tool_use' && (item as ToolUseBlock).id === (block as ToolUseBlock).id
        )
        if (idx >= 0) {
          const existingInput = (merged[idx] as ToolUseBlock).input
          const incomingInput = (block as ToolUseBlock).input
          // 只有当新的 input 有值时才覆盖（避免 null 覆盖已有值）
          merged[idx] = {
            ...merged[idx],
            ...block,
            input: incomingInput != null ? incomingInput : existingInput
          }
        } else {
          merged.push(block)
        }
      } else if (block.type === 'thinking') {
        const idx = merged.findIndex(item => item.type === 'thinking')
        if (idx >= 0) {
          const existing = merged[idx] as ContentBlock & { thinking?: string }
          merged[idx] = { ...existing, ...block, thinking: (block as ContentBlock & { thinking?: string }).thinking || existing.thinking || '' }
        } else {
          merged.push(block)
        }
      } else if (block.type === 'text') {
        const idx = merged.findIndex(item => item.type === 'text')
        if (idx >= 0) {
          merged[idx] = block
        } else {
          merged.push(block)
        }
      } else {
        merged.push(block)
      }
    })

    target.content = merged

    // 保留 tokenUsage 等附加信息
    if ((incoming as any).tokenUsage) {
      (target as any).tokenUsage = (incoming as any).tokenUsage
    }
  }

  /**
   * 计算哪些文本块是工具入参回显，需要跳过展示（并尽量补充到 tool_use.input）
   */
  function computeToolTextSkip(message: Message): Set<number> {
    const skip = new Set<number>()
    if (message.role !== 'assistant') return skip

    const toolUses = (message.content as ContentBlock[]).filter(isToolUseBlock) as ToolUseBlock[]
    if (toolUses.length === 0) return skip

    ;(message.content as ContentBlock[]).forEach((block, idx) => {
      if (block.type !== 'text') return
      const text = (block as any).text as string
      const trimmed = text.trim()
      if (!trimmed) return

      const start = text.indexOf('{')
      const end = text.lastIndexOf('}')
      let parsed: any = null
      if (start !== -1 && end !== -1 && end > start) {
        try { parsed = JSON.parse(text.slice(start, end + 1)) } catch { /* ignore */ }
      }

      if (parsed && toolUses.some(t => !t.input || Object.keys(t.input as any).length === 0)) {
        toolUses.forEach(t => {
          if (!t.input || Object.keys(t.input as any).length === 0) {
            t.input = parsed
          }
        })
        skip.add(idx)
        return
      }

      const structuralChars = (trimmed.match(/[{}\[\]\"“”：:,]/g) || []).length
      const structuralRatio = structuralChars / trimmed.length
      const containsField = /todos|status|activeForm|file_path|path|tool_use_id|content/i.test(trimmed)
      if (structuralRatio > 0.15 && containsField) {
        skip.add(idx)
      }
    })

    return skip
  }

  /**
   * 当 messageId 更新时，移除旧 messageId 生成的展示项，避免重复展示
   */
  // @ts-expect-error 保留供将来使用
  function _dropAssistantDisplayItemsById(sessionState: SessionState, messageId: string) {
    sessionState.displayItems = sessionState.displayItems.filter(item => {
      if (item.displayType === 'assistantText' || item.displayType === 'thinking') {
        return !item.id.startsWith(`${messageId}-`)
      }
      return true
    })
  }

  /**
   * 🔧 增量更新文本 displayItem
   *
   * 直接更新对应的 displayItem 的 content 属性，避免重建整个 displayItems 数组
   * 这样可以实现真正的流式渲染效果
   */
  function updateTextDisplayItemIncrementally(
    message: Message,
    blockIndex: number,
    newText: string,
    sessionState: SessionState
  ) {
    const expectedId = `${message.id}-text-${blockIndex}`

    // 🔍 调试
    console.log('🔍 [updateTextDisplayItemIncrementally]', {
      messageId: message.id,
      blockIndex,
      expectedId,
      displayItemsCount: sessionState.displayItems.length,
      displayItemIds: sessionState.displayItems.slice(0, 5).map(d => d.id)
    })

    for (let i = 0; i < sessionState.displayItems.length; i++) {
      const item = sessionState.displayItems[i]
      if (item.id === expectedId && item.displayType === 'assistantText') {
        // 取出来，更新 content，创建新对象放回去
        // 这样 vue-virtual-scroller 能检测到这个元素变化
        const updated = { ...item, content: newText } as AssistantText
        sessionState.displayItems[i] = updated
        console.log('🔍 [updateTextDisplayItemIncrementally] ✅ 找到并更新了 displayItem')
        return
      }
    }

    // 🔧 修复：如果找不到 text DisplayItem，创建一个新的
    // 这解决了 content_block_start 时 text 为空导致未创建 DisplayItem 的问题
    {
      const newTextItem: AssistantText = {
        displayType: 'assistantText',
        id: expectedId,
        content: newText,
        timestamp: message.timestamp,
        isLastInMessage: false,
        stats: undefined,
        isStreaming: true
      }
      sessionState.displayItems.push(newTextItem)
      console.log(`🔧 [updateTextDisplayItemIncrementally] 创建新的 text DisplayItem: ${expectedId}`)
    }
  }

  /**
   * 🔧 增量更新思考 displayItem
   */
  function updateThinkingDisplayItemIncrementally(
    message: Message,
    blockIndex: number,
    newThinking: string,
    sessionState: SessionState
  ) {
    const expectedId = `${message.id}-thinking-${blockIndex}`

    for (let i = 0; i < sessionState.displayItems.length; i++) {
      const item = sessionState.displayItems[i]
      if (item.id === expectedId && item.displayType === 'thinking') {
        // 取出来，更新 content，创建新对象放回去
        const updated = { ...item, content: newThinking } as ThinkingContent
        sessionState.displayItems[i] = updated
        return
      }
    }

    // 🔧 修复：如果找不到 thinking DisplayItem，创建一个新的
    // 这解决了 content_block_start 时 thinking 为空导致未创建 DisplayItem 的问题
    // 流式事件是按顺序来的，直接追加到末尾即可
    {
      const newThinkingItem: ThinkingContent = {
        displayType: 'thinking',
        id: expectedId,
        content: newThinking,
        timestamp: message.timestamp
      }
      sessionState.displayItems.push(newThinkingItem)
      console.log(`🔧 [updateThinkingDisplayItemIncrementally] 创建新的 thinking DisplayItem: ${expectedId}`)
    }
  }

  /**
   * 同步消息到 displayItems
   *
   * 核心原则：displayItems 只增不减
   * - 存在 → 更新属性
   * - 不存在 → 追加到末尾
   * - 永不删除
   */
  function syncDisplayItemsForMessage(message: Message, sessionState: SessionState) {
    const skipTextIndices = computeToolTextSkip(message)

    // 遍历 message.content，查找或创建对应的 DisplayItem
    for (let blockIdx = 0; blockIdx < message.content.length; blockIdx++) {
      const block = message.content[blockIdx]

      if (isTextBlock(block)) {
        if (skipTextIndices.has(blockIdx)) continue

        const expectedId = `${message.id}-text-${blockIdx}`
        // 直接在数组中查找
        let existingItem = sessionState.displayItems.find(
          item => item.id === expectedId && item.displayType === 'assistantText'
        ) as AssistantText | undefined

        if (existingItem) {
          // 更新属性
          existingItem.content = (block as TextBlock).text
        } else {
          // 追加新项
          sessionState.displayItems.push({
            displayType: 'assistantText' as const,
            id: expectedId,
            content: (block as TextBlock).text,
            timestamp: message.timestamp,
            isLastInMessage: false,
            stats: undefined
          })
        }
      } else if (block.type === 'thinking') {
        const expectedId = `${message.id}-thinking-${blockIdx}`
        let existingItem = sessionState.displayItems.find(
          item => item.id === expectedId && item.displayType === 'thinking'
        ) as ThinkingContent | undefined

        if (existingItem) {
          existingItem.content = block.thinking || ''
          if (block.signature) existingItem.signature = block.signature
        } else {
          sessionState.displayItems.push({
            displayType: 'thinking' as const,
            id: expectedId,
            content: block.thinking || '',
            signature: block.signature,
            timestamp: message.timestamp
          })
        }
      } else if (isToolUseBlock(block)) {
        const toolUseBlock = block as ToolUseBlock
        let existingItem = sessionState.displayItems.find(
          item => item.id === block.id && item.displayType === 'toolCall'
        ) as ToolCall | undefined

        if (existingItem) {
          // 更新 input
          if (toolUseBlock.input !== undefined &&
              Object.keys(toolUseBlock.input as Record<string, unknown>).length > 0) {
            existingItem.input = toolUseBlock.input as Record<string, unknown>
          }
        } else {
          // 创建并追加
          const toolCall = createToolCall(toolUseBlock as ToolUseContent, sessionState.pendingToolCalls)
          sessionState.displayItems.push(toolCall)
        }
      }
    }
  }

  /**
   * 处理 result 消息，更新请求统计信息
   *
   * result 消息格式：
   * {
   *   type: 'result',
   *   duration_ms: number,
   *   duration_api_ms: number,
   *   is_error: boolean,
   *   num_turns: number,
   *   session_id: string,
   *   total_cost_usd?: number,
   *   usage?: { input_tokens: number, output_tokens: number }
   * }
   */
  function handleResultMessage(sessionId: string, resultData: RpcResultMessage) {
    log.debug(`handleResultMessage: 收到 result 消息, sessionId=${sessionId}`)

    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`handleResultMessage: 会话 ${sessionId} 不存在`)
      return
    }

    // 获取追踪信息
    const tracker = sessionState.requestTracker

    // 解析 usage 信息
    let inputTokens = 0
    let outputTokens = 0

    const usage = resultData.usage as { input_tokens?: number; output_tokens?: number } | undefined
    if (usage) {
      inputTokens = usage.input_tokens || 0
      outputTokens = usage.output_tokens || 0
    }

    // 计算请求时长
    const durationMs = resultData.duration_ms ||
      (tracker ? Date.now() - tracker.requestStartTime : 0)

    log.debug(`handleResultMessage: 统计信息 duration=${durationMs}ms, tokens=${inputTokens}/${outputTokens}`)

    // 更新对应用户消息的统计信息
    if (tracker?.lastUserMessageId) {
      // 在 displayItems 中找到对应的用户消息并更新
      const displayItemIndex = sessionState.displayItems.findIndex(
        item => isDisplayUserMessage(item) && item.id === tracker.lastUserMessageId
      )

      if (displayItemIndex !== -1) {
        const userMessage = sessionState.displayItems[displayItemIndex]
        if (isDisplayUserMessage(userMessage)) {
          userMessage.requestStats = {
            requestDuration: durationMs,
            inputTokens,
            outputTokens
          }
          userMessage.isStreaming = false
        }
        log.debug(`handleResultMessage: 更新用户消息统计信息`)

        // 触发响应式更新
        sessionState.displayItems = [...sessionState.displayItems]
      }
    }

    // 🔧 结束正在流式的 assistant 消息（打断时可能不会收到 message_stop 事件）
    const streamingMessage = findStreamingAssistantMessage(sessionState)
    if (streamingMessage) {
      streamingMessage.isStreaming = false
      // ❌ 不调用 syncDisplayItemsForMessage
      // displayItems 已经通过 content_block_start/delta/stop 事件创建和更新
      log.debug('handleResultMessage: 结束流式 assistant 消息')
    }

    // 打断响应：先结束生成，再渲染红色打断提示（i18n）
    if (resultData.subtype === 'interrupted') {
      // 1) 先结束生成，清理追踪，确保后续流事件不再影响 UI
      setSessionGenerating(sessionId, false)
      sessionState.requestTracker = null
      // 2) 再渲染红色打断提示（专用组件）
      sessionState.displayItems.push({
        id: `interrupt-${Date.now()}`,
        displayType: 'interruptedHint',
        timestamp: Date.now(),
        message: i18n.global.t('system.interrupted')
      } as any)
      log.info('handleResultMessage: 渲染打断提示')
    }

    // 处理错误：如果 is_error 为 true，添加错误 DisplayItem
    if (resultData.is_error && resultData.result) {
      sessionState.lastError = resultData.result
      log.warn(`handleResultMessage: 后端返回错误: ${resultData.result}`)

      // 添加错误结果到 displayItems
      const errorItem: DisplayItem = {
        id: `error-${Date.now()}`,
        displayType: 'errorResult',
        timestamp: Date.now(),
        message: resultData.result
      }
      sessionState.displayItems.push(errorItem)
    }

    // 标记生成完成（非打断场景）
    if (resultData.subtype !== 'interrupted') {
      setSessionGenerating(sessionId, false)
      sessionState.requestTracker = null
      log.debug('handleResultMessage: 请求完成, 清除追踪信息')
    }

    // 处理队列中的下一条消息
    processNextQueuedMessage()
  }

  /**
   * 开始追踪请求（发送用户消息时调用）
   */
  function startRequestTracking(sessionId: string, userMessageId: string, streamingMessageId: string) {
    log.debug(`startRequestTracking: sessionId=${sessionId}, userMessageId=${userMessageId}`)
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`startRequestTracking: 会话 ${sessionId} 不存在`)
      return
    }

    sessionState.requestTracker = {
      lastUserMessageId: userMessageId,
      requestStartTime: Date.now(),
      inputTokens: 0,
      outputTokens: 0,
      currentStreamingMessageId: streamingMessageId
    }

    // 设置 isGenerating = true（开始生成）
    setSessionGenerating(sessionId, true)

    // 更新 displayItem 的 isStreaming 状态
    const displayItemIndex = sessionState.displayItems.findIndex(
      item => isDisplayUserMessage(item) && item.id === userMessageId
    )
    if (displayItemIndex !== -1) {
      const userMessage = sessionState.displayItems[displayItemIndex]
      if (isDisplayUserMessage(userMessage)) {
        userMessage.isStreaming = true
      }
      // 触发响应式更新
      sessionState.displayItems = [...sessionState.displayItems]
    }
  }

  /**
   * 累加 token 使用量（用于增量更新）
   */
  function addTokenUsage(sessionId: string, inputTokens: number, outputTokens: number) {
    const sessionState = getSessionState(sessionId)
    if (sessionState?.requestTracker) {
      sessionState.requestTracker.inputTokens += inputTokens
      sessionState.requestTracker.outputTokens += outputTokens
    }
  }

  /**
   * 设置 token 使用量（用于累计值更新，如 message_delta.usage）
   */
  function setTokenUsage(sessionId: string, inputTokens: number, outputTokens: number) {
    const sessionState = getSessionState(sessionId)
    if (sessionState?.requestTracker) {
      sessionState.requestTracker.inputTokens = inputTokens
      sessionState.requestTracker.outputTokens = outputTokens
    }
  }

  /**
   * 获取当前请求的统计信息（供组件使用）
   */
  function getRequestStats(sessionId: string) {
    const sessionState = getSessionState(sessionId)
    const tracker = sessionState?.requestTracker
    if (!tracker) return null
    return {
      startTime: tracker.requestStartTime,
      inputTokens: tracker.inputTokens,
      outputTokens: tracker.outputTokens
    }
  }

  /**
   * 获取当前正在流式输出的消息 ID
   */
  function getCurrentStreamingMessageId(sessionId: string): string | null {
    const sessionState = getSessionState(sessionId)
    return sessionState?.requestTracker?.currentStreamingMessageId ?? null
  }

  /**
   * 更新当前流式消息的 ID（当后端返回真实 ID 时调用）
   */
  function updateStreamingMessageId(sessionId: string, newMessageId: string) {
    const sessionState = getSessionState(sessionId)
    if (sessionState?.requestTracker) {
      log.debug(`updateStreamingMessageId: ${sessionState.requestTracker.currentStreamingMessageId} -> ${newMessageId}`)
      sessionState.requestTracker.currentStreamingMessageId = newMessageId
    }
  }

  /**
   * 移除消息
   *
   * @param sessionId 会话ID
   * @param index 消息索引
   */
  function removeMessage(sessionId: string, index: number) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`removeMessage: 会话 ${sessionId} 不存在`)
      return
    }

    if (index >= 0 && index < sessionState.messages.length) {
      const newMessages = [...sessionState.messages]
      newMessages.splice(index, 1)
      sessionState.messages = newMessages
      sessionState.displayItems = convertToDisplayItems(newMessages, sessionState.pendingToolCalls)
      log.debug(`removeMessage: 会话 ${sessionId} 移除消息，当前共 ${newMessages.length} 条`)
    }
  }

  /**
   * 删除会话
   */
  async function deleteSession(sessionId: string) {
    try {
      log.info(`删除会话: ${sessionId}`)

      // 断开连接
      await aiAgentService.disconnect(sessionId)

      // 清除连接状态
      connectionStatuses.value.delete(sessionId)

      // 从列表中移除（SessionState 会自动删除，包括其中的所有状态）
      sessions.delete(sessionId)
      unlinkExternalSessionId(sessionId)

      // 清除其他缓存
      sessionModelIds.value.delete(sessionId)

      // 如果删除的是当前会话,切换到第一个会话
      if (currentSessionId.value === sessionId) {
        const remainingSessions = Array.from(sessions.values())
        if (remainingSessions.length > 0) {
          await switchSession(remainingSessions[0].id)
        } else {
          currentSessionId.value = null
        }
      }

      log.info(`会话已删除: ${sessionId}`)
      return true
    } catch (error) {
      log.error('删除会话异常:', error)
      return false
    }
  }

  /**
   * 重命名会话
   */
  async function renameSession(sessionId: string, newName: string) {
    try {
      log.debug(`重命名会话: ${sessionId} → ${newName}`)

      const session = getSessionState(sessionId)
      if (session) {
        session.name = newName
        session.updatedAt = Date.now()
        return true
      } else {
        log.error(`会话不存在: ${sessionId}`)
        return false
      }
    } catch (error) {
      log.error('重命名会话异常:', error)
      return false
    }
  }

  /**
   * 加载会话历史消息
   */
  async function loadSessionHistory(sessionId: string): Promise<AgentStreamEvent[]> {
    loading.value = true
    try {
      log.debug(`加载历史消息: ${sessionId}`)
      // getHistory 返回的是 stream event 记录，后续可在 resume 流中替代
      const messages = await aiAgentService.getHistory(sessionId)
      log.debug(`加载了 ${messages.length} 条历史消息`)
      return messages
    } catch (error) {
      log.error('加载历史消息失败:', error)
      return []
    } finally {
      loading.value = false
    }
  }

  // currentMessages 已在前面定义（第 61 行）

  /**
   * 获取指定会话的消息列表
   */
  function getMessages(sessionId: string): Message[] {
    // ✅ 从 SessionState 读取
    const sessionState = getSessionState(sessionId)
    return sessionState ? sessionState.messages : []
  }

  // currentConnectionStatus 已在前面定义（第 73 行）

  /**
   * 发送消息 (纯文本)
   */
  async function sendMessage(message: string): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    // 发送前同步设置（延迟同步策略）
    await syncSettingsIfNeeded()

    await aiAgentService.sendMessage(currentSessionId.value, message)
  }

  /**
   * 发送消息 (支持图片，stream-json 格式)
   *
   * @param content 内容块数组 [{ type: 'text', text: '...' }, { type: 'image', data: '...', mimeType: '...' }]
   */
  async function sendMessageWithContent(content: ContentBlock[]): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    // 发送前同步设置（延迟同步策略）
    await syncSettingsIfNeeded()

    await aiAgentService.sendMessageWithContent(
      currentSessionId.value,
      content as unknown as import('../services/AiAgentSession').ContentBlock[]
    )
  }

  /**
   * 将消息加入队列并自动处理发送
   * - 如果正在生成中，消息会被加入队列等待
   * - 如果不在生成中，直接发送
   */
  function enqueueMessage(message: { contexts: any[]; contents: ContentBlock[] }) {
    if (!currentSessionId.value) {
      console.error('❌ enqueueMessage: 没有活跃会话')
      return
    }

    const sessionId = currentSessionId.value
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      console.error('❌ enqueueMessage: 会话状态不存在')
      return
    }

    // 如果正在生成中，将消息加入队列
    if (sessionState.isGenerating) {
      const pendingMessage: PendingMessage = {
        id: `pending-${Date.now()}-${Math.random().toString(36).substring(2, 10)}`,
        contexts: message.contexts,
        contents: message.contents,
        createdAt: Date.now()
      }
      messageQueue.value.push(pendingMessage)
      log.info(`消息已加入队列，当前队列长度: ${messageQueue.value.length}`)
      return
    }

    // 将 contexts 转换为 ContentBlock 格式
    // buildUserMessageContent 会将文件引用、图片等转换为对应的内容块
    const contextBlocks = message.contexts.length > 0
      ? buildUserMessageContent({
          text: '',  // 文本内容从 message.contents 获取
          contexts: message.contexts
        })
      : []

    // 合并: contexts 内容块 + 用户输入内容块
    const mergedContent = [...contextBlocks, ...message.contents]

    console.log('📤 enqueueMessage: contexts=', message.contexts.length, 'contents=', message.contents.length, 'merged=', mergedContent.length)

    // 1. 先将用户消息添加到本地显示（使用合并后的内容）
    const userMessage: Message = {
      id: `user-${Date.now()}`,
      role: 'user',
      timestamp: Date.now(),
      content: mergedContent
    }

    // 添加到 messages
    sessionState.messages.push(userMessage)

    // 添加到 displayItems
    const newDisplayItems = convertMessageToDisplayItems(userMessage, sessionState.pendingToolCalls)
    sessionState.displayItems.push(...newDisplayItems)

    console.log('📤 用户消息已添加到显示列表:', userMessage.id)

    // 2. 开始请求追踪（设置 isGenerating = true）
    const streamingMessageId = `assistant-${Date.now()}`
    startRequestTracking(sessionId, userMessage.id, streamingMessageId)

    // 3. 发送到后端（使用合并后的内容）
    sendMessageWithContent(mergedContent).catch(err => {
      console.error('❌ enqueueMessage 发送失败:', err)
      // 发送失败时重置状态
      setSessionGenerating(sessionId, false)
      const sessionState = getSessionState(sessionId)
      if (sessionState) {
        sessionState.requestTracker = null
      }
    })
  }

  /**
   * 处理队列中的下一条消息
   * 在上一个请求完成后自动调用
   */
  function processNextQueuedMessage() {
    if (messageQueue.value.length === 0) {
      return
    }

    const nextMessage = messageQueue.value.shift()
    if (!nextMessage) {
      return
    }

    log.info(`从队列中取出消息: ${nextMessage.id}，剩余队列长度: ${messageQueue.value.length}`)

    // 递归调用 enqueueMessage，此时 isGenerating 应为 false
    enqueueMessage({
      contexts: nextMessage.contexts,
      contents: nextMessage.contents
    })
  }

  /**
   * 编辑队列中的消息（从队列移除并返回内容，用于填充到输入框）
   */
  function editQueueMessage(id: string): PendingMessage | null {
    const index = messageQueue.value.findIndex(m => m.id === id)
    if (index === -1) return null
    const [removed] = messageQueue.value.splice(index, 1)
    log.info(`编辑队列消息: ${id}，剩余队列长度: ${messageQueue.value.length}`)
    return removed
  }

  /**
   * 从队列中删除消息
   */
  function removeFromQueue(id: string): boolean {
    const index = messageQueue.value.findIndex(m => m.id === id)
    if (index === -1) return false
    messageQueue.value.splice(index, 1)
    log.info(`删除队列消息: ${id}，剩余队列长度: ${messageQueue.value.length}`)
    return true
  }

  /**
   * 清空消息队列（取消生成时调用）
   */
  function clearQueue(): void {
    const count = messageQueue.value.length
    messageQueue.value = []
    log.info(`清空消息队列，已丢弃 ${count} 条消息`)
  }

  /**
   * 中断当前操作
   */
  async function interrupt(): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    await aiAgentService.interrupt(currentSessionId.value)
  }

  /**
   * 设置当前会话的模型
   */
  async function setModel(model: string): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    await aiAgentService.setModel(currentSessionId.value, model)

    // 更新本地记录
    sessionModelIds.value.set(currentSessionId.value, model)
    currentModelId.value = model

    const session = getSessionState(currentSessionId.value)
    if (session) {
      session.modelId = model
    }

    // 更新 lastAppliedSettings
    if (lastAppliedSettings.value) {
      lastAppliedSettings.value = {
        ...lastAppliedSettings.value,
        modelId: model
      }
    }
  }

  /**
   * 设置当前会话的权限模式
   */
  async function setPermissionMode(mode: RpcPermissionMode): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    await aiAgentService.setPermissionMode(currentSessionId.value, mode)
    log.info(`权限模式已切换为: ${mode}`)

    // 更新 lastAppliedSettings
    if (lastAppliedSettings.value) {
      lastAppliedSettings.value = {
        ...lastAppliedSettings.value,
        permissionMode: mode
      }
    }
  }

  /**
   * 发送 query 之前调用，按需同步设置到后端
   *
   * 延迟同步策略：用户切换模型/思考开关时只保存设置到会话状态，
   * 在发送消息前才比较当前设置和上次应用的设置，按需同步
   */
  async function syncSettingsIfNeeded(): Promise<void> {
    if (!currentSessionId.value) {
      return
    }

    // 从当前会话读取设置（而不是 settingsStore）
    const sessionSettings = currentSessionSettings.value
    if (!sessionSettings || !sessionSettings.modelId) {
      log.warn('syncSettingsIfNeeded: 当前会话设置无效')
      return
    }

    const current = {
      modelId: sessionSettings.modelId,
      thinkingEnabled: sessionSettings.thinkingEnabled,
      permissionMode: sessionSettings.permissionMode,
      skipPermissions: sessionSettings.skipPermissions
    }

    const last = lastAppliedSettings.value

    // 检查是否完全相同
    if (last &&
      current.modelId === last.modelId &&
      current.thinkingEnabled === last.thinkingEnabled &&
      current.permissionMode === last.permissionMode &&
      current.skipPermissions === last.skipPermissions
    ) {
      return  // 无变化
    }

    log.info('🔄 syncSettingsIfNeeded: 检测到设置变化', { current, last })

    // 1️⃣ 判断是否需要重连（thinkingEnabled 或 skipPermissions 变了）
    const needReconnect = last && (
      current.thinkingEnabled !== last.thinkingEnabled ||
      current.skipPermissions !== last.skipPermissions
    )

    if (needReconnect) {
      await reconnect(current)
    } else {
      // 2️⃣ 不需要重连，分别处理 model 和 permissionMode
      if (!last || current.modelId !== last.modelId) {
        await setModel(current.modelId)
      }
      if (!last || current.permissionMode !== last.permissionMode) {
        await setPermissionMode(current.permissionMode)
      }
    }

    lastAppliedSettings.value = current
  }

  /**
   * 重连当前会话（disconnect + connect）
   * 用于修改 thinkingEnabled 等只能在 connect 时配置的参数
   */
  async function reconnect(settings: {
    modelId: string
    thinkingEnabled: boolean
    permissionMode: string
    skipPermissions: boolean
  }): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    const sessionId = currentSessionId.value
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      throw new Error('会话状态不存在')
    }

    log.info(`🔄 重连会话: ${sessionId}`, settings)

    // 1. 断开当前连接
    await aiAgentService.disconnect(sessionId)

    // 2. 构建 connect 选项
    const options = buildConnectOptions({
      model: settings.modelId,
      thinkingEnabled: settings.thinkingEnabled,
      permissionMode: settings.permissionMode as RpcPermissionMode,
      dangerouslySkipPermissions: settings.skipPermissions,
      continueConversation: true,
      resume: sessionId
    })

    // 3. 重新连接
    const connectResult = await aiAgentService.connect(options, (rawMessage: RpcMessage) => {
      const normalized = normalizeRpcMessage(rawMessage)
      if (normalized) {
        handleMessage(connectResult.sessionId, normalized)
      }
    })

    // 4. 更新前端 session 映射
    const newSessionId = connectResult.sessionId
    if (newSessionId !== sessionId) {
      sessions.set(newSessionId, sessionState)
      sessions.delete(sessionId)
      currentSessionId.value = newSessionId
    }

    // 5. 更新本地状态
    sessionModelIds.value.set(newSessionId, settings.modelId)
    currentModelId.value = settings.modelId
    sessionState.modelId = settings.modelId

    // 6. 更新 lastAppliedSettings
    lastAppliedSettings.value = settings

    log.info(`✅ 重连完成: ${newSessionId}`)
  }

  /**
   * 注册工具调用
   * 当收到 tool_use 消息时调用
   */
  function registerToolCall(block: ToolUseBlock) {
    // 如果已经注册过，跳过（避免重复注册导致状态被重置）
    if (toolCallsMap.value.has(block.id)) {
      return
    }

    toolCallsMap.value.set(block.id, {
      id: block.id,
      name: block.toolName,
      status: 'running',
      startTime: Date.now()
    })
    log.debug(`注册工具调用: ${block.toolName} (${block.id})`)
  }

  /**
   * 更新工具结果
   * 当收到 tool_result 消息时调用
   */
  function updateToolResult(toolUseId: string, result: ToolResultBlock) {
    const state = toolCallsMap.value.get(toolUseId)
    if (state) {
      state.status = result.is_error ? 'failed' : 'success'
      state.result = result.content
      state.endTime = Date.now()
      log.debug(`更新工具状态: ${state.name} -> ${state.status}`)
    } else {
      log.warn(`找不到工具调用记录: ${toolUseId}`)
    }
  }

  /**
   * 获取工具调用状态
   */
  function getToolStatus(toolId: string): 'running' | 'success' | 'failed' {
    const state = toolCallsMap.value.get(toolId)
    return state?.status || 'running'
  }

  /**
   * 获取工具调用结果
   */
  function getToolResult(toolId: string): any {
    const state = toolCallsMap.value.get(toolId)
    return state?.result
  }

  /**
   * 更新Tab顺序（拖拽后调用）
   * @param newOrder 新的顺序数组，按顺序包含sessionId
   */
  function updateTabOrder(newOrder: string[]) {
    newOrder.forEach((sessionId, index) => {
      const session = sessions.get(sessionId)
      if (session) {
        session.order = index
      }
    })
  }

  /**
   * 为会话注册 askUserQuestion 处理器
   * 在会话创建或恢复后调用
   */
  /**
   * 验证 AskUserQuestion 参数
   * @param params 原始参数
   * @returns 验证后的 questions 数组
   * @throws Error 如果验证失败，抛出详细错误信息
   */
  function validateAskUserQuestionParams(params: any): Array<{
    question: string
    header: string
    options: Array<{ label: string; description?: string }>
    multiSelect?: boolean
  }> {
    // 调试日志：打印实际收到的参数结构
    log.debug(`[AskUserQuestion] 验证参数 - params类型: ${typeof params}, keys: ${params ? Object.keys(params) : 'null'}`)

    let questions = params?.questions

    // 如果 questions 是 JSON 字符串，尝试解析
    if (typeof questions === 'string') {
      try {
        questions = JSON.parse(questions)
        log.debug(`[AskUserQuestion] questions 是字符串，已解析为: ${typeof questions}`)
      } catch (e) {
        throw new Error(`questions 是无效的 JSON 字符串: ${questions}`)
      }
    }

    if (!questions) {
      // 打印更多调试信息
      log.error(`[AskUserQuestion] 缺少 questions 参数, params: ${JSON.stringify(params)}`)
      throw new Error('缺少必需参数: questions')
    }
    if (!Array.isArray(questions)) {
      throw new Error(`questions 必须是数组，实际类型: ${typeof questions}`)
    }
    if (questions.length === 0) {
      throw new Error('questions 数组不能为空')
    }

    const errors: string[] = []
    questions.forEach((q, index) => {
      if (!q.question) {
        errors.push(`questions[${index}]: 缺少必需字段 'question'`)
      }
      if (!q.header) {
        errors.push(`questions[${index}]: 缺少必需字段 'header'`)
      }
      if (!q.options) {
        errors.push(`questions[${index}]: 缺少必需字段 'options'`)
      } else if (!Array.isArray(q.options)) {
        errors.push(`questions[${index}].options 必须是数组`)
      } else if (q.options.length === 0) {
        errors.push(`questions[${index}].options 不能为空`)
      } else {
        // 验证并规范化每个选项
        q.options.forEach((opt: any, optIndex: number) => {
          if (typeof opt === 'string') {
            // 兼容 Claude 格式：字符串自动转换为对象
            q.options[optIndex] = { label: opt, description: '' }
          } else if (!opt.label) {
            errors.push(`questions[${index}].options[${optIndex}]: 缺少必需字段 'label'`)
          }
        })
      }
    })

    if (errors.length > 0) {
      throw new Error(`参数验证失败:\n${errors.join('\n')}`)
    }

    return questions
  }

  function registerAskUserQuestionHandler(sessionId: string): () => void {
    log.info(`[AskUserQuestion] 为会话 ${sessionId} 注册处理器`)

    return aiAgentService.register(sessionId, 'AskUserQuestion', async (params) => {
      log.info(`[AskUserQuestion] 收到问题请求:`, params)

      const sessionState = getSessionState(sessionId)
      if (!sessionState) {
        throw new Error(`会话 ${sessionId} 不存在`)
      }

      // 验证参数
      const questions = validateAskUserQuestionParams(params)

      // 生成唯一ID
      const questionId = `ask-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`

      // 返回一个 Promise，当用户回答后 resolve
      // 返回数组格式：[{ question, header, answer }, ...]
      return new Promise<Array<{ question: string; header: string; answer: string }>>((resolve, reject) => {
        // 存储到 session.pendingQuestions
        sessionState.pendingQuestions.set(questionId, {
          id: questionId,
          sessionId,
          questions: questions.map(q => ({ ...q, multiSelect: q.multiSelect ?? false })),
          createdAt: Date.now(),
          resolve: (answersMap) => {
            sessionState.pendingQuestions.delete(questionId)
            // 转换为数组格式
            const result = questions.map(q => ({
              question: q.question,
              header: q.header,
              answer: answersMap[q.question] || ''
            }))
            resolve(result)
          },
          reject: (error) => {
            sessionState.pendingQuestions.delete(questionId)
            reject(error)
          }
        })

        log.info(`[AskUserQuestion] 问题已加入待回答队列: ${questionId}`)
      })
    })
  }

  /**
   * 获取当前会话的待回答问题
   */
  function getCurrentPendingQuestions(): PendingUserQuestion[] {
    const session = currentSession.value
    if (!session) return []
    return Array.from(session.pendingQuestions.values())
      .sort((a, b) => a.createdAt - b.createdAt)
  }

  /**
   * 回答问题
   * @param questionId 问题ID
   * @param answers 用户的回答 { [header]: selectedOption }
   */
  function answerQuestion(questionId: string, answers: Record<string, string>): boolean {
    const session = currentSession.value
    if (!session) {
      log.warn(`[AskUserQuestion] 当前没有激活的会话`)
      return false
    }

    const pending = session.pendingQuestions.get(questionId)
    if (!pending) {
      log.warn(`[AskUserQuestion] 问题不存在或已回答: ${questionId}`)
      return false
    }

    log.info(`[AskUserQuestion] 用户回答问题: ${questionId}`, answers)
    pending.resolve(answers)
    return true
  }

  /**
   * 取消问题（用户关闭对话框等情况）
   * @param questionId 问题ID
   */
  function cancelQuestion(questionId: string): boolean {
    const session = currentSession.value
    if (!session) {
      log.warn(`[AskUserQuestion] 当前没有激活的会话`)
      return false
    }

    const pending = session.pendingQuestions.get(questionId)
    if (!pending) {
      log.warn(`[AskUserQuestion] 问题不存在或已回答: ${questionId}`)
      return false
    }

    log.info(`[AskUserQuestion] 用户取消问题: ${questionId}`)
    pending.reject(new Error('User cancelled'))
    return true
  }

  // ==================== RequestPermission 授权相关函数 ====================

  /**
   * 查找匹配的 running 状态的工具调用
   * 通过 toolName 匹配，返回最近的一个
   */
  function findMatchingToolCall(toolName: string): string | undefined {
    const items = currentDisplayItems.value
    // 从后往前找，找最近的 running 状态的匹配工具
    for (let i = items.length - 1; i >= 0; i--) {
      const item = items[i]
      if (item.displayType === 'toolCall' &&
          (item as ToolCall).toolName === toolName &&
          (item as ToolCall).status === ToolCallStatus.RUNNING) {
        return item.id
      }
    }
    return undefined
  }

  /**
   * 根据 toolCallId 获取对应的权限请求
   */
  function getPermissionForToolCall(toolCallId: string): PendingPermissionRequest | undefined {
    return Array.from(pendingPermissions.values()).find(p => p.matchedToolCallId === toolCallId)
  }

  /**
   * 注册授权请求处理器
   * @param sessionId 会话ID
   */
  function registerPermissionHandler(sessionId: string): () => void {
    log.info(`[RequestPermission] 为会话 ${sessionId} 注册处理器`)

    return aiAgentService.register(sessionId, 'RequestPermission', async (params) => {
      log.info(`[RequestPermission] 收到授权请求:`, params)

      const sessionState = getSessionState(sessionId)
      if (!sessionState) {
        throw new Error(`会话 ${sessionId} 不存在`)
      }

      const { toolName, input, toolUseId, permissionSuggestions } = params

      if (!toolName) {
        throw new Error('缺少 toolName 参数')
      }
      if (!input) {
        throw new Error('缺少 input 参数')
      }

      // 生成唯一ID
      const permissionId = `perm-${Date.now()}-${Math.random().toString(36).substring(2, 8)}`

      // 使用后端传来的 toolUseId（来自 canUseTool 回调），精确关联工具块
      // 如果没有 toolUseId，则回退到按 toolName 匹配
      const matchedToolCallId = toolUseId || findMatchingToolCall(toolName)
      log.info(`[RequestPermission] 匹配工具调用: ${matchedToolCallId || '未找到'}, toolUseId=${toolUseId}`)

      // 返回一个 Promise，当用户响应后 resolve
      return new Promise<{ approved: boolean }>((resolve, reject) => {
        sessionState.pendingPermissions.set(permissionId, {
          id: permissionId,
          sessionId,
          toolName,
          input,
          createdAt: Date.now(),
          matchedToolCallId,
          permissionSuggestions,
          resolve: (response) => {
            sessionState.pendingPermissions.delete(permissionId)
            resolve(response)
          },
          reject: (error) => {
            sessionState.pendingPermissions.delete(permissionId)
            reject(error)
          }
        })

        log.info(`[RequestPermission] 授权请求已加入待处理队列: ${permissionId}, 工具: ${toolName}, matchedToolCallId: ${matchedToolCallId}`)
      })
    })
  }

  /**
   * 获取当前会话的待处理授权请求
   */
  function getCurrentPendingPermissions(): PendingPermissionRequest[] {
    const session = currentSession.value
    if (!session) return []
    return Array.from(session.pendingPermissions.values())
      .sort((a, b) => a.createdAt - b.createdAt)
  }

  /**
   * 响应授权请求
   * @param permissionId 授权请求ID
   * @param response 权限响应（包含是否批准、权限更新、拒绝原因）
   */
  function respondPermission(permissionId: string, response: PermissionResponse): boolean {
    const session = currentSession.value
    if (!session) {
      log.warn(`[RequestPermission] 当前没有激活的会话`)
      return false
    }

    const pending = session.pendingPermissions.get(permissionId)
    if (!pending) {
      log.warn(`[RequestPermission] 授权请求不存在或已响应: ${permissionId}`)
      return false
    }

    log.info(`[RequestPermission] 用户响应授权请求: ${permissionId}, approved=${response.approved}`)

    // 如果选择了权限更新，应用到本地（支持多个权限更新）
    if (response.approved && response.permissionUpdates?.length) {
      for (const update of response.permissionUpdates) {
        // setMode 类型总是处理（更新前端 UI），其他类型只处理 session destination
        if (update.type === 'setMode' || update.destination === 'session') {
          addSessionPermissionRule(pending.sessionId, update)
        }
      }
    }

    pending.resolve(response)
    return true
  }

  /**
   * 应用会话级权限更新（支持所有权限类型）
   */
  function applySessionPermissionUpdate(sessionId: string, update: PermissionUpdate) {
    // setMode 类型不受 destination 限制（仅更新前端 UI 状态）
    if (update.type !== 'setMode' && update.destination !== 'session') return

    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`[SessionPermission] 应用权限更新失败: 会话 ${sessionId} 不存在`)
      return
    }

    const rules = sessionState.permissionRules
    const directories = sessionState.permissionDirectories

    switch (update.type) {
      case 'addRules':
        if (update.rules) {
          for (const r of update.rules) {
            rules.push({
              toolName: r.toolName,
              ruleContent: r.ruleContent,
              behavior: update.behavior || 'allow'
            })
          }
        }
        break

      case 'replaceRules':
        rules.length = 0
        if (update.rules) {
          for (const r of update.rules) {
            rules.push({
              toolName: r.toolName,
              ruleContent: r.ruleContent,
              behavior: update.behavior || 'allow'
            })
          }
        }
        break

      case 'removeRules':
        if (update.rules) {
          for (const r of update.rules) {
            const idx = rules.findIndex(
              rule => rule.toolName === r.toolName && rule.ruleContent === r.ruleContent
            )
            if (idx !== -1) rules.splice(idx, 1)
          }
        }
        break

      case 'setMode':
        if (update.mode) {
          // 仅更新前端 UI，不同步到后端（后端已通过权限响应自动切换）
          updateSessionPermissionMode(sessionId, update.mode as RpcPermissionMode, false)
        }
        break

      case 'addDirectories':
        if (update.directories) {
          for (const dir of update.directories) {
            if (!directories.includes(dir)) {
              directories.push(dir)
            }
          }
        }
        log.info(`[SessionPermission] 添加目录权限: ${sessionId}`, { directories })
        break

      case 'removeDirectories':
        if (update.directories) {
          for (const dir of update.directories) {
            const idx = directories.indexOf(dir)
            if (idx !== -1) directories.splice(idx, 1)
          }
        }
        log.info(`[SessionPermission] 移除目录权限: ${sessionId}`, { directories })
        break

      default:
        log.warn(`[SessionPermission] 未知的更新类型: ${update.type}`)
    }

    log.info(`[SessionPermission] 应用权限更新: ${sessionId}`, { type: update.type })
  }

  // 保留别名以兼容旧代码
  const addSessionPermissionRule = applySessionPermissionUpdate

  /**
   * 检查会话级权限
   */
  function checkSessionPermission(sessionId: string, toolName: string): PermissionBehavior | null {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) return null

    for (const rule of sessionState.permissionRules) {
      if (rule.toolName === toolName) {
        return rule.behavior
      }
    }
    return null
  }

  /**
   * 获取会话的权限规则
   */
  function getSessionPermissionRules(sessionId: string): SessionPermissionRule[] {
    const sessionState = getSessionState(sessionId)
    return sessionState?.permissionRules || []
  }

  /**
   * 获取会话的目录权限
   */
  function getSessionPermissionDirectories(sessionId: string): string[] {
    const sessionState = getSessionState(sessionId)
    return sessionState?.permissionDirectories || []
  }

  /**
   * 清理会话权限（规则和目录）
   */
  function clearSessionPermissionRules(sessionId: string) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`[SessionPermission] 清理会话级权限失败: 会话 ${sessionId} 不存在`)
      return
    }

    sessionState.permissionRules = []
    sessionState.permissionDirectories = []
    log.info(`[SessionPermission] 清理会话级权限: ${sessionId}`)
  }

  /**
   * 清理指定会话的待处理问题
   */
  function clearPendingQuestions(sessionId: string) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`[Session] 清理待处理问题失败: 会话 ${sessionId} 不存在`)
      return
    }

    const count = sessionState.pendingQuestions.size
    sessionState.pendingQuestions.forEach(pending => {
      pending.reject(new Error('Session closed'))
    })
    sessionState.pendingQuestions.clear()
    log.info(`[Session] 清理待处理问题: ${sessionId}, 数量: ${count}`)
  }

  /**
   * 清理指定会话的待处理权限请求
   */
  function clearPendingPermissions(sessionId: string) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`[Session] 清理待处理权限请求失败: 会话 ${sessionId} 不存在`)
      return
    }

    const count = sessionState.pendingPermissions.size
    sessionState.pendingPermissions.forEach(pending => {
      pending.reject(new Error('Session closed'))
    })
    sessionState.pendingPermissions.clear()
    log.info(`[Session] 清理待处理权限: ${sessionId}, 数量: ${count}`)
  }

  /**
   * 更新会话权限模式
   * @param sessionId 会话 ID
   * @param mode 权限模式
   * @param syncToBackend 是否同步到后端（权限对话框选择时为 false，手动切换时为 true）
   */
  function updateSessionPermissionMode(
    sessionId: string,
    mode: RpcPermissionMode,
    syncToBackend: boolean = false
  ) {
    const session = sessions.get(sessionId)
    if (!session) {
      log.warn(`[Session] 更新权限模式失败，会话不存在: ${sessionId}`)
      return
    }

    session.permissionMode = mode
    log.info(`[Session] 更新权限模式 (syncToBackend=${syncToBackend}): ${sessionId} -> ${mode}`)

    if (syncToBackend) {
      setPermissionMode(mode)
    }
  }

  /**
   * 取消授权请求（用户关闭对话框等情况）
   * @param permissionId 授权请求ID
   */
  function cancelPermission(permissionId: string): boolean {
    const session = currentSession.value
    if (!session) {
      log.warn(`[RequestPermission] 当前没有激活的会话`)
      return false
    }

    const pending = session.pendingPermissions.get(permissionId)
    if (!pending) {
      log.warn(`[RequestPermission] 授权请求不存在或已响应: ${permissionId}`)
      return false
    }

    log.info(`[RequestPermission] 用户取消授权请求: ${permissionId}`)
    pending.reject(new Error('User cancelled'))
    return true
  }

  // ============================================================================
  // 错误状态管理
  // ============================================================================

  /**
   * 当前会话的最后一次错误
   */
  const currentLastError = computed(() => {
    return currentSession.value?.lastError ?? null
  })

  /**
   * 清除当前会话的错误
   */
  function clearCurrentError() {
    const session = currentSession.value
    if (session) {
      session.lastError = null
    }
  }

  return {
    sessions,
    activeTabs,
    allSessions,
    currentSessionId,
    currentSession,
    currentMessages,
    currentDisplayItems,
    currentModelId,
    currentConnectionStatus,
    // 会话设置相关
    currentSessionSettings,
    updateCurrentSessionSettings,
    loading,
    messageQueue,
    createSession,
    startNewSession,
    switchSession,
    deleteSession,
    renameSession,
    loadSessionHistory,
    addMessage,
    removeMessage,
    getMessages,
    setSessionGenerating,
    handleMessage,
    sendMessage,
    sendMessageWithContent,
    enqueueMessage,
    editQueueMessage,
    removeFromQueue,
    clearQueue,
    interrupt,
    setModel,
    resumeSession,
    resolveSessionIdentifier,
    // 工具状态管理
    toolCallsMap,
    registerToolCall,
    updateToolResult,
    getToolStatus,
    getToolResult,
    // Tab顺序管理
    updateTabOrder,
    // 请求统计追踪
    startRequestTracking,
    addTokenUsage,
    getRequestStats,
    // AskUserQuestion 相关
    getCurrentPendingQuestions,
    answerQuestion,
    cancelQuestion,
    // RequestPermission 授权相关
    getCurrentPendingPermissions,
    getPermissionForToolCall,
    respondPermission,
    cancelPermission,
    // 会话级权限规则
    addSessionPermissionRule,
    checkSessionPermission,
    getSessionPermissionRules,
    getSessionPermissionDirectories,
    clearSessionPermissionRules,
    // 错误状态管理
    currentLastError,
    clearCurrentError
  }
})
