/**
 * Thinking Configuration Types Unit Tests
 *
 * Tests for thinking presets, conversion functions, and display helpers
 */

import { describe, it, expect } from 'vitest'
import {
  getClaudeThinkingPresets,
  getCodexEffortLevels,
  getCodexSummaryModes,
  findClaudePresetByTokens,
  isThinkingEnabled,
  isClaudeThinking,
  isCodexThinking,
  createClaudeThinkingConfig,
  createCodexThinkingConfig,
  createThinkingConfig,
  claudeTokensToCodexEffort,
  codexEffortToClaudeTokens,
  getThinkingDisplayString,
  getThinkingShortDisplayString,
  type ClaudeThinkingConfig,
  type CodexThinkingConfig,
  type ThinkingConfig,
} from '../thinking'

// ============================================================================
// Preset Lookup Tests
// ============================================================================

describe('Preset Lookups', () => {
  describe('getClaudeThinkingPresets', () => {
    it('should return all Claude presets as array', () => {
      const presets = getClaudeThinkingPresets()
      expect(Array.isArray(presets)).toBe(true)
      expect(presets.length).toBe(6) // OFF, LOW, MEDIUM, HIGH, VERY_HIGH, ULTRA
    })

    it('should include OFF preset', () => {
      const presets = getClaudeThinkingPresets()
      const off = presets.find(p => p.id === 'off')
      expect(off).toBeDefined()
      expect(off?.tokens).toBe(0)
    })

    it('should include HIGH preset as default', () => {
      const presets = getClaudeThinkingPresets()
      const high = presets.find(p => p.id === 'high')
      expect(high).toBeDefined()
      expect(high?.tokens).toBe(8096)
    })
  })

  describe('getCodexEffortLevels', () => {
    it('should return all Codex effort levels as array', () => {
      const levels = getCodexEffortLevels()
      expect(Array.isArray(levels)).toBe(true)
      expect(levels.length).toBe(6) // OFF, MINIMAL, LOW, MEDIUM, HIGH, XHIGH
    })

    it('should include OFF level with null id', () => {
      const levels = getCodexEffortLevels()
      const off = levels.find(l => l.id === null)
      expect(off).toBeDefined()
      expect(off?.label).toBe('Off')
    })

    it('should include MEDIUM as default level', () => {
      const levels = getCodexEffortLevels()
      const medium = levels.find(l => l.id === 'medium')
      expect(medium).toBeDefined()
    })
  })

  describe('getCodexSummaryModes', () => {
    it('should return all summary modes as array', () => {
      const modes = getCodexSummaryModes()
      expect(Array.isArray(modes)).toBe(true)
      expect(modes.length).toBe(4) // AUTO, CONCISE, DETAILED, NONE
    })

    it('should include AUTO mode', () => {
      const modes = getCodexSummaryModes()
      const auto = modes.find(m => m.id === 'auto')
      expect(auto).toBeDefined()
    })
  })

  describe('findClaudePresetByTokens', () => {
    it('should return OFF preset for 0 tokens', () => {
      const preset = findClaudePresetByTokens(0)
      expect(preset.id).toBe('off')
    })

    it('should find exact match for 8096 tokens', () => {
      const preset = findClaudePresetByTokens(8096)
      expect(preset.id).toBe('high')
    })

    it('should find closest match for non-exact value', () => {
      const preset = findClaudePresetByTokens(7000)
      expect(preset.id).toBe('high') // 8096 is closer than 4096
    })

    it('should return ULTRA for very high values', () => {
      const preset = findClaudePresetByTokens(30000)
      expect(preset.id).toBe('ultra')
    })
  })
})

// ============================================================================
// Conversion Function Tests
// ============================================================================

describe('Conversion Functions', () => {
  describe('claudeTokensToCodexEffort', () => {
    it('should return null for 0 tokens', () => {
      expect(claudeTokensToCodexEffort(0)).toBeNull()
    })

    it('should return minimal for low tokens', () => {
      expect(claudeTokensToCodexEffort(500)).toBe('minimal')
      expect(claudeTokensToCodexEffort(1024)).toBe('minimal')
    })

    it('should return low for medium-low tokens', () => {
      expect(claudeTokensToCodexEffort(2000)).toBe('low')
      expect(claudeTokensToCodexEffort(4096)).toBe('low')
    })

    it('should return medium for standard tokens', () => {
      expect(claudeTokensToCodexEffort(6000)).toBe('medium')
      expect(claudeTokensToCodexEffort(8096)).toBe('medium')
    })

    it('should return high for high tokens', () => {
      expect(claudeTokensToCodexEffort(12000)).toBe('high')
      expect(claudeTokensToCodexEffort(16384)).toBe('high')
    })

    it('should return xhigh for very high tokens', () => {
      expect(claudeTokensToCodexEffort(20000)).toBe('xhigh')
      expect(claudeTokensToCodexEffort(32768)).toBe('xhigh')
    })
  })

  describe('codexEffortToClaudeTokens', () => {
    it('should return 0 for null effort', () => {
      expect(codexEffortToClaudeTokens(null)).toBe(0)
    })

    it('should return correct tokens for each effort level', () => {
      expect(codexEffortToClaudeTokens('minimal')).toBe(1024)
      expect(codexEffortToClaudeTokens('low')).toBe(4096)
      expect(codexEffortToClaudeTokens('medium')).toBe(8096)
      expect(codexEffortToClaudeTokens('high')).toBe(16384)
      expect(codexEffortToClaudeTokens('xhigh')).toBe(32768)
    })
  })

  describe('bidirectional conversion', () => {
    it('should be approximately reversible for standard values', () => {
      const originalTokens = 8096
      const effort = claudeTokensToCodexEffort(originalTokens)
      const backToTokens = codexEffortToClaudeTokens(effort)
      expect(backToTokens).toBe(originalTokens)
    })
  })
})

// ============================================================================
// Display Helper Tests
// ============================================================================

describe('Display Helpers', () => {
  describe('getThinkingDisplayString', () => {
    it('should show "Thinking: Off" for disabled Claude config', () => {
      const config: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: false,
        tokenBudget: 8096,
      }
      expect(getThinkingDisplayString(config)).toBe('Thinking: Off')
    })

    it('should show "Thinking: Off" for zero token budget', () => {
      const config: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: true,
        tokenBudget: 0,
      }
      expect(getThinkingDisplayString(config)).toBe('Thinking: Off')
    })

    it('should show preset label for enabled Claude config', () => {
      const config: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: true,
        tokenBudget: 8096,
      }
      expect(getThinkingDisplayString(config)).toBe('Thinking: High (8K)')
    })

    it('should show "Reasoning: Off" for null Codex effort', () => {
      const config: CodexThinkingConfig = {
        type: 'codex',
        effort: null,
        summary: 'auto',
      }
      expect(getThinkingDisplayString(config)).toBe('Reasoning: Off')
    })

    it('should show effort level for enabled Codex config', () => {
      const config: CodexThinkingConfig = {
        type: 'codex',
        effort: 'medium',
        summary: 'auto',
      }
      expect(getThinkingDisplayString(config)).toBe('Reasoning: Medium')
    })
  })

  describe('getThinkingShortDisplayString', () => {
    it('should show "Off" for disabled config', () => {
      const claudeConfig: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: false,
        tokenBudget: 8096,
      }
      expect(getThinkingShortDisplayString(claudeConfig)).toBe('Off')

      const codexConfig: CodexThinkingConfig = {
        type: 'codex',
        effort: null,
        summary: 'auto',
      }
      expect(getThinkingShortDisplayString(codexConfig)).toBe('Off')
    })

    it('should show token count in K for Claude', () => {
      const config: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: true,
        tokenBudget: 8096,
      }
      expect(getThinkingShortDisplayString(config)).toBe('8K')
    })

    it('should show capitalized effort for Codex', () => {
      const config: CodexThinkingConfig = {
        type: 'codex',
        effort: 'high',
        summary: 'auto',
      }
      expect(getThinkingShortDisplayString(config)).toBe('High')
    })
  })
})

// ============================================================================
// Type Guard Tests
// ============================================================================

describe('Type Guards', () => {
  describe('isThinkingEnabled', () => {
    it('should return true for enabled Claude with budget', () => {
      const config: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: true,
        tokenBudget: 8096,
      }
      expect(isThinkingEnabled(config)).toBe(true)
    })

    it('should return false for disabled Claude', () => {
      const config: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: false,
        tokenBudget: 8096,
      }
      expect(isThinkingEnabled(config)).toBe(false)
    })

    it('should return false for zero budget Claude', () => {
      const config: ClaudeThinkingConfig = {
        type: 'claude',
        enabled: true,
        tokenBudget: 0,
      }
      expect(isThinkingEnabled(config)).toBe(false)
    })

    it('should return true for Codex with effort', () => {
      const config: CodexThinkingConfig = {
        type: 'codex',
        effort: 'medium',
        summary: 'auto',
      }
      expect(isThinkingEnabled(config)).toBe(true)
    })

    it('should return false for Codex with null effort', () => {
      const config: CodexThinkingConfig = {
        type: 'codex',
        effort: null,
        summary: 'auto',
      }
      expect(isThinkingEnabled(config)).toBe(false)
    })
  })

  describe('isClaudeThinking', () => {
    it('should return true for Claude config', () => {
      const config: ThinkingConfig = createClaudeThinkingConfig()
      expect(isClaudeThinking(config)).toBe(true)
    })

    it('should return false for Codex config', () => {
      const config: ThinkingConfig = createCodexThinkingConfig()
      expect(isClaudeThinking(config)).toBe(false)
    })
  })

  describe('isCodexThinking', () => {
    it('should return true for Codex config', () => {
      const config: ThinkingConfig = createCodexThinkingConfig()
      expect(isCodexThinking(config)).toBe(true)
    })

    it('should return false for Claude config', () => {
      const config: ThinkingConfig = createClaudeThinkingConfig()
      expect(isCodexThinking(config)).toBe(false)
    })
  })
})

// ============================================================================
// Factory Function Tests
// ============================================================================

describe('Factory Functions', () => {
  describe('createClaudeThinkingConfig', () => {
    it('should create default Claude config', () => {
      const config = createClaudeThinkingConfig()
      expect(config.type).toBe('claude')
      expect(config.enabled).toBe(true)
      expect(config.tokenBudget).toBe(8096)
    })

    it('should accept custom values', () => {
      const config = createClaudeThinkingConfig(false, 4096)
      expect(config.enabled).toBe(false)
      expect(config.tokenBudget).toBe(4096)
    })
  })

  describe('createCodexThinkingConfig', () => {
    it('should create default Codex config', () => {
      const config = createCodexThinkingConfig()
      expect(config.type).toBe('codex')
      expect(config.effort).toBe('medium')
      expect(config.summary).toBe('auto')
    })

    it('should accept custom values', () => {
      const config = createCodexThinkingConfig('high', 'detailed')
      expect(config.effort).toBe('high')
      expect(config.summary).toBe('detailed')
    })

    it('should accept null effort', () => {
      const config = createCodexThinkingConfig(null)
      expect(config.effort).toBeNull()
    })
  })

  describe('createThinkingConfig', () => {
    it('should create Claude config for claude backend', () => {
      const config = createThinkingConfig('claude')
      expect(config.type).toBe('claude')
      expect(isClaudeThinking(config)).toBe(true)
    })

    it('should create Codex config for codex backend', () => {
      const config = createThinkingConfig('codex')
      expect(config.type).toBe('codex')
      expect(isCodexThinking(config)).toBe(true)
    })
  })
})
