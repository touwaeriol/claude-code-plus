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

/**
 * 权限模式
 */
export enum PermissionMode {
  DEFAULT = 'DEFAULT',
  ACCEPT = 'ACCEPT',
  BYPASS = 'BYPASS',
  PLAN = 'PLAN'
}

/**
 * 上下文显示类型
 */
export enum ContextDisplayType {
  TAG = 'TAG',
  INLINE = 'INLINE'
}

/**
 * 上下文引用基类
 */
export interface ContextReference {
  displayType: ContextDisplayType
  uri: string
}

/**
 * 文件引用
 */
export interface FileReference extends ContextReference {
  type: 'file'
  path: string
  fullPath: string
}

/**
 * Web 引用
 */
export interface WebReference extends ContextReference {
  type: 'web'
  url: string
  title?: string
}

/**
 * 文件夹引用
 */
export interface FolderReference extends ContextReference {
  type: 'folder'
  path: string
  fileCount?: number
  totalSize?: number
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
 */
export type MessageTimelineItem =
  | { type: 'toolCall'; toolCall: ToolCall; timestamp: number }
  | { type: 'content'; content: string; timestamp: number }
  | { type: 'status'; status: string; isStreaming: boolean; timestamp: number }

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
    .filter(item => item.type === 'content')
    .map(item => (item as any).content)
    .join('')
}

/**
 * 辅助函数：从 EnhancedMessage 提取工具调用
 */
export function getMessageToolCalls(message: EnhancedMessage): ToolCall[] {
  return message.orderedElements
    .filter(item => item.type === 'toolCall')
    .map(item => (item as any).toolCall)
}

/**
 * 辅助函数：检查消息是否包含工具调用
 */
export function hasToolCalls(message: EnhancedMessage): boolean {
  return message.orderedElements.some(item => item.type === 'toolCall')
}

/**
 * 辅助函数：检查是否为纯文本消息
 */
export function isPlainTextMessage(message: EnhancedMessage): boolean {
  return !hasToolCalls(message)
}
