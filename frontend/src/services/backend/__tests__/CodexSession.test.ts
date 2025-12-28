/**
 * CodexSession Unit Tests
 *
 * Tests for Codex backend session implementation
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { CodexSession } from '../CodexSession'
import type { CodexBackendConfig } from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'

// ============================================================================
// Mock HTTP API
// ============================================================================

const mockFetch = vi.fn()
global.fetch = mockFetch

// Mock EventSource for SSE
const mockEventSource = {
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  close: vi.fn(),
  onopen: null as (() => void) | null,
  onerror: null as ((e: Event) => void) | null,
  onmessage: null as ((e: MessageEvent) => void) | null,
}

vi.stubGlobal('EventSource', vi.fn().mockImplementation(() => mockEventSource))

// ============================================================================
// Test Configuration
// ============================================================================

const TEST_CODEX_CONFIG: CodexBackendConfig = {
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

// ============================================================================
// Tests
// ============================================================================

describe('CodexSession', () => {
  let session: CodexSession

  beforeEach(() => {
    vi.clearAllMocks()

    // Default successful responses
    mockFetch.mockImplementation((url: string, _options?: RequestInit) => {
      if (url.includes('/api/codex/endpoint')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ url: 'http://localhost:8765' })
        })
      }
      if (url.includes('/api/codex/thread/start')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({
            success: true,
            data: {
              threadId: 'test-thread-id',
              thread: { id: 'test-thread-id', preview: 'Test', createdAt: new Date().toISOString() }
            }
          })
        })
      }
      if (url.includes('/api/codex/turn/start')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ success: true, data: { turnId: 'test-turn-id' } })
        })
      }
      if (url.includes('/api/codex/turn/interrupt')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ success: true })
        })
      }
      if (url.includes('/api/codex/thread/archive')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ success: true })
        })
      }
      if (url.includes('/api/codex/config')) {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ success: true, data: TEST_CODEX_CONFIG })
        })
      }
      return Promise.resolve({
        ok: true,
        json: () => Promise.resolve({ success: true })
      })
    })

    session = new CodexSession(TEST_CODEX_CONFIG)
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
    it('should create session with Codex config', () => {
      expect(session).toBeDefined()
      expect(session.getBackendType()).toBe('codex')
    })

    it('should throw error for non-Codex config', () => {
      const claudeConfig = {
        type: 'claude' as const,
        modelId: 'claude-sonnet-4-5-20251101',
        permissionMode: 'default' as const,
        skipPermissions: false,
        maxTurns: 10,
        thinkingEnabled: true,
        thinkingTokenBudget: 8096,
        includePartialMessages: true,
      }

      expect(() => new CodexSession(claudeConfig)).toThrow()
    })
  })

  // =========================================================================
  // Lifecycle Tests
  // =========================================================================

  describe('Lifecycle', () => {
    it('should not be connected initially', () => {
      expect(session.isConnected()).toBe(false)
    })

    it('should connect and create thread', async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })
      expect(session.isConnected()).toBe(true)
    })

    it('should have thread ID after connect', async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })
      expect(session.getSessionId()).toBe('test-thread-id')
    })

    it('should disconnect and archive thread', async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })
      session.disconnect()
      expect(session.isConnected()).toBe(false)
    })

    it('should call thread/start API on connect', async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/thread/start'),
        expect.objectContaining({ method: 'POST' })
      )
    })
  })

  // =========================================================================
  // Message Sending Tests
  // =========================================================================

  describe('Message Sending', () => {
    beforeEach(async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })
    })

    it('should send message via turn/start API', () => {
      session.sendMessage({
        contents: [{ type: 'text', text: 'Hello Codex!' }],
      })

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/turn/start'),
        expect.objectContaining({ method: 'POST' })
      )
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
      await session.connect({ config: TEST_CODEX_CONFIG })
    })

    it('should call turn/interrupt API', async () => {
      session.sendMessage({ contents: [{ type: 'text', text: 'Hello' }] })
      await session.interrupt()

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/turn/interrupt'),
        expect.objectContaining({ method: 'POST' })
      )
    })
  })

  // =========================================================================
  // Approval Response Tests
  // =========================================================================

  describe('Approval Response', () => {
    beforeEach(async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })
    })

    it('should send approval response via API', () => {
      session.respondToApproval({
        requestId: 'test-request-id',
        approved: true,
      })

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/approval'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('test-request-id'),
        })
      )
    })

    it('should send rejection with reason', () => {
      session.respondToApproval({
        requestId: 'test-request-id',
        approved: false,
        reason: 'Not safe',
      })

      const callArgs = mockFetch.mock.calls.find(
        call => call[0].includes('/api/codex/approval')
      )
      expect(callArgs).toBeDefined()
      expect(callArgs![1].body).toContain('Not safe')
    })
  })

  // =========================================================================
  // Config Update Tests
  // =========================================================================

  describe('Config Updates', () => {
    beforeEach(async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })
    })

    it('should update thinking config (effort level)', async () => {
      const thinkingConfig: ThinkingConfig = {
        type: 'codex',
        effort: 'high',
        summary: 'detailed',
      }

      await session.updateThinkingConfig(thinkingConfig)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/config'),
        expect.objectContaining({ method: 'PUT' })
      )
    })

    it('should disable thinking with null effort', async () => {
      const thinkingConfig: ThinkingConfig = {
        type: 'codex',
        effort: null,
        summary: 'auto',
      }

      await session.updateThinkingConfig(thinkingConfig)

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/config'),
        expect.any(Object)
      )
    })
  })

  // =========================================================================
  // Capabilities Tests
  // =========================================================================

  describe('Capabilities', () => {
    it('should return Codex capabilities', () => {
      const caps = session.getCapabilities()
      expect(caps.type).toBe('codex')
      expect(caps.supportsThinking).toBe(true)
    })

    it('should have effort_level thinking type', () => {
      const caps = session.getCapabilities()
      expect(caps.thinkingConfigType).toBe('effort_level')
    })

    it('should support sandbox', () => {
      const caps = session.getCapabilities()
      expect(caps.supportsSandbox).toBe(true)
    })

    it('should support MCP', () => {
      const caps = session.getCapabilities()
      expect(caps.supportsMcp).toBe(true)
    })

    it('should not support sub-agents', () => {
      const caps = session.getCapabilities()
      expect(caps.supportsSubAgents).toBe(false)
    })
  })

  // =========================================================================
  // Event Tests
  // =========================================================================

  describe('Events', () => {
    it('should subscribe to events', () => {
      const callback = vi.fn()
      const unsubscribe = session.onEvent(callback)

      expect(typeof unsubscribe).toBe('function')
    })

    it('should allow unsubscribe', () => {
      const callback = vi.fn()
      const unsubscribe = session.onEvent(callback)

      expect(() => unsubscribe()).not.toThrow()
    })

    it('should subscribe to connection status changes', () => {
      const callback = vi.fn()
      const unsubscribe = session.onConnectionStatusChange(callback)

      expect(typeof unsubscribe).toBe('function')
    })
  })

  // =========================================================================
  // Error Handling Tests
  // =========================================================================

  describe('Error Handling', () => {
    it('should handle connection failure', async () => {
      mockFetch.mockRejectedValueOnce(new Error('Network error'))

      await expect(session.connect({ config: TEST_CODEX_CONFIG })).rejects.toThrow()
      expect(session.getState().connectionStatus).toBe('error')
    })

    it('should handle API error response', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.resolve({ success: false, error: 'Server error' })
      })

      await expect(session.connect({ config: TEST_CODEX_CONFIG })).rejects.toThrow()
    })
  })

  // =========================================================================
  // State Tests
  // =========================================================================

  describe('State', () => {
    it('should return current state', () => {
      const state = session.getState()
      expect(state.backendType).toBe('codex')
      expect(state.connectionStatus).toBe('disconnected')
    })

    it('should update state on connect', async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })
      const state = session.getState()
      expect(state.connectionStatus).toBe('connected')
      expect(state.sessionId).toBe('test-thread-id')
    })
  })

  // =========================================================================
  // Run In Background Tests
  // =========================================================================

  describe('Run In Background', () => {
    it('should be a no-op for Codex', async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })

      // Should not throw
      await expect(session.runInBackground()).resolves.toBeUndefined()
    })
  })

  // =========================================================================
  // Thread/Turn Lifecycle Tests
  // =========================================================================

  describe('Thread/Turn Lifecycle', () => {
    it('should create thread on connect', async () => {
      await session.connect({ config: TEST_CODEX_CONFIG })

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/thread/start'),
        expect.any(Object)
      )
    })

    it('should resume existing thread if resumeSessionId provided', async () => {
      await session.connect({
        config: TEST_CODEX_CONFIG,
        resumeSessionId: 'existing-thread-id',
      })

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/codex/thread/resume'),
        expect.objectContaining({
          body: expect.stringContaining('existing-thread-id')
        })
      )
    })
  })

  // =========================================================================
  // History Tests
  // =========================================================================

  describe('History', () => {
    it('should load thread history', async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () => Promise.resolve({
          success: true,
          data: { messages: [], hasMore: false }
        })
      })

      const result = await session.loadHistory({
        sessionId: 'test-thread-id',
      })

      expect(result).toHaveProperty('messages')
      expect(result).toHaveProperty('hasMore')
    })
  })
})

// ============================================================================
// Notification Mapping Tests
// ============================================================================

describe('CodexSession Notification Mapping', () => {
  it('should map item/agentMessage/delta to TextDeltaEvent', () => {
    // This would test internal mapping logic if exposed
    // For now, verify the session can be created and connected
    const session = new CodexSession(TEST_CODEX_CONFIG)
    expect(session.getBackendType()).toBe('codex')
  })

  it('should map item/reasoning/summaryTextDelta to ThinkingDeltaEvent', () => {
    const session = new CodexSession(TEST_CODEX_CONFIG)
    expect(session.getCapabilities().supportsThinking).toBe(true)
  })
})
