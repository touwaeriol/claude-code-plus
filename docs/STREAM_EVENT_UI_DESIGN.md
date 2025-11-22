# Stream Event UI 实时更新设计方案

## 📋 概述

本文档描述了前端如何实现 Stream Event 的 UI 实时更新，确保用户能够看到 Claude 回复的实时流式输出。

## 🏗️ 架构设计

### 数据流

```
后端 (Kotlin SDK)
  ↓ StreamEvent
WebSocket RPC
  ↓ JSON
前端 ClaudeSession
  ↓ Message Handler
sessionStore.handleMessage()
  ↓ StreamEvent 解析
streamEventHandler.ts
  ↓ 状态更新
Vue 响应式系统
  ↓ UI 渲染
MessageDisplay.vue
```

### 核心组件

1. **类型定义** (`frontend/src/types/streamEvent.ts`)
   - 定义所有 Stream Event 类型
   - 提供类型安全的接口

2. **工具函数** (`frontend/src/utils/streamEventHandler.ts`)
   - 解析 Stream Event 数据
   - 类型守卫函数
   - 增量更新处理函数

3. **状态管理** (`frontend/src/stores/sessionStore.ts`)
   - `handleStreamEvent()`: 主处理函数
   - 管理消息状态和工具调用状态

4. **UI 组件** (`frontend/src/components/chat/MessageDisplay.vue`)
   - 实时渲染消息内容
   - 显示流式更新效果

## 🔄 Stream Event 处理流程

### 1. 消息开始 (`message_start`)

```typescript
// 创建占位符消息
const placeholder = createPlaceholderMessage()
sessionState.messages.push(placeholder)

// 标记为正在生成
setSessionGenerating(sessionId, true)
```

**UI 效果**: 显示"正在生成..."指示器

### 2. 内容块开始 (`content_block_start`)

#### 文本块
```typescript
// 文本块通常不需要特殊处理，等待 delta 事件
```

#### 工具调用块
```typescript
const toolUseBlock: ToolUseBlock = {
  type: 'tool_use',
  id: content_block.id,
  name: content_block.name,
  input: content_block.input || {}
}
lastAssistantMessage.content.push(toolUseBlock)

// 注册到 store
registerToolCall(toolUseBlock)
```

**UI 效果**: 显示工具调用卡片（折叠状态）

#### Thinking 块
```typescript
const thinkingBlock: ThinkingBlock = {
  type: 'thinking',
  thinking: content_block.thinking || '',
  signature: content_block.signature
}
lastAssistantMessage.content.push(thinkingBlock)
```

**UI 效果**: 显示 Thinking 内容（可选，通常隐藏）

### 3. 内容块增量更新 (`content_block_delta`)

#### 文本增量 (`text_delta`)
```typescript
applyTextDelta(message, index, delta)
// 追加文本到现有文本块
```

**UI 效果**: 文本实时追加显示，打字机效果

#### 工具输入 JSON 增量 (`input_json_delta`)
```typescript
applyInputJsonDelta(message, index, delta, accumulator)
// 累积 partial_json，解析后更新工具调用的 input
```

**UI 效果**: 工具调用参数实时更新（展开时可见）

#### Thinking 增量 (`thinking_delta`)
```typescript
applyThinkingDelta(message, index, delta)
// 追加 Thinking 内容
```

**UI 效果**: Thinking 内容实时更新（可选显示）

### 4. 内容块结束 (`content_block_stop`)

```typescript
// 清理工具输入的 JSON 累积器
toolInputJsonAccumulator.delete(accumulatorKey)
```

**UI 效果**: 工具调用参数解析完成

### 5. 消息结束 (`message_stop`)

```typescript
// 清理所有累积器
toolInputJsonAccumulator.clear()

// 停止生成状态
setSessionGenerating(sessionId, false)

// 更新消息时间戳
message.timestamp = Date.now()
```

**UI 效果**: 隐藏"正在生成..."指示器，消息完成

## 📝 实现细节

### 类型安全

使用 TypeScript 类型守卫确保类型安全：

```typescript
if (isTextDelta(delta)) {
  applyTextDelta(message, index, delta)
} else if (isInputJsonDelta(delta)) {
  applyInputJsonDelta(message, index, delta, accumulator)
} else if (isThinkingDelta(delta)) {
  applyThinkingDelta(message, index, delta)
}
```

### 不可变更新

所有状态更新都使用不可变模式，确保 Vue 响应式系统正常工作：

```typescript
// ❌ 错误：直接修改
message.content[index].text += delta.text

// ✅ 正确：不可变更新
const newContent = [...message.content]
newContent[index] = {
  ...newContent[index],
  text: newContent[index].text + delta.text
}
message.content = newContent
```

### JSON 累积策略

工具输入的 JSON 是增量传输的，需要累积后才能解析：

```typescript
// 累积 partial_json
const accumulatedJson = accumulator.get(key) + delta.partial_json
accumulator.set(key, accumulatedJson)

// 尝试解析
try {
  const parsed = JSON.parse(accumulatedJson)
  // 更新工具调用块的 input
} catch (e) {
  // JSON 还不完整，等待更多增量
}
```

### 占位符消息管理

使用占位符消息确保所有 stream event 都有目标消息：

```typescript
// 查找或创建最后一个 assistant 消息
const lastMessage = findOrCreateLastAssistantMessage(messages)

// 如果 message_start 事件包含消息 ID，更新占位符
if (event.message?.id && lastMessage.id.startsWith('assistant-placeholder-')) {
  lastMessage.id = event.message.id
}
```

## 🎨 UI 更新策略

### 文本流式显示

- **实时追加**: 每次收到 `text_delta` 立即更新 UI
- **打字机效果**: 使用 CSS 动画或 JavaScript 实现平滑的文本显示
- **自动滚动**: 如果用户在底部，自动滚动到最新内容

### 工具调用实时更新

- **折叠状态**: 默认折叠，显示工具名称和状态
- **展开状态**: 显示完整的工具参数（实时更新）
- **状态指示**: 使用彩色圆点表示状态（绿色=成功，红色=失败，灰色=进行中）

### 性能优化

1. **防抖/节流**: 对于高频更新，使用防抖或节流
2. **虚拟滚动**: 使用 `vue3-virtual-scroll-list` 处理大量消息
3. **增量更新**: 只更新变化的部分，不重新渲染整个消息

## 🔍 调试技巧

### 日志输出

所有 stream event 处理都包含详细的日志：

```typescript
console.log(`📡 [handleStreamEvent] 处理事件类型: ${eventType}`)
console.log(`📝 [handleStreamEvent] 更新文本块 #${index}, 当前长度: ${text.length}`)
console.log(`🔧 [handleStreamEvent] 更新工具输入 JSON: ${toolName}`)
```

### 状态检查

在浏览器控制台检查状态：

```typescript
// 检查当前会话的消息
sessionStore.currentMessages

// 检查工具调用状态
sessionStore.toolCallsMap

// 检查 JSON 累积器
sessionStore.toolInputJsonAccumulator
```

## 📚 相关文件

- **类型定义**: `frontend/src/types/streamEvent.ts`
- **工具函数**: `frontend/src/utils/streamEventHandler.ts`
- **状态管理**: `frontend/src/stores/sessionStore.ts`
- **UI 组件**: `frontend/src/components/chat/MessageDisplay.vue`
- **消息类型**: `frontend/src/types/message.ts`

## ✅ 已完成功能

- [x] Stream Event 类型定义
- [x] 类型安全的解析函数
- [x] 文本增量更新 (`text_delta`)
- [x] 工具输入 JSON 增量更新 (`input_json_delta`)
- [x] Thinking 增量更新 (`thinking_delta`)
- [x] 工具调用块创建 (`content_block_start`)
- [x] 消息开始/结束处理 (`message_start`, `message_stop`)
- [x] 占位符消息管理
- [x] JSON 累积策略
- [x] 不可变更新模式

## 🚀 未来优化

- [ ] 添加防抖/节流优化高频更新
- [ ] 实现打字机效果动画
- [ ] 优化大量消息的性能
- [ ] 添加 Thinking 内容的显示选项
- [ ] 支持消息编辑和重新生成



