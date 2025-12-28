/**
 * BackendSession Unit Tests
 *
 * Tests for BaseBackendSession, event emission, and state management
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  BaseBackendSession,
  type BackendSession,
  type SessionConnectOptions,
  type UserMessage,
  type ApprovalResponse,
  type HistoryLoadOptions,
  type HistoryLoadResult,
} from '../BackendSession'
import type { BackendConfig, BackendCapabilities, BackendEvent, BackendConnectionStatus } from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'
import { DEFAULT_CLAUDE_CONFIG } from '@/types/backend'
import { CLAUDE_CAPABILITIES } from '@/services/backendCapabilities'

// ============================================================================
// Test Implementation of BaseBackendSession
// ============================================================================

/**
 * Concrete implementation for testing
 */
class TestSession extends BaseBackendSession {
  public connectCalled = false
  public disconnectCalled = false
  public sendMessageCalled = false
  public interruptCalled = false

  constructor(config: BackendConfig = DEFAULT_CLAUDE_CONFIG) {
    super('claude', config)
  }

  async connect(_options: SessionConnectOptions): Promise<void> {
    this.connectCalled = true
    this.setConnectionStatus('connected')
    this.state.sessionId = 'test-session-id'
  }

  disconnect(): void {
    this.disconnectCalled = true
    this.setConnectionStatus('disconnected')
    this.state.sessionId = null
  }

  sendMessage(_message: UserMessage): void {
    this.sendMessageCalled = true
    this.state.isGenerating = true
  }

  async interrupt(): Promise<void> {
    this.interruptCalled = true
    this.state.isGenerating = false
  }

  respondToApproval(_response: ApprovalResponse): void {
    // Test implementation
  }

  async updateConfig(config: Partial<BackendConfig>): Promise<void> {
    this.state.config = { ...this.state.config, ...config } as BackendConfig
  }

  async updateThinkingConfig(_thinkingConfig: ThinkingConfig): Promise<void> {
    // Test implementation
  }

  getCapabilities(): BackendCapabilities {
    return CLAUDE_CAPABILITIES
  }

  async loadHistory(_options: HistoryLoadOptions): Promise<HistoryLoadResult> {
    return { messages: [], hasMore: false }
  }

  // Expose protected methods for testing
  public emitEventPublic(event: BackendEvent): void {
    this.emitEvent(event)
  }

  public setConnectionStatusPublic(status: BackendConnectionStatus): void {
    this.setConnectionStatus(status)
  }

  public setErrorPublic(error: string): void {
    this.state.lastError = error
    this.setConnectionStatus('error')
  }
}

// ============================================================================
// Tests
// ============================================================================

describe('BaseBackendSession', () => {
  let session: TestSession

  beforeEach(() => {
    session = new TestSession()
  })

  // =========================================================================
  // State Management Tests
  // =========================================================================

  describe('State Management', () => {
    it('should initialize with correct state', () => {
      const state = session.getState()
      expect(state.connectionStatus).toBe('disconnected')
      expect(state.sessionId).toBeNull()
      expect(state.isGenerating).toBe(false)
      expect(state.lastError).toBeNull()
      expect(state.backendType).toBe('claude')
    })

    it('should report not connected initially', () => {
      expect(session.isConnected()).toBe(false)
    })

    it('should report connected after connect', async () => {
      await session.connect({ config: DEFAULT_CLAUDE_CONFIG })
      expect(session.isConnected()).toBe(true)
    })

    it('should report disconnected after disconnect', async () => {
      await session.connect({ config: DEFAULT_CLAUDE_CONFIG })
      session.disconnect()
      expect(session.isConnected()).toBe(false)
    })

    it('should update session ID on connect', async () => {
      await session.connect({ config: DEFAULT_CLAUDE_CONFIG })
      expect(session.getSessionId()).toBe('test-session-id')
    })

    it('should clear session ID on disconnect', async () => {
      await session.connect({ config: DEFAULT_CLAUDE_CONFIG })
      session.disconnect()
      expect(session.getSessionId()).toBeNull()
    })

    it('should return backend type', () => {
      expect(session.getBackendType()).toBe('claude')
    })
  })

  // =========================================================================
  // Event Emission Tests
  // =========================================================================

  describe('Event Emission', () => {
    it('should emit events to subscribers', () => {
      const callback = vi.fn()
      session.onEvent(callback)

      const event: BackendEvent = {
        type: 'text_delta',
        sessionId: 'test-session-id',
        itemId: 'test-item',
        text: 'Hello',
        timestamp: Date.now(),
      }
      session.emitEventPublic(event)

      expect(callback).toHaveBeenCalledWith(event)
    })

    it('should support multiple subscribers', () => {
      const callback1 = vi.fn()
      const callback2 = vi.fn()
      session.onEvent(callback1)
      session.onEvent(callback2)

      const event: BackendEvent = {
        type: 'text_delta',
        sessionId: 'test-session-id',
        itemId: 'test-item',
        text: 'Hello',
        timestamp: Date.now(),
      }
      session.emitEventPublic(event)

      expect(callback1).toHaveBeenCalledWith(event)
      expect(callback2).toHaveBeenCalledWith(event)
    })

    it('should allow unsubscribe', () => {
      const callback = vi.fn()
      const unsubscribe = session.onEvent(callback)
      unsubscribe()

      const event: BackendEvent = {
        type: 'text_delta',
        sessionId: 'test-session-id',
        itemId: 'test-item',
        text: 'Hello',
        timestamp: Date.now(),
      }
      session.emitEventPublic(event)

      expect(callback).not.toHaveBeenCalled()
    })

    it('should emit connection status changes', () => {
      const callback = vi.fn()
      session.onConnectionStatusChange(callback)

      session.setConnectionStatusPublic('connecting')
      expect(callback).toHaveBeenCalledWith('connecting')

      session.setConnectionStatusPublic('connected')
      expect(callback).toHaveBeenCalledWith('connected')
    })

    it('should allow unsubscribe from status changes', () => {
      const callback = vi.fn()
      const unsubscribe = session.onConnectionStatusChange(callback)
      unsubscribe()

      session.setConnectionStatusPublic('connected')
      expect(callback).not.toHaveBeenCalled()
    })
  })

  // =========================================================================
  // Error Handling Tests
  // =========================================================================

  describe('Error Handling', () => {
    it('should set error and update status', () => {
      session.setErrorPublic('Test error message')

      const state = session.getState()
      expect(state.lastError).toBe('Test error message')
      expect(state.connectionStatus).toBe('error')
    })

    it('should emit error status change', () => {
      const callback = vi.fn()
      session.onConnectionStatusChange(callback)

      session.setErrorPublic('Test error')
      expect(callback).toHaveBeenCalledWith('error')
    })
  })

  // =========================================================================
  // Lifecycle Tests
  // =========================================================================

  describe('Lifecycle', () => {
    it('should call connect implementation', async () => {
      await session.connect({ config: DEFAULT_CLAUDE_CONFIG })
      expect(session.connectCalled).toBe(true)
    })

    it('should call disconnect implementation', () => {
      session.disconnect()
      expect(session.disconnectCalled).toBe(true)
    })

    it('should call sendMessage implementation', () => {
      session.sendMessage({ contents: [{ type: 'text', text: 'Hello' }] })
      expect(session.sendMessageCalled).toBe(true)
    })

    it('should call interrupt implementation', async () => {
      await session.interrupt()
      expect(session.interruptCalled).toBe(true)
    })
  })

  // =========================================================================
  // runInBackground Tests
  // =========================================================================

  describe('runInBackground', () => {
    it('should be a no-op by default', async () => {
      // Should not throw
      await expect(session.runInBackground()).resolves.toBeUndefined()
    })
  })

  // =========================================================================
  // Config Update Tests
  // =========================================================================

  describe('updateConfig', () => {
    it('should update configuration', async () => {
      await session.updateConfig({ maxTurns: 20 })
      const state = session.getState()
      expect(state.config.maxTurns).toBe(20)
    })
  })

  // =========================================================================
  // Capabilities Tests
  // =========================================================================

  describe('getCapabilities', () => {
    it('should return backend capabilities', () => {
      const caps = session.getCapabilities()
      expect(caps.type).toBe('claude')
      expect(caps.supportsThinking).toBe(true)
    })
  })

  // =========================================================================
  // History Tests
  // =========================================================================

  describe('loadHistory', () => {
    it('should return history result', async () => {
      const result = await session.loadHistory({ sessionId: 'test' })
      expect(result.messages).toEqual([])
      expect(result.hasMore).toBe(false)
    })
  })
})

// ============================================================================
// Interface Compliance Tests
// ============================================================================

describe('BackendSession Interface Compliance', () => {
  it('TestSession should implement BackendSession interface', () => {
    const session: BackendSession = new TestSession()

    // Type check - these should all exist
    expect(typeof session.connect).toBe('function')
    expect(typeof session.disconnect).toBe('function')
    expect(typeof session.isConnected).toBe('function')
    expect(typeof session.sendMessage).toBe('function')
    expect(typeof session.interrupt).toBe('function')
    expect(typeof session.runInBackground).toBe('function')
    expect(typeof session.respondToApproval).toBe('function')
    expect(typeof session.updateConfig).toBe('function')
    expect(typeof session.updateThinkingConfig).toBe('function')
    expect(typeof session.getState).toBe('function')
    expect(typeof session.getCapabilities).toBe('function')
    expect(typeof session.getBackendType).toBe('function')
    expect(typeof session.getSessionId).toBe('function')
    expect(typeof session.onEvent).toBe('function')
    expect(typeof session.onConnectionStatusChange).toBe('function')
    expect(typeof session.loadHistory).toBe('function')
  })
})
