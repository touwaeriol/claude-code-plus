/**
 * Stream Event 处理器
 *
 * 负责处理各种 stream event 的业务逻辑
 * 将 sessionStore 中过长的 handleStreamEvent 函数拆分为职责单一的小函数
 */

import type { Message, ToolUseBlock, ThinkingBlock, TextBlock, ContentBlock } from '@/types/message'
import type { StreamEvent } from '@/types/streamEvent'
import { loggers } from '@/utils/logger'
import {
  isMessageStartEvent,
  isMessageDeltaEvent,
  isMessageStopEvent,
  isContentBlockStartEvent,
  isContentBlockDeltaEvent,
  isContentBlockStopEvent,
  isTextDelta,
  isInputJsonDelta,
  isThinkingDelta,
  applyTextDelta,
  applyInputJsonDelta,
  applyThinkingDelta,
  findOrCreateLastAssistantMessage
} from '@/utils/streamEventHandler'
import { isToolUseBlock } from '@/utils/contentBlockUtils'

const log = loggers.stream

/**
 * 判断消息内容是否实际为空
 *
 * 考虑以下情况为"实际为空"：
 * 1. content 数组长度为 0
 * 2. content 只包含空文本块（text 为空字符串或只有空白字符）
 */
function isMessageContentEmpty(content: ContentBlock[]): boolean {
  if (content.length === 0) return true

  // 检查是否只有空文本块
  return content.every(block => {
    if (block.type === 'text') {
      const textBlock = block as TextBlock
      return !textBlock.text || textBlock.text.trim() === ''
    }
    // 其他类型的块（如 tool_use）不算空
    return false
  })
}

/**
 * Stream Event 处理结果
 */
export interface StreamEventProcessResult {
  shouldUpdateMessages: boolean  // 是否需要更新 messages
  shouldUpdateDisplayItems: boolean  // 是否需要更新 displayItems
  shouldSetGenerating: boolean | null  // 是否需要设置生成状态 (null = 不改变)
  messageUpdated: boolean  // 消息是否被更新
}

/**
 * Stream Event 处理上下文
 */
export interface StreamEventContext {
  messages: Message[]  // 会话消息列表
  toolInputJsonAccumulator: Map<string, string>  // JSON 累积器
  registerToolCall?: (block: ToolUseBlock) => void  // 注册工具调用的回调
}

/**
 * 处理 message_start 事件
 *
 * 简化逻辑：
 * 1. 查找最后一个 assistant 消息
 * 2. 如果是空的占位符，继续使用并更新 ID
 * 3. 否则创建新消息（这是新的一轮 API 调用）
 */
export function processMessageStart(
  event: StreamEvent,
  context: StreamEventContext
): StreamEventProcessResult {
  if (!isMessageStartEvent(event)) {
    return createNoOpResult()
  }

  const eventMessageId = event.message?.id
  log.debug(`processMessageStart: id=${eventMessageId}`)

  // 查找最后一个 assistant 消息
  const lastMessage = context.messages
    .slice()
    .reverse()
    .find(m => m.role === 'assistant')

  // 情况1：有空的占位符消息，继续使用它
  // 使用 isMessageContentEmpty 判断，考虑空文本块的情况
  if (lastMessage && isMessageContentEmpty(lastMessage.content)) {
    // 更新消息 ID（如果后端返回了 ID）
    if (eventMessageId && lastMessage.id !== eventMessageId) {
      const messageIndex = context.messages.findIndex(m => m.id === lastMessage.id)
      if (messageIndex !== -1) {
        context.messages[messageIndex] = { ...lastMessage, id: eventMessageId }
      }
    }

    return {
      shouldUpdateMessages: true,
      shouldUpdateDisplayItems: false,
      shouldSetGenerating: true,
      messageUpdated: true
    }
  }

  // 情况2：没有消息或最后一条消息已有实际内容，创建新消息
  const newMessage: Message = {
    id: eventMessageId || `assistant-placeholder-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`,
    role: 'assistant',
    content: [],
    timestamp: Date.now()
  }
  context.messages.push(newMessage)

  return {
    shouldUpdateMessages: true,
    shouldUpdateDisplayItems: true,
    shouldSetGenerating: true,
    messageUpdated: true
  }
}

/**
 * 处理 content_block_delta 事件
 */
export function processContentBlockDelta(
  event: StreamEvent,
  context: StreamEventContext
): StreamEventProcessResult {
  if (!isContentBlockDeltaEvent(event)) {
    return createNoOpResult()
  }

  const { index, delta } = event
  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)
  let success = false

  if (isTextDelta(delta)) {
    // 处理文本增量
    success = applyTextDelta(lastAssistantMessage, index, delta)
  } else if (isInputJsonDelta(delta)) {
    // 处理工具输入 JSON 增量
    success = applyInputJsonDelta(
      lastAssistantMessage,
      index,
      delta,
      context.toolInputJsonAccumulator
    )
  } else if (isThinkingDelta(delta)) {
    // 处理 Thinking 增量
    success = applyThinkingDelta(lastAssistantMessage, index, delta)
  }

  return {
    shouldUpdateMessages: true,
    shouldUpdateDisplayItems: true,  // 增量更新需要实时反映到 UI
    shouldSetGenerating: true,
    messageUpdated: success
  }
}

/**
 * 处理 content_block_start 事件
 */
export function processContentBlockStart(
  event: StreamEvent,
  context: StreamEventContext
): StreamEventProcessResult {
  if (!isContentBlockStartEvent(event)) {
    return createNoOpResult()
  }

  const { index, content_block } = event
  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)
  let blockAdded = false

  if (content_block.type === 'tool_use' && content_block.id && content_block.name) {
    // 处理工具调用块
    blockAdded = processToolUseBlock(content_block, index, lastAssistantMessage, context)
  } else if (content_block.type === 'text') {
    // 文本块开始（通常不需要特殊处理，等待 delta 事件）
  } else if (content_block.type === 'thinking') {
    // 处理 Thinking 块
    blockAdded = processThinkingBlock(content_block, index, lastAssistantMessage)
  }

  return {
    shouldUpdateMessages: blockAdded,
    shouldUpdateDisplayItems: blockAdded,
    shouldSetGenerating: true,
    messageUpdated: blockAdded
  }
}

/**
 * 处理工具调用块
 */
function processToolUseBlock(
  content_block: any,
  index: number,
  message: Message,
  context: StreamEventContext
): boolean {
  // 检查是否已存在该工具调用
  const existingBlock = message.content.find(
    (block) => isToolUseBlock(block) && block.id === content_block.id
  )

  if (existingBlock) {
    return false  // 已存在，不重复添加
  }

  // 添加新的工具调用块
  const toolUseBlock: ToolUseBlock = {
    type: 'tool_use',
    id: content_block.id,
    name: content_block.name,
    input: content_block.input || {}
  }

  // 确保在正确的位置插入（根据 index）
  if (index >= message.content.length) {
    message.content = [...message.content, toolUseBlock]
  } else {
    const newContent = [...message.content]
    newContent[index] = toolUseBlock
    message.content = newContent
  }

  // 注册到 store
  if (context.registerToolCall) {
    context.registerToolCall(toolUseBlock)
  }

  return true
}

/**
 * 处理 Thinking 块
 */
function processThinkingBlock(
  content_block: any,
  index: number,
  message: Message
): boolean {
  const thinkingBlock: ThinkingBlock = {
    type: 'thinking',
    thinking: content_block.thinking || '',
    signature: content_block.signature
  }
  
  if (index >= message.content.length) {
    message.content = [...message.content, thinkingBlock]
  } else {
    const newContent = [...message.content]
    newContent[index] = thinkingBlock
    message.content = newContent
  }

  return true
}

/**
 * 处理 content_block_stop 事件
 */
export function processContentBlockStop(
  event: StreamEvent,
  context: StreamEventContext
): StreamEventProcessResult {
  if (!isContentBlockStopEvent(event)) {
    return createNoOpResult()
  }

  const { index } = event
  const lastAssistantMessage = findOrCreateLastAssistantMessage(context.messages)

  // 清理该内容块对应的 JSON 累积器（如果是工具调用块）
  if (index !== undefined && lastAssistantMessage.content[index]) {
    const block = lastAssistantMessage.content[index]
    if (isToolUseBlock(block)) {
      const accumulatorKey = `tool_input_${block.id}`
      context.toolInputJsonAccumulator.delete(accumulatorKey)
    }
  }

  return {
    shouldUpdateMessages: false,
    shouldUpdateDisplayItems: true,
    shouldSetGenerating: null,
    messageUpdated: false
  }
}

/**
 * 处理 message_delta 事件
 */
export function processMessageDelta(
  event: StreamEvent,
  context: StreamEventContext
): StreamEventProcessResult {
  if (!isMessageDeltaEvent(event)) {
    return createNoOpResult()
  }

  // message_delta 通常不需要更新 UI，只是元数据
  return {
    shouldUpdateMessages: false,
    shouldUpdateDisplayItems: false,
    shouldSetGenerating: null,
    messageUpdated: false
  }
}

/**
 * 处理 message_stop 事件
 *
 * 注意：message_stop 表示单轮 API 调用结束，但不代表整个请求结束！
 * 一次用户请求可能包含多轮 API 调用（Claude 调用工具后会继续调用 API）。
 *
 * isGenerating 只应在收到 ResultMessage (type: "result") 时才设置为 false。
 */
export function processMessageStop(
  event: StreamEvent,
  context: StreamEventContext,
  messages: Message[]
): StreamEventProcessResult {
  if (!isMessageStopEvent(event)) {
    return createNoOpResult()
  }

  // 清理所有工具输入的 JSON 累积器
  context.toolInputJsonAccumulator.clear()

  // 更新消息的 timestamp（使用当前时间作为完成时间）
  const lastAssistantMessage = findOrCreateLastAssistantMessage(messages)
  const messageIndex = messages.findIndex(m => m.id === lastAssistantMessage.id)
  if (messageIndex !== -1) {
    messages[messageIndex] = {
      ...messages[messageIndex],
      timestamp: Date.now()
    }
  }

  // 注意：shouldSetGenerating 设为 null，不修改 isGenerating 状态
  // isGenerating 只在 handleResultMessage() 中设置为 false
  return {
    shouldUpdateMessages: true,
    shouldUpdateDisplayItems: true,
    shouldSetGenerating: null,  // 不修改 isGenerating，等待 ResultMessage
    messageUpdated: true
  }
}

/**
 * 创建空操作结果
 */
function createNoOpResult(): StreamEventProcessResult {
  return {
    shouldUpdateMessages: false,
    shouldUpdateDisplayItems: false,
    shouldSetGenerating: null,
    messageUpdated: false
  }
}

/**
 * 统一的 Stream Event 处理入口
 * 
 * 根据事件类型分发到对应的处理函数
 */
export function processStreamEvent(
  event: StreamEvent,
  context: StreamEventContext
): StreamEventProcessResult {
  const eventType = event.type

  switch (eventType) {
    case 'message_start':
      return processMessageStart(event, context)
    
    case 'content_block_delta':
      return processContentBlockDelta(event, context)
    
    case 'content_block_start':
      return processContentBlockStart(event, context)
    
    case 'content_block_stop':
      return processContentBlockStop(event, context)
    
    case 'message_delta':
      return processMessageDelta(event, context)
    
    case 'message_stop':
      return processMessageStop(event, context, context.messages)
    
    default:
      log.warn(`未知的事件类型: ${eventType}`)
      return createNoOpResult()
  }
}
