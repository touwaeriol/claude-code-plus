/**
 * OpenAI Codex 相关类型定义
 * 基于 codex-rs 协议实现
 */

/**
 * Codex 审批模式
 * - untrusted: 只自动运行受信任命令（如 ls, cat），其他需用户批准
 * - on-failure: 自动运行所有命令，仅失败时询问用户
 * - on-request: 模型自己决定何时询问用户（默认）
 * - never: 从不询问，完全自动执行
 */
export type CodexApprovalMode = 'untrusted' | 'on-failure' | 'on-request' | 'never'

/**
 * Codex 沙盒模式
 * - read-only: 只读，不能写入任何文件
 * - workspace-write: 只能写入工作区目录（默认推荐）
 * - danger-full-access: 完全访问，无沙盒限制（危险）
 */
export type CodexSandboxMode = 'read-only' | 'workspace-write' | 'danger-full-access'

/**
 * Codex 推理深度
 * - minimal: 最小推理（快速响应）
 * - low: 低级推理
 * - medium: 中等推理（默认推荐）
 * - high: 高级推理
 * - xhigh: 最高级推理（非延迟敏感任务）
 */
export type CodexReasoningEffort = 'minimal' | 'low' | 'medium' | 'high' | 'xhigh'

/**
 * Codex 可用模型
 */
export interface CodexModelInfo {
  id: string
  displayName: string
  supportsReasoning: boolean
}

/**
 * Codex 预设模型列表
 */
export const CODEX_MODELS: CodexModelInfo[] = [
  { id: 'gpt-5.2-codex', displayName: 'GPT-5.2 Codex', supportsReasoning: true },
  { id: 'gpt-5.2', displayName: 'GPT-5.2', supportsReasoning: true },
]

/**
 * Codex 会话配置
 */
export interface CodexSessionConfig {
  model: string
  approvalMode: CodexApprovalMode
  sandboxMode: CodexSandboxMode
  reasoningEffort: CodexReasoningEffort
  /** 额外可写目录 */
  additionalWritableDirs: string[]
}

/**
 * 默认 Codex 会话配置
 */
export const DEFAULT_CODEX_CONFIG: CodexSessionConfig = {
  model: 'gpt-5.2-codex',
  approvalMode: 'on-request',
  sandboxMode: 'workspace-write',
  reasoningEffort: 'medium',
  additionalWritableDirs: [],
}

/**
 * 审批模式显示配置
 */
export const APPROVAL_MODE_OPTIONS: Array<{
  value: CodexApprovalMode
  label: string
  description: string
  icon: string
}> = [
  {
    value: 'untrusted',
    label: 'Untrusted',
    description: '只自动运行受信任命令，其他需批准',
    icon: '🔒',
  },
  {
    value: 'on-failure',
    label: 'On Failure',
    description: '自动运行所有命令，仅失败时询问',
    icon: '⚠️',
  },
  {
    value: 'on-request',
    label: 'On Request',
    description: '由模型决定何时询问（默认）',
    icon: '🤖',
  },
  {
    value: 'never',
    label: 'Never',
    description: '从不询问，完全自动执行',
    icon: '⚡',
  },
]

/**
 * 沙盒模式显示配置
 */
export const SANDBOX_MODE_OPTIONS: Array<{
  value: CodexSandboxMode
  label: string
  description: string
  icon: string
}> = [
  {
    value: 'read-only',
    label: 'Read Only',
    description: '只读模式，不能写入任何文件',
    icon: '👁️',
  },
  {
    value: 'workspace-write',
    label: 'Workspace',
    description: '只能写入工作区目录（推荐）',
    icon: '📝',
  },
  {
    value: 'danger-full-access',
    label: 'Full Access',
    description: '完全访问，无沙盒限制（危险）',
    icon: '🔥',
  },
]

/**
 * 推理深度显示配置
 */
export const REASONING_EFFORT_OPTIONS: Array<{
  value: CodexReasoningEffort
  label: string
  description: string
  shortLabel: string
}> = [
  {
    value: 'minimal',
    label: 'Minimal',
    description: '最小推理，快速响应',
    shortLabel: 'Min',
  },
  {
    value: 'low',
    label: 'Low',
    description: '低级推理',
    shortLabel: 'Low',
  },
  {
    value: 'medium',
    label: 'Medium',
    description: '中等推理（推荐）',
    shortLabel: 'Med',
  },
  {
    value: 'high',
    label: 'High',
    description: '高级推理',
    shortLabel: 'High',
  },
  {
    value: 'xhigh',
    label: 'X-High',
    description: '最高级推理，适合复杂任务',
    shortLabel: 'Max',
  },
]

/**
 * 根据模型 ID 检查是否支持推理
 */
export function modelSupportsReasoning(modelId: string): boolean {
  const model = CODEX_MODELS.find(m => m.id === modelId)
  return model?.supportsReasoning ?? false
}

/**
 * 获取推理深度的显示标签
 */
export function getReasoningEffortLabel(effort: CodexReasoningEffort): string {
  const option = REASONING_EFFORT_OPTIONS.find(o => o.value === effort)
  return option?.shortLabel ?? effort
}

/**
 * 获取审批模式的图标
 */
export function getApprovalModeIcon(mode: CodexApprovalMode): string {
  const option = APPROVAL_MODE_OPTIONS.find(o => o.value === mode)
  return option?.icon ?? '?'
}

/**
 * 获取沙盒模式的图标
 */
export function getSandboxModeIcon(mode: CodexSandboxMode): string {
  const option = SANDBOX_MODE_OPTIONS.find(o => o.value === mode)
  return option?.icon ?? '?'
}
