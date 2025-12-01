/**
 * 前端显示层类型定义
 *
 * 这些类型用于 UI 展示，是从后端 Message 转换而来的 ViewModel
 *
 * 字段命名规范：
 * - displayType: DisplayItem 的种类（如 'toolCall', 'userMessage'）
 * - toolName: 工具显示名称（如 'Read', 'mcp__excel__read'）
 * - toolType: 工具类型标识（如 'CLAUDE_READ', 'MCP'）
 */

import type { ContentBlock } from './message'
import { CLAUDE_TOOL_TYPE, OTHER_TOOL_TYPE, type ToolType } from '@/constants/toolTypes'

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
 * 上下文显示类型
 */
export type ContextDisplayType = 'TAG' | 'INLINE'

/**
 * 上下文引用
 */
export interface ContextReference {
  type: 'file' | 'web' | 'folder' | 'image'
  uri: string
  displayType: ContextDisplayType
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
  displayType: string    // 原 type，改为 displayType
  timestamp: number
}

// ============ 消息类型 ============

/**
 * 用户消息
 *
 * 设计说明：
 * - contexts: 上下文引用（@文件路径、图片等），从 content 开头解析出来
 * - content: 用户直接输入的内容（第一个普通文本块之后的内容，保持顺序）
 *   - 主要是文本，偶尔有图片（用户直接上传的）
 */
export interface UserMessage extends BaseDisplayItem {
  displayType: 'userMessage'
  /** 上下文引用（@文件路径、图片等），从 content 开头解析出来 */
  contexts?: ContextReference[]
  /** 用户直接输入的内容（第一个普通文本块之后的内容，保持顺序） */
  content: ContentBlock[]
  /** 请求统计信息（请求完成后填充） */
  requestStats?: RequestStats
  /** 是否正在流式响应 */
  isStreaming?: boolean
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
  displayType: 'assistantText'
  content: string
  stats?: RequestStats        // 请求统计信息（仅最后一个文本块有）
  isLastInMessage?: boolean   // 是否是该消息的最后一个文本块
  isStreaming?: boolean       // 是否处于流式输出阶段
}

/**
 * 系统消息
 */
export interface SystemMessage extends BaseDisplayItem {
  displayType: 'systemMessage'
  content: string
  level: 'info' | 'warning' | 'error'
}

/**
 * 思考内容
 */
export interface ThinkingContent extends BaseDisplayItem {
  displayType: 'thinking'
  content: string
  signature?: string
}

// ============ 工具调用类型 ============

/**
 * 工具调用基础接口
 */
export interface BaseToolCall extends BaseDisplayItem {
  displayType: 'toolCall'
  /** 工具显示名称（如 "Read", "Write", "mcp__excel__read"） */
  toolName: string
  /** 工具类型标识（如 "CLAUDE_READ", "CLAUDE_WRITE", "MCP"） */
  toolType: ToolType
  status: ToolCallStatus
  startTime: number
  endTime?: number
  input: Record<string, any>
  result?: ToolResult
}

/**
 * 工具调用结果
 *
 * 直接使用后端格式（与 ToolResultContent 保持一致），不做格式转换
 */
export type ToolResult = {
  type?: 'tool_result'
  tool_use_id?: string
  content?: string | unknown[]
  is_error?: boolean
}

// ============ Claude SDK 工具调用类型 ============

/**
 * Claude Read 工具调用
 */
export interface ClaudeReadToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.READ
  input: {
    file_path?: string
    path?: string
    offset?: number
    limit?: number
    view_range?: [number, number]
  }
}

/**
 * Claude Write 工具调用
 */
export interface ClaudeWriteToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.WRITE
  input: {
    file_path?: string
    path?: string
    content: string
  }
}

/**
 * Claude Edit 工具调用
 */
export interface ClaudeEditToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.EDIT
  input: {
    file_path: string
    old_string: string
    new_string: string
    replace_all?: boolean
  }
}

/**
 * Claude MultiEdit 工具调用
 */
export interface ClaudeMultiEditToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.MULTI_EDIT
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
 * Claude TodoWrite 工具调用
 */
export interface ClaudeTodoWriteToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.TODO_WRITE
  input: {
    todos: Array<{
      content: string
      status: 'pending' | 'in_progress' | 'completed'
      activeForm: string
    }>
  }
}

/**
 * Claude Bash 工具调用
 */
export interface ClaudeBashToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.BASH
  input: {
    command: string
    description?: string
    cwd?: string
    timeout?: number
  }
}

/**
 * Claude BashOutput 工具调用
 */
export interface ClaudeBashOutputToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.BASH_OUTPUT
  input: {
    bash_id: string
    filter?: string
  }
}

/**
 * Claude KillShell 工具调用
 */
export interface ClaudeKillShellToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.KILL_SHELL
  input: {
    shell_id: string
  }
}

/**
 * Claude Grep 工具调用
 */
export interface ClaudeGrepToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.GREP
  input: {
    pattern: string
    path?: string
    glob?: string
    type?: string
    output_mode?: 'content' | 'files_with_matches' | 'count'
  }
}

/**
 * Claude Glob 工具调用
 */
export interface ClaudeGlobToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.GLOB
  input: {
    pattern: string
    path?: string
  }
}

/**
 * Claude WebSearch 工具调用
 */
export interface ClaudeWebSearchToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.WEB_SEARCH
  input: {
    query: string
    allowed_domains?: string[]
    blocked_domains?: string[]
  }
}

/**
 * Claude WebFetch 工具调用
 */
export interface ClaudeWebFetchToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.WEB_FETCH
  input: {
    url: string
    prompt: string
  }
}

/**
 * Claude Task 工具调用
 */
export interface ClaudeTaskToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.TASK
  input: {
    description: string
    prompt: string
    subagent_type: string
  }
}

/**
 * Claude AskUserQuestion 工具调用
 */
export interface ClaudeAskUserQuestionToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.ASK_USER_QUESTION
  input: {
    questions: Array<{
      question: string
      header: string
      options: Array<{
        label: string
        description: string
      }>
      multiSelect: boolean
    }>
  }
}

/**
 * Claude NotebookEdit 工具调用
 */
export interface ClaudeNotebookEditToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.NOTEBOOK_EDIT
  input: {
    notebook_path: string
    new_source: string
    cell_id?: string
    cell_type?: string
    edit_mode?: string
  }
}

/**
 * Claude ExitPlanMode 工具调用
 */
export interface ClaudeExitPlanModeToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.EXIT_PLAN_MODE
  input: Record<string, any>
}

/**
 * Claude EnterPlanMode 工具调用
 */
export interface ClaudeEnterPlanModeToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.ENTER_PLAN_MODE
  input: Record<string, any>
}

/**
 * Claude Skill 工具调用
 */
export interface ClaudeSkillToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.SKILL
  input: {
    skill: string
  }
}

/**
 * Claude SlashCommand 工具调用
 */
export interface ClaudeSlashCommandToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.SLASH_COMMAND
  input: {
    command: string
  }
}

/**
 * Claude ListMcpResources 工具调用
 */
export interface ClaudeListMcpResourcesToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.LIST_MCP_RESOURCES
  input: {
    server?: string
  }
}

/**
 * Claude ReadMcpResource 工具调用
 */
export interface ClaudeReadMcpResourceToolCall extends BaseToolCall {
  toolType: typeof CLAUDE_TOOL_TYPE.READ_MCP_RESOURCE
  input: {
    server: string
    uri: string
  }
}

// ============ 其他工具类型 ============

/**
 * MCP 工具调用
 */
export interface McpToolCall extends BaseToolCall {
  toolType: typeof OTHER_TOOL_TYPE.MCP
  input: Record<string, any>
}

/**
 * 未知工具调用
 */
export interface UnknownToolCall extends BaseToolCall {
  toolType: typeof OTHER_TOOL_TYPE.UNKNOWN
  input: Record<string, any>
}

// ============ 联合类型 ============

/**
 * 工具调用联合类型
 */
export type ToolCall =
  | ClaudeReadToolCall
  | ClaudeWriteToolCall
  | ClaudeEditToolCall
  | ClaudeMultiEditToolCall
  | ClaudeTodoWriteToolCall
  | ClaudeBashToolCall
  | ClaudeBashOutputToolCall
  | ClaudeKillShellToolCall
  | ClaudeGrepToolCall
  | ClaudeGlobToolCall
  | ClaudeWebSearchToolCall
  | ClaudeWebFetchToolCall
  | ClaudeTaskToolCall
  | ClaudeAskUserQuestionToolCall
  | ClaudeNotebookEditToolCall
  | ClaudeExitPlanModeToolCall
  | ClaudeEnterPlanModeToolCall
  | ClaudeSkillToolCall
  | ClaudeSlashCommandToolCall
  | ClaudeListMcpResourcesToolCall
  | ClaudeReadMcpResourceToolCall
  | McpToolCall
  | UnknownToolCall

/**
 * 所有显示项的联合类型（使用 displayType 作为判别器）
 */
export type DisplayItem =
  | UserMessage
  | AssistantText
  | ThinkingContent
  | ToolCall
  | SystemMessage

// ============ 类型守卫 ============

/**
 * 判断是否为工具调用
 */
export function isToolCall(item: DisplayItem): item is ToolCall {
  return item.displayType === 'toolCall'
}

/**
 * 判断是否为用户消息
 */
export function isUserMessage(item: DisplayItem): item is UserMessage {
  return item.displayType === 'userMessage'
}

/**
 * 判断是否为助手文本
 */
export function isAssistantText(item: DisplayItem): item is AssistantText {
  return item.displayType === 'assistantText'
}

/**
 * 判断是否为思考内容
 */
export function isThinkingContent(item: DisplayItem): item is ThinkingContent {
  return item.displayType === 'thinking'
}

/**
 * 判断是否为系统消息
 */
export function isSystemMessage(item: DisplayItem): item is SystemMessage {
  return item.displayType === 'systemMessage'
}
