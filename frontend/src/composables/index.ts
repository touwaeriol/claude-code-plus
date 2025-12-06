/**
 * Composables 导出索引
 *
 * Tab 自治架构的核心 Composables:
 * - useSessionTab: 核心入口，组合其他 composables
 * - useSessionTools: 工具调用管理
 * - useSessionStats: 统计管理
 * - useSessionPermissions: 权限管理
 * - useSessionMessages: 消息处理
 */

// 核心入口
export { useSessionTab } from './useSessionTab'
export type { SessionTabInstance, TabInfo, UIState, TabConnectOptions } from './useSessionTab'

// 工具调用管理
export { useSessionTools } from './useSessionTools'
export type { SessionToolsInstance, ToolCallState } from './useSessionTools'

// 统计管理
export { useSessionStats } from './useSessionStats'
export type { SessionStatsInstance, RequestTrackerInfo, CumulativeStats } from './useSessionStats'

// 权限管理
export { useSessionPermissions } from './useSessionPermissions'
export type { SessionPermissionsInstance } from './useSessionPermissions'

// 消息处理
export { useSessionMessages } from './useSessionMessages'
export type { SessionMessagesInstance } from './useSessionMessages'

// 其他 composables
export { useI18n } from './useI18n'
export { useEnvironment } from './useEnvironment'
export { useKeyboardShortcuts } from './useKeyboardShortcuts'
