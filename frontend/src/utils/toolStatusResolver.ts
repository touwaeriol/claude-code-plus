/**
 * 工具状态解析器
 *
 * 从消息列表倒序查找 tool_result，计算工具调用状态
 * 替代原来的两套 Map (toolCallsMap + pendingToolCalls)
 */

import type { Message, ToolResultContent } from '@/types/message'

export type ToolStatus = 'running' | 'success' | 'error'

export interface ToolStatusInfo {
  status: ToolStatus
  result?: ToolResultContent
}

/**
 * 从消息列表倒序查找工具结果
 *
 * 工具结果通常在最近的消息中，所以倒序遍历效率最高
 * 大多数情况下只需要遍历 1-2 条消息就能找到结果
 */
export function resolveToolStatus(
  toolUseId: string,
  messages: Message[]
): ToolStatusInfo {
  // 倒序遍历消息
  for (let i = messages.length - 1; i >= 0; i--) {
    const msg = messages[i]
    if (!msg.content) continue

    // 在消息内容中查找 tool_result
    for (const block of msg.content) {
      if (block.type === 'tool_result') {
        const resultBlock = block as ToolResultContent
        if (resultBlock.tool_use_id === toolUseId) {
          return {
            status: resultBlock.is_error ? 'error' : 'success',
            result: resultBlock
          }
        }
      }
    }
  }

  return { status: 'running' }
}

/**
 * 批量解析多个工具的状态
 *
 * 优化：一次遍历消息列表，找到所有需要的工具结果
 * 适合在组件挂载时批量获取状态
 */
export function resolveToolStatuses(
  toolUseIds: string[],
  messages: Message[]
): Map<string, ToolStatusInfo> {
  const results = new Map<string, ToolStatusInfo>()
  const pending = new Set(toolUseIds)

  // 如果没有要查找的工具，直接返回
  if (pending.size === 0) {
    return results
  }

  // 倒序遍历，找到所有结果后提前退出
  for (let i = messages.length - 1; i >= 0 && pending.size > 0; i--) {
    const msg = messages[i]
    if (!msg.content) continue

    for (const block of msg.content) {
      if (block.type === 'tool_result') {
        const resultBlock = block as ToolResultContent
        if (pending.has(resultBlock.tool_use_id)) {
          results.set(resultBlock.tool_use_id, {
            status: resultBlock.is_error ? 'error' : 'success',
            result: resultBlock
          })
          pending.delete(resultBlock.tool_use_id)
        }
      }
    }
  }

  // 剩余未找到结果的都是 running 状态
  for (const id of pending) {
    results.set(id, { status: 'running' })
  }

  return results
}

/**
 * 从消息列表中提取所有工具调用 ID
 */
export function extractToolUseIds(messages: Message[]): string[] {
  const ids: string[] = []

  for (const msg of messages) {
    if (!msg.content) continue

    for (const block of msg.content) {
      if (block.type === 'tool_use') {
        ids.push((block as any).id)
      }
    }
  }

  return ids
}

/**
 * 将 ToolStatus 转换为 ToolCallStatus 枚举
 * 用于兼容现有的 ToolCallStatus 类型
 */
export function toToolCallStatus(status: ToolStatus): 'RUNNING' | 'SUCCESS' | 'FAILED' {
  switch (status) {
    case 'running':
      return 'RUNNING'
    case 'success':
      return 'SUCCESS'
    case 'error':
      return 'FAILED'
  }
}
