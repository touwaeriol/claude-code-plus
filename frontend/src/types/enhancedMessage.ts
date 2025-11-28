/**
 * 增强的消息类型定义
 * 对应 Kotlin 的 EnhancedMessage 和相关模型
 */

/**
 * 消息角色
 */
export enum MessageRole {
  USER = 'USER',
  ASSISTANT = 'ASSISTANT',
  SYSTEM = 'SYSTEM',
  ERROR = 'ERROR'
}

/**
 * 消息状态
 */
export enum MessageStatus {
  SENDING = 'SENDING',
  STREAMING = 'STREAMING',
  COMPLETE = 'COMPLETE',
  FAILED = 'FAILED'
}

/**
 * 工具调用状态
 */
export enum ToolCallStatus {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  CANCELLED = 'CANCELLED'
}

/**
 * AI 模型枚举
 */
export enum AiModel {
  DEFAULT = 'DEFAULT',
  OPUS = 'OPUS',
  SONNET = 'SONNET',
  OPUS_PLAN = 'OPUS_PLAN'
}

import type { ContextReference } from './display'
// PermissionMode 统一使用 RpcPermissionMode，从 rpc.ts 导入
export type { RpcPermissionMode as PermissionMode } from './rpc'
export type { ContextDisplayType } from './display'

/**
 * 图片引用（扩展 ContextReference）
 */
export interface ImageReference {
  type: 'image'
  uri: string
  displayType: 'TAG' | 'INLINE'
  name: string
  mimeType: string
  base64Data: string
  size?: number
}

/**
 * 工具调用结果
 */
export type ToolResult =
  | { type: 'success'; output: string; summary?: string; details?: string; affectedFiles?: string[] }
  | { type: 'failure'; error: string; details?: string }
  | { type: 'fileSearch'; files: any[]; totalCount: number }
  | { type: 'fileRead'; content: string; lineCount: number; language?: string }
  | { type: 'fileEdit'; oldContent: string; newContent: string; changedLines: [number, number] }
  | { type: 'command'; output: string; exitCode: number; duration: number }

/**
 * 工具调用信息
 */
export interface ToolCall {
  id: string
  name: string
  displayName?: string
  status: ToolCallStatus
  result?: ToolResult
  startTime: number
  endTime?: number
  viewModel?: any  // 对应 Kotlin 的 ToolCallViewModel
}

/**
 * 消息时间线元素
 *
 * 注：displayType 用于统一与 DisplayItem 的命名规范
 */
export type MessageTimelineItem =
  | { displayType: 'toolCall'; toolCall: ToolCall; timestamp: number }
  | { displayType: 'content'; content: string; timestamp: number }
  | { displayType: 'thinking'; content: string; signature?: string; timestamp: number }
  | { displayType: 'status'; status: string; isStreaming: boolean; timestamp: number }

/**
 * Token 使用信息
 */
export interface TokenUsage {
  inputTokens: number
  outputTokens: number
  cacheCreationTokens: number
  cacheReadTokens: number
  totalTokens: number
}

/**
 * 增强的消息模型
 */
export interface EnhancedMessage {
  id: string
  role: MessageRole
  timestamp: number
  contexts: ContextReference[]
  model?: AiModel
  status: MessageStatus
  isStreaming: boolean
  isError: boolean
  orderedElements: MessageTimelineItem[]  // 核心数据：有序元素
  tokenUsage?: TokenUsage
  isCompactSummary: boolean
}

/**
 * 辅助函数：从 EnhancedMessage 提取纯文本内容
 */
export function getMessageContent(message: EnhancedMessage): string {
  return message.orderedElements
    .filter(item => item.displayType === 'content')
    .map(item => (item as any).content)
    .join('')
}

/**
 * 辅助函数：从 EnhancedMessage 提取工具调用
 */
export function getMessageToolCalls(message: EnhancedMessage): ToolCall[] {
  return message.orderedElements
    .filter(item => item.displayType === 'toolCall')
    .map(item => (item as any).toolCall)
}

/**
 * 辅助函数：检查消息是否包含工具调用
 */
export function hasToolCalls(message: EnhancedMessage): boolean {
  return message.orderedElements.some(item => item.displayType === 'toolCall')
}

/**
 * 辅助函数：检查是否为纯文本消息
 */
export function isPlainTextMessage(message: EnhancedMessage): boolean {
  return !hasToolCalls(message)
}
