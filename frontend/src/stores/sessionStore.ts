import { ref, computed, reactive } from 'vue'
import { defineStore } from 'pinia'
import { claudeService } from '@/services/claudeService'
import type { ConnectOptions } from '@/services/claudeService'
import type { Message, ContentBlock, ToolUseBlock, ToolResultBlock } from '@/types/message'
import type { SessionState } from '@/types/session'
import { convertToDisplayItems } from '@/utils/displayItemConverter'
import { ConnectionStatus, ToolCallStatus } from '@/types/display'
import type { StreamEvent } from '@/types/streamEvent'
import { parseStreamEventData } from '@/utils/streamEventHandler'
import { processStreamEvent, type StreamEventContext, type StreamEventProcessResult } from '@/utils/streamEventProcessor'
import { isToolUseBlock } from '@/utils/contentBlockUtils'
import { loggers } from '@/utils/logger'

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
  | { kind: 'stream_event'; data: any }
  | { kind: 'result'; data: any }

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

  // 向后兼容：保留旧的接口
  const currentModelId = ref<string | null>(null)
  const sessionModelIds = ref<Map<string, string>>(new Map())
  const connectionStatuses = ref<Map<string, ConnectionStatus>>(new Map())
  const toolCallsMap = ref<Map<string, ToolCallState>>(new Map())
  const externalSessionIndex = reactive(new Map<string, string>())
  
  // 存储每个工具调用块的累积 JSON 字符串（用于 input_json_delta 增量更新）
  const toolInputJsonAccumulator = reactive(new Map<string, string>())

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
    modelId: string | null
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
      modelId,
      connection: null,
      isGenerating: false,
      uiState: {
        inputText: '',
        contexts: [],
        scrollPosition: 0
      }
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

  /**
   * 创建新会话
   */
  async function createSession(name?: string) {
    try {
      log.info('创建新会话...')
      const options = buildConnectOptions()

      // 设置连接状态
      connectionStatuses.value.set('pending', ConnectionStatus.CONNECTING)

      // 使用 claudeService 创建会话
      const sessionId = await claudeService.connect(options, (rawMessage: any) => {
        const normalized = normalizeRpcMessage(rawMessage)
        if (normalized) {
          handleMessage(sessionId, normalized)
        }
      })

      const newSessionState = createSessionState(
        sessionId,
        name || `会话 ${new Date().toLocaleString()}`,
        options.model || null
      )

      // 添加到 sessions Map
      sessions.set(sessionId, newSessionState)

      // 设置连接状态（向后兼容）
      connectionStatuses.value.delete('pending')
      connectionStatuses.value.set(sessionId, ConnectionStatus.CONNECTED)

      // 切换到新会话
      currentSessionId.value = sessionId
      sessionModelIds.value.set(sessionId, options.model || '')
      currentModelId.value = options.model || null

      log.info(`会话已创建: ${sessionId}`)
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
      const options = buildConnectOptions({
        continueConversation: true,
        resume: externalSessionId
      })

      connectionStatuses.value.set('pending', ConnectionStatus.CONNECTING)
      const sessionId = await claudeService.connect(options, (rawMessage: any) => {
        const normalized = normalizeRpcMessage(rawMessage)
        if (normalized) {
          handleMessage(sessionId, normalized)
        }
      })

      const resumeLabel = externalSessionId.slice(-8) || externalSessionId
      const resumedSessionState = createSessionState(
        sessionId,
        name || `历史会话 ${resumeLabel}`,
        options.model || null
      )

      sessions.set(sessionId, resumedSessionState)
      connectionStatuses.value.delete('pending')
      connectionStatuses.value.set(sessionId, ConnectionStatus.CONNECTED)

      sessionModelIds.value.set(sessionId, options.model || '')
      currentModelId.value = options.model || null
      currentSessionId.value = sessionId

      linkExternalSessionId(externalSessionId, sessionId)
      log.info(`历史会话已恢复: ${sessionId}`)
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
  function normalizeRpcMessage(raw: any): NormalizedRpcMessage | null {
    if (!raw || typeof raw !== 'object') {
      return null
    }

    const type = raw.type || raw.role

    // 处理 stream_event 消息
    if (type === 'stream_event') {
      return { kind: 'stream_event', data: raw }
    }

    // 处理 result 消息（包含 usage 统计信息）
    if (type === 'result') {
      return { kind: 'result', data: raw }
    }

    // 处理 assistant 消息
    if (type === 'assistant') {
      const content: ContentBlock[] = Array.isArray(raw.content) ? raw.content : []
      const timestamp = typeof raw.timestamp === 'number' ? raw.timestamp : Date.now()

      const normalized: Message = {
        id: raw.id || '',
        role: 'assistant',
        content,
        timestamp,
        tokenUsage: raw.token_usage
      }

      return { kind: 'message', data: normalized }
    }

    // 处理 user 消息（包含 tool_result）
    if (type === 'user') {
      const content: ContentBlock[] = Array.isArray(raw.content) ? raw.content : []
      const hasToolResult = content.some((block: ContentBlock) => block.type === 'tool_result')

      if (hasToolResult) {
        const timestamp = typeof raw.timestamp === 'number' ? raw.timestamp : Date.now()
        const normalized: Message = {
          id: raw.id || '',
          role: 'user',
          content,
          timestamp
        }
        return { kind: 'message', data: normalized }
      }
    }

    // 其他类型的消息忽略
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
    // 确保消息有 id 字段
    if (!message.id) {
      message.id = generateMessageId(message.role)
    }

    // 检查是否是 tool_result 消息
    const isToolResultMessage = message.role === 'user' &&
      message.content.some((block: ContentBlock) => block.type === 'tool_result')

    // 处理消息（添加到消息列表）
    if (message.role === 'assistant') {
      const replaced = replacePlaceholderMessage(sessionId, message)
      if (!replaced) {
        mergeOrAddMessage(sessionId, message)
      }
    } else if (!isToolResultMessage) {
      addMessage(sessionId, message)
    }

    // 更新 displayItems
    sessionState.displayItems = convertToDisplayItems(sessionState.messages, sessionState.pendingToolCalls)
    touchSession(sessionId)

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
        toolCall.status = result.is_error ? ToolCallStatus.FAILED : ToolCallStatus.SUCCESS
        toolCall.endTime = Date.now()
        toolCall.result = result.is_error
          ? { type: 'error', error: typeof result.content === 'string' ? result.content : JSON.stringify(result.content) }
          : { type: 'success', output: typeof result.content === 'string' ? result.content : JSON.stringify(result.content) }
      }
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
    sessionState.displayItems = convertToDisplayItems(newMessages, sessionState.pendingToolCalls)

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
      sessionState.displayItems = convertToDisplayItems(newMessages, sessionState.pendingToolCalls)
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
    if ((newMsg as any).isStreaming !== undefined) {
      (merged as any).isStreaming = (newMsg as any).isStreaming
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
   * 使用模块化的 stream event 处理器，将复杂的事件处理逻辑委托给专门的处理模块
   */
  function handleStreamEvent(sessionId: string, streamEventData: any) {
    const sessionState = getSessionState(sessionId)
    if (!sessionState) {
      log.warn(`handleStreamEvent: 会话 ${sessionId} 不存在`)
      return
    }

    // 解析 stream event 数据
    const parsed = parseStreamEventData(streamEventData)
    if (!parsed || !parsed.event) {
      log.warn('handleStreamEvent: 无效的 event 数据')
      return
    }

    const event: StreamEvent = parsed.event
    const eventType = event.type

    // 更新 token 使用量（message_delta.usage 是累计值，不是增量）
    if (eventType === 'message_delta' && (event as any).usage) {
      const usage = (event as any).usage
      // 使用 setTokenUsage 替代 addTokenUsage，因为 usage 是累计值
      setTokenUsage(sessionId, usage.input_tokens || 0, usage.output_tokens || 0)
    }

    // 构建处理上下文
    const context: StreamEventContext = {
      messages: sessionState.messages,
      toolInputJsonAccumulator: toolInputJsonAccumulator,
      registerToolCall: registerToolCall
    }

    // 使用模块化处理器处理事件
    const result: StreamEventProcessResult = processStreamEvent(event, context)

    // 根据处理结果更新状态
    if (result.shouldSetGenerating !== null) {
      setSessionGenerating(sessionId, result.shouldSetGenerating)
    }

    // 更新消息数组（如果消息被修改）
    if (result.messageUpdated && result.shouldUpdateMessages) {
      const lastAssistantMessage = context.messages
        .slice()
        .reverse()
        .find(m => m.role === 'assistant')
      
      if (lastAssistantMessage) {
        const messageIndex = sessionState.messages.findIndex(m => m.id === lastAssistantMessage.id)
        if (messageIndex !== -1) {
          const newMessages = [...sessionState.messages]
          newMessages[messageIndex] = { ...lastAssistantMessage }
          sessionState.messages = newMessages
        }
      }
    }

    // 更新 displayItems（如果需要）
    if (result.shouldUpdateDisplayItems) {
      sessionState.displayItems = convertToDisplayItems(
        sessionState.messages,
        sessionState.pendingToolCalls
      )
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
  function handleResultMessage(sessionId: string, resultData: any) {
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

    if (resultData.usage) {
      inputTokens = resultData.usage.input_tokens || 0
      outputTokens = resultData.usage.output_tokens || 0
    }

    // 计算请求时长
    const durationMs = resultData.duration_ms ||
      (tracker ? Date.now() - tracker.requestStartTime : 0)

    log.debug(`handleResultMessage: 统计信息 duration=${durationMs}ms, tokens=${inputTokens}/${outputTokens}`)

    // 更新对应用户消息的统计信息
    if (tracker?.lastUserMessageId) {
      // 在 displayItems 中找到对应的用户消息并更新
      const displayItemIndex = sessionState.displayItems.findIndex(
        item => item.id === tracker.lastUserMessageId && item.type === 'userMessage'
      )

      if (displayItemIndex !== -1) {
        const userMessage = sessionState.displayItems[displayItemIndex] as any
        userMessage.requestStats = {
          requestDuration: durationMs,
          inputTokens,
          outputTokens
        }
        userMessage.isStreaming = false
        log.debug(`handleResultMessage: 更新用户消息统计信息`)

        // 触发响应式更新
        sessionState.displayItems = [...sessionState.displayItems]
      }
    }

    // 标记生成完成
    setSessionGenerating(sessionId, false)
    requestTracker.delete(sessionId)
    log.debug('handleResultMessage: 请求完成, 清除追踪信息')
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
        item => item.id === userMessageId && item.type === 'userMessage'
      )
      if (displayItemIndex !== -1) {
        const userMessage = sessionState.displayItems[displayItemIndex] as any
        userMessage.isStreaming = true
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
      await claudeService.disconnect(sessionId)

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
  async function loadSessionHistory(sessionId: string): Promise<Message[]> {
    loading.value = true
    try {
      log.debug(`加载历史消息: ${sessionId}`)
      // getHistory 返回的是简化的 Message 类型，需要转换
      // TODO: 在新架构中，历史消息应该通过 resume 会话时的 stream event 获取
      const messages = await claudeService.getHistory(sessionId) as any as Message[]
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

    await claudeService.sendMessage(currentSessionId.value, message)
  }

  /**
   * 发送消息 (支持图片，stream-json 格式)
   *
   * @param content 内容块数组 [{ type: 'text', text: '...' }, { type: 'image', data: '...', mimeType: '...' }]
   */
  async function sendMessageWithContent(content: import('../services/ClaudeSession').ContentBlock[]): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    await claudeService.sendMessageWithContent(currentSessionId.value, content)
  }

  /**
   * 中断当前操作
   */
  async function interrupt(): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    await claudeService.interrupt(currentSessionId.value)
  }

  /**
   * 设置当前会话的模型
   */
  async function setModel(model: string): Promise<void> {
    if (!currentSessionId.value) {
      throw new Error('当前没有活跃的会话')
    }

    await claudeService.setModel(currentSessionId.value, model)

    // 更新本地记录
    sessionModelIds.value.set(currentSessionId.value, model)
    currentModelId.value = model

    const session = getSessionState(currentSessionId.value)
    if (session) {
      session.modelId = model
    }
  }

  /**
   * 注册工具调用
   * 当收到 tool_use 消息时调用
   */
  function registerToolCall(block: ToolUseBlock) {
    toolCallsMap.value.set(block.id, {
      id: block.id,
      name: block.name,
      status: 'running',
      startTime: Date.now()
    })
    log.debug(`注册工具调用: ${block.name} (${block.id})`)
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
    currentDisplayItems,  // 新增
    currentModelId,
    currentConnectionStatus,
    loading,
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
    requestTracker  // 暴露给组件访问实时数据
  }
})
