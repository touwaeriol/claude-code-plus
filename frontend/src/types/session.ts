/**
 * 会话状态类型定义
 */

import type { Message } from './message'
import type { DisplayItem, ToolCall, ConnectionStatus } from './display'
import type { ClaudeSession } from '@/services/ClaudeSession'

/**
 * UI 状态（用于切换会话时保存/恢复）
 */
export interface UIState {
  inputText: string
  contexts: any[]  // ContextReference[]
  scrollPosition: number
}

/**
 * 会话状态
 *
 * 包含会话的所有数据：
 * - 基本信息（id, name, 时间戳）
 * - 原始数据（messages - 来自后端，用于持久化）
 * - ViewModel（displayItems - 用于 UI 展示）
 * - 运行时状态（pendingToolCalls - 用于响应式更新）
 * - 连接信息（connection, connectionStatus, modelId）
 * - 执行状态（isGenerating - 决定是否显示在 Tab 上）
 * - UI 状态（uiState - 用于切换会话时保存/恢复）
 */
export interface SessionState {
  // 基本信息
  id: string
  name: string
  createdAt: number
  updatedAt: number
  lastActiveAt: number  // 最后活跃时间（用于历史列表排序）
  order: number  // Tab显示顺序（用于拖拽排序）

  // 原始数据（来自后端，用于持久化）
  messages: Message[]

  // ViewModel（用于 UI 展示）
  displayItems: DisplayItem[]

  // 运行中的工具调用（用于响应式更新）
  pendingToolCalls: Map<string, ToolCall>

  // 连接状态
  connectionStatus: ConnectionStatus
  modelId: string | null

  // RPC 连接实例（不可序列化，不持久化）
  connection: ClaudeSession | null

  // 执行状态（是否正在生成）
  isGenerating: boolean

  // UI 状态（用于切换会话时保存/恢复）
  uiState: UIState
}

/**
 * 可序列化的会话数据（用于持久化）
 */
export interface SerializableSessionData {
  id: string
  name: string
  createdAt: number
  updatedAt: number
  messages: Message[]
  modelId: string | null
}

