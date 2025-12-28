/**
 * Backend Capabilities Service Unit Tests
 *
 * Tests for capability lookups, feature checks, and model validation
 */

import { describe, it, expect } from 'vitest'
import {
  CLAUDE_MODELS,
  CODEX_MODELS,
  CLAUDE_CAPABILITIES,
  CODEX_CAPABILITIES,
  getCapabilities,
  getAvailableBackends,
  getModels,
  getDefaultModel,
  getModelById,
  supportsFeature,
  supportsTool,
  getThinkingOptions,
  getSummaryModeOptions,
  getSandboxOptions,
  getBackendDisplayName,
  getBackendIcon,
  isValidThinkingConfig,
  isValidModel,
} from '../backendCapabilities'
import { createClaudeThinkingConfig, createCodexThinkingConfig } from '@/types/thinking'

// ============================================================================
// Capability Lookup Tests
// ============================================================================

describe('Capability Lookups', () => {
  describe('getCapabilities', () => {
    it('should return Claude capabilities for claude type', () => {
      const caps = getCapabilities('claude')
      expect(caps.type).toBe('claude')
      expect(caps.displayName).toBe('Claude')
    })

    it('should return Codex capabilities for codex type', () => {
      const caps = getCapabilities('codex')
      expect(caps.type).toBe('codex')
      expect(caps.displayName).toBe('OpenAI Codex')
    })
  })

  describe('getAvailableBackends', () => {
    it('should return all backend types', () => {
      const backends = getAvailableBackends()
      expect(backends).toContain('claude')
      expect(backends).toContain('codex')
      expect(backends.length).toBe(2)
    })
  })

  describe('getModels', () => {
    it('should return Claude models for claude type', () => {
      const models = getModels('claude')
      expect(models).toEqual(CLAUDE_MODELS)
      expect(models.length).toBeGreaterThan(0)
    })

    it('should return Codex models for codex type', () => {
      const models = getModels('codex')
      expect(models).toEqual(CODEX_MODELS)
      expect(models.length).toBeGreaterThan(0)
    })
  })

  describe('getDefaultModel', () => {
    it('should return default Claude model', () => {
      const model = getDefaultModel('claude')
      expect(model).toBeDefined()
      expect(model?.isDefault).toBe(true)
    })

    it('should return default Codex model', () => {
      const model = getDefaultModel('codex')
      expect(model).toBeDefined()
      expect(model?.isDefault).toBe(true)
    })
  })

  describe('getModelById', () => {
    it('should find Claude model by ID', () => {
      const model = getModelById('claude', 'claude-sonnet-4-5-20251101')
      expect(model).toBeDefined()
      expect(model?.displayName).toBe('Claude Sonnet 4.5')
    })

    it('should find Codex model by ID', () => {
      const model = getModelById('codex', 'gpt-4o')
      expect(model).toBeDefined()
      expect(model?.displayName).toBe('GPT-4o')
    })

    it('should return undefined for non-existent model', () => {
      const model = getModelById('claude', 'non-existent-model')
      expect(model).toBeUndefined()
    })
  })
})

// ============================================================================
// Feature Check Tests
// ============================================================================

describe('Feature Checks', () => {
  describe('supportsFeature', () => {
    describe('Claude features', () => {
      it('should support thinking', () => {
        expect(supportsFeature('claude', 'thinking')).toBe(true)
      })

      it('should support sub_agents', () => {
        expect(supportsFeature('claude', 'sub_agents')).toBe(true)
      })

      it('should support mcp', () => {
        expect(supportsFeature('claude', 'mcp')).toBe(true)
      })

      it('should not support sandbox', () => {
        expect(supportsFeature('claude', 'sandbox')).toBe(false)
      })

      it('should support prompt_caching', () => {
        expect(supportsFeature('claude', 'prompt_caching')).toBe(true)
      })

      it('should support token_usage', () => {
        expect(supportsFeature('claude', 'token_usage')).toBe(true)
      })

      it('should support token_budget_thinking', () => {
        expect(supportsFeature('claude', 'token_budget_thinking')).toBe(true)
      })

      it('should not support effort_level_thinking', () => {
        expect(supportsFeature('claude', 'effort_level_thinking')).toBe(false)
      })
    })

    describe('Codex features', () => {
      it('should support thinking', () => {
        expect(supportsFeature('codex', 'thinking')).toBe(true)
      })

      it('should not support sub_agents', () => {
        expect(supportsFeature('codex', 'sub_agents')).toBe(false)
      })

      it('should support mcp', () => {
        expect(supportsFeature('codex', 'mcp')).toBe(true)
      })

      it('should support sandbox', () => {
        expect(supportsFeature('codex', 'sandbox')).toBe(true)
      })

      it('should not support prompt_caching', () => {
        expect(supportsFeature('codex', 'prompt_caching')).toBe(false)
      })

      it('should support effort_level_thinking', () => {
        expect(supportsFeature('codex', 'effort_level_thinking')).toBe(true)
      })

      it('should not support token_budget_thinking', () => {
        expect(supportsFeature('codex', 'token_budget_thinking')).toBe(false)
      })
    })
  })

  describe('supportsTool', () => {
    describe('Claude tools', () => {
      it('should support Read tool', () => {
        expect(supportsTool('claude', 'Read')).toBe(true)
      })

      it('should support Write tool', () => {
        expect(supportsTool('claude', 'Write')).toBe(true)
      })

      it('should support Bash tool', () => {
        expect(supportsTool('claude', 'Bash')).toBe(true)
      })

      it('should support Task tool (sub-agents)', () => {
        expect(supportsTool('claude', 'Task')).toBe(true)
      })

      it('should not support CommandExecution (Codex tool)', () => {
        expect(supportsTool('claude', 'CommandExecution')).toBe(false)
      })
    })

    describe('Codex tools', () => {
      it('should support CommandExecution tool', () => {
        expect(supportsTool('codex', 'CommandExecution')).toBe(true)
      })

      it('should support FileChange tool', () => {
        expect(supportsTool('codex', 'FileChange')).toBe(true)
      })

      it('should support McpToolCall tool', () => {
        expect(supportsTool('codex', 'McpToolCall')).toBe(true)
      })

      it('should not support Read (Claude tool)', () => {
        expect(supportsTool('codex', 'Read')).toBe(false)
      })
    })
  })
})

// ============================================================================
// Model Validation Tests
// ============================================================================

describe('Model Validation', () => {
  describe('isValidModel', () => {
    it('should return true for valid Claude model', () => {
      expect(isValidModel('claude', 'claude-sonnet-4-5-20251101')).toBe(true)
    })

    it('should return true for valid Codex model', () => {
      expect(isValidModel('codex', 'gpt-4o')).toBe(true)
    })

    it('should return false for invalid model', () => {
      expect(isValidModel('claude', 'gpt-4o')).toBe(false)
      expect(isValidModel('codex', 'claude-sonnet-4-5-20251101')).toBe(false)
    })

    it('should return false for non-existent model', () => {
      expect(isValidModel('claude', 'fake-model')).toBe(false)
    })
  })

  describe('isValidThinkingConfig', () => {
    it('should accept Claude config for Claude backend', () => {
      const config = createClaudeThinkingConfig()
      expect(isValidThinkingConfig('claude', config)).toBe(true)
    })

    it('should accept Codex config for Codex backend', () => {
      const config = createCodexThinkingConfig()
      expect(isValidThinkingConfig('codex', config)).toBe(true)
    })

    it('should reject Claude config for Codex backend', () => {
      const config = createClaudeThinkingConfig()
      expect(isValidThinkingConfig('codex', config)).toBe(false)
    })

    it('should reject Codex config for Claude backend', () => {
      const config = createCodexThinkingConfig()
      expect(isValidThinkingConfig('claude', config)).toBe(false)
    })
  })
})

// ============================================================================
// Options Tests
// ============================================================================

describe('Options', () => {
  describe('getThinkingOptions', () => {
    it('should return token budget options for Claude', () => {
      const options = getThinkingOptions('claude')
      expect(options.length).toBeGreaterThan(0)
      expect(options[0]).toHaveProperty('value')
    })

    it('should return effort level options for Codex', () => {
      const options = getThinkingOptions('codex')
      expect(options.length).toBeGreaterThan(0)
      expect(options[0]).toHaveProperty('description')
    })
  })

  describe('getSummaryModeOptions', () => {
    it('should return summary mode options', () => {
      const options = getSummaryModeOptions()
      expect(options.length).toBe(4) // auto, concise, detailed, none
      expect(options.map(o => o.id)).toContain('auto')
      expect(options.map(o => o.id)).toContain('concise')
    })
  })

  describe('getSandboxOptions', () => {
    it('should return sandbox options', () => {
      const options = getSandboxOptions()
      expect(options.length).toBe(3)
      expect(options.map(o => o.id)).toContain('read-only')
      expect(options.map(o => o.id)).toContain('workspace-write')
      expect(options.map(o => o.id)).toContain('full-access')
    })

    it('should have labels and descriptions', () => {
      const options = getSandboxOptions()
      options.forEach(opt => {
        expect(opt.label).toBeDefined()
        expect(opt.description).toBeDefined()
      })
    })
  })
})

// ============================================================================
// Display Helper Tests
// ============================================================================

describe('Display Helpers', () => {
  describe('getBackendDisplayName', () => {
    it('should return "Claude" for claude', () => {
      expect(getBackendDisplayName('claude')).toBe('Claude')
    })

    it('should return "OpenAI Codex" for codex', () => {
      expect(getBackendDisplayName('codex')).toBe('OpenAI Codex')
    })
  })

  describe('getBackendIcon', () => {
    it('should return anthropic icon for claude', () => {
      expect(getBackendIcon('claude')).toBe('anthropic')
    })

    it('should return openai icon for codex', () => {
      expect(getBackendIcon('codex')).toBe('openai')
    })
  })
})

// ============================================================================
// Capability Constants Tests
// ============================================================================

describe('Capability Constants', () => {
  describe('CLAUDE_CAPABILITIES', () => {
    it('should have correct type', () => {
      expect(CLAUDE_CAPABILITIES.type).toBe('claude')
    })

    it('should use token_budget for thinking', () => {
      expect(CLAUDE_CAPABILITIES.thinkingConfigType).toBe('token_budget')
    })

    it('should have available models', () => {
      expect(CLAUDE_CAPABILITIES.availableModels.length).toBeGreaterThan(0)
    })
  })

  describe('CODEX_CAPABILITIES', () => {
    it('should have correct type', () => {
      expect(CODEX_CAPABILITIES.type).toBe('codex')
    })

    it('should use effort_level for thinking', () => {
      expect(CODEX_CAPABILITIES.thinkingConfigType).toBe('effort_level')
    })

    it('should have sandbox modes', () => {
      expect(CODEX_CAPABILITIES.sandboxModes).toBeDefined()
      expect(CODEX_CAPABILITIES.sandboxModes!.length).toBe(3)
    })
  })
})
