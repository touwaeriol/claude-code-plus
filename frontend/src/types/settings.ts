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

import { BaseModel, UiModelOption, MODEL_CAPABILITIES } from '@/constants/models'

/**
 * 设置配置
 */
export interface Settings {
  // 模型配置（新架构：使用 BaseModel）
  model: BaseModel

  // 权限模式
  permissionMode: PermissionMode

  // 会话控制
  systemPrompt: string | null  // 系统提示词
  maxTurns: number | null
  continueConversation: boolean

  // 高级选项
  maxTokens: number | null
  /**
   * 是否启用扩展思考
   *
   * true: 根据 Claude settings 中的 env.MAX_THINKING_TOKENS 或默认值 1024 计算思考 token 预算
   * false: 强制关闭思考（服务端会将 maxThinkingTokens 视为 0）
   */
  thinkingEnabled: boolean

  /**
   * 思考令牌预算（仅用于显示与后端同步，不再由前端直接控制）
   *
   * 实际值由服务端根据 thinkingEnabled + Claude settings 合成，
   * 前端保存该值是为了在设置面板中展示当前配置结果。
   */
  maxThinkingTokens: number
  temperature: number | null

  // 调试选项
  verbose: boolean

  // API 配置
  apiKey: string | null

  // 跳过权限确认（dangerouslySkipPermissions）
  skipPermissions: boolean

  // 流式输出时包含部分消息（用于实时 token 用量信息）
  includePartialMessages: boolean
}

/**
 * 默认设置
 */
export const DEFAULT_SETTINGS: Settings = {
  model: BaseModel.OPUS_45,
  permissionMode: PermissionMode.DEFAULT,
  systemPrompt: null,  // 默认使用 claude_code 提示词
  maxTurns: 10,
  continueConversation: false,
  maxTokens: null,
  thinkingEnabled: MODEL_CAPABILITIES[BaseModel.OPUS_45].defaultThinkingEnabled,  // 根据模型默认值
  maxThinkingTokens: 8000,
  temperature: null,
  verbose: false,
  apiKey: null,
  skipPermissions: false,  // 默认不跳过权限检查，需要用户授权
  includePartialMessages: true  // 默认包含部分消息
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
 * 模型显示名称（新架构：使用 BaseModel）
 */
export const MODEL_LABELS: Record<BaseModel, string> = {
  [BaseModel.OPUS_45]: MODEL_CAPABILITIES[BaseModel.OPUS_45].displayName,
  [BaseModel.SONNET_45]: MODEL_CAPABILITIES[BaseModel.SONNET_45].displayName,
  [BaseModel.HAIKU_45]: MODEL_CAPABILITIES[BaseModel.HAIKU_45].displayName,
}

/**
 * @deprecated 旧模型显示名称，使用 MODEL_LABELS 代替
 */
export const LEGACY_MODEL_LABELS: Record<UiModelOption, string> = {
  [UiModelOption.SONNET_45]: 'Sonnet 4.5',
  [UiModelOption.SONNET_45_THINKING]: 'Sonnet 4.5 (Thinking)',
  [UiModelOption.OPUS_45]: 'Opus 4.5',
  [UiModelOption.OPUS_45_THINKING]: 'Opus 4.5 (Thinking)',
  [UiModelOption.HAIKU_45]: 'Haiku 4.5',
  [UiModelOption.HAIKU_45_THINKING]: 'Haiku 4.5 (Thinking)'
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
