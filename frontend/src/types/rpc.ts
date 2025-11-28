/**
 * RPC 类型定义
 *
 * 与后端 ai-agent-rpc-api/RpcModels.kt 完全对应
 * 所有 WebSocket 消息都应通过这些类型进行类型安全处理
 */

// ===== Provider & Enums =====

export type RpcProvider = 'claude' | 'codex'

export type RpcSessionStatus = 'connected' | 'disconnected' | 'interrupted' | 'model_changed'

export type RpcContentStatus = 'in_progress' | 'completed' | 'failed'

/** 权限模式枚举 */
export type RpcPermissionMode = 'default' | 'bypassPermissions' | 'acceptEdits' | 'plan' | 'dontAsk'

// ===== Stream Events (核心流式事件) =====

export interface RpcMessageStart {
  type: 'message_start'
  messageId: string
  content?: RpcContentBlock[]
  provider: RpcProvider
}

export interface RpcTextDelta {
  type: 'text_delta'
  text: string
  provider: RpcProvider
}

export interface RpcThinkingDelta {
  type: 'thinking_delta'
  thinking: string
  provider: RpcProvider
}

export interface RpcToolStart {
  type: 'tool_start'
  toolId: string
  toolName: string       // 显示名称: "Read", "Write", "mcp__xxx"
  toolType: string       // 类型标识: "CLAUDE_READ", "CLAUDE_WRITE", "MCP"
  inputPreview?: string
  provider: RpcProvider
}

export interface RpcToolProgress {
  type: 'tool_progress'
  toolId: string
  status: RpcContentStatus
  outputPreview?: string
  provider: RpcProvider
}

export interface RpcToolComplete {
  type: 'tool_complete'
  toolId: string
  result: RpcContentBlock
  provider: RpcProvider
}

export interface RpcMessageComplete {
  type: 'message_complete'
  usage?: RpcUsage
  provider: RpcProvider
}

export interface RpcResultMessage {
  type: 'result'
  duration_ms?: number
  duration_api_ms?: number
  is_error?: boolean
  num_turns?: number
  session_id?: string
  total_cost_usd?: number
  usage?: unknown
  result?: string | null
  provider: RpcProvider
}

export interface RpcErrorEvent {
  type: 'error'
  message: string
  provider: RpcProvider
}

/**
 * 完整的助手消息（用于校验流式响应）
 * 在流式增量响应结束后发送，包含完整的内容块列表
 */
export interface RpcAssistantMessage {
  type: 'assistant'
  content: RpcContentBlock[]
  provider: RpcProvider
}

export interface RpcUserMessage {
  type: 'user'
  content: RpcContentBlock[]
  provider: RpcProvider
}

/** 流式事件联合类型 */
export type RpcStreamEvent =
  | RpcMessageStart
  | RpcTextDelta
  | RpcThinkingDelta
  | RpcToolStart
  | RpcToolProgress
  | RpcToolComplete
  | RpcMessageComplete
  | RpcResultMessage
  | RpcUserMessage
  | RpcErrorEvent
  | RpcAssistantMessage

/** 流式事件类型字符串 */
export type RpcStreamEventType = RpcStreamEvent['type']

// ===== WebSocket 消息包装 =====

/** 流式数据包装 {"id":"req-1","type":"stream","data":{...}} */
export interface RpcStreamWrapper {
  id: string
  type: 'stream'
  data: RpcStreamEvent
}

/** 请求结果包装 {"id":"req-1","result":{...},"error":null} */
export interface RpcResultWrapper<T = unknown> {
  id: string
  result: T
  error: null
}

/** 错误包装 {"id":"req-1","result":null,"error":{...}} */
export interface RpcErrorWrapper {
  id: string
  result: null
  error: RpcErrorDetail
}

export interface RpcErrorDetail {
  code: number
  message: string
  data?: unknown
}

/** 完成包装 {"id":"req-1","type":"complete"} */
export interface RpcCompleteWrapper {
  id: string
  type: 'complete'
}

/** WebSocket 消息联合类型 */
export type RpcMessage =
  | RpcStreamWrapper
  | RpcResultWrapper
  | RpcErrorWrapper
  | RpcCompleteWrapper

// ===== Content Blocks (内容块) =====

export interface RpcTextBlock {
  type: 'text'
  text: string
}

export interface RpcThinkingBlock {
  type: 'thinking'
  thinking: string
  signature?: string
}

export interface RpcToolUseBlock {
  type: 'tool_use'
  id: string
  toolName: string       // 显示名称: "Read", "Write", "mcp__xxx"（原 name 字段）
  toolType: string       // 类型标识: "CLAUDE_READ", "CLAUDE_WRITE", "MCP"
  input?: unknown
  status?: RpcContentStatus
}

export interface RpcToolResultBlock {
  type: 'tool_result'
  tool_use_id: string
  content?: unknown
  is_error?: boolean
}

export interface RpcImageBlock {
  type: 'image'
  source: RpcImageSource
}

export interface RpcImageSource {
  type: string
  media_type: string
  data?: string
  url?: string
}

export interface RpcCommandExecutionBlock {
  type: 'command_execution'
  command: string
  output?: string
  exitCode?: number
  status: RpcContentStatus
}

export interface RpcFileChangeBlock {
  type: 'file_change'
  status: RpcContentStatus
  changes: RpcFileChange[]
}

export interface RpcFileChange {
  path: string
  kind: string
}

export interface RpcMcpToolCallBlock {
  type: 'mcp_tool_call'
  server?: string
  tool?: string
  arguments?: unknown
  result?: unknown
  status: RpcContentStatus
}

export interface RpcWebSearchBlock {
  type: 'web_search'
  query: string
}

export interface RpcTodoListBlock {
  type: 'todo_list'
  items: RpcTodoItem[]
}

export interface RpcTodoItem {
  text: string
  completed: boolean
}

export interface RpcErrorBlock {
  type: 'error'
  message: string
}

export interface RpcUnknownBlock {
  type: 'unknown'
  originalType: string
  data: unknown
}

/** 内容块联合类型 */
export type RpcContentBlock =
  | RpcTextBlock
  | RpcThinkingBlock
  | RpcToolUseBlock
  | RpcToolResultBlock
  | RpcImageBlock
  | RpcCommandExecutionBlock
  | RpcFileChangeBlock
  | RpcMcpToolCallBlock
  | RpcWebSearchBlock
  | RpcTodoListBlock
  | RpcErrorBlock
  | RpcUnknownBlock

// ===== Usage (Token 统计) =====

export interface RpcUsage {
  inputTokens?: number
  outputTokens?: number
  cachedInputTokens?: number
  provider?: RpcProvider
  raw?: unknown
}

// ===== Connect Options & Results =====

/**
 * 连接选项（统一扁平结构）
 *
 * 所有配置项都在顶层，根据 provider 能力决定哪些配置生效：
 * - Claude: permissionMode, dangerouslySkipPermissions, allowDangerouslySkipPermissions,
 *           includePartialMessages, continueConversation, thinkingEnabled
 * - Codex: baseUrl, apiKey, sandboxMode
 * - 通用: provider, model, systemPrompt, initialPrompt, sessionId, resumeSessionId, metadata
 */
export interface RpcConnectOptions {
  // === 通用配置 ===
  provider?: RpcProvider
  model?: string
  systemPrompt?: string
  initialPrompt?: string
  sessionId?: string
  resumeSessionId?: string
  metadata?: Record<string, string>

  // === Claude 相关配置（根据 provider 能力生效）===
  permissionMode?: 'default' | 'bypassPermissions' | 'acceptEdits' | 'plan' | 'dontAsk'
  dangerouslySkipPermissions?: boolean
  allowDangerouslySkipPermissions?: boolean
  includePartialMessages?: boolean
  continueConversation?: boolean
  thinkingEnabled?: boolean

  // === Codex 相关配置（根据 provider 能力生效）===
  baseUrl?: string
  apiKey?: string
  sandboxMode?: 'read-only' | 'workspace-write' | 'danger-full-access'
}

/** Agent 能力声明 */
export interface RpcCapabilities {
  canInterrupt: boolean
  canSwitchModel: boolean
  canSwitchPermissionMode: boolean
  supportedPermissionModes: RpcPermissionMode[]
  canSkipPermissions: boolean
  canSendRichContent: boolean
  canThink: boolean
  canResumeSession: boolean
}

export interface RpcConnectResult {
  sessionId: string
  provider: RpcProvider
  status: RpcSessionStatus
  model?: string
  capabilities?: RpcCapabilities
}

export interface RpcStatusResult {
  status: RpcSessionStatus
}

export interface RpcSetModelResult {
  status: RpcSessionStatus
  model: string
}

export interface RpcSetPermissionModeResult {
  mode: RpcPermissionMode
  success: boolean
}
