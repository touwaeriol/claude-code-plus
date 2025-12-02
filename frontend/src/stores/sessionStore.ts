import { ref, computed, reactive } from 'vue'
import { defineStore } from 'pinia'
import { i18n } from '@/i18n'
import { aiAgentService } from '@/services/aiAgentService'
import type { ConnectOptions } from '@/services/aiAgentService'
import type { AgentStreamEvent } from '@/services/AiAgentSession'
import type { Message, ContentBlock, ToolUseBlock, ToolResultBlock, ThinkingBlock } from '@/types/message'
import type { SessionState, PendingMessage } from '@/types/session'
import { convertToDisplayItems, convertMessageToDisplayItems } from '@/utils/displayItemConverter'
import { ConnectionStatus, ToolCallStatus } from '@/types/display'
import type { DisplayItem } from '@/types/display'
import { isAssistantText, isThinkingContent, isUserMessage as isDisplayUserMessage } from '@/types/display'
import { isToolUseBlock, isTextBlock } from '@/utils/contentBlockUtils'
import type { TextBlock } from '@/types/message'
import { loggers } from '@/utils/logger'
import { ideService } from '@/services/ideaBridge'
import { ideaBridge } from '@/services/ideaBridge'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'
import type { ReadToolCall, WriteToolCall, EditToolCall, MultiEditToolCall, ToolCall } from '@/types/display'
import { buildUserMessageContent } from '@/utils/userMessageBuilder'
import { useSettingsStore } from '@/stores/settingsStore'
import { MODEL_CAPABILITIES, BaseModel } from '@/constants/models'
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

  // 存储请求统计追踪信息：sessionId -> { lastUserMessageId, requestStartTime, inputTokens, outputTokens, currentStreamingMessageId }
  const requestTracker = reactive(new Map<string, {
    lastUserMessageId: string
    requestStartTime: number
    inputTokens: number
    outputTokens: number
    currentStreamingMessageId: string | null  // 当前正在流式输出的消息 ID
  }>())

  function buildConnectOptions(overrides: Partial<ConnectOptions> = {}): ConnectOptions {
    // 只传入用户指定的参数，不添加任何默认值
    return {
      print: true,
      outputFormat: 'stream-json',
      verbose: true,
      includePartialMessages: true,
      dangerouslySkipPermissions: true,
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
      toolInputJsonAccumulator: new Map()
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
    return sessions.get(sessionId) || null
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
  const DEFAULT_SESSION_SETTINGS = {
    modelId: MODEL_CAPABILITIES[BaseModel.OPUS_45].modelId,
    thinkingEnabled: MODEL_CAPABILITIES[BaseModel.OPUS_45].defaultThinkingEnabled,
    permissionMode: 'default',
    skipPermissions: true
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
        permissionMode: initialSettings.permissionMode
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
      keys: Object.keys(raw as Record<string, unknown>),
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
   */
  function handleNormalMessage(sessionId: string, sessionState: SessionState, message: Message) {
    // 🔍 打印完整消息内容用于调试
    console.log('🔍 [RPC Message]', {
      role: message.role,
      id: message.id,
      contentLength: message.content.length,
      contentTypes: message.content.map(b => b.type),
      fullContent: JSON.stringify(message.content, null, 2)
    })

    // 确保消息有 id 字段
    if (!message.id) {
      const streamingId = message.role === 'assistant' ? getCurrentStreamingMessageId(sessionId) : null
      message.id = streamingId || generateMessageId(message.role)
    }

    // 检查是否是 tool_result 消息
    const isToolResultMessage = message.role === 'user' &&
      message.content.some((block: ContentBlock) => block.type === 'tool_result')

    // ✅ 流式模式下，assistant 消息已通过 handleStreamEvent 处理
    // RPC 消息中的 assistant 消息可能是重复的，使用消息 ID 判断
    if (message.role === 'assistant') {
      const streamingMessage = findStreamingAssistantMessage(sessionState)
      if (streamingMessage) {
        // 将最终 assistant 消息合并到当前流式消息，避免重复追加
        mergeAssistantMessage(streamingMessage, message)
        streamingMessage.isStreaming = false
        streamingMessage.metadata = { ...streamingMessage.metadata, ...message.metadata }
        syncDisplayItemsForMessage(streamingMessage, sessionState)
        touchSession(sessionId)
        return
      }

      // 检查最后一条消息的 ID 是否相同
      const lastMsg = sessionState.messages[sessionState.messages.length - 1]
      if (lastMsg && lastMsg.id === message.id) {
        log.debug(`跳过重复的 assistant 消息: ${message.id}`)
        return
      }
      // ID 不同，继续处理（可能是 StreamEvent 丢失的情况）
    }

    // 只处理非 assistant 消息
    if (!isToolResultMessage) {
      addMessage(sessionId, message)
      // ✅ addMessage 已经增量更新了 displayItems，不需要再次重建
    } else {
      // tool_result 消息：只更新工具状态，不添加新消息
      // displayItems 中的工具调用对象是响应式的，状态更新会自动反映
      touchSession(sessionId)
    }

    // 处理 tool_result
    if (isToolResultMessage) {
      processToolResults(sessionState, message.content)
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

        // 在 IDEA 环境下，工具调用成功后自动执行 IDEA 操作
        if (wasSuccess && ideaBridge.isInIde()) {
          executeIdeActionForTool(toolCall)
        }
      }
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
          const readCall = toolCall as ReadToolCall
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
          const writeCall = toolCall as WriteToolCall
          const filePath = writeCall.input.file_path || writeCall.input.path || ''
          if (!filePath) break

          await ideService.openFile(filePath)
          log.debug(`[executeIdeActionForTool] WRITE: 打开文件 ${filePath}`)
          break
        }

        case CLAUDE_TOOL_TYPE.EDIT: {
          const editCall = toolCall as EditToolCall
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
          const multiEditCall = toolCall as MultiEditToolCall
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
  function replacePlaceholderMessage(sessionId: string, message: Message): boolean {
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
  function mergeOrAddMessage(sessionId: string, newMessage: Message) {
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
      sessionState.displayItems = convertToDisplayItems(newMessages, sessionState.pendingToolCalls)
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
        const contentBlocks = (event.message?.content ?? []).map(mapRpcContentBlock).filter((b): b is ContentBlock => !!b)
        const existingStreaming = findStreamingAssistantMessage(sessionState)
        const previousId = existingStreaming?.id
        const messageId = event.message?.id || previousId || `assistant-${Date.now()}`

        if (existingStreaming && previousId && previousId !== messageId) {
          // 结束上一条流式消息，开始新消息（保持旧内容不被覆盖）
          existingStreaming.isStreaming = false
          syncDisplayItemsForMessage(existingStreaming, sessionState)

          const newMessage: Message = {
            id: messageId,
            role: 'assistant',
            timestamp: Date.now(),
            content: [],
            isStreaming: true
          }
          sessionState.messages.push(newMessage)
          updateStreamingMessageId(sessionId, messageId)
          mergeInitialAssistantContent(newMessage, contentBlocks)
          syncDisplayItemsForMessage(newMessage, sessionState)
        } else {
          const targetMessage = ensureStreamingAssistantMessage(sessionId, sessionState)
          // 将占位消息 id 更新为后端真实 id
          if (targetMessage.id !== messageId) {
            updateStreamingMessageId(sessionId, messageId)
            targetMessage.id = messageId
          }
          targetMessage.isStreaming = true
          mergeInitialAssistantContent(targetMessage, contentBlocks)
          syncDisplayItemsForMessage(targetMessage, sessionState)
        }

        setSessionGenerating(sessionId, true)
        touchSession(sessionId)
        break
      }

      case 'message_stop': {
        const streamingMessage = findStreamingAssistantMessage(sessionState)
        if (streamingMessage) {
          streamingMessage.isStreaming = false
          syncDisplayItemsForMessage(streamingMessage, sessionState)
        }
        setSessionGenerating(sessionId, false)
        touchSession(sessionId)
        break
      }

      case 'content_block_start': {
        const message = ensureStreamingAssistantMessage(sessionId, sessionState)
        const contentBlock = mapRpcContentBlock(event.content_block)
        if (contentBlock) {
          message.content.push(contentBlock)
          if (contentBlock.type === 'tool_use' && contentBlock.id) {
            toolInputJsonAccumulator.set(contentBlock.id, '')
            registerToolCall(contentBlock as ToolUseBlock)
          }
        }
        syncDisplayItemsForMessage(message, sessionState)
        break
      }

      case 'content_block_delta': {
        const message = ensureStreamingAssistantMessage(sessionId, sessionState)
        const index = event.index
        const delta = event.delta

        if (index >= 0 && index < message.content.length && delta) {
          const contentBlock = message.content[index]
          if (delta.type === 'text_delta' && contentBlock.type === 'text') {
            contentBlock.text += delta.text
          } else if (delta.type === 'text_delta' && contentBlock.type === 'tool_use') {
            const accumulated = toolInputJsonAccumulator.get(contentBlock.id) || ''
            const newAccumulated = accumulated + delta.text
            toolInputJsonAccumulator.set(contentBlock.id, newAccumulated)
            try {
              contentBlock.input = JSON.parse(newAccumulated)
            } catch {
              contentBlock.input = newAccumulated
            }
          } else if (delta.type === 'input_json_delta' && contentBlock.type === 'tool_use') {
            const accumulated = toolInputJsonAccumulator.get(contentBlock.id) || ''
            const newAccumulated = accumulated + delta.partial_json
            toolInputJsonAccumulator.set(contentBlock.id, newAccumulated)
            try { contentBlock.input = JSON.parse(newAccumulated) } catch { /* ignore */ }
          } else if (delta.type === 'thinking_delta' && contentBlock.type === 'thinking') {
            contentBlock.thinking += delta.thinking
          }
        }

        syncDisplayItemsForMessage(message, sessionState)
        break
      }

      case 'content_block_stop': {
        const message = findStreamingAssistantMessage(sessionState)
        if (message && event.index >= 0 && event.index < message.content.length) {
          const block = message.content[event.index]
          if (block.type === 'tool_use') {
            const toolCall = sessionState.pendingToolCalls.get((block as ToolUseBlock).id)
            if (toolCall) {
              // 参数已完整解析，刷新输入快照，等待 tool_result 更新最终状态
              toolCall.input = (block as ToolUseBlock).input || toolCall.input
            }
          }
          // 🔧 修复：工具调用参数完成后，同步更新 displayItems
          syncDisplayItemsForMessage(message, sessionState)
        }
        break
      }
    }
  }

  /**
   * 查找当前处于 streaming 状态的 assistant 消息
   */
  function findStreamingAssistantMessage(sessionState: SessionState): Message | null {
    const tracker = requestTracker.get(sessionState.id)
    const streamingId = tracker?.currentStreamingMessageId
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

    const tracker = requestTracker.get(sessionId)
    const placeholderId = tracker?.currentStreamingMessageId || `assistant-${Date.now()}`
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
  function mergeAssistantMessage(target: Message, incoming: Message) {
    const merged: ContentBlock[] = [...target.content]

    incoming.content.forEach(block => {
      if (block.type === 'tool_use') {
        const idx = merged.findIndex(
          item => item.type === 'tool_use' && (item as ToolUseBlock).id === (block as ToolUseBlock).id
        )
        if (idx >= 0) {
          merged[idx] = { ...merged[idx], ...block }
        } else {
          merged.push(block)
        }
      } else if (block.type === 'thinking') {
        const idx = merged.findIndex(item => item.type === 'thinking')
        if (idx >= 0) {
          const existing = merged[idx] as ThinkingBlock
          merged[idx] = { ...existing, ...block, thinking: (block as ThinkingBlock).thinking || existing.thinking }
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
  function dropAssistantDisplayItemsById(sessionState: SessionState, messageId: string) {
    sessionState.displayItems = sessionState.displayItems.filter(item => {
      if (item.displayType === 'assistantText' || item.displayType === 'thinking') {
        return !item.id.startsWith(`${messageId}-`)
      }
      return true
    })
  }

  /**
   * 同步 displayItems 以反映消息内容的变化
   * 
   * 当流式更新修改了 message.content 时，需要更新 displayItems 中对应的对象
   * 
   * 🔧 关键：按照 message.content 的顺序来同步 displayItems，确保顺序正确
   */
  function syncDisplayItemsForMessage(message: Message, sessionState: SessionState) {
    // 1. 找到该消息对应的所有 displayItems 的索引范围
    let messageStartIndex = -1
    let messageEndIndex = -1
    
    for (let i = 0; i < sessionState.displayItems.length; i++) {
      const item = sessionState.displayItems[i]
      const isMessageItem =
        (item.displayType === 'assistantText' && item.id.startsWith(`${message.id}-text-`)) ||
        (item.displayType === 'thinking' && item.id.startsWith(`${message.id}-thinking-`)) ||
        (item.displayType === 'toolCall' && message.content.some(block =>
          isToolUseBlock(block) && block.id === item.id
        ))
      
      if (isMessageItem) {
        if (messageStartIndex === -1) {
          messageStartIndex = i
        }
        messageEndIndex = i
      } else if (messageStartIndex !== -1) {
        // 已经找到了消息的结束位置
        break
      }
    }

    // 2. 收集所有文本块的索引（用于标记最后一个文本块）
    const textBlockIndices: number[] = []
    message.content.forEach((block, idx) => {
      if (isTextBlock(block) && block.text.trim()) {
        textBlockIndices.push(idx)
      }
    })
    const lastTextBlockIndex = textBlockIndices.length > 0 ? textBlockIndices[textBlockIndices.length - 1] : -1

    // 3. 按照 message.content 的顺序，构建新的 displayItems
    const newDisplayItems: DisplayItem[] = []
    const existingItemsMap = new Map<string, DisplayItem>()
    
    // 收集现有的 displayItems（用于复用）
    if (messageStartIndex !== -1 && messageEndIndex !== -1) {
      for (let i = messageStartIndex; i <= messageEndIndex; i++) {
        const item = sessionState.displayItems[i]
        existingItemsMap.set(item.id, item)
      }
    }

    // 按照 message.content 的顺序构建
    const skipTextIndices = computeToolTextSkip(message)

    for (let blockIdx = 0; blockIdx < message.content.length; blockIdx++) {
      const block = message.content[blockIdx]

      if (isTextBlock(block) && block.text.trim()) {
        if (skipTextIndices.has(blockIdx)) {
          continue
        }
        const textBlock = block as TextBlock
        const expectedId = `${message.id}-text-${blockIdx}`
        const existingItem = existingItemsMap.get(expectedId)
        
        if (existingItem && isAssistantText(existingItem)) {
          newDisplayItems.push({
            ...existingItem,
            content: textBlock.text,
            isLastInMessage: blockIdx === lastTextBlockIndex
          })
        } else {
          // 创建新的文本块
          const isLastTextBlock = blockIdx === lastTextBlockIndex
          const assistantText = {
            displayType: 'assistantText' as const,
            id: expectedId,
            content: textBlock.text,
            timestamp: message.timestamp,
            isLastInMessage: isLastTextBlock,
            stats: undefined
          }
          newDisplayItems.push(assistantText)
        }
      } else if (block.type === 'thinking') {
        const expectedId = `${message.id}-thinking-${blockIdx}`
        const existingItem = existingItemsMap.get(expectedId)

        if (existingItem && isThinkingContent(existingItem)) {
          newDisplayItems.push({
            ...existingItem,
            content: block.thinking || ''
          })
        } else {
          newDisplayItems.push({
            displayType: 'thinking' as const,
            id: expectedId,
            content: block.thinking || '',
            signature: block.signature,
            timestamp: message.timestamp
          })
        }
      } else if (isToolUseBlock(block)) {
        // 工具调用块：复用现有的或创建新的
        const existingItem = existingItemsMap.get(block.id) as ToolCall | undefined
        const toolUseBlock = block as ToolUseBlock

        if (existingItem && existingItem.displayType === 'toolCall') {
          // 🔧 修复：使用 Object.assign 更新属性，保持引用一致性
          // 这样 pendingToolCalls 和 displayItems 共享同一对象，后续状态更新能正确反映
          if (toolUseBlock.input !== undefined &&
              Object.keys(toolUseBlock.input as Record<string, unknown>).length > 0) {
            Object.assign(existingItem, { input: toolUseBlock.input })
          }
          newDisplayItems.push(existingItem)
        } else {
          // 创建新的工具调用
          const toolCall = convertMessageToDisplayItems(message, sessionState.pendingToolCalls)
            .find(item => item.displayType === 'toolCall' && item.id === block.id)
          if (toolCall) {
            newDisplayItems.push(toolCall)
          }
        }
      }
    }

    // 4. 替换旧的 displayItems
    if (messageStartIndex !== -1 && messageEndIndex !== -1) {
      // 删除旧的 displayItems，插入新的
      sessionState.displayItems.splice(messageStartIndex, messageEndIndex - messageStartIndex + 1, ...newDisplayItems)
    } else {
      // 如果找不到旧的位置，直接追加到末尾
      sessionState.displayItems.push(...newDisplayItems)
    }

    // 5. 触发响应式更新
    sessionState.displayItems = [...sessionState.displayItems]
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
    const tracker = requestTracker.get(sessionId)

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

    // 标记生成完成
    setSessionGenerating(sessionId, false)
    requestTracker.delete(sessionId)
    log.debug('handleResultMessage: 请求完成, 清除追踪信息')

    // 处理队列中的下一条消息
    processNextQueuedMessage()
  }

  /**
   * 开始追踪请求（发送用户消息时调用）
   */
  function startRequestTracking(sessionId: string, userMessageId: string, streamingMessageId: string) {
    log.debug(`startRequestTracking: sessionId=${sessionId}, userMessageId=${userMessageId}`)
    requestTracker.set(sessionId, {
      lastUserMessageId: userMessageId,
      requestStartTime: Date.now(),
      inputTokens: 0,
      outputTokens: 0,
      currentStreamingMessageId: streamingMessageId
    })

    // 设置 isGenerating = true（开始生成）
    setSessionGenerating(sessionId, true)

    // 更新 displayItem 的 isStreaming 状态
    const sessionState = getSessionState(sessionId)
    if (sessionState) {
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
  }

  /**
   * 累加 token 使用量（用于增量更新）
   */
  function addTokenUsage(sessionId: string, inputTokens: number, outputTokens: number) {
    const tracker = requestTracker.get(sessionId)
    if (tracker) {
      tracker.inputTokens += inputTokens
      tracker.outputTokens += outputTokens
    }
  }

  /**
   * 设置 token 使用量（用于累计值更新，如 message_delta.usage）
   */
  function setTokenUsage(sessionId: string, inputTokens: number, outputTokens: number) {
    const tracker = requestTracker.get(sessionId)
    if (tracker) {
      tracker.inputTokens = inputTokens
      tracker.outputTokens = outputTokens
    }
  }

  /**
   * 获取当前请求的统计信息（供组件使用）
   */
  function getRequestStats(sessionId: string) {
    const tracker = requestTracker.get(sessionId)
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
    const tracker = requestTracker.get(sessionId)
    return tracker?.currentStreamingMessageId ?? null
  }

  /**
   * 更新当前流式消息的 ID（当后端返回真实 ID 时调用）
   */
  function updateStreamingMessageId(sessionId: string, newMessageId: string) {
    const tracker = requestTracker.get(sessionId)
    if (tracker) {
      log.debug(`updateStreamingMessageId: ${tracker.currentStreamingMessageId} -> ${newMessageId}`)
      tracker.currentStreamingMessageId = newMessageId
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

      // 从列表中移除（SessionState 会自动删除）
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
      content as unknown as import('../services/ClaudeSession').ContentBlock[]
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
      requestTracker.delete(sessionId)
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
      permissionMode: settings.permissionMode,
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
      name: (block as any).toolName || block.name,
      status: 'running',
      startTime: Date.now()
    })
    log.debug(`注册工具调用: ${(block as any).toolName || block.name} (${block.id})`)
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
    requestTracker
  }
})
