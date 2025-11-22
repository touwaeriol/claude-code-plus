/**
 * 前端显示层类型定义
 * 
 * 这些类型用于 UI 展示，是从后端 Message 转换而来的 ViewModel
 */

import type { ImageBlock } from './message'
import { TOOL_TYPE } from '@/constants/toolTypes'

// ============ 基础类型 ============

/**
 * 工具调用状态
 */
export enum ToolCallStatus {
  RUNNING = 'RUNNING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED'
}

/**
 * 连接状态
 */
export enum ConnectionStatus {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  ERROR = 'ERROR'
}

/**
 * 上下文引用
 */
export interface ContextReference {
  type: 'file' | 'web' | 'folder' | 'image'
  uri: string
  displayType: 'TAG' | 'INLINE'
  // 具体类型的额外字段
  path?: string
  fullPath?: string
  url?: string
  title?: string
  fileCount?: number
  totalSize?: number
  name?: string
  mimeType?: string
  base64Data?: string
  size?: number
}

// ============ DisplayItem 基础接口 ============

/**
 * 所有 DisplayItem 的基础接口
 */
export interface BaseDisplayItem {
  id: string
  timestamp: number
}

// ============ 消息类型 ============

/**
 * 用户消息
 */
export interface UserMessage extends BaseDisplayItem {
  type: 'userMessage'
  content: string
  images?: ImageBlock[]
  contexts?: ContextReference[]
  requestStats?: RequestStats  // 请求统计信息（请求完成后填充）
  isStreaming?: boolean        // 是否正在流式响应
}

/**
 * 请求统计信息
 */
export interface RequestStats {
  requestDuration: number     // 请求耗时（毫秒）
  inputTokens: number         // 上行 tokens
  outputTokens: number        // 下行 tokens
}

/**
 * AI 文本回复
 */
export interface AssistantText extends BaseDisplayItem {
  type: 'assistantText'
  content: string
  stats?: RequestStats        // 请求统计信息（仅最后一个文本块有）
  isLastInMessage?: boolean   // 是否是该消息的最后一个文本块
}

/**
 * 系统消息
 */
export interface SystemMessage extends BaseDisplayItem {
  type: 'systemMessage'
  content: string
  level: 'info' | 'warning' | 'error'
}

// ============ 工具调用类型 ============

/**
 * 工具调用基础接口
 */
export interface BaseToolCall extends BaseDisplayItem {
  type: 'toolCall'
  toolType: string
  status: ToolCallStatus
  startTime: number
  endTime?: number
  input: Record<string, any>
  result?: ToolResult
}

/**
 * 工具调用结果
 */
export type ToolResult =
  | { type: 'success'; output: string; summary?: string; details?: string; affectedFiles?: string[] }
  | { type: 'error'; error: string; details?: string }

/**
 * Read 工具调用
 */
export interface ReadToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.READ
  input: {
    file_path?: string
    path?: string
    offset?: number
    limit?: number
    view_range?: [number, number]
  }
}

/**
 * Write 工具调用
 */
export interface WriteToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.WRITE
  input: {
    file_path?: string
    path?: string
    content: string
  }
}

/**
 * Edit 工具调用
 */
export interface EditToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.EDIT
  input: {
    file_path: string
    old_string: string
    new_string: string
    replace_all?: boolean
  }
}

/**
 * MultiEdit 工具调用
 */
export interface MultiEditToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.MULTI_EDIT
  input: {
    file_path: string
    edits: Array<{
      old_string: string
      new_string: string
      replace_all?: boolean
    }>
  }
}

/**
 * TodoWrite 工具调用
 */
export interface TodoWriteToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.TODO_WRITE
  input: {
    todos: Array<{
      content: string
      status: 'pending' | 'in_progress' | 'completed'
      activeForm: string
    }>
  }
}

/**
 * Bash 工具调用
 */
export interface BashToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.BASH
  input: {
    command: string
    description?: string
    cwd?: string
    timeout?: number
  }
}

/**
 * Grep 工具调用
 */
export interface GrepToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.GREP
  input: {
    pattern: string
    path?: string
    glob?: string
    type?: string
    output_mode?: 'content' | 'files_with_matches' | 'count'
  }
}

/**
 * Glob 工具调用
 */
export interface GlobToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.GLOB
  input: {
    pattern: string
    path?: string
  }
}

/**
 * WebSearch 工具调用
 */
export interface WebSearchToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.WEB_SEARCH
  input: {
    query: string
    allowed_domains?: string[]
    blocked_domains?: string[]
  }
}

/**
 * WebFetch 工具调用
 */
export interface WebFetchToolCall extends BaseToolCall {
  toolType: typeof TOOL_TYPE.WEB_FETCH
  input: {
    url: string
    prompt: string
  }
}

/**
 * 通用工具调用（用于未知或 MCP 工具）
 */
export interface GenericToolCall extends BaseToolCall {
  toolType: string
  input: Record<string, any>
}

/**
 * 工具调用联合类型
 */
export type ToolCall =
  | ReadToolCall
  | WriteToolCall
  | EditToolCall
  | MultiEditToolCall
  | TodoWriteToolCall
  | BashToolCall
  | GrepToolCall
  | GlobToolCall
  | WebSearchToolCall
  | WebFetchToolCall
  | GenericToolCall

// ============ DisplayItem 联合类型 ============

/**
 * 所有显示项的联合类型
 */
export type DisplayItem =
  | UserMessage
  | AssistantText
  | ToolCall
  | SystemMessage
