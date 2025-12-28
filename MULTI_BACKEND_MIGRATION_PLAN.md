# Multi-Backend Architecture Migration Plan

This document provides a detailed implementation plan for supporting multiple AI backends (Claude and OpenAI Codex) in Claude Code Plus.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Key Requirements](#2-key-requirements)
3. [Implementation Phases](#3-implementation-phases)
4. [Detailed TODO List](#4-detailed-todo-list)
5. [File Modification Guide](#5-file-modification-guide)
6. [Testing Strategy](#6-testing-strategy)

---

## 1. Architecture Overview

### 1.1 Current Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                      │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │  sessionStore   │  │  settingsStore  │                   │
│  │  (Tab 管理)     │  │  (全局设置)     │                   │
│  └────────┬────────┘  └─────────────────┘                   │
│           │                                                  │
│  ┌────────▼────────┐                                        │
│  │  useSessionTab  │ ← 每个 Tab 独立管理状态/连接/消息       │
│  │  (Composable)   │                                        │
│  └────────┬────────┘                                        │
│           │                                                  │
│  ┌────────▼────────┐                                        │
│  │  RSocketSession │ ← 直接耦合 Claude SDK                   │
│  └────────┬────────┘                                        │
└───────────┼─────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────┐
│              Backend (Kotlin + Claude Agent SDK)             │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Target Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend (Vue 3)                      │
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │  sessionStore   │  │  settingsStore  │                   │
│  │  + backendType  │  │  + 后端配置     │                   │
│  └────────┬────────┘  └─────────────────┘                   │
│           │                                                  │
│  ┌────────▼────────┐                                        │
│  │  useSessionTab  │                                        │
│  │  + backendType  │ ← 每个 Tab 绑定一个后端类型             │
│  └────────┬────────┘                                        │
│           │                                                  │
│  ┌────────▼────────────────────────────────────┐            │
│  │           BackendSession (Interface)         │            │
│  │  ┌──────────────────┐ ┌──────────────────┐  │            │
│  │  │ ClaudeSession    │ │ CodexSession     │  │            │
│  │  │ (RSocket)        │ │ (JSON-RPC/stdio) │  │            │
│  │  └────────┬─────────┘ └────────┬─────────┘  │            │
│  └───────────┼────────────────────┼────────────┘            │
└──────────────┼────────────────────┼─────────────────────────┘
               │                    │
               ▼                    ▼
┌──────────────────────┐  ┌──────────────────────────────────┐
│  Kotlin Backend      │  │  Codex App Server (Rust)         │
│  (Claude SDK)        │  │  (JSON-RPC over stdio)           │
└──────────────────────┘  └──────────────────────────────────┘
```

---

## 2. Key Requirements

### 2.1 Session-Level Backend Binding

- Each Tab can select its own backend type
- Backend type is immutable once session starts (must create new session to switch)
- Session history is not transferable between backends

### 2.2 Differentiated UI

| Feature | Claude | Codex |
|---------|--------|-------|
| **Thinking Config** | Token budget (number input) | Effort level (dropdown: minimal/low/medium/high/xhigh) |
| **Thinking Display** | Boolean on/off toggle | Effort level selector |
| **Models** | Opus/Sonnet/Haiku | GPT-5.1-codex-max, o3, gpt-4o, etc. |
| **Sandbox** | N/A (permission-based) | ReadOnly/WorkspaceWrite/FullAccess |
| **Tool Types** | Read/Write/Edit/Bash/Task | CommandExecution/FileChange/McpToolCall |

### 2.3 Configuration

- Global default backend setting in IDEA settings
- Per-session backend selection in UI
- Backend-specific configuration options

---

## 3. Implementation Phases

### Phase 1: Type System & Interfaces (Foundation)

**Goal**: Define backend abstraction layer types and interfaces

**Duration**: 2-3 days

**Tasks**:
1. Define `BackendType` enum
2. Define `BackendSession` interface
3. Define backend-specific configuration types
4. Define backend capability types

### Phase 2: Backend Session Implementations

**Goal**: Implement concrete session classes for each backend

**Duration**: 5-7 days

**Tasks**:
1. Refactor `RSocketSession` to implement `BackendSession`
2. Implement `CodexSession` for Codex backend
3. Create `BackendSessionFactory`
4. Integrate Codex App Server process management

### Phase 3: Frontend Store & Composable Updates

**Goal**: Update frontend to support backend selection per Tab

**Duration**: 3-4 days

**Tasks**:
1. Update `useSessionTab` to include `backendType`
2. Update `sessionStore` for backend-aware session creation
3. Update `settingsStore` for backend configuration
4. Create backend capability service

### Phase 4: UI Components

**Goal**: Create differentiated UI based on backend type

**Duration**: 4-5 days

**Tasks**:
1. Create `BackendSelector` component
2. Create `ThinkingConfigPanel` with backend variants
3. Update `ModelSelector` for backend-specific models
4. Update tool display components for backend-specific tools
5. Add backend indicator in Tab UI

### Phase 5: Kotlin Backend Integration

**Goal**: Add Codex backend support in Kotlin layer

**Duration**: 5-7 days

**Tasks**:
1. Create Codex process manager
2. Implement JSON-RPC client for Codex
3. Create backend adapter for unified API
4. Add IDEA settings for Codex configuration

### Phase 6: Testing & Polish

**Goal**: Ensure stability and smooth UX

**Duration**: 3-4 days

**Tasks**:
1. Unit tests for backend sessions
2. Integration tests for backend switching
3. E2E tests for complete workflows
4. UX polish and edge case handling

---

## 4. Detailed TODO List

### 4.1 Phase 1: Type System & Interfaces

#### TODO 1.1: Define Backend Types
```
File: frontend/src/types/backend.ts (NEW)

- [ ] Define BackendType enum: 'claude' | 'codex'
- [ ] Define BackendCapabilities interface
- [ ] Define BackendConfig interface (base)
- [ ] Define ClaudeBackendConfig interface
- [ ] Define CodexBackendConfig interface
- [ ] Define BackendEvent union type
- [ ] Define BackendSessionState interface
```

#### TODO 1.2: Define Backend Session Interface
```
File: frontend/src/services/backend/BackendSession.ts (NEW)

- [ ] Define BackendSession interface:
  - connect(options): Promise<void>
  - disconnect(): void
  - sendMessage(message): void
  - interrupt(): Promise<void>
  - onEvent(callback): unsubscribe
  - getCapabilities(): BackendCapabilities
  - getState(): BackendSessionState
```

#### TODO 1.3: Define Thinking Configuration Types
```
File: frontend/src/types/thinking.ts (NEW)

- [ ] Define ClaudeThinkingConfig (tokenBudget: number, enabled: boolean)
- [ ] Define CodexThinkingConfig (effort: 'minimal'|'low'|'medium'|'high'|'xhigh')
- [ ] Define ThinkingConfig = ClaudeThinkingConfig | CodexThinkingConfig
- [ ] Define helper functions: isClaudeThinking(), isCodexThinking()
```

### 4.2 Phase 2: Backend Session Implementations

#### TODO 2.1: Refactor ClaudeSession
```
File: frontend/src/services/backend/ClaudeSession.ts (NEW, refactored from RSocketSession)

- [ ] Implement BackendSession interface
- [ ] Move RSocket-specific logic from useSessionTab
- [ ] Add getCapabilities() returning Claude capabilities
- [ ] Add event normalization to BackendEvent
- [ ] Keep backward compatibility with existing code
```

#### TODO 2.2: Implement CodexSession
```
File: frontend/src/services/backend/CodexSession.ts (NEW)

- [ ] Implement BackendSession interface
- [ ] Create JSON-RPC message handlers
- [ ] Map thread/turn API to session abstraction
- [ ] Handle approval requests (commandExecution, fileChange)
- [ ] Handle streaming notifications (delta events)
- [ ] Map Codex items to BackendEvent
```

#### TODO 2.3: Create Backend Session Factory
```
File: frontend/src/services/backend/BackendSessionFactory.ts (NEW)

- [ ] Define createSession(type: BackendType, options): BackendSession
- [ ] Handle backend-specific initialization
- [ ] Manage backend process lifecycle (for Codex)
```

#### TODO 2.4: Codex Process Manager
```
File: claude-code-server/src/main/kotlin/com/asakii/server/codex/CodexProcessManager.kt (NEW)

- [ ] Start/stop codex-app-server process
- [ ] Manage stdin/stdout streams
- [ ] Handle process lifecycle events
- [ ] Implement JSON-RPC message routing
```

### 4.3 Phase 3: Frontend Store & Composable Updates

#### TODO 3.1: Update useSessionTab
```
File: frontend/src/composables/useSessionTab.ts

- [ ] Add backendType: Ref<BackendType> field
- [ ] Add backendConfig: Ref<BackendConfig> field
- [ ] Update TabConnectOptions to include backendType
- [ ] Create session via BackendSessionFactory based on type
- [ ] Add getBackendCapabilities() method
- [ ] Add isFeatureSupported(feature) helper
```

#### TODO 3.2: Update sessionStore
```
File: frontend/src/stores/sessionStore.ts

- [ ] Update createTab() to accept backendType parameter
- [ ] Add backend type to TabInfo interface
- [ ] Add validation: cannot switch backend on existing session
- [ ] Add getAvailableBackends() method
- [ ] Update resumeSession() to preserve backend type
```

#### TODO 3.3: Update settingsStore
```
File: frontend/src/stores/settingsStore.ts

- [ ] Add defaultBackendType to Settings
- [ ] Add claudeConfig: ClaudeBackendConfig
- [ ] Add codexConfig: CodexBackendConfig
- [ ] Add loadBackendSettings() for each backend
- [ ] Add backend-specific setting update methods
```

#### TODO 3.4: Create Backend Capability Service
```
File: frontend/src/services/backendCapabilities.ts (NEW)

- [ ] Define CLAUDE_CAPABILITIES constant
- [ ] Define CODEX_CAPABILITIES constant
- [ ] Define getCapabilities(backendType) function
- [ ] Define supportsFeature(backendType, feature) function
- [ ] Define getThinkingOptions(backendType) function
- [ ] Define getModelList(backendType) function
```

### 4.4 Phase 4: UI Components

#### TODO 4.1: Create BackendSelector Component
```
File: frontend/src/components/settings/BackendSelector.vue (NEW)

- [ ] Dropdown to select backend type
- [ ] Show backend icon/logo
- [ ] Disable when session is active (show tooltip)
- [ ] Emit 'change' event with new backend type
```

#### TODO 4.2: Create ThinkingConfigPanel Component
```
File: frontend/src/components/settings/ThinkingConfigPanel.vue (NEW)

- [ ] Detect current backend type from props/inject
- [ ] Claude variant: toggle + token budget input
- [ ] Codex variant: effort level dropdown
- [ ] Emit 'update:config' with typed config
```

#### TODO 4.3: Update ModelSelector
```
File: frontend/src/components/chat/ModelSelector.vue

- [ ] Accept backendType prop
- [ ] Filter models based on backend type
- [ ] Show backend-specific model info
- [ ] Handle model validation on backend switch
```

#### TODO 4.4: Update ChatHeader
```
File: frontend/src/components/chat/ChatHeader.vue

- [ ] Show backend type indicator on each tab
- [ ] Add backend selector in new session creation
- [ ] Disable backend change for existing sessions
```

#### TODO 4.5: Update Tool Display Components
```
Files: frontend/src/components/tools/*.vue

- [ ] Update ToolUseDisplay.vue to handle Codex item types
- [ ] Map CommandExecution to Bash-like display
- [ ] Map FileChange to Write/Edit display
- [ ] Map McpToolCall to MCP display
- [ ] Add backend context for tool rendering
```

#### TODO 4.6: Create Backend Settings Dialog
```
File: frontend/src/components/settings/BackendSettingsDialog.vue (NEW)

- [ ] Tab panel for each backend
- [ ] Claude settings: API key, default model, thinking
- [ ] Codex settings: binary path, model provider, sandbox mode
- [ ] Validation and save functionality
```

### 4.5 Phase 5: Kotlin Backend Integration

#### TODO 5.1: Create Codex Backend Provider
```
File: claude-code-server/src/main/kotlin/com/asakii/server/codex/CodexBackendProvider.kt (NEW)

- [ ] Implement BackendProvider interface
- [ ] Manage codex-app-server process
- [ ] Handle JSON-RPC communication
- [ ] Map Codex events to unified format
```

#### TODO 5.2: Create JSON-RPC Client
```
File: claude-code-server/src/main/kotlin/com/asakii/server/codex/JsonRpcClient.kt (NEW)

- [ ] Handle stdin/stdout communication
- [ ] Implement request/response matching
- [ ] Handle notifications (streaming events)
- [ ] Implement server request handling (approvals)
```

#### TODO 5.3: Update HTTP API
```
File: claude-code-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt

- [ ] Add /api/codex/* endpoints
- [ ] Add backend selection to session creation
- [ ] Route requests based on backend type
- [ ] Add Codex-specific configuration endpoints
```

#### TODO 5.4: Add IDEA Settings for Codex
```
File: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/settings/CodexConfigurable.kt (NEW)

- [ ] Codex binary path configuration
- [ ] Default model provider
- [ ] Sandbox mode setting
- [ ] MCP server configuration for Codex
```

#### TODO 5.5: Update Plugin Config
```
File: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/PluginConfig.kt

- [ ] Add CodexSettings data class
- [ ] Add defaultBackendType setting
- [ ] Add codexBinaryPath setting
- [ ] Add codexModelProvider setting
```

### 4.6 Phase 6: Testing & Polish

#### TODO 6.1: Unit Tests
```
Files: frontend/src/**/*.test.ts (NEW)

- [ ] BackendSession interface compliance tests
- [ ] ClaudeSession unit tests
- [ ] CodexSession unit tests
- [ ] BackendSessionFactory tests
- [ ] backendCapabilities tests
```

#### TODO 6.2: Integration Tests
```
Files: tests/integration/*.test.ts (NEW)

- [ ] Backend switching behavior
- [ ] Session creation with different backends
- [ ] Message flow for each backend
- [ ] Permission handling for each backend
```

#### TODO 6.3: E2E Tests
```
Files: tests/e2e/*.spec.ts (NEW)

- [ ] Create session with Claude backend
- [ ] Create session with Codex backend
- [ ] Send message and receive response
- [ ] Tool approval workflow
- [ ] Backend settings configuration
```

#### TODO 6.4: UX Polish
```
- [ ] Loading states during backend initialization
- [ ] Error handling for backend failures
- [ ] Graceful degradation when backend unavailable
- [ ] Clear messaging for backend limitations
- [ ] Tooltips explaining backend differences
```

---

## 5. File Modification Guide

### 5.1 New Files to Create

| Path | Purpose |
|------|---------|
| `frontend/src/types/backend.ts` | Backend type definitions |
| `frontend/src/types/thinking.ts` | Thinking configuration types |
| `frontend/src/services/backend/BackendSession.ts` | Session interface |
| `frontend/src/services/backend/ClaudeSession.ts` | Claude implementation |
| `frontend/src/services/backend/CodexSession.ts` | Codex implementation |
| `frontend/src/services/backend/BackendSessionFactory.ts` | Factory |
| `frontend/src/services/backendCapabilities.ts` | Capability service |
| `frontend/src/components/settings/BackendSelector.vue` | Backend selector |
| `frontend/src/components/settings/ThinkingConfigPanel.vue` | Thinking config |
| `frontend/src/components/settings/BackendSettingsDialog.vue` | Settings dialog |
| `claude-code-server/.../codex/CodexProcessManager.kt` | Process manager |
| `claude-code-server/.../codex/CodexBackendProvider.kt` | Backend provider |
| `claude-code-server/.../codex/JsonRpcClient.kt` | JSON-RPC client |
| `jetbrains-plugin/.../settings/CodexConfigurable.kt` | IDEA settings |

### 5.2 Files to Modify

| Path | Changes |
|------|---------|
| `frontend/src/composables/useSessionTab.ts` | Add backendType, use BackendSession |
| `frontend/src/stores/sessionStore.ts` | Backend-aware session creation |
| `frontend/src/stores/settingsStore.ts` | Backend configuration |
| `frontend/src/types/settings.ts` | Add backend settings |
| `frontend/src/components/chat/ChatHeader.vue` | Backend indicator |
| `frontend/src/components/chat/ModelSelector.vue` | Backend-specific models |
| `frontend/src/components/tools/*.vue` | Backend-specific rendering |
| `claude-code-server/.../HttpApiServer.kt` | Codex API endpoints |
| `jetbrains-plugin/.../PluginConfig.kt` | Codex settings |

---

## 6. Testing Strategy

### 6.1 Unit Testing

```typescript
// Example: BackendSession compliance test
describe('ClaudeSession', () => {
  it('should implement BackendSession interface', () => {
    const session = new ClaudeSession(config);
    expect(session.connect).toBeInstanceOf(Function);
    expect(session.disconnect).toBeInstanceOf(Function);
    expect(session.sendMessage).toBeInstanceOf(Function);
    expect(session.interrupt).toBeInstanceOf(Function);
    expect(session.onEvent).toBeInstanceOf(Function);
    expect(session.getCapabilities).toBeInstanceOf(Function);
  });
});
```

### 6.2 Integration Testing

```typescript
// Example: Backend switching test
describe('Backend Switching', () => {
  it('should prevent switching backend on active session', async () => {
    const store = useSessionStore();
    await store.createTab('Test', { backendType: 'claude' });

    // Start a conversation
    await store.currentTab?.sendMessage({ text: 'Hello' });

    // Attempt to switch backend should fail
    expect(() => {
      store.currentTab?.setBackendType('codex');
    }).toThrow('Cannot switch backend on active session');
  });
});
```

### 6.3 E2E Testing

```typescript
// Example: Full workflow test
describe('Codex Backend Workflow', () => {
  it('should complete a conversation with Codex', async () => {
    // 1. Create session with Codex backend
    await page.click('[data-test="new-session"]');
    await page.selectOption('[data-test="backend-selector"]', 'codex');

    // 2. Send message
    await page.fill('[data-test="message-input"]', 'Hello');
    await page.click('[data-test="send-button"]');

    // 3. Wait for response
    await page.waitForSelector('[data-test="assistant-message"]');

    // 4. Verify response
    const response = await page.textContent('[data-test="assistant-message"]');
    expect(response).toBeTruthy();
  });
});
```

---

## Summary

This migration plan provides a comprehensive roadmap for adding multi-backend support to Claude Code Plus. The key architectural decisions are:

1. **Backend abstraction at session level** - Each Tab binds to one backend type
2. **No cross-backend session transfer** - Switching backends requires new session
3. **Differentiated UI based on capabilities** - Each backend shows its specific options
4. **Gradual migration** - Existing Claude functionality remains unchanged

The implementation is divided into 6 phases with approximately 4-6 weeks of total effort. The modular approach allows for parallel development and incremental testing.
