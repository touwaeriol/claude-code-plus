/**
 * RPC 类型定义 - 完全对齐 Claude Agent SDK
 *
 * 与后端 ai-agent-rpc-api/RpcModels.kt 完全对应
 * 所有 WebSocket 消息都应通过这些类型进行类型安全处理
 */

// ============================================================================
// Provider & Enums
// ============================================================================

export type RpcProvider = 'claude' | 'codex'

export type RpcSessionStatus = 'connected' | 'disconnected' | 'interrupted' | 'model_changed'

export type RpcContentStatus = 'in_progress' | 'completed' | 'failed'

/** 权限模式枚举 */
export type RpcPermissionMode = 'default' | 'bypassPermissions' | 'acceptEdits' | 'plan' | 'dontAsk'

// ============================================================================
// RPC 消息类型 - 对应 Claude SDK Message
// ============================================================================

/** 消息类型枚举 */
export type RpcMessageType = 'user' | 'assistant' | 'result' | 'stream_event' | 'error' | 'status_system' | 'compact_boundary'

/** 消息元数据（历史回放、排序用） */
export interface RpcMessageMetadata {
  /** 历史文件中的顺序序号（从 0 开始） */
  replaySeq?: number
  /** 历史加载的起始 offset */
  historyStart?: number
  /** 历史总条数 */
  historyTotal?: number
  /** 是否需要在 UI 中展示 */
  isDisplayable?: boolean
  /** 历史消息唯一标识（用于去重/排序） */
  messageId?: string
}

/** 基础消息接口 */
interface RpcMessageBase {
  provider: RpcProvider
  metadata?: RpcMessageMetadata
}

/** 用户消息 - 对应 Claude SDK UserMessage */
export interface RpcUserMessage extends RpcMessageBase {
  type: 'user'
  message: RpcMessageContent
  parent_tool_use_id?: string
  /**
   * 是否是回放消息（用于区分压缩摘要和确认消息）
   * - isReplay = false: 压缩摘要（新生成的上下文）
   * - isReplay = true: 确认消息（如 "Compacted"）
   */
  isReplay?: boolean
}

/** 助手消息 - 对应 Claude SDK AssistantMessage */
export interface RpcAssistantMessage extends RpcMessageBase {
  type: 'assistant'
  message: RpcMessageContent
}

/** 结果消息 - 对应 Claude SDK ResultMessage */
export interface RpcResultMessage extends RpcMessageBase {
  type: 'result'
  subtype: string
  duration_ms?: number
  duration_api_ms?: number
  is_error: boolean
  num_turns: number
  session_id?: string
  total_cost_usd?: number
  usage?: unknown
  result?: string | null
}

/** 流式事件 - 对应 Claude SDK StreamEvent */
export interface RpcStreamEvent extends RpcMessageBase {
  type: 'stream_event'
  uuid: string
  session_id: string
  event: RpcStreamEventData
  parent_tool_use_id?: string
}

/** 错误消息 */
export interface RpcErrorMessage extends RpcMessageBase {
  type: 'error'
  message: string
}

// ============================================================================
// 压缩相关系统消息
// ============================================================================

/** 压缩元数据 */
export interface RpcCompactMetadata {
  trigger?: 'manual' | 'auto'  // 触发方式
  pre_tokens?: number          // 压缩前的 token 数
}

/** 状态系统消息 - 用于通知客户端状态变化（如 compacting） */
export interface RpcStatusSystemMessage extends RpcMessageBase {
  type: 'status_system'
  subtype: 'status'
  status: string | null  // 如 "compacting" 或 null
  session_id: string
  uuid?: string
}

/** 压缩边界消息 - 标记会话压缩的边界 */
export interface RpcCompactBoundaryMessage extends RpcMessageBase {
  type: 'compact_boundary'
  subtype: 'compact_boundary'
  session_id: string
  uuid?: string
  compact_metadata?: RpcCompactMetadata
}

/** 所有 RPC 消息联合类型 */
export type RpcMessage =
  | RpcUserMessage
  | RpcAssistantMessage
  | RpcResultMessage
  | RpcStreamEvent
  | RpcErrorMessage
  | RpcStatusSystemMessage
  | RpcCompactBoundaryMessage

/** 消息内容（assistant/user 消息的 message 字段） */
export interface RpcMessageContent {
  content: RpcContentBlock[]
  model?: string
}

// ============================================================================
// 流式事件数据 - 对应 Anthropic API 流事件
// ============================================================================

/** 流式事件类型枚举 */
export type RpcStreamEventType =
  | 'message_start'
  | 'content_block_start'
  | 'content_block_delta'
  | 'content_block_stop'
  | 'message_delta'
  | 'message_stop'

/** 流式事件数据基础接口 */
interface RpcStreamEventDataBase {
  type: RpcStreamEventType
}

export interface RpcMessageStartEvent extends RpcStreamEventDataBase {
  type: 'message_start'
  message?: RpcMessageStartInfo
}

export interface RpcMessageStartInfo {
  id?: string
  model?: string
  content?: RpcContentBlock[]
}

export interface RpcContentBlockStartEvent extends RpcStreamEventDataBase {
  type: 'content_block_start'
  index: number
  content_block: RpcContentBlock
}

export interface RpcContentBlockDeltaEvent extends RpcStreamEventDataBase {
  type: 'content_block_delta'
  index: number
  delta: RpcDelta
}

export interface RpcContentBlockStopEvent extends RpcStreamEventDataBase {
  type: 'content_block_stop'
  index: number
}

export interface RpcMessageDeltaEvent extends RpcStreamEventDataBase {
  type: 'message_delta'
  delta?: unknown
  usage?: RpcUsage
}

export interface RpcMessageStopEvent extends RpcStreamEventDataBase {
  type: 'message_stop'
}

/** 流式事件数据联合类型 */
export type RpcStreamEventData =
  | RpcMessageStartEvent
  | RpcContentBlockStartEvent
  | RpcContentBlockDeltaEvent
  | RpcContentBlockStopEvent
  | RpcMessageDeltaEvent
  | RpcMessageStopEvent

// ============================================================================
// Delta 类型 - 内容块增量更新
// ============================================================================

/** Delta 类型枚举 */
export type RpcDeltaType = 'text_delta' | 'thinking_delta' | 'input_json_delta'

export interface RpcTextDelta {
  type: 'text_delta'
  text: string
}

export interface RpcThinkingDelta {
  type: 'thinking_delta'
  thinking: string
}

export interface RpcInputJsonDelta {
  type: 'input_json_delta'
  partial_json: string
}

/** Delta 联合类型 */
export type RpcDelta = RpcTextDelta | RpcThinkingDelta | RpcInputJsonDelta

// ============================================================================
// 类型守卫函数
// ============================================================================

/** 检查是否为用户消息 */
export function isUserMessage(msg: RpcMessage): msg is RpcUserMessage {
  return msg.type === 'user'
}

/** 检查是否为助手消息 */
export function isAssistantMessage(msg: RpcMessage): msg is RpcAssistantMessage {
  return msg.type === 'assistant'
}

/** 检查是否为结果消息 */
export function isResultMessage(msg: RpcMessage): msg is RpcResultMessage {
  return msg.type === 'result'
}

/** 检查是否为流式事件 */
export function isStreamEvent(msg: RpcMessage): msg is RpcStreamEvent {
  return msg.type === 'stream_event'
}

/** 检查是否为错误消息 */
export function isErrorMessage(msg: RpcMessage): msg is RpcErrorMessage {
  return msg.type === 'error'
}

// ===== 流式事件子类型守卫 =====

export function isMessageStartEvent(event: RpcStreamEventData): event is RpcMessageStartEvent {
  return event.type === 'message_start'
}

export function isContentBlockStartEvent(event: RpcStreamEventData): event is RpcContentBlockStartEvent {
  return event.type === 'content_block_start'
}

export function isContentBlockDeltaEvent(event: RpcStreamEventData): event is RpcContentBlockDeltaEvent {
  return event.type === 'content_block_delta'
}

export function isContentBlockStopEvent(event: RpcStreamEventData): event is RpcContentBlockStopEvent {
  return event.type === 'content_block_stop'
}

export function isMessageDeltaEvent(event: RpcStreamEventData): event is RpcMessageDeltaEvent {
  return event.type === 'message_delta'
}

export function isMessageStopEvent(event: RpcStreamEventData): event is RpcMessageStopEvent {
  return event.type === 'message_stop'
}

// ===== Delta 子类型守卫 =====

export function isTextDelta(delta: RpcDelta): delta is RpcTextDelta {
  return delta.type === 'text_delta'
}

export function isThinkingDelta(delta: RpcDelta): delta is RpcThinkingDelta {
  return delta.type === 'thinking_delta'
}

export function isInputJsonDelta(delta: RpcDelta): delta is RpcInputJsonDelta {
  return delta.type === 'input_json_delta'
}

// ============================================================================
// WebSocket 消息包装（JSON-RPC 协议层）
// ============================================================================

/** 流式数据包装 {"id":"req-1","type":"stream","data":{...}} */
export interface RpcStreamWrapper {
  id: string
  type: 'stream'
  data: RpcMessage
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

/** WebSocket 消息联合类型（JSON-RPC 协议层） */
export type RpcWebSocketMessage =
  | RpcStreamWrapper
  | RpcResultWrapper
  | RpcErrorWrapper
  | RpcCompleteWrapper

// ============================================================================
// Content Blocks (内容块)
// ============================================================================

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
  type: 'base64' | 'url' | string
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

// ============================================================================
// Usage (Token 统计)
// ============================================================================

export interface RpcUsage {
  inputTokens?: number
  outputTokens?: number
  cachedInputTokens?: number
  provider?: RpcProvider
  raw?: unknown
}

// ============================================================================
// Connect Options & Results
// ============================================================================

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
  resume?: string  // 恢复会话 ID（别名）
  metadata?: Record<string, string>

  // === Claude 相关配置（根据 provider 能力生效）===
  permissionMode?: RpcPermissionMode
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
  cwd?: string
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

// ============================================================================
// 应用层流式事件类型 - 用于 rpcEventProcessor.ts
// ============================================================================

/** message_start 事件 */
export interface RpcMessageStart {
  type: 'message_start'
  messageId?: string
  content?: RpcContentBlock[]
  provider?: RpcProvider
}

/** tool_start 事件 */
export interface RpcToolStart {
  type: 'tool_start'
  toolId: string
  toolName: string
  toolType: string
  provider?: RpcProvider
}

/** tool_progress 事件 */
export interface RpcToolProgress {
  type: 'tool_progress'
  toolId: string
  status?: string
  outputPreview?: string
  provider?: RpcProvider
}

/** tool_complete 事件 */
export interface RpcToolComplete {
  type: 'tool_complete'
  toolId: string
  result?: RpcContentBlock
  provider?: RpcProvider
}

/** message_complete 事件 */
export interface RpcMessageComplete {
  type: 'message_complete'
  usage?: RpcUsage
  provider?: RpcProvider
}

/** error 事件 */
export interface RpcErrorEvent {
  type: 'error'
  message: string
  provider?: RpcProvider
}

/** 应用层流式事件联合类型 - 用于 processRpcStreamEvent */
export type RpcAppStreamEvent =
  | RpcMessageStart
  | RpcTextDelta
  | RpcThinkingDelta
  | RpcToolStart
  | RpcToolProgress
  | RpcToolComplete
  | RpcMessageComplete
  | RpcErrorEvent
  | (RpcAssistantMessage & { content: RpcContentBlock[] })
  | (RpcUserMessage & { content?: RpcContentBlock[] })
