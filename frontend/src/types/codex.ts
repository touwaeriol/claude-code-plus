/**
 * OpenAI Codex types and helpers.
 */

export type CodexApprovalMode = 'untrusted' | 'on-failure' | 'on-request' | 'never'

export type CodexSandboxMode = 'read-only' | 'workspace-write' | 'danger-full-access'

export type CodexReasoningEffort = 'none' | 'minimal' | 'low' | 'medium' | 'high' | 'xhigh'

export interface CodexModelInfo {
  modelId: string
  displayName: string
  supportsReasoning: boolean
}

export const CODEX_MODELS: CodexModelInfo[] = [
  { modelId: 'gpt-5.4', displayName: 'GPT-5.4', supportsReasoning: true },
  { modelId: 'gpt-5.3-codex', displayName: 'GPT-5.3-Codex', supportsReasoning: true },
]

export interface CodexSessionConfig {
  model: string
  approvalMode: CodexApprovalMode
  sandboxMode: CodexSandboxMode
  reasoningEffort: CodexReasoningEffort
  additionalWritableDirs: string[]
}

export const APPROVAL_MODE_OPTIONS: Array<{
  value: CodexApprovalMode
  label: string
  description: string
  icon: string
}> = [
  {
    value: 'untrusted',
    label: 'Untrusted',
    description: 'Only run trusted commands; ask for approval otherwise.',
    icon: '🔒',
  },
  {
    value: 'on-failure',
    label: 'On Failure',
    description: 'Run commands automatically; ask only if a command fails.',
    icon: '⚠️',
  },
  {
    value: 'on-request',
    label: 'On Request',
    description: 'The model decides when to request approval (default).',
    icon: '🤖',
  },
  {
    value: 'never',
    label: 'Never',
    description: 'Never ask for approval; run everything automatically.',
    icon: '⛔',
  },
]

export const SANDBOX_MODE_OPTIONS: Array<{
  value: CodexSandboxMode
  label: string
  description: string
  icon: string
}> = [
  {
    value: 'read-only',
    label: 'Chat',
    description: 'Chat only (read-only).',
    icon: '💬',
  },
  {
    value: 'workspace-write',
    label: 'Agent',
    description: 'Agent with workspace write access.',
    icon: '🤖',
  },
  {
    value: 'danger-full-access',
    label: 'Agent (full access)',
    description: 'Agent with full system access (dangerous).',
    icon: '🔓',
  },
]

export const REASONING_EFFORT_OPTIONS: Array<{
  value: CodexReasoningEffort
  label: string
  description: string
  shortLabel: string
}> = [
  {
    value: 'none',
    label: 'none',
    description: 'Disable reasoning (fastest).',
    shortLabel: 'none',
  },
  {
    value: 'minimal',
    label: 'minimal',
    description: 'Minimal reasoning (fast).',
    shortLabel: 'minimal',
  },
  {
    value: 'low',
    label: 'low',
    description: 'Low reasoning.',
    shortLabel: 'low',
  },
  {
    value: 'medium',
    label: 'medium',
    description: 'Balanced reasoning (default).',
    shortLabel: 'medium',
  },
  {
    value: 'high',
    label: 'high',
    description: 'High reasoning.',
    shortLabel: 'high',
  },
  {
    value: 'xhigh',
    label: 'xhigh',
    description: 'Extra high reasoning.',
    shortLabel: 'xhigh',
  },
]

export function modelSupportsReasoning(modelId: string): boolean {
  const model = CODEX_MODELS.find(m => m.modelId === modelId)
  return model?.supportsReasoning ?? false
}

export function getReasoningEffortLabel(effort: CodexReasoningEffort): string {
  const option = REASONING_EFFORT_OPTIONS.find(o => o.value === effort)
  return option?.shortLabel ?? effort
}

export function getApprovalModeIcon(mode: CodexApprovalMode): string {
  const option = APPROVAL_MODE_OPTIONS.find(o => o.value === mode)
  return option?.icon ?? '?'
}

export function getSandboxModeIcon(mode: CodexSandboxMode): string {
  const option = SANDBOX_MODE_OPTIONS.find(o => o.value === mode)
  return option?.icon ?? '?'
}
