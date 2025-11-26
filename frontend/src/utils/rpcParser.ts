/**
 * RPC 消息解析器
 *
 * 提供类型守卫和安全解析函数，将 unknown 类型转换为强类型的 RPC 消息
 */

import type {
  RpcMessage,
  RpcStreamWrapper,
  RpcResultWrapper,
  RpcErrorWrapper,
  RpcCompleteWrapper,
  RpcStreamEvent,
  RpcStreamEventType,
  RpcMessageStart,
  RpcTextDelta,
  RpcThinkingDelta,
  RpcToolStart,
  RpcToolProgress,
  RpcToolComplete,
  RpcMessageComplete,
  RpcErrorEvent,
  RpcAssistantMessage
} from '@/types/rpc'

// ===== 常量 =====

/** 所有流式事件类型 */
export const STREAM_EVENT_TYPES: readonly RpcStreamEventType[] = [
  'message_start',
  'text_delta',
  'thinking_delta',
  'tool_start',
  'tool_progress',
  'tool_complete',
  'message_complete',
  'error',
  'assistant'
] as const

// ===== 辅助函数 =====

function hasType(value: unknown): value is { type: string } {
  return typeof value === 'object' && value !== null && 'type' in value
}

/** 检查是否为有效的流式事件类型 */
export function isStreamEventType(type: unknown): type is RpcStreamEventType {
  return typeof type === 'string' && STREAM_EVENT_TYPES.includes(type as RpcStreamEventType)
}

// ===== 类型守卫：流式事件 =====

/** 检查是否为 RpcStreamEvent */
export function isRpcStreamEvent(value: unknown): value is RpcStreamEvent {
  if (!hasType(value)) return false
  return isStreamEventType(value.type)
}

/** 检查是否为 RpcMessageStart */
export function isRpcMessageStart(value: unknown): value is RpcMessageStart {
  return (
    hasType(value) &&
    value.type === 'message_start' &&
    typeof (value as any).messageId === 'string' &&
    (value as any).messageId.length > 0
  )
}

/** 检查是否为 RpcTextDelta */
export function isRpcTextDelta(value: unknown): value is RpcTextDelta {
  return hasType(value) && value.type === 'text_delta' && 'text' in value
}

/** 检查是否为 RpcThinkingDelta */
export function isRpcThinkingDelta(value: unknown): value is RpcThinkingDelta {
  return hasType(value) && value.type === 'thinking_delta' && 'thinking' in value
}

/** 检查是否为 RpcToolStart */
export function isRpcToolStart(value: unknown): value is RpcToolStart {
  return hasType(value) && value.type === 'tool_start' && 'toolId' in value && 'toolName' in value
}

/** 检查是否为 RpcToolProgress */
export function isRpcToolProgress(value: unknown): value is RpcToolProgress {
  return hasType(value) && value.type === 'tool_progress' && 'toolId' in value && 'status' in value
}

/** 检查是否为 RpcToolComplete */
export function isRpcToolComplete(value: unknown): value is RpcToolComplete {
  return hasType(value) && value.type === 'tool_complete' && 'toolId' in value && 'result' in value
}

/** 检查是否为 RpcMessageComplete */
export function isRpcMessageComplete(value: unknown): value is RpcMessageComplete {
  return hasType(value) && value.type === 'message_complete'
}

/** 检查是否为 RpcErrorEvent */
export function isRpcErrorEvent(value: unknown): value is RpcErrorEvent {
  return hasType(value) && value.type === 'error' && 'message' in value
}

// ===== 类型守卫：WebSocket 消息包装 =====

/** 检查是否为 RpcStreamWrapper */
export function isRpcStreamWrapper(value: unknown): value is RpcStreamWrapper {
  if (!hasType(value)) return false
  const obj = value as Record<string, unknown>
  return obj.type === 'stream' && 'id' in obj && 'data' in obj && isRpcStreamEvent(obj.data)
}

/** 检查是否为 RpcResultWrapper */
export function isRpcResultWrapper(value: unknown): value is RpcResultWrapper {
  if (typeof value !== 'object' || value === null) return false
  const obj = value as Record<string, unknown>
  return 'id' in obj && 'result' in obj && obj.error === null
}

/** 检查是否为 RpcErrorWrapper */
export function isRpcErrorWrapper(value: unknown): value is RpcErrorWrapper {
  if (typeof value !== 'object' || value === null) return false
  const obj = value as Record<string, unknown>
  return 'id' in obj && obj.result === null && 'error' in obj && obj.error !== null
}

/** 检查是否为 RpcCompleteWrapper */
export function isRpcCompleteWrapper(value: unknown): value is RpcCompleteWrapper {
  if (!hasType(value)) return false
  const obj = value as Record<string, unknown>
  return obj.type === 'complete' && 'id' in obj
}

// ===== 主解析函数 =====

/**
 * 解析 WebSocket 消息为强类型 RpcMessage
 */
export function parseRpcMessage(raw: unknown): RpcMessage | null {
  if (raw === null || typeof raw !== 'object') {
    return null
  }
  if (isRpcStreamWrapper(raw)) return raw
  if (isRpcCompleteWrapper(raw)) return raw
  if (isRpcResultWrapper(raw)) return raw
  if (isRpcErrorWrapper(raw)) return raw
  return null
}

/**
 * 从 RpcMessage 中提取流式事件
 */
export function extractStreamEvent(message: RpcMessage): RpcStreamEvent | null {
  if (isRpcStreamWrapper(message)) {
    return message.data
  }
  return null
}

/**
 * 解析 JSON 字符串为 RpcMessage
 */
export function parseRpcMessageFromString(jsonString: string): RpcMessage | null {
  try {
    const parsed = JSON.parse(jsonString)
    return parseRpcMessage(parsed)
  } catch {
    return null
  }
}
