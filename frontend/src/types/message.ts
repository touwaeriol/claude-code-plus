/**
 * 统一消息类型定义（兼容旧字段名 role，同时对齐 ai-agent-sdk 数据）
 */

export type MessageRole = 'user' | 'assistant' | 'system' | 'result'

export interface MessageMetadata {
  model?: string
  usage?: UnifiedUsage
  sessionId?: string
  threadId?: string
  provider?: 'claude' | 'codex'
  raw?: unknown
}

export interface UnifiedMessage {
  id: string
  role: MessageRole
  timestamp: number
  content: ContentBlock[]
  metadata?: MessageMetadata
  isStreaming?: boolean
  tokenUsage?: UnifiedUsage
  /**
   * 是否是回放消息（用于区分压缩相关消息）
   * - isReplay = false: 压缩摘要（新生成的上下文）
   * - isReplay = true: 确认消息（如 "Compacted"）
   */
  isReplay?: boolean
  /**
   * 是否是压缩摘要消息
   * 当 isReplay = false 且内容以 "This session is being continued" 开头时为 true
   */
  isCompactSummary?: boolean
}

export interface UnifiedUsage {
  inputTokens?: number
  outputTokens?: number
  cachedInputTokens?: number
  provider?: 'claude' | 'codex'
  raw?: unknown
}

export type ContentStatus = 'in_progress' | 'completed' | 'failed'

// === 统一内容块定义 ===
export interface TextContent {
  type: 'text'
  text: string
}

// 图片内容块
export interface ImageContent {
  type: 'image'
  source: {
    type: 'base64' | 'url'
    media_type: string  // 例如 'image/png', 'image/jpeg'
    data?: string       // base64 编码的图片数据
    url?: string        // 图片 URL
  }
}

export interface ThinkingContent {
  type: 'thinking'
  thinking: string
  signature?: string
}

// 统一工具块（带 toolUseId 字段，兼容旧 tool_use_id 名称）
export interface ToolUseContent {
  type: 'tool_use'
  id: string
  /** 工具名称（如 "Read", "Write", "mcp__xxx"） */
  toolName: string
  /** 工具类型标识（如 "CLAUDE_READ", "CLAUDE_WRITE", "MCP"） */
  toolType?: string
  input?: unknown
  status?: ContentStatus
}

export interface ToolResultContent {
  type: 'tool_result'
  tool_use_id: string
  content?: unknown
  is_error?: boolean
}

export interface CommandExecutionContent {
  type: 'command_execution'
  command: string
  output?: string
  exitCode?: number
  status: ContentStatus
}

export interface FileChangeContent {
  type: 'file_change'
  changes: unknown[]
  status: ContentStatus
}

export interface McpToolCallContent {
  type: 'mcp_tool_call'
  server?: string
  tool?: string
  arguments?: unknown
  result?: unknown
  status: ContentStatus
}

export interface WebSearchContent {
  type: 'web_search'
  query: string
}

export interface TodoListContent {
  type: 'todo_list'
  items: Array<{ text: string; completed: boolean }>
}

export interface ErrorContent {
  type: 'error'
  message: string
}

// === 联合类型 ===
export type ContentBlock =
  | TextContent
  | ImageContent
  | ThinkingContent
  | ToolUseContent
  | ToolResultContent
  | CommandExecutionContent
  | FileChangeContent
  | McpToolCallContent
  | WebSearchContent
  | TodoListContent
  | ErrorContent

// === 兼容旧名称 ===
export type ToolUseBlock = ToolUseContent
export type ToolResultBlock = ToolResultContent
export type Message = UnifiedMessage
export type ImageBlock = ImageContent
export type TextBlock = TextContent
