import type { Message, ContentBlock, TextBlock } from '@/types/message'
import type { WebSocketResponse } from './websocketClient'
import {
  isUserMessage,
  isAssistantMessage,
  isSystemMessage,
  isErrorMessage,
  isSystemMessageSent
} from '@/utils/typeGuards'

let messageCounter = 0

function nextMessageId(prefix: string): string {
  messageCounter += 1
  return `${prefix}-${Date.now()}-${messageCounter}`
}

function mapAssistantContent(rawContent: any): ContentBlock[] {
  if (!rawContent) return []

  // 已经是内容块数组
  if (Array.isArray(rawContent)) {
    return rawContent as ContentBlock[]
  }

  // 单纯字符串
  if (typeof rawContent === 'string') {
    const block: TextBlock = { type: 'text', text: rawContent }
    return [block]
  }

  // Anthropic 风格 content 数组
  if (Array.isArray((rawContent as any).content)) {
    return (rawContent as any).content as ContentBlock[]
  }

  return []
}

function extractUserText(rawContent: any): string {
  if (!rawContent) return ''

  if (typeof rawContent === 'string') return rawContent

  // UserMessage.content 也可能是数组或对象
  if (Array.isArray(rawContent)) {
    const parts: string[] = []
    rawContent.forEach((el) => {
      if (typeof el === 'string') {
        parts.push(el)
      } else if (el && typeof el === 'object') {
        const type = (el as any).type
        if (type === 'text' && typeof (el as any).text === 'string') {
          parts.push((el as any).text)
        }
      }
    })
    return parts.join('\n')
  }

  if (rawContent && typeof rawContent === 'object') {
    // 兼容 { content: [...] }
    const inner = (rawContent as any).content
    return extractUserText(inner)
  }

  return ''
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
    // response.message 现在是 { content: string }
    const text = extractUserText(response.message.content)
    if (!text) return []

    const block: TextBlock = { type: 'text', text }
    const msg: Message = {
      id: nextMessageId('user'),
      role: 'user',
      content: [block],
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

  // result / stream_event 暂时不直接映射到可见消息
  return []
}
