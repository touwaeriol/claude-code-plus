# 前端消息系统重构设计文档

## 📋 目录

1. [背景和问题](#背景和问题)
2. [核心设计理念](#核心设计理念)
3. [类型系统设计](#类型系统设计)
4. [数据流设计](#数据流设计)
5. [UI 渲染架构](#ui-渲染架构)
6. [实施步骤](#实施步骤)

---

## 背景和问题

### 当前架构的问题

1. **数据结构混乱**：
   - 数据分散在多个 Map 中（`sessionMessages`、`toolCallsMap`、`sessionModelIds`、`connectionStatuses`）
   - `toolCallsMap` 是全局的，不支持多会话
   - 工具调用信息既在 `Message.content` 中，又在 `toolCallsMap` 中，数据冗余

2. **响应式更新问题**：
   - 修改 `Map` 内部对象的属性不会触发 Vue 3 响应式更新
   - 工具状态一直显示"执行中"，无法更新为"成功"或"失败"

3. **前后端数据不一致**：
   - 后端返回的 `tool_result` 消息（role='user'）被前端存储为独立消息
   - 导致 UI 上显示多余的消息

4. **组件逻辑分散**：
   - `orderedElements` 在组件中动态计算，每次渲染都要重新计算
   - 逻辑分散在组件中，难以维护

---

## 核心设计理念

### 1. 单一数据源原则

**所有数据存储在 `SessionState` 中，组件只负责渲染**

```typescript
interface SessionState {
  id: string
  name: string
  createdAt: number
  updatedAt: number

  // 原始数据（来自后端，用于持久化）
  messages: Message[]

  // ViewModel（用于 UI 展示）
  displayItems: DisplayItem[]  // ← 核心：扁平的显示项列表

  // 运行中的工具调用（用于响应式更新）
  pendingToolCalls: Map<string, ToolCall>

  // 连接相关
  connectionStatus: ConnectionStatus
  modelId: string | null
  connection: ClaudeSession | null  // ← RPC 连接实例（不可序列化，持久化时跳过）
}
```

**说明**：
- ✅ 所有会话数据集中管理（包括 RPC 连接）
- ✅ 组件通过 computed 自动响应数据变化
- ✅ 不需要在组件中维护状态
- ✅ 不需要通过 sessionId 查找连接，直接使用 `session.connection`
- ⚠️ `connection` 字段在持久化时需要跳过（不能序列化 WebSocket 连接）
- ⚠️ 恢复会话时需要重新建立连接

### 2. DisplayItem 设计原则

**一个 DisplayItem = 消息列表中的一个显示项**

- `DisplayItem` 有多个子类：用户消息、AI 文本回复、工具调用、系统消息
- 工具调用有具体的子类：ReadToolCall、WriteToolCall、EditToolCall 等
- 每个子类有明确的数据模型（类型安全、易于扩展）

### 3. 工具调用处理原则

**工具调用和结果组合在一起，不作为独立消息**

- 后端返回的 `tool_result` 消息**不添加到 `messages`**
- `tool_result` 直接更新对应的 `ToolCall.result`
- `ToolCall` 是 reactive 对象，修改后自动触发 UI 更新

### 4. 数据转换原则

**在收到消息时立即转换为 DisplayItem，而不是在渲染时动态计算**

- 收到 `Message` → 立即转换为 `DisplayItem` → 添加到 `displayItems`
- 组件直接从 `displayItems` 读取数据，不需要计算

---

## 类型系统设计

### 1. DisplayItem 基础类型

```typescript
// ============ DisplayItem 基类 ============

interface BaseDisplayItem {
  id: string
  timestamp: number
}

// ============ DisplayItem 联合类型 ============

type DisplayItem =
  | UserMessage
  | AssistantText
  | ToolCall
  | SystemMessage
```

### 2. 具体消息类型

```typescript
// ============ 用户消息 ============

interface UserMessage extends BaseDisplayItem {
  type: 'userMessage'
  content: string
  images?: ImageBlock[]
  contexts?: ContextReference[]
}

// ============ AI 文本回复 ============

interface AssistantText extends BaseDisplayItem {
  type: 'assistantText'
  content: string
}

// ============ 系统消息 ============

interface SystemMessage extends BaseDisplayItem {
  type: 'systemMessage'
  content: string
  level: 'info' | 'warning' | 'error'
}
```

### 3. 工具调用类型系统

```typescript
// ============ 工具调用状态 ============

enum ToolCallStatus {
  RUNNING = 'RUNNING',
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED'
}

// ============ 工具调用基类 ============

interface BaseToolCall extends BaseDisplayItem {
  type: 'toolCall'
  toolType: string  // 具体工具类型
  status: ToolCallStatus
  startTime: number
  endTime?: number
}

// ============ 工具调用联合类型 ============

type ToolCall =
  | ReadToolCall
  | WriteToolCall
  | EditToolCall
  | MultiEditToolCall
  | TodoWriteToolCall
  // ... 可以继续扩展

// ============ Read 工具 ============

interface ReadToolCall extends BaseToolCall {
  toolType: 'Read'
  input: {
    path: string
    viewRange?: [number, number]
    searchQueryRegex?: string
    caseInsensitive?: boolean
  }
  result?: {
    type: 'success'
    content: string
    lineCount: number
    language?: string
  } | {
    type: 'error'
    message: string
  }
}

// ============ Write 工具 ============

interface WriteToolCall extends BaseToolCall {
  toolType: 'Write'
  input: {
    path: string
    fileContent: string
    addLastLineNewline?: boolean
  }
  result?: {
    type: 'success'
    output: string
    affectedFiles?: string[]
  } | {
    type: 'error'
    message: string
  }
}

// ============ Edit 工具 ============

interface EditToolCall extends BaseToolCall {
  toolType: 'Edit'
  input: {
    path: string
    oldStr: string
    newStr: string
    oldStrStartLineNumber: number
    oldStrEndLineNumber: number
  }
  result?: {
    type: 'success'
    oldContent: string
    newContent: string
    changedLines: [number, number]
  } | {
    type: 'error'
    message: string
  }
}

// ============ MultiEdit 工具 ============

interface MultiEditToolCall extends BaseToolCall {
  toolType: 'MultiEdit'
  input: {
    path: string
    edits: Array<{
      oldStr: string
      newStr: string
      oldStrStartLineNumber: number
      oldStrEndLineNumber: number
    }>
  }
  result?: {
    type: 'success'
    output: string
    editCount: number
  } | {
    type: 'error'
    message: string
  }
}

// ============ TodoWrite 工具 ============

interface TodoWriteToolCall extends BaseToolCall {
  toolType: 'TodoWrite'
  input: {
    path: string
    content: string
  }
  result?: {
    type: 'success'
    output: string
  } | {
    type: 'error'
    message: string
  }
}
```

---

## 数据流设计

### 1. 收到用户消息

```typescript
// 后端返回
const message: Message = {
  id: 'msg-1',
  role: 'user',
  content: [
    { type: 'text', text: '请帮我读取文件' }
  ],
  timestamp: 1234567890
}

// Store 处理
function handleUserMessage(session: SessionState, message: Message) {
  // 1. 存储原始消息
  session.messages.push(message)

  // 2. 转换为 UserMessage
  const item: UserMessage = {
    type: 'userMessage',
    id: message.id,
    content: extractTextContent(message),
    images: extractImageBlocks(message),
    contexts: extractContexts(message),
    timestamp: message.timestamp
  }

  // 3. 添加到 displayItems
  session.displayItems.push(item)
}
```

**结果**：
```typescript
session.displayItems = [
  { type: 'userMessage', id: 'msg-1', content: '请帮我读取文件', ... }
]
```

### 2. 收到 AI 消息（包含工具调用）

```typescript
// 后端返回
const message: Message = {
  id: 'msg-2',
  role: 'assistant',
  content: [
    { type: 'text', text: '好的，我来帮你' },
    { type: 'tool_use', id: 'tool-1', name: 'Read', input: {...} },
    { type: 'text', text: '正在读取...' }
  ],
  timestamp: 1234567891
}

// Store 处理
function handleAssistantMessage(session: SessionState, message: Message) {
  // 1. 存储原始消息
  session.messages.push(message)

  // 2. 转换为多个 DisplayItem
  const items: DisplayItem[] = []

  message.content.forEach((block, index) => {
    if (block.type === 'text') {
      // 文本块 → AssistantText
      items.push({
        type: 'assistantText',
        id: `${message.id}-text-${index}`,
        content: block.text,
        timestamp: message.timestamp
      })
    }

    if (block.type === 'tool_use') {
      // 工具调用块 → ToolCall
      const toolCall = createToolCall(block, message.timestamp)
      items.push(toolCall)

      // 注册到 pendingToolCalls（用于后续更新）
      session.pendingToolCalls.set(block.id, toolCall)
    }
  })

  // 3. 添加到 displayItems
  session.displayItems.push(...items)
}

// 创建 ToolCall
function createToolCall(block: ToolUseBlock, timestamp: number): ToolCall {
  const base = {
    type: 'toolCall' as const,
    id: block.id,
    status: ToolCallStatus.RUNNING,
    startTime: timestamp,
    timestamp: timestamp
  }

  // 根据工具类型创建具体的 ToolCall
  switch (block.name) {
    case 'Read':
      return reactive<ReadToolCall>({
        ...base,
        toolType: 'Read',
        input: block.input as ReadToolCall['input'],
        result: undefined
      })

    case 'Write':
      return reactive<WriteToolCall>({
        ...base,
        toolType: 'Write',
        input: block.input as WriteToolCall['input'],
        result: undefined
      })

    // ... 其他工具类型

    default:
      throw new Error(`Unknown tool type: ${block.name}`)
  }
}
```

**结果**：
```typescript
session.displayItems = [
  { type: 'userMessage', id: 'msg-1', content: '请帮我读取文件', ... },
  { type: 'assistantText', id: 'msg-2-text-0', content: '好的，我来帮你', ... },
  { type: 'toolCall', toolType: 'Read', id: 'tool-1', status: 'RUNNING', ... },
  { type: 'assistantText', id: 'msg-2-text-2', content: '正在读取...', ... }
]
```

### 3. 收到工具结果

```typescript
// 后端返回（role='user' 的消息，包含 tool_result）
const message: Message = {
  id: 'msg-3',
  role: 'user',
  content: [
    {
      type: 'tool_result',
      tool_use_id: 'tool-1',
      content: '文件内容...',
      is_error: false
    }
  ],
  timestamp: 1234567892
}

// Store 处理
function handleMessage(session: SessionState, message: Message) {
  // ✅ 检查是否是 tool_result 消息
  if (message.role === 'user' && message.content) {
    const toolResults = message.content.filter(b => b.type === 'tool_result')

    if (toolResults.length > 0) {
      // ✅ 处理 tool_result：更新 pendingToolCalls
      toolResults.forEach((result: ToolResultBlock) => {
        const toolCall = session.pendingToolCalls.get(result.tool_use_id)

        if (toolCall) {
          // 更新 ToolCall（reactive 对象，自动触发 UI 更新）
          toolCall.status = result.is_error
            ? ToolCallStatus.FAILED
            : ToolCallStatus.SUCCESS
          toolCall.endTime = Date.now()

          // 根据工具类型设置 result
          if (toolCall.toolType === 'Read') {
            (toolCall as ReadToolCall).result = result.is_error
              ? { type: 'error', message: result.content as string }
              : {
                  type: 'success',
                  content: result.content as string,
                  lineCount: (result.content as string).split('\n').length
                }
          }
          // ... 其他工具类型

          // 从 pendingToolCalls 删除
          session.pendingToolCalls.delete(result.tool_use_id)
        }
      })

      return  // ❌ 不添加到 messages 和 displayItems
    }
  }

  // 其他消息正常处理
  // ...
}
```

**关键点**：
- ❌ `tool_result` 消息**不添加到 `messages`**
- ❌ `tool_result` 消息**不添加到 `displayItems`**
- ✅ 只更新已存在的 `ToolCall`
- ✅ `ToolCall` 是 reactive 对象，修改后自动触发 UI 更新

**结果**：
```typescript
// displayItems 中的 ToolCall 自动更新
session.displayItems = [
  { type: 'userMessage', id: 'msg-1', content: '请帮我读取文件', ... },
  { type: 'assistantText', id: 'msg-2-text-0', content: '好的，我来帮你', ... },
  {
    type: 'toolCall',
    toolType: 'Read',
    id: 'tool-1',
    status: 'SUCCESS',  // ← 更新了
    result: { type: 'success', content: '...', lineCount: 100 }  // ← 更新了
  },
  { type: 'assistantText', id: 'msg-2-text-2', content: '正在读取...', ... }
]
```

---

## UI 渲染架构

### 1. 消息列表组件

```typescript
// MessageList.vue
<template>
  <div class="message-list">
    <!-- ✅ 只需要一层循环 -->
    <div v-for="item in displayItems" :key="item.id">

      <!-- 用户消息 -->
      <UserMessageDisplay
        v-if="item.type === 'userMessage'"
        :item="item"
      />

      <!-- AI 文本回复 -->
      <AssistantTextDisplay
        v-else-if="item.type === 'assistantText'"
        :item="item"
      />

      <!-- 工具调用 -->
      <ToolCallDisplay
        v-else-if="item.type === 'toolCall'"
        :item="item"
      />

      <!-- 系统消息 -->
      <SystemMessageDisplay
        v-else-if="item.type === 'systemMessage'"
        :item="item"
      />

    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'

const sessionStore = useSessionStore()

// ✅ 直接从 store 获取，不需要计算
const displayItems = computed(() => {
  return sessionStore.currentSession?.displayItems || []
})
</script>
```

### 2. 工具调用组件

```typescript
// ToolCallDisplay.vue
<template>
  <div class="tool-call-display">

    <!-- Read 工具 -->
    <ReadToolCallDisplay
      v-if="item.toolType === 'Read'"
      :item="item"
    />

    <!-- Write 工具 -->
    <WriteToolCallDisplay
      v-else-if="item.toolType === 'Write'"
      :item="item"
    />

    <!-- Edit 工具 -->
    <EditToolCallDisplay
      v-else-if="item.toolType === 'Edit'"
      :item="item"
    />

    <!-- MultiEdit 工具 -->
    <MultiEditToolCallDisplay
      v-else-if="item.toolType === 'MultiEdit'"
      :item="item"
    />

    <!-- TodoWrite 工具 -->
    <TodoWriteToolCallDisplay
      v-else-if="item.toolType === 'TodoWrite'"
      :item="item"
    />

    <!-- 通用工具显示（fallback） -->
    <GenericToolCallDisplay
      v-else
      :item="item"
    />

  </div>
</template>

<script setup lang="ts">
import type { ToolCall } from '@/types/display'

interface Props {
  item: ToolCall
}

defineProps<Props>()
</script>
```

### 3. 具体工具组件示例

```typescript
// ReadToolCallDisplay.vue
<template>
  <div class="read-tool-call">
    <div class="tool-header">
      <span class="tool-icon">📖</span>
      <span class="tool-name">Read</span>
      <span class="tool-status" :class="statusClass">{{ statusText }}</span>
    </div>

    <div class="tool-input">
      <div class="input-item">
        <span class="label">文件路径：</span>
        <span class="value">{{ item.input.path }}</span>
      </div>
      <div v-if="item.input.viewRange" class="input-item">
        <span class="label">行范围：</span>
        <span class="value">{{ item.input.viewRange[0] }} - {{ item.input.viewRange[1] }}</span>
      </div>
    </div>

    <div v-if="item.result" class="tool-result">
      <div v-if="item.result.type === 'success'" class="success-result">
        <div class="result-meta">
          <span>行数：{{ item.result.lineCount }}</span>
          <span v-if="item.result.language">语言：{{ item.result.language }}</span>
        </div>
        <pre class="result-content">{{ item.result.content }}</pre>
      </div>
      <div v-else-if="item.result.type === 'error'" class="error-result">
        <span class="error-icon">❌</span>
        <span class="error-message">{{ item.result.message }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ReadToolCall } from '@/types/display'

interface Props {
  item: ReadToolCall
}

const props = defineProps<Props>()

const statusClass = computed(() => {
  switch (props.item.status) {
    case 'RUNNING': return 'status-running'
    case 'SUCCESS': return 'status-success'
    case 'FAILED': return 'status-failed'
    default: return ''
  }
})

const statusText = computed(() => {
  switch (props.item.status) {
    case 'RUNNING': return '执行中...'
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    default: return ''
  }
})
</script>
```

### 4. 会话导航与历史视图

新版 UI 需要提供“顶部 Tab + 历史列表”能力来匹配 JetBrains 插件体验，因此我们在 `ModernChatView.vue` 中引入了两个配套组件：

- **ChatHeader.vue**
  - 直接依赖 `sessionStore.activeTabs`，优先展示当前会话，并附带所有 `isGenerating === true` 的会话
  - Tab 上的绿色圆点来自 `sessionState.isGenerating`
  - 右侧两个按钮分别触发历史覆盖层和新建会话（委托给 store 的 `startNewSession`）

- **SessionListOverlay.vue**
  - 通过 `<Teleport to="body">` 渲染，避免受父级布局影响
  - 数据来源 `sessionStore.allSessions`，按照 `lastActiveAt` 倒序排列最近使用的会话
  - 支持 ESC / 背景点击关闭，所有交互通过事件传回 `ModernChatView`，由该视图统一调用 `switchSession`

- **sessionStore.ts 双输出**
  - 运行时依旧使用 `Map<string, SessionState>` 保存全量状态
  - 新增 `activeTabs`、`allSessions` 两个 computed，将 Map 转换为适合 UI 消费的数组
  - 对外暴露 `setSessionGenerating()`，并在内部使用 `touchSession()` 统一维护 `lastActiveAt`，从而驱动 Tab 点亮与历史排序

整体架构保持“Store 管数据、组件只展示”的原则：ChatHeader 和覆盖层不需要知道消息细节，只消费 store 暴露的轻量 ViewModel，就能实现 Tab 状态同步和历史列表更新。

---

## 实施步骤

### 第一步：定义类型系统

**文件**：`frontend/src/types/display.ts`

1. 定义 `BaseDisplayItem` 接口
2. 定义 `UserMessage`、`AssistantText`、`SystemMessage` 接口
3. 定义 `BaseToolCall` 接口
4. 定义所有工具的具体类型：`ReadToolCall`、`WriteToolCall` 等
5. 定义 `DisplayItem` 和 `ToolCall` 联合类型

### 第二步：重构 SessionState

**文件**：`frontend/src/stores/sessionStore.ts`

1. 修改 `SessionState` 接口：
   ```typescript
   interface SessionState {
     id: string
     name: string
     createdAt: number
     updatedAt: number
     messages: Message[]
     displayItems: DisplayItem[]  // ← 新增
     pendingToolCalls: Map<string, ToolCall>  // ← 修改类型
     connectionStatus: ConnectionStatus
     modelId: string | null
     connection: ClaudeSession | null  // ← 新增：RPC 连接实例
   }
   ```

2. 删除旧的数据结构：
   - 删除 `sessions: ref<Session[]>`
   - 删除 `sessionMessages: ref<Map<string, Message[]>>`
   - 删除 `sessionModelIds: ref<Map<string, string>>`
   - 删除 `connectionStatuses: ref<Map<string, ConnectionStatus>>`
   - 删除 `toolCallsMap: ref<Map<string, ToolCallState>>`
   - 删除 `toolResultsMap: ref<Map<string, ToolResultBlock>>`

3. 添加新的数据结构：
   ```typescript
   const sessions = ref<Map<string, SessionState>>(new Map())
   const currentSessionId = ref<string | null>(null)
   ```

4. 修改 `createSession` 函数：
   ```typescript
   async function createSession(name?: string) {
     // 创建 RPC 连接
     const connection = new ClaudeSession()

     // 创建会话状态
     const session = reactive<SessionState>({
       id: '', // 连接后会更新
       name: name || `会话 ${new Date().toLocaleString()}`,
       createdAt: Date.now(),
       updatedAt: Date.now(),
       messages: [],
       displayItems: [],
       pendingToolCalls: new Map(),
       connectionStatus: 'connecting',
       modelId: null,
       connection: connection  // ← 保存连接实例
     })

     // 订阅消息
     connection.onMessage((message) => {
       handleMessage(session, message)
     })

     // 连接并获取 sessionId
     const sessionId = await connection.connect(options)
     session.id = sessionId
     session.connectionStatus = 'connected'

     // 保存会话
     sessions.value.set(sessionId, session)
     return session
   }
   ```

5. 修改 `sendMessage` 函数：
   ```typescript
   async function sendMessage(message: string) {
     if (!currentSessionId.value) {
       throw new Error('当前没有活跃的会话')
     }

     const session = sessions.value.get(currentSessionId.value)
     if (!session || !session.connection) {
       throw new Error('会话连接不存在')
     }

     // 直接使用 session.connection 发送消息
     await session.connection.sendMessage(message)
   }
   ```

6. 修改 `interrupt` 函数：
   ```typescript
   async function interrupt() {
     if (!currentSessionId.value) {
       throw new Error('当前没有活跃的会话')
     }

     const session = sessions.value.get(currentSessionId.value)
     if (!session || !session.connection) {
       throw new Error('会话连接不存在')
     }

     // 直接使用 session.connection 中断
     await session.connection.interrupt()
   }
   ```

### 第三步：实现消息转换逻辑

**文件**：`frontend/src/stores/sessionStore.ts`

1. 实现 `convertToDisplayItems` 函数：
   - 处理用户消息 → `UserMessage`
   - 处理 AI 消息 → `AssistantText` + `ToolCall`
   - 处理系统消息 → `SystemMessage`

2. 实现 `createToolCall` 函数：
   - 根据工具类型创建具体的 `ToolCall`
   - 使用 `reactive()` 包装，确保响应式

3. 修改 `handleMessage` 函数：
   - 接收 `session: SessionState` 参数（而不是 sessionId）
   - 检查是否是 `tool_result` 消息
   - 如果是，更新 `pendingToolCalls`，不添加到 `messages` 和 `displayItems`
   - 如果不是，正常处理

### 第四步：创建 UI 组件

**新建文件**：

1. `frontend/src/components/chat/MessageList.vue` - 消息列表组件
2. `frontend/src/components/chat/UserMessageDisplay.vue` - 用户消息组件
3. `frontend/src/components/chat/AssistantTextDisplay.vue` - AI 文本回复组件
4. `frontend/src/components/chat/ToolCallDisplay.vue` - 工具调用路由组件
5. `frontend/src/components/chat/tools/ReadToolCallDisplay.vue` - Read 工具组件
6. `frontend/src/components/chat/tools/WriteToolCallDisplay.vue` - Write 工具组件
7. `frontend/src/components/chat/tools/EditToolCallDisplay.vue` - Edit 工具组件
8. 其他工具组件...

### 第五步：会话数据管理

**重要说明**：会话数据由后端 SDK 管理，前端不需要持久化到 localStorage。

**文件**：`frontend/src/stores/sessionStore.ts`

1. **会话数据来源**：
   - 会话列表、消息历史等数据由后端 Kotlin 插件和 SDK 管理
   - 前端通过 RPC 接口获取会话数据
   - 前端只在内存中维护 `SessionState`，不需要保存到 localStorage

2. **会话生命周期**：
   - **创建会话**：通过 `createSession()` 调用后端 API 创建
   - **加载会话**：通过 `switchSession()` 从后端加载历史消息
   - **更新会话**：通过 WebSocket 接收实时消息更新
   - **删除会话**：通过 `deleteSession()` 调用后端 API 删除

3. **连接管理**：
   ```typescript
   async function switchSession(sessionId: string) {
     const session = sessions.value.get(sessionId)
     if (!session) return

     // 创建新的 RPC 连接
     const connection = new ClaudeSession(sessionId, {
       onMessage: (msg) => handleMessage(sessionId, msg),
       onStatusChange: (status) => {
         session.connectionStatus = status
       }
     })

     await connection.connect()
     session.connection = connection
     session.connectionStatus = 'connected'
   }
   ```

### 第六步：删除旧代码

1. 删除 `MessageDisplay.vue` 中的 `enhancedMessage` computed
2. 删除 `claudeService` 中的会话管理逻辑：
   - 删除 `sessions: Map<string, ClaudeSession>`
   - 删除 `sendMessage(sessionId, message)` 等方法
   - 只保留 `ClaudeSession` 类的定义
3. 删除 `sessionStore` 中的旧函数：
   - `registerToolCall`
   - `updateToolResult`
   - `getToolStatus`
   - `getToolResult`
4. 删除 `EnhancedMessage` 类型（如果不再使用）
5. 删除旧的 `Session` 接口（已被 `SessionState` 替代）

### 第七步：测试验证

1. 创建新会话
2. 发送消息触发工具调用
3. 验证工具状态从 RUNNING → SUCCESS
4. 验证工具可以折叠/展开
5. 切换会话，验证消息和工具状态正确显示
6. 刷新页面，验证状态恢复（从 localStorage 加载）
7. 验证重新连接功能（点击断开连接的会话时自动重连）

---

## 设计优势

### 1. 类型安全

- 每种工具有明确的 input 和 result 类型
- TypeScript 可以自动推断和检查类型
- IDE 提供完整的代码补全

### 2. 易于扩展

添加新工具只需：
1. 定义新的 `XxxToolCall` 接口
2. 在 `createToolCall` 中添加 case
3. 创建对应的 `XxxToolCallDisplay` 组件

### 3. 数据完整

- 调用参数和结果组合在一起
- 不需要额外查找关联
- 单一数据源，易于维护

### 4. 渲染清晰

- 每种消息类型有对应的组件
- 每种工具有专门的展示组件
- 可以针对每种工具定制 UI

### 5. 响应式自动

- `ToolCall` 是 reactive 对象
- 修改 status 和 result 自动触发 UI 更新
- 不需要手动触发更新

### 6. 性能优化

- `displayItems` 只转换一次，不需要每次重新计算
- 扁平的数组结构，渲染效率高
- 只有一层循环，不需要嵌套循环

---

## 关键注意事项

### 1. tool_result 消息处理

**重要**：后端返回的 `tool_result` 消息（role='user'）**不添加到 `messages` 和 `displayItems`**

```typescript
// ❌ 错误做法
if (message.role === 'user') {
  session.messages.push(message)  // 不要这样做
  session.displayItems.push(...)  // 不要这样做
}

// ✅ 正确做法
if (message.role === 'user' && hasToolResult(message)) {
  updatePendingToolCalls(session, message)  // 只更新 pendingToolCalls
  return  // 不添加到 messages 和 displayItems
}
```

### 2. reactive 对象

**重要**：`ToolCall` 必须使用 `reactive()` 包装，确保响应式更新

```typescript
// ✅ 正确做法
const toolCall = reactive<ReadToolCall>({
  type: 'toolCall',
  toolType: 'Read',
  status: ToolCallStatus.RUNNING,
  // ...
})

// 后续修改会触发 UI 更新
toolCall.status = ToolCallStatus.SUCCESS
toolCall.result = { ... }
```

### 3. 一个 Message 可能转换为多个 DisplayItem

```typescript
// 一条 AI 消息
const message: Message = {
  content: [
    { type: 'text', text: '好的' },
    { type: 'tool_use', ... },
    { type: 'text', text: '正在处理' }
  ]
}

// 转换为 3 个 DisplayItem
const items: DisplayItem[] = [
  { type: 'assistantText', content: '好的', ... },
  { type: 'toolCall', ... },
  { type: 'assistantText', content: '正在处理', ... }
]
```

### 4. pendingToolCalls 的作用

- 存储运行中的 `ToolCall` 引用
- 用于在收到 `tool_result` 时快速查找并更新
- 完成后可以删除（因为 `ToolCall` 已经在 `displayItems` 中）

---

## 总结

这个设计方案的核心思想是：

1. **单一数据源**：所有数据存储在 `SessionState` 中
2. **扁平结构**：`displayItems` 是扁平的数组，不是嵌套结构
3. **类型安全**：每种消息和工具有明确的类型定义
4. **响应式自动**：使用 reactive 对象，修改后自动触发 UI 更新
5. **职责分离**：Store 负责数据管理，组件只负责渲染

通过这个设计，可以解决当前架构的所有问题，并为未来的扩展打下良好的基础。
```

