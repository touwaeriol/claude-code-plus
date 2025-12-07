/**
 * RPC 消息解析器
 *
 * 提供类型守卫和安全解析函数，将 unknown 转成强类型 RPC 结构。
 */

import type {
  RpcMessage,
  RpcMessageType,
  RpcStreamWrapper,
  RpcResultWrapper,
  RpcErrorWrapper,
  RpcCompleteWrapper,
  RpcWebSocketMessage,
  RpcStreamEvent,
  RpcStreamEventData,
  RpcDelta
} from '@/types/rpc'

// ===== 常量 =====

/** 所有顶层 RPC 消息类型 */
export const RPC_MESSAGE_TYPES: readonly RpcMessageType[] = [
  'user',
  'assistant',
  'result',
  'stream_event',
  'error',
  'status_system',
  'compact_boundary'
] as const

// ===== 帮助函数 =====

const isObject = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null
const isString = (v: unknown): v is string => typeof v === 'string'
const isNumber = (v: unknown): v is number => typeof v === 'number'
const isBoolean = (v: unknown): v is boolean => typeof v === 'boolean'
const isArray = Array.isArray

function hasType(value: unknown): value is { type: string } {
  return isObject(value) && isString((value as any).type)
}

function isRpcMessageType(type: unknown): type is RpcMessageType {
  return isString(type) && RPC_MESSAGE_TYPES.includes(type as RpcMessageType)
}

// ===== Delta 守卫 =====

function isRpcTextDelta(delta: unknown): delta is RpcDelta {
  return isObject(delta) && delta.type === 'text_delta' && isString((delta as any).text)
}

function isRpcThinkingDelta(delta: unknown): delta is RpcDelta {
  return isObject(delta) && delta.type === 'thinking_delta' && isString((delta as any).thinking)
}

function isRpcInputJsonDelta(delta: unknown): delta is RpcDelta {
  return isObject(delta) && delta.type === 'input_json_delta' && isString((delta as any).partial_json)
}

function isRpcDelta(delta: unknown): delta is RpcDelta {
  return isRpcTextDelta(delta) || isRpcThinkingDelta(delta) || isRpcInputJsonDelta(delta)
}

// ===== StreamEventData 守卫 =====

function isMessageStartEvent(event: unknown): event is RpcStreamEventData {
  if (!isObject(event) || event.type !== 'message_start') return false
  if ('message' in event && (event as any).message !== undefined) {
    const msg = (event as any).message
    // message 字段可能为 null，表示暂无初始内容
    if (msg === null) return true
    if (!isObject(msg)) return false
    const content = (msg as any).content
    // content 允许为空或 null；有值时必须是数组
    if (content !== undefined && content !== null && !isArray(content)) return false
  }
  return true
}

function isContentBlockStartEvent(event: unknown): event is RpcStreamEventData {
  if (!isObject(event) || event.type !== 'content_block_start') return false
  return isNumber((event as any).index) && isObject((event as any).content_block)
}

function isContentBlockDeltaEvent(event: unknown): event is RpcStreamEventData {
  if (!isObject(event) || event.type !== 'content_block_delta') return false
  return isNumber((event as any).index) && isRpcDelta((event as any).delta)
}

function isContentBlockStopEvent(event: unknown): event is RpcStreamEventData {
  return isObject(event) && event.type === 'content_block_stop' && isNumber((event as any).index)
}

function isMessageDeltaEvent(event: unknown): event is RpcStreamEventData {
  return isObject(event) && event.type === 'message_delta'
}

function isMessageStopEvent(event: unknown): event is RpcStreamEventData {
  return isObject(event) && event.type === 'message_stop'
}

function isRpcStreamEventData(event: unknown): event is RpcStreamEventData {
  return (
    isMessageStartEvent(event) ||
    isContentBlockStartEvent(event) ||
    isContentBlockDeltaEvent(event) ||
    isContentBlockStopEvent(event) ||
    isMessageDeltaEvent(event) ||
    isMessageStopEvent(event)
  )
}

// ===== 类型守卫：顶层 RPC 消息 =====

/** 检查是否为 RpcMessage（user/assistant/result/stream_event/error） */
export function isRpcMessage(value: unknown): value is RpcMessage {
  if (!hasType(value) || !isRpcMessageType((value as any).type)) return false

  const obj = value as any
  switch (obj.type) {
    case 'user':
      return isObject(obj.message) && isArray(obj.message.content)
    case 'assistant':
      return isObject(obj.message) && isArray(obj.message.content)
    case 'result':
      return isBoolean(obj.is_error) && isString(obj.subtype || '') && ('duration_ms' in obj ? isNumber(obj.duration_ms) || obj.duration_ms === undefined : true)
    case 'error':
      return isString(obj.message)
    case 'stream_event':
      return isString(obj.uuid) && isString(obj.session_id) && isRpcStreamEventData(obj.event)
    case 'status_system':
      // status_system: { type, status, session_id, provider, ... }
      return true
    case 'compact_boundary':
      // compact_boundary: { type, compact_metadata: { trigger, pre_tokens } }
      return true
    default:
      return false
  }
}

/** 检查是否为 RpcStreamEvent（顶层 type === 'stream_event'） */
export function isRpcStreamEvent(value: unknown): value is RpcStreamEvent {
  return isRpcMessage(value) && (value as RpcMessage).type === 'stream_event'
}

// ===== 类型守卫：WebSocket 消息包装 =====

/** 检查是否为 RpcStreamWrapper */
export function isRpcStreamWrapper(value: unknown): value is RpcStreamWrapper {
  if (!hasType(value)) return false
  const obj = value as Record<string, unknown>
  return obj.type === 'stream' && typeof obj.id === 'string' && 'data' in obj && isRpcMessage(obj.data)
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
 * 解析 WebSocket 消息为强类型 Rpc 消息/包装
 */
export function parseRpcMessage(raw: unknown): RpcWebSocketMessage | RpcMessage | null {
  if (raw === null || typeof raw !== 'object') {
    return null
  }
  if (isRpcStreamWrapper(raw)) return raw
  if (isRpcCompleteWrapper(raw)) return raw
  if (isRpcResultWrapper(raw)) return raw
  if (isRpcErrorWrapper(raw)) return raw
  if (isRpcMessage(raw)) return raw
  return null
}

/**
 * 从 RpcMessage 中提取流式事件（type === 'stream_event'）
 */
export function extractStreamEvent(message: RpcMessage): RpcStreamEvent | null {
  if (isRpcStreamEvent(message)) return message
  return null
}

/**
 * 解析 JSON 字符串为 RpcMessage
 */
export function parseRpcMessageFromString(jsonString: string): RpcWebSocketMessage | RpcMessage | null {
  try {
    const parsed = JSON.parse(jsonString)
    return parseRpcMessage(parsed)
  } catch {
    return null
  }
}
