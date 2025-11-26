# Claude Agent SDK 与 OpenAI Codex SDK 统一方案完整报告

---

## 一、可行性结论

✅ **完全可行** —— 通过适配器模式可以把两个 SDK 的消息模型与流式响应完整统一，前端只消费统一的 UI 事件即可。

---

## 二、核心架构对比

### Claude Agent SDK（Python）

**消息模型**
```text
Message = UserMessage | AssistantMessage | SystemMessage | ResultMessage | StreamEvent
ContentBlock = TextBlock | ThinkingBlock | ToolUseBlock | ToolResultBlock
```

**流式 API**
```python
async for message in client.receive_messages():
    if message.type == 'stream_event':
        # Token 级别增量
        event = message.event
    elif message.type == 'assistant':
        # 完整 AssistantMessage
```

**特点**
- 有状态的 `ClaudeSDKClient`，可随时发送消息
- Token 级别流式更新
- ToolUseBlock 表示工具调用
- 原生支持 `interrupt()` 中断

### OpenAI Codex SDK（TypeScript）

**事件模型**
```text
ThreadEvent =
  | ThreadStartedEvent
  | TurnStartedEvent
  | ItemStartedEvent
  | ItemUpdatedEvent
  | ItemCompletedEvent
  | TurnCompletedEvent
  | TurnFailedEvent

ThreadItem =
  | AgentMessageItem
  | ReasoningItem
  | CommandExecutionItem
  | FileChangeItem
  | McpToolCallItem
  | WebSearchItem
  | TodoListItem
  | ErrorItem
```

**流式 API**
```ts
const { events } = await thread.runStreamed(input)
for await (const event of events) {
  // 处理事件
}
```

**特点**
- 轻量 Thread，基于 thread_id 恢复
- Item 级别生命周期事件
- 专用 Item 类型表示不同操作
- 通过 AbortSignal 中断

---

## 三、统一数据模型设计

### 1. 统一消息模型
```ts
interface UnifiedMessage {
  id: string
  type: 'user' | 'assistant' | 'system' | 'result'
  timestamp: number
  content: UnifiedContentBlock[]
  metadata?: {
    model?: string
    usage?: Usage
    sessionId?: string
    threadId?: string
  }
}
```

```ts
type UnifiedContentBlock =
  | { type: 'text'; text: string }
  | { type: 'thinking'; thinking: string; signature?: string }
  | { type: 'tool_use'; id: string; name: string; input: any; status?: Status }
  | { type: 'tool_result'; toolUseId: string; content: any; isError?: boolean }
  | { type: 'command_execution'; command: string; output: string; exitCode?: number; status: Status }
  | { type: 'file_change'; changes: FileChange[]; status: Status }
  | { type: 'mcp_tool_call'; server: string; tool: string; arguments: any; result?: any; status: Status }
  | { type: 'web_search'; query: string }
  | { type: 'todo_list'; items: TodoItem[] }
  | { type: 'error'; message: string }

type Status = 'in_progress' | 'completed' | 'failed'
```

### 2. 三层流式事件模型
```ts
type RawStreamEvent =
  | { sdk: 'claude'; event: ClaudeStreamEvent | ClaudeMessage }
  | { sdk: 'codex'; event: ThreadEvent }

type NormalizedStreamEvent =
  | { type: 'session.started'; sessionId: string }
  | { type: 'turn.started' }
  | { type: 'content.started'; id: string; contentType: string }
  | { type: 'content.delta'; id: string; delta: ContentDelta }
  | { type: 'content.completed'; id: string; content: UnifiedContentBlock }
  | { type: 'turn.completed'; usage: Usage }
  | { type: 'turn.failed'; error: string }

type UIStreamEvent =
  | { type: 'message.start'; messageId: string | null }
  | { type: 'text.delta'; text: string }
  | { type: 'thinking.delta'; thinking: string }
  | { type: 'tool.start'; toolId: string; toolName: string; input: any }
  | { type: 'tool.progress'; toolId: string; status: string; output?: string }
  | { type: 'tool.complete'; toolId: string; result: any }
  | { type: 'message.complete'; usage: Usage }
  | { type: 'error'; message: string }
```

---

## 四、适配器实现

### ClaudeStreamAdapter（核心）
```ts
class ClaudeStreamAdapter {
  private contentBlockBuffer = new Map<number, ContentBlockState>()
  private currentMessageId: string | null = null

  async *normalize(rawStream: AsyncIterator<ClaudeMessage>): AsyncGenerator<NormalizedStreamEvent> {
    for await (const message of rawStream) {
      if (message.type === 'stream_event') {
        yield* this.handleStreamEvent(message.event)
      } else if (message.type === 'assistant') {
        for (const block of message.content) {
          yield {
            type: 'content.completed',
            id: generateId(),
            content: this.convertContentBlock(block)
          }
        }
      } else if (message.type === 'result') {
        yield { type: 'turn.completed', usage: message.usage }
      }
    }
  }

  private async *handleStreamEvent(event: any): AsyncGenerator<NormalizedStreamEvent> {
    switch (event.type) {
      case 'message_start':
        this.currentMessageId = generateId()
        yield { type: 'session.started', sessionId: this.currentMessageId }
        break
      case 'content_block_start':
        const blockId = `${this.currentMessageId}-${event.index}`
        this.contentBlockBuffer.set(event.index, {
          id: blockId,
          type: event.content_block.type,
          content: ''
        })
        yield { type: 'content.started', id: blockId, contentType: event.content_block.type }
        break
      case 'content_block_delta':
        const state = this.contentBlockBuffer.get(event.index)
        if (!state) break
        const delta = this.extractDelta(event.delta)
        state.content += delta.text || delta.thinking || ''
        yield { type: 'content.delta', id: state.id, delta }
        break
      case 'content_block_stop':
        const finalState = this.contentBlockBuffer.get(event.index)
        if (!finalState) break
        yield { type: 'content.completed', id: finalState.id, content: this.buildContentBlock(finalState) }
        this.contentBlockBuffer.delete(event.index)
        break
      case 'message_delta':
        if (event.delta.stop_reason) {
          yield { type: 'turn.completed', usage: event.usage }
        }
        break
    }
  }
}
```

### CodexStreamAdapter（核心）
```ts
class CodexStreamAdapter {
  async *normalize(rawStream: AsyncGenerator<ThreadEvent>): AsyncGenerator<NormalizedStreamEvent> {
    for await (const event of rawStream) {
      switch (event.type) {
        case 'thread.started':
          yield { type: 'session.started', sessionId: event.thread_id }
          break
        case 'turn.started':
          yield { type: 'turn.started' }
          break
        case 'item.started':
          yield { type: 'content.started', id: event.item.id, contentType: event.item.type }
          break
        case 'item.updated':
          yield { type: 'content.delta', id: event.item.id, delta: this.extractItemDelta(event.item) }
          break
        case 'item.completed':
          yield { type: 'content.completed', id: event.item.id, content: this.convertThreadItem(event.item) }
          break
        case 'turn.completed':
          yield { type: 'turn.completed', usage: event.usage }
          break
        case 'turn.failed':
          yield { type: 'turn.failed', error: event.error.message }
          break
      }
    }
  }
}
```

### UIStreamAdapter（前端使用）
```ts
class UIStreamAdapter {
  async *toUIEvents(normalizedStream: AsyncGenerator<NormalizedStreamEvent>): AsyncGenerator<UIStreamEvent> {
    for await (const event of normalizedStream) {
      switch (event.type) {
        case 'session.started':
          yield { type: 'message.start', messageId: event.sessionId }
          break
        case 'content.delta':
          if (event.delta.type === 'text') {
            yield { type: 'text.delta', text: event.delta.text }
          } else if (event.delta.type === 'thinking') {
            yield { type: 'thinking.delta', thinking: event.delta.thinking }
          }
          break
        case 'content.started':
          if (event.contentType.includes('tool') || event.contentType.includes('command')) {
            yield { type: 'tool.start', toolId: event.id, toolName: event.contentType, input: {} }
          }
          break
        case 'content.completed':
          if (event.content.type.includes('tool') || event.content.type.includes('command')) {
            yield { type: 'tool.complete', toolId: event.id, result: event.content }
          }
          break
        case 'turn.completed':
          yield { type: 'message.complete', usage: event.usage }
          break
        case 'turn.failed':
          yield { type: 'error', message: event.error }
          break
      }
    }
  }
}
```

---

## 五、统一客户端接口
在实现层面，这一接口已经通过 `ai-agent-sdk` 与 `ai-agent-server` 落地，核心类为：

- `ai-agent-sdk`: `UnifiedAgentClient` / `UnifiedAgentClientFactory` / `AiAgentStreamBridge`
- `ai-agent-server`: `AiAgentRpcService`（原 `ClaudeRpcService`）与 `AiAgentRpcServiceImpl`（原 `ClaudeRpcServiceImpl`），通过 WebSocket RPC 暴露统一流事件。

**会话参数统一说明**
- `AiAgentConnectOptions` 中的公共字段（`model/systemPrompt/initialPrompt/sessionId/metadata`）两个 SDK 都支持。
- `resumeSessionId`：Claude 映射为 `resume`，Codex 映射为 `threadId`；原来的 `resumeId/threadId` 被合并。
- `baseUrl/apiKey` 不再是公共字段，保留在 `codex.clientOptions` 内；Claude 会忽略 Codex 专属参数。

```kotlin
object UnifiedAgentClientFactory {
    fun create(provider: AiAgentProvider): UnifiedAgentClient =
        when (provider) {
            CLAUDE -> ClaudeAgentClientImpl()
            CODEX -> CodexAgentClientImpl()
        }
}
```

### AI Agent RPC 强类型模型

- **DTO 层（`ai-agent-rpc-api`）**：定义 `RpcConnectOptions / RpcConnectResult / RpcUiEvent / RpcContentBlock / RpcUsage` 等 `@Serializable` 数据结构，使用 `classDiscriminator = "type"`，避免手写 JSON。
- **实现层（`AiAgentRpcServiceImpl`）**：只和 `UnifiedAgentClient` 交互，提供映射 `UiStreamEvent → RpcUiEvent`、`UnifiedContentBlock → RpcContentBlock`、`UnifiedUsage → RpcUsage`。
- **传输层（`WebSocketHandler`）**：保持 RPC 外壳结构不变，仅用 `Json.encodeToJsonElement()` 序列化 DTO，再发送给前端；反方向则把前端 JSON 解码成 `RpcConnectOptions` / `RpcContentBlock`。
- **前端（`AiAgentSession.ts`）**：直接消费 `AgentStreamEvent`，其结构 1:1 对应 `RpcUiEvent`，`result` 字段就是 `RpcContentBlock`，无需再解析弱类型 JSON。

```kotlin
@Serializable
sealed interface RpcUiEvent {
    val provider: RpcProvider?
}

@Serializable @SerialName("ui.tool_complete")
data class RpcToolComplete(
    val toolId: String,
    val result: RpcContentBlock,
    override val provider: RpcProvider?
) : RpcUiEvent
```

```ts
type AgentStreamEvent =
  | { type: 'ui.tool_complete'; toolId: string; provider: 'claude' | 'codex'; result: RpcContentBlock }
  | { type: 'ui.message_complete'; provider: RpcProvider; usage?: RpcUsage }
```

> **一条事件链示意**：`Claude/Codex 原始事件 → UiStreamEvent → RpcUiEvent (Kotlin DTO) → WebSocket JSON → AgentStreamEvent (TypeScript 类型)`。这样 JSON 只是“线缆格式”，不会再出现“双重抽象”或字段漂移。

---

## 六、前端集成示例
```ts
async function handleSendMessage(input: string) {
  for await (const event of client.streamEvents()) {
    switch (event.type) {
      case 'message.start':
        createNewMessageBubble(event.messageId)
        break
      case 'text.delta':
        appendTextToCurrentMessage(event.text)
        break
      case 'thinking.delta':
        appendThinkingToCurrentMessage(event.thinking)
        break
      case 'tool.start':
        createToolCard(event.toolId, event.toolName)
        break
      case 'tool.progress':
        updateToolCard(event.toolId, event.status, event.output)
        break
      case 'tool.complete':
        finalizeToolCard(event.toolId, event.result)
        break
      case 'message.complete':
        finalizeMessage(event.usage)
        break
      case 'error':
        showError(event.message)
        break
    }
  }
}
```

---

## 七、关键挑战与解决方案

### 挑战 1：会话模型差异
- Claude：有状态 Client，可随时发送
- Codex：Thread 模式，每次 `run()` 都是完整 turn

**解决方案**：`CodexSessionManager` 使用消息队列或中断策略串行化 `thread.run()`

### 挑战 2：流式粒度差异
- Claude：Token 级别
- Codex：Item 级别

**解决方案**：`BufferedStreamAdapter` + 关键事件立即刷新，保证体验一致。

### 挑战 3：中断机制差异
- Claude：`client.interrupt()`
- Codex：`AbortController`

**解决方案**：统一包一层 `interrupt()`，底层分别调用对应方法。

---

## 八、实现路径（8-13 天）
1. **阶段 1（1-2 天）**：定义统一类型，完成 Claude/Codex/UI 适配器 + 单测  
2. **阶段 2（2-3 天）**：实现 `UnifiedSDKClient`、Claude/Codex 客户端封装 + 集成测试  
3. **阶段 3（3-5 天）**：前端把 `ClaudeSession.ts` 改成 `UnifiedSession.ts`，接入 UI 事件并实现 SDK 切换  
4. **阶段 4（2-3 天）**：性能优化、端到端测试、错误处理、文档

---

## 九、性能优化策略
1. **批量刷新**：50ms 批量刷新，降低 DOM 频率  
2. **背压控制**：队列上限 100，超限即暂停拉取  
3. **关键事件优先**：`tool.start` / `message.complete` 立即输出，保证及时反馈

---

## 十、总结

✅ 统一方案可行，三层事件模型 + 适配器模式让前端无感知切换。  
优势包括易扩展、性能可控、类型安全。风险主要集中在会话/流式差异，均已提供成熟解法。

---

## 附录：Codex 是什么？为什么说“每次都是完整请求”？

### Codex 解释
- OpenAI 的代码生成与编程助手，基于 GPT 系列模型
- 提供 TypeScript SDK 方便集成

### “完整请求”含义
- `thread.run()` 或 `thread.runStreamed()` 都表示一个完整回合：发送输入 → 等待完整响应 → 返回 `Turn`
- 必须等待该回合结束才能发起下一次 `run()`，无法在 AI 回复过程中插入新消息

### 与 Claude 对比
| 维度 | Codex | Claude |
| --- | --- | --- |
| 会话 | Thread（基于 thread_id） | Client（长连接） |
| 发送/接收 | `run()` 一体化 | `send_message` + `receive_messages` |
| 并发 | 不支持（需等待） | 支持随时发送 |
| 中断 | `AbortSignal` | `interrupt()` |

### 统一方案影响
- 通过消息队列或中断重跑，让 Codex 也能模拟“随时发送”  
- 前端统一使用 `client.sendMessage()`，无需关注底层差异

---

## 附录：常见问答

> **两个都是“开启会话 → 发送消息 → 接受响应 → 发送下一条”的流程，对吗？**  

是的，但实现细节不同：  
- Claude 是双向通道（可以“边听边说”）  
- Codex 是回合制（一次 run = 一次完整请求）  

统一后的客户端屏蔽差异，让用户始终体验一致。
