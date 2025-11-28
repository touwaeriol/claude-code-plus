/**
 * 工具 ViewModel 构建（简化版，直接基于 ToolCall）
 */
import type { ToolCall } from '@/types/display'

export interface ToolCallViewModel {
  toolType?: string
  compactSummary?: string
  toolDetail?: {
    parameters?: Record<string, any>
    toolType?: string
  }
  toolResult?: any
}

export function buildToolViewModel(toolCall: ToolCall): ToolCallViewModel {
  return {
    toolType: toolCall.toolType,
    compactSummary: undefined,
    toolDetail: {
      parameters: toolCall.input,
      toolType: toolCall.toolType
    },
    toolResult: toolCall.result
  }
}
