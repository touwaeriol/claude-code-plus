import type { Message, ContentBlock, TextBlock, ToolResultBlock } from '@/types/message'
import type { WebSocketResponse } from './websocketClient'
import {
  isUserMessage,
  isAssistantMessage,
  isSystemMessage,
  isErrorMessage,
  isSystemMessageSent,
  isStreamEvent,
  isResultMessage
} from '@/utils/typeGuards'

let messageCounter = 0

function nextMessageId(prefix: string): string {
  messageCounter += 1
  return `${prefix}-${Date.now()}-${messageCounter}`
}

/**
 * 将原始内容转换为 ContentBlock 数组
 *
 * 注意：此函数是纯函数，不会产生副作用
 * 工具调用的注册应由调用方（如 sessionStore）处理
 */
function mapAssistantContent(rawContent: any): ContentBlock[] {
  if (!rawContent) {
    return []
  }

  // 已经是内容块数组
  if (Array.isArray(rawContent)) {
    return rawContent as ContentBlock[]
  }

  // 单纯字符串
  if (typeof rawContent === 'string') {
    return [{ type: 'text', text: rawContent }]
  }

  // Anthropic 风格 content 数组
  if (rawContent.content && Array.isArray(rawContent.content)) {
    return rawContent.content as ContentBlock[]
  }

  return []
}

/**
 * 提取用户消息内容
 *
 * 注意：此函数是纯函数，不会产生副作用
 * 工具结果的处理应由调用方（如 sessionStore）处理
 */
function extractUserContent(rawContent: any): ContentBlock[] {
  if (!rawContent) return []

  // 如果是数组
  if (Array.isArray(rawContent)) {
    const blocks: ContentBlock[] = []
    for (const el of rawContent) {
      if (typeof el === 'string') {
        blocks.push({ type: 'text', text: el })
      } else if (el && typeof el === 'object') {
        const type = el.type
        if (type === 'text' && typeof el.text === 'string') {
          blocks.push(el as TextBlock)
        } else if (type === 'tool_result') {
          blocks.push(el as ToolResultBlock)
        }
      }
    }
    return blocks
  }

  // 单纯字符串
  if (typeof rawContent === 'string') {
    return [{ type: 'text', text: rawContent }]
  }

  // 对象格式 { content: [...] }
  if (rawContent && typeof rawContent === 'object' && rawContent.content) {
    return extractUserContent(rawContent.content)
  }

  return []
}

/**
 * 将 WebSocket 响应映射为 UI 消息
 *
 * 使用类型守卫实现类型安全的消息转换
 */
export function mapWebSocketResponseToMessages(response: WebSocketResponse): Message[] {
  // ✅ 类型安全：AI 助手消息
  if (isAssistantMessage(response)) {
    // response.message 现在是 { content: ContentBlock[], model: string, isStreaming: boolean }
    const contentBlocks = mapAssistantContent(response.message.content)
    if (contentBlocks.length === 0) return []

    const msg: Message = {
      id: nextMessageId('assistant'),
      role: 'assistant',
      content: contentBlocks,
      timestamp: Date.now()
    }
    return [msg]
  }

  // ✅ 类型安全：用户消息
  if (isUserMessage(response)) {
    // response.message 现在是 { content: string | ContentBlock[] }
    const contentBlocks = extractUserContent(response.message.content)
    if (contentBlocks.length === 0) return []

    const msg: Message = {
      id: nextMessageId('user'),
      role: 'user',
      content: contentBlocks,
      timestamp: Date.now()
    }
    return [msg]
  }

  // ✅ 类型安全：系统消息
  if (isSystemMessage(response)) {
    // response.message 现在是 { subtype: string, data?: any, message?: string }

    // 忽略纯确认类系统消息（例如 "消息已发送"）
    if (isSystemMessageSent(response)) {
      return []
    }

    const text = typeof response.message.message === 'string' ? response.message.message : ''
    if (!text) return []

    const block: TextBlock = { type: 'text', text }
    const msg: Message = {
      id: nextMessageId('system'),
      role: 'system',
      content: [block],
      timestamp: Date.now()
    }
    return [msg]
  }

  // ✅ 类型安全：错误消息
  if (isErrorMessage(response)) {
    // response.message 现在是 { error: string }
    const text = response.message.error
    const block: TextBlock = { type: 'text', text: `错误: ${text}` }
    const msg: Message = {
      id: nextMessageId('system'),
      role: 'system',
      content: [block],
      timestamp: Date.now()
    }
    return [msg]
  }

  // 流式事件：不在这里处理，由 sessionStore.handleStreamEvent 处理
  if (isStreamEvent(response)) {
    return []
  }

  // ✅ 处理 result 消息中的错误
  if (isResultMessage(response)) {
    // 检查是否有错误
    if (response.message.is_error && response.message.result) {
      const block: TextBlock = {
        type: 'text',
        text: response.message.result
      }
      const msg: Message = {
        id: nextMessageId('system'),
        role: 'system',
        content: [block],
        timestamp: Date.now()
      }
      return [msg]
    }
    // 非错误的 result 消息仍然不显示
    return []
  }

  // 其他未处理的消息类型
  return []
}
