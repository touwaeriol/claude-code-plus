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
  ContextReference,
  CompactSummary,
  LocalCommandOutput
} from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { resolveToolType } from '@/constants/toolTypes'
import type {
  ContentBlock,
  Message,
  TextContent,
  ThinkingContent as MessageThinkingContent,
  ToolUseContent,
  ToolResultContent,
  UnifiedMessage
} from '@/types/message'

/**
 * 压缩摘要消息类型守卫
 * 当 isCompactSummary = true 时，表示这是压缩后的上下文摘要
 */
function isCompactSummaryMessage(message: Message): message is UnifiedMessage & { isCompactSummary: true } {
  return message.role === 'user' && (message as UnifiedMessage).isCompactSummary === true
}

/**
 * 压缩确认消息类型守卫
 * 当 isReplay = true 时，表示这是压缩确认消息（如 "Compacted"）
 */
function isReplayMessage(message: Message): message is UnifiedMessage & { isReplay: true } {
  return message.role === 'user' && (message as UnifiedMessage).isReplay === true
}
import type { ThinkingContent as DisplayThinkingContent } from '@/types/display'
import { isToolUseBlock, isTextBlock } from '@/utils/contentBlockUtils'
import { parseUserMessage } from '@/utils/userMessageBuilder'
import { parseLocalCommandTags } from '@/utils/xmlTagParser'

/**
 * 粗略判断文本块是否是工具输入参数的“原样 dump”
 * - 同一消息里包含 tool_use 时才检查
 * - 以大量花括号/引号/冒号为主，且包含常见字段名（todos/status/file_path 等）
 */
function _isLikelyToolInputText(text: string, content: ContentBlock[]): boolean {
  const hasToolUse = content.some(isToolUseBlock)
  if (!hasToolUse) return false

  const trimmed = text.trim()
  if (trimmed.length < 10) return false

  const structuralChars = (trimmed.match(new RegExp('[{}\\[\\]"“”:,]', 'g')) || []).length
  const structuralRatio = structuralChars / trimmed.length
  const containsField =
    /todos|status|activeForm|file_path|path|tool_use_id|content/i.test(trimmed)

  return structuralRatio > 0.15 && containsField
}

/** 从文本中提取 JSON（用于将模型吐出的参数字符串还原为对象） */
function extractJsonFromText(text: string): any | null {
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start === -1 || end === -1 || end <= start) return null
  const slice = text.slice(start, end + 1)
  try {
    return JSON.parse(slice)
  } catch {
    return null
  }
}

/**
 * 计算哪些文本块应视为工具入参回显，并尝试把 JSON 入参填回 tool_use
 */
function computeToolTextSkipAndHydrate(message: Message): Set<number> {
  const skip = new Set<number>()
  if (message.role !== 'assistant') return skip

  const toolUses = (message.content as ContentBlock[]).filter(isToolUseBlock) as ToolUseContent[]
  if (toolUses.length === 0) return skip

  ;(message.content as ContentBlock[]).forEach((block, idx) => {
    if (!isTextBlock(block)) return
    const text = (block as TextContent).text
    const jsonObj = extractJsonFromText(text)
    // 只跳过纯 JSON dump（能成功解析且 tool_use 没有 input 的情况）
    if (jsonObj && toolUses.some(t => !t.input || Object.keys(t.input as any).length === 0)) {
      toolUses.forEach(t => {
        if (!t.input || Object.keys(t.input as any).length === 0) {
          t.input = jsonObj as any
        }
      })
      skip.add(idx)
    }
    // 移除 isLikelyToolInputText 检查：太宽泛，会误伤正常的助手文本
  })

  return skip
}

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

  // 优先使用后端传来的 toolType，否则通过 toolName 解析
  const toolType = block.toolType || resolveToolType(block.toolName)

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
  if (block.toolName === 'Task') {
    (toolCall as any).agentName = (block as any).input?.subagent_type || (block as any).input?.model
  }
  pendingToolCalls.set(block.id, toolCall)
  return toolCall
}

export function updateToolCallResult(toolCall: ToolCall, resultBlock: ToolResultContent) {
  toolCall.status = resultBlock.is_error ? ToolCallStatus.FAILED : ToolCallStatus.SUCCESS
  toolCall.endTime = Date.now()
  // 直接存储后端数据，不做格式转换，保留 is_error 字段
  toolCall.result = resultBlock as ToolResult

  // 从结构化字段读取 agentId（仅 Task 工具有）
  if (toolCall.toolName === 'Task' && (resultBlock as any).agent_id) {
    (toolCall as any).agentId = (resultBlock as any).agent_id
  }
}

export function convertMessageToDisplayItems(
  message: Message,
  pendingToolCalls: Map<string, ToolCall>
): DisplayItem[] {
  const displayItems: DisplayItem[] = []

  if (message.role === 'user') {
    // 检查是否是压缩摘要消息（使用类型守卫）
    if (isCompactSummaryMessage(message)) {
      const compactSummary: CompactSummary = {
        displayType: 'compactSummary',
        id: message.id,
        content: message.content,
        isReplay: message.isReplay,
        preTokens: (message as any).compactMetadata?.preTokens,
        trigger: (message as any).compactMetadata?.trigger,
        timestamp: message.timestamp
      }
      displayItems.push(compactSummary)
      return displayItems
    }

    // 处理回放消息（isReplay = true）
    // 注意：即使是 replay 消息，如果包含 tool_result，也需要处理它
    if (isReplayMessage(message)) {
      // 先处理 tool_result（关联到对应的 tool_use 卡片）
      const toolResults = (message.content as ContentBlock[]).filter(b => b.type === 'tool_result') as ToolResultContent[]
      for (const resultBlock of toolResults) {
        const toolCall = pendingToolCalls.get(resultBlock.tool_use_id)
        if (toolCall) {
          updateToolCallResult(toolCall, resultBlock)
        }
      }

      // 如果只有 tool_result，不需要显示为用户消息
      const hasOnlyToolResult = message.content.every(b => b.type === 'tool_result' || b.type === 'tool_use')
      if (hasOnlyToolResult && toolResults.length > 0) {
        return displayItems
      }

      const textBlock = message.content.find(b => b.type === 'text') as { type: 'text', text: string } | undefined
      if (textBlock?.text) {
        // 尝试解析 XML 标签（本地命令输出）
        const parsed = parseLocalCommandTags(textBlock.text)
        if (parsed) {
          const localCommandOutput: LocalCommandOutput = {
            displayType: 'localCommandOutput',
            id: message.id,
            command: parsed.content,
            outputType: parsed.type,
            timestamp: message.timestamp
          }
          displayItems.push(localCommandOutput)
          return displayItems
        }
        // 没有本地命令标签，作为普通回放消息显示（左对齐，markdown 渲染，次要颜色）
        const userMessage: UserMessage = {
          displayType: 'userMessage',
          id: message.id,
          uuid: (message as UnifiedMessage).uuid,
          content: [{ type: 'text', text: textBlock.text }] as any,
          timestamp: message.timestamp,
          isReplay: true,
          style: 'hint'
        }
        displayItems.push(userMessage)
        return displayItems
      }
      return displayItems
    }

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
        uuid: (message as UnifiedMessage).uuid,
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
    const consumedTextIndices = computeToolTextSkipAndHydrate(message)

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
        if (consumedTextIndices.has(blockIdx)) {
          continue
        }

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
        if (thinkingBlock.thinking !== undefined) {
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
      // 检查是否是压缩摘要消息（使用类型守卫）
      if (isCompactSummaryMessage(message)) {
        const compactSummary: CompactSummary = {
          displayType: 'compactSummary',
          id: message.id,
          content: message.content,
          isReplay: message.isReplay,
          preTokens: (message as any).compactMetadata?.preTokens,
          trigger: (message as any).compactMetadata?.trigger,
          timestamp: message.timestamp
        }
        displayItems.push(compactSummary)
        continue
      }

      // 处理回放消息（isReplay = true）
      // 注意：即使是 replay 消息，如果包含 tool_result，也需要处理它
      if (isReplayMessage(message)) {
        // 先处理 tool_result（关联到对应的 tool_use 卡片）
        const toolResults = (message.content as ContentBlock[]).filter(b => b.type === 'tool_result') as ToolResultContent[]
        for (const resultBlock of toolResults) {
          const toolCall = pendingToolCalls.get(resultBlock.tool_use_id)
          if (toolCall) {
            updateToolCallResult(toolCall, resultBlock)
          }
        }

        // 如果只有 tool_result，不需要显示为用户消息
        const hasOnlyToolResult = message.content.every(b => b.type === 'tool_result' || b.type === 'tool_use')
        if (hasOnlyToolResult && toolResults.length > 0) {
          continue
        }

        const textBlock = message.content.find(b => b.type === 'text') as { type: 'text', text: string } | undefined
        if (textBlock?.text) {
          // 尝试解析 XML 标签（本地命令输出）
          const parsed = parseLocalCommandTags(textBlock.text)
          if (parsed) {
            const localCommandOutput: LocalCommandOutput = {
              displayType: 'localCommandOutput',
              id: message.id,
              command: parsed.content,
              outputType: parsed.type,
              timestamp: message.timestamp
            }
            displayItems.push(localCommandOutput)
            continue
          }
          // 没有本地命令标签，作为普通回放消息显示（左对齐，markdown 渲染，次要颜色）
          const userMessage: UserMessage = {
            displayType: 'userMessage',
            id: message.id,
            content: [{ type: 'text', text: textBlock.text }] as any,
            timestamp: message.timestamp,
            isReplay: true,
            style: 'hint'
          }
          displayItems.push(userMessage)
        }
        continue
      }

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
      const consumedTextIndices = computeToolTextSkipAndHydrate(message)
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
          if (consumedTextIndices.has(blockIdx)) continue
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
          if (thinkingBlock.thinking !== undefined) {
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
