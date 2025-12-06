# SDK - RPC Server - WebSocket - 前端 数据流架构

本文档详细描述了 Claude Code Plus 项目中从 Claude Agent SDK 到前端展示的完整数据流。

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Claude CLI (外部进程)                               │
│                              stdin/stdout JSON 通信                              │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ stream-json 格式
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Claude Agent SDK (Kotlin)                              │
│  ┌──────────────────┐  ┌─────────────────────┐  ┌─────────────────────────────┐ │
│  │SubprocessTransport│ │  ControlProtocol    │  │  ClaudeCodeSdkClient        │ │
│  │  - 进程启动/管理   │──│  - 消息路由        │──│  - 连接管理                 │ │
│  │  - I/O 流读写     │  │  - 控制消息处理    │  │  - 消息发送/接收            │ │
│  │  - JSON 解析      │  │  - MCP 消息转发    │  │  - Flow<SdkMessage> 输出    │ │
│  └──────────────────┘  └─────────────────────┘  └─────────────────────────────┘ │
│                                                                                  │
│  输出类型: StreamEvent, UserMessage, AssistantMessage, ResultMessage, etc.       │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ UiStreamEvent (统一事件模型)
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        AI Agent RPC Server (Kotlin + Ktor)                       │
│  ┌──────────────────────────────┐  ┌─────────────────────────────────────────┐  │
│  │  AiAgentRpcServiceImpl       │  │  WebSocketHandler                       │  │
│  │  - UiEvent → RpcMessage 转换 │──│  - JSON-RPC 协议处理                    │  │
│  │  - 会话状态管理              │  │  - 流式响应包装                         │  │
│  │  - 权限回调处理              │  │  - 请求路由分发                         │  │
│  └──────────────────────────────┘  └─────────────────────────────────────────┘  │
│                                                                                  │
│  输出类型: RpcMessage (user/assistant/stream_event/result/error)                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ JSON-RPC over WebSocket
                                        │ {id, type: "stream", data: RpcMessage}
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Frontend (Vue 3 + TypeScript)                          │
│  ┌──────────────────────────────┐  ┌─────────────────────────────────────────┐  │
│  │  ClaudeSession.ts            │  │  sessionStore.ts                        │  │
│  │  - WebSocket 连接管理         │──│  - RpcMessage → DisplayItem 转换       │  │
│  │  - JSON-RPC 请求/响应        │  │  - 状态管理 (Pinia)                     │  │
│  │  - 流式事件分发              │  │  - 消息历史维护                         │  │
│  └──────────────────────────────┘  └─────────────────────────────────────────┘  │
│                                                                                  │
│  输出类型: DisplayItem (userMessage/assistantText/toolCall/thinking/etc.)        │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ Vue 响应式数据
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                UI 组件渲染                                       │
│  - DisplayItemRenderer.vue: 根据 displayType 分发渲染                            │
│  - AssistantTextDisplay.vue: 渲染 AI 文本回复                                    │
│  - ToolCallDisplay.vue: 渲染工具调用                                             │
│  - ThinkingDisplay.vue: 渲染思考过程                                             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 详细数据流

### 1. Claude Agent SDK 层

#### 1.0 流事件适配器 (UiStreamAdapter)

SDK 使用 `UiStreamAdapter` 将归一化事件转换为前端直接使用的 UI 事件。

**关键文件**: `ai-agent-sdk/src/main/kotlin/com/asakii/ai/agent/sdk/adapter/UiStreamAdapter.kt`

**核心机制: Index → ToolId 映射**

由于 Claude API 的 `content_block_delta` 事件只携带 `index`（内容块索引），不携带 `toolId`，而前端需要通过 `toolId` 关联工具调用和增量更新，因此 SDK 层维护一个 `index → toolId` 映射：

```kotlin
class UiStreamAdapter {
    // 维护 index → toolId 的映射，用于在 delta 事件中获取正确的 toolId
    private val indexToToolIdMap = mutableMapOf<Int, String>()

    // 在 content_block_start 时记录映射
    private fun convertContentStart(event: ContentStartedEvent): List<UiStreamEvent> {
        if (event.contentType.contains("tool")) {
            val toolId = (event.content as? ToolUseContent)?.id ?: event.index.toString()
            indexToToolIdMap[event.index] = toolId  // 记录映射
            // ...
        }
    }

    // 在 delta 事件中使用映射查找真正的 toolId
    private fun convertDelta(event: ContentDeltaEvent): List<UiStreamEvent> =
        when (val delta = event.delta) {
            is ToolDeltaPayload -> {
                val toolId = indexToToolIdMap[event.index] ?: event.index.toString()
                listOf(UiToolProgress(toolId = toolId, ...))
            }
            // ...
        }

    // 在 message_start 时重置映射
    fun resetContentIndex() {
        contentIndexCounter = 0
        indexToToolIdMap.clear()
    }
}
```

**为什么需要这个映射？**

| 事件类型 | 携带的标识 | 说明 |
|----------|-----------|------|
| `content_block_start` | `index` + `content_block.id` | 可以获取真正的 toolId |
| `content_block_delta` | 只有 `index` | 需要通过映射查找 toolId |
| `content_block_stop` | `index` | 需要通过映射查找 toolId |

**Delta 类型转换链路**

SDK 中定义了 4 种 `ContentDeltaPayload` 类型，经过 UiStreamAdapter 和 RPC Server 转换后发送给前端：

```
SDK (ContentDeltaPayload)      UiStreamAdapter       RPC Server              前端收到
─────────────────────────────────────────────────────────────────────────────────────────
TextDeltaPayload          →  UiTextDelta      →  RpcTextDelta         →  text_delta
ThinkingDeltaPayload      →  UiThinkingDelta  →  RpcThinkingDelta     →  thinking_delta
ToolDeltaPayload          →  UiToolProgress   →  RpcInputJsonDelta    →  input_json_delta
CommandDeltaPayload       →  UiToolProgress   →  RpcInputJsonDelta    →  input_json_delta
```

**渲染策略分类**

从渲染角度，Delta 类型分为两类：

| 分类 | Delta 类型 | 渲染策略 | 原因 |
|------|-----------|----------|------|
| **文本类** | `text_delta`, `thinking_delta` | ✅ 实时渲染 | 字符串可逐字显示 |
| **JSON 类** | `input_json_delta` | ⏳ 累加后渲染 | JSON 片段不完整无法解析 |

- **文本类 delta**：每次收到 delta 立即更新 DisplayItem，用户看到逐字显示效果
- **JSON 类 delta**：累加到 `toolInputJsonAccumulator`，等 `content_block_stop` 时解析完整 JSON 后才更新 DisplayItem

#### 1.1 进程通信 (SubprocessTransport)

SDK 通过子进程方式启动 Claude CLI，使用 stdin/stdout 进行 JSON 通信。

**启动命令示例**:
```bash
claude.cmd --verbose \
  --output-format stream-json \
  --include-partial-messages \
  --input-format stream-json \
  --model claude-opus-4-5-20251101 \
  --permission-mode default \
  --permission-prompt-tool stdio \
  --mcp-config "{\"mcpServers\":{\"user_interaction\":{\"type\":\"sdk\"}}}" \
  --agents '{"code-reviewer":{"description":"...","prompt":"...","tools":["Read","Grep"]}}'
```

**关键文件**: `claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/transport/SubprocessTransport.kt`

**子代理参数传递**

SDK 支持通过 `--agents` 参数传递自定义子代理配置：

```kotlin
val options = ClaudeAgentOptions(
    agents = mapOf(
        "code-reviewer" to AgentDefinition(
            description = "Reviews code for quality",
            prompt = "You are a code reviewer...",
            tools = listOf("Read", "Grep"),
            model = "sonnet"
        )
    )
)
```

SDK 将 agents 序列化为 JSON 并传递给 CLI：
- 正常情况：`--agents <JSON>`
- 命令行过长（> 8000 字符）：使用临时文件 `--agents @<filepath>`

#### 1.2 消息协议 (ControlProtocol)

SDK 与 CLI 之间使用控制协议进行通信：

**控制请求** (SDK → CLI):
```json
{
  "type": "control_request",
  "request_id": "req_1_xxxx",
  "request": {
    "subtype": "initialize"
  }
}
```

**控制响应** (CLI → SDK):
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_1_xxxx",
    "response": {
      "commands": [...],
      "models": [...],
      "account": {...}
    }
  }
}
```

**用户消息** (SDK → CLI):
```json
{
  "type": "user",
  "message": {
    "role": "user",
    "content": [{"type": "text", "text": "用户输入"}]
  },
  "session_id": "xxx-xxx-xxx"
}
```

**流式事件** (CLI → SDK):
```json
{
  "type": "stream_event",
  "event": {
    "type": "content_block_start",
    "index": 0,
    "content_block": {"type": "tool_use", "name": "Bash", "id": "xxx"}
  },
  "session_id": "xxx",
  "uuid": "xxx"
}
```

**关键文件**: `claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/protocol/ControlProtocol.kt`

#### 1.3 SDK 消息类型

定义在 `claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/`:

| 类型 | 说明 | 触发时机 |
|------|------|----------|
| `StreamEvent` | 流式事件容器 | 包含 Anthropic API 流事件 |
| `MessageStartEvent` | 消息开始 | 新的 assistant 消息开始 |
| `ContentBlockStartEvent` | 内容块开始 | 新的 text/tool_use/thinking 块开始 |
| `ContentBlockDeltaEvent` | 内容块增量 | 文本/JSON/思考内容增量更新 |
| `ContentBlockStopEvent` | 内容块结束 | text/tool_use/thinking 块结束 |
| `MessageDeltaEvent` | 消息增量 | usage 统计更新 |
| `MessageStopEvent` | 消息结束 | assistant 消息完成 |

---

### 2. RPC Server 层

#### 2.1 服务实现 (AiAgentRpcServiceImpl)

负责将 SDK 的 `UiStreamEvent` 转换为 RPC 协议的 `RpcMessage`。

**关键文件**: `ai-agent-server/src/main/kotlin/com/asakii/server/rpc/AiAgentRpcServiceImpl.kt`

**转换逻辑**:
```kotlin
private fun UiStreamEvent.toRpcMessage(provider: AiAgentProvider): RpcMessage {
    return when (this) {
        is UiMessageStart -> wrapAsStreamEvent(RpcMessageStartEvent(...))
        is UiTextDelta -> wrapAsStreamEvent(RpcContentBlockDeltaEvent(...))
        is UiToolStart -> wrapAsStreamEvent(RpcContentBlockStartEvent(...))
        is UiToolComplete -> wrapAsStreamEvent(RpcContentBlockStopEvent(...))
        is UiAssistantMessage -> RpcAssistantMessage(...)
        is UiResultMessage -> RpcResultMessage(...)
        // ...
    }
}
```

#### 2.2 RPC 消息类型

定义在 `ai-agent-rpc-api/src/main/kotlin/com/asakii/rpc/api/RpcModels.kt`:

| 类型 | SerialName | 说明 |
|------|------------|------|
| `RpcUserMessage` | `user` | 用户消息 |
| `RpcAssistantMessage` | `assistant` | 助手完整消息 |
| `RpcStreamEvent` | `stream_event` | 流式事件（包装 Anthropic 事件） |
| `RpcResultMessage` | `result` | 回合结果（包含统计信息） |
| `RpcErrorMessage` | `error` | 错误消息 |

#### 2.3 流式事件数据 (RpcStreamEventData)

| 事件类型 | 说明 | 包含字段 |
|----------|------|----------|
| `message_start` | 消息开始 | `message: {id, model, content}` |
| `content_block_start` | 块开始 | `index`, `content_block` |
| `content_block_delta` | 块增量 | `index`, `delta` |
| `content_block_stop` | 块结束 | `index` |
| `message_delta` | 消息增量 | `usage` |
| `message_stop` | 消息结束 | - |

#### 2.4 内容块类型 (RpcContentBlock)

| 类型 | 说明 | 关键字段 |
|------|------|----------|
| `text` | 文本 | `text` |
| `thinking` | 思考 | `thinking`, `signature` |
| `tool_use` | 工具调用 | `id`, `toolName`, `toolType`, `input`, `status` |
| `tool_result` | 工具结果 | `tool_use_id`, `content`, `is_error` |
| `image` | 图片 | `source: {type, media_type, data}` |

---

### 3. WebSocket 层

#### 3.1 JSON-RPC 协议

WebSocket 消息使用 JSON-RPC 风格封装：

**请求** (前端 → 后端):
```json
{
  "id": "req-1",
  "method": "connect",
  "params": {
    "provider": "claude",
    "model": "claude-opus-4-5-20251101"
  }
}
```

**响应** (后端 → 前端):
```json
{
  "id": "req-1",
  "result": {
    "sessionId": "xxx",
    "provider": "claude",
    "status": "connected"
  },
  "error": null
}
```

**流式数据** (后端 → 前端):
```json
{
  "id": "req-2",
  "type": "stream",
  "data": {
    "type": "stream_event",
    "uuid": "evt-xxx-1",
    "session_id": "xxx",
    "event": {
      "type": "content_block_delta",
      "index": 0,
      "delta": {"type": "text_delta", "text": "Hello"}
    },
    "provider": "claude"
  }
}
```

**完成信号** (后端 → 前端):
```json
{
  "id": "req-2",
  "type": "complete"
}
```

**关键文件**: `ai-agent-server/src/main/kotlin/com/asakii/server/WebSocketHandler.kt`

#### 3.2 RPC 方法

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `connect` | `RpcConnectOptions` | `RpcConnectResult` | 建立会话 |
| `queryWithContent` | `List<RpcContentBlock>` | `Flow<RpcMessage>` | 发送消息（流式响应） |
| `interrupt` | - | `RpcStatusResult` | 中断当前操作 |
| `disconnect` | - | `RpcStatusResult` | 断开会话 |
| `setModel` | `model: String` | `RpcSetModelResult` | 切换模型 |
| `setPermissionMode` | `mode: RpcPermissionMode` | `RpcSetPermissionModeResult` | 切换权限模式 |

---

### 4. 前端展示层

#### 4.1 类型定义

**RPC 类型** (`frontend/src/types/rpc.ts`):
- 与后端 `RpcModels.kt` 完全对应
- 提供类型守卫函数

**展示类型** (`frontend/src/types/display.ts`):
- `DisplayItem`: UI 展示的基础单元
- 从 `RpcMessage` 转换而来

#### 4.2 DisplayItem 类型

| displayType | 说明 | 来源 |
|-------------|------|------|
| `userMessage` | 用户消息 | `RpcUserMessage` |
| `assistantText` | AI 文本回复 | `RpcAssistantMessage` 中的 text 块 |
| `thinking` | 思考内容 | `RpcAssistantMessage` 中的 thinking 块 |
| `toolCall` | 工具调用 | `RpcAssistantMessage` 中的 tool_use 块 |
| `systemMessage` | 系统消息 | 前端生成 |
| `errorResult` | 错误结果 | `RpcResultMessage (is_error=true)` |
| `interruptedHint` | 中断提示 | 前端生成 |

#### 4.3 工具类型映射

| toolType | 说明 | 示例 toolName |
|----------|------|---------------|
| `CLAUDE_READ` | 读取文件 | `Read` |
| `CLAUDE_WRITE` | 写入文件 | `Write` |
| `CLAUDE_EDIT` | 编辑文件 | `Edit` |
| `CLAUDE_BASH` | 执行命令 | `Bash` |
| `CLAUDE_GREP` | 搜索内容 | `Grep` |
| `CLAUDE_GLOB` | 搜索文件 | `Glob` |
| `CLAUDE_TASK` | 子任务 | `Task` |
| `MCP` | MCP 工具 | `mcp__xxx__yyy` |

---

## 典型数据流示例

### 用户发送消息到 AI 回复完整流程

```
用户输入: "使用 ls 读取文件目录"

1. 前端 → WebSocket:
   {"id":"req-2","method":"queryWithContent","params":{"content":[{"type":"text","text":"使用 ls 读取文件目录"}]}}

2. RPC Server → SDK:
   {"type":"user","message":{"role":"user","content":[{"type":"text","text":"使用 ls 读取文件目录"}]},"session_id":"xxx"}

3. SDK → CLI (stdin)
   [同上]

4. CLI → SDK (stdout) - 流式事件序列:
   {"type":"stream_event","event":{"type":"message_start","message":{...}}}
   {"type":"stream_event","event":{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","name":"Bash",...}}}
   {"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"..."}}}
   ...
   {"type":"stream_event","event":{"type":"content_block_stop","index":0}}

5. SDK → RPC Server:
   [UiStreamEvent 序列]

6. RPC Server → WebSocket:
   {"id":"req-2","type":"stream","data":{"type":"stream_event","event":{"type":"message_start",...}}}
   {"id":"req-2","type":"stream","data":{"type":"stream_event","event":{"type":"content_block_start",...}}}
   {"id":"req-2","type":"stream","data":{"type":"assistant","message":{"content":[{"type":"tool_use",...}]}}}
   {"id":"req-2","type":"stream","data":{"type":"user","message":{"content":[{"type":"tool_result",...}]}}}
   {"id":"req-2","type":"stream","data":{"type":"result","is_error":false,"num_turns":1,...}}
   {"id":"req-2","type":"complete"}

7. 前端处理:
   - 解析 RpcMessage
   - 转换为 DisplayItem
   - 更新 Pinia store
   - Vue 响应式渲染 UI
```

---

## 日志分析

### SDK 日志 (.log/claude-agent-sdk.log)

关键日志模式：
```
📤 向CLI写入数据: {...}        # SDK 发送到 CLI
📥 从 CLI 读取到原始行: {...}  # CLI 返回数据
🔀 [ControlProtocol] 路由消息  # 消息路由
🌊 [ControlProtocol] StreamEvent 详情  # 流事件处理
```

### WebSocket 日志 (.log/ws.log)

关键日志模式：
```
📨 收到 RPC 请求: connect/queryWithContent  # 前端请求
📤 [WebSocket] 发送 RPC 响应               # 响应发送
📤 [WebSocket] 发送流式数据                # 流式推送
```

### Server 日志 (.log/server.log)

关键日志模式：
```
[executeTurn] start            # 回合开始
[executeTurn] got stream event # 收到流事件
[executeTurn] event sent       # 事件已发送
[executeTurn] done             # 回合结束
```

---

## 关键代码文件索引

| 层级 | 文件 | 职责 |
|------|------|------|
| SDK | `ai-agent-sdk/.../adapter/UiStreamAdapter.kt` | 流事件转换 + index→toolId 映射 |
| SDK | `claude-agent-sdk/.../transport/SubprocessTransport.kt` | CLI 进程管理与 I/O |
| SDK | `claude-agent-sdk/.../protocol/ControlProtocol.kt` | 消息路由与控制协议 |
| SDK | `claude-agent-sdk/.../ClaudeCodeSdkClient.kt` | SDK 客户端主类 |
| SDK | `claude-agent-sdk/.../types/StreamEvents.kt` | 流事件类型定义 |
| RPC API | `ai-agent-rpc-api/.../RpcModels.kt` | RPC 数据模型 |
| RPC API | `ai-agent-rpc-api/.../AiAgentRpcService.kt` | RPC 服务接口 |
| Server | `ai-agent-server/.../AiAgentRpcServiceImpl.kt` | RPC 服务实现 |
| Server | `ai-agent-server/.../WebSocketHandler.kt` | WebSocket 处理 |
| Frontend | `frontend/src/types/rpc.ts` | 前端 RPC 类型定义 |
| Frontend | `frontend/src/types/display.ts` | 前端展示类型定义 |
| Frontend | `frontend/src/services/ClaudeSession.ts` | WebSocket 会话管理 |
| Frontend | `frontend/src/stores/sessionStore.ts` | 状态管理 |

---

## 前端流式消息处理详解

### Claude API 流式事件规范

根据 [Claude API Streaming Messages](https://docs.anthropic.com/en/api/messages-streaming) 文档，流式响应遵循以下事件序列：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Claude API 流式事件序列                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  1. message_start          ← 消息开始，content 为空数组                       │
│                                                                              │
│  2. 对于每个 content block (可能有多个):                                       │
│     ├─ content_block_start  ← 块开始，携带 index 和初始 content_block          │
│     ├─ content_block_delta  ← 增量更新 (可能多次)                              │
│     │   ├─ text_delta       ← 文本增量                                        │
│     │   ├─ thinking_delta   ← 思考增量                                        │
│     │   └─ input_json_delta ← 工具输入 JSON 增量                              │
│     └─ content_block_stop   ← 块结束                                          │
│                                                                              │
│  3. message_delta           ← 消息元数据更新 (usage 统计)                       │
│  4. message_stop            ← 消息结束                                         │
└─────────────────────────────────────────────────────────────────────────────┘
```

**关键概念：Content Block Index**

每个 content block 有一个 `index` 值，对应最终 Message 的 `content` 数组位置：

```
Message.content = [
  { type: "thinking", ... },  // index 0
  { type: "text", ... },      // index 1
  { type: "tool_use", ... }   // index 2
]
```

多个 blocks 按顺序流式传输，同一个 block 的所有 delta 事件共享相同的 index。

---

### 前端 StreamEvent → DisplayItem 转换

#### 核心数据结构关系

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           数据结构映射关系                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Message (消息模型)              DisplayItem (UI 展示模型)                     │
│  ┌────────────────────┐         ┌─────────────────────────────┐             │
│  │ id: string         │         │ id: string                  │             │
│  │ role: 'assistant'  │   1:N   │ displayType: string         │             │
│  │ content: [         │ ──────► │ timestamp: number           │             │
│  │   ContentBlock,    │         │ ...具体字段                  │             │
│  │   ContentBlock,    │         └─────────────────────────────┘             │
│  │   ...              │                                                      │
│  │ ]                  │         一条 Message 拆分为多个 DisplayItems:         │
│  │ isStreaming: bool  │         - thinking → ThinkingContent                │
│  └────────────────────┘         - text → AssistantText                      │
│                                 - tool_use → ToolCall                        │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### DisplayItem ID 命名规则

```typescript
// 文本块
`${message.id}-text-${blockIndex}`     // 例: "msg_01xxx-text-1"

// 思考块
`${message.id}-thinking-${blockIndex}` // 例: "msg_01xxx-thinking-0"

// 工具调用块
`${toolUseBlock.id}`                   // 使用 tool_use 的原始 id
```

#### 处理流程 (sessionStore.ts)

```typescript
// 1. 消息分发入口
function handleMessage(sessionId, normalized) {
  switch (normalized.kind) {
    case 'stream_event':
      handleStreamEvent(sessionId, normalized.data)  // 流式事件
      return
    case 'result':
      handleResultMessage(sessionId, normalized.data) // 结果消息
      return
    case 'message':
      handleNormalMessage(sessionId, sessionState, normalized.data) // 完整消息
      return
  }
}

// 2. 流式事件处理 (核心) - 2024-12 更新
function handleStreamEvent(sessionId, streamEventData) {
  const event = streamEventData.event

  switch (event.type) {
    case 'message_start':
      // 只负责初始化 Message 对象
      // ❌ 不调用 syncDisplayItemsForMessage
      // ✅ displayItems 由后续的 content_block_start 创建

    case 'content_block_start':
      // 1. 在 message.content[index] 创建空的 ContentBlock
      // 2. ✅ 创建对应的 DisplayItem 并 push 到 displayItems
      //    - text → AssistantText (content: '')
      //    - thinking → ThinkingContent (content: '')
      //    - tool_use → ToolCall (status: RUNNING)

    case 'content_block_delta':
      // 1. 累加内容到 message.content[index]
      // 2. 🔑 增量更新对应的 DisplayItem
      //    - text_delta → 更新 AssistantText.content
      //    - thinking_delta → 更新 ThinkingContent.content
      //    - input_json_delta → 累加 JSON 片段 (不立即更新 UI)

    case 'content_block_stop':
      // 1. 标记 content block 完成
      // 2. 对于 tool_use: 解析完整 JSON，更新 ToolCall.input

    case 'message_delta':
      // 更新 usage 统计

    case 'message_stop':
      // 只标记 message.isStreaming = false
      // ❌ 不调用 syncDisplayItemsForMessage
  }
}

// 🔑 核心原则：
// - displayItems 只在 content_block_start/delta/stop 中创建和更新
// - 不在 message_start/message_stop/handleResultMessage 中调用 syncDisplayItemsForMessage
// - 这样避免了部分 assistant 消息干扰流式累积的内容
```

---

### 增量更新实现

#### 文本增量更新

```typescript
function updateTextDisplayItemIncrementally(
  message: Message,
  blockIndex: number,
  newText: string,
  sessionState: SessionState
) {
  const expectedId = `${message.id}-text-${blockIndex}`

  // 查找现有的 DisplayItem
  for (let i = 0; i < sessionState.displayItems.length; i++) {
    const item = sessionState.displayItems[i]
    if (item.id === expectedId && item.displayType === 'assistantText') {
      // 🔑 创建新对象触发 Vue 响应式更新
      sessionState.displayItems[i] = { ...item, content: newText }
      return
    }
  }

  // 如果不存在，创建新的 DisplayItem
  sessionState.displayItems.push({
    displayType: 'assistantText',
    id: expectedId,
    content: newText,
    timestamp: message.timestamp,
    isStreaming: true
  })
}
```

#### 工具输入 JSON 累加

```typescript
// tool_use 的 input 是 JSON，需要累加完整后才能解析
case 'input_json_delta':
  if (contentBlock.type === 'tool_use') {
    // 1. 累加 JSON 片段
    const accumulated = toolInputJsonAccumulator.get(contentBlock.id) || ''
    const newAccumulated = accumulated + delta.partial_json
    toolInputJsonAccumulator.set(contentBlock.id, newAccumulated)

    // 2. 尝试解析 (可能失败，JSON 不完整)
    try {
      contentBlock.input = JSON.parse(newAccumulated)
    } catch {
      // 继续累加，等待完整 JSON
    }
  }
  break

case 'content_block_stop':
  // JSON 解析完成，更新 ToolCall DisplayItem 的 input
  if (block.type === 'tool_use') {
    const toolCallItem = sessionState.displayItems.find(
      item => item.id === block.id && item.displayType === 'toolCall'
    )
    if (toolCallItem) {
      toolCallItem.input = block.input
    }
  }
  break
```

---

### 消息类型处理差异

#### 流式事件 (stream_event) vs 完整消息 (assistant)

后端会发送两种类型的消息，前端需要正确区分处理：

| 消息类型 | 时机 | 内容 | 处理方式 |
|----------|------|------|----------|
| `stream_event` | 实时流式 | 增量事件 | **立即**更新 DisplayItem |
| `assistant` | 内容块完成后 | **部分**内容 | **合并或忽略** |
| `result` | 回合结束 | 统计信息 | 结束流式状态 |

**⚠️ 关键问题：部分 assistant 消息**

后端在每个内容块完成后会发送一个 `assistant` 消息，但该消息**只包含当前完成的块**，不是完整消息：

```
时序:
1. stream_event: content_block_start (index=0, thinking)
2. stream_event: content_block_delta (thinking_delta) × N
3. ⚠️ assistant: {content: [thinking]}        ← 只有 thinking！
4. stream_event: content_block_start (index=1, text)
5. stream_event: content_block_delta (text_delta) × N
6. ⚠️ assistant: {content: [text]}            ← 只有 text！
7. stream_event: content_block_start (index=2, tool_use)
8. stream_event: content_block_delta (input_json_delta) × N
9. ⚠️ assistant: {content: [tool_use]}        ← 只有 tool_use！
```

#### 正确处理策略（2024-12 更新）

**简化后的处理逻辑**：

由于 SDK 层已正确维护 `index → toolId` 映射，前端收到的 `input_json_delta` 等增量事件已携带正确的 `toolId`，因此 `handleNormalMessage` 的逻辑可以大幅简化：

```typescript
/**
 * 处理普通消息（assistant/user 消息）
 *
 * 简化后的处理策略：
 * - stream_event 负责增量组装消息
 * - 完整消息与最新流式消息 ID 相同 → 忽略（流式已组装完成）
 * - 完整消息 ID 不同 → 添加新消息
 * - user 消息（包含 tool_result）：更新对应的 tool_use 状态
 */
function handleNormalMessage(sessionId, sessionState, message) {
  if (message.role === 'assistant') {
    // 获取最新的流式消息
    const latestStreamingMessage = findStreamingAssistantMessage(sessionState)

    // 情况 1：ID 相同 → 忽略（流式已组装完成）
    if (latestStreamingMessage && latestStreamingMessage.id === message.id) {
      log.debug('⏭️ 忽略同 ID 的完整消息', { messageId: message.id })
      return
    }

    // 情况 2：ID 不同或无流式消息 → 添加新消息
    log.debug('➕ 添加新 assistant 消息', { messageId: message.id })
    addMessage(sessionId, message)
    touchSession(sessionId)
    return
  }

  // user 消息处理
  if (message.role === 'user') {
    // tool_result 消息：只更新工具状态
    const hasToolResult = message.content.some(b => b.type === 'tool_result')
    if (hasToolResult) {
      processToolResults(sessionState, message.content)
      return
    }
    // ...
  }
}
```

**核心原则**：
1. DisplayItems 只在 `content_block_start/delta/stop` 流式事件中创建和更新
2. 完整 `assistant` 消息与流式消息 ID 相同时忽略，避免重复
3. SDK 层的 `indexToToolIdMap` 确保增量事件携带正确的 `toolId`

---

### 完整的事件时序图

```
用户发送: "使用 Write 工具写入文件"

                前端                    WebSocket                   后端
                 │                         │                         │
    sendMessage ─┼────── query ───────────►│                         │
                 │                         │                         │
                 │                         │◄──── message_start ─────┤
                 │◄─ stream_event ─────────│                         │
          创建 Message (isStreaming=true)   │                         │
                 │                         │                         │
                 │                         │◄── content_block_start ─┤ (index=0, thinking)
                 │◄─ stream_event ─────────│                         │
          创建 ThinkingContent DisplayItem  │                         │
                 │                         │                         │
                 │                         │◄── content_block_delta ─┤ (thinking_delta)
                 │◄─ stream_event ─────────│                         │
          更新 ThinkingContent.content      │                         │
                 │                         │       ... × N           │
                 │                         │                         │
                 │                         │◄──── assistant ─────────┤ ⚠️ 只有 [thinking]
                 │◄──── assistant ─────────│                         │
          ❌ 错误: 覆盖 content              │                         │
          ✅ 正确: 忽略部分消息              │                         │
                 │                         │                         │
                 │                         │◄── content_block_start ─┤ (index=1, text)
                 │◄─ stream_event ─────────│                         │
          创建 AssistantText DisplayItem   │                         │
                 │                         │                         │
                 │                         │◄── content_block_delta ─┤ (text_delta)
                 │◄─ stream_event ─────────│                         │
          更新 AssistantText.content       │                         │
                 │                         │       ... × N           │
                 │                         │                         │
                 │                         │◄── content_block_start ─┤ (index=2, tool_use)
                 │◄─ stream_event ─────────│                         │
          创建 ToolCall DisplayItem        │                         │
                 │                         │                         │
                 │                         │◄── content_block_delta ─┤ (input_json_delta)
                 │◄─ stream_event ─────────│                         │
          累加 JSON 片段                    │       ... × N           │
                 │                         │                         │
                 │                         │◄── content_block_stop ──┤ (index=2)
                 │◄─ stream_event ─────────│                         │
          解析完整 JSON，更新 ToolCall.input │                         │
                 │                         │                         │
                 │                         │◄──── message_stop ──────┤
                 │◄─ stream_event ─────────│                         │
          message.isStreaming = false      │                         │
                 │                         │                         │
                 │                         │◄──── tool_result ───────┤ (工具执行结果)
                 │◄──────── user ──────────│                         │
          更新 ToolCall.result/status      │                         │
                 │                         │                         │
                 │                         │◄──────── result ────────┤
                 │◄──────── result ────────│                         │
          结束流式状态, 更新统计             │                         │
                 │                         │                         │
```

---

### DisplayItem 状态机

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DisplayItem 生命周期                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  AssistantText / ThinkingContent:                                            │
│  ┌──────────┐   content_block_start   ┌──────────┐   content_block_delta   │
│  │ 不存在   │ ─────────────────────► │ 已创建   │ ────────────────────►   │
│  └──────────┘    创建空 DisplayItem   │ content:''│    更新 content         │
│                                       └──────────┘                           │
│                                            │                                 │
│                                            │ message_stop                    │
│                                            ▼                                 │
│                                       ┌──────────┐                           │
│                                       │ 已完成   │                           │
│                                       │ content:X│                           │
│                                       └──────────┘                           │
│                                                                              │
│  ToolCall:                                                                   │
│  ┌──────────┐   content_block_start   ┌──────────┐   input_json_delta      │
│  │ 不存在   │ ─────────────────────► │ RUNNING  │ ────────────────────►   │
│  └──────────┘    创建 DisplayItem     │ input:?  │    累加 JSON 片段        │
│                  status: RUNNING      └──────────┘                           │
│                                            │                                 │
│                                            │ content_block_stop              │
│                                            │ 解析完整 JSON                   │
│                                            ▼                                 │
│                                       ┌──────────┐                           │
│                                       │ RUNNING  │                           │
│                                       │ input:OK │                           │
│                                       └──────────┘                           │
│                                            │                                 │
│                                            │ tool_result                     │
│                                            ▼                                 │
│                                    ┌───────┴───────┐                         │
│                                    ▼               ▼                         │
│                               ┌──────────┐   ┌──────────┐                   │
│                               │ SUCCESS  │   │ FAILED   │                   │
│                               │ result:OK│   │ result:X │                   │
│                               └──────────┘   └──────────┘                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 不同 Delta 类型的渲染策略

#### 渲染策略对比

| Delta 类型 | 实时渲染 | 原因 | 更新时机 |
|------------|----------|------|----------|
| `text_delta` | ✅ 是 | 文本可逐字显示 | 每次 delta 立即更新 |
| `thinking_delta` | ✅ 是 | 思考内容可逐字显示 | 每次 delta 立即更新 |
| `input_json_delta` | ❌ 否 | JSON 片段不完整无法解析 | `content_block_stop` 时更新 |
| `signature_delta` | ❌ 否 | 签名需要完整 | `content_block_stop` 时更新 |

#### text_delta / thinking_delta 处理

```typescript
// 实时渲染：每次 delta 都更新 UI
case 'text_delta':
  // 1. 累加到 Message.content
  contentBlock.text += delta.text

  // 2. 立即更新 DisplayItem（触发 Vue 响应式）
  updateTextDisplayItemIncrementally(message, index, contentBlock.text, sessionState)
  break

case 'thinking_delta':
  contentBlock.thinking += delta.thinking
  updateThinkingDisplayItemIncrementally(message, index, contentBlock.thinking, sessionState)
  break
```

**UI 效果**: 用户看到文字逐字出现（打字机效果）

#### input_json_delta 处理

```typescript
// 延迟渲染：累加完整后才更新 UI
case 'input_json_delta':
  // 1. 只累加，不更新 UI
  const accumulated = toolInputJsonAccumulator.get(contentBlock.id) || ''
  toolInputJsonAccumulator.set(contentBlock.id, accumulated + delta.partial_json)

  // 2. 尝试解析到 message.content（可能失败）
  try {
    contentBlock.input = JSON.parse(newAccumulated)
  } catch {
    // JSON 不完整，继续等待
  }
  break

case 'content_block_stop':
  // 3. JSON 完整了，更新 DisplayItem
  if (block.type === 'tool_use') {
    const toolCallItem = displayItems.find(i => i.id === block.id)
    toolCallItem.input = block.input  // 现在才更新 UI
  }
  break
```

**UI 效果**: 工具卡片先显示（无参数），参数在 `content_block_stop` 后一次性出现

#### input_json_delta 示例

```
收到 delta 序列:
  delta 1: {"file_pa
  delta 2: th":"/src/
  delta 3: App.vue","c
  delta 4: ontent":"hello"}

累加过程:
  第1次: {"file_pa                    ← 无法解析
  第2次: {"file_path":"/src/          ← 无法解析
  第3次: {"file_path":"/src/App.vue","c  ← 无法解析
  第4次: {"file_path":"/src/App.vue","content":"hello"}  ← ✅ 完整，可解析

content_block_stop 时:
  → 解析成功
  → 更新 ToolCall.input = { file_path: "/src/App.vue", content: "hello" }
  → UI 显示工具参数
```

#### 完整渲染时序

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Stream 渲染时序                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  message_start                                                               │
│      │                                                                       │
│      └─► 创建 Message (isStreaming: true)                                    │
│                                                                              │
│  content_block_start (index=0, thinking)                                     │
│      │                                                                       │
│      └─► 创建 ThinkingContent DisplayItem (content: '')                      │
│          └─► UI: 显示空的思考卡片                                             │
│                                                                              │
│  thinking_delta × N                                                          │
│      │                                                                       │
│      └─► 每次都更新 ThinkingContent.content                                   │
│          └─► UI: 思考内容逐字出现 ✨                                          │
│                                                                              │
│  content_block_start (index=1, text)                                         │
│      │                                                                       │
│      └─► 创建 AssistantText DisplayItem (content: '')                        │
│          └─► UI: 显示空的文本区域                                             │
│                                                                              │
│  text_delta × N                                                              │
│      │                                                                       │
│      └─► 每次都更新 AssistantText.content                                     │
│          └─► UI: 文字逐字出现 ✨                                              │
│                                                                              │
│  content_block_start (index=2, tool_use)                                     │
│      │                                                                       │
│      └─► 创建 ToolCall DisplayItem (input: undefined, status: RUNNING)       │
│          └─► UI: 显示工具卡片，状态为"运行中"，无参数                          │
│                                                                              │
│  input_json_delta × N                                                        │
│      │                                                                       │
│      └─► 只累加到 toolInputJsonAccumulator                                    │
│          └─► UI: 无变化（等待 JSON 完整）                                     │
│                                                                              │
│  content_block_stop (index=2)                                                │
│      │                                                                       │
│      └─► 解析完整 JSON，更新 ToolCall.input                                   │
│          └─► UI: 工具卡片显示完整参数 ✨                                      │
│                                                                              │
│  message_stop                                                                │
│      │                                                                       │
│      └─► message.isStreaming = false                                         │
│                                                                              │
│  tool_result (user 消息)                                                     │
│      │                                                                       │
│      └─► 更新 ToolCall.status = SUCCESS/FAILED, ToolCall.result = ...        │
│          └─► UI: 工具卡片显示执行结果 ✨                                      │
│                                                                              │
│  result                                                                      │
│      │                                                                       │
│      └─► 结束流式状态，更新统计信息                                           │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 关键代码文件索引 (前端流式处理)

| 文件 | 函数/模块 | 职责 |
|------|-----------|------|
| `sessionStore.ts` | `handleStreamEvent()` | 流式事件分发处理 |
| `sessionStore.ts` | `handleNormalMessage()` | 完整消息处理 |
| `sessionStore.ts` | `updateTextDisplayItemIncrementally()` | 文本增量更新 |
| `sessionStore.ts` | `updateThinkingDisplayItemIncrementally()` | 思考增量更新 |
| `sessionStore.ts` | `syncDisplayItemsForMessage()` | Message→DisplayItems 同步 |
| `sessionStore.ts` | `findStreamingAssistantMessage()` | 查找当前流式消息 |
| `sessionStore.ts` | `ensureStreamingAssistantMessage()` | 确保存在流式消息 |
| `types/rpc.ts` | 类型定义 | RPC 消息类型守卫 |
| `types/display.ts` | 类型定义 | DisplayItem 类型定义 |
| `utils/rpcMappers.ts` | `mapRpcContentBlock()` | RPC→内部类型转换 |

---

## 设计原则

1. **类型安全**: 各层之间的类型定义保持一致，使用 sealed interface/class 确保类型完备
2. **流式优先**: 使用 Kotlin Flow 和 WebSocket 实现真正的流式响应
3. **事件驱动**: 统一的事件模型贯穿各层，便于追踪和调试
4. **解耦设计**: SDK、RPC Server、Frontend 各层独立演进
5. **渐进式展示**: 支持从 stream_event 到完整 message 的渐进式 UI 更新
6. **增量更新**: DisplayItem 采用增量更新策略，避免重建整个数组
7. **流式事件主导** (2024-12 更新):
   - DisplayItems 只在 `content_block_start/delta/stop` 流式事件中创建和更新
   - 流式模式下完全忽略 RPC 中的 assistant 消息（它们只是部分内容）
   - 不在 `message_start`、`message_stop`、`handleResultMessage` 中调用 `syncDisplayItemsForMessage`
8. **消息类型区分处理**:
   - `stream_event`: 实时处理，创建和更新 DisplayItem
   - `assistant`: 流式模式下忽略，非流式模式下正常处理
   - `user` (tool_result): 只更新对应 tool_use 的状态
   - `user` (tool_use): 忽略（已通过 stream_event 处理）
   - `result`: 结束流式状态，更新统计信息

---

## 参考资料

- [Claude API Streaming Messages](https://docs.anthropic.com/en/api/messages-streaming) - 官方流式消息文档
- [Anthropic Claude Messages API](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-anthropic-claude-messages.html) - AWS Bedrock 文档
