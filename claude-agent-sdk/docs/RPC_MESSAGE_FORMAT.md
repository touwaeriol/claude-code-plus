# RPC 消息格式规范 - 与 Claude Agent SDK 对齐

## 概述

本文档定义了 ai-agent-sdk 与前端之间的 RPC 消息格式，确保与 Claude Agent SDK 原生格式保持一致。

---

## 一、Claude Agent SDK 原生消息格式

### 1.1 消息大类

Claude Agent SDK 定义了 5 种消息大类（见 `claude-agent-sdk/.../types/Messages.kt`）：

| type | 类名 | 说明 |
|------|------|------|
| `user` | UserMessage | 用户消息，包含文本、图片、`tool_result` 等 |
| `assistant` | AssistantMessage | 助手完整消息，包含文本、`tool_use` 等 |
| `result` | ResultMessage | 请求结束统计（duration、usage、cost 等） |
| `stream_event` | StreamEvent | 流式事件包装器，内部 `event` 字段是原始 Anthropic API 事件 |
| `system` | SystemMessage | 系统消息（init 等） |

### 1.2 StreamEvent 内部事件类型

`stream_event.event` 字段是原始 Anthropic API 流事件（见 `claude-agent-sdk/.../types/StreamEvents.kt`）：

| event.type | 说明 |
|------------|------|
| `message_start` | 消息开始，包含 message.id、model 等 |
| `content_block_start` | 内容块开始，包含 index、content_block |
| `content_block_delta` | 内容块增量，包含 index、delta |
| `content_block_stop` | 内容块结束，包含 index |
| `message_delta` | 消息元数据变化，包含 stop_reason、usage |
| `message_stop` | 消息结束，空对象 |

### 1.3 Delta 类型

`content_block_delta.delta` 字段的类型：

| delta.type | 说明 |
|------------|------|
| `text_delta` | 文本增量，包含 `text` 字段 |
| `thinking_delta` | 思考增量，包含 `thinking` 字段 |
| `input_json_delta` | 工具**输入** JSON 增量，包含 `partial_json` 字段 |

> ⚠️ **重要**：`input_json_delta` 仅用于工具**调用参数**的流式传输，不是工具输出！

### 1.4 完整的流式事件序列

Anthropic API 流式响应的标准事件序列：

```
message_start
  → content_block_start (index=0)
    → content_block_delta (text_delta/thinking_delta/input_json_delta) × N
  → content_block_stop (index=0)
  → content_block_start (index=1)
    → content_block_delta × N
  → content_block_stop (index=1)
  → ...
→ message_delta (stop_reason, usage)
→ message_stop
```

---

## 二、ai-agent-sdk 中间层：UiStreamEvent

### 2.1 设计目的

`ai-agent-sdk` 定义了 `UiStreamEvent` 作为 UI 消费层的抽象（见 `ai-agent-sdk/.../model/StreamEvents.kt`）。

这是对原始 Anthropic 事件的**语义化封装**，简化前端处理逻辑。

### 2.2 UiStreamEvent 类型列表

| 类型 | 说明 | 对应原生事件 |
|------|------|-------------|
| `UiMessageStart` | 消息开始 | `message_start` |
| `UiTextDelta` | 文本增量 | `content_block_delta(text_delta)` |
| `UiThinkingDelta` | 思考增量 | `content_block_delta(thinking_delta)` |
| `UiToolStart` | 工具调用开始 | `content_block_start(tool_use)` |
| `UiToolProgress` | 工具执行进度 | **无对应**（SDK 特有） |
| `UiToolComplete` | 工具执行完成 | **无对应**（SDK 特有） |
| `UiMessageComplete` | 消息完成 | `message_delta` + `message_stop` |
| `UiAssistantMessage` | 完整助手消息 | `assistant` 消息 |
| `UiUserMessage` | 完整用户消息 | `user` 消息（含 `tool_result`） |
| `UiResultMessage` | 请求结束统计 | `result` 消息 |
| `UiError` | 错误 | 自定义 |

### 2.3 与 Claude SDK 的差异

| 差异点 | 说明 |
|--------|------|
| `UiToolProgress` | Claude SDK 没有工具进度事件，工具输出通过 `tool_result` 返回 |
| `UiToolComplete` | Claude SDK 没有工具完成事件，工具结果在 `user` 消息的 `tool_result` 块中 |
| `UiMessageComplete` | Claude SDK 是两个事件：`message_delta` + `message_stop` |

---

## 三、RPC 消息格式（RpcMessage）

### 3.1 设计原则

RPC 消息格式（`RpcModels.kt`）**必须与 Claude Agent SDK 保持一致**，便于：

1. 前端代码与官方 SDK 示例对齐
2. 类型系统统一，减少转换开销
3. 便于调试和日志分析

### 3.2 RpcMessage 类型层次

```
RpcMessage (sealed interface)
├── RpcUserMessage (@SerialName("user"))
│   └── message: RpcMessageContent { content: List<RpcContentBlock> }
│   └── parent_tool_use_id?: String
├── RpcAssistantMessage (@SerialName("assistant"))
│   └── message: RpcMessageContent { content, model? }
├── RpcResultMessage (@SerialName("result"))
│   └── subtype, duration_ms, is_error, num_turns, ...
├── RpcStreamEvent (@SerialName("stream_event"))
│   └── uuid: String
│   └── session_id: String
│   └── event: RpcStreamEventData  ← 嵌套的流式事件
└── RpcErrorMessage (@SerialName("error"))
    └── message: String
```

### 3.3 RpcStreamEventData 类型层次

```
RpcStreamEventData (sealed interface)
├── RpcMessageStartEvent (@SerialName("message_start"))
├── RpcContentBlockStartEvent (@SerialName("content_block_start"))
├── RpcContentBlockDeltaEvent (@SerialName("content_block_delta"))
│   └── delta: RpcDelta
├── RpcContentBlockStopEvent (@SerialName("content_block_stop"))
├── RpcMessageDeltaEvent (@SerialName("message_delta"))
└── RpcMessageStopEvent (@SerialName("message_stop"))
```

### 3.4 RpcDelta 类型

```
RpcDelta (sealed interface)
├── RpcTextDelta (@SerialName("text_delta"))
├── RpcThinkingDelta (@SerialName("thinking_delta"))
└── RpcInputJsonDelta (@SerialName("input_json_delta"))
```

---

## 四、UiStreamEvent → RpcMessage 转换规则

### 4.1 正确的转换映射

| UiStreamEvent | → | RpcMessage |
|---------------|---|------------|
| `UiMessageStart` | → | `RpcStreamEvent { event: RpcMessageStartEvent }` |
| `UiTextDelta` | → | `RpcStreamEvent { event: RpcContentBlockDeltaEvent { delta: RpcTextDelta } }` |
| `UiThinkingDelta` | → | `RpcStreamEvent { event: RpcContentBlockDeltaEvent { delta: RpcThinkingDelta } }` |
| `UiToolStart` | → | `RpcStreamEvent { event: RpcContentBlockStartEvent { content_block: RpcToolUseBlock } }` |
| `UiToolProgress` | → | ⚠️ 见下方说明 |
| `UiToolComplete` | → | ⚠️ 见下方说明 |
| `UiMessageComplete` | → | `RpcStreamEvent { event: RpcMessageDeltaEvent }` + `RpcStreamEvent { event: RpcMessageStopEvent }` |
| `UiAssistantMessage` | → | `RpcAssistantMessage` |
| `UiUserMessage` | → | `RpcUserMessage` |
| `UiResultMessage` | → | `RpcResultMessage` |
| `UiError` | → | `RpcErrorMessage` |

### 4.2 特殊处理：UiToolProgress

**问题**：`UiToolProgress.outputPreview` 是工具**输出**，不是输入。

**错误做法**：
```kotlin
// ❌ 错误：把输出当作输入 JSON
is UiToolProgress -> wrapAsStreamEvent(
    RpcContentBlockDeltaEvent(
        delta = RpcInputJsonDelta(partialJson = outputPreview ?: "")
    )
)
```

**正确做法**：

方案 A：不发送此事件（前端通过 `tool_result` 获取最终结果）
```kotlin
is UiToolProgress -> null  // 不转换为 RPC 消息
```

方案 B：使用 `text_delta` 传输输出预览（如果前端需要实时显示）
```kotlin
is UiToolProgress -> wrapAsStreamEvent(
    RpcContentBlockDeltaEvent(
        delta = RpcTextDelta(text = outputPreview ?: "")
    )
)
```

方案 C：扩展协议，新增 `output_delta` 类型（需要前端配合）
```kotlin
// 需要在 RpcDelta 中新增 RpcOutputDelta 类型
```

### 4.3 特殊处理：UiToolComplete

**问题**：`UiToolComplete.result` 包含工具执行结果，但当前被丢弃。

**错误做法**：
```kotlin
// ❌ 错误：result 被丢弃
is UiToolComplete -> wrapAsStreamEvent(
    RpcContentBlockStopEvent(index = 0)
)
```

**正确做法**：发送 `user` 消息 + `tool_result` 块
```kotlin
is UiToolComplete -> RpcUserMessage(
    message = RpcMessageContent(
        content = listOf(
            RpcToolResultBlock(
                toolUseId = toolId,
                content = result.toJsonElement(),
                isError = false
            )
        )
    ),
    provider = rpcProvider
)
```

> **注意**：这与 Claude Agent SDK 的行为一致 —— 工具结果作为 `user` 消息的 `tool_result` 块返回。

### 4.4 特殊处理：UiMessageComplete

**问题**：Claude SDK 是两个独立事件，当前只发送了一个。

**错误做法**：
```kotlin
// ❌ 错误：只发送 message_delta，没有 message_stop
is UiMessageComplete -> wrapAsStreamEvent(
    RpcMessageDeltaEvent(usage = usage?.toRpcUsage())
)
```

**正确做法**：发送两个事件
```kotlin
is UiMessageComplete -> listOf(
    wrapAsStreamEvent(RpcMessageDeltaEvent(usage = usage?.toRpcUsage())),
    wrapAsStreamEvent(RpcMessageStopEvent())
)
```

> **注意**：这需要修改 `toRpcMessage()` 的返回类型，或在上层处理。

---

## 五、前端处理规范

### 5.1 消息分发

```typescript
function handleMessage(msg: RpcMessage) {
  switch (msg.type) {
    case 'user':
      handleUserMessage(msg)  // 可能包含 tool_result
      break
    case 'assistant':
      handleAssistantMessage(msg)
      break
    case 'result':
      handleResultMessage(msg)
      break
    case 'stream_event':
      handleStreamEvent(msg.event)
      break
    case 'error':
      handleErrorMessage(msg)
      break
  }
}
```

### 5.2 流式事件处理

```typescript
function handleStreamEvent(event: RpcStreamEventData) {
  switch (event.type) {
    case 'message_start':
      // 创建新的 assistant 消息占位符
      break
    case 'content_block_start':
      // 添加新的内容块
      break
    case 'content_block_delta':
      handleDelta(event.delta)
      break
    case 'content_block_stop':
      // 内容块完成
      break
    case 'message_delta':
      // 更新 usage
      break
    case 'message_stop':
      // 标记消息完成，设置 isStreaming = false
      break
  }
}
```

### 5.3 工具结果处理

工具结果通过 `user` 消息的 `tool_result` 块返回：

```typescript
function handleUserMessage(msg: RpcUserMessage) {
  for (const block of msg.message.content) {
    if (block.type === 'tool_result') {
      updateToolCallStatus(block.tool_use_id, {
        status: block.is_error ? 'failed' : 'success',
        result: block.content
      })
    }
  }
}
```

---

## 六、JSON 示例

### 6.1 流式文本增量

```json
{
  "type": "stream_event",
  "uuid": "evt-abc12345-1",
  "session_id": "sess-xyz",
  "event": {
    "type": "content_block_delta",
    "index": 0,
    "delta": { "type": "text_delta", "text": "Hello" }
  },
  "provider": "claude"
}
```

### 6.2 工具调用开始

```json
{
  "type": "stream_event",
  "uuid": "evt-abc12345-2",
  "session_id": "sess-xyz",
  "event": {
    "type": "content_block_start",
    "index": 1,
    "content_block": {
      "type": "tool_use",
      "id": "toolu_xxx",
      "toolName": "Read",
      "toolType": "CLAUDE_READ",
      "input": null,
      "status": "in_progress"
    }
  },
  "provider": "claude"
}
```

### 6.3 工具结果（user 消息）

```json
{
  "type": "user",
  "message": {
    "content": [
      {
        "type": "tool_result",
        "tool_use_id": "toolu_xxx",
        "content": "file contents here...",
        "is_error": false
      }
    ]
  },
  "provider": "claude"
}
```

### 6.4 消息结束序列

```json
// message_delta
{
  "type": "stream_event",
  "uuid": "evt-abc12345-10",
  "session_id": "sess-xyz",
  "event": {
    "type": "message_delta",
    "delta": { "stop_reason": "end_turn" },
    "usage": { "output_tokens": 150 }
  },
  "provider": "claude"
}

// message_stop
{
  "type": "stream_event",
  "uuid": "evt-abc12345-11",
  "session_id": "sess-xyz",
  "event": {
    "type": "message_stop"
  },
  "provider": "claude"
}
```

### 6.5 请求结束统计

```json
{
  "type": "result",
  "subtype": "success",
  "duration_ms": 1234,
  "duration_api_ms": 456,
  "is_error": false,
  "num_turns": 2,
  "session_id": "sess-xyz",
  "total_cost_usd": 0.015,
  "provider": "claude"
}
```

---

## 七、已知问题和待修复项

### 7.1 index 硬编码

**问题**：当前所有 `content_block_*` 事件的 `index` 都硬编码为 0。

**位置**：`AiAgentRpcServiceImpl.kt`

**影响**：多内容块场景下无法正确区分。

**修复方案**：在转换时维护内容块索引状态。

### 7.2 RpcUnknownBlock 字段名不一致

**后端**：`type: String` + `data: String`

**前端**：`type: 'unknown'` + `originalType: string` + `data: unknown`

**修复方案**：统一字段名。

---

## 八、参考资料

- [Anthropic Streaming Messages](https://docs.anthropic.com/en/docs/build-with-claude/streaming)
- [Claude Agent SDK - Messages.kt](../src/main/kotlin/com/asakii/claude/agent/sdk/types/Messages.kt)
- [Claude Agent SDK - StreamEvents.kt](../src/main/kotlin/com/asakii/claude/agent/sdk/types/StreamEvents.kt)
- [ai-agent-sdk - StreamEvents.kt](../../ai-agent-sdk/src/main/kotlin/com/asakii/ai/agent/sdk/model/StreamEvents.kt)
- [RpcModels.kt](../../ai-agent-rpc-api/src/main/kotlin/com/asakii/rpc/api/RpcModels.kt)
