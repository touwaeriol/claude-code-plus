# 混合架构集成指南

本文档说明如何在 Vue 组件中使用新的混合架构（WebSocket + RESTful API）。

## 架构概览

```
Vue 组件
  ↓
sessionStore (Pinia)
  ↓
├─ apiClient (RESTful API) - 会话管理
└─ claudeService (WebSocket) - 消息交互
  ↓
后端服务
```

## 在 ModernChatView.vue 中集成

### 1. 导入依赖

```typescript
<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { claudeService } from '@/services/claudeService'

// 使用 session store
const sessionStore = useSessionStore()

// 获取当前会话的消息
const messages = computed(() => sessionStore.currentMessages)
const currentSessionId = computed(() => sessionStore.currentSessionId)
</script>
```

### 2. 初始化会话

```typescript
onMounted(async () => {
  // 加载会话列表
  await sessionStore.loadSessions()

  // sessionStore.loadSessions() 会自动：
  // 1. 调用 apiClient.getSessions() 获取会话列表
  // 2. 如果有会话，自动切换到第一个会话
  // 3. switchSession() 会自动建立 WebSocket 连接并加载历史
})
```

### 3. 发送消息（占位符模式）

```typescript
async function handleSendMessage(text: string) {
  const sessionId = currentSessionId.value
  if (!sessionId) {
    console.error('❌ 没有活跃的会话')
    return
  }

  // 1. 立即添加用户消息到 UI
  sessionStore.addMessage(sessionId, {
    type: 'user',
    content: text,
    timestamp: Date.now()
  })

  // 2. 添加助手占位符
  const assistantMsgId = `assistant-${Date.now()}`
  sessionStore.addMessage(sessionId, {
    id: assistantMsgId,
    type: 'assistant',
    content: [],
    isStreaming: true,
    timestamp: Date.now()
  })

  // 3. 通过 WebSocket 发送消息
  // sessionStore 已经在 switchSession() 时建立了 WebSocket 连接
  // 并设置了消息处理回调 handleWebSocketMessage()
  // 所以这里只需要发送消息即可
  claudeService.sendMessage(sessionId, text)

  // 4. 后端响应会通过 WebSocket 自动推送回来
  // sessionStore.handleWebSocketMessage() 会自动更新 UI
}
```

### 4. 中断操作

```typescript
function handleStopGeneration() {
  const sessionId = currentSessionId.value
  if (sessionId) {
    claudeService.interrupt(sessionId)
  }
}
```

### 5. 会话管理

```typescript
// 创建新会话
async function createNewSession(name?: string) {
  const session = await sessionStore.createSession(name)
  // sessionStore.createSession() 会自动：
  // 1. 调用 apiClient.createSession()
  // 2. 调用 switchSession() 切换到新会话
  // 3. 建立 WebSocket 连接
}

// 切换会话
async function switchToSession(sessionId: string) {
  await sessionStore.switchSession(sessionId)
  // switchSession() 会自动：
  // 1. 断开旧会话的 WebSocket 连接
  // 2. 加载新会话的历史消息
  // 3. 建立新会话的 WebSocket 连接
}

// 删除会话
async function deleteCurrentSession() {
  const sessionId = currentSessionId.value
  if (sessionId) {
    await sessionStore.deleteSession(sessionId)
    // deleteSession() 会自动：
    // 1. 断开 WebSocket 连接
    // 2. 清除消息缓存
    // 3. 切换到第一个可用会话
  }
}
```

### 6. 清理资源

```typescript
onBeforeUnmount(() => {
  // 断开所有 WebSocket 连接
  claudeService.disconnectAll()
})
```

## 消息流程说明

### 发送消息流程

```
1. 用户输入 "Hello Claude"
   ↓
2. handleSendMessage() 立即显示用户消息
   ↓
3. 添加助手占位符（content: [], isStreaming: true）
   ↓
4. claudeService.sendMessage() 通过 WebSocket 发送
   ↓
5. WebSocket 连接已在 switchSession() 时建立
   ↓
6. 后端收到消息，通过 ClaudeSessionManager 处理
   ↓
7. SDK 返回响应，通过 WebSocketHandler 推送
   ↓
8. 前端 WebSocket 收到消息
   ↓
9. sessionStore.handleWebSocketMessage() 自动更新 UI
```

### WebSocket 消息类型

```typescript
// 助手消息（流式）
{
  type: 'assistant',
  message: {
    content: [
      { type: 'text', text: '这是第一段回复...' }
    ],
    model: 'claude-sonnet-4-5-20250929',
    isStreaming: true
  }
}

// 结束标志
{
  type: 'result',
  message: {
    subtype: 'success',
    is_error: false,
    num_turns: 1,
    tokenUsage: { input: 100, output: 200 }
  }
}

// 错误消息
{
  type: 'error',
  message: {
    error: '错误信息'
  }
}
```

## 关键设计特点

### 1. 自动资源管理

- **WebSocket 连接**：switchSession() 自动断开旧连接，建立新连接
- **消息缓存**：deleteSession() 自动清除缓存
- **历史加载**：switchSession() 自动加载历史（如果未加载）

### 2. 占位符模式

```typescript
// 发送前
messages = [
  { type: 'user', content: 'Hello' }
]

// 发送后立即
messages = [
  { type: 'user', content: 'Hello' },
  { type: 'assistant', content: [], isStreaming: true }  // 占位符
]

// 收到响应后
messages = [
  { type: 'user', content: 'Hello' },
  { type: 'assistant', content: [{ type: 'text', text: 'Hi!' }], isStreaming: true }
]

// 完成时
messages = [
  { type: 'user', content: 'Hello' },
  { type: 'assistant', content: [{ type: 'text', text: 'Hi!' }], isStreaming: false }
]
```

### 3. 会话隔离

- 每个 sessionId 独立的 WebSocket 连接
- 每个 sessionId 独立的消息列表
- 消息过滤：只处理当前会话的消息

### 4. 错误处理

- WebSocket 自动重连（最多 5 次）
- 连接失败时消息加入队列
- 连接恢复后自动发送队列消息

## 完整示例

```vue
<template>
  <div class="chat-view">
    <!-- 消息列表 -->
    <div class="messages">
      <div
        v-for="msg in messages"
        :key="msg.id"
        :class="['message', msg.type]"
      >
        <template v-if="msg.type === 'user'">
          {{ msg.content }}
        </template>
        <template v-else-if="msg.type === 'assistant'">
          <div v-if="msg.isStreaming" class="streaming-indicator">
            ⏳ 生成中...
          </div>
          <div v-for="(block, i) in msg.content" :key="i">
            {{ block.text }}
          </div>
        </template>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-area">
      <input
        v-model="inputText"
        @keyup.enter="handleSendMessage(inputText)"
        :disabled="!currentSessionId"
        placeholder="输入消息..."
      />
      <button @click="handleSendMessage(inputText)" :disabled="!currentSessionId">
        发送
      </button>
      <button @click="handleStopGeneration" :disabled="!isGenerating">
        停止
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { claudeService } from '@/services/claudeService'

const sessionStore = useSessionStore()
const inputText = ref('')

const messages = computed(() => sessionStore.currentMessages)
const currentSessionId = computed(() => sessionStore.currentSessionId)
const isGenerating = computed(() => {
  const lastMsg = messages.value[messages.value.length - 1]
  return lastMsg?.type === 'assistant' && lastMsg?.isStreaming
})

onMounted(async () => {
  await sessionStore.loadSessions()
})

onBeforeUnmount(() => {
  claudeService.disconnectAll()
})

async function handleSendMessage(text: string) {
  if (!text.trim() || !currentSessionId.value) return

  const sessionId = currentSessionId.value

  // 1. 显示用户消息
  sessionStore.addMessage(sessionId, {
    type: 'user',
    content: text,
    timestamp: Date.now()
  })

  // 2. 添加占位符
  sessionStore.addMessage(sessionId, {
    id: `assistant-${Date.now()}`,
    type: 'assistant',
    content: [],
    isStreaming: true,
    timestamp: Date.now()
  })

  // 3. 发送消息
  claudeService.sendMessage(sessionId, text)

  // 4. 清空输入框
  inputText.value = ''
}

function handleStopGeneration() {
  if (currentSessionId.value) {
    claudeService.interrupt(currentSessionId.value)
  }
}
</script>
```

## 测试步骤

1. **启动后端服务**：确保 Ktor 服务器运行
2. **打开前端**：浏览器访问前端页面
3. **查看控制台**：应该看到 WebSocket 连接成功
4. **发送消息**：输入文本并发送
5. **观察流程**：
   - 用户消息立即显示
   - 助手占位符立即显示
   - WebSocket 消息逐步到达
   - UI 逐步更新

## 故障排查

### WebSocket 连接失败

```
❌ WebSocket 错误: sessionId=xxx
```

**解决方法**：
- 检查后端服务是否运行
- 检查端口是否正确
- 检查防火墙设置

### 消息未显示

```
⚠️ 忽略非当前会话的消息
```

**解决方法**：
- 确认 sessionId 匹配
- 检查 handleWebSocketMessage() 逻辑

### 自动重连失败

```
🔄 尝试重连 (5/5)，延迟 16000ms
```

**解决方法**：
- 检查网络连接
- 重启后端服务
- 刷新页面
