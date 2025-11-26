/**
 * 消息转换工具
 * 
 * 将后端的 Message 转换为前端的 DisplayItem
 */

import { reactive } from 'vue'
import type { Message, TextBlock, ToolUseBlock, ToolResultBlock, ImageBlock, ContentBlock } from '@/types/message'
import type {
  DisplayItem,
  UserMessage,
  AssistantText,
  SystemMessage,
  ToolCall,
  ToolResult,
  RequestStats,
  ContextReference,
  ReadToolCall,
  WriteToolCall,
  EditToolCall,
  MultiEditToolCall,
  TodoWriteToolCall,
  BashToolCall,
  GrepToolCall,
  GlobToolCall,
  WebSearchToolCall,
  WebFetchToolCall,
  GenericToolCall
} from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { TOOL_TYPE } from '@/constants/toolTypes'
import { isToolUseBlock, isTextBlock } from '@/utils/contentBlockUtils'
import { parseUserMessage } from '@/utils/userMessageBuilder'

/**
 * 工具名称到类型的映射
 */
const TOOL_NAME_TO_TYPE: Record<string, string> = {
  'Read': TOOL_TYPE.READ,
  'Write': TOOL_TYPE.WRITE,
  'Edit': TOOL_TYPE.EDIT,
  'MultiEdit': TOOL_TYPE.MULTI_EDIT,
  'TodoWrite': TOOL_TYPE.TODO_WRITE,
  'Bash': TOOL_TYPE.BASH,
  'Grep': TOOL_TYPE.GREP,
  'Glob': TOOL_TYPE.GLOB,
  'WebSearch': TOOL_TYPE.WEB_SEARCH,
  'WebFetch': TOOL_TYPE.WEB_FETCH
}

/**
 * 从 ToolUseBlock 创建 ToolCall
 * 
 * @param block 工具使用块
 * @param pendingToolCalls 待处理的工具调用 Map（用于查找已存在的工具调用）
 * @returns 响应式的 ToolCall 对象
 */
export function createToolCall(
  block: ToolUseBlock,
  pendingToolCalls: Map<string, ToolCall>
): ToolCall {
  // 检查是否已存在（用于更新状态）
  const existing = pendingToolCalls.get(block.id)
  if (existing) {
    // 🔧 关键修复：同步更新已存在对象的 input
    // 因为 stream event 中 input_json_delta 会逐步更新 block.input
    // 但 pendingToolCalls 中的对象不会自动同步
    if (block.input && Object.keys(block.input).length > 0) {
      existing.input = block.input
    }
    return existing
  }

  const toolType = TOOL_NAME_TO_TYPE[block.name] || block.name
  const timestamp = Date.now()

  // 创建基础工具调用对象
  const baseToolCall = {
    id: block.id,
    type: 'toolCall' as const,
    toolType,
    status: ToolCallStatus.RUNNING,
    startTime: timestamp,
    timestamp,
    input: block.input
  }

  // 根据工具类型创建具体的 ToolCall
  let toolCall: ToolCall

  switch (toolType) {
    case TOOL_TYPE.READ:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.READ } as ReadToolCall
      break
    case TOOL_TYPE.WRITE:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.WRITE } as WriteToolCall
      break
    case TOOL_TYPE.EDIT:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.EDIT } as EditToolCall
      break
    case TOOL_TYPE.MULTI_EDIT:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.MULTI_EDIT } as MultiEditToolCall
      break
    case TOOL_TYPE.TODO_WRITE:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.TODO_WRITE } as TodoWriteToolCall
      break
    case TOOL_TYPE.BASH:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.BASH } as BashToolCall
      break
    case TOOL_TYPE.GREP:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.GREP } as GrepToolCall
      break
    case TOOL_TYPE.GLOB:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.GLOB } as GlobToolCall
      break
    case TOOL_TYPE.WEB_SEARCH:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.WEB_SEARCH } as WebSearchToolCall
      break
    case TOOL_TYPE.WEB_FETCH:
      toolCall = { ...baseToolCall, toolType: TOOL_TYPE.WEB_FETCH } as WebFetchToolCall
      break
    default:
      toolCall = { ...baseToolCall, toolType } as GenericToolCall
  }

  // 使用 reactive 包装，使其响应式
  const reactiveToolCall = reactive(toolCall) as ToolCall

  // 添加到 pendingToolCalls
  pendingToolCalls.set(block.id, reactiveToolCall)

  return reactiveToolCall
}

/**
 * 更新工具调用结果
 *
 * @param toolCall 工具调用对象
 * @param resultBlock 工具结果块
 */
export function updateToolCallResult(toolCall: ToolCall, resultBlock: ToolResultBlock) {
  // 更新状态
  toolCall.status = resultBlock.is_error ? ToolCallStatus.FAILED : ToolCallStatus.SUCCESS
  toolCall.endTime = Date.now()

  // 解析结果
  const result: ToolResult = resultBlock.is_error
    ? {
        type: 'error',
        error: typeof resultBlock.content === 'string' ? resultBlock.content : JSON.stringify(resultBlock.content)
      }
    : {
        type: 'success',
        output: typeof resultBlock.content === 'string' ? resultBlock.content : JSON.stringify(resultBlock.content)
      }

  toolCall.result = result
}

/**
 * 将单个 Message 转换为 DisplayItem 数组（增量更新用）
 *
 * @param message 单个消息
 * @param pendingToolCalls 待处理的工具调用 Map
 * @returns DisplayItem 数组
 */
export function convertMessageToDisplayItems(
  message: Message,
  pendingToolCalls: Map<string, ToolCall>
): DisplayItem[] {
  const displayItems: DisplayItem[] = []

  if (message.role === 'user') {
    // 用户消息：使用 parseUserMessage 解析上下文和用户输入
    const parsed = parseUserMessage(message.content)
    
    // 构建上下文引用（文件引用 + Context 图片）
    const contexts: ContextReference[] = [...parsed.contexts]
    
    // 将 Context 图片转换为 ContextReference
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
    
    // 构建用户消息（content 是 ContentBlock[]，包含用户输入的文本和图片）
    if (parsed.userContent.length > 0 || contexts.length > 0) {
      const userMessage: UserMessage = {
        type: 'userMessage',
        id: message.id,
        contexts: contexts.length > 0 ? contexts : undefined,
        content: parsed.userContent, // ContentBlock[]，保持原始顺序
        timestamp: message.timestamp
      }
      displayItems.push(userMessage)
    }

    // 处理 tool_result（更新工具调用状态）
    const toolResults = message.content.filter(b => b.type === 'tool_result') as ToolResultBlock[]
    for (const resultBlock of toolResults) {
      const toolCall = pendingToolCalls.get(resultBlock.tool_use_id)
      if (toolCall) {
        updateToolCallResult(toolCall, resultBlock)
      }
    }
  } else if (message.role === 'assistant') {
    // AI 助手消息
    const textBlockIndices: number[] = []
    message.content.forEach((block, idx) => {
      if (isTextBlock(block) && block.text.trim()) {
        textBlockIndices.push(idx)
      }
    })
    const lastTextBlockIndex = textBlockIndices.length > 0 ? textBlockIndices[textBlockIndices.length - 1] : -1

    for (let blockIdx = 0; blockIdx < message.content.length; blockIdx++) {
      const block = message.content[blockIdx]

      if (isTextBlock(block) && block.text.trim()) {
        const isLastTextBlock = blockIdx === lastTextBlockIndex
        let stats = undefined
        if (isLastTextBlock && message.tokenUsage) {
          stats = {
            requestDuration: 0,
            inputTokens: message.tokenUsage.input_tokens,
            outputTokens: message.tokenUsage.output_tokens
          }
        }

        const assistantText = {
          type: 'assistantText' as const,
          id: `${message.id}-text-${blockIdx}`,
          content: block.text,
          timestamp: message.timestamp,
          isLastInMessage: isLastTextBlock,
          stats
        }
        displayItems.push(assistantText)
      } else if (isToolUseBlock(block)) {
        const toolCall = createToolCall(block, pendingToolCalls)
        displayItems.push(toolCall)
      }
    }
  } else if (message.role === 'system') {
    // 系统消息
    const textBlocks = message.content.filter(b => b.type === 'text') as TextBlock[]
    if (textBlocks.length > 0) {
      const systemMessage: SystemMessage = {
        type: 'systemMessage',
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

/**
 * 将 Message 数组转换为 DisplayItem 数组（初始化用）
 *
 * @param messages 原始消息数组
 * @param pendingToolCalls 待处理的工具调用 Map
 * @returns DisplayItem 数组
 */
export function convertToDisplayItems(
  messages: Message[],
  pendingToolCalls: Map<string, ToolCall>
): DisplayItem[] {
  const displayItems: DisplayItem[] = []

  for (const message of messages) {
    if (message.role === 'user') {
    // 用户消息 - 解析 contexts 和 content
    // 过滤掉 tool_use 和 tool_result（这些会单独处理）
    const userContentBlocks = message.content.filter(
      block => block.type === 'text' || block.type === 'image'
    )
    
    if (userContentBlocks.length > 0) {
      // 解析用户消息：分离 contexts 和 content
      const parsed = parseUserMessage(userContentBlocks)
      
      // 构建 contexts（文件引用 + Context 图片）
      const contexts: ContextReference[] = [
        ...parsed.contexts,
        // Context 图片也加入 contexts
        ...parsed.contextImages.map(img => ({
          type: 'image' as const,
          uri: `image://context`,
          displayType: 'TAG' as const,
          mimeType: img.source.media_type,
          base64Data: img.source.type === 'base64' ? img.source.data : undefined
        }))
      ]
      
      // content 只包含用户直接输入的内容（第一个普通文本块之后的内容，保持原始顺序）
      const content = parsed.userContent
      
        const userMessage: UserMessage = {
          type: 'userMessage',
          id: message.id,
        contexts: contexts.length > 0 ? contexts : undefined,
        content: content.length > 0 ? content : [],
          timestamp: message.timestamp
        }
        displayItems.push(userMessage)
      }

      // 处理 tool_result（更新工具调用状态）
      const toolResults = message.content.filter(b => b.type === 'tool_result') as ToolResultBlock[]
      for (const resultBlock of toolResults) {
        const toolCall = pendingToolCalls.get(resultBlock.tool_use_id)
        if (toolCall) {
          updateToolCallResult(toolCall, resultBlock)
        }
      }
    } else if (message.role === 'assistant') {
      // AI 助手消息 - 按顺序处理 content 块
      // 收集所有文本块的索引，用于标记最后一个文本块
      const textBlockIndices: number[] = []
      message.content.forEach((block, idx) => {
        if (isTextBlock(block) && block.text.trim()) {
          textBlockIndices.push(idx)
        }
      })
      const lastTextBlockIndex = textBlockIndices.length > 0 ? textBlockIndices[textBlockIndices.length - 1] : -1

      for (let blockIdx = 0; blockIdx < message.content.length; blockIdx++) {
        const block = message.content[blockIdx]

        if (isTextBlock(block) && block.text.trim()) {
          // 文本块 -> AssistantText
          const isLastTextBlock = blockIdx === lastTextBlockIndex

          // 构建统计信息（仅最后一个文本块有）
          let stats: RequestStats | undefined
          if (isLastTextBlock && message.tokenUsage) {
            // 查找最近的用户消息时间戳
            let lastUserTimestamp = 0
            for (let i = messages.indexOf(message) - 1; i >= 0; i--) {
              if (messages[i].role === 'user') {
                lastUserTimestamp = messages[i].timestamp
                break
              }
            }
            const requestDuration = lastUserTimestamp > 0
              ? message.timestamp - lastUserTimestamp
              : 0

            stats = {
              requestDuration,
              inputTokens: message.tokenUsage.input_tokens,
              outputTokens: message.tokenUsage.output_tokens
            }
          }

          const assistantText: AssistantText = {
            type: 'assistantText',
            id: `${message.id}-text-${displayItems.length}`,
            content: block.text,
            timestamp: message.timestamp,
            isLastInMessage: isLastTextBlock,
            stats
          }
          displayItems.push(assistantText)
        } else if (isToolUseBlock(block)) {
          // 工具调用块 -> ToolCall
          const toolCall = createToolCall(block, pendingToolCalls)
          displayItems.push(toolCall)
        }
      }
    } else if (message.role === 'system') {
      // 系统消息
      const textBlocks = message.content.filter(b => b.type === 'text') as TextBlock[]
      if (textBlocks.length > 0) {
        const systemMessage: SystemMessage = {
          type: 'systemMessage',
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
