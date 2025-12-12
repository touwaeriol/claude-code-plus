/**
 * 消息处理 Composable
 *
 * 每个 Tab 实例独立持有自己的消息状态
 * 提供消息处理、流式渲染、发送队列等功能
 *
 * 这是最复杂的 Composable，负责：
 * - 消息管理（messages, displayItems）
 * - 流式消息处理（handleStreamEvent）
 * - 结果消息处理（handleResultMessage）
 * - 用户消息发送（enqueueMessage）
 * - 消息队列管理
 */

import { ref, reactive, computed } from 'vue'
import { i18n } from '@/i18n'
import type { Message, ContentBlock, ToolUseBlock, ToolResultBlock, ToolUseContent } from '@/types/message'
import type { PendingMessage } from '@/types/session'
import type { DisplayItem, AssistantText, ThinkingContent, UserMessage, ToolCall } from '@/types/display'
import { isUserMessage as isDisplayUserMessage } from '@/types/display'
import { convertMessageToDisplayItems, createToolCall } from '@/utils/displayItemConverter'
import { buildUserMessageContent } from '@/utils/userMessageBuilder'
import { mapRpcContentBlock } from '@/utils/rpcMappers'
import type { RpcStreamEvent, RpcResultMessage } from '@/types/rpc'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'
import type { ClaudeReadToolCall, ClaudeWriteToolCall, ClaudeEditToolCall, ClaudeMultiEditToolCall } from '@/types/display'
import { ideService, ideaBridge } from '@/services/ideaBridge'
import { loggers } from '@/utils/logger'
import type { SessionToolsInstance } from './useSessionTools'
import type { SessionStatsInstance } from './useSessionStats'
import { ChunkedMessageStore } from '@/utils/ChunkedMessageStore'
import { MESSAGE_WINDOW_TOTAL } from '@/constants/messageWindow'

const log = loggers.session

/**
 * 消息处理 Composable
 *
 * 依赖注入：
 * - tools: 工具调用管理实例
 * - stats: 统计管理实例
 *
 * 注意：sendMessageFn 需要在连接建立后通过 setSendMessageFn 设置
 */
export function useSessionMessages(
  tools: SessionToolsInstance,
  stats: SessionStatsInstance
) {
  // ========== 核心状态 ==========

  /**
   * 原始消息列表（来自后端，用于持久化）
   */
  const messages = reactive<Message[]>([])

  /** 
   * 显示项列表（用于 UI 展示）
   */
  const displayItems = reactive<DisplayItem[]>([])
  const DISPLAY_WINDOW_TOTAL = MESSAGE_WINDOW_TOTAL
  const STORE_RETENTION = Number.MAX_SAFE_INTEGER // 保留全量，窗口单独控制
  const displayStore = new ChunkedMessageStore<DisplayItem>({
    windowSize: STORE_RETENTION,
    dedupe: true,
    keySelector: (item) => (item as any)?.id
  })
  // 子代理挂起消息缓存：Task toolUseId -> DisplayItem[]
  const pendingSubagentMessages = new Map<string, DisplayItem[]>()
  // 子代理流式状态：Task toolUseId -> { messageId, content: ContentBlock[], timestamp }
  const subagentStreamingState = new Map<string, { messageId: string; content: ContentBlock[]; timestamp: number }>()

  function refreshDisplayWindow(): void {
    const windowItems = displayStore.getWindow(DISPLAY_WINDOW_TOTAL)
    displayItems.splice(0, displayItems.length, ...windowItems)
  }

  function pushDisplayItems(items: DisplayItem[]): void {
    if (items.length === 0) return
    displayStore.pushBatch(items)
    refreshDisplayWindow()
  }

  function prependDisplayItems(items: DisplayItem[]): void {
    if (items.length === 0) return
    displayStore.prependBatch(items)
    refreshDisplayWindow()
  }

  function clearDisplayItems(): void {
    displayStore.clear()
    displayItems.splice(0, displayItems.length)
  }

  /**
   * 将子代理 DisplayItem 附加到对应 Task
   *
   * 注意：过滤掉 userMessage 类型，因为子代理的用户提示已经在 Task 工具的 prompt 参数中显示
   */
  function appendSubagentDisplayItems(taskToolUseId: string, items: DisplayItem[]): void {
    if (!items || items.length === 0) return
    // 过滤掉 userMessage（子代理的 prompt 已在 Task 参数中显示）
    const filteredItems = items.filter(item => item.displayType !== 'userMessage')
    if (filteredItems.length === 0) return

    const taskCall = tools.pendingToolCalls.get(taskToolUseId) as ToolCall | undefined
    if (!taskCall) {
      const pending = pendingSubagentMessages.get(taskToolUseId) ?? []
      pending.push(...filteredItems)
      pendingSubagentMessages.set(taskToolUseId, pending)
      return
    }
    if (!taskCall.subagentMessages) {
      taskCall.subagentMessages = []
    }
    taskCall.subagentMessages.push(...filteredItems)
  }

  /**
   * Task 刚创建时回填挂起的子代理消息
   */
  function flushPendingSubagentMessages(taskToolUseId: string, taskCall: ToolCall) {
    const pending = pendingSubagentMessages.get(taskToolUseId)
    if (pending && pending.length > 0) {
      taskCall.subagentMessages = (taskCall.subagentMessages || []).concat(pending)
      pendingSubagentMessages.delete(taskToolUseId)
    }
  }

  /**
   * 查找子代理显示项
   */
  function findSubagentDisplayItem(taskToolUseId: string, displayId: string): DisplayItem | undefined {
    const taskCall = tools.pendingToolCalls.get(taskToolUseId) as ToolCall | undefined
    if (!taskCall || !taskCall.subagentMessages) return undefined
    return taskCall.subagentMessages.find(item => (item as any).id === displayId)
  }

  /**
   * 更新子代理文本显示项
   */
  function updateSubagentTextDisplay(taskToolUseId: string, displayId: string, text: string) {
    const item = findSubagentDisplayItem(taskToolUseId, displayId) as AssistantText | undefined
    if (item && item.displayType === 'assistantText') {
      item.content = text
      return
    }
    // 如果不存在，创建一个新的文本显示项
    const newItem: AssistantText = {
      displayType: 'assistantText',
      id: displayId,
      content: text,
      timestamp: Date.now(),
      isStreaming: true
    }
    appendSubagentDisplayItems(taskToolUseId, [newItem])
  }

  /**
   * 更新子代理思考显示项
   */
  function updateSubagentThinkingDisplay(taskToolUseId: string, displayId: string, content: string, signature?: string) {
    const item = findSubagentDisplayItem(taskToolUseId, displayId) as ThinkingContent | undefined
    if (item && item.displayType === 'thinking') {
      item.content = content
      if (signature) item.signature = signature
      return
    }
    const newItem: ThinkingContent = {
      displayType: 'thinking',
      id: displayId,
      content,
      signature,
      timestamp: Date.now()
    }
    appendSubagentDisplayItems(taskToolUseId, [newItem])
  }

  /**
   * 消息队列（待发送消息）
   */
  const messageQueue = ref<PendingMessage[]>([])

  /**
   * 是否正在生成
   */
  const isGenerating = ref(false)

  /**
   * 最后一次错误信息
   */
  const lastError = ref<string | null>(null)

  // ========== 函数注入 ==========

  /**
   * 发送消息函数（由 Tab 连接建立后注入）
   */
  let sendMessageFn: ((content: ContentBlock[]) => Promise<void>) | null = null

  /**
   * 处理队列前的回调（由 Tab 注入，用于应用 pending settings）
   */
  let beforeProcessQueueFn: (() => Promise<void>) | null = null

  /**
   * 设置发送消息函数
   */
  function setSendMessageFn(fn: (content: ContentBlock[]) => Promise<void>): void {
    sendMessageFn = fn
  }

  /**
   * 设置处理队列前的回调
   */
  function setBeforeProcessQueueFn(fn: () => Promise<void>): void {
    beforeProcessQueueFn = fn
  }


  // ========== 计算属性 ==========

  /**
   * 消息数量
   */
  const messageCount = computed(() => messages.length)

  /**
   * 显示项数量
   */
  const displayItemCount = computed(() => displayItems.length)

  /**
   * 队列中的消息数量
   */
  const queueLength = computed(() => messageQueue.value.length)

  /**
   * 是否有消息
   */
  const hasMessages = computed(() => messages.length > 0)

  // ========== 消息处理核心方法 ==========

  /**
   * 处理流式事件
   *
   * 直接解析和处理 stream event 数据，不依赖外部模块
   */
  function handleStreamEvent(streamEventData: RpcStreamEvent): void {
    // 生成状态门控：仅当 isGenerating=true 时处理流事件
    if (!isGenerating.value) {
      log.debug('[useSessionMessages] isGenerating=false，忽略流式事件')
      return
    }

    // 如果请求已完成（无活动请求），忽略延迟到达的流式事件
    if (!stats.hasActiveRequest.value) {
      log.debug('[useSessionMessages] 无活动请求，忽略延迟的流式事件')
      return
    }

    // 子代理流式事件：路由到对应 Task 卡片
    if (streamEventData.parentToolUseId) {
      handleSubagentStreamEvent(streamEventData)
      return
    }

    const event = streamEventData.event
    if (!event) {
      log.warn('[useSessionMessages] 无效的 event 数据:', streamEventData)
      return
    }

    const eventType = event.type
    log.debug(`[useSessionMessages] 处理事件: ${eventType}`)

    // 更新 token 使用量
    if (eventType === 'message_delta' && 'usage' in event && event.usage) {
      const usage = event.usage as { input_tokens?: number; output_tokens?: number; inputTokens?: number; outputTokens?: number }
      stats.addTokenUsage(
        usage.input_tokens ?? usage.inputTokens ?? 0,
        usage.output_tokens ?? usage.outputTokens ?? 0
      )
    }

    // 处理不同类型的事件
    switch (eventType) {
      case 'message_start':
        handleMessageStart(event)
        break

      case 'message_stop':
        handleMessageStop()
        break

      case 'content_block_start':
        handleContentBlockStart(event)
        break

      case 'content_block_delta':
        handleContentBlockDelta(event)
        break

      case 'content_block_stop':
        handleContentBlockStop(event)
        break
    }
  }

  /**
   * 处理 message_start 事件
   */
  function handleMessageStart(event: any): void {
    const contentBlocks = (event.message?.content ?? [])
      .map(mapRpcContentBlock)
      .filter((b: ContentBlock | null): b is ContentBlock => !!b)

    const existingStreaming = findStreamingAssistantMessage()
    const previousId = existingStreaming?.id
    const messageId = event.message?.id || previousId || `assistant-${Date.now()}`

    log.debug('[message_start]', {
      messageId,
      previousId,
      hasExistingStreaming: !!existingStreaming,
      initialContentLength: contentBlocks.length
    })

    if (existingStreaming && previousId && previousId !== messageId) {
      // 结束上一条流式消息，开始新消息
      existingStreaming.isStreaming = false

      const newMessage: Message = {
        id: messageId,
        role: 'assistant',
        timestamp: Date.now(),
        content: [],
        isStreaming: true
      }
      messages.push(newMessage)
      stats.setStreamingMessageId(messageId)

      // 合并初始内容（如果有的话）
      if (contentBlocks.length > 0) {
        mergeInitialAssistantContent(newMessage, contentBlocks)
      }
    } else {
      const targetMessage = ensureStreamingAssistantMessage()
      // 将占位消息 id 更新为后端真实 id
      if (targetMessage.id !== messageId) {
        stats.setStreamingMessageId(messageId)
        targetMessage.id = messageId
      }
      targetMessage.isStreaming = true

      // 合并初始内容（如果有的话）
      if (contentBlocks.length > 0) {
        mergeInitialAssistantContent(targetMessage, contentBlocks)
      }
    }

    isGenerating.value = true
    touchMessages()
  }

  /**
   * 子代理流式事件处理
   */
  function handleSubagentStreamEvent(streamEventData: RpcStreamEvent): void {
    const taskId = streamEventData.parentToolUseId as string
    const event = streamEventData.event
    if (!event) return

    switch (event.type) {
      case 'message_start': {
        const messageId = (event as any).message?.id || `subagent-${Date.now()}`
        const timestamp = Date.now()
        subagentStreamingState.set(taskId, { messageId, content: [], timestamp })
        // 初始化已有内容块
        const contentBlocks = ((event as any).message?.content ?? [])
          .map(mapRpcContentBlock)
          .filter((b: ContentBlock | null): b is ContentBlock => !!b)
        contentBlocks.forEach((block, idx) => {
          if (block.type === 'text') {
            const displayId = `${messageId}-text-${idx}`
            appendSubagentDisplayItems(taskId, [{
              displayType: 'assistantText',
              id: displayId,
              content: (block as any).text || '',
              timestamp,
              isStreaming: true
            } as AssistantText])
          } else if (block.type === 'thinking') {
            const displayId = `${messageId}-thinking-${idx}`
            appendSubagentDisplayItems(taskId, [{
              displayType: 'thinking',
              id: displayId,
              content: (block as any).thinking || '',
              signature: (block as any).signature,
              timestamp
            } as ThinkingContent])
          } else if (block.type === 'tool_use' && (block as any).id) {
            const toolCall = createToolCall(block as unknown as ToolUseContent, tools.pendingToolCalls)
            appendSubagentDisplayItems(taskId, [toolCall])
          }
        })
        break
      }
      case 'content_block_start': {
        const state = subagentStreamingState.get(taskId)
        if (!state) break
        const blockIndex = (event as any).index
        const contentBlock = mapRpcContentBlock((event as any).content_block)
        if (!contentBlock) break
        while (state.content.length < blockIndex) {
          state.content.push({ type: 'text', text: '' } as any)
        }
        state.content[blockIndex] = contentBlock
        if (contentBlock.type === 'text') {
          const displayId = `${state.messageId}-text-${blockIndex}`
          appendSubagentDisplayItems(taskId, [{
            displayType: 'assistantText',
            id: displayId,
            content: '',
            timestamp: state.timestamp,
            isStreaming: true
          } as AssistantText])
        } else if (contentBlock.type === 'thinking') {
          const displayId = `${state.messageId}-thinking-${blockIndex}`
          appendSubagentDisplayItems(taskId, [{
            displayType: 'thinking',
            id: displayId,
            content: '',
            signature: (contentBlock as any).signature,
            timestamp: state.timestamp
          } as ThinkingContent])
        } else if (contentBlock.type === 'tool_use' && (contentBlock as any).id) {
          const toolCall = createToolCall(contentBlock as unknown as ToolUseContent, tools.pendingToolCalls)
          appendSubagentDisplayItems(taskId, [toolCall])
        }
        break
      }
      case 'content_block_delta': {
        const state = subagentStreamingState.get(taskId)
        if (!state) break
        const index = (event as any).index
        const delta = (event as any).delta
        const block = state.content[index]
        if (!block) break
        if (delta.type === 'text_delta' && block.type === 'text') {
          block.text = (block as any).text + (delta.text || '')
          const displayId = `${state.messageId}-text-${index}`
          updateSubagentTextDisplay(taskId, displayId, block.text || '')
        } else if (delta.type === 'thinking_delta' && block.type === 'thinking') {
          block.thinking = (block as any).thinking + (delta.thinking || '')
          const displayId = `${state.messageId}-thinking-${index}`
          updateSubagentThinkingDisplay(taskId, displayId, block.thinking || '', (block as any).signature)
        } else if ((delta as any).type === 'signature_delta' && block.type === 'thinking') {
          block.signature = (delta as any).signature || (block as any).signature
          const displayId = `${state.messageId}-thinking-${index}`
          updateSubagentThinkingDisplay(taskId, displayId, (block as any).thinking || '', block.signature)
        } else if (delta.type === 'input_json_delta' && block.type === 'tool_use') {
          // 仅更新累积 JSON
          const accumulated = tools.appendJsonDelta((block as any).id, delta.partial_json || '')
          try {
            block.input = JSON.parse(accumulated)
          } catch {
            /* ignore */
          }
        }
        break
      }
      case 'content_block_stop':
        // 结束时尝试解析累积 JSON
        subagentStreamingState.get(taskId)?.content.forEach((block) => {
          if (block.type === 'tool_use') {
            const input = tools.parseAndApplyAccumulatedJson((block as any).id)
            if (input) {
              block.input = input
            }
          }
        })
        break
      case 'message_stop':
        subagentStreamingState.delete(taskId)
        break
      default:
        break
    }
  }

  /**
   * 处理 message_stop 事件
   */
  function handleMessageStop(): void {
    const streamingMessage = findStreamingAssistantMessage()
    if (streamingMessage) {
      streamingMessage.isStreaming = false
    }
    // 注意：不在这里设置 isGenerating = false
    // isGenerating 只在 handleResultMessage() 中设置为 false
    touchMessages()
  }

  /**
   * 处理 content_block_start 事件
   */
  function handleContentBlockStart(event: any): void {
    const message = ensureStreamingAssistantMessage()
    const contentBlock = mapRpcContentBlock(event.content_block)
    const blockIndex = event.index

    if (contentBlock) {
      // 添加到 message.content
      while (message.content.length < blockIndex) {
        message.content.push({ type: 'text', text: '' } as any)
      }
      if (message.content.length === blockIndex) {
        message.content.push(contentBlock)
      } else {
        message.content[blockIndex] = contentBlock
      }

      // 直接创建 DisplayItem 并 push（内容为空）
      if (contentBlock.type === 'text') {
        const displayId = `${message.id}-text-${blockIndex}`
        if (!displayItems.find(item => item.id === displayId)) {
          pushDisplayItems([{
            displayType: 'assistantText' as const,
            id: displayId,
            content: '', // 初始为空
            timestamp: message.timestamp,
            isLastInMessage: false,
            stats: undefined
          } as AssistantText])
        }
      } else if (contentBlock.type === 'thinking') {
        const displayId = `${message.id}-thinking-${blockIndex}`
        if (!displayItems.find(item => item.id === displayId)) {
          pushDisplayItems([{
            displayType: 'thinking' as const,
            id: displayId,
            content: '', // 初始为空
            signature: contentBlock.signature,
            timestamp: message.timestamp
          } as ThinkingContent])
        }
      } else if (contentBlock.type === 'tool_use' && contentBlock.id) {
        // 注册工具调用
        tools.registerToolCall(contentBlock as ToolUseBlock)

        // 创建工具调用的展示对象
        const existingToolItem = displayItems.find(
          item => item.displayType === 'toolCall' && item.id === contentBlock.id
        )
        if (!existingToolItem) {
          const toolCall = createToolCall(contentBlock as unknown as ToolUseContent, tools.pendingToolCalls)
          if (contentBlock.name === 'Task' || (contentBlock as any).toolName === 'Task') {
            (toolCall as any).agentName = (contentBlock as any).input?.subagent_type || (contentBlock as any).input?.model
            flushPendingSubagentMessages(contentBlock.id, toolCall)
          }
          pushDisplayItems([toolCall])
        }
      }
    }
  }

  /**
   * 处理 content_block_delta 事件
   */
  function handleContentBlockDelta(event: any): void {
    const message = ensureStreamingAssistantMessage()
    const index = event.index
    const delta = event.delta

    if (index >= 0 && index < message.content.length && delta) {
      const contentBlock = message.content[index]

      switch (delta.type) {
        case 'text_delta':
          if (contentBlock.type === 'text') {
            contentBlock.text += delta.text
            updateTextDisplayItemIncrementally(message, index, contentBlock.text)
          }
          break

        case 'thinking_delta':
          if (contentBlock.type === 'thinking') {
            contentBlock.thinking += delta.thinking
            updateThinkingDisplayItemIncrementally(message, index, contentBlock.thinking)
          }
          break

        case 'input_json_delta':
          if (contentBlock.type === 'tool_use') {
            const accumulated = tools.appendJsonDelta(contentBlock.id, delta.partial_json)
            // 尝试解析到 message.content
            try {
              contentBlock.input = JSON.parse(accumulated)
            } catch {
              // JSON 不完整，继续累加
            }
          }
          break

        default:
          // 处理 signature_delta
          if ((delta as any).type === 'signature_delta' && contentBlock.type === 'thinking') {
            const sigDelta = delta as any
            if (sigDelta.signature) {
              contentBlock.signature = sigDelta.signature
              // 更新对应 displayItem 的 signature
              const displayItem = displayItems.find(
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
  }

  /**
   * 处理 content_block_stop 事件
   */
  function handleContentBlockStop(event: any): void {
    const message = findStreamingAssistantMessage()
    if (message && event.index >= 0 && event.index < message.content.length) {
      const block = message.content[event.index]

      if (block.type === 'tool_use') {
        const toolUseBlock = block as ToolUseBlock

        log.debug('[content_block_stop] (tool_use):', {
          id: toolUseBlock.id,
          toolName: toolUseBlock.toolName,
          hasInput: !!toolUseBlock.input
        })

        // JSON 解析完成，更新 DisplayItem
        const existingDisplayItem = displayItems.find(
          item => item.id === toolUseBlock.id && item.displayType === 'toolCall'
        ) as ToolCall | undefined

        if (!existingDisplayItem) {
          const toolCall = createToolCall(toolUseBlock as unknown as ToolUseContent, tools.pendingToolCalls)
          pushDisplayItems([toolCall])
        } else {
          existingDisplayItem.input = toolUseBlock.input as Record<string, unknown> || existingDisplayItem.input
        }

        // 同时更新 pendingToolCalls
        tools.updateToolInput(toolUseBlock.id, toolUseBlock.input || {})

        // 强制触发 Vue 响应式更新
        triggerDisplayItemsUpdate()
      }
    }
  }

  /**
   * 处理结果消息
   */
  function handleResultMessage(resultData: RpcResultMessage): void {
    log.debug('[useSessionMessages] 收到 result 消息')

    // 获取追踪信息
    const tracker = stats.getCurrentTracker()

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

    log.debug(`[useSessionMessages] 统计信息 duration=${durationMs}ms, tokens=${inputTokens}/${outputTokens}`)

    // 更新对应用户消息的统计信息
    if (tracker?.lastUserMessageId) {
      const displayItemIndex = displayItems.findIndex(
        item => isDisplayUserMessage(item) && item.id === tracker.lastUserMessageId
      )

      if (displayItemIndex !== -1) {
        const userMessage = displayItems[displayItemIndex] as UserMessage
        userMessage.requestStats = {
          requestDuration: durationMs,
          inputTokens,
          outputTokens
        }
        userMessage.isStreaming = false
        triggerDisplayItemsUpdate()
      }
    }

    // 结束正在流式的 assistant 消息
    const streamingMessage = findStreamingAssistantMessage()
    if (streamingMessage) {
      streamingMessage.isStreaming = false
      log.debug('[useSessionMessages] 结束流式 assistant 消息')
    }

    // 打断响应处理
    if (resultData.subtype === 'interrupted') {
      isGenerating.value = false
      stats.cancelRequestTracking()

      // 渲染打断提示
      pushDisplayItems([{
        id: `interrupt-${Date.now()}`,
        displayType: 'interruptedHint',
        timestamp: Date.now(),
        message: i18n.global.t('system.interrupted')
      } as any])
      log.info('[useSessionMessages] 渲染打断提示')
    }

    // 处理错误
    if (resultData.is_error && resultData.result) {
      lastError.value = resultData.result
      log.warn(`[useSessionMessages] 后端返回错误: ${resultData.result}`)

      pushDisplayItems([{
        id: `error-${Date.now()}`,
        displayType: 'errorResult',
        timestamp: Date.now(),
        message: resultData.result
      } as any])
    }

    // 标记生成完成（非打断场景）
    if (resultData.subtype !== 'interrupted') {
      isGenerating.value = false
      stats.finishRequestTracking(!resultData.is_error)
      log.debug('[useSessionMessages] 请求完成')
    }

    // 处理队列中的下一条消息（先调用回调，让 Tab 层应用 pending settings）
    handleQueueAfterResult()
  }

  /**
   * 生成完成后处理队列
   * 先调用 beforeProcessQueueFn（应用 pending settings），再处理队列
   */
  async function handleQueueAfterResult(): Promise<void> {
    if (messageQueue.value.length === 0) {
      return
    }

    // 先调用回调（让 Tab 层应用 pending settings、处理重连等）
    if (beforeProcessQueueFn) {
      try {
        await beforeProcessQueueFn()
      } catch (err) {
        console.error('[useSessionMessages] beforeProcessQueueFn 执行失败:', err)
      }
    }

    // 再处理队列
    processNextQueuedMessage()
  }

  /**
   * 处理普通消息（assistant/user 消息）
   */
  function handleNormalMessage(message: Message): void {
    log.debug('[useSessionMessages] handleNormalMessage:', {
      role: message.role,
      id: message.id,
      contentLength: message.content.length,
      parentToolUseId: message.parentToolUseId
    })

    // 确保消息有 id 字段
    if (!message.id) {
      const streamingId = message.role === 'assistant'
        ? stats.getCurrentTracker()?.currentStreamingMessageId
        : null
      message.id = streamingId || generateMessageId(message.role)
    }

    // 子代理消息：归档到对应 Task 卡片
    const parentToolUseId = message.parentToolUseId
    if (parentToolUseId) {
      const displayBatch = convertMessageToDisplayItems(message, tools.pendingToolCalls)
      appendSubagentDisplayItems(parentToolUseId, displayBatch)
      return
    }

    // assistant 消息处理
    if (message.role === 'assistant') {
      const latestStreamingMessage = findStreamingAssistantMessage()

      // 存在流式消息且 ID 相同 → 忽略（流式已组装完成）
      if (latestStreamingMessage && latestStreamingMessage.id === message.id) {
        log.debug('[useSessionMessages] 忽略同 ID 的完整消息（流式已组装）')
        return
      }

      // ID 不同或无流式消息 → 添加新消息
      log.debug('[useSessionMessages] 添加新 assistant 消息')
      addMessage(message)
      touchMessages()
      return
    }

    // user 消息处理
    if (message.role === 'user') {
      const hasToolResult = message.content.some((block: ContentBlock) => block.type === 'tool_result')
      const hasToolUse = message.content.some((block: ContentBlock) => block.type === 'tool_use')
      const hasText = message.content.some((block: ContentBlock) => block.type === 'text')

      // tool_result 消息：只更新工具状态
      if (hasToolResult) {
        processToolResults(message.content)
        touchMessages()
        return
      }

      // 纯 tool_use 的 user 消息：忽略
      if (hasToolUse && !hasText) {
        log.debug('[useSessionMessages] 忽略纯 tool_use 的 user 消息')
        return
      }

      // 文本类型的 user 消息
      if (hasText) {
        const textBlock = message.content.find((block: ContentBlock) => block.type === 'text') as { text?: string } | undefined
        const text = textBlock?.text || ''
        if (text.includes('[Request interrupted') || text.includes('interrupted')) {
          log.debug('[useSessionMessages] 忽略中断相关的 user 消息')
          return
        }
      }

      // 检查是否已存在（避免重复）
      const existingUserMsg = messages.find(m => m.id === message.id)
      if (existingUserMsg) {
        log.debug('[useSessionMessages] 忽略重复的 user 消息:', message.id)
        return
      }

      // 添加新的 user 消息
      addMessage(message)
      touchMessages()
    }
  }

  // ========== 消息发送方法 ==========

  /**
   * 添加消息到 UI（不发送）
   *
   * @param message 消息内容
   * @returns userMessage 和 mergedContent，用于后续发送
   */
  function addMessageToUI(message: { contexts: any[]; contents: ContentBlock[] }): {
    userMessage: Message
    mergedContent: ContentBlock[]
  } {
    // 将 contexts 转换为 ContentBlock 格式
    const contextBlocks = message.contexts.length > 0
      ? buildUserMessageContent({
          text: '',
          contexts: message.contexts
        })
      : []

    // 合并: contexts 内容块 + 用户输入内容块
    const mergedContent = [...contextBlocks, ...message.contents]

    log.debug('[useSessionMessages] addMessageToUI:', {
      contexts: message.contexts.length,
      contents: message.contents.length,
      merged: mergedContent.length
    })

    // 创建用户消息
    const userMessage: Message = {
      id: `user-${Date.now()}`,
      role: 'user',
      timestamp: Date.now(),
      content: mergedContent
    }

    // 添加到 UI（用户立即可见）
    messages.push(userMessage)
    const newDisplayItems = convertMessageToDisplayItems(userMessage, tools.pendingToolCalls)
    pushDisplayItems(newDisplayItems)
    log.debug('[useSessionMessages] 用户消息已添加:', userMessage.id)

    return { userMessage, mergedContent }
  }

  /**
   * 只将消息加入队列（不添加到 UI）
   * 用于生成中发送的消息
   */
  function addToQueue(message: { contexts: any[]; contents: ContentBlock[] }): void {
    // 将 contexts 转换为 ContentBlock 格式
    const contextBlocks = message.contexts.length > 0
      ? buildUserMessageContent({
          text: '',
          contexts: message.contexts
        })
      : []

    // 合并: contexts 内容块 + 用户输入内容块
    const mergedContent = [...contextBlocks, ...message.contents]

    const id = `user-${Date.now()}`
    log.info(`[useSessionMessages] 消息加入队列（不添加到 UI）: ${id}`)

    messageQueue.value.push({
      id,
      contexts: message.contexts,
      contents: message.contents,
      mergedContent,
      createdAt: Date.now()
    })
  }

  /**
   * 直接发送消息（不判断 isGenerating）
   * 调用方应确保连接已建立且不在生成中
   *
   * @param userMessage 已添加到 UI 的用户消息
   * @param mergedContent 合并后的消息内容
   * @param originalMessage 原始消息（用于发送失败时加入队列）
   */
  function sendDirectly(
    userMessage: Message,
    mergedContent: ContentBlock[],
    originalMessage?: { contexts: any[]; contents: ContentBlock[] }
  ): void {
    // 连接未建立
    if (!sendMessageFn) {
      log.error('[useSessionMessages] sendMessageFn 未设置，请确保连接已建立后再发送')
      return
    }

    // 开始请求追踪
    const streamingMessageId = `assistant-${Date.now()}`
    stats.startRequestTracking(userMessage.id)
    stats.setStreamingMessageId(streamingMessageId)
    isGenerating.value = true

    // 更新 displayItem 的 isStreaming 状态
    const displayItemIndex = displayItems.findIndex(
      item => isDisplayUserMessage(item) && item.id === userMessage.id
    )
    if (displayItemIndex !== -1) {
      const userDisplayItem = displayItems[displayItemIndex] as UserMessage
      userDisplayItem.isStreaming = true
      triggerDisplayItemsUpdate()
    }

    // 发送到后端
    sendMessageFn(mergedContent).catch(err => {
      console.error('[useSessionMessages] 发送失败，加入重试队列:', err)

      // 加入队列等待重连后重试（消息已在 UI 中，保留 mergedContent）
      messageQueue.value.push({
        id: userMessage.id,
        contexts: originalMessage?.contexts || [],
        contents: originalMessage?.contents || [],
        mergedContent,
        createdAt: Date.now()
      })

      isGenerating.value = false
      stats.cancelRequestTracking()
    })
  }


  /**
   * 处理队列中的下一条消息
   *
   * 队列中的消息有 mergedContent：
   * - 生成中排队的消息：需要先添加到 UI，再发送
   * - 发送失败重试的消息：已在 UI 中，直接发送
   */
  function processNextQueuedMessage(): void {
    if (messageQueue.value.length === 0) {
      return
    }

    const nextMessage = messageQueue.value.shift()
    if (!nextMessage) {
      return
    }

    log.info(`[useSessionMessages] 从队列中取出消息: ${nextMessage.id}`)

    if (!sendMessageFn) {
      console.error('[useSessionMessages] processNextQueuedMessage: 发送函数未设置')
      return
    }

    // 检查消息是否已在 UI 中（发送失败重试的情况）
    const existingItem = displayItems.find(
      item => isDisplayUserMessage(item) && item.id === nextMessage.id
    )

    if (existingItem) {
      // 消息已在 UI 中（发送失败重试），直接发送
      sendDirectly(
        { id: nextMessage.id, role: 'user', timestamp: nextMessage.createdAt, content: nextMessage.mergedContent! } as Message,
        nextMessage.mergedContent!,
        { contexts: nextMessage.contexts, contents: nextMessage.contents }
      )
    } else {
      // 消息不在 UI 中（生成中排队的），先添加到 UI 再发送
      const { userMessage, mergedContent } = addMessageToUI({
        contexts: nextMessage.contexts,
        contents: nextMessage.contents
      })
      sendDirectly(userMessage, mergedContent, { contexts: nextMessage.contexts, contents: nextMessage.contents })
    }
  }

  // ========== 辅助方法 ==========

  /**
   * 查找当前处于 streaming 状态的 assistant 消息
   */
  function findStreamingAssistantMessage(): Message | null {
    const tracker = stats.getCurrentTracker()
    const streamingId = tracker?.currentStreamingMessageId
    if (streamingId) {
      const matched = [...messages].reverse().find(msg => msg.id === streamingId && msg.role === 'assistant')
      if (matched) return matched
    }

    for (let i = messages.length - 1; i >= 0; i--) {
      const msg = messages[i]
      if (msg.role === 'assistant' && msg.isStreaming) {
        return msg
      }
    }
    return null
  }

  /**
   * 确保存在一个用于流式渲染的 assistant 消息
   */
  function ensureStreamingAssistantMessage(): Message {
    const existing = findStreamingAssistantMessage()
    if (existing) return existing

    const tracker = stats.getCurrentTracker()
    const placeholderId = tracker?.currentStreamingMessageId || `assistant-${Date.now()}`
    const newMessage: Message = {
      id: placeholderId,
      role: 'assistant',
      timestamp: Date.now(),
      content: [],
      isStreaming: true
    }
    messages.push(newMessage)
    const items = convertMessageToDisplayItems(newMessage, tools.pendingToolCalls)
    pushDisplayItems(items)
    return newMessage
  }

  /**
   * 合并 message_start 内置的初始内容
   */
  function mergeInitialAssistantContent(target: Message, initialBlocks: ContentBlock[]): void {
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
   * 增量更新文本 displayItem
   */
  function updateTextDisplayItemIncrementally(
    message: Message,
    blockIndex: number,
    newText: string
  ): void {
    const expectedId = `${message.id}-text-${blockIndex}`

    const existing = displayItems.find(
      item => item.id === expectedId && item.displayType === 'assistantText'
    ) as AssistantText | undefined

    if (existing) {
      existing.content = newText
      return
    }

    // 如果找不到，创建新的
    const newTextItem: AssistantText = {
      displayType: 'assistantText',
      id: expectedId,
      content: newText,
      timestamp: message.timestamp,
      isLastInMessage: false,
      stats: undefined,
      isStreaming: true
    }
    pushDisplayItems([newTextItem])
  }

  /**
   * 增量更新思考 displayItem
   */
  function updateThinkingDisplayItemIncrementally(
    message: Message,
    blockIndex: number,
    newThinking: string
  ): void {
    const expectedId = `${message.id}-thinking-${blockIndex}`

    const existing = displayItems.find(
      item => item.id === expectedId && item.displayType === 'thinking'
    ) as ThinkingContent | undefined

    if (existing) {
      existing.content = newThinking
      return
    }

    // 如果找不到，创建新的
    const newThinkingItem: ThinkingContent = {
      displayType: 'thinking',
      id: expectedId,
      content: newThinking,
      timestamp: message.timestamp
    }
    pushDisplayItems([newThinkingItem])
  }

  /**
   * 处理 tool_result 内容块
   */
  function processToolResults(content: ContentBlock[]): void {
    const toolResults = content.filter((block): block is ToolResultBlock => block.type === 'tool_result')

    let hasUpdates = false
    for (const result of toolResults) {
      const success = tools.updateToolResult(result.tool_use_id, result)
      if (success) {
        hasUpdates = true

        // 在 IDEA 环境下，工具调用成功后自动执行 IDEA 操作
        const wasSuccess = !result.is_error
        if (wasSuccess && ideaBridge.isInIde()) {
          const toolCall = tools.getToolCall(result.tool_use_id)
          if (toolCall) {
            executeIdeActionForTool(toolCall)
          }
        }
      }
    }

    // 强制触发 Vue 响应式更新
    if (hasUpdates) {
      triggerDisplayItemsUpdate()
    }
  }

  /**
   * 为工具调用执行对应的 IDEA 操作
   */
  async function executeIdeActionForTool(toolCall: ToolCall): Promise<void> {
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
          break
        }

        case CLAUDE_TOOL_TYPE.WRITE: {
          const writeCall = toolCall as ClaudeWriteToolCall
          const filePath = writeCall.input.file_path || writeCall.input.path || ''
          if (!filePath) break

          await ideService.openFile(filePath)
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
          break
        }
      }
    } catch (error) {
      log.warn(`[useSessionMessages] 执行 IDEA 操作失败: ${error}`)
    }
  }

  /**
   * 添加消息
   */
  function addMessage(message: Message): void {
    appendMessagesBatch([message])
  }

  /**
   * 生成消息 ID
   */
  function generateMessageId(role: string): string {
    return `${role}-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`
  }

  /**
   * 触发 displayItems 更新
   */
  function triggerDisplayItemsUpdate(): void {
    refreshDisplayWindow()
  }

  /**
   * 触发消息列表更新
   */
  function touchMessages(): void {
    // Vue 3 reactive 数组会自动追踪变化
    // 这里可以用于未来扩展
  }

  // ========== 队列管理方法 ==========

  /**
   * 编辑队列中的消息
   */
  function editQueueMessage(id: string): PendingMessage | null {
    const index = messageQueue.value.findIndex(m => m.id === id)
    if (index === -1) return null
    const [removed] = messageQueue.value.splice(index, 1)
    return removed
  }

  /**
   * 从队列中删除消息
   */
  function removeFromQueue(id: string): boolean {
    const index = messageQueue.value.findIndex(m => m.id === id)
    if (index === -1) return false
    messageQueue.value.splice(index, 1)
    return true
  }

  /**
   * 清空消息队列
   */
  function clearQueue(): void {
    messageQueue.value = []
    log.info('[useSessionMessages] 清空消息队列')
  }

  // ========== 重置方法 ==========

  /**
   * 清空所有消息
   */
  function clearMessages(): void {
    messages.splice(0, messages.length)
    clearDisplayItems()
    log.debug('[useSessionMessages] 消息已清空')
  }

  /**
   * 批量前插消息（用于历史回放）
   */
  function prependMessagesBatch(msgs: Message[]): void {
    if (msgs.length === 0) return
    // 先按顺序生成 displayItems，再前插
    const displayBatch = msgs.flatMap(m => convertMessageToDisplayItems(m, tools.pendingToolCalls))
    prependDisplayItems(displayBatch)
    // 再更新 messages 状态（保持原顺序）
    for (let i = msgs.length - 1; i >= 0; i -= 1) {
      messages.unshift(msgs[i])
    }
  }

  /**
   * 批量尾插消息
   */
  function appendMessagesBatch(msgs: Message[]): void {
    if (msgs.length === 0) return
    const displayBatch = msgs.flatMap(m => convertMessageToDisplayItems(m, tools.pendingToolCalls))
    pushDisplayItems(displayBatch)
    messages.push(...msgs)
  }

  /**
   * 重置所有状态
   */
  function reset(): void {
    clearMessages()
    clearQueue()
    isGenerating.value = false
    lastError.value = null
    sendMessageFn = null
    log.debug('[useSessionMessages] 状态已重置')
  }

  /**
   * 添加错误消息到 UI
   */
  function addErrorMessage(message: string): void {
    pushDisplayItems([{
      id: `error-${Date.now()}`,
      displayType: 'errorResult',
      timestamp: Date.now(),
      message
    } as any])
    triggerDisplayItemsUpdate()
  }

  // ========== 导出 ==========

  return {
    // 响应式状态
    messages,
    displayItems,
    messageQueue,
    isGenerating,
    lastError,

    // 计算属性
    messageCount,
    displayItemCount,
    queueLength,
    hasMessages,

    // 设置方法
    setSendMessageFn,
    setBeforeProcessQueueFn,
    appendMessagesBatch,
    prependMessagesBatch,

    // 消息处理方法
    handleStreamEvent,
    handleResultMessage,
    handleNormalMessage,

    // 消息发送方法
    addMessageToUI,
    addToQueue,
    sendDirectly,
    processNextQueuedMessage,

    // 队列管理
    editQueueMessage,
    removeFromQueue,
    clearQueue,

    // 查询方法
    findStreamingAssistantMessage,

    // 管理方法
    clearMessages,
    reset,
    addErrorMessage,

    // 窗口化辅助（供历史前插调用）
    pushDisplayItems,
    prependDisplayItems,
    refreshDisplayWindow
  }
}

/**
 * useSessionMessages 返回类型
 */
export type SessionMessagesInstance = ReturnType<typeof useSessionMessages>
