/**
 * Backend Types Unit Tests
 *
 * Tests for type guards, default configs, and factory functions
 */

import { describe, it, expect } from 'vitest'
import {
  BackendTypes,
  isClaudeConfig,
  isCodexConfig,
  isClaude,
  isCodex,
  getDefaultConfig,
  DEFAULT_CLAUDE_CONFIG,
  DEFAULT_CODEX_CONFIG,
  type BackendType,
  type ClaudeBackendConfig,
  type CodexBackendConfig,
} from '../backend'

// ============================================================================
// Type Guards Tests
// ============================================================================

describe('Type Guards', () => {
  describe('isClaudeConfig', () => {
    it('should return true for Claude config', () => {
      const config: ClaudeBackendConfig = {
        type: 'claude',
        modelId: 'claude-sonnet-4-5-20251101',
        permissionMode: 'default',
        skipPermissions: false,
        maxTurns: 10,
        thinkingEnabled: true,
        thinkingTokenBudget: 8096,
        includePartialMessages: true,
      }
      expect(isClaudeConfig(config)).toBe(true)
    })

    it('should return false for Codex config', () => {
      const config: CodexBackendConfig = {
        type: 'codex',
        modelId: 'gpt-5.1-codex-max',
        modelProvider: 'openai',
        permissionMode: 'default',
        skipPermissions: false,
        maxTurns: 10,
        reasoningEffort: 'medium',
        reasoningSummary: 'auto',
        sandboxMode: 'workspace-write',
      }
      expect(isClaudeConfig(config)).toBe(false)
    })
  })

  describe('isCodexConfig', () => {
    it('should return true for Codex config', () => {
      const config: CodexBackendConfig = {
        type: 'codex',
        modelId: 'gpt-5.1-codex-max',
        modelProvider: 'openai',
        permissionMode: 'default',
        skipPermissions: false,
        maxTurns: 10,
        reasoningEffort: 'medium',
        reasoningSummary: 'auto',
        sandboxMode: 'workspace-write',
      }
      expect(isCodexConfig(config)).toBe(true)
    })

    it('should return false for Claude config', () => {
      const config: ClaudeBackendConfig = {
        type: 'claude',
        modelId: 'claude-sonnet-4-5-20251101',
        permissionMode: 'default',
        skipPermissions: false,
        maxTurns: 10,
        thinkingEnabled: true,
        thinkingTokenBudget: 8096,
        includePartialMessages: true,
      }
      expect(isCodexConfig(config)).toBe(false)
    })
  })

  describe('isClaude', () => {
    it('should return true for claude type', () => {
      expect(isClaude('claude')).toBe(true)
    })

    it('should return false for codex type', () => {
      expect(isClaude('codex')).toBe(false)
    })
  })

  describe('isCodex', () => {
    it('should return true for codex type', () => {
      expect(isCodex('codex')).toBe(true)
    })

    it('should return false for claude type', () => {
      expect(isCodex('claude')).toBe(false)
    })
  })
})

// ============================================================================
// Default Config Tests
// ============================================================================

describe('Default Configs', () => {
  describe('DEFAULT_CLAUDE_CONFIG', () => {
    it('should have correct type', () => {
      expect(DEFAULT_CLAUDE_CONFIG.type).toBe('claude')
    })

    it('should have required Claude-specific fields', () => {
      expect(DEFAULT_CLAUDE_CONFIG.thinkingEnabled).toBeDefined()
      expect(DEFAULT_CLAUDE_CONFIG.thinkingTokenBudget).toBeDefined()
      expect(DEFAULT_CLAUDE_CONFIG.includePartialMessages).toBeDefined()
    })

    it('should have valid default values', () => {
      expect(DEFAULT_CLAUDE_CONFIG.thinkingEnabled).toBe(true)
      expect(DEFAULT_CLAUDE_CONFIG.thinkingTokenBudget).toBe(8096)
      expect(DEFAULT_CLAUDE_CONFIG.permissionMode).toBe('default')
      expect(DEFAULT_CLAUDE_CONFIG.skipPermissions).toBe(false)
    })
  })

  describe('DEFAULT_CODEX_CONFIG', () => {
    it('should have correct type', () => {
      expect(DEFAULT_CODEX_CONFIG.type).toBe('codex')
    })

    it('should have required Codex-specific fields', () => {
      expect(DEFAULT_CODEX_CONFIG.modelProvider).toBeDefined()
      expect(DEFAULT_CODEX_CONFIG.reasoningEffort).toBeDefined()
      expect(DEFAULT_CODEX_CONFIG.reasoningSummary).toBeDefined()
      expect(DEFAULT_CODEX_CONFIG.sandboxMode).toBeDefined()
    })

    it('should have valid default values', () => {
      expect(DEFAULT_CODEX_CONFIG.modelProvider).toBe('openai')
      expect(DEFAULT_CODEX_CONFIG.reasoningEffort).toBe('medium')
      expect(DEFAULT_CODEX_CONFIG.reasoningSummary).toBe('auto')
      expect(DEFAULT_CODEX_CONFIG.sandboxMode).toBe('workspace-write')
    })
  })
})

// ============================================================================
// Factory Function Tests
// ============================================================================

describe('getDefaultConfig', () => {
  it('should return Claude config for claude type', () => {
    const config = getDefaultConfig('claude')
    expect(config.type).toBe('claude')
    expect(isClaudeConfig(config)).toBe(true)
  })

  it('should return Codex config for codex type', () => {
    const config = getDefaultConfig('codex')
    expect(config.type).toBe('codex')
    expect(isCodexConfig(config)).toBe(true)
  })

  it('should return a copy, not the original object', () => {
    const config1 = getDefaultConfig('claude')
    const config2 = getDefaultConfig('claude')
    expect(config1).not.toBe(config2)
    expect(config1).toEqual(config2)
  })

  it('should allow modification without affecting defaults', () => {
    const config = getDefaultConfig('claude') as ClaudeBackendConfig
    config.thinkingEnabled = false

    expect(DEFAULT_CLAUDE_CONFIG.thinkingEnabled).toBe(true)
    expect(config.thinkingEnabled).toBe(false)
  })
})

// ============================================================================
// BackendTypes Constant Tests
// ============================================================================

describe('BackendTypes', () => {
  it('should have CLAUDE constant', () => {
    expect(BackendTypes.CLAUDE).toBe('claude')
  })

  it('should have CODEX constant', () => {
    expect(BackendTypes.CODEX).toBe('codex')
  })

  it('should be usable as BackendType', () => {
    const type: BackendType = BackendTypes.CLAUDE
    expect(isClaude(type)).toBe(true)
  })
})
