# 混合架构实施总结

## 🎯 实施目标

将原有的 HTTP + SSE 架构升级为 **WebSocket + RESTful API** 混合架构，解决以下问题：

1. ✅ **资源泄漏**：SSE 无法检测客户端断开，导致 SDK 客户端（子进程）持续运行
2. ✅ **连接管理**：需要双向通信支持中断操作
3. ✅ **API 规范**：使用 RESTful 风格，清晰易维护
4. ✅ **会话隔离**：每个会话独立的连接和上下文

## 📊 完成的任务清单

### 后端改动（4个文件）

| 文件 | 状态 | 描述 |
|------|------|------|
| ClaudeSessionManager.kt | ✅ | 客户端池管理，每个会话独立 SDK 客户端 |
| WebSocketHandler.kt | ✅ | WebSocket 路由处理，自动资源清理 |
| SessionActionHandler.kt | ✅ | 拆分公开方法，支持 RESTful API |
| HttpApiServer.kt | ✅ | 添加 WebSocket + RESTful 路由 |

### 前端改动（4个文件）

| 文件 | 状态 | 描述 |
|------|------|------|
| websocketClient.ts | ✅ | WebSocket 客户端，支持连接池和自动重连 |
| apiClient.ts | ✅ | 重构为 RESTful API 客户端 |
| claudeService.ts | ✅ | 改用 WebSocket 发送消息 |
| sessionStore.ts | ✅ | 添加 WebSocket 连接管理和会话切换逻辑 |

### 文档

| 文件 | 状态 | 描述 |
|------|------|------|
| INTEGRATION_GUIDE.md | ✅ | 前端集成指南 |
| IMPLEMENTATION_SUMMARY.md | ✅ | 实施总结（本文档） |

## 🏗️ 架构设计

### 混合架构

```
┌─────────────────────────────────────────────┐
│           Vue Frontend (Browser)            │
├──────────────────┬──────────────────────────┤
│  sessionStore    │  Messages: WebSocket     │
│  (Pinia)         │  Sessions: HTTP REST     │
└──────────────────┴──────────────────────────┘
         │                    │
         │ WebSocket          │ HTTP RESTful
         │ /ws/sessions/{id}  │ /api/sessions/*
         ↓                    ↓
┌─────────────────────────────────────────────┐
│       Ktor Backend Server (Random Port)     │
├──────────────────┬──────────────────────────┤
│ WebSocketHandler │  RESTful Routes          │
│ (Kotlin)         │  (Kotlin)                │
└──────────────────┴──────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────┐
│        ClaudeSessionManager                 │
│  - 客户端池管理                              │
│  - 资源自动清理                              │
└─────────────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────────────┐
│    ClaudeCodeSdkClient (per session)        │
│  → Claude CLI Process → Claude API          │
└─────────────────────────────────────────────┘
```

### WebSocket 用于会话交互

**路由**: `/ws/sessions/{sessionId}`

**客户端 → 服务端**:
```json
// 发送消息
{ "type": "query", "data": { "message": "Hello Claude" } }

// 中断
{ "type": "interrupt" }
```

**服务端 → 客户端**（流式）:
```json
// 助手消息
{ "type": "assistant", "message": { "content": [...], "isStreaming": true } }

// 结束标志
{ "type": "result", "message": { "is_error": false, "tokenUsage": {...} } }
```

### HTTP RESTful 用于会话管理

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | /api/sessions | 列出所有会话 |
| POST | /api/sessions | 创建新会话 |
| GET | /api/sessions/{id}/history | 获取历史消息 |
| DELETE | /api/sessions/{id} | 删除会话 |
| PATCH | /api/sessions/{id} | 重命名会话 |
| GET | /api/config | 获取配置 |
| PUT | /api/config | 保存配置 |
| GET | /api/theme | 获取 IDE 主题 |

## 🔑 关键特性

### 1. 自动资源管理

```kotlin
// WebSocketHandler.kt
finally {
    // ✅ 连接关闭时自动清理
    ClaudeSessionManager.closeSession(sessionId)
    // 清理内容：
    // - 断开 SDK 客户端
    // - 终止 Claude CLI 子进程
    // - 取消协程作用域
}
```

### 2. 客户端池管理

```kotlin
// ClaudeSessionManager.kt
private val sessionClients = ConcurrentHashMap<String, ClaudeCodeSdkClient>()

// 每个 sessionId 一个独立的 SDK 客户端（子进程）
suspend fun getOrCreateClient(sessionId: String, project: Project): ClaudeCodeSdkClient {
    return sessionClients.getOrPut(sessionId) {
        val client = ClaudeCodeSdkClient(options)
        client.connect()
        client
    }
}
```

### 3. 前端连接管理

```typescript
// sessionStore.ts
async function switchSession(sessionId: string) {
    // 1. 断开旧会话的 WebSocket 连接
    if (currentSessionId.value) {
        claudeService.disconnect(currentSessionId.value)
    }

    // 2. 加载新会话的历史消息
    const history = await loadSessionHistory(sessionId)

    // 3. 建立新会话的 WebSocket 连接
    await claudeService.connect(sessionId, handleWebSocketMessage)
}
```

### 4. 自动重连机制

```typescript
// websocketClient.ts
private scheduleReconnect(): void {
    this.reconnectAttempts++
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1)

    setTimeout(() => {
        if (!this.isManualClose) {
            this.connect()  // 指数退避重连
        }
    }, delay)
}
```

### 5. 占位符模式

```typescript
// 发送消息时立即显示占位符
async function handleSendMessage(text: string) {
    // 1. 显示用户消息
    sessionStore.addMessage(sessionId, {
        type: 'user',
        content: text
    })

    // 2. 添加助手占位符
    sessionStore.addMessage(sessionId, {
        type: 'assistant',
        content: [],
        isStreaming: true  // ← 流式标志
    })

    // 3. 发送消息
    claudeService.sendMessage(sessionId, text)

    // 4. WebSocket 响应会自动更新占位符
}
```

## 🔄 消息流程

### 完整流程图

```
用户输入 "Hello Claude"
    │
    ├─ 1. handleSendMessage()
    │   └─ 立即添加用户消息到 UI
    │
    ├─ 2. 添加助手占位符
    │   └─ { type: 'assistant', content: [], isStreaming: true }
    │
    ├─ 3. claudeService.sendMessage(sessionId, text)
    │   └─ websocketClient.send({ type: 'query', data: { message: text } })
    │       └─ WebSocket 发送 JSON
    │
    ├─ 4. 后端 WebSocketHandler 接收
    │   └─ handleQuery(sessionId, request)
    │       └─ ClaudeSessionManager.sendMessage(sessionId, message, project)
    │           └─ client.query(message, sessionId)
    │
    ├─ 5. SDK 处理
    │   └─ Claude CLI 子进程
    │       └─ Claude API
    │           └─ 流式返回响应
    │
    ├─ 6. 后端推送响应
    │   └─ client.receiveResponse().collect { sdkMessage ->
    │       └─ convertSdkMessage(sdkMessage)
    │           └─ send(WebSocketResponse)
    │
    ├─ 7. 前端 WebSocket 接收
    │   └─ ws.onmessage = (event) => {
    │       └─ response = JSON.parse(event.data)
    │           └─ messageHandlers.forEach(handler => handler(response))
    │
    └─ 8. 前端更新 UI
        └─ handleWebSocketMessage(sessionId, response)
            └─ sessionStore.addMessage(sessionId, response.message)
                └─ UI 自动更新（Vue 响应式）
```

## 📝 使用示例

### 发送消息

```typescript
import { useSessionStore } from '@/stores/sessionStore'
import { claudeService } from '@/services/claudeService'

const sessionStore = useSessionStore()
const currentSessionId = computed(() => sessionStore.currentSessionId)

async function handleSendMessage(text: string) {
    if (!currentSessionId.value) return

    const sessionId = currentSessionId.value

    // 1. 显示用户消息
    sessionStore.addMessage(sessionId, {
        type: 'user',
        content: text
    })

    // 2. 添加占位符
    sessionStore.addMessage(sessionId, {
        id: `assistant-${Date.now()}`,
        type: 'assistant',
        content: [],
        isStreaming: true
    })

    // 3. 发送消息（WebSocket）
    claudeService.sendMessage(sessionId, text)
}
```

### 会话管理

```typescript
// 加载会话列表
await sessionStore.loadSessions()
// → GET /api/sessions

// 创建新会话
const session = await sessionStore.createSession('新会话')
// → POST /api/sessions
// → 自动建立 WebSocket 连接

// 切换会话
await sessionStore.switchSession(sessionId)
// → 断开旧连接
// → GET /api/sessions/{id}/history
// → 建立新 WebSocket 连接

// 删除会话
await sessionStore.deleteSession(sessionId)
// → DELETE /api/sessions/{id}
// → 断开 WebSocket 连接
```

## 🧪 测试步骤

### 1. 启动后端

```bash
# 在 IntelliJ IDEA 中运行插件
# 或者使用 Gradle 任务
./gradlew :jetbrains-plugin:runIde
```

**预期输出**:
```
🚀 Ktor server started at: http://127.0.0.1:{随机端口}
```

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

**预期输出**:
```
✅ API Base URL detected: http://localhost:{端口}
```

### 3. 测试 WebSocket 连接

打开浏览器控制台，应该看到：

```
📋 加载会话列表...
✅ 加载了 1 个会话
🔄 切换到会话: session-123
🔌 连接到会话: session-123
🔌 WebSocket 连接已建立: session-123
✅ 已切换到会话: session-123
```

### 4. 测试消息发送

输入 "Hello Claude" 并发送，控制台应该显示：

```
📤 发送消息到会话 session-123: Hello Claude
📨 收到会话 session-123 的消息: assistant
💬 会话 session-123 添加消息，当前共 3 条
📨 收到会话 session-123 的消息: result
✅ 会话结束
```

### 5. 测试会话切换

切换到另一个会话，控制台应该显示：

```
🔄 切换到会话: session-456
🔌 断开会话: session-123
📡 加载历史消息: session-456
🔌 连接到会话: session-456
✅ 已切换到会话: session-456
```

### 6. 测试资源清理

关闭浏览器标签页，后端控制台应该显示：

```
🔌 WebSocket 连接已关闭
🧹 WebSocket 连接关闭，清理会话资源: session-123
🚪 关闭会话 session-123
✅ 会话 session-123 的 SDK 客户端已断开
✅ 会话 session-123 的协程作用域已取消
✅ 会话 session-123 已完全关闭
```

## 🎉 实施成果

### 解决的问题

1. ✅ **资源泄漏**：WebSocket 断开时自动清理 SDK 客户端和子进程
2. ✅ **双向通信**：支持客户端主动中断操作
3. ✅ **API 规范**：RESTful 风格，清晰易维护
4. ✅ **会话隔离**：每个会话独立的连接和上下文
5. ✅ **自动重连**：网络中断后自动恢复连接
6. ✅ **占位符模式**：立即反馈，提升用户体验

### 性能优化

- **连接复用**：同一会话复用 WebSocket 连接
- **消息队列**：连接建立前的消息缓存
- **增量更新**：流式响应，逐步更新 UI
- **资源清理**：自动释放不再使用的连接

### 可维护性

- **清晰的分层**：UI → Store → Service → API
- **类型安全**：TypeScript + Kotlin 强类型
- **错误处理**：完整的异常捕获和重试机制
- **日志记录**：详细的操作日志，便于调试

## 📚 相关文档

- **集成指南**: [frontend/INTEGRATION_GUIDE.md](frontend/INTEGRATION_GUIDE.md)
- **项目文档**: [CLAUDE.md](CLAUDE.md)
- **Jewel 组件**: [.claude/rules/jewel-components.md](.claude/rules/jewel-components.md)

## 🔮 后续优化建议

1. **消息持久化**：将消息保存到数据库（可选）
2. **离线支持**：IndexedDB 缓存历史消息
3. **批量操作**：支持批量删除会话
4. **搜索功能**：全文搜索历史消息
5. **导出功能**：导出会话历史为 Markdown
6. **主题同步**：实时同步 IDE 主题变化
7. **性能监控**：添加 WebSocket 连接健康检查

## 🐛 故障排查

### WebSocket 连接失败

**症状**: `❌ WebSocket 错误: sessionId=xxx`

**解决方法**:
1. 检查后端服务是否运行
2. 检查端口是否正确
3. 检查防火墙设置
4. 查看后端日志

### 消息未显示

**症状**: `⚠️ 忽略非当前会话的消息`

**解决方法**:
1. 确认 sessionId 匹配
2. 检查 handleWebSocketMessage() 逻辑
3. 查看浏览器控制台日志

### 自动重连失败

**症状**: `🔄 尝试重连 (5/5)，延迟 16000ms`

**解决方法**:
1. 检查网络连接
2. 重启后端服务
3. 刷新浏览器页面
4. 清除浏览器缓存

## 📞 支持

如有问题，请查看：
- **日志文件**：浏览器控制台 + IDEA 日志
- **GitHub Issues**：提交问题报告
- **文档**：查看集成指南和项目文档

---

**实施完成时间**: 2025-01-10
**实施人**: Claude Code
**架构设计**: 基于 Compose UI 实现，参考官方 Claude Agent SDK
