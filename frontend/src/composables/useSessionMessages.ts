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
import type { Message, ContentBlock, ToolUseBlock, ToolResultBlock, ToolUseContent } from '@/types/message'
import type { PendingMessage } from '@/types/session'
import type { ActiveFileInfo } from '@/services/ideaRSocket'
import type { DisplayItem, AssistantText, ThinkingContent, UserMessage, ToolCall } from '@/types/display'
import { isUserMessage as isDisplayUserMessage } from '@/types/display'
import { convertMessageToDisplayItems, createToolCall } from '@/utils/displayItemConverter'
import { buildUserMessageContent, ideContextToContentBlocks } from '@/utils/userMessageBuilder'
import { mapRpcContentBlock } from '@/utils/rpcMappers'
import type { RpcStreamEvent, RpcResultMessage } from '@/types/rpc'
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
 * 注意：发送逻辑由 useSessionTab 负责，本 Composable 只管消息状态
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
   * 打断模式
   * - null: 正常完成（非打断）
   * - 'clear': 打断并清空队列（用户主动打断）
   * - 'keep': 打断但保留队列并自动发送（强制发送场景）
   */
  type InterruptMode = null | 'clear' | 'keep'
  let interruptMode: InterruptMode = null

  /**
   * 最后一次错误信息
   */
  const lastError = ref<string | null>(null)

  /**
   * Whether partial messages are included (set on connect).
   * When true, full assistant messages should merge into existing streaming items.
   */
  const includePartialMessages = ref(false)
  let lastStreamingMessageId: string | null = null

  /**
   * 设置是否包含分片消息
   */
  function setIncludePartialMessages(value: boolean): void {
    includePartialMessages.value = value
    log.debug(`[useSessionMessages] includePartialMessages 设置为: ${value}`)
  }

  // ========== 函数注入 ==========

  /**
   * 处理队列前的回调（由 Tab 注入，用于应用 pending settings）
   */
  let beforeProcessQueueFn: (() => Promise<void>) | null = null

  /**
   * 处理队列消息的回调（由 Tab 注入）
   */
  let processQueueFn: (() => Promise<void>) | null = null

  /**
   * 设置处理队列前的回调
   */
  function setBeforeProcessQueueFn(fn: () => Promise<void>): void {
    beforeProcessQueueFn = fn
  }

  /**
   * 设置处理队列消息的回调
   */
  function setProcessQueueFn(fn: () => Promise<void>): void {
    processQueueFn = fn
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

  // ========== Skill 内容关联 ==========

  /**
   * 建立 Skill 工具 ID 到 SKILL.md 内容的映射
   * 基于消息顺序：Skill 工具调用（assistant msg）后的第一个 user 消息
   */
  const skillContentMap = computed(() => {
    const map = new Map<string, string>() // toolUseId -> skillContent

    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i]
      if (msg.role !== 'assistant') continue

      // 查找 Skill 工具调用
      for (const block of msg.content) {
        if (block.type === 'tool_use' &&
            ((block as any).toolName === 'Skill' || (block as any).name === 'Skill')) {
          const toolUseId = (block as any).id
          // 查找紧随其后的 user 消息
          const nextMsg = messages[i + 1]
          if (nextMsg?.role === 'user') {
            const textBlock = nextMsg.content.find((b: any) => b.type === 'text')
            if (textBlock && 'text' in textBlock) {
              map.set(toolUseId, (textBlock as any).text)
            }
          }
        }
      }
    }
    return map
  })

  /**
   * 获取被 Skill 工具关联的 user 消息 ID 集合（这些消息需要隐藏）
   */
  const skillContentMessageIds = computed(() => {
    const ids = new Set<string>()

    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i]
      if (msg.role !== 'assistant') continue

      for (const block of msg.content) {
        if (block.type === 'tool_use' &&
            ((block as any).toolName === 'Skill' || (block as any).name === 'Skill')) {
          const nextMsg = messages[i + 1]
          if (nextMsg?.role === 'user') {
            ids.add(nextMsg.id)
          }
        }
      }
    }
    return ids
  })

  /**
   * 判断消息是否为 Skill 内容消息（需要隐藏）
   */
  function isSkillContentMessage(msgId: string): boolean {
    return skillContentMessageIds.value.has(msgId)
  }

  /**
   * 获取 Skill 工具的关联内容
   */
  function getSkillContent(toolUseId: string): string | undefined {
    return skillContentMap.value.get(toolUseId)
  }

  // ========== 消息处理核心方法 ==========

  /**
   * 处理流式事件
   *
   * 直接解析和处理 stream event 数据，不依赖外部模块
   * 注意：不再根据 isGenerating 状态拦截，收到消息就展示
   */
  function handleStreamEvent(streamEventData: RpcStreamEvent): void {

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

    // 更新 token 使用量（message_start 和 message_delta 都可能包含 usage）
    // message_start: 包含 event.message.usage（初始 input_tokens）
    // message_delta: 包含 event.usage（最终的 input_tokens + output_tokens）
    const usageSource =
      (eventType === 'message_start' && event.message?.usage) ? event.message.usage :
      (eventType === 'message_delta' && event.usage) ? event.usage :
      null

    if (usageSource) {
      const usage = usageSource as {
        input_tokens?: number
        output_tokens?: number
        cached_input_tokens?: number
        cache_creation_tokens?: number
        cache_read_tokens?: number
        inputTokens?: number
        outputTokens?: number
        cachedInputTokens?: number
        cacheCreationTokens?: number
        cacheReadTokens?: number
      }
      stats.addTokenUsage(
        usage.input_tokens ?? usage.inputTokens ?? 0,
        usage.output_tokens ?? usage.outputTokens ?? 0,
        usage.cache_read_tokens ?? usage.cacheReadTokens ?? 0
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

    const rawMessageId = event.message?.id
    const messageId = rawMessageId || generateMessageId('assistant')
    lastStreamingMessageId = messageId

    const existingStreaming = findStreamingAssistantMessage()
    const existingById = findLatestAssistantById(messageId)
    log.info('[message_start]', {
      messageId,
      hasBackendId: !!rawMessageId,
      hasExistingById: !!existingById,
      hasExistingStreaming: !!existingStreaming,
      initialContentLength: contentBlocks.length
    })
    if (existingById) {
      if (existingStreaming && existingStreaming !== existingById) {
        existingStreaming.isStreaming = false
      }
      existingById.isStreaming = true
      ensureAssistantDisplayId(existingById)
      stats.setStreamingMessageId(messageId)
      if (contentBlocks.length > 0) {
        mergeInitialAssistantContent(existingById, contentBlocks)
      }
      isGenerating.value = true
      touchMessages()
      return
    }
    if (existingStreaming) {
      existingStreaming.isStreaming = false
    }

    const targetMessage: Message = {
      id: messageId,
      role: 'assistant',
      timestamp: Date.now(),
      content: [],
      isStreaming: true
    }
    ensureAssistantDisplayId(targetMessage)
    messages.push(targetMessage)

    // 将占位消息 id 更新为后端真实 id
    stats.setStreamingMessageId(messageId)
    targetMessage.isStreaming = true

    // 合并初始内容（如果有的话）
    if (contentBlocks.length > 0) {
      mergeInitialAssistantContent(targetMessage, contentBlocks)
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
        const contentBlocks: ContentBlock[] = ((event as any).message?.content ?? [])
          .map(mapRpcContentBlock)
          .filter((b: ContentBlock | null): b is ContentBlock => !!b)
        contentBlocks.forEach((block: ContentBlock, idx: number) => {
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
      const summary = streamingMessage.content.map((block) => {
        if (block.type === 'text') {
          return { type: 'text', length: (block as any).text?.length ?? 0 }
        }
        if (block.type === 'thinking') {
          return { type: 'thinking', length: (block as any).thinking?.length ?? 0 }
        }
        if (block.type === 'tool_use') {
          return { type: 'tool_use', id: (block as any).id }
        }
        if (block.type === 'tool_result') {
          return { type: 'tool_result', id: (block as any).tool_use_id }
        }
        return { type: (block as any).type }
      })
      log.info('[message_stop] streaming stopped', {
        messageId: streamingMessage.id,
        blocks: summary
      })
    } else {
      log.info('[message_stop] streaming stopped (no active message)')
    }
    // 注意：不在这里设置 isGenerating = false
    // isGenerating 只在 handleResultMessage() 中设置为 false
    touchMessages()
  }

  /**
   * 处理 content_block_start 事件
   */
  function handleContentBlockStart(event: any): void {
    const message = findStreamingAssistantMessage()
    if (!message) {
      log.warn('[content_block_start] 未找到 streaming 消息，跳过')
      return
    }
    const contentBlock = mapRpcContentBlock(event.content_block)
    const blockIndex = event.index

    // 🔍 调试日志：记录收到的 content_block_start 事件
    log.info(`[content_block_start] 收到事件:`, {
      index: blockIndex,
      rawContentBlock: event.content_block,
      mappedType: contentBlock?.type,
      mappedId: (contentBlock as any)?.id
    })

    if (contentBlock) {
      const displayBaseId = getAssistantDisplayId(message)
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
        const displayId = `${displayBaseId}-text-${blockIndex}`
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
        const displayId = `${displayBaseId}-thinking-${blockIndex}`
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
          if ((contentBlock as any).toolName === 'Task' || (contentBlock as any).name === 'Task') {
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
    const message = findStreamingAssistantMessage()
    if (!message) {
      log.warn('[content_block_delta] 未找到 streaming 消息，跳过')
      return
    }
    const index = event.index
    const delta = event.delta

    if (index >= 0 && index < message.content.length && delta) {
      const contentBlock = message.content[index]

      switch (delta.type) {
        case 'text_delta':
          if (contentBlock.type === 'text') {
            contentBlock.text += delta.text
            updateTextDisplayItemIncrementally(message, index, contentBlock.text)
            stats.incrementContentVersion() // 触发自动滚动
          }
          break

        case 'thinking_delta':
          if (contentBlock.type === 'thinking') {
            contentBlock.thinking += delta.thinking
            updateThinkingDisplayItemIncrementally(message, index, contentBlock.thinking)
            stats.incrementContentVersion() // 触发自动滚动
          }
          break

        case 'input_json_delta':
          if (contentBlock.type === 'tool_use') {
            const accumulated = tools.appendJsonDelta(contentBlock.id, delta.partial_json)
            // 尝试解析到 message.content
            try {
              contentBlock.input = JSON.parse(accumulated)
              stats.incrementContentVersion() // 触发自动滚动（TodoList 等工具输入变化时）
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
                  item => item.id === `${getAssistantDisplayId(message)}-thinking-${index}` && item.displayType === 'thinking'
                ) as ThinkingContent | undefined
                if (displayItem) {
                  displayItem.signature = sigDelta.signature
                triggerDisplayItemsUpdate()
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
      } else if (block.type === 'text') {
        log.info('[content_block_stop] text', {
          index: event.index,
          length: (block as any).text?.length ?? 0
        })
      } else if (block.type === 'thinking') {
        log.info('[content_block_stop] thinking', {
          index: event.index,
          length: (block as any).thinking?.length ?? 0,
          signature: (block as any).signature
        })
      }
      // thinking 块的完成状态由 syncThinkingSignatures 在收到完整消息时处理
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

    const usage = resultData.usage as {
      input_tokens?: number
      output_tokens?: number
      cache_creation_input_tokens?: number
      cache_read_input_tokens?: number
    } | undefined
    if (usage) {
      inputTokens = usage.input_tokens || 0
      outputTokens = usage.output_tokens || 0
    }

    // 非流式模式下，从 RpcResultMessage 累加 usage
    // 流式模式下 tracker 已经通过 message_start/message_delta 累加过了
    if (tracker && tracker.inputTokens === 0 && tracker.outputTokens === 0 && usage) {
      stats.addTokenUsage(
        inputTokens,
        outputTokens,
        usage.cache_read_input_tokens || 0
      )
      log.debug('[useSessionMessages] 非流式模式，从 RpcResultMessage 累加 usage')
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

    // 打断响应处理（interrupted 或 error_during_execution 都视为打断）
    const isInterrupted = resultData.subtype === 'interrupted' || resultData.subtype === 'error_during_execution'
    if (isInterrupted) {
      log.info('[useSessionMessages] 🛑 收到打断信号，subtype:', resultData.subtype, '队列长度:', messageQueue.value.length)
      isGenerating.value = false
      log.info('[useSessionMessages] 🛑 isGenerating 已设为 false')
      stats.cancelRequestTracking()

      // 标记打断相关的用户消息为 error 样式
      // - error_during_execution: 标记最后一条用户消息
      // - interrupted: 标记包含 "[Request interrupted by user]" 的打断提示消息
      if (resultData.subtype === 'error_during_execution') {
        for (let i = displayItems.length - 1; i >= 0; i--) {
          const item = displayItems[i]
          if (isDisplayUserMessage(item) && !(item as any).parentToolUseId) {
            (item as any).style = 'error'
            log.info('[useSessionMessages] 🛑 标记用户消息 style: error', item.id)
            break
          }
        }
      } else if (resultData.subtype === 'interrupted') {
        // 找到打断提示消息并标记为 error 样式（红色）
        for (let i = displayItems.length - 1; i >= 0; i--) {
          const item = displayItems[i]
          if (isDisplayUserMessage(item) && !(item as any).parentToolUseId) {
            const userItem = item as UserMessage
            // 检查消息内容是否为打断提示
            const textContent = userItem.content?.find(b => b.type === 'text') as { text?: string } | undefined
            if (textContent?.text?.includes('[Request interrupted by user]')) {
              (item as any).style = 'error'
              log.info('[useSessionMessages] 🛑 标记打断提示消息 style: error', item.id)
              break
            }
          }
        }
      }
      touchMessages()
    }

    // 处理错误（排除打断场景）
    if (!isInterrupted && resultData.is_error && resultData.result) {
      lastError.value = resultData.result
      log.warn(`[useSessionMessages] 后端返回错误: ${resultData.result}`)

      // 标记最近的用户消息为 error 样式
      for (let i = displayItems.length - 1; i >= 0; i--) {
        const item = displayItems[i]
        if (isDisplayUserMessage(item) && !(item as any).parentToolUseId) {
          (item as any).style = 'error'
          log.info('[useSessionMessages] 🛑 标记用户消息 style: error (is_error=true)', item.id)
          break
        }
      }

      pushDisplayItems([{
        id: `error-${Date.now()}`,
        displayType: 'errorResult',
        timestamp: Date.now(),
        message: resultData.result
      } as any])
    }

    // 标记生成完成（非打断场景）
    if (!isInterrupted) {
      isGenerating.value = false
      stats.finishRequestTracking(!resultData.is_error)
      log.debug('[useSessionMessages] 请求完成')
    }

    // 自动处理队列中的下一条消息
    // 1. is_error = false 时（正常完成）
    // 2. isInterrupted = true 时（打断场景，支持 forceSendMessage 的自动重发）
    if (!resultData.is_error || isInterrupted) {
      handleQueueAfterResult()
    } else {
      log.info(`[useSessionMessages] 📋 is_error=true 且非打断，不自动发送队列消息`)
    }
  }

  /**
   * 生成完成后处理队列
   * 先调用 beforeProcessQueueFn（应用 pending settings），再处理队列
   *
   * 根据 interruptMode 决定行为：
   * - 'clear': 清空队列，不自动发送（用户主动打断）
   * - 'keep' 或 null: 保留队列，自动发送下一条
   */
  async function handleQueueAfterResult(): Promise<void> {
    log.info('[useSessionMessages] 📋 handleQueueAfterResult 调用，interruptMode:', interruptMode, '队列长度:', messageQueue.value.length)

    // 打断模式为 'clear' 时，清空队列
    if (interruptMode === 'clear') {
      log.info('[useSessionMessages] 📋 打断模式为 clear，清空队列')
      messageQueue.value = []
      interruptMode = null  // 重置
      return
    }

    // 重置打断模式（'keep' 或 null 都走正常流程）
    interruptMode = null

    if (messageQueue.value.length === 0) {
      log.info('[useSessionMessages] 📋 队列为空，跳过')
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
    if (processQueueFn) {
      await processQueueFn()
    }
  }

  /**
   * 同步完整消息中的 thinking signature 到 displayItem
   * 收到完整消息后，思考块会自动折叠（因为 ThinkingDisplay 根据 signature 判断是否完成）
   */
  function syncThinkingSignatures(message: Message): void {
    if (message.role !== 'assistant') return

      let hasUpdate = false
      const displayBaseId = getAssistantDisplayId(message)
      message.content.forEach((block, blockIdx) => {
        if (block.type === 'thinking') {
          const displayId = `${displayBaseId}-thinking-${blockIdx}`
          const displayItem = displayItems.find(
            item => item.id === displayId && item.displayType === 'thinking'
          ) as ThinkingContent | undefined

        if (displayItem && !displayItem.signature) {
          // 优先使用 SDK 返回的 signature，否则标记为 complete
          displayItem.signature = (block as any).signature || 'complete'
          hasUpdate = true
          log.debug(`[useSessionMessages] 同步 signature 到 thinking displayItem: ${displayId}`)
        }
      }
    })

    if (hasUpdate) {
      triggerDisplayItemsUpdate()
    }
  }

  /**
   * 同步新增的内容块到 displayItems（仅用于非流式模式）
   * 
   * 注意：流式模式下不会调用此函数，因为 content_block_start 已经创建了 displayItem
   */
  function syncNewContentBlocks(newMessage: Message, existingMessage: Message): void {
    if (newMessage.role !== 'assistant') return

    if (existingMessage && existingMessage.role === 'assistant') {
      ensureAssistantDisplayId(existingMessage)
      const existingBlocks = existingMessage.content
      const incomingBlocks = newMessage.content
      if (existingBlocks.length === 0) {
        existingMessage.content = [...incomingBlocks]
      } else {
        const mergedBlocks = [...existingBlocks]
        const isPrefix = isAssistantContentPrefix(existingMessage, newMessage)
        const isSuffix = isAssistantContentSuffix(existingMessage, newMessage)
        const offset = isSuffix ? Math.max(0, mergedBlocks.length - incomingBlocks.length) : 0

        const applyMergeAt = (index: number, incoming: ContentBlock) => {
          if (index < 0 || index > mergedBlocks.length) return
          const current = mergedBlocks[index]
          if (!current) {
            mergedBlocks[index] = incoming
            return
          }
          if (current.type !== incoming.type) {
            mergedBlocks.splice(index, 0, incoming)
            return
          }
          mergedBlocks[index] = mergeAssistantBlock(current, incoming)
        }

        if (isPrefix || isSuffix) {
          for (let i = 0; i < incomingBlocks.length; i += 1) {
            applyMergeAt(i + offset, incomingBlocks[i])
          }
        } else {
          for (let i = 0; i < incomingBlocks.length; i += 1) {
            const incoming = incomingBlocks[i]
            if (i < mergedBlocks.length && mergedBlocks[i].type === incoming.type) {
              mergedBlocks[i] = mergeAssistantBlock(mergedBlocks[i], incoming)
              continue
            }
            const matchIndex = findMatchingBlockIndex(mergedBlocks, incoming)
            if (matchIndex !== -1) {
              mergedBlocks[matchIndex] = mergeAssistantBlock(mergedBlocks[matchIndex], incoming)
              continue
            }
            mergedBlocks.push(incoming)
          }
        }

        existingMessage.content = mergedBlocks
      }
    }

    const messageForDisplay = (existingMessage && existingMessage.role === 'assistant')
      ? existingMessage
      : newMessage
    if (messageForDisplay.role === 'assistant') {
      ensureAssistantDisplayId(messageForDisplay)
    }
    const newDisplayItems = convertMessageToDisplayItems(messageForDisplay, tools.pendingToolCalls, getSkillContent)
    if (newDisplayItems.length === 0) return

    const allDisplayItems = displayStore.getWindow(STORE_RETENTION)
    const existingById = new Map(allDisplayItems.map(item => [item.id, item]))
    const displayBaseId = getAssistantDisplayId(messageForDisplay)
    const toolUseIndexById = new Map<string, number>()
    messageForDisplay.content.forEach((block, idx) => {
      if (block.type === 'tool_use' && (block as ToolUseBlock).id) {
        toolUseIndexById.set((block as ToolUseBlock).id, idx)
      }
    })

    const isMessageDisplayItem = (item: DisplayItem): boolean => {
      if (item.displayType === 'assistantText' || item.displayType === 'thinking') {
        return item.id.startsWith(`${displayBaseId}-`)
      }
      if (item.displayType === 'toolCall') {
        return toolUseIndexById.has(item.id)
      }
      return false
    }

    const existingThinkingItems = allDisplayItems.filter(
      item => item.displayType === 'thinking' && item.id.startsWith(`${displayBaseId}-thinking-`)
    )
    const existingTextItems = allDisplayItems.filter(
      item => item.displayType === 'assistantText' && item.id.startsWith(`${displayBaseId}-text-`)
    )
    const newThinkingItems = newDisplayItems.filter(item => item.displayType === 'thinking')
    const newTextItems = newDisplayItems.filter(item => item.displayType === 'assistantText')

    // 处理索引不一致导致的重复：同一条消息只有一个 thinking/text 时，优先复用已有 displayItem
    if (newThinkingItems.length === 1 && existingThinkingItems.length === 1) {
      const newItem = newThinkingItems[0]
      const existingItem = existingThinkingItems[0]
      if (newItem.id !== existingItem.id) {
        existingById.set(newItem.id, existingItem)
      }
    }
    if (newTextItems.length === 1 && existingTextItems.length === 1) {
      const newItem = newTextItems[0]
      const existingItem = existingTextItems[0]
      if (newItem.id !== existingItem.id) {
        existingById.set(newItem.id, existingItem)
      }
    }

    let updatedCount = 0
    const missingItems: DisplayItem[] = []

    for (const newItem of newDisplayItems) {
      const existing = existingById.get(newItem.id)
      if (!existing) {
        missingItems.push(newItem)
        continue
      }

      if (newItem.displayType === 'assistantText' && existing.displayType === 'assistantText') {
        const next = newItem as AssistantText
        const current = existing as AssistantText
        if (next.content && next.content.length > (current.content || '').length) {
          current.content = next.content
          updatedCount += 1
        }
        if (next.stats && !current.stats) {
          current.stats = next.stats
          updatedCount += 1
        }
        if (next.isLastInMessage !== undefined) {
          current.isLastInMessage = next.isLastInMessage
        }
        if (next.isStreaming !== undefined) {
          current.isStreaming = next.isStreaming
        }
      } else if (newItem.displayType === 'thinking' && existing.displayType === 'thinking') {
        const next = newItem as ThinkingContent
        const current = existing as ThinkingContent
        if (next.content && next.content.length > (current.content || '').length) {
          current.content = next.content
          updatedCount += 1
        }
        if (next.signature && !current.signature) {
          current.signature = next.signature
          updatedCount += 1
        }
      } else if (newItem.displayType === 'toolCall' && existing.displayType === 'toolCall') {
        const next = newItem as ToolCall
        const current = existing as ToolCall
        const nextInput = next.input || {}
        const currentInput = current.input || {}
        if (Object.keys(currentInput).length === 0 && Object.keys(nextInput).length > 0) {
          current.input = nextInput
          updatedCount += 1
        }
        if (next.result && !current.result) {
          current.result = next.result
          updatedCount += 1
        }
        if (next.status && current.status !== next.status) {
          current.status = next.status
          updatedCount += 1
        }
      }
    }

    const desiredItems = newDisplayItems.map(item => existingById.get(item.id) ?? item)
    const messageItems = allDisplayItems.filter(isMessageDisplayItem)
    const orderMismatch = messageItems.length !== desiredItems.length ||
      messageItems.some((item, idx) => item !== desiredItems[idx])
    const needsReorder = missingItems.length > 0 || orderMismatch

    if (needsReorder) {
      const nonMessageItems = allDisplayItems.filter(item => !isMessageDisplayItem(item))
      let insertAt = nonMessageItems.length
      if (messageItems.length > 0) {
        const firstIndex = allDisplayItems.findIndex(isMessageDisplayItem)
        insertAt = 0
        for (let i = 0; i < firstIndex; i += 1) {
          if (!isMessageDisplayItem(allDisplayItems[i])) {
            insertAt += 1
          }
        }
      }
      const reordered = [...nonMessageItems]
      reordered.splice(insertAt, 0, ...desiredItems)
      displayStore.clear()
      displayStore.pushBatch(reordered)
      refreshDisplayWindow()
      log.debug(`[useSessionMessages] Reordered displayItems: total=${desiredItems.length}, added=${missingItems.length}`)
    }

    if (updatedCount > 0 || needsReorder) {
      log.debug(`[useSessionMessages] Updated ${updatedCount} existing displayItems`)
      stats.incrementContentVersion()
    }
  }

  function isPartialAssistantMessage(incoming: Message, streaming: Message): boolean {
    return isAssistantContentPrefix(incoming, streaming)
  }

  function isAssistantContentPrefix(full: Message, partial: Message): boolean {
    if (full.role !== 'assistant' || partial.role !== 'assistant') return false
    if (!Array.isArray(full.content) || !Array.isArray(partial.content)) return false
    if (partial.content.length === 0 || full.content.length === 0) return false
    if (partial.content.length > full.content.length) return false

    for (let i = 0; i < partial.content.length; i += 1) {
      const partialBlock = partial.content[i]
      const fullBlock = full.content[i]
      if (!fullBlock || partialBlock.type !== fullBlock.type) return false

      if (partialBlock.type === 'text') {
        const partialText = (partialBlock as any).text || ''
        const fullText = (fullBlock as any).text || ''
        if (!fullText.startsWith(partialText)) return false
      } else if (partialBlock.type === 'thinking') {
        const partialThinking = (partialBlock as any).thinking || ''
        const fullThinking = (fullBlock as any).thinking || ''
        if (!fullThinking.startsWith(partialThinking)) return false
        const partialSig = (partialBlock as any).signature
        const fullSig = (fullBlock as any).signature
        if (partialSig && fullSig && partialSig !== fullSig) return false
      } else if (partialBlock.type === 'tool_use') {
        const partialId = (partialBlock as any).id
        const fullId = (fullBlock as any).id
        if (partialId && fullId && partialId !== fullId) return false
        const partialName = (partialBlock as any).toolName
        const fullName = (fullBlock as any).toolName
        if (partialName && fullName && partialName !== fullName) return false
      } else if (partialBlock.type === 'tool_result') {
        const partialTool = (partialBlock as any).tool_use_id
        const fullTool = (fullBlock as any).tool_use_id
        if (partialTool && fullTool && partialTool !== fullTool) return false
      }
    }

    return true
  }

  function isAssistantContentSuffix(full: Message, partial: Message): boolean {
    if (full.role !== 'assistant' || partial.role !== 'assistant') return false
    if (!Array.isArray(full.content) || !Array.isArray(partial.content)) return false
    if (partial.content.length === 0 || full.content.length === 0) return false
    if (partial.content.length > full.content.length) return false

    const offset = full.content.length - partial.content.length
    for (let i = 0; i < partial.content.length; i += 1) {
      const partialBlock = partial.content[i]
      const fullBlock = full.content[i + offset]
      if (!fullBlock || partialBlock.type !== fullBlock.type) return false

      if (partialBlock.type === 'text') {
        const partialText = (partialBlock as any).text || ''
        const fullText = (fullBlock as any).text || ''
        if (!fullText.startsWith(partialText)) return false
      } else if (partialBlock.type === 'thinking') {
        const partialThinking = (partialBlock as any).thinking || ''
        const fullThinking = (fullBlock as any).thinking || ''
        if (!fullThinking.startsWith(partialThinking)) return false
        const partialSig = (partialBlock as any).signature
        const fullSig = (fullBlock as any).signature
        if (partialSig && fullSig && partialSig !== fullSig) return false
      } else if (partialBlock.type === 'tool_use') {
        const partialId = (partialBlock as any).id
        const fullId = (fullBlock as any).id
        if (partialId && fullId && partialId !== fullId) return false
        const partialName = (partialBlock as any).toolName
        const fullName = (fullBlock as any).toolName
        if (partialName && fullName && partialName !== fullName) return false
      } else if (partialBlock.type === 'tool_result') {
        const partialTool = (partialBlock as any).tool_use_id
        const fullTool = (fullBlock as any).tool_use_id
        if (partialTool && fullTool && partialTool !== fullTool) return false
      }
    }

    return true
  }

  function findMatchingBlockIndex(blocks: ContentBlock[], incoming: ContentBlock): number {
    if (incoming.type === 'tool_use') {
      const incomingId = (incoming as any).id
      if (incomingId) {
        return blocks.findIndex(
          block => block.type === 'tool_use' && (block as any).id === incomingId
        )
      }
    }
    if (incoming.type === 'tool_result') {
      const incomingTool = (incoming as any).tool_use_id
      if (incomingTool) {
        return blocks.findIndex(
          block => block.type === 'tool_result' && (block as any).tool_use_id === incomingTool
        )
      }
    }
    return -1
  }

  function mergeAssistantBlock(existing: ContentBlock, incoming: ContentBlock): ContentBlock {
    if (existing.type !== incoming.type) return incoming
    if (incoming.type === 'tool_use') {
      const mergedTool = { ...existing, ...incoming } as ToolUseBlock
      if (!mergedTool.toolName && (existing as any).toolName) {
        mergedTool.toolName = (existing as any).toolName
      }
      if (!mergedTool.toolType && (existing as any).toolType) {
        mergedTool.toolType = (existing as any).toolType
      }
      const nextInput = (mergedTool.input || {}) as Record<string, unknown>
      const currentInput = ((existing as any).input || {}) as Record<string, unknown>
      if (Object.keys(nextInput).length === 0 && Object.keys(currentInput).length > 0) {
        mergedTool.input = currentInput
      }
      return mergedTool
    }
    if (incoming.type === 'text') {
      const nextText = (incoming as any).text || ''
      const currentText = (existing as any).text || ''
      return {
        ...incoming,
        text: currentText.length > nextText.length ? currentText : nextText
      } as ContentBlock
    }
    if (incoming.type === 'thinking') {
      const nextThinking = (incoming as any).thinking || ''
      const currentThinking = (existing as any).thinking || ''
      const nextSignature = (incoming as any).signature
      const currentSignature = (existing as any).signature
      return {
        ...incoming,
        thinking: currentThinking.length > nextThinking.length ? currentThinking : nextThinking,
        signature: nextSignature || currentSignature
      } as ContentBlock
    }
    return incoming
  }

  function isAssistantMessageCoveredByStreaming(incoming: Message, streaming: Message): boolean {
    if (incoming.role !== 'assistant' || streaming.role !== 'assistant') return false
    if (!Array.isArray(incoming.content) || !Array.isArray(streaming.content)) return false
    if (incoming.content.length === 0 || streaming.content.length === 0) return false

    for (const incomingBlock of incoming.content) {
      if (incomingBlock.type === 'text') {
        const incomingText = (incomingBlock as any).text || ''
        if (incomingText.length === 0) continue
        const matched = streaming.content.some(block =>
          block.type === 'text' && ((block as any).text || '').startsWith(incomingText)
        )
        if (!matched) return false
      } else if (incomingBlock.type === 'thinking') {
        const incomingThinking = (incomingBlock as any).thinking || ''
        const incomingSig = (incomingBlock as any).signature
        if (incomingThinking.length === 0 && !incomingSig) continue
        const matched = streaming.content.some(block => {
          if (block.type !== 'thinking') return false
          const streamingThinking = (block as any).thinking || ''
          if (!streamingThinking.startsWith(incomingThinking)) return false
          const streamingSig = (block as any).signature
          if (incomingSig && streamingSig && incomingSig !== streamingSig) return false
          return true
        })
        if (!matched) return false
      } else if (incomingBlock.type === 'tool_use') {
        const incomingId = (incomingBlock as any).id
        const incomingName = (incomingBlock as any).toolName
        const matched = streaming.content.some(block => {
          if (block.type !== 'tool_use') return false
          const streamingId = (block as any).id
          if (incomingId && streamingId && incomingId !== streamingId) return false
          const streamingName = (block as any).toolName
          if (incomingName && streamingName && incomingName !== streamingName) return false
          return true
        })
        if (!matched) return false
      } else if (incomingBlock.type === 'tool_result') {
        const incomingTool = (incomingBlock as any).tool_use_id
        const matched = streaming.content.some(block =>
          block.type === 'tool_result' &&
          (!incomingTool || (block as any).tool_use_id === incomingTool)
        )
        if (!matched) return false
      } else {
        return false
      }
    }

    return true
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
      const latestAssistant = [...messages].reverse().find(m => m.role === 'assistant')
      const streamingMessage = findStreamingAssistantMessage()

      if (includePartialMessages.value && streamingMessage && message.id && streamingMessage.id !== message.id) {
        log.info('[useSessionMessages] align streaming message id', {
          fromId: streamingMessage.id,
          toId: message.id
        })
        streamingMessage.id = message.id
        stats.setStreamingMessageId(message.id)
      }

      if (includePartialMessages.value && isGenerating.value && !streamingMessage) {
        message.isStreaming = true
        stats.setStreamingMessageId(message.id)
        log.info('[useSessionMessages] adopt assistant as streaming', {
          messageId: message.id
        })
      }

    const compareAssistant = streamingMessage ?? latestAssistant

    if (includePartialMessages.value && compareAssistant && compareAssistant.id !== message.id) {
      log.info('[useSessionMessages] assistant id mismatch (full vs latest)', {
        fullId: message.id,
        latestId: compareAssistant.id
      })
    }

    if (includePartialMessages.value && compareAssistant && compareAssistant.id === message.id) {
      log.info('[useSessionMessages] 完整消息与最新流式消息相同，跳过重复展示')
      ;(message as any).displayId = getAssistantDisplayId(compareAssistant)
      syncThinkingSignatures(message)
      return
    }

      log.debug('[useSessionMessages] 添加新 assistant 消息')
      ensureAssistantDisplayId(message)
      addMessage(message)
      touchMessages()
      return
    }

    // user 消息处理
    if (message.role === 'user') {
      const hasToolResult = message.content.some((block: ContentBlock) => block.type === 'tool_result')
      const hasToolUse = message.content.some((block: ContentBlock) => block.type === 'tool_use')
      const hasText = message.content.some((block: ContentBlock) => block.type === 'text')

      // 🔍 调试日志：打印 user 消息中的 tool_result 详情
      if (hasToolResult) {
        const toolResults = message.content.filter((block: ContentBlock) => block.type === 'tool_result')
        log.info('[useSessionMessages] user 消息包含 tool_result:', {
          messageId: message.id,
          toolResultCount: toolResults.length,
          toolResults: toolResults.map((r: any) => ({
            tool_use_id: r.tool_use_id,
            is_error: r.is_error,
            contentType: typeof r.content,
            contentPreview: typeof r.content === 'string' 
              ? r.content.substring(0, 100) 
              : Array.isArray(r.content) 
                ? `Array[${r.content.length}]` 
                : JSON.stringify(r.content)?.substring(0, 100)
          }))
        })
      }

      // 压缩摘要消息：直接添加到消息列表
      if ((message as any).isCompactSummary === true) {
        log.info('[useSessionMessages] 添加压缩摘要消息')
        addMessage(message)
        touchMessages()
        return
      }

      // tool_result 消息：更新工具状态，并保留消息用于状态解析
      if (hasToolResult) {
        processToolResults(message.content)
      }

      // 纯 tool_use 的 user 消息：忽略
      if (hasToolUse && !hasText && !hasToolResult) {
        log.debug('[useSessionMessages] 忽略纯 tool_use 的 user 消息')
        return
      }

      // Skill 内容消息：已关联到 Skill 工具卡片，不单独显示
      if (isSkillContentMessage(message.id)) {
        log.debug('[useSessionMessages] 忽略 Skill 内容消息（已关联到工具卡片）:', message.id)
        // 仍然保存消息用于状态追踪，但不生成 DisplayItem
        addMessage(message)
        return
      }

      // 检查是否已存在（避免重复）- 基于 ID
      const existingUserMsg = messages.find(m => m.id === message.id)
      if (existingUserMsg) {
        log.debug('[useSessionMessages] 忽略重复的 user 消息:', message.id)
        return
      }

      // 检查是否是刚发送消息的回放
      // 如果后端返回的消息有 uuid，找到最后一条没有 uuid 的用户消息并更新
      // （因为流式 assistant 消息可能已经被添加，所以不能只检查 lastMsg）
      if (message.uuid && hasText) {
        // 从后往前找最后一条没有 uuid 的用户消息
        const localUserMsg = [...messages].reverse().find(
          m => m.role === 'user' && !(m as any).uuid
        )
        if (localUserMsg) {
          log.info('[useSessionMessages] 更新本地用户消息的 uuid:', message.uuid)
          ;(localUserMsg as any).uuid = message.uuid

          // 同时更新对应的 displayItem 的 uuid
          const displayItem = displayItems.find(
            item => isDisplayUserMessage(item) && item.id === localUserMsg.id
          ) as UserMessage | undefined
          if (displayItem) {
            displayItem.uuid = message.uuid
          }
          touchMessages()
          return
        }
      }

      // 添加新的 user 消息（历史加载或后端回放的消息，标记为 hint 样式）
      addMessage(message)
      // 设置 style: 'hint'（禁止编辑，md 渲染）
      const addedItem = displayItems.find(item => isDisplayUserMessage(item) && item.id === message.id)
      if (addedItem) {
        (addedItem as any).style = 'hint'
      }
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
  function addMessageToUI(message: { contexts: any[]; contents: ContentBlock[]; ideContext?: ActiveFileInfo | null }): {
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

    // 将 ideContext 转换为 XML ContentBlock（发送后端时使用）
    const ideContextBlocks = ideContextToContentBlocks(message.ideContext)

    // UI 展示用：contexts 在前（让用户看到附件）
    const uiContent = [...contextBlocks, ...message.contents, ...ideContextBlocks]
    // 发送 SDK 用：用户内容在前，contexts 在后（符合 Claude Code CLI 格式）
    const mergedContent = [...message.contents, ...contextBlocks, ...ideContextBlocks]

    log.debug('[useSessionMessages] addMessageToUI:', {
      contexts: message.contexts.length,
      contents: message.contents.length,
      ideContext: !!message.ideContext,
      uiContent: uiContent.length,
      merged: mergedContent.length
    })

    // 创建用户消息（UI 展示用 uiContent）
    const userMessage: Message = {
      id: generateMessageId('user'),
      role: 'user',
      timestamp: Date.now(),
      content: uiContent
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
  function addToQueue(message: { contexts: any[]; contents: ContentBlock[]; ideContext?: ActiveFileInfo | null }): void {
    // 将 contexts 转换为 ContentBlock 格式
    const contextBlocks = message.contexts.length > 0
      ? buildUserMessageContent({
          text: '',
          contexts: message.contexts
        })
      : []

    // 将 ideContext 转换为 XML ContentBlock（发送后端时使用）
    const ideContextBlocks = ideContextToContentBlocks(message.ideContext)

    // UI 展示用：contexts 在前（让用户看到附件）
    const uiContent = [...contextBlocks, ...message.contents, ...ideContextBlocks]
    // 发送 SDK 用：用户内容在前，contexts 在后（符合 Claude Code CLI 格式）
    const mergedContent = [...message.contents, ...contextBlocks, ...ideContextBlocks]

    const id = generateMessageId('user')
    log.info(`[useSessionMessages] 消息加入队列（不添加到 UI）: ${id}`)

    messageQueue.value.push({
      id,
      contexts: message.contexts,
      contents: message.contents,
      ideContext: message.ideContext,  // 保存结构化的 IDE 上下文（用于队列显示）
      uiContent,  // UI 展示用的内容
      mergedContent,  // 发送后端用的内容（用户内容在前）
      createdAt: Date.now()
    })
  }

  /**
   * 将消息插入队列最前面（用于强制发送场景）
   * 不添加到 UI，等待 result 返回后自动发送
   */
  function prependToQueue(message: { contexts: any[]; contents: ContentBlock[]; ideContext?: ActiveFileInfo | null }): void {
    // 将 contexts 转换为 ContentBlock 格式
    const contextBlocks = message.contexts.length > 0
      ? buildUserMessageContent({
          text: '',
          contexts: message.contexts
        })
      : []

    // 将 ideContext 转换为 XML ContentBlock（发送后端时使用）
    const ideContextBlocks = ideContextToContentBlocks(message.ideContext)

    // UI 展示用：contexts 在前（让用户看到附件）
    const uiContent = [...contextBlocks, ...message.contents, ...ideContextBlocks]
    // 发送 SDK 用：用户内容在前，contexts 在后（符合 Claude Code CLI 格式）
    const mergedContent = [...message.contents, ...contextBlocks, ...ideContextBlocks]

    const id = generateMessageId('user')
    log.info(`[useSessionMessages] 消息插入队列最前面: ${id}`)

    messageQueue.value.unshift({
      id,
      contexts: message.contexts,
      contents: message.contents,
      ideContext: message.ideContext,  // 保存结构化的 IDE 上下文（用于队列显示）
      uiContent,  // UI 展示用的内容
      mergedContent,  // 发送后端用的内容（用户内容在前）
      createdAt: Date.now()
    })
  }

  /**
   * 设置打断模式
   * - 'clear': 打断后清空队列（用户主动打断）
   * - 'keep': 打断后保留队列并自动发送（强制发送场景）
   */
  function setInterruptMode(mode: InterruptMode): void {
    interruptMode = mode
    log.info(`[useSessionMessages] 设置打断模式: ${mode}`)
  }

  /**
   * 开始生成状态（由 useSessionTab 调用）
   *
   * @param userMessageId 用户消息 ID
   * @returns streamingMessageId 用于追踪的 assistant 消息 ID
   */
  function startGenerating(userMessageId: string): string {
    const streamingMessageId = generateMessageId('assistant')
    stats.startRequestTracking(userMessageId)
    stats.setStreamingMessageId(streamingMessageId)

    log.info('[useSessionMessages] 📤 startGenerating，用户消息 ID:', userMessageId)
    isGenerating.value = true
    log.info('[useSessionMessages] ✅ isGenerating 已设置为 true')

    // 🧹 清理已完成的工具调用（避免旧记录影响新请求）
    // 使用较短的 maxAge（0）立即清理所有已完成的工具调用
    const cleaned = tools.cleanupCompletedToolCalls(0)
    if (cleaned > 0) {
      log.info(`[useSessionMessages] 🧹 清理了 ${cleaned} 个已完成的工具调用`)
    }

    // 更新 displayItem 的 isStreaming 状态
    const displayItemIndex = displayItems.findIndex(
      item => isDisplayUserMessage(item) && item.id === userMessageId
    )
    if (displayItemIndex !== -1) {
      const userDisplayItem = displayItems[displayItemIndex] as UserMessage
      userDisplayItem.isStreaming = true
      triggerDisplayItemsUpdate()
    }

    return streamingMessageId
  }

  /**
   * 停止生成状态（发送失败时调用）
   */
  function stopGenerating(): void {
    isGenerating.value = false
    stats.cancelRequestTracking()
    log.info('[useSessionMessages] isGenerating 已设置为 false')
  }


  /**
   * 取出队列中的下一条消息并准备发送
   *
   * @returns 准备好的消息信息，如果队列为空则返回 null
   */
  function popNextQueuedMessage(): {
    userMessage: Message
    mergedContent: ContentBlock[]
    originalMessage: { contexts: any[]; contents: ContentBlock[] }
  } | null {
    if (messageQueue.value.length === 0) {
      return null
    }

    const nextMessage = messageQueue.value.shift()
    if (!nextMessage) {
      return null
    }

    log.info(`[useSessionMessages] 从队列中取出消息: ${nextMessage.id}`)

    // 检查消息是否已在 UI 中（发送失败重试的情况）
    const existingItem = displayItems.find(
      item => isDisplayUserMessage(item) && item.id === nextMessage.id
    )

    if (existingItem) {
      // 消息已在 UI 中（发送失败重试）
      return {
        userMessage: {
          id: nextMessage.id,
          role: 'user',
          timestamp: nextMessage.createdAt,
          content: nextMessage.uiContent || nextMessage.mergedContent!  // UI 展示用 uiContent
        } as Message,
        mergedContent: nextMessage.mergedContent!,  // 发送后端用 mergedContent
        originalMessage: { contexts: nextMessage.contexts, contents: nextMessage.contents }
      }
    } else {
      // 消息不在 UI 中（生成中排队的），先添加到 UI
      const { userMessage, mergedContent } = addMessageToUI({
        contexts: nextMessage.contexts,
        contents: nextMessage.contents,
        ideContext: nextMessage.ideContext  // 修复：传递 IDE 上下文
      })
      return {
        userMessage,
        mergedContent,
        originalMessage: { contexts: nextMessage.contexts, contents: nextMessage.contents }
      }
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
      const matched = [...messages].reverse().find(
        msg => msg.id === streamingId && msg.role === 'assistant' && msg.isStreaming
      )
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
    if (existing) {
      lastStreamingMessageId = existing.id
      ensureAssistantDisplayId(existing)
      return existing
    }
    throw new Error('[useSessionMessages] Streaming message not found')
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

  function renameDisplayItemsForMessage(oldMessageId: string, newMessageId: string): void {
    if (!oldMessageId || oldMessageId === newMessageId) return
    const oldPrefix = `${oldMessageId}-`
    const newPrefix = `${newMessageId}-`
    let updated = false

    for (const item of displayItems) {
      if (!item?.id) continue
      if (item.id === oldMessageId) {
        item.id = newMessageId
        updated = true
        continue
      }
      if (item.id.startsWith(oldPrefix)) {
        item.id = `${newPrefix}${item.id.slice(oldPrefix.length)}`
        updated = true
      }
    }

    if (updated) {
      displayStore.reindexKeys()
      triggerDisplayItemsUpdate()
    }
  }

  /**
   * 增量更新文本 displayItem
   */
  function updateTextDisplayItemIncrementally(
    message: Message,
    blockIndex: number,
    newText: string
  ): void {
    const expectedId = `${getAssistantDisplayId(message)}-text-${blockIndex}`

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
    const expectedId = `${getAssistantDisplayId(message)}-thinking-${blockIndex}`

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

    log.info(`[processToolResults] 开始处理 ${toolResults.length} 个 tool_result`)

    let hasUpdates = false
    for (const result of toolResults) {
      log.info(`[processToolResults] 处理 tool_result:`, {
        tool_use_id: result.tool_use_id,
        is_error: result.is_error,
        hasContent: !!result.content
      })
      
      const success = tools.updateToolResult(result.tool_use_id, result)
      log.info(`[processToolResults] updateToolResult 结果: ${success ? '成功' : '失败'}, tool_use_id=${result.tool_use_id}`)
      
      if (success) {
        hasUpdates = true
        // 不再自动执行 IDEA 操作
        // 改为用户点击工具卡片时通过 toolShowInterceptor 触发
      }
    }

    // 强制触发 Vue 响应式更新
    if (hasUpdates) {
      log.info(`[processToolResults] 触发 displayItems 更新`)
      triggerDisplayItemsUpdate()
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
    const randomSuffix = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID().substring(0, 8)
      : Math.random().toString(16).slice(2, 10)
    return `${role}-${Date.now()}-${randomSuffix}`
  }

  function ensureAssistantDisplayId(message: Message): string {
    if (message.role !== 'assistant') return message.id
    const existing = (message as any).displayId as string | undefined
    if (existing) return existing
    const baseId = message.id || generateMessageId('assistant')
    const randomSuffix = typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID().substring(0, 8)
      : Math.random().toString(16).slice(2, 10)
    const displayId = `${baseId}-ui-${randomSuffix}`
    ;(message as any).displayId = displayId
    return displayId
  }

  function getAssistantDisplayId(message: Message): string {
    if (message.role !== 'assistant') return message.id
    return (message as any).displayId || message.id
  }

  function findLatestAssistantById(messageId: string): Message | undefined {
    for (let i = messages.length - 1; i >= 0; i -= 1) {
      const msg = messages[i]
      if (msg.role === 'assistant' && msg.id === messageId) {
        return msg
      }
    }
    return undefined
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
   * 检查消息是否已存在（基于 id 或 uuid）
   */
  function isMessageExists(msg: Message): boolean {
    // 基于 ID 检查
    if (messages.some(m => m.id === msg.id)) {
      return true
    }
    // 基于 uuid 检查（如果消息有 uuid）
    const msgUuid = (msg as any).uuid
    if (msgUuid && messages.some(m => (m as any).uuid === msgUuid)) {
      return true
    }
    return false
  }

  /**
   * 从消息数组中检测 Skill 内容消息 ID 集合
   * 基于消息顺序：Skill 工具调用（assistant msg）后的第一个 user 消息
   */
  function detectSkillContentMessageIds(msgList: Message[]): Set<string> {
    const ids = new Set<string>()
    for (let i = 0; i < msgList.length; i++) {
      const msg = msgList[i]
      if (msg.role !== 'assistant') continue
      for (const block of msg.content) {
        if (block.type === 'tool_use' &&
            ((block as any).toolName === 'Skill' || (block as any).name === 'Skill')) {
          const nextMsg = msgList[i + 1]
          if (nextMsg?.role === 'user') {
            ids.add(nextMsg.id)
          }
        }
      }
    }
    return ids
  }

  /**
   * 批量前插消息（用于历史回放）
   */
  function prependMessagesBatch(msgs: Message[]): void {
    if (msgs.length === 0) return
    
    // 过滤掉已存在的消息
    const newMsgs = msgs.filter(m => !isMessageExists(m))
    if (newMsgs.length === 0) {
      log.debug('[useSessionMessages] prependMessagesBatch: 所有消息已存在，跳过')
      return
    }
    if (newMsgs.length < msgs.length) {
      log.debug(`[useSessionMessages] prependMessagesBatch: 过滤掉 ${msgs.length - newMsgs.length} 条重复消息`)
    }

    newMsgs.forEach(m => {
      if (m.role === 'assistant') {
        ensureAssistantDisplayId(m)
      }
    })

    // 先添加消息到数组，确保 skillContentMap computed 能检测到
    for (let i = newMsgs.length - 1; i >= 0; i -= 1) {
      messages.unshift(newMsgs[i])
    }

    // 检测并过滤 Skill 内容消息（不生成独立 DisplayItem）
    const skillContentIds = detectSkillContentMessageIds(messages)
    const msgsForDisplay = newMsgs.filter(m => !skillContentIds.has(m.id))

    const displayBatch = msgsForDisplay.flatMap(m => convertMessageToDisplayItems(m, tools.pendingToolCalls, getSkillContent))
    // 历史消息中的用户消息设置 hint 样式（禁止编辑，md 渲染）
    displayBatch.forEach(item => {
      if (isDisplayUserMessage(item)) {
        (item as UserMessage).style = 'hint'
      }
    })
    prependDisplayItems(displayBatch)
  }

  /**
   * 批量尾插消息
   */
  function appendMessagesBatch(msgs: Message[]): void {
    if (msgs.length === 0) return
    
    // 过滤掉已存在的消息
    const newMsgs = msgs.filter(m => !isMessageExists(m))
    if (newMsgs.length === 0) {
      log.debug('[useSessionMessages] appendMessagesBatch: 所有消息已存在，跳过')
      return
    }
    if (newMsgs.length < msgs.length) {
      log.debug(`[useSessionMessages] appendMessagesBatch: 过滤掉 ${msgs.length - newMsgs.length} 条重复消息`)
    }

    newMsgs.forEach(m => {
      if (m.role === 'assistant') {
        ensureAssistantDisplayId(m)
      }
    })

    // 先添加消息到数组，确保 skillContentMap computed 能检测到
    messages.push(...newMsgs)

    // 检测并过滤 Skill 内容消息（不生成独立 DisplayItem）
    const skillContentIds = detectSkillContentMessageIds(messages)
    const msgsForDisplay = newMsgs.filter(m => !skillContentIds.has(m.id))

    const displayBatch = msgsForDisplay.flatMap(m => convertMessageToDisplayItems(m, tools.pendingToolCalls, getSkillContent))
    // 历史/后端消息中的用户消息设置 hint 样式（禁止编辑，md 渲染）
    displayBatch.forEach(item => {
      if (isDisplayUserMessage(item)) {
        (item as UserMessage).style = 'hint'
      }
    })
    pushDisplayItems(displayBatch)
  }

  /**
   * 更新消息并截断其后的内容（用于编辑重发功能）
   *
   * 找到指定 UUID 的用户消息，更新其内容并清空 UUID（让它看起来像新发送的消息），
   * 然后删除该消息之后的所有 displayItems 和 messages。
   *
   * @param uuid 要更新的用户消息 UUID
   * @param newContent 新的消息内容
   * @returns 更新后的 Message 对象和 mergedContent，用于后续发送；失败时返回 null
   */
  function truncateMessages(
    uuid: string,
    newContent: { contexts?: any[]; contents: ContentBlock[] }
  ): { userMessage: Message; mergedContent: ContentBlock[] } | null {
    log.info(`[useSessionMessages] 更新并截断消息: uuid=${uuid}`)

    // 1. 在 displayItems 中找到对应的 UserMessage
    const displayIndex = displayItems.findIndex(
      item => isDisplayUserMessage(item) && (item as UserMessage).uuid === uuid
    )

    if (displayIndex === -1) {
      log.warn(`[useSessionMessages] 未找到 uuid=${uuid} 的消息`)
      return null
    }

    // 2. 获取并更新 displayItem
    const currentDisplayItem = displayItems[displayIndex] as UserMessage
    currentDisplayItem.content = newContent.contents
    currentDisplayItem.contexts = newContent.contexts
    // 清空 UUID，让消息看起来像新发送的
    currentDisplayItem.uuid = undefined
    // 清空请求统计（因为要重新发送）
    currentDisplayItem.requestStats = undefined

    // 3. 构建 mergedContent（与 addMessageToUI 相同逻辑）
    const contextBlocks = (newContent.contexts?.length ?? 0) > 0
      ? buildUserMessageContent({
          text: '',
          contexts: newContent.contexts!
        })
      : []
    const mergedContent = [...contextBlocks, ...newContent.contents]

    // 4. 更新 messages 中对应的 Message
    const messageIndex = messages.findIndex(m => m.id === currentDisplayItem.id)
    let userMessage: Message
    if (messageIndex !== -1) {
      messages[messageIndex].content = mergedContent
      userMessage = messages[messageIndex]
      // 删除之后的消息
      messages.splice(messageIndex + 1)
      log.info(`[useSessionMessages] 从 messages index=${messageIndex + 1} 开始截断`)
    } else {
      // 如果找不到对应的 message（不应该发生），创建一个新的
      userMessage = {
        id: currentDisplayItem.id,
        role: 'user',
        timestamp: Date.now(),
        content: mergedContent
      }
      log.warn(`[useSessionMessages] 未找到对应的 message，创建新的`)
    }

    // 5. 保留当前消息，只截断其后的 displayItems
    const truncatedDisplayItems = displayItems.slice(0, displayIndex + 1)
    displayStore.clear()
    if (truncatedDisplayItems.length > 0) {
      displayStore.pushBatch(truncatedDisplayItems)
    }
    displayItems.splice(0, displayItems.length, ...truncatedDisplayItems)

    log.info(`[useSessionMessages] 截断完成: displayItems 剩余 ${displayItems.length} 个, messages 剩余 ${messages.length} 个`)
    return { userMessage, mergedContent }
  }

  /**
   * 重置所有状态
   */
  function reset(): void {
    clearMessages()
    clearQueue()
    isGenerating.value = false
    lastError.value = null
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
    setBeforeProcessQueueFn,
    setProcessQueueFn,
    setIncludePartialMessages,
    appendMessagesBatch,
    prependMessagesBatch,

    // 消息处理方法
    handleStreamEvent,
    handleResultMessage,
    handleNormalMessage,

    // 消息 UI 方法
    addMessageToUI,
    addToQueue,
    popNextQueuedMessage,

    // 生成状态控制（由 useSessionTab 调用）
    startGenerating,
    stopGenerating,

    // 队列管理
    editQueueMessage,
    removeFromQueue,
    clearQueue,
    prependToQueue,
    setInterruptMode,

    // 查询方法
    findStreamingAssistantMessage,

    // 管理方法
    clearMessages,
    reset,
    addErrorMessage,
    truncateMessages,

    // 窗口化辅助（供历史前插调用）
    pushDisplayItems,
    prependDisplayItems,
    refreshDisplayWindow,

    // Skill 内容关联
    skillContentMap,
    isSkillContentMessage,
    getSkillContent
  }
}

/**
 * useSessionMessages 返回类型
 */
export type SessionMessagesInstance = ReturnType<typeof useSessionMessages>
