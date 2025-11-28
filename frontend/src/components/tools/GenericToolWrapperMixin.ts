import type { ToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'

export interface BaseToolProps {
  toolCall: ToolCall
}

export function buildStatus(toolCall: ToolCall) {
  return {
    isSuccess: toolCall.status === ToolCallStatus.SUCCESS,
    isFailed: toolCall.status === ToolCallStatus.FAILED,
    isRunning: toolCall.status === ToolCallStatus.RUNNING
  }
}
