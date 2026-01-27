/**
 * 通知类型
 *
 * 翻译自: codex-agent-sdk/.../appserver/AppServerTypes.kt 行 497-631
 */

import type { ThreadInfo } from './thread'
import type { TurnInfo, TurnError } from './turn'
import type { ThreadItem } from './threadItem'

// ============== Thread/Turn 通知 ==============

export interface ThreadStartedNotification {
  thread: ThreadInfo
}

export interface TurnStartedNotification {
  threadId: string
  turn: TurnInfo
}

export interface TurnCompletedNotification {
  threadId: string
  turn: TurnInfo
}

export interface TurnDiffUpdatedNotification {
  threadId: string
  turnId: string
  diff: string
}

export type TurnPlanStepStatus = 'pending' | 'inProgress' | 'completed'

export interface TurnPlanStep {
  step: string
  status: TurnPlanStepStatus
}

export interface TurnPlanUpdatedNotification {
  threadId: string
  turnId: string
  explanation?: string
  plan: TurnPlanStep[]
}

// ============== Item 通知 ==============

export interface ItemStartedNotification {
  item: ThreadItem
  threadId: string
  turnId: string
}

export interface ItemCompletedNotification {
  item: ThreadItem
  threadId: string
  turnId: string
}

// ============== Delta 通知 ==============

export interface AgentMessageDeltaNotification {
  threadId: string
  turnId: string
  itemId: string
  delta: string
}

export interface ReasoningSummaryTextDeltaNotification {
  threadId: string
  turnId: string
  itemId: string
  delta: string
  summaryIndex: number
}

export interface ReasoningSummaryPartAddedNotification {
  threadId: string
  turnId: string
  itemId: string
  summaryIndex: number
}

export interface ReasoningTextDeltaNotification {
  threadId: string
  turnId: string
  itemId: string
  delta: string
  contentIndex: number
}

export interface CommandExecutionOutputDeltaNotification {
  threadId: string
  turnId: string
  itemId: string
  delta: string
}

export interface FileChangeOutputDeltaNotification {
  threadId: string
  turnId: string
  itemId: string
  delta: string
}

export interface McpToolCallProgressNotification {
  threadId: string
  turnId: string
  itemId: string
  message: string
}

export interface ContextCompactedNotification {
  threadId: string
  turnId: string
}

// ============== Error 通知 ==============

export interface ErrorNotification {
  error: TurnError
  willRetry: boolean
  threadId: string
  turnId: string
}

// ============== Token Usage ==============

export interface TokenUsageBreakdown {
  totalTokens: number
  inputTokens: number
  cachedInputTokens: number
  outputTokens: number
  reasoningOutputTokens: number
}

export interface ThreadTokenUsage {
  total: TokenUsageBreakdown
  last: TokenUsageBreakdown
  modelContextWindow?: number
}

export interface ThreadTokenUsageUpdatedNotification {
  threadId: string
  turnId: string
  tokenUsage: ThreadTokenUsage
}

// ============== Rate Limits ==============

export interface RateLimitInfo {
  usedPercent: number
  windowDurationMins: number
  resetsAt: number
}

export interface RateLimits {
  primary?: RateLimitInfo
  secondary?: RateLimitInfo
}

// ============== Account ==============

export interface AccountInfo {
  type: string // "apiKey" or "chatgpt"
  email?: string
  planType?: string
}

export interface AccountReadResult {
  account?: AccountInfo
  requiresOpenaiAuth: boolean
}

export interface AccountReadParams {
  refreshToken: boolean
}
