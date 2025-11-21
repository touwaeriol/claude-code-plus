/**
 * 消息转换工具
 * 
 * 将后端的 Message 转换为前端的 DisplayItem
 */

import { reactive } from 'vue'
import type { Message, TextBlock, ToolUseBlock, ToolResultBlock, ImageBlock } from '@/types/message'
import type {
  DisplayItem,
  UserMessage,
  AssistantText,
  SystemMessage,
  ToolCall,
  ToolResult,
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
 * 将 Message 数组转换为 DisplayItem 数组
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
      // 用户消息
      const textBlocks = message.content.filter(b => b.type === 'text') as TextBlock[]
      const imageBlocks = message.content.filter(b => b.type === 'image') as ImageBlock[]

      // 只有文本或图片时才创建用户消息
      if (textBlocks.length > 0 || imageBlocks.length > 0) {
        const userMessage: UserMessage = {
          type: 'userMessage',
          id: message.id,
          content: textBlocks.map(b => b.text).join('\n'),
          images: imageBlocks.length > 0 ? imageBlocks : undefined,
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
      for (const block of message.content) {
        if (block.type === 'text') {
          // 文本块 -> AssistantText
          const textBlock = block as TextBlock
          if (textBlock.text.trim()) {
            const assistantText: AssistantText = {
              type: 'assistantText',
              id: `${message.id}-text-${displayItems.length}`,
              content: textBlock.text,
              timestamp: message.timestamp
            }
            displayItems.push(assistantText)
          }
        } else if (block.type === 'tool_use' || (typeof block.type === 'string' && block.type.endsWith('_tool_use'))) {
          // 工具调用块 -> ToolCall
          const toolUseBlock = block as ToolUseBlock
          const toolCall = createToolCall(toolUseBlock, pendingToolCalls)
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
