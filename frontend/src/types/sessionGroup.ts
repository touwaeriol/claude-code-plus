/**
 * 会话分组和标签类型定义
 */

export interface SessionGroup {
  id: string
  name: string
  description?: string
  color: string
  icon?: string
  parentId?: string // 支持嵌套分组
  order: number
  isCollapsed: boolean
}

export interface SessionTag {
  id: string
  name: string
  color: string
  description?: string
}

export enum SessionStatus {
  ACTIVE = 'ACTIVE',
  INTERRUPTED = 'INTERRUPTED',
  COMPLETED = 'COMPLETED',
  ARCHIVED = 'ARCHIVED',
  LOADING = 'LOADING',
  ERROR = 'ERROR'
}

// 扩展 Session 类型以包含分组和标签
export interface SessionWithGrouping {
  id: string
  name: string
  timestamp: number
  groupId?: string
  tags: SessionTag[]
  status: SessionStatus
  summary?: string
}

// 预定义的颜色
export const GROUP_COLORS = [
  '#1976D2', // 蓝色
  '#388E3C', // 绿色
  '#F57C00', // 橙色
  '#7B1FA2', // 紫色
  '#C62828', // 红色
  '#0097A7', // 青色
  '#5D4037', // 棕色
  '#455A64'  // 灰蓝色
]

export const TAG_COLORS = [
  '#2196F3', // 亮蓝色
  '#4CAF50', // 亮绿色
  '#FF9800', // 亮橙色
  '#9C27B0', // 亮紫色
  '#F44336', // 亮红色
  '#00BCD4', // 亮青色
  '#795548', // 亮棕色
  '#607D8B'  // 亮灰蓝色
]

// 预定义的图标
export const GROUP_ICONS = [
  '📁', '📂', '📊', '📈', '📉', '📋', '📌', '📍',
  '🎯', '🎨', '🎭', '🎪', '🎬', '🎮', '🎲', '🎰',
  '💼', '💻', '💡', '💬', '💭', '💾', '💿', '📀',
  '🔧', '🔨', '🔩', '🔪', '🔫', '🔬', '🔭', '🔮'
]

