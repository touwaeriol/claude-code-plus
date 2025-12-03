/**
 * 会话状态类型定义
 *
 * 设计原则：每个 SessionState 拥有自己的连接和状态，不通过 sessionId 查找
 */

import type { UnifiedMessage, ContentBlock } from './message'
import type { DisplayItem, ToolCall, ConnectionStatus, ContextReference } from './display'
import type { AiAgentSession } from '@/services/AiAgentSession'
import type { RpcCapabilities, RpcPermissionMode } from '@/types/rpc'

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
 * 每个会话拥有：
 * - 基本信息（id, name, 时间戳）
 * - 原始数据（messages - 来自后端，用于持久化）
 * - ViewModel（displayItems - 用于 UI 展示）
 * - 运行时状态（pendingToolCalls - 用于响应式更新）
 * - 连接实例（session - 直接持有 AiAgentSession）
 * - 连接状态（connectionStatus, modelId）
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
  messages: UnifiedMessage[]

  // ViewModel（用于 UI 展示）
  displayItems: DisplayItem[]

  // 运行中的工具调用（用于响应式更新）
  pendingToolCalls: Map<string, ToolCall>

  // WebSocket 连接实例（每个会话持有自己的连接）
  session: AiAgentSession | null

  // 连接状态
  connectionStatus: ConnectionStatus
  modelId: string | null

  // Agent 能力信息（connect 时获取）
  capabilities: RpcCapabilities | null

  // 当前权限模式
  permissionMode: RpcPermissionMode

  // 是否跳过权限（连接时确定，切换需要重连）
  skipPermissions: boolean

  // 是否启用思考（连接时确定，切换需要重连）
  thinkingEnabled: boolean

  // 执行状态（是否正在生成）
  isGenerating: boolean

  // UI 状态（用于切换会话时保存/恢复）
  uiState: UIState

  // 工具输入 JSON 累积器（每个会话独立）
  toolInputJsonAccumulator: Map<string, string>

  // 最后一次错误信息（用于在输入框中显示）
  lastError: string | null
}

/**
 * 可序列化的会话数据（用于持久化）
 */
export interface SerializableSessionData {
  id: string
  name: string
  createdAt: number
  updatedAt: number
  messages: UnifiedMessage[]
  modelId: string | null
}

/**
 * 待发送消息（用于消息队列）
 *
 * 当用户在 AI 生成中发送消息时，消息会被加入队列等待发送
 * - contexts: 上下文栏引用（文件、图片等）
 * - contents: 输入框内容（文本块、图片块等 ContentBlock）
 */
export interface PendingMessage {
  id: string                        // 唯一标识（用于删除/编辑）
  contexts: ContextReference[]      // 上下文栏引用（文件、图片）
  contents: ContentBlock[]          // 输入框内容（文本块、图片块）
  createdAt: number                 // 创建时间
}

// 兼容别名
export type Session = SessionState
