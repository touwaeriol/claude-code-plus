/**
 * 工具使用统计
 *
 * 从 DisplayItems 中统计各类工具的使用情况
 */

import type { DisplayItem, ToolCall } from '@/types/display'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'

export interface ToolUsageStats {
  /** 修改的文件数 (Write + Edit + MultiEdit) */
  filesChanged: number
  /** 读取的文件数 (Read) */
  filesRead: number
  /** 总工具调用数 */
  toolsUsed: number
  /** 执行的命令数 (Bash) */
  commandsRun: number
  /** 搜索次数 (Grep + Glob + WebSearch) */
  searchesPerformed: number
}

/**
 * 计算工具使用统计
 */
export function calculateToolStats(displayItems: DisplayItem[]): ToolUsageStats {
  const stats: ToolUsageStats = {
    filesChanged: 0,
    filesRead: 0,
    toolsUsed: 0,
    commandsRun: 0,
    searchesPerformed: 0
  }

  for (const item of displayItems) {
    if (item.displayType !== 'toolCall') continue

    const toolCall = item as ToolCall
    stats.toolsUsed++

    switch (toolCall.toolType) {
      case CLAUDE_TOOL_TYPE.READ:
        stats.filesRead++
        break
      case CLAUDE_TOOL_TYPE.WRITE:
      case CLAUDE_TOOL_TYPE.EDIT:
      case CLAUDE_TOOL_TYPE.MULTI_EDIT:
        stats.filesChanged++
        break
      case CLAUDE_TOOL_TYPE.BASH:
        stats.commandsRun++
        break
      case CLAUDE_TOOL_TYPE.GREP:
      case CLAUDE_TOOL_TYPE.GLOB:
      case CLAUDE_TOOL_TYPE.WEB_SEARCH:
        stats.searchesPerformed++
        break
    }
  }

  return stats
}

/**
 * 检查是否有统计数据（用于决定是否显示统计栏）
 */
export function hasToolStats(stats: ToolUsageStats): boolean {
  return stats.toolsUsed > 0
}

/**
 * 从消息列表计算工具统计（用于直接从 messages 计算）
 */
export function calculateToolStatsFromMessages(messages: any[]): ToolUsageStats {
  const stats: ToolUsageStats = {
    filesChanged: 0,
    filesRead: 0,
    toolsUsed: 0,
    commandsRun: 0,
    searchesPerformed: 0
  }

  for (const msg of messages) {
    if (!msg.content) continue

    for (const block of msg.content) {
      if (block.type !== 'tool_use') continue

      stats.toolsUsed++
      const toolName = (block as any).toolName

      switch (toolName) {
        case 'Read':
          stats.filesRead++
          break
        case 'Write':
        case 'Edit':
        case 'MultiEdit':
          stats.filesChanged++
          break
        case 'Bash':
          stats.commandsRun++
          break
        case 'Grep':
        case 'Glob':
        case 'WebSearch':
          stats.searchesPerformed++
          break
      }
    }
  }

  return stats
}
