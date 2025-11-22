/**
 * ContentBlock 相关的工具函数
 *
 * 提供类型守卫和通用的内容块处理函数
 */

import type { ContentBlock, ToolUseBlock, ToolResultBlock, TextBlock, ThinkingBlock } from '@/types/message'

/**
 * 检查是否为 tool_use 块
 *
 * 支持两种形式：
 * - type === 'tool_use'
 * - type.endsWith('_tool_use')（如 'edit_tool_use'）
 */
export function isToolUseBlock(block: ContentBlock | any): block is ToolUseBlock {
  if (!block || typeof block !== 'object') return false
  const type = block.type
  return type === 'tool_use' || (typeof type === 'string' && type.endsWith('_tool_use'))
}

/**
 * 检查是否为 tool_result 块
 */
export function isToolResultBlock(block: ContentBlock | any): block is ToolResultBlock {
  return block?.type === 'tool_result'
}

/**
 * 检查是否为 text 块
 */
export function isTextBlock(block: ContentBlock | any): block is TextBlock {
  return block?.type === 'text'
}

/**
 * 检查是否为 thinking 块
 */
export function isThinkingBlock(block: ContentBlock | any): block is ThinkingBlock {
  return block?.type === 'thinking'
}

/**
 * 从内容块数组中提取所有 tool_use 块
 */
export function extractToolUseBlocks(content: ContentBlock[]): ToolUseBlock[] {
  return content.filter(isToolUseBlock)
}

/**
 * 从内容块数组中提取所有 tool_result 块
 */
export function extractToolResultBlocks(content: ContentBlock[]): ToolResultBlock[] {
  return content.filter(isToolResultBlock)
}

/**
 * 从内容块数组中提取所有文本块
 */
export function extractTextBlocks(content: ContentBlock[]): TextBlock[] {
  return content.filter(isTextBlock)
}

/**
 * 生成唯一的消息/块 ID
 */
export function generateId(prefix: string = 'id'): string {
  return `${prefix}-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`
}

/**
 * 生成占位符消息 ID
 */
export function generatePlaceholderId(): string {
  return `assistant-placeholder-${Date.now()}-${crypto.randomUUID().substring(0, 8)}`
}
