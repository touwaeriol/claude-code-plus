/**
 * Thinking/Reasoning Configuration Types
 *
 * Different backends have different approaches to thinking/reasoning:
 * - Claude: Uses token budget (numeric value)
 * - Codex: Uses effort levels (discrete levels)
 *
 * This module provides unified types and utilities for handling both.
 */

import type { BackendType, CodexReasoningEffort } from './backend'

// ============================================================================
// Claude Thinking Configuration
// ============================================================================

/**
 * Claude thinking configuration
 *
 * Claude uses a token budget approach where you specify the maximum
 * number of tokens to use for thinking.
 */
export interface ClaudeThinkingConfig {
  type: 'claude'

  /** Whether thinking is enabled */
  enabled: boolean

  /**
   * Token budget for thinking
   * Common values:
   * - 0: Off
   * - 1024: Low (1K)
   * - 4096: Medium (4K)
   * - 8096: High (8K) - Default
   * - 16384: Very High (16K)
   * - 32768: Ultra (32K)
   */
  tokenBudget: number
}

/**
 * Preset thinking levels for Claude
 */
export const CLAUDE_THINKING_PRESETS = {
  OFF: { id: 'off', label: 'Off', tokens: 0 },
  LOW: { id: 'low', label: 'Low (1K)', tokens: 1024 },
  MEDIUM: { id: 'medium', label: 'Medium (4K)', tokens: 4096 },
  HIGH: { id: 'high', label: 'High (8K)', tokens: 8096 },
  VERY_HIGH: { id: 'very_high', label: 'Very High (16K)', tokens: 16384 },
  ULTRA: { id: 'ultra', label: 'Ultra (32K)', tokens: 32768 },
} as const

export type ClaudeThinkingPresetId = keyof typeof CLAUDE_THINKING_PRESETS

/**
 * Get Claude thinking presets as array for UI
 */
export function getClaudeThinkingPresets() {
  return Object.values(CLAUDE_THINKING_PRESETS)
}

/**
 * Find preset by token value (closest match)
 */
export function findClaudePresetByTokens(tokens: number): typeof CLAUDE_THINKING_PRESETS[ClaudeThinkingPresetId] {
  if (tokens === 0) return CLAUDE_THINKING_PRESETS.OFF

  const presets = Object.values(CLAUDE_THINKING_PRESETS)
  let closest = CLAUDE_THINKING_PRESETS.HIGH

  for (const preset of presets) {
    if (Math.abs(preset.tokens - tokens) < Math.abs(closest.tokens - tokens)) {
      closest = preset
    }
  }

  return closest
}

// ============================================================================
// Codex Thinking Configuration
// ============================================================================

/**
 * Codex thinking/reasoning configuration
 *
 * Codex uses effort levels instead of token budgets.
 */
export interface CodexThinkingConfig {
  type: 'codex'

  /**
   * Reasoning effort level
   * null = disabled
   */
  effort: CodexReasoningEffort | null

  /**
   * Summary mode for reasoning output
   */
  summary: CodexReasoningSummaryMode
}

/**
 * Codex reasoning summary modes
 */
export type CodexReasoningSummaryMode = 'auto' | 'concise' | 'detailed' | 'none'

/**
 * Codex effort level definitions
 */
export const CODEX_EFFORT_LEVELS = {
  OFF: { id: null, label: 'Off', description: 'Disable reasoning' },
  MINIMAL: { id: 'minimal' as const, label: 'Minimal', description: 'Very brief reasoning' },
  LOW: { id: 'low' as const, label: 'Low', description: 'Quick reasoning' },
  MEDIUM: { id: 'medium' as const, label: 'Medium', description: 'Balanced reasoning (default)' },
  HIGH: { id: 'high' as const, label: 'High', description: 'Detailed reasoning' },
  XHIGH: { id: 'xhigh' as const, label: 'Extra High', description: 'Most thorough reasoning' },
} as const

export type CodexEffortLevelKey = keyof typeof CODEX_EFFORT_LEVELS

/**
 * Get Codex effort levels as array for UI
 */
export function getCodexEffortLevels() {
  return Object.values(CODEX_EFFORT_LEVELS)
}

/**
 * Codex summary mode definitions
 */
export const CODEX_SUMMARY_MODES = {
  AUTO: { id: 'auto' as const, label: 'Auto', description: 'Automatic summary' },
  CONCISE: { id: 'concise' as const, label: 'Concise', description: 'Brief summary' },
  DETAILED: { id: 'detailed' as const, label: 'Detailed', description: 'Full summary' },
  NONE: { id: 'none' as const, label: 'None', description: 'No summary' },
} as const

/**
 * Get Codex summary modes as array for UI
 */
export function getCodexSummaryModes() {
  return Object.values(CODEX_SUMMARY_MODES)
}

// ============================================================================
// Unified Thinking Configuration
// ============================================================================

/**
 * Union type for thinking configuration
 */
export type ThinkingConfig = ClaudeThinkingConfig | CodexThinkingConfig

/**
 * Check if thinking is enabled for any backend
 */
export function isThinkingEnabled(config: ThinkingConfig): boolean {
  if (config.type === 'claude') {
    return config.enabled && config.tokenBudget > 0
  } else {
    return config.effort !== null
  }
}

// ============================================================================
// Type Guards
// ============================================================================

/**
 * Check if config is Claude thinking config
 */
export function isClaudeThinking(config: ThinkingConfig): config is ClaudeThinkingConfig {
  return config.type === 'claude'
}

/**
 * Check if config is Codex thinking config
 */
export function isCodexThinking(config: ThinkingConfig): config is CodexThinkingConfig {
  return config.type === 'codex'
}

// ============================================================================
// Factory Functions
// ============================================================================

/**
 * Create default Claude thinking config
 */
export function createClaudeThinkingConfig(
  enabled: boolean = true,
  tokenBudget: number = 8096
): ClaudeThinkingConfig {
  return {
    type: 'claude',
    enabled,
    tokenBudget,
  }
}

/**
 * Create default Codex thinking config
 */
export function createCodexThinkingConfig(
  effort: CodexReasoningEffort | null = 'medium',
  summary: CodexReasoningSummaryMode = 'auto'
): CodexThinkingConfig {
  return {
    type: 'codex',
    effort,
    summary,
  }
}

/**
 * Create thinking config based on backend type
 */
export function createThinkingConfig(backendType: BackendType): ThinkingConfig {
  return backendType === 'claude'
    ? createClaudeThinkingConfig()
    : createCodexThinkingConfig()
}

// ============================================================================
// Conversion Utilities
// ============================================================================

/**
 * Convert Claude token budget to approximate Codex effort level
 * (for migration/comparison purposes)
 */
export function claudeTokensToCodexEffort(tokens: number): CodexReasoningEffort | null {
  if (tokens === 0) return null
  if (tokens <= 1024) return 'minimal'
  if (tokens <= 4096) return 'low'
  if (tokens <= 8096) return 'medium'
  if (tokens <= 16384) return 'high'
  return 'xhigh'
}

/**
 * Convert Codex effort level to approximate Claude token budget
 * (for migration/comparison purposes)
 */
export function codexEffortToClaudeTokens(effort: CodexReasoningEffort | null): number {
  switch (effort) {
    case null: return 0
    case 'minimal': return 1024
    case 'low': return 4096
    case 'medium': return 8096
    case 'high': return 16384
    case 'xhigh': return 32768
    default: return 8096
  }
}

// ============================================================================
// Display Helpers
// ============================================================================

/**
 * Get display string for thinking configuration
 */
export function getThinkingDisplayString(config: ThinkingConfig): string {
  if (config.type === 'claude') {
    if (!config.enabled || config.tokenBudget === 0) {
      return 'Thinking: Off'
    }
    const preset = findClaudePresetByTokens(config.tokenBudget)
    return `Thinking: ${preset.label}`
  } else {
    if (config.effort === null) {
      return 'Reasoning: Off'
    }
    const level = CODEX_EFFORT_LEVELS[config.effort.toUpperCase() as CodexEffortLevelKey]
    return `Reasoning: ${level?.label || config.effort}`
  }
}

/**
 * Get short display string for thinking configuration (for compact UI)
 */
export function getThinkingShortDisplayString(config: ThinkingConfig): string {
  if (config.type === 'claude') {
    if (!config.enabled || config.tokenBudget === 0) {
      return 'Off'
    }
    return `${Math.round(config.tokenBudget / 1024)}K`
  } else {
    if (config.effort === null) {
      return 'Off'
    }
    return config.effort.charAt(0).toUpperCase() + config.effort.slice(1)
  }
}
