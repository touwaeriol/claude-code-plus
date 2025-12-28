/**
 * ClaudeSession Unit Tests
 *
 * Tests for Claude backend session implementation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ClaudeSession } from '../ClaudeSession'
import type { ClaudeBackendConfig } from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'

// ============================================================================
// Mock RSocketSession
// ============================================================================

const mockRSocketSession = {
  connect: vi.fn().mockResolvedValue('test-session-id'),
  disconnect: vi.fn(),
  sendMessage: vi.fn(),
  sendMessageWithContent: vi.fn().mockResolvedValue(undefined),
  interrupt: vi.fn().mockResolvedValue(undefined),
  runInBackground: vi.fn().mockResolvedValue(undefined),
  setMaxThinkingTokens: vi.fn().mockResolvedValue(undefined),
  setModel: vi.fn().mockResolvedValue(undefined),
  onMessage: vi.fn(),
  onDisconnect: vi.fn(),
  register: vi.fn(),
  capabilities: null,
  connected: false,
}

vi.mock('../rsocket/RSocketSession', () => ({
  RSocketSession: vi.fn().mockImplementation(() => mockRSocketSession),
}))

// ============================================================================
// Test Configuration
// ============================================================================

const TEST_CLAUDE_CONFIG: ClaudeBackendConfig = {
  type: 'claude',
  modelId: 'claude-sonnet-4-5-20251101',
  permissionMode: 'default',
  skipPermissions: false,
  maxTurns: 10,
  thinkingEnabled: true,
  thinkingTokenBudget: 8096,
  includePartialMessages: true,
}

// ============================================================================
// Tests
// ============================================================================

describe('ClaudeSession', () => {
  let session: ClaudeSession

  beforeEach(() => {
    vi.clearAllMocks()
    session = new ClaudeSession(TEST_CLAUDE_CONFIG)
  })

  afterEach(() => {
    if (session.isConnected()) {
      session.disconnect()
    }
  })

  // =========================================================================
  // Constructor Tests
  // =========================================================================

  describe('Constructor', () => {
    it('should create session with Claude config', () => {
      expect(session).toBeDefined()
      expect(session.getBackendType()).toBe('claude')
    })

    it('should throw error for non-Claude config', () => {
      const codexConfig = {
        type: 'codex' as const,
        modelId: 'gpt-4o',
        modelProvider: 'openai' as const,
        permissionMode: 'default' as const,
        skipPermissions: false,
        maxTurns: 10,
        reasoningEffort: 'medium' as const,
        reasoningSummary: 'auto' as const,
        sandboxMode: 'workspace-write' as const,
      }

      expect(() => new ClaudeSession(codexConfig)).toThrow('ClaudeSession requires ClaudeBackendConfig')
    })

    it('should accept optional websocket URL', () => {
      const sessionWithUrl = new ClaudeSession(TEST_CLAUDE_CONFIG, 'ws://localhost:8765')
      expect(sessionWithUrl).toBeDefined()
    })
  })

  // =========================================================================
  // Lifecycle Tests
  // =========================================================================

  describe('Lifecycle', () => {
    it('should not be connected initially', () => {
      expect(session.isConnected()).toBe(false)
    })

    it('should connect successfully', async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
      expect(session.isConnected()).toBe(true)
    })

    it('should have session ID after connect', async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
      expect(session.getSessionId()).toBe('test-session-id')
    })

    it('should disconnect gracefully', async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
      session.disconnect()
      expect(session.isConnected()).toBe(false)
    })

    it('should clear session ID after disconnect', async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
      session.disconnect()
      expect(session.getSessionId()).toBeNull()
    })
  })

  // =========================================================================
  // Message Sending Tests
  // =========================================================================

  describe('Message Sending', () => {
    beforeEach(async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
    })

    it('should send text message', () => {
      session.sendMessage({
        contents: [{ type: 'text', text: 'Hello Claude!' }],
      })

      expect(mockRSocketSession.sendMessageWithContent).toHaveBeenCalled()
    })

    it('should send message with file context', () => {
      session.sendMessage({
        contents: [{ type: 'text', text: 'Review this file' }],
        contexts: [{ type: 'file', path: '/src/main.ts', content: 'console.log("Hello")' }],
      })

      expect(mockRSocketSession.sendMessageWithContent).toHaveBeenCalled()
    })

    it('should set generating state on message send', () => {
      session.sendMessage({
        contents: [{ type: 'text', text: 'Hello' }],
      })

      expect(session.getState().isGenerating).toBe(true)
    })
  })

  // =========================================================================
  // Interrupt Tests
  // =========================================================================

  describe('Interrupt', () => {
    beforeEach(async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
    })

    it('should call RSocket interrupt', async () => {
      await session.interrupt()
      expect(mockRSocketSession.interrupt).toHaveBeenCalled()
    })
  })

  // =========================================================================
  // Run In Background Tests
  // =========================================================================

  describe('Run In Background', () => {
    beforeEach(async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
    })

    it('should call RSocket runInBackground', async () => {
      await session.runInBackground()
      expect(mockRSocketSession.runInBackground).toHaveBeenCalled()
    })
  })

  // =========================================================================
  // Config Update Tests
  // =========================================================================

  describe('Config Updates', () => {
    beforeEach(async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
    })

    it('should update thinking config', async () => {
      const thinkingConfig: ThinkingConfig = {
        type: 'claude',
        enabled: true,
        tokenBudget: 16384,
      }

      await session.updateThinkingConfig(thinkingConfig)
      expect(mockRSocketSession.setMaxThinkingTokens).toHaveBeenCalledWith(16384)
    })

    it('should disable thinking with 0 tokens', async () => {
      const thinkingConfig: ThinkingConfig = {
        type: 'claude',
        enabled: false,
        tokenBudget: 0,
      }

      await session.updateThinkingConfig(thinkingConfig)
      expect(mockRSocketSession.setMaxThinkingTokens).toHaveBeenCalledWith(0)
    })
  })

  // =========================================================================
  // Capabilities Tests
  // =========================================================================

  describe('Capabilities', () => {
    it('should return Claude capabilities', () => {
      const caps = session.getCapabilities()
      expect(caps.type).toBe('claude')
      expect(caps.supportsThinking).toBe(true)
      expect(caps.thinkingConfigType).toBe('token_budget')
    })

    it('should have token_budget thinking type', () => {
      const caps = session.getCapabilities()
      expect(caps.thinkingConfigType).toBe('token_budget')
    })

    it('should support MCP', () => {
      const caps = session.getCapabilities()
      expect(caps.supportsMcp).toBe(true)
    })

    it('should not support sandbox', () => {
      const caps = session.getCapabilities()
      expect(caps.supportsSandbox).toBe(false)
    })
  })

  // =========================================================================
  // Event Mapping Tests
  // =========================================================================

  describe('Event Mapping', () => {
    it('should subscribe to events', async () => {
      const callback = vi.fn()
      const unsubscribe = session.onEvent(callback)

      expect(typeof unsubscribe).toBe('function')
    })

    it('should allow unsubscribe', () => {
      const callback = vi.fn()
      const unsubscribe = session.onEvent(callback)

      // Should not throw
      expect(() => unsubscribe()).not.toThrow()
    })
  })

  // =========================================================================
  // Error Handling Tests
  // =========================================================================

  describe('Error Handling', () => {
    it('should handle connection failure', async () => {
      mockRSocketSession.connect.mockRejectedValueOnce(new Error('Connection failed'))

      await expect(session.connect({ config: TEST_CLAUDE_CONFIG })).rejects.toThrow('Connection failed')
      expect(session.getState().connectionStatus).toBe('error')
    })
  })

  // =========================================================================
  // State Tests
  // =========================================================================

  describe('State', () => {
    it('should return current state', () => {
      const state = session.getState()
      expect(state.backendType).toBe('claude')
      expect(state.connectionStatus).toBe('disconnected')
    })

    it('should update state on connect', async () => {
      await session.connect({ config: TEST_CLAUDE_CONFIG })
      const state = session.getState()
      expect(state.connectionStatus).toBe('connected')
      expect(state.sessionId).toBe('test-session-id')
    })
  })

  // =========================================================================
  // History Tests
  // =========================================================================

  describe('History', () => {
    it('should load history', async () => {
      const result = await session.loadHistory({
        sessionId: 'test-session',
        projectPath: '/test/project',
      })

      expect(result).toHaveProperty('messages')
      expect(result).toHaveProperty('hasMore')
    })
  })
})

// ============================================================================
// Factory Function Tests
// ============================================================================

describe('createClaudeSession', () => {
  it('should be importable', async () => {
    const module = await import('../ClaudeSession')
    expect(module.createClaudeSession).toBeDefined()
  })
})
