# Claude Agent SDK Client 使用指南

## 架构说明

```
ClaudeAgentSdkClient           // 主客户端（单例）
    ├─ connect()               // 建立 WebSocket 连接
    ├─ createSession()         // 创建会话
    ├─ initSession()           // 初始化会话
    ├─ query()                 // 发送查询
    ├─ interrupt()             // 中断
    ├─ setModel()              // 设置模型
    ├─ getHistory()            // 获取历史
    ├─ deleteSession()         // 删除会话
    └─ disconnect()            // 断开连接

ClaudeAgentSdkSession          // 会话实例
    ├─ connect()               // 初始化 SDK
    ├─ query()                 // 发送查询
    ├─ interrupt()             // 中断
    ├─ setModel()              // 设置模型
    ├─ getHistory()            // 获取历史
    └─ delete()                // 删除会话
```

---

## Vue 3 使用示例

### 1. 基本使用

```vue
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { claudeAgentSdkClient } from '@/services/ClaudeAgentSdkClient'
import { ClaudeAgentSdkSession } from '@/services/ClaudeAgentSdkSession'

const session = ref<ClaudeAgentSdkSession | null>(null)
const messages = ref<any[]>([])
const isConnected = ref(false)
const isLoading = ref(false)

onMounted(async () => {
  try {
    // 1. 连接到服务端
    await claudeAgentSdkClient.connect()
    isConnected.value = true
    console.log('✅ 已连接到 Claude Agent SDK')

    // 2. 创建会话
    const sessionInfo = await claudeAgentSdkClient.createSession('我的对话', {
      model: 'claude-sonnet-4-5-20250929',
      permissionMode: 'bypassPermissions',
      maxTurns: 50,
      dangerouslySkipPermissions: true
    })

    session.value = new ClaudeAgentSdkSession(sessionInfo)
    console.log('✅ 会话已创建:', sessionInfo)

    // 3. 初始化会话（连接 SDK）
    await session.value.connect()
    console.log('✅ 会话已连接，可以开始对话')

  } catch (error) {
    console.error('❌ 初始化失败:', error)
  }
})

onUnmounted(() => {
  claudeAgentSdkClient.disconnect()
})

// 发送查询
const sendQuery = (text: string) => {
  if (!session.value) {
    console.error('会话未初始化')
    return
  }

  isLoading.value = true

  session.value.query(text, {
    // 流式数据回调
    onStream: (data) => {
      console.log('📨 收到流式数据:', data)

      if (data.type === 'assistant') {
        messages.value.push(data)
      }
    },

    // 完成回调
    onEnd: () => {
      console.log('✅ 查询完成')
      isLoading.value = false
    },

    // 错误回调
    onError: (error) => {
      console.error('❌ 查询失败:', error)
      isLoading.value = false
    }
  })
}

// 中断
const interrupt = async () => {
  if (session.value) {
    await session.value.interrupt()
    isLoading.value = false
  }
}

// 切换模型
const changeModel = async (model: string) => {
  if (session.value) {
    await session.value.setModel(model)
    console.log(`✅ 模型已切换到: ${model}`)
  }
}

// 查看历史
const viewHistory = async () => {
  if (session.value) {
    const history = await session.value.getHistory()
    console.log('📋 历史消息:', history)
  }
}

// 删除会话
const deleteSession = async () => {
  if (session.value) {
    await session.value.delete()
    session.value = null
  }
}
</script>

<template>
  <div class="chat-container">
    <div v-if="!isConnected" class="connecting">
      连接中...
    </div>

    <div v-else-if="!session" class="creating-session">
      创建会话中...
    </div>

    <div v-else class="chat">
      <div class="messages">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="message"
        >
          <div v-if="msg.type === 'assistant'">
            {{ msg.content }}
          </div>
        </div>
      </div>

      <div class="actions">
        <button @click="sendQuery('帮我写代码')" :disabled="isLoading">
          发送查询
        </button>
        <button @click="interrupt()" :disabled="!isLoading">
          中断
        </button>
        <button @click="changeModel('claude-opus-4')">
          切换模型
        </button>
        <button @click="viewHistory()">
          查看历史
        </button>
        <button @click="deleteSession()">
          删除会话
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-container {
  padding: 20px;
}

.connecting,
.creating-session {
  text-align: center;
  padding: 40px;
}

.messages {
  height: 400px;
  overflow-y: auto;
  border: 1px solid #ccc;
  padding: 10px;
  margin-bottom: 20px;
}

.message {
  padding: 10px;
  margin-bottom: 10px;
  background: #f5f5f5;
  border-radius: 8px;
}

.actions button {
  margin-right: 10px;
  padding: 8px 16px;
}
</style>
```

---

### 2. 多会话管理

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { claudeAgentSdkClient } from '@/services/ClaudeAgentSdkClient'
import { ClaudeAgentSdkSession } from '@/services/ClaudeAgentSdkSession'

const sessions = ref<ClaudeAgentSdkSession[]>([])
const currentSession = ref<ClaudeAgentSdkSession | null>(null)

// 创建新会话
const createNewSession = async () => {
  const sessionInfo = await claudeAgentSdkClient.createSession(`会话 ${sessions.value.length + 1}`)
  const session = new ClaudeAgentSdkSession(sessionInfo)
  await session.connect()

  sessions.value.push(session)
  currentSession.value = session
}

// 切换会话
const switchSession = (session: ClaudeAgentSdkSession) => {
  currentSession.value = session
}

// 删除会话
const deleteSession = async (session: ClaudeAgentSdkSession) => {
  await session.delete()
  sessions.value = sessions.value.filter(s => s.id !== session.id)

  if (currentSession.value?.id === session.id) {
    currentSession.value = sessions.value[0] || null
  }
}
</script>

<template>
  <div class="session-manager">
    <div class="session-list">
      <button @click="createNewSession">➕ 新建会话</button>

      <div
        v-for="session in sessions"
        :key="session.id"
        :class="['session-item', { active: currentSession?.id === session.id }]"
        @click="switchSession(session)"
      >
        {{ session.name }}
        <button @click.stop="deleteSession(session)">🗑️</button>
      </div>
    </div>

    <div class="session-chat">
      <div v-if="currentSession">
        <!-- 聊天界面 -->
      </div>
      <div v-else>
        请创建或选择一个会话
      </div>
    </div>
  </div>
</template>
```

---

### 3. 错误处理

```typescript
try {
  await claudeAgentSdkClient.connect()

  const session = new ClaudeAgentSdkSession(
    await claudeAgentSdkClient.createSession('测试')
  )

  await session.connect()

  session.query('你好', {
    onStream: (data) => console.log(data),
    onError: (error) => {
      // 查询级别的错误
      console.error('查询失败:', error)
    }
  })

} catch (error) {
  // 连接/创建/初始化级别的错误
  console.error('操作失败:', error)
}
```

---

## API 参考

### ClaudeAgentSdkClient

| 方法 | 参数 | 返回值 | 说明 |
|-----|------|--------|------|
| `connect()` | - | `Promise<void>` | 连接到服务端 |
| `createSession(name?, options?)` | name, options | `Promise<SessionInfo>` | 创建新会话 |
| `initSession(sessionId)` | sessionId | `Promise<void>` | 初始化会话 |
| `query(...)` | sessionId, message, callbacks | `string` | 发送查询（返回requestId） |
| `interrupt(sessionId)` | sessionId | `Promise<void>` | 中断执行 |
| `setModel(sessionId, model)` | sessionId, model | `Promise<void>` | 设置模型 |
| `getHistory(sessionId)` | sessionId | `Promise<any[]>` | 获取历史 |
| `deleteSession(sessionId)` | sessionId | `Promise<void>` | 删除会话 |
| `disconnect()` | - | `void` | 断开连接 |
| `connected()` | - | `boolean` | 检查连接状态 |

### ClaudeAgentSdkSession

| 方法 | 参数 | 返回值 | 说明 |
|-----|------|--------|------|
| `connect()` | - | `Promise<void>` | 初始化会话 |
| `query(message, callbacks)` | message, callbacks | `string` | 发送查询 |
| `interrupt()` | - | `Promise<void>` | 中断执行 |
| `setModel(model)` | model | `Promise<void>` | 设置模型 |
| `getHistory()` | - | `Promise<any[]>` | 获取历史 |
| `delete()` | - | `Promise<void>` | 删除会话 |
| `connected()` | - | `boolean` | 检查是否已初始化 |

---

## 完整工作流程

```
1. 连接服务端
   claudeAgentSdkClient.connect()

2. 创建会话
   const sessionInfo = await claudeAgentSdkClient.createSession(...)
   const session = new ClaudeAgentSdkSession(sessionInfo)

3. 初始化会话
   await session.connect()

4. 发送查询
   session.query("你好", { onStream, onEnd, onError })

5. 可选操作
   - await session.interrupt()
   - await session.setModel("...")
   - await session.getHistory()

6. 清理
   await session.delete()
   claudeAgentSdkClient.disconnect()
```

---

## 注意事项

1. **单例模式**：`claudeAgentSdkClient` 是单例，全局只需连接一次
2. **会话隔离**：每个 `ClaudeAgentSdkSession` 是独立的，可以同时多个
3. **必须初始化**：在使用 `query()` 前必须先调用 `connect()`
4. **错误处理**：所有异步方法都可能抛出异常，需要 try-catch
5. **资源清理**：组件卸载时记得 `disconnect()`

---

## 与旧版 WebSocket 客户端的对比

| 特性 | 旧版 WebSocketClient | 新版 ClaudeAgentSdkClient |
|-----|---------------------|--------------------------|
| **连接方式** | `/ws/sessions/{id}` | `/ws` (统一入口) |
| **会话创建** | HTTP API | RPC 消息 |
| **操作方式** | URL 参数 | RPC 方法调用 |
| **多会话** | 每个会话一个连接 | 单连接多会话 |
| **可扩展性** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **面向对象** | ⭐⭐ | ⭐⭐⭐⭐⭐ |
