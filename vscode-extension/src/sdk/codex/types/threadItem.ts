/**
 * ThreadItem 类型 (sealed class 等价)
 *
 * 翻译自: codex-agent-sdk/.../appserver/AppServerTypes.kt 行 325-494
 */

import type { UserInput } from './turn'

// ============== Status 枚举 ==============

export type CommandExecutionStatus = 'inProgress' | 'completed' | 'failed' | 'declined'
export type PatchApplyStatus = 'inProgress' | 'completed' | 'failed' | 'declined'
export type McpToolCallStatus = 'inProgress' | 'completed' | 'failed'

// ============== PatchChangeKind ==============

export interface PatchChangeKindAdd {
  type: 'add'
}

export interface PatchChangeKindDelete {
  type: 'delete'
}

export interface PatchChangeKindUpdate {
  type: 'update'
  movePath?: string
}

export type PatchChangeKind = PatchChangeKindAdd | PatchChangeKindDelete | PatchChangeKindUpdate

export interface FileUpdateChange {
  path: string
  kind: PatchChangeKind
  diff: string
}

// ============== MCP Tool Call ==============

export interface McpToolCallResult {
  content: unknown[]
  structuredContent?: unknown
}

export interface McpToolCallError {
  message: string
}

// ============== CommandAction ==============

export interface CommandActionRead {
  type: 'read'
  command: string
  name: string
  path: string
}

export interface CommandActionListFiles {
  type: 'listFiles'
  command: string
  path?: string
}

export interface CommandActionSearch {
  type: 'search'
  command: string
  query?: string
  path?: string
}

export interface CommandActionUnknown {
  type: 'unknown'
  command: string
}

export type CommandAction =
  | CommandActionRead
  | CommandActionListFiles
  | CommandActionSearch
  | CommandActionUnknown

// ============== ThreadItem 类型 ==============

export interface ThreadItemBase {
  id: string
}

export interface ThreadItemUserMessage extends ThreadItemBase {
  type: 'userMessage'
  content: UserInput[]
}

export interface ThreadItemAgentMessage extends ThreadItemBase {
  type: 'agentMessage'
  text: string
}

export interface ThreadItemReasoning extends ThreadItemBase {
  type: 'reasoning'
  summary: string[]
  content: string[]
}

export interface ThreadItemCommandExecution extends ThreadItemBase {
  type: 'commandExecution'
  command: string
  cwd: string
  processId?: string
  status: CommandExecutionStatus
  commandActions: CommandAction[]
  aggregatedOutput?: string
  exitCode?: number
  durationMs?: number
}

export interface ThreadItemFileChange extends ThreadItemBase {
  type: 'fileChange'
  changes: FileUpdateChange[]
  status: PatchApplyStatus
}

export interface ThreadItemMcpToolCall extends ThreadItemBase {
  type: 'mcpToolCall'
  server: string
  tool: string
  status: McpToolCallStatus
  arguments: unknown
  result?: McpToolCallResult
  error?: McpToolCallError
  durationMs?: number
}

export interface ThreadItemWebSearch extends ThreadItemBase {
  type: 'webSearch'
  query: string
}

export interface ThreadItemImageView extends ThreadItemBase {
  type: 'imageView'
  path: string
}

export interface ThreadItemEnteredReviewMode extends ThreadItemBase {
  type: 'enteredReviewMode'
  review: string
}

export interface ThreadItemExitedReviewMode extends ThreadItemBase {
  type: 'exitedReviewMode'
  review: string
}

export type ThreadItem =
  | ThreadItemUserMessage
  | ThreadItemAgentMessage
  | ThreadItemReasoning
  | ThreadItemCommandExecution
  | ThreadItemFileChange
  | ThreadItemMcpToolCall
  | ThreadItemWebSearch
  | ThreadItemImageView
  | ThreadItemEnteredReviewMode
  | ThreadItemExitedReviewMode

export type ThreadItemType = ThreadItem['type']
