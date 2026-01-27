/**
 * Thread 相关类型
 *
 * 翻译自: codex-agent-sdk/.../appserver/AppServerTypes.kt 行 57-186
 */

import type { TurnInfo } from './turn'

// ============== 初始化 ==============

export interface ClientInfo {
  name: string
  title?: string
  version: string
}

export interface InitializeParams {
  clientInfo: ClientInfo
}

export interface InitializeResult {
  userAgent?: string
}

// ============== Thread 相关 ==============

export type SessionSource = 'cli' | 'vscode' | 'exec' | 'appServer' | 'unknown'

export interface GitInfo {
  sha?: string
  branch?: string
  originUrl?: string
}

export interface ThreadInfo {
  id: string
  preview: string
  modelProvider: string
  createdAt: number
  path: string
  cwd: string
  cliVersion: string
  source: SessionSource
  gitInfo?: GitInfo
  turns: TurnInfo[]
}

export interface ThreadStartParams {
  model?: string
  modelProvider?: string
  cwd?: string
  approvalPolicy?: string
  sandbox?: string
  config?: Record<string, unknown>
  baseInstructions?: string
  developerInstructions?: string
}

export interface ThreadStartResult {
  thread: ThreadInfo
}

export interface ThreadResumeParams {
  threadId: string
}

export interface ThreadArchiveParams {
  threadId: string
}

export interface ThreadListParams {
  cursor?: string
  limit?: number
  modelProviders?: string[]
}

export interface ThreadListResult {
  data: ThreadInfo[]
  nextCursor?: string
}

// ============== Models ==============

export interface ReasoningEffortOption {
  reasoningEffort: string
  description: string
}

export interface ModelInfo {
  id: string
  model: string
  displayName: string
  description: string
  supportedReasoningEfforts: ReasoningEffortOption[]
  defaultReasoningEffort?: string
  isDefault: boolean
}

export interface ModelListParams {
  cursor?: string
  limit?: number
}

export interface ModelListResponse {
  data: ModelInfo[]
  nextCursor?: string
}
