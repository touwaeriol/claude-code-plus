/**
 * DisplayItem 转换（基于统一内容块）
 *
 * 字段命名规范：
 * - displayType: DisplayItem 的种类（如 'toolCall', 'userMessage'）
 * - toolName: 工具显示名称（如 'Read', 'mcp__excel__read'）
 * - toolType: 工具类型标识（如 'CLAUDE_READ', 'MCP'）
 */
import { reactive } from 'vue'
import type {
  DisplayItem,
  UserMessage,
  AssistantText,
  SystemMessage,
  ToolCall,
  ToolResult,
  RequestStats,
  ContextReference
} from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { resolveToolType } from '@/constants/toolTypes'
import type {
  ContentBlock,
  Message,
  TextContent,
  ThinkingContent as MessageThinkingContent,
  ToolUseContent,
  ToolResultContent
} from '@/types/message'
import type { ThinkingContent as DisplayThinkingContent } from '@/types/display'
import { isToolUseBlock, isTextBlock } from '@/utils/contentBlockUtils'
import { parseUserMessage } from '@/utils/userMessageBuilder'

export function createToolCall(
  block: ToolUseContent,
  pendingToolCalls: Map<string, ToolCall>
): ToolCall {
  const existing = pendingToolCalls.get(block.id)
  if (existing) {
    if (block.input && Object.keys(block.input as any).length > 0) {
      existing.input = block.input as any
    }
    return existing
  }

  // 🔧 调试日志
  console.log('📦 [createToolCall] block:', { id: block.id, toolName: block.toolName, toolType: block.toolType, type: block.type })

  // 优先使用后端传来的 toolType，否则通过 toolName 解析
  const toolType = block.toolType || resolveToolType(block.toolName)
  console.log('📦 [createToolCall] resolved toolType:', { toolName: block.toolName, blockToolType: block.toolType, resolvedToolType: toolType })

  const timestamp = Date.now()

  const baseToolCall = {
    id: block.id,
    displayType: 'toolCall' as const,
    toolName: block.toolName,

    toolType,                           // 类型标识（CLAUDE_READ 等）
    status: ToolCallStatus.RUNNING,
    startTime: timestamp,
    timestamp,
    input: (block.input || {}) as Record<string, any>
  }

  const toolCall = reactive({ ...baseToolCall }) as ToolCall
  pendingToolCalls.set(block.id, toolCall)
  return toolCall
}

export function updateToolCallResult(toolCall: ToolCall, resultBlock: ToolResultContent) {
  toolCall.status = resultBlock.is_error ? ToolCallStatus.FAILED : ToolCallStatus.SUCCESS
  toolCall.endTime = Date.now()
  // 直接存储后端数据，不做格式转换，保留 is_error 字段
  toolCall.result = resultBlock as ToolResult
}

export function convertMessageToDisplayItems(
  message: Message,
  pendingToolCalls: Map<string, ToolCall>
): DisplayItem[] {
  const displayItems: DisplayItem[] = []

  if (message.role === 'user') {
    const parsed = parseUserMessage(message.content as any)
    const contexts: ContextReference[] = [...parsed.contexts]
    for (const imgBlock of parsed.contextImages) {
      if (imgBlock.source.type === 'base64') {
        contexts.push({
          type: 'image',
          uri: `image://context-${message.id}-${contexts.length}`,
          displayType: 'TAG',
          mimeType: imgBlock.source.media_type,
          base64Data: imgBlock.source.data
        })
      }
    }
    if (parsed.userContent.length > 0 || contexts.length > 0) {
      const userMessage: UserMessage = {
        displayType: 'userMessage',
        id: message.id,
        contexts: contexts.length > 0 ? contexts : undefined,
        content: parsed.userContent as any,
        timestamp: message.timestamp
      }
      displayItems.push(userMessage)
    }

    const toolResults = (message.content as ContentBlock[]).filter(b => b.type === 'tool_result') as ToolResultContent[]
    for (const resultBlock of toolResults) {
      const toolCall = pendingToolCalls.get(resultBlock.tool_use_id)
      if (toolCall) {
        updateToolCallResult(toolCall, resultBlock)
      }
    }
  } else if (message.role === 'assistant') {
    const textBlockIndices: number[] = []
    ;(message.content as ContentBlock[]).forEach((block, idx) => {
      if (isTextBlock(block) && (block as TextContent).text.trim()) {
        textBlockIndices.push(idx)
      }
    })
    const lastTextBlockIndex = textBlockIndices.length > 0 ? textBlockIndices[textBlockIndices.length - 1] : -1

    for (let blockIdx = 0; blockIdx < message.content.length; blockIdx++) {
      const block = message.content[blockIdx]

      if (isTextBlock(block) && (block as TextContent).text.trim()) {
        const isLastTextBlock = blockIdx === lastTextBlockIndex
        let stats = undefined
        if (isLastTextBlock && (message as any).tokenUsage) {
          const usage = (message as any).tokenUsage
          stats = {
            requestDuration: 0,
            inputTokens: usage.inputTokens || usage.input_tokens,
            outputTokens: usage.outputTokens || usage.output_tokens
          } as RequestStats
        }

        const assistantText: AssistantText = {
          displayType: 'assistantText',
          id: `${message.id}-text-${blockIdx}`,
          content: (block as TextContent).text,
          timestamp: message.timestamp,
          isLastInMessage: isLastTextBlock,
          stats,
          isStreaming: (message as any).isStreaming === true
        }
        displayItems.push(assistantText)
      } else if (block.type === 'thinking') {
        // 处理 thinking 块（消息格式使用 thinking 字段，显示格式使用 content 字段）
        const thinkingBlock = block as MessageThinkingContent
        if (thinkingBlock.thinking && thinkingBlock.thinking.trim()) {
          const thinkingContent: DisplayThinkingContent = {
            displayType: 'thinking',
            id: `${message.id}-thinking-${blockIdx}`,
            content: thinkingBlock.thinking,  // 消息格式 .thinking → 显示格式 .content
            signature: thinkingBlock.signature,
            timestamp: message.timestamp
          }
          displayItems.push(thinkingContent)
        }
      } else if (isToolUseBlock(block)) {
        const toolCall = createToolCall(block as ToolUseContent, pendingToolCalls)
        displayItems.push(toolCall)
      }
    }
  } else if (message.role === 'system') {
    const textBlocks = (message.content as ContentBlock[]).filter(b => b.type === 'text') as TextContent[]
    if (textBlocks.length > 0) {
      const systemMessage: SystemMessage = {
        displayType: 'systemMessage',
        id: message.id,
        content: textBlocks.map(b => b.text).join('\n'),
        level: 'info',
        timestamp: message.timestamp
      }
      displayItems.push(systemMessage)
    }
  }

  return displayItems
}

export function convertToDisplayItems(
  messages: Message[],
  pendingToolCalls: Map<string, ToolCall>
): DisplayItem[] {
  const displayItems: DisplayItem[] = []

  for (const message of messages) {
    if (message.role === 'user') {
      const parsed = parseUserMessage(message.content as any)
      const contexts: ContextReference[] = [
        ...parsed.contexts,
        ...parsed.contextImages.map(img => ({
          type: 'image' as const,
          uri: `image://context`,
          displayType: 'TAG' as const,
          mimeType: img.source.media_type,
          base64Data: img.source.type === 'base64' ? img.source.data : undefined
        }))
      ]
      const content = parsed.userContent

      const userMessage: UserMessage = {
        displayType: 'userMessage',
        id: message.id,
        contexts: contexts.length > 0 ? contexts : undefined,
        content: content.length > 0 ? (content as any) : [],
        timestamp: message.timestamp
      }
      displayItems.push(userMessage)

      const toolResults = (message.content as ContentBlock[]).filter(b => b.type === 'tool_result') as ToolResultContent[]
      for (const resultBlock of toolResults) {
        const toolCall = pendingToolCalls.get(resultBlock.tool_use_id)
        if (toolCall) {
          updateToolCallResult(toolCall, resultBlock)
        }
      }
    } else if (message.role === 'assistant') {
      const textBlockIndices: number[] = []
      ;(message.content as ContentBlock[]).forEach((block, idx) => {
        if (isTextBlock(block) && (block as TextContent).text.trim()) {
          textBlockIndices.push(idx)
        }
      })
      const lastTextBlockIndex = textBlockIndices.length > 0 ? textBlockIndices[textBlockIndices.length - 1] : -1

      for (let blockIdx = 0; blockIdx < message.content.length; blockIdx++) {
        const block = message.content[blockIdx]

        if (isTextBlock(block) && (block as TextContent).text.trim()) {
          const isLastTextBlock = blockIdx === lastTextBlockIndex

          let stats: RequestStats | undefined
          const usage = (message as any).tokenUsage
          if (isLastTextBlock && usage) {
            let lastUserTimestamp = 0
            for (let i = messages.indexOf(message) - 1; i >= 0; i--) {
              if (messages[i].role === 'user') {
                lastUserTimestamp = messages[i].timestamp
                break
              }
            }
            const requestDuration = lastUserTimestamp > 0 ? message.timestamp - lastUserTimestamp : 0
            stats = {
              requestDuration,
              inputTokens: usage.inputTokens || usage.input_tokens,
              outputTokens: usage.outputTokens || usage.output_tokens
            }
          }

        const assistantText: AssistantText = {
          displayType: 'assistantText',
          id: `${message.id}-text-${displayItems.length}`,
          content: (block as TextContent).text,
          timestamp: message.timestamp,
          isLastInMessage: isLastTextBlock,
          stats,
          isStreaming: (message as any).isStreaming === true
        }
          displayItems.push(assistantText)
        } else if (block.type === 'thinking') {
          // 处理 thinking 块（消息格式使用 thinking 字段，显示格式使用 content 字段）
          const thinkingBlock = block as MessageThinkingContent
          if (thinkingBlock.thinking && thinkingBlock.thinking.trim()) {
            const thinkingContent: DisplayThinkingContent = {
              displayType: 'thinking',
              id: `${message.id}-thinking-${blockIdx}`,
              content: thinkingBlock.thinking,  // 消息格式 .thinking → 显示格式 .content
              signature: thinkingBlock.signature,
              timestamp: message.timestamp
            }
            displayItems.push(thinkingContent)
          }
        } else if (isToolUseBlock(block)) {
          const toolCall = createToolCall(block as ToolUseContent, pendingToolCalls)
          displayItems.push(toolCall)
        }
      }
    } else if (message.role === 'system') {
      const textBlocks = (message.content as ContentBlock[]).filter(b => b.type === 'text') as TextContent[]
      if (textBlocks.length > 0) {
        const systemMessage: SystemMessage = {
          displayType: 'systemMessage',
          id: message.id,
          content: textBlocks.map(b => b.text).join('\n'),
          level: 'info',
          timestamp: message.timestamp
        }
        displayItems.push(systemMessage)
      }
    }
  }

  return displayItems
}
