/**
 * settingsStore 类型定义
 * 
 * 提取自 settingsStore.ts 以减少文件大小
 */

import type {
  BackendType,
  CodexReasoningEffort,
  CodexReasoningSummary,
  SandboxMode,
  ClaudeBackendConfig,
  CodexBackendConfig
} from '@/types/backend'
import type { IdeSettings as BaseIdeSettings, OptionConfig } from '@/services/ideaRSocket'

/**
 * 多后端设置接口
 */
export interface Settings {
  // === 通用设置 ===
  defaultBackendType: BackendType           // 默认后端类型
  permissionMode: string                    // 权限模式（通用）
  skipPermissions: boolean                  // Bypass 模式（通用）
  includePartialMessages: boolean           // 包含部分消息（通用）
  maxTurns: number | null                   // 最大轮次（通用）

  // === 会话默认设置 ===
  claudeDefaultAutoCleanupContexts: boolean // Claude 默认自动清理上下文
  codexDefaultAutoCleanupContexts: boolean  // Codex 默认自动清理上下文

  // === 后端配置对象 ===
  claudeConfig: ClaudeBackendConfig         // Claude 后端配置
  codexConfig: CodexBackendConfig           // Codex 后端配置

  // === Claude 特定设置（向后兼容，将迁移到 claudeConfig）===
  claudeModel: string                       // Claude 模型 ID
  claudeThinkingEnabled: boolean | null     // 是否启用思考（null = 从后端获取）
  claudeThinkingTokens: number | null       // 思考 token 预算（null = 从后端获取）

  // === Codex 特定设置（向后兼容，将迁移到 codexConfig）===
  codexModel: string                        // Codex 模型 ID
  codexReasoningEffort: CodexReasoningEffort | null  // 推理努力级别
  codexReasoningSummary: 'auto' | 'concise' | 'detailed' | 'none'  // 推理总结模式
  codexSandboxMode: SandboxMode             // 沙盒模式
  codexApiKey?: string                      // Codex API Key（可选）

  // === 兼容旧版本（迁移后可移除）===
  model?: string                            // @deprecated 使用 claudeModel
  thinkingEnabled?: boolean                 // @deprecated 使用 claudeThinkingEnabled
  maxThinkingTokens?: number                // @deprecated 使用 claudeThinkingTokens

  // === 高级设置 ===
  systemPrompt: string | null               // 系统提示词
  continueConversation: boolean             // 继续对话
  maxTokens: number | null                  // 最大 token 数
  temperature: number | null                // 温度
  verbose: boolean                          // 详细输出
}

/**
 * 扩展的 IDE 设置接口（支持多后端）
 */
export interface IdeSettings extends BaseIdeSettings {
  // === 通用 ===
  defaultBackendType?: BackendType
  permissionMode?: string  // 传输时使用字符串，会转换为 PermissionMode
  defaultBypassPermissions?: boolean
  includePartialMessages?: boolean

  // === Claude 特定 ===
  claudeDefaultModelId?: string
  claudeThinkingLevelId?: string
  claudeThinkingTokens?: number
  claudeThinkingLevels?: Array<{ levelId: string; tokens: number }>
  claudeDefaultAutoCleanupContexts?: boolean

  // === Codex 特定 ===
  codexDefaultModelId?: string
  codexDefaultReasoningEffort?: CodexReasoningEffort
  codexDefaultReasoningSummary?: CodexReasoningSummary
  codexDefaultSandboxMode?: SandboxMode
  codexDefaultAutoCleanupContexts?: boolean
  codexReasoningEffort?: CodexReasoningEffort
  codexReasoningSummary?: CodexReasoningSummary
  codexSandboxMode?: SandboxMode
  codexApiKey?: string

  // === 兼容旧版本 ===
  defaultModelId?: string
  defaultThinkingLevelId?: string
  defaultThinkingTokens?: number

  // === 配置选项列表（由后端动态返回）===
  codexReasoningEffortOptions?: OptionConfig[]  // Codex 推理努力级别选项
  codexReasoningSummaryOptions?: OptionConfig[] // Codex 推理总结模式选项
  codexSandboxModeOptions?: OptionConfig[]      // Codex 沙盒模式选项
  permissionModeOptions?: OptionConfig[]        // 权限模式选项
}

/**
 * HTTP 获取的默认设置（用于浏览器模式）
 */
export interface HttpDefaultSettings {
  // 通用
  defaultBackendType?: BackendType
  defaultBypassPermissions: boolean
  includePartialMessages: boolean

  // Claude 配置
  claudeDefaultModelId?: string
  claudeDefaultThinkingLevel?: string
  claudeDefaultThinkingTokens?: number
  claudeDefaultAutoCleanupContexts?: boolean

  // Codex 配置
  codexDefaultModelId?: string
  codexReasoningEffort?: CodexReasoningEffort
  codexReasoningSummary?: CodexReasoningSummary
  codexSandboxMode?: SandboxMode
  codexDefaultAutoCleanupContexts?: boolean
  defaultThinkingLevel?: string
  defaultThinkingTokens?: number
}

/**
 * 默认设置常量
 */
export { DEFAULT_CLAUDE_CONFIG, DEFAULT_CODEX_CONFIG } from '@/types/backend'
