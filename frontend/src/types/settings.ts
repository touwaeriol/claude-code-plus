/**
 * 设置类型定义
 */

/**
 * 权限模式
 */
export enum PermissionMode {
  DEFAULT = 'default',           // 默认模式 - 需要用户确认
  ACCEPT_EDITS = 'accept_edits', // 自动接受编辑
  PLAN = 'plan',                 // 计划模式
  BYPASS_PERMISSIONS = 'bypass', // 绕过权限检查
  DONT_ASK = 'dontAsk'           // 不询问模式
}

/**
 * 模型类型
 */
export enum ModelType {
  SONNET = 'sonnet',       // Claude Sonnet
  OPUS = 'opus',           // Claude Opus
  HAIKU = 'haiku',         // Claude Haiku
  SONNET_45 = 'sonnet-4-5' // Claude Sonnet 4.5
}

/**
 * 设置配置
 */
export interface Settings {
  // 模型配置
  model: ModelType

  // 权限模式
  permissionMode: PermissionMode

  // 会话控制
  systemPrompt: string | null  // 系统提示词
  maxTurns: number | null
  continueConversation: boolean

  // 高级选项
  maxTokens: number | null
  maxThinkingTokens: number
  temperature: number | null

  // 调试选项
  verbose: boolean

  // API 配置
  apiKey: string | null
}

/**
 * 默认设置
 */
export const DEFAULT_SETTINGS: Settings = {
  model: ModelType.SONNET,
  permissionMode: PermissionMode.DEFAULT,
  systemPrompt: null,  // 默认使用 claude_code 提示词
  maxTurns: 10,
  continueConversation: false,
  maxTokens: null,
  maxThinkingTokens: 8000,
  temperature: null,
  verbose: false,
  apiKey: null
}

/**
 * 权限模式显示名称
 */
export const PERMISSION_MODE_LABELS: Record<PermissionMode, string> = {
  [PermissionMode.DEFAULT]: '默认 - 需要确认',
  [PermissionMode.ACCEPT_EDITS]: '自动接受编辑',
  [PermissionMode.PLAN]: '计划模式',
  [PermissionMode.BYPASS_PERMISSIONS]: '绕过权限检查',
  [PermissionMode.DONT_ASK]: '不询问模式'
}

/**
 * 模型显示名称
 */
export const MODEL_LABELS: Record<ModelType, string> = {
  [ModelType.SONNET]: 'Claude 3.5 Sonnet',
  [ModelType.OPUS]: 'Claude 3 Opus',
  [ModelType.HAIKU]: 'Claude 3 Haiku',
  [ModelType.SONNET_45]: 'Claude 4.5 Sonnet'
}

/**
 * 权限模式描述
 */
export const PERMISSION_MODE_DESCRIPTIONS: Record<PermissionMode, string> = {
  [PermissionMode.DEFAULT]: '每个工具调用都需要用户确认',
  [PermissionMode.ACCEPT_EDITS]: '自动接受文件编辑操作',
  [PermissionMode.PLAN]: '先制定计划,再执行操作',
  [PermissionMode.BYPASS_PERMISSIONS]: '自动执行所有操作(谨慎使用)',
  [PermissionMode.DONT_ASK]: '不询问直接执行'
}
