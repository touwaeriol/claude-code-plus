/**
 * Backend Capabilities Service
 *
 * This module provides capability information for different backends,
 * enabling the frontend to adapt its UI based on what each backend supports.
 */

import type {
  BackendType,
  BackendCapabilities,
  BackendModelInfo,
  SandboxMode,
} from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'

// Re-export types for convenience
export type { BackendModelInfo, BackendType, BackendCapabilities }
import {
  CLAUDE_THINKING_PRESETS,
  CODEX_EFFORT_LEVELS,
  CODEX_SUMMARY_MODES,
} from '@/types/thinking'

// ============================================================================
// Claude Capabilities
// ============================================================================

/**
 * Default Claude models
 */
export const CLAUDE_MODELS: BackendModelInfo[] = [
  {
    id: 'claude-opus-4-5-20251101',
    displayName: 'Claude Opus 4.5',
    description: 'Most capable model for complex tasks',
    supportsThinking: true,
    isDefault: true,
  },
  {
    id: 'claude-sonnet-4-5-20250929',
    displayName: 'Claude Sonnet 4.5',
    description: 'Balanced performance and cost',
    supportsThinking: true,
    isDefault: false,
  },
  {
    id: 'claude-haiku-4-5-20251001',
    displayName: 'Claude Haiku 4.5',
    description: 'Fast and efficient for simple tasks',
    supportsThinking: true,
    isDefault: false,
  },
]

/**
 * Claude backend capabilities
 */
export const CLAUDE_CAPABILITIES: BackendCapabilities = {
  type: 'claude',
  displayName: 'Claude',
  supportsThinking: true,
  thinkingConfigType: 'token_budget',
  supportsSubAgents: true,
  supportsMcp: true,
  supportsSandbox: false,
  supportsPromptCaching: true,
  exposesTokenUsage: true,
  supportedTools: [
    'Read',
    'Write',
    'Edit',
    'MultiEdit',
    'Bash',
    'Task',
    'Glob',
    'Grep',
    'LS',
    'TodoRead',
    'TodoWrite',
    'WebFetch',
    'WebSearch',
    'NotebookRead',
    'NotebookEdit',
  ],
  availableModels: CLAUDE_MODELS,
}

// ============================================================================
// Codex Capabilities
// ============================================================================

/**
 * Default Codex models
 */
export const CODEX_MODELS: BackendModelInfo[] = [
  {
    id: 'gpt-5.1-codex-max',
    displayName: 'GPT-5.1 Codex Max',
    description: 'Most capable Codex model',
    supportsThinking: true,
    isDefault: true,
  },
  {
    id: 'o3',
    displayName: 'o3',
    description: 'Advanced reasoning model',
    supportsThinking: true,
    isDefault: false,
  },
  {
    id: 'gpt-4o',
    displayName: 'GPT-4o',
    description: 'Balanced multimodal model',
    supportsThinking: false,
    isDefault: false,
  },
  {
    id: 'gpt-4o-mini',
    displayName: 'GPT-4o Mini',
    description: 'Fast and efficient',
    supportsThinking: false,
    isDefault: false,
  },
]

/**
 * Available sandbox modes for Codex
 */
export const CODEX_SANDBOX_MODES: SandboxMode[] = [
  'read-only',
  'workspace-write',
  'full-access',
]

/**
 * Codex backend capabilities
 */
export const CODEX_CAPABILITIES: BackendCapabilities = {
  type: 'codex',
  displayName: 'OpenAI Codex',
  supportsThinking: true,
  thinkingConfigType: 'effort_level',
  supportsSubAgents: false,
  supportsMcp: true,
  supportsSandbox: true,
  sandboxModes: CODEX_SANDBOX_MODES,
  supportsPromptCaching: false, // Model-dependent
  exposesTokenUsage: false, // Not exposed in current API
  supportedTools: [
    'CommandExecution',
    'FileChange',
    'McpToolCall',
    'WebSearch',
    'ImageView',
  ],
  availableModels: CODEX_MODELS,
}

// ============================================================================
// Capability Service
// ============================================================================

/**
 * All backend capabilities
 */
const CAPABILITIES_MAP: Record<BackendType, BackendCapabilities> = {
  claude: CLAUDE_CAPABILITIES,
  codex: CODEX_CAPABILITIES,
}

/**
 * Get capabilities for a backend type
 */
export function getCapabilities(type: BackendType): BackendCapabilities {
  return CAPABILITIES_MAP[type]
}

/**
 * Get all available backend types
 */
export function getAvailableBackends(): BackendType[] {
  return Object.keys(CAPABILITIES_MAP) as BackendType[]
}

/**
 * Get available models for a backend type
 */
export function getModels(type: BackendType): BackendModelInfo[] {
  return getCapabilities(type).availableModels
}

/**
 * Get default model for a backend type
 */
export function getDefaultModel(type: BackendType): BackendModelInfo | undefined {
  return getModels(type).find(m => m.isDefault)
}

/**
 * Get model by ID for a backend type
 */
export function getModelById(type: BackendType, modelId: string): BackendModelInfo | undefined {
  return getModels(type).find(m => m.id === modelId)
}

// ============================================================================
// Feature Checks
// ============================================================================

/**
 * Feature flags that can be checked
 */
export type BackendFeature =
  | 'thinking'
  | 'sub_agents'
  | 'mcp'
  | 'sandbox'
  | 'prompt_caching'
  | 'token_usage'
  | 'token_budget_thinking'  // Claude-style thinking
  | 'effort_level_thinking'  // Codex-style thinking

/**
 * Check if a backend supports a feature
 */
export function supportsFeature(type: BackendType, feature: BackendFeature): boolean {
  const caps = getCapabilities(type)

  switch (feature) {
    case 'thinking':
      return caps.supportsThinking
    case 'sub_agents':
      return caps.supportsSubAgents
    case 'mcp':
      return caps.supportsMcp
    case 'sandbox':
      return caps.supportsSandbox
    case 'prompt_caching':
      return caps.supportsPromptCaching
    case 'token_usage':
      return caps.exposesTokenUsage
    case 'token_budget_thinking':
      return caps.supportsThinking && caps.thinkingConfigType === 'token_budget'
    case 'effort_level_thinking':
      return caps.supportsThinking && caps.thinkingConfigType === 'effort_level'
    default:
      return false
  }
}

/**
 * Check if a backend supports a specific tool
 */
export function supportsTool(type: BackendType, toolName: string): boolean {
  const caps = getCapabilities(type)
  return caps.supportedTools.includes(toolName)
}

// ============================================================================
// Thinking Configuration Options
// ============================================================================

/**
 * Thinking configuration options for UI
 */
export interface ThinkingOption {
  id: string | null
  label: string
  description?: string
  value?: number // For Claude token budget
}

/**
 * Get thinking options for a backend type
 */
export function getThinkingOptions(type: BackendType): ThinkingOption[] {
  const caps = getCapabilities(type)

  if (!caps.supportsThinking) {
    return []
  }

  if (caps.thinkingConfigType === 'token_budget') {
    // Claude: token budget presets
    return Object.values(CLAUDE_THINKING_PRESETS).map(preset => ({
      id: preset.id,
      label: preset.label,
      value: preset.tokens,
    }))
  } else {
    // Codex: effort levels
    return Object.values(CODEX_EFFORT_LEVELS).map(level => ({
      id: level.id,
      label: level.label,
      description: level.description,
    }))
  }
}

/**
 * Get summary mode options (Codex only)
 */
export function getSummaryModeOptions(): ThinkingOption[] {
  return Object.values(CODEX_SUMMARY_MODES).map(mode => ({
    id: mode.id,
    label: mode.label,
    description: mode.description,
  }))
}

// ============================================================================
// Sandbox Mode Options
// ============================================================================

/**
 * Sandbox mode option for UI
 */
export interface SandboxOption {
  id: SandboxMode
  label: string
  description: string
}

/**
 * Get sandbox mode options (Codex only)
 */
export function getSandboxOptions(): SandboxOption[] {
  return [
    {
      id: 'read-only',
      label: 'Read Only',
      description: 'Can only read files, no modifications allowed',
    },
    {
      id: 'workspace-write',
      label: 'Workspace Write',
      description: 'Can write to workspace directories only',
    },
    {
      id: 'full-access',
      label: 'Full Access',
      description: 'Full system access (dangerous)',
    },
  ]
}

// ============================================================================
// Display Helpers
// ============================================================================

/**
 * Get backend display name
 */
export function getBackendDisplayName(type: BackendType): string {
  return getCapabilities(type).displayName
}

/**
 * Get backend icon (for UI)
 */
export function getBackendIcon(type: BackendType): string {
  switch (type) {
    case 'claude':
      return 'anthropic' // Use appropriate icon identifier
    case 'codex':
      return 'openai'
    default:
      return 'robot'
  }
}

/**
 * Get thinking config type display name
 */
export function getThinkingConfigTypeDisplayName(type: BackendType): string {
  const caps = getCapabilities(type)
  if (!caps.supportsThinking) {
    return 'Not supported'
  }
  return caps.thinkingConfigType === 'token_budget'
    ? 'Token Budget'
    : 'Effort Level'
}

// ============================================================================
// Validation
// ============================================================================

/**
 * Validate thinking config for a backend type
 */
export function isValidThinkingConfig(type: BackendType, config: ThinkingConfig): boolean {
  const caps = getCapabilities(type)

  if (!caps.supportsThinking) {
    return false
  }

  if (caps.thinkingConfigType === 'token_budget') {
    return config.type === 'claude'
  } else {
    return config.type === 'codex'
  }
}

/**
 * Validate model ID for a backend type
 */
export function isValidModel(type: BackendType, modelId: string): boolean {
  return getModels(type).some(m => m.id === modelId)
}
