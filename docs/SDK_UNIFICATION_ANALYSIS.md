# Claude Agent SDK 与 OpenAI Codex SDK 统一分析

## 执行摘要

**结论：可行，但需要适配层**

两个 SDK 的核心概念相似但实现细节差异较大。可以通过创建统一的前端数据模型和适配层来实现同时接入。

---

## 核心架构对比

### Claude Agent SDK (Python)

**核心概念：**
- **Client**: `ClaudeSDKClient` - 双向交互式会话
- **消息类型**: `UserMessage`, `AssistantMessage`, `SystemMessage`, `ResultMessage`, `StreamEvent`
- **内容块**: `TextBlock`, `ThinkingBlock`, `ToolUseBlock`, `ToolResultBlock`
- **通信方式**: 通过子进程启动 Claude CLI，使用 JSON-RPC 协议

**数据流：**
```
ClaudeSDKClient → SubprocessCLITransport → Claude CLI → Anthropic API
                ↓
         MessageParser → Message (typed)
```

---

### OpenAI Codex SDK (TypeScript)

**核心概念：**
- **Client**: `Codex` - 主入口类
- **Thread**: 对话线程，支持多轮对话
- **事件类型**: `ThreadEvent` (thread.started, turn.started, turn.completed, item.started, item.updated, item.completed)
- **Item 类型**: `AgentMessageItem`, `ReasoningItem`, `CommandExecutionItem`, `FileChangeItem`, `McpToolCallItem`, `WebSearchItem`, `TodoListItem`, `ErrorItem`
- **通信方式**: 通过子进程启动 Codex CLI，使用 JSONL 流式输出

**数据流：**
```
Codex → Thread → CodexExec → Codex CLI → OpenAI API
              ↓
       ThreadEvent stream → ThreadItem[]
```

---

## 关键差异

### 1. 消息模型

| 维度 | Claude Agent SDK | OpenAI Codex SDK |
|------|------------------|------------------|
| **基本单位** | Message (user/assistant/system/result) | ThreadEvent + ThreadItem |
| **内容结构** | ContentBlock[] (text/thinking/tool_use/tool_result) | ThreadItem (agent_message/reasoning/command_execution/file_change/mcp_tool_call/web_search/todo_list/error) |
| **流式更新** | StreamEvent (包含 Anthropic API 原始事件) | ItemStartedEvent → ItemUpdatedEvent → ItemCompletedEvent |
| **工具调用** | ToolUseBlock + ToolResultBlock | CommandExecutionItem / McpToolCallItem / FileChangeItem |

### 2. 会话管理

| 维度 | Claude Agent SDK | OpenAI Codex SDK |
|------|------------------|------------------|
| **会话抽象** | ClaudeSDKClient (有状态，支持中断) | Thread (轻量级，基于 thread_id) |
| **恢复会话** | `resume` 参数 + `session_id` | `resumeThread(id)` |
| **多轮对话** | `continue_conversation=True` | 同一个 Thread 实例多次调用 `run()` |

### 3. 流式响应

| 维度 | Claude Agent SDK | OpenAI Codex SDK |
|------|------------------|------------------|
| **API** | `async for message in client.messages()` | `async for event in thread.runStreamed()` |
| **事件粒度** | 消息级别 (完整的 Message) | 事件级别 (turn/item 生命周期) |
| **部分消息** | `include_partial_messages=True` → StreamEvent | 默认支持 (item.updated) |

---

## 统一数据模型设计

### 方案：适配器模式 + 统一前端模型

```typescript
// 统一的前端消息模型
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

// 统一的内容块模型
type UnifiedContentBlock =
  | { type: 'text'; text: string }
  | { type: 'thinking'; thinking: string; signature?: string }
  | { type: 'tool_use'; id: string; name: string; input: any; status?: 'in_progress' | 'completed' | 'failed' }
  | { type: 'tool_result'; toolUseId: string; content: any; isError?: boolean }
  | { type: 'command_execution'; command: string; output: string; exitCode?: number; status: 'in_progress' | 'completed' | 'failed' }
  | { type: 'file_change'; changes: FileChange[]; status: 'completed' | 'failed' }
  | { type: 'web_search'; query: string }
  | { type: 'todo_list'; items: TodoItem[] }
  | { type: 'error'; message: string }

// 统一的流式事件模型
interface UnifiedStreamEvent {
  type: 'message_start' | 'content_block_start' | 'content_block_delta' | 'content_block_end' | 'message_end'
  message?: UnifiedMessage
  contentBlock?: UnifiedContentBlock
  delta?: any
}
```

---

## 适配器实现策略

### Claude Agent SDK → 统一模型

```typescript
class ClaudeAdapter {
  // Message → UnifiedMessage
  convertMessage(message: ClaudeMessage): UnifiedMessage {
    switch (message.type) {
      case 'user':
      case 'assistant':
        return {
          id: generateId(),
          type: message.type,
          timestamp: Date.now(),
          content: this.convertContentBlocks(message.content),
          metadata: {
            model: message.model,
            sessionId: message.session_id
          }
        }
      case 'system':
        return {
          id: generateId(),
          type: 'system',
          timestamp: Date.now(),
          content: [{ type: 'text', text: JSON.stringify(message.data) }],
          metadata: { sessionId: message.session_id }
        }
      case 'result':
        return {
          id: generateId(),
          type: 'result',
          timestamp: Date.now(),
          content: [{ type: 'text', text: message.result || '' }],
          metadata: {
            usage: message.usage,
            sessionId: message.session_id
          }
        }
    }
  }

  // ContentBlock → UnifiedContentBlock
  convertContentBlocks(blocks: ClaudeContentBlock[]): UnifiedContentBlock[] {
    return blocks.map(block => {
      switch (block.type) {
        case 'text':
          return { type: 'text', text: block.text }
        case 'thinking':
          return { type: 'thinking', thinking: block.thinking, signature: block.signature }
        case 'tool_use':
          return { type: 'tool_use', id: block.id, name: block.name, input: block.input }
        case 'tool_result':
          return { type: 'tool_result', toolUseId: block.tool_use_id, content: block.content, isError: block.is_error }
      }
    })
  }

  // StreamEvent → UnifiedStreamEvent
  convertStreamEvent(event: ClaudeStreamEvent): UnifiedStreamEvent {
    // Claude 的 StreamEvent 包含 Anthropic API 原始事件
    const apiEvent = event.event
    switch (apiEvent.type) {
      case 'message_start':
        return { type: 'message_start', message: this.convertMessage(apiEvent.message) }
      case 'content_block_start':
        return { type: 'content_block_start', contentBlock: this.convertContentBlock(apiEvent.content_block) }
      case 'content_block_delta':
        return { type: 'content_block_delta', delta: apiEvent.delta }
      case 'content_block_stop':
        return { type: 'content_block_end' }
      case 'message_delta':
        return { type: 'message_end', delta: apiEvent.delta }
    }
  }
}
```

### Codex SDK → 统一模型

```typescript
class CodexAdapter {
  private itemsBuffer: Map<string, ThreadItem> = new Map()

  // ThreadEvent → UnifiedStreamEvent
  convertThreadEvent(event: ThreadEvent): UnifiedStreamEvent | null {
    switch (event.type) {
      case 'thread.started':
        return { type: 'message_start', message: { id: event.thread_id, type: 'assistant', timestamp: Date.now(), content: [], metadata: { threadId: event.thread_id } } }

      case 'turn.started':
        return { type: 'message_start' }

      case 'item.started':
        this.itemsBuffer.set(event.item.id, event.item)
        return { type: 'content_block_start', contentBlock: this.convertThreadItem(event.item) }

      case 'item.updated':
        this.itemsBuffer.set(event.item.id, event.item)
        return { type: 'content_block_delta', contentBlock: this.convertThreadItem(event.item) }

      case 'item.completed':
        const item = this.itemsBuffer.get(event.item.id)
        this.itemsBuffer.delete(event.item.id)
        return { type: 'content_block_end', contentBlock: this.convertThreadItem(event.item) }

      case 'turn.completed':
        return { type: 'message_end', delta: { usage: event.usage } }

      case 'turn.failed':
        return { type: 'content_block_end', contentBlock: { type: 'error', message: event.error.message } }
    }
  }

  // ThreadItem → UnifiedContentBlock
  convertThreadItem(item: ThreadItem): UnifiedContentBlock {
    switch (item.type) {
      case 'agent_message':
        return { type: 'text', text: item.text }

      case 'reasoning':
        return { type: 'thinking', thinking: item.text }

      case 'command_execution':
        return {
          type: 'command_execution',
          command: item.command,
          output: item.aggregated_output,
          exitCode: item.exit_code,
          status: item.status
        }

      case 'file_change':
        return {
          type: 'file_change',
          changes: item.changes,
          status: item.status
        }

      case 'mcp_tool_call':
        return {
          type: 'tool_use',
          id: item.id,
          name: `${item.server}.${item.tool}`,
          input: item.arguments,
          status: item.status
        }

      case 'web_search':
        return { type: 'web_search', query: item.query }

      case 'todo_list':
        return { type: 'todo_list', items: item.items }

      case 'error':
        return { type: 'error', message: item.message }
    }
  }

  // Turn → UnifiedMessage
  convertTurn(turn: Turn, threadId: string): UnifiedMessage {
    return {
      id: generateId(),
      type: 'assistant',
      timestamp: Date.now(),
      content: turn.items.map(item => this.convertThreadItem(item)),
      metadata: {
        usage: turn.usage,
        threadId
      }
    }
  }
}
```

---

## 前端集成架构

```typescript
// 统一的 SDK 客户端接口
interface UnifiedSDKClient {
  connect(): Promise<void>
  disconnect(): Promise<void>
  sendMessage(content: string): Promise<void>
  streamMessages(): AsyncIterator<UnifiedStreamEvent>
  interrupt(): Promise<void>
}

// Claude 客户端实现
class ClaudeClient implements UnifiedSDKClient {
  private client: ClaudeSDKClient
  private adapter: ClaudeAdapter

  async connect() {
    await this.client.connect()
  }

  async sendMessage(content: string) {
    await this.client.send_message(content)
  }

  async *streamMessages() {
    for await (const message of this.client.messages()) {
      if (message.type === 'stream_event') {
        yield this.adapter.convertStreamEvent(message)
      } else {
        yield { type: 'message_end', message: this.adapter.convertMessage(message) }
      }
    }
  }

  async interrupt() {
    await this.client.interrupt()
  }
}

// Codex 客户端实现
class CodexClient implements UnifiedSDKClient {
  private codex: Codex
  private thread: Thread | null = null
  private adapter: CodexAdapter

  async connect() {
    this.thread = this.codex.startThread()
  }

  async sendMessage(content: string) {
    if (!this.thread) throw new Error('Not connected')
    // Codex 不支持独立的 sendMessage，需要调用 run()
    // 这里可以使用 runStreamed() 并在后台处理
  }

  async *streamMessages() {
    if (!this.thread) throw new Error('Not connected')
    // 需要在外部调用 thread.runStreamed(input)
    // 这里只是示例，实际需要重新设计
  }

  async interrupt() {
    // Codex 通过 AbortSignal 中断
    // 需要在 TurnOptions 中传入 signal
  }
}

// 前端使用
const client = sdkType === 'claude'
  ? new ClaudeClient(options)
  : new CodexClient(options)

await client.connect()
await client.sendMessage('Hello')

for await (const event of client.streamMessages()) {
  // 统一处理流式事件
  handleStreamEvent(event)
}
```

---

## 实现挑战与解决方案

### 挑战 1: 会话模型差异

**问题：**
- Claude: 有状态的 Client，支持随时发送消息
- Codex: 无状态的 Thread，每次 `run()` 是一个完整的 turn

**解决方案：**
- 为 Codex 创建一个状态管理层，维护 Thread 实例和消息队列
- 将前端的 `sendMessage()` 调用转换为 Codex 的 `thread.run(input)`

### 挑战 2: 工具调用表示差异

**问题：**
- Claude: 通用的 `ToolUseBlock` + `ToolResultBlock`
- Codex: 特定的 `CommandExecutionItem`, `FileChangeItem`, `McpToolCallItem`

**解决方案：**
- 在统一模型中保留两种表示方式
- 前端 UI 根据 `type` 字段动态渲染不同的工具卡片

### 挑战 3: 流式更新粒度差异

**问题：**
- Claude: 基于 Anthropic API 的原始流式事件 (token 级别)
- Codex: 基于 Item 的生命周期事件 (item 级别)

**解决方案：**
- 统一模型采用 Codex 的粒度 (content_block 级别)
- Claude 的 StreamEvent 需要缓冲和聚合，转换为 content_block 级别的更新

### 挑战 4: 中断机制差异

**问题：**
- Claude: `client.interrupt()` 方法
- Codex: `AbortSignal` 传入 `TurnOptions`

**解决方案：**
- 统一接口提供 `interrupt()` 方法
- Codex 适配器内部维护 `AbortController`，在 `interrupt()` 时调用 `abort()`

---

## 推荐实现路径

### 阶段 1: 核心适配器 (1-2 天)

1. 定义统一的 TypeScript 类型 (`UnifiedMessage`, `UnifiedContentBlock`, `UnifiedStreamEvent`)
2. 实现 `ClaudeAdapter` 和 `CodexAdapter`
3. 编写单元测试验证转换逻辑

### 阶段 2: 客户端封装 (2-3 天)

1. 实现 `UnifiedSDKClient` 接口
2. 实现 `ClaudeClient` 和 `CodexClient`
3. 处理会话管理和状态同步

### 阶段 3: 前端集成 (3-5 天)

1. 修改现有的 `ClaudeSession.ts` 为 `UnifiedSession.ts`
2. 更新 Vue 组件以支持统一模型
3. 实现 SDK 切换 UI (下拉菜单或配置项)

### 阶段 4: 测试与优化 (2-3 天)

1. 端到端测试两个 SDK 的功能
2. 性能优化 (流式更新、内存管理)
3. 错误处理和边界情况

**总计：8-13 天**

---

## 风险与注意事项

### 高风险

1. **Codex SDK 的会话管理**
   - Codex 的 Thread 模型与 Claude 的 Client 模型差异较大
   - 需要额外的状态管理层，可能引入复杂性

2. **流式更新的性能**
   - Claude 的 token 级别更新需要缓冲和聚合
   - 可能影响实时性和内存占用

### 中风险

1. **工具调用的语义差异**
   - 两个 SDK 对工具调用的抽象不同
   - 需要仔细设计统一模型以保留语义

2. **错误处理的一致性**
   - 两个 SDK 的错误类型和处理方式不同
   - 需要统一的错误处理策略

### 低风险

1. **类型安全**
   - TypeScript 类型系统可以保证编译时安全
   - 适配器的单元测试可以覆盖大部分情况

---

## 结论

**可行性：✅ 高度可行**

通过适配器模式和统一的前端数据模型，可以实现同时接入 Claude Agent SDK 和 OpenAI Codex SDK。主要挑战在于会话管理和流式更新的差异，但都有成熟的解决方案。

**推荐方案：**
1. 采用适配器模式，保持两个 SDK 的独立性
2. 定义统一的前端数据模型，兼容两个 SDK 的特性
3. 分阶段实现，先核心功能后优化

**预期收益：**
- 用户可以在同一个工具中切换使用 Claude 和 Codex
- 前端代码复用，降低维护成本
- 为未来接入更多 SDK 奠定基础
