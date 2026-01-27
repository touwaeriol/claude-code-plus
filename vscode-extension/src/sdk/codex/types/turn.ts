/**
 * Turn 相关类型
 *
 * 翻译自: codex-agent-sdk/.../appserver/AppServerTypes.kt 行 223-325
 */

import type { ThreadItem } from './threadItem'

// ============== UserInput ==============

export interface UserInputText {
  type: 'text'
  text: string
}

export interface UserInputImage {
  type: 'image'
  url: string
}

export interface UserInputLocalImage {
  type: 'localImage'
  path: string
}

export type UserInput = UserInputText | UserInputImage | UserInputLocalImage

// ============== SandboxPolicy ==============

export interface SandboxPolicyDangerFullAccess {
  type: 'dangerFullAccess'
}

export interface SandboxPolicyReadOnly {
  type: 'readOnly'
}

export interface SandboxPolicyWorkspaceWrite {
  type: 'workspaceWrite'
  writableRoots?: string[]
  networkAccess?: boolean
  excludeTmpdirEnvVar?: boolean
}

export type SandboxPolicy =
  | SandboxPolicyDangerFullAccess
  | SandboxPolicyReadOnly
  | SandboxPolicyWorkspaceWrite

export type ReasoningSummary = 'auto' | 'concise' | 'detailed' | 'none'

// ============== Turn ==============

export interface TurnStartParams {
  threadId: string
  input: UserInput[]
  cwd?: string
  approvalPolicy?: string
  sandboxPolicy?: SandboxPolicy
  model?: string
  effort?: string
  summary?: ReasoningSummary
}

export interface TurnStartResult {
  turn: TurnInfo
}

export type TurnStatus = 'completed' | 'interrupted' | 'failed' | 'inProgress'

export interface TurnError {
  message: string
  codexErrorInfo?: unknown
}

export interface TurnInfo {
  id: string
  status: TurnStatus
  items: ThreadItem[]
  error?: TurnError
}

export interface TurnInterruptParams {
  threadId: string
  turnId: string
}
