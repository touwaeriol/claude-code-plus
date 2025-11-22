/**
 * Stream Event 处理工具函数
 * 
 * 提供类型安全的 stream event 解析和处理功能
 */

import type {
  StreamEvent,
  StreamEventData,
  MessageStartEvent,
  MessageDeltaEvent,
  MessageStopEvent,
  ContentBlockStartEvent,
  ContentBlockDeltaEvent,
  ContentBlockStopEvent,
  TextDelta,
  InputJsonDelta,
  ThinkingDelta
} from '@/types/streamEvent'
import type { Message, TextBlock, ToolUseBlock, ThinkingBlock } from '@/types/message'
import { isToolUseBlock } from '@/utils/contentBlockUtils'

/**
 * 解析 Stream Event 数据
 */
export function parseStreamEventData(data: any): StreamEventData | null {
  if (!data || typeof data !== 'object') {
    return null
  }

  // 支持两种格式：
  // 1. 直接是 StreamEventData
  // 2. 包装在 event 字段中
  const event = data.event || data.message?.event || data

  if (!event || typeof event !== 'object' || !event.type) {
    return null
  }

  return {
    type: 'stream_event',
    uuid: data.uuid,
    session_id: data.session_id,
    event: event as StreamEvent,
    parent_tool_use_id: data.parent_tool_use_id
  }
}

/**
 * 类型守卫：检查是否为 MessageStartEvent
 */
export function isMessageStartEvent(event: StreamEvent): event is MessageStartEvent {
  return event.type === 'message_start'
}

/**
 * 类型守卫：检查是否为 MessageDeltaEvent
 */
export function isMessageDeltaEvent(event: StreamEvent): event is MessageDeltaEvent {
  return event.type === 'message_delta'
}

/**
 * 类型守卫：检查是否为 MessageStopEvent
 */
export function isMessageStopEvent(event: StreamEvent): event is MessageStopEvent {
  return event.type === 'message_stop'
}

/**
 * 类型守卫：检查是否为 ContentBlockStartEvent
 */
export function isContentBlockStartEvent(event: StreamEvent): event is ContentBlockStartEvent {
  return event.type === 'content_block_start'
}

/**
 * 类型守卫：检查是否为 ContentBlockDeltaEvent
 */
export function isContentBlockDeltaEvent(event: StreamEvent): event is ContentBlockDeltaEvent {
  return event.type === 'content_block_delta'
}

/**
 * 类型守卫：检查是否为 ContentBlockStopEvent
 */
export function isContentBlockStopEvent(event: StreamEvent): event is ContentBlockStopEvent {
  return event.type === 'content_block_stop'
}

/**
 * 类型守卫：检查是否为 TextDelta
 */
export function isTextDelta(delta: any): delta is TextDelta {
  return delta && delta.type === 'text_delta' && typeof delta.text === 'string'
}

/**
 * 类型守卫：检查是否为 InputJsonDelta
 */
export function isInputJsonDelta(delta: any): delta is InputJsonDelta {
  return delta && delta.type === 'input_json_delta' && typeof delta.partial_json === 'string'
}

/**
 * 类型守卫：检查是否为 ThinkingDelta
 */
export function isThinkingDelta(delta: any): delta is ThinkingDelta {
  return delta && delta.type === 'thinking_delta' && typeof delta.delta === 'string'
}

/**
 * 处理文本增量更新
 * 
 * @param message 目标消息
 * @param index 内容块索引
 * @param delta 文本增量
 * @returns 是否成功更新
 */
export function applyTextDelta(
  message: Message,
  index: number,
  delta: TextDelta
): boolean {
  if (!delta.text) {
    return false
  }

  const existingBlock = message.content[index] as TextBlock | undefined

  if (existingBlock && existingBlock.type === 'text') {
    // 追加到现有文本块
    const updatedBlock: TextBlock = {
      type: 'text',
      text: existingBlock.text + delta.text
    }
    const newContent = [...message.content]
    newContent[index] = updatedBlock
    message.content = newContent
    return true
  } else {
    // 创建新的文本块
    const newBlock: TextBlock = {
      type: 'text',
      text: delta.text
    }
    
    if (index >= message.content.length) {
      // 追加到末尾
      message.content = [...message.content, newBlock]
    } else {
      // 替换指定位置
      const newContent = [...message.content]
      newContent[index] = newBlock
      message.content = newContent
    }
    return true
  }
}

/**
 * 处理工具输入 JSON 增量更新
 * 
 * @param message 目标消息
 * @param index 内容块索引
 * @param delta JSON 增量
 * @param accumulator 累积器 Map（用于存储部分 JSON 字符串）
 * @returns 是否成功更新
 */
export function applyInputJsonDelta(
  message: Message,
  index: number,
  delta: InputJsonDelta,
  accumulator: Map<string, string>
): boolean {
  if (!delta.partial_json) {
    return false
  }

  // 查找对应的 tool_use 块
  // 优先通过 index 查找（最准确）
  let toolUseBlock: ToolUseBlock | undefined
  let toolIndex = -1

  if (index < message.content.length) {
    const block = message.content[index]
    if (isToolUseBlock(block)) {
      toolUseBlock = block
      toolIndex = index
    }
  }

  // 如果通过 index 找不到，尝试查找最后一个 tool_use 块
  if (!toolUseBlock) {
    for (let i = message.content.length - 1; i >= 0; i--) {
      const block = message.content[i]
      if (isToolUseBlock(block)) {
        toolUseBlock = block
        toolIndex = i
        break
      }
    }
  }

  if (!toolUseBlock || toolIndex === -1) {
    // 如果仍然找不到，说明 content_block_start 事件可能还未处理
    // 这种情况下，我们无法更新，返回 false
    return false
  }

  // 累积 partial_json
  const accumulatorKey = `tool_input_${toolUseBlock.id}`
  const accumulatedJson = (accumulator.get(accumulatorKey) || '') + delta.partial_json
  accumulator.set(accumulatorKey, accumulatedJson)

  // 尝试解析累积的 JSON
  try {
    const parsed = JSON.parse(accumulatedJson)
    
    // 更新工具调用块的 input
    const updatedBlock: ToolUseBlock = {
      ...toolUseBlock,
      input: parsed
    }
    
    const newContent = [...message.content]
    newContent[toolIndex] = updatedBlock
    message.content = newContent
    
    return true
  } catch (parseError) {
    // JSON 可能还不完整，暂时不更新
    // 但保留累积的字符串，等待更多增量
    return false
  }
}

/**
 * 处理 Thinking 增量更新
 * 
 * @param message 目标消息
 * @param index 内容块索引
 * @param delta Thinking 增量
 * @returns 是否成功更新
 */
export function applyThinkingDelta(
  message: Message,
  index: number,
  delta: ThinkingDelta
): boolean {
  if (!delta.delta) {
    return false
  }

  // Thinking 块的处理方式与文本块类似
  // 但类型是 'thinking' 而不是 'text'
  const existingBlock = message.content[index] as ThinkingBlock | undefined

  if (existingBlock && existingBlock.type === 'thinking') {
    // 追加到现有 thinking 块
    const updatedBlock: ThinkingBlock = {
      ...existingBlock,
      thinking: existingBlock.thinking + delta.delta
    }
    const newContent = [...message.content]
    newContent[index] = updatedBlock
    message.content = newContent
    return true
  } else {
    // 创建新的 thinking 块
    const newBlock: ThinkingBlock = {
      type: 'thinking',
      thinking: delta.delta,
      signature: undefined // 将在 content_block_stop 时设置
    }
    
    if (index >= message.content.length) {
      message.content = [...message.content, newBlock]
    } else {
      const newContent = [...message.content]
      newContent[index] = newBlock
      message.content = newContent
    }
    return true
  }
}

/**
 * 创建占位符消息
 */
export function createPlaceholderMessage(): Message {
  return {
    id: `assistant-placeholder-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`,
    role: 'assistant',
    content: [],
    timestamp: Date.now()
  }
}

/**
 * 查找或创建最后一个 assistant 消息
 * 
 * ⚠️ 注意：此函数会修改 messages 数组（如果不存在则添加占位符）
 * 调用者需要确保 messages 是响应式的，以便 Vue 能够检测到变化
 */
export function findOrCreateLastAssistantMessage(messages: Message[]): Message {
  const lastMessage = messages
    .slice()
    .reverse()
    .find(m => m.role === 'assistant')

  if (lastMessage) {
    return lastMessage
  }

  // 创建新的占位符消息并添加到数组
  // 注意：这里直接修改数组是为了保持响应式
  // 因为 messages 应该是 Vue 的响应式数组
  const placeholder = createPlaceholderMessage()
  messages.push(placeholder)
  return placeholder
}

