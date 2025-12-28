# OpenAI Codex Integration Analysis

This document analyzes how OpenAI Codex App Server can implement the features currently available in Claude Code Plus, and provides implementation guidance for each feature category.

---

## Table of Contents

1. [Architecture Comparison](#1-architecture-comparison)
2. [Feature Compatibility Matrix](#2-feature-compatibility-matrix)
3. [Implementation Details by Feature](#3-implementation-details-by-feature)
4. [Integration Architecture](#4-integration-architecture)
5. [Configuration Mapping](#5-configuration-mapping)
6. [Implementation Roadmap](#6-implementation-roadmap)

---

## 1. Architecture Comparison

### 1.1 Communication Protocol Comparison

| Aspect | Claude Code Plus | OpenAI Codex App Server |
|--------|-----------------|-------------------------|
| **Primary Protocol** | WebSocket + RSocket | JSON-RPC over stdio |
| **Message Format** | Protobuf | JSON |
| **Streaming** | RSocket push stream | JSON-RPC notifications |
| **Request-Response** | RSocket request-response | JSON-RPC request/response |
| **Bidirectional** | ✅ Yes | ✅ Yes (via server requests) |

### 1.2 Session Model Comparison

| Aspect | Claude Code Plus | OpenAI Codex App Server |
|--------|-----------------|-------------------------|
| **Session Concept** | Session (multiple messages) | Thread + Turn (v2 API) |
| **State Management** | Server-side state | Server-side state |
| **Persistence** | JSONL files | Thread archive files |
| **Multi-session** | ✅ Yes | ✅ Yes (multiple threads) |
| **Resume** | Load history | `thread/resume` |

### 1.3 Tool Execution Model

| Aspect | Claude Code Plus | OpenAI Codex App Server |
|--------|-----------------|-------------------------|
| **Tool Types** | Read/Write/Edit/Bash/Task | CommandExecution/FileChange/McpToolCall |
| **Approval Flow** | PermissionRequest → Response | `**/requestApproval` → Response |
| **Sandbox** | None (relies on permissions) | ReadOnly/WorkspaceWrite/FullAccess |
| **MCP Support** | ✅ Yes | ✅ Yes |

---

## 2. Feature Compatibility Matrix

### 2.1 Frontend Interface Features

| Feature ID | Feature Name | Codex Support | Implementation Approach |
|------------|-------------|---------------|------------------------|
| **Chat Core** |
| F-CHAT-001 | Message Sending | ✅ Full | `turn/start` with `UserInput` |
| F-CHAT-002 | Streaming Response | ✅ Full | `item/agentMessage/delta` notifications |
| F-CHAT-003 | Message History | ✅ Full | `thread/list` + load thread items |
| F-CHAT-004 | Message Status | ✅ Full | `turn/completed` notification with status |
| F-CHAT-005 | Token Usage | ⚠️ Partial | Not exposed in current API (need extension) |
| F-CHAT-006 | Thinking Display | ✅ Full | `Reasoning` item type + `summaryTextDelta` |
| F-CHAT-007 | Error Dialogs | ✅ Full | `error` notifications |
| **Input Area** |
| F-INPUT-001 | Rich Text Input | ✅ Full | Frontend only (no backend changes) |
| F-INPUT-002 | Send Message | ✅ Full | `turn/start` |
| F-INPUT-003 | Force Send | ⚠️ Partial | Frontend handles pending approvals |
| F-INPUT-004 | Stop Generation | ✅ Full | `turn/interrupt` |
| F-INPUT-005 | Context Drag & Drop | ✅ Full | Frontend + `LocalImage` input type |
| F-INPUT-006 | Pending Task Display | ✅ Full | Frontend state management |
| **Context Management** |
| F-CTX-001 | @Mention Files | ✅ Full | Frontend + include in message content |
| F-CTX-002 | File Search | ✅ Full | Frontend (IDE integration) |
| F-CTX-003 | Context Tags | ✅ Full | Frontend only |
| F-CTX-004 | Line Range Selection | ✅ Full | Frontend + include in context |
| F-CTX-005 | Active File Auto-Add | ✅ Full | Frontend (IDE integration) |
| F-CTX-006 | Image Context | ✅ Full | `Image` or `LocalImage` UserInput type |
| F-CTX-007 | Auto-Clear Context | ✅ Full | Frontend configuration |
| **Session Management** |
| F-SESS-001 | Create New Session | ✅ Full | `thread/start` |
| F-SESS-002 | Session Tabs | ✅ Full | Frontend + multiple threads |
| F-SESS-003 | Rename Session | ⚠️ Partial | Need custom metadata storage |
| F-SESS-004 | Switch Session | ✅ Full | Frontend thread selection |
| F-SESS-005 | Close Session | ✅ Full | Frontend + optionally `thread/archive` |
| F-SESS-006 | Session History Overlay | ✅ Full | `thread/list` |
| F-SESS-007 | Search History | ⚠️ Partial | Frontend filtering of `thread/list` |
| F-SESS-008 | Delete History | ⚠️ Partial | Archive or manual file deletion |
| F-SESS-009 | Session Preview | ✅ Full | Thread `preview` field |
| **Tool Display** |
| F-TOOL-001 | Read Tool | ⚠️ Different | No separate Read tool, handled by agent |
| F-TOOL-002 | Write Tool | ✅ Full | `FileChange` item type |
| F-TOOL-003 | Edit Tool | ✅ Full | `FileChange` item type |
| F-TOOL-004 | MultiEdit Tool | ✅ Full | `FileChange` with multiple changes |
| F-TOOL-005 | Bash Tool | ✅ Full | `CommandExecution` item type |
| F-TOOL-006 | MCP Tool | ✅ Full | `McpToolCall` item type |
| F-TOOL-007 | Task Tool | ⚠️ Partial | No direct equivalent (need custom impl) |
| F-TOOL-008 | Tool Collapse Mode | ✅ Full | Frontend only |
| F-TOOL-009 | Tool Expand Mode | ✅ Full | Frontend only |
| F-TOOL-010 | Tool Status | ✅ Full | Item `status` field |
| **IDEA Integration** |
| F-IDEA-001 | Click to Open File | ✅ Full | Frontend + IDE bridge (unchanged) |
| F-IDEA-002 | Click to Show Diff | ✅ Full | Frontend + IDE bridge (unchanged) |
| F-IDEA-003 | Navigate to Line | ✅ Full | Frontend + IDE bridge (unchanged) |
| **Permission Management** |
| F-PERM-001 | Permission Request | ✅ Full | `**/requestApproval` server requests |
| F-PERM-002 | Parameter Preview | ✅ Full | Request params contain details |
| F-PERM-003 | Allow Operation | ✅ Full | Respond with `Approved` decision |
| F-PERM-004 | Deny Operation | ✅ Full | Respond with `Declined` decision |
| F-PERM-005 | Permission Mode Selection | ✅ Full | Via `ExecPolicyAmendment` |
| F-PERM-006 | Plan Mode | ⚠️ Partial | `turn/plan/updated` notification |
| **Permission Modes** |
| F-PERM-M01 | Default Mode | ✅ Full | `approvalPolicy: "on-request"` |
| F-PERM-M02 | Accept Edits | ⚠️ Partial | Need custom approval logic |
| F-PERM-M03 | Plan Mode | ⚠️ Partial | Via `turn/plan/updated` |
| F-PERM-M04 | Bypass Permissions | ✅ Full | `approvalPolicy: "never"` |
| **User Interaction** |
| F-ASK-001 | Claude Questions | ⚠️ Different | Agent message with question format |
| F-ASK-002 | Answer Question | ✅ Full | Send as next turn input |
| F-ASK-003 | Skip Question | ✅ Full | Send empty or skip message |
| F-ASK-004 | Multiple Choice | ⚠️ Partial | Need custom UI handling |
| **Theme & i18n** |
| F-THEME-* | All Theme Features | ✅ Full | Frontend only (unchanged) |
| F-I18N-* | All i18n Features | ✅ Full | Frontend only (unchanged) |
| **Advanced Features** |
| F-ADV-001 | Edit History Message | ⚠️ Partial | Start new turn with edited content |
| F-ADV-002 | Restart from Message | ⚠️ Partial | Start new thread or truncate |
| F-ADV-003 | Auto Compacting | ✅ Full | `model_auto_compact_token_limit` config |
| F-ADV-004 | Compacting Progress | ⚠️ Partial | Not exposed in notifications |
| F-ADV-005 | Compacted Summary | ⚠️ Partial | Context summarization internal |
| F-ADV-006 | Export Markdown | ✅ Full | Frontend (generate from thread) |
| F-ADV-007 | Export JSON | ✅ Full | Frontend (thread data is JSON) |
| F-ADV-008 | Export HTML | ✅ Full | Frontend (generate from thread) |
| **Model Selection** |
| F-MODEL-001 | Opus 4.5 | ❌ N/A | Claude-specific model |
| F-MODEL-002 | Sonnet 4.5 | ❌ N/A | Claude-specific model |
| F-MODEL-003 | Haiku 4.5 | ❌ N/A | Claude-specific model |
| F-MODEL-004 | Custom Model | ✅ Full | `thread/start` with custom `model` param |
| **Extended Thinking** |
| F-THINK-001 | Thinking Enable | ✅ Full | `model_reasoning_effort` config |
| F-THINK-002 | Token Budget | ⚠️ Different | Via `effort` ("minimal" to "xhigh") |
| F-THINK-003 | Real-time Display | ✅ Full | `Reasoning` item + delta notifications |
| F-THINK-004 | Thinking Collapse | ✅ Full | Frontend only |

### 2.2 IDEA Configuration Features

| Feature ID | Feature Name | Codex Support | Implementation Approach |
|------------|-------------|---------------|------------------------|
| **Main Settings** |
| C-MAIN-001 | API Key | ✅ Full | `OPENAI_API_KEY` env var |
| C-MAIN-002 | Default Model | ✅ Full | `model` in config |
| C-MAIN-003 | Default Permission Mode | ✅ Full | `approval_policy` in config |
| C-MAIN-004 | Auto Accept Permissions | ✅ Full | `approval_policy: "never"` |
| C-MAIN-005 | Thinking Enable | ✅ Full | `model_reasoning_effort` |
| C-MAIN-006 | Thinking Token Budget | ⚠️ Different | Via `effort` levels |
| C-MAIN-007 | System Prompt | ✅ Full | `base_instructions` or `developer_instructions` |
| C-MAIN-008 | Max Turns | ⚠️ Partial | Need custom implementation |
| C-MAIN-009 | Max Tokens | ⚠️ Partial | Model-dependent |
| C-MAIN-010 | Temperature | ⚠️ Partial | Not in current API (model default) |
| C-MAIN-011 | Verbose Logging | ✅ Full | Local logging config |
| C-MAIN-012 | Skip Confirmation | ✅ Full | `approval_policy: "never"` |
| **MCP Settings** |
| C-MCP-001 | MCP Server List | ✅ Full | `mcp_servers` in config |
| C-MCP-002 | Add MCP Server | ✅ Full | Config file editing |
| C-MCP-003 | Delete MCP Server | ✅ Full | Config file editing |
| C-MCP-004 | MCP Command | ✅ Full | `mcp_servers[name].command` |
| C-MCP-005 | MCP Env Variables | ✅ Full | `mcp_servers[name].env` |
| C-MCP-006 | Terminal MCP Config | ✅ Full | Via MCP server config |
| **Claude Code Settings** |
| C-CLI-001 | Node.js Path | ⚠️ Different | Uses Rust binary, not Node.js |
| C-CLI-002 | Claude CLI Path | ⚠️ Different | Uses `codex-app-server` binary |
| C-CLI-003 | Auto Detect CLI | ✅ Full | Detect `codex-app-server` in PATH |
| **Git Generate Settings** |
| C-GIT-001 | Enable Git Generate | ✅ Full | Custom implementation needed |
| C-GIT-002 | Custom System Prompt | ✅ Full | Use in generate request |
| C-GIT-003 | Auto Commit | ✅ Full | Frontend/IDE integration |
| **Environment Detection** |
| C-ENV-001 | Node.js Detection | ❌ N/A | Not needed (Rust binary) |
| C-ENV-002 | Version Validation | ✅ Full | Check `codex-app-server --version` |
| C-ENV-003 | CLI Detection | ✅ Full | Detect `codex-app-server` |
| C-ENV-004 | CLI Version Check | ✅ Full | Version command |
| C-ENV-005 | Init Prompt | ✅ Full | Frontend implementation |
| **IDE APIs** |
| C-API-* | All IDE APIs | ✅ Full | Frontend/IDE unchanged |
| **Git Integration** |
| C-GIT-* | All Git Features | ✅ Full | Frontend/IDE unchanged + custom generate |
| **Code Analysis** |
| C-JAVA-* | All Java Features | ✅ Full | Frontend/IDE unchanged |
| **Multi-Version** |
| Compat | All Versions | ✅ Full | Plugin unchanged |

### 2.3 Backend SDK Features

| Feature ID | Feature Name | Codex Support | Implementation Approach |
|------------|-------------|---------------|------------------------|
| **Session Management** |
| B-SESS-001 | Create Session | ✅ Full | `thread/start` |
| B-SESS-002 | Session State | ✅ Full | Thread state tracking |
| B-SESS-003 | Session Interrupt | ✅ Full | `turn/interrupt` |
| B-SESS-004 | Parallel Sessions | ✅ Full | Multiple threads |
| **Message Processing** |
| B-MSG-001 | Streaming Messages | ✅ Full | JSON-RPC notifications |
| B-MSG-002 | Partial Messages | ✅ Full | Delta notifications |
| B-MSG-003 | Message Encoding | ⚠️ Different | JSON instead of Protobuf |
| B-MSG-004 | Message History | ✅ Full | Thread items |
| **Tools** |
| B-TOOL-001 | Read | ⚠️ Internal | Handled by agent, not exposed |
| B-TOOL-002 | Write | ✅ Full | FileChange |
| B-TOOL-003 | Edit | ✅ Full | FileChange |
| B-TOOL-004 | MultiEdit | ✅ Full | FileChange with multiple changes |
| B-TOOL-005 | Bash | ✅ Full | CommandExecution |
| B-TOOL-006 | Task | ⚠️ Partial | No sub-agent concept |
| **MCP** |
| B-MCP-* | All MCP Features | ✅ Full | Native MCP support |
| **Streaming** |
| B-STREAM-* | All Streaming | ✅ Full | JSON-RPC notifications |
| **Response Types** |
| B-TYPE-001 | Text | ✅ Full | AgentMessage item |
| B-TYPE-002 | Thinking | ✅ Full | Reasoning item |
| B-TYPE-003 | Tool Call | ✅ Full | Various item types |
| B-TYPE-004 | Tool Result | ✅ Full | Item completion |
| B-TYPE-005 | Image | ⚠️ Partial | ImageView item type |
| B-TYPE-006 | Error | ✅ Full | Error notifications |
| **Token Management** |
| B-TOKEN-* | Token Stats | ⚠️ Limited | Not exposed in current API |
| **History** |
| B-HIST-001 | Storage | ✅ Full | Thread files |
| B-HIST-002 | Session Level | ✅ Full | Per-thread |
| B-HIST-003 | Project Level | ⚠️ Partial | Via cwd in thread |
| B-HIST-004 | Load History | ✅ Full | `thread/resume` |
| B-HIST-005 | Get Sessions | ✅ Full | `thread/list` |
| B-HIST-006 | Delete History | ⚠️ Partial | Archive only |
| B-HIST-007 | Truncate | ⚠️ Partial | Start new thread |
| **Advanced** |
| B-ADV-001 | Thinking Enable | ✅ Full | `model_reasoning_effort` |
| B-ADV-002 | Thinking Budget | ⚠️ Different | Effort levels |
| B-ADV-003 | Thinking Display | ✅ Full | Reasoning item |
| B-ADV-004-006 | Prompt Caching | ⚠️ Model-dep | Depends on model support |
| B-ADV-007-010 | Sub-agents | ❌ No | No equivalent concept |
| **Configuration** |
| B-CFG-001 | Default Prompt | ✅ Full | Built-in Codex prompt |
| B-CFG-002 | Custom Prompt | ✅ Full | `base_instructions` |
| B-CFG-003 | maxTokens | ⚠️ Model-dep | Model configuration |
| B-CFG-004 | temperature | ⚠️ Partial | Not exposed |
| B-CFG-005 | maxTurns | ⚠️ Partial | Frontend limit |
| B-CFG-006 | continueConversation | ✅ Full | `thread/resume` |

---

## 3. Implementation Details by Feature

### 3.1 Core Chat Implementation

#### Message Sending
```typescript
// Claude Code Plus current implementation
await claudeSession.sendMessage({
  content: "Hello",
  contextFiles: [...]
});

// Codex equivalent
const response = await codexClient.request("turn/start", {
  threadId: currentThreadId,
  input: [
    { type: "text", text: "Hello" },
    { type: "localImage", path: "/path/to/context/file" } // if image
  ]
});
```

#### Streaming Response Handling
```typescript
// Codex notification handler
codexClient.onNotification("item/agentMessage/delta", (params) => {
  // params: { threadId, turnId, itemId, textDelta }
  appendToMessage(params.itemId, params.textDelta);
});

codexClient.onNotification("turn/completed", (params) => {
  // params: { threadId, turnId, status }
  if (params.status === "Completed") {
    finishTurn();
  }
});
```

### 3.2 Tool Execution Implementation

#### Command Execution
```typescript
// Codex CommandExecution item
interface CommandExecution {
  type: "commandExecution";
  id: string;
  command: string;
  cwd: string;
  status: "InProgress" | "Completed" | "Cancelled";
  aggregatedOutput: string | null;
  exitCode: number | null;
}

// Streaming output
codexClient.onNotification("item/commandExecution/outputDelta", (params) => {
  // params: { threadId, turnId, itemId, outputDelta }
  appendOutput(params.itemId, params.outputDelta);
});
```

#### File Change
```typescript
// Codex FileChange item
interface FileChange {
  type: "fileChange";
  id: string;
  changes: Array<{
    newContent?: string;
    oldContent?: string;
    path: string;
    additions?: string;  // Diff format
    deletions?: string;
  }>;
  status: "InProgress" | "Applied" | "Declined";
}
```

### 3.3 Permission Request Implementation

#### Approval Flow
```typescript
// Server sends request for approval
codexClient.onRequest("item/commandExecution/requestApproval", async (params) => {
  // params: { threadId, turnId, itemId, reason, proposedExecpolicyAmendment }

  // Show UI to user
  const userDecision = await showApprovalDialog({
    type: "command",
    command: await getCommandFromItem(params.itemId),
    reason: params.reason
  });

  return {
    decision: userDecision ? "Approved" : "Declined"
  };
});

codexClient.onRequest("item/fileChange/requestApproval", async (params) => {
  // Similar handling for file changes
  const userDecision = await showApprovalDialog({
    type: "fileChange",
    changes: await getChangesFromItem(params.itemId)
  });

  return {
    decision: userDecision ? "Approved" : "Declined"
  };
});
```

### 3.4 Session Management Implementation

#### Thread Lifecycle
```typescript
// Start new thread
const startResponse = await codexClient.request("thread/start", {
  model: "gpt-5.1-codex-max",
  modelProvider: "openai",
  cwd: projectPath,
  approvalPolicy: "on-request",
  sandbox: "workspace-write",
  baseInstructions: customSystemPrompt
});

const threadId = startResponse.thread.id;

// Resume existing thread
const resumeResponse = await codexClient.request("thread/resume", {
  threadId: savedThreadId,
  cwd: projectPath
});

// List all threads
const threads = await codexClient.request("thread/list", {});

// Archive thread
await codexClient.request("thread/archive", { threadId });
```

### 3.5 Extended Thinking Implementation

```typescript
// Enable reasoning in thread start
const response = await codexClient.request("thread/start", {
  model: "o3",  // or other reasoning-capable model
  // Effort is set via config or turn params
});

// Per-turn reasoning control
await codexClient.request("turn/start", {
  threadId,
  input: [...],
  effort: "high",  // "minimal", "low", "medium", "high", "xhigh"
  summary: "detailed"  // "auto", "concise", "detailed", "none"
});

// Handle reasoning notifications
codexClient.onNotification("item/started", (params) => {
  if (params.item.type === "reasoning") {
    startReasoningDisplay(params.item.id);
  }
});

codexClient.onNotification("item/reasoning/summaryTextDelta", (params) => {
  appendReasoningSummary(params.itemId, params.textDelta);
});
```

---

## 4. Integration Architecture

### 4.1 Proposed Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Claude Code Plus Frontend                 │
│                        (Vue 3 + TypeScript)                  │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Backend Abstraction Layer                   │
│              (Kotlin - New BackendProvider Interface)        │
│                                                              │
│  ┌─────────────────────┐   ┌─────────────────────┐          │
│  │  ClaudeBackend      │   │   CodexBackend      │          │
│  │  (Current impl)     │   │   (New impl)        │          │
│  └──────────┬──────────┘   └──────────┬──────────┘          │
│             │                         │                      │
└─────────────┼─────────────────────────┼─────────────────────┘
              │                         │
              ▼                         ▼
┌─────────────────────────┐   ┌─────────────────────────────────┐
│   Claude Agent SDK      │   │      Codex App Server           │
│   (Python + RSocket)    │   │      (Rust + JSON-RPC/stdio)    │
└─────────────────────────┘   └─────────────────────────────────┘
              │                         │
              ▼                         ▼
┌─────────────────────────┐   ┌─────────────────────────────────┐
│   Anthropic Claude API  │   │      OpenAI API / Local LLM     │
└─────────────────────────┘   └─────────────────────────────────┘
```

### 4.2 Backend Provider Interface

```kotlin
interface BackendProvider {
    // Lifecycle
    suspend fun start()
    suspend fun stop()

    // Session Management
    suspend fun createSession(config: SessionConfig): SessionHandle
    suspend fun resumeSession(sessionId: String): SessionHandle
    suspend fun listSessions(): List<SessionMetadata>
    suspend fun archiveSession(sessionId: String)

    // Conversation
    suspend fun sendMessage(
        sessionId: String,
        message: UserMessage,
        onEvent: (BackendEvent) -> Unit
    )
    suspend fun interruptGeneration(sessionId: String)

    // Permissions
    fun setApprovalCallback(callback: (ApprovalRequest) -> ApprovalResponse)

    // Configuration
    fun getCapabilities(): BackendCapabilities
    fun updateConfig(config: BackendConfig)
}

// Events emitted by backend
sealed class BackendEvent {
    data class TextDelta(val text: String) : BackendEvent()
    data class ThinkingDelta(val text: String) : BackendEvent()
    data class ToolStarted(val tool: ToolInfo) : BackendEvent()
    data class ToolOutput(val toolId: String, val output: String) : BackendEvent()
    data class ToolCompleted(val toolId: String, val result: ToolResult) : BackendEvent()
    data class TurnCompleted(val status: TurnStatus) : BackendEvent()
    data class Error(val error: BackendError) : BackendEvent()
}
```

### 4.3 Codex Backend Implementation

```kotlin
class CodexBackendProvider(
    private val project: Project,
    private val codexBinaryPath: String
) : BackendProvider {

    private var process: Process? = null
    private var jsonRpcClient: JsonRpcClient? = null

    override suspend fun start() {
        // Start codex-app-server process
        process = ProcessBuilder(codexBinaryPath)
            .redirectErrorStream(true)
            .start()

        // Initialize JSON-RPC client over stdio
        jsonRpcClient = JsonRpcClient(
            input = process!!.inputStream,
            output = process!!.outputStream
        )

        // Send initialize request
        jsonRpcClient!!.request("initialize", mapOf(
            "capabilities" to mapOf(
                "experimental_raw_events" to false
            )
        ))
    }

    override suspend fun createSession(config: SessionConfig): SessionHandle {
        val response = jsonRpcClient!!.request("thread/start", mapOf(
            "model" to config.model,
            "modelProvider" to config.modelProvider,
            "cwd" to project.basePath,
            "approvalPolicy" to config.approvalPolicy.toCodexFormat(),
            "sandbox" to config.sandbox.toCodexFormat(),
            "baseInstructions" to config.systemPrompt
        ))

        return CodexSessionHandle(
            threadId = response["thread"]["id"].asString,
            client = jsonRpcClient!!
        )
    }

    override suspend fun sendMessage(
        sessionId: String,
        message: UserMessage,
        onEvent: (BackendEvent) -> Unit
    ) {
        // Start turn
        jsonRpcClient!!.request("turn/start", mapOf(
            "threadId" to sessionId,
            "input" to message.toCodexInput()
        ))

        // Handle notifications until turn completes
        while (true) {
            val notification = jsonRpcClient!!.nextNotification()
            val event = notification.toBackendEvent()
            onEvent(event)

            if (event is BackendEvent.TurnCompleted) {
                break
            }
        }
    }

    // ... other implementations
}
```

### 4.4 Frontend Adapter

```typescript
// frontend/src/services/BackendService.ts

interface BackendService {
  // Session management
  createSession(config: SessionConfig): Promise<string>;
  resumeSession(sessionId: string): Promise<void>;
  listSessions(): Promise<SessionMetadata[]>;

  // Conversation
  sendMessage(
    sessionId: string,
    message: UserMessage,
    onEvent: (event: BackendEvent) => void
  ): Promise<void>;
  interruptGeneration(sessionId: string): Promise<void>;

  // Capabilities
  getCapabilities(): BackendCapabilities;
}

// Unified event types
type BackendEvent =
  | { type: 'textDelta'; text: string }
  | { type: 'thinkingDelta'; text: string }
  | { type: 'toolStarted'; tool: ToolInfo }
  | { type: 'toolOutput'; toolId: string; output: string }
  | { type: 'toolCompleted'; toolId: string; result: ToolResult }
  | { type: 'turnCompleted'; status: TurnStatus }
  | { type: 'error'; error: BackendError };

// Current Claude implementation
class ClaudeBackendService implements BackendService {
  // Uses existing ClaudeSession with RSocket
}

// New Codex implementation
class CodexBackendService implements BackendService {
  // Adapts to unified interface

  async sendMessage(
    sessionId: string,
    message: UserMessage,
    onEvent: (event: BackendEvent) => void
  ): Promise<void> {
    // Start turn via HTTP API to Kotlin backend
    await fetch('/api/codex/turn/start', {
      method: 'POST',
      body: JSON.stringify({
        threadId: sessionId,
        input: this.convertMessage(message)
      })
    });

    // WebSocket for streaming events
    this.ws.onmessage = (msg) => {
      const notification = JSON.parse(msg.data);
      const event = this.convertNotification(notification);
      onEvent(event);
    };
  }

  private convertNotification(notification: CodexNotification): BackendEvent {
    switch (notification.method) {
      case 'item/agentMessage/delta':
        return { type: 'textDelta', text: notification.params.textDelta };
      case 'item/commandExecution/outputDelta':
        return {
          type: 'toolOutput',
          toolId: notification.params.itemId,
          output: notification.params.outputDelta
        };
      // ... other mappings
    }
  }
}
```

---

## 5. Configuration Mapping

### 5.1 Approval Policy Mapping

| Claude Code Plus | Codex App Server |
|-----------------|------------------|
| `DEFAULT` | `on-request` |
| `ACCEPT_EDITS` | Custom logic (approve FileChange, ask for CommandExecution) |
| `PLAN` | Via `turn/plan/updated` handling |
| `BYPASS_PERMISSIONS` | `never` |

### 5.2 Model Mapping

| Claude Code Plus | Codex App Server |
|-----------------|------------------|
| `claude-opus-4-5-20251101` | `gpt-5.1-codex-max` or `o3` |
| `claude-sonnet-4-5-20251101` | `gpt-4o` or similar |
| `claude-haiku-4-5-20251101` | `gpt-4o-mini` or similar |
| Custom model | Any supported model |

### 5.3 Thinking/Reasoning Mapping

| Claude Code Plus | Codex App Server |
|-----------------|------------------|
| `thinkingEnabled: false` | `model_reasoning_effort: null` |
| `thinkingEnabled: true` | `model_reasoning_effort: "medium"` |
| `maxThinkingTokens: 2048` | `effort: "low"` |
| `maxThinkingTokens: 8096` | `effort: "medium"` |
| `maxThinkingTokens: 16000` | `effort: "high"` |
| `maxThinkingTokens: 32000+` | `effort: "xhigh"` |

### 5.4 MCP Configuration Mapping

| Claude Code Plus | Codex App Server |
|-----------------|------------------|
| `McpServerConfig.command` | `mcp_servers[name].command` |
| `McpServerConfig.args` | `mcp_servers[name].args` |
| `McpServerConfig.env` | `mcp_servers[name].env` |
| (no equivalent) | `mcp_servers[name].enabled_tools` |
| (no equivalent) | `mcp_servers[name].disabled_tools` |
| (no equivalent) | `mcp_servers[name].startup_timeout_sec` |

---

## 6. Implementation Roadmap

### Phase 1: Core Infrastructure (Week 1-2)

1. **Backend Provider Interface**
   - Define `BackendProvider` interface in Kotlin
   - Refactor current Claude implementation to use interface
   - Add backend selection to settings

2. **Codex Process Management**
   - Implement `codex-app-server` process lifecycle
   - JSON-RPC client over stdio
   - Error handling and recovery

3. **Basic Message Flow**
   - `thread/start` / `thread/resume`
   - `turn/start` / `turn/interrupt`
   - Handle streaming notifications

### Phase 2: Feature Parity (Week 3-4)

4. **Tool Display**
   - Map `CommandExecution` → Bash tool display
   - Map `FileChange` → Write/Edit tool display
   - Map `McpToolCall` → MCP tool display

5. **Permission Handling**
   - Handle `**/requestApproval` server requests
   - Integrate with existing permission UI
   - Support approval policy configuration

6. **Session Management**
   - Thread list and selection
   - Thread archival and deletion
   - Session state persistence

### Phase 3: Advanced Features (Week 5-6)

7. **Reasoning/Thinking**
   - Handle `Reasoning` item type
   - Display reasoning summaries
   - Configure effort levels

8. **MCP Integration**
   - MCP server configuration UI
   - Dynamic tool loading
   - MCP OAuth flows

9. **Configuration UI**
   - Model provider selection
   - Sandbox mode configuration
   - Per-project settings

### Phase 4: Polish & Testing (Week 7-8)

10. **Error Handling**
    - Connection recovery
    - Graceful degradation
    - User-friendly error messages

11. **Performance Optimization**
    - Message buffering
    - Efficient state updates
    - Memory management

12. **Testing & Documentation**
    - Integration tests
    - E2E tests
    - User documentation

---

## Summary

### Feature Coverage

| Category | Full Support | Partial Support | Not Supported |
|----------|-------------|-----------------|---------------|
| Chat Core | 6/7 (86%) | 1/7 (14%) | 0 |
| Input | 6/6 (100%) | 0 | 0 |
| Context | 7/7 (100%) | 0 | 0 |
| Sessions | 6/9 (67%) | 3/9 (33%) | 0 |
| Tools | 7/10 (70%) | 3/10 (30%) | 0 |
| IDEA Integration | 3/3 (100%) | 0 | 0 |
| Permissions | 4/6 (67%) | 2/6 (33%) | 0 |
| Theme/i18n | 100% | 0 | 0 |
| Advanced | 6/11 (55%) | 5/11 (45%) | 0 |
| **Total** | **~75%** | **~25%** | **~0%** |

### Key Gaps to Address

1. **Token Usage Statistics** - Need to extend Codex API or estimate locally
2. **Sub-agent (Task) Support** - No equivalent in Codex, may need custom implementation
3. **Read Tool Exposure** - Codex handles file reading internally
4. **Session Rename/Delete** - Need custom metadata storage
5. **Message Edit/Restart** - Different approach (new thread vs. truncate)

### Recommended Priority

1. ✅ **High Priority** - Core chat, streaming, tool display, permissions
2. ⚠️ **Medium Priority** - Session management, reasoning display, MCP
3. 📋 **Low Priority** - Token stats, sub-agents, advanced history features
