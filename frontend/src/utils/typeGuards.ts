/**
 * WebSocket 响应类型守卫
 * 
 * 提供类型安全的运行时类型检查函数
 * 使用 TypeScript 的类型谓词（Type Predicates）实现类型收窄
 */

import type {
  WebSocketResponse,
  UserMessageResponse,
  AssistantMessageResponse,
  SystemMessageResponse,
  ResultMessageResponse,
  StreamEventResponse,
  ErrorMessageResponse
} from '@/services/websocketClient'

// ===== 基础类型守卫 =====

/**
 * 检查是否为用户消息响应
 */
export function isUserMessage(
  response: WebSocketResponse
): response is UserMessageResponse {
  return response.type === 'user'
}

/**
 * 检查是否为 AI 助手消息响应
 */
export function isAssistantMessage(
  response: WebSocketResponse
): response is AssistantMessageResponse {
  return response.type === 'assistant'
}

/**
 * 检查是否为系统消息响应
 */
export function isSystemMessage(
  response: WebSocketResponse
): response is SystemMessageResponse {
  return response.type === 'system'
}

/**
 * 检查是否为结果消息响应（会话结束）
 */
export function isResultMessage(
  response: WebSocketResponse
): response is ResultMessageResponse {
  return response.type === 'result'
}

/**
 * 检查是否为流事件响应
 */
export function isStreamEvent(
  response: WebSocketResponse
): response is StreamEventResponse {
  return response.type === 'stream_event'
}

/**
 * 检查是否为错误消息响应
 */
export function isErrorMessage(
  response: WebSocketResponse
): response is ErrorMessageResponse {
  return response.type === 'error'
}

// ===== 特殊类型守卫 =====

/**
 * 检查是否为系统初始化消息
 */
export function isSystemInit(
  response: WebSocketResponse
): response is SystemMessageResponse & { message: { subtype: 'init' } } {
  return isSystemMessage(response) && response.message.subtype === 'init'
}

/**
 * 检查是否为模型变更消息
 */
export function isSystemModelChanged(
  response: WebSocketResponse
): response is SystemMessageResponse & { message: { subtype: 'model_changed' } } {
  return isSystemMessage(response) && response.message.subtype === 'model_changed'
}

/**
 * 检查是否为消息已发送确认
 */
export function isSystemMessageSent(
  response: WebSocketResponse
): response is SystemMessageResponse & { message: { subtype: 'message_sent' } } {
  return isSystemMessage(response) && response.message.subtype === 'message_sent'
}

// ===== 组合类型守卫 =====

/**
 * 检查是否为需要提取模型 ID 的系统消息
 */
export function isSystemMessageWithModelId(
  response: WebSocketResponse
): response is SystemMessageResponse & { message: { subtype: 'init' | 'model_changed' } } {
  return isSystemInit(response) || isSystemModelChanged(response)
}

/**
 * 检查是否为可见的消息类型（需要显示在 UI 中）
 */
export function isVisibleMessage(
  response: WebSocketResponse
): response is UserMessageResponse | AssistantMessageResponse | ErrorMessageResponse {
  return isUserMessage(response) || isAssistantMessage(response) || isErrorMessage(response)
}

/**
 * 检查是否为元数据消息（不直接显示，但需要处理）
 */
export function isMetadataMessage(
  response: WebSocketResponse
): response is SystemMessageResponse | ResultMessageResponse | StreamEventResponse {
  return isSystemMessage(response) || isResultMessage(response) || isStreamEvent(response)
}

