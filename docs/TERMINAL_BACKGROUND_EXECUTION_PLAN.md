# JetBrains Terminal MCP 后台执行功能实现计划

## 一、背景与目标

### 1.1 功能需求
当 Terminal MCP 命令执行超过 5 秒时：
1. 显示 "Ctrl+B" 后台运行提示
2. 用户可以点击将特定终端命令移到后台
3. 用户可以 Ctrl+B 将所有可后台的任务移到后台
4. 移到后台后，命令继续运行，但立即返回 "正在后台执行" 响应
5. AI 可以后续通过 `TerminalRead` 读取命令结果

### 1.2 现有架构分析

**终端 MCP 现状**:
- `TerminalTool` 支持 `wait=true/false` 参数
- `wait=false` 时立即返回 "Command sent. Use TerminalRead to check output."
- `wait=true` 时阻塞等待命令完成
- `TerminalSession` 有 `isBackground` 字段但未使用

**前端后台提示现状**:
- `CompactToolCard.vue` 有 `supportsBackground` 属性
- 5 秒后显示 "Ctrl+B to background" 提示
- 但目前只用于 CLI 的原生 Bash 工具

**会话绑定 API 模式** (参考 batchRollback):
- 使用 RSocket 流式传输
- 前端通过 `jetbrainsRSocket` 调用
- 后端通过 `JetBrainsRSocketHandler` 处理

---

## 二、技术方案（RSocket 模式）

### 2.1 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                           前端                                   │
├─────────────────────────────────────────────────────────────────┤
│  TerminalToolDisplay.vue                                        │
│    ├─ 显示 "Ctrl+B to background" 提示                          │
│    └─ 点击触发 jetbrainsRSocket.terminalBackground()             │
│                                                                  │
│  MessageArea.vue / 全局快捷键                                    │
│    └─ Ctrl+B 触发批量后台 API                                    │
├─────────────────────────────────────────────────────────────────┤
│                      RSocket (流式响应)                          │
│                  jetbrains.terminalBackground                    │
├─────────────────────────────────────────────────────────────────┤
│                           后端                                   │
├─────────────────────────────────────────────────────────────────┤
│  JetBrainsRSocketHandler                                         │
│    └─ handleTerminalBackground(): Flow<Payload>                  │
│                                                                  │
│  TerminalMcpServerImpl                                           │
│    └─ 管理后台任务状态                                            │
│                                                                  │
│  TerminalSessionManager                                          │
│    └─ markAsBackground(sessionId, toolUseId)                     │
│    └─ getBackgroundableTasks()                                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Protobuf 消息定义

已添加到 `ai-agent-proto/src/main/proto/jetbrains_api.proto`:

```protobuf
// ========== Terminal 后台执行 ==========

// 单个终端后台项
message JetBrainsTerminalBackgroundItem {
  string session_id = 1;             // 终端会话 ID
  string tool_use_id = 2;            // MCP 工具调用 ID
}

// 终端后台请求（单个或批量）
message JetBrainsTerminalBackgroundRequest {
  repeated JetBrainsTerminalBackgroundItem items = 1;
}

// 终端后台状态枚举
enum TerminalBackgroundStatus {
  TERMINAL_BG_STARTED = 0;           // 开始后台化
  TERMINAL_BG_SUCCESS = 1;           // 后台化成功
  TERMINAL_BG_FAILED = 2;            // 后台化失败
}

// 终端后台流式事件
message JetBrainsTerminalBackgroundEvent {
  string session_id = 1;             // 终端会话 ID
  string tool_use_id = 2;            // MCP 工具调用 ID
  TerminalBackgroundStatus status = 3;
  optional string error = 4;
}

// ========== 获取可后台的终端任务 ==========

message JetBrainsGetBackgroundableTerminalsRequest {
  optional string ai_session_id = 1;
}

message JetBrainsBackgroundableTerminal {
  string session_id = 1;
  string tool_use_id = 2;
  string command = 3;
  int64 start_time = 4;
  int64 elapsed_ms = 5;
}

message JetBrainsGetBackgroundableTerminalsResponse {
  bool success = 1;
  repeated JetBrainsBackgroundableTerminal terminals = 2;
  optional string error = 3;
}
```

### 2.3 RSocket API 设计

| 路由 | 模式 | 请求 | 响应 | 说明 |
|------|------|------|------|------|
| `jetbrains.terminalBackground` | Stream | `TerminalBackgroundRequest` | `Flow<TerminalBackgroundEvent>` | 批量后台，流式返回 |
| `jetbrains.getBackgroundableTerminals` | Request-Response | `GetBackgroundableTerminalsRequest` | `GetBackgroundableTerminalsResponse` | 获取可后台任务列表 |

### 2.4 MCP 工具返回格式

当命令移到后台时，MCP 返回：
```json
{
  "success": true,
  "session_id": "term-123",
  "session_name": "Default Terminal",
  "running_in_background": true,
  "message": "Command is running in background. Use TerminalRead to check output when ready."
}
```

---

## 三、实现步骤

### Phase 1: 后端实现 (4-5 个文件)

#### 1.1 生成 Protobuf 代码
```bash
./gradlew :ai-agent-proto:generateProto
```

#### 1.2 扩展 TerminalSessionManager
```kotlin
// jetbrains-plugin/.../TerminalSessionManager.kt

// 新增: 后台任务追踪
data class TerminalBackgroundTask(
    val sessionId: String,
    val toolUseId: String,
    val command: String,
    val startTime: Long,
    var backgroundTime: Long? = null,
    var isBackground: Boolean = false
)

private val runningTasks = ConcurrentHashMap<String, TerminalBackgroundTask>()

/**
 * 记录任务开始执行（MCP 工具调用时）
 */
fun recordTaskStart(sessionId: String, toolUseId: String, command: String)

/**
 * 将任务标记为后台执行
 */
fun markAsBackground(sessionId: String, toolUseId: String): Boolean

/**
 * 获取可后台化的任务列表（运行超过阈值且未后台化的）
 */
fun getBackgroundableTasks(thresholdMs: Long = 5000): List<TerminalBackgroundTask>
```

#### 1.3 添加 RSocket 处理器
```kotlin
// jetbrains-plugin/.../JetBrainsRSocketHandler.kt

"jetbrains.terminalBackground" -> handleTerminalBackground(dataBytes)
"jetbrains.getBackgroundableTerminals" -> handleGetBackgroundableTerminals(dataBytes)

private fun handleTerminalBackground(dataBytes: ByteArray): Flow<Payload> = flow {
    val req = JetBrainsTerminalBackgroundRequest.parseFrom(dataBytes)
    
    for (item in req.itemsList) {
        // 发送"开始"事件
        emit(buildBackgroundEvent(item.sessionId, item.toolUseId, TERMINAL_BG_STARTED, null))
        
        try {
            val success = terminalSessionManager.markAsBackground(item.sessionId, item.toolUseId)
            if (success) {
                emit(buildBackgroundEvent(..., TERMINAL_BG_SUCCESS, null))
            } else {
                emit(buildBackgroundEvent(..., TERMINAL_BG_FAILED, "Task not found"))
            }
        } catch (e: Exception) {
            emit(buildBackgroundEvent(..., TERMINAL_BG_FAILED, e.message))
        }
    }
}
```

#### 1.4 修改 TerminalTool
```kotlin
// 执行命令时记录任务
sessionManager.recordTaskStart(session.id, toolUseId, command)

// 如果任务被后台化，返回后台响应
if (sessionManager.isBackground(toolUseId)) {
    return TerminalResultFormatter.formatBackgroundResult(session.id, session.name)
}
```

### Phase 2: 前端实现 (4-5 个文件)

#### 2.1 添加 RSocket 方法
```typescript
// frontend/src/services/ideaRSocket.ts

/**
 * 批量后台终端任务（流式返回结果）
 */
terminalBackground(
  items: TerminalBackgroundItem[],
  onEvent: (event: TerminalBackgroundEvent) => void,
  onComplete?: () => void,
  onError?: (error: Error) => void
): () => void {
  const data = encodeTerminalBackgroundRequest(items)
  
  return this.client.requestStream(
    'jetbrains.terminalBackground',
    data,
    {
      onNext: (responseData) => {
        const event = decodeTerminalBackgroundEvent(responseData)
        onEvent(event)
      },
      onComplete: () => onComplete?.(),
      onError: (error) => onError?.(error)
    }
  )
}

/**
 * 获取可后台的终端任务
 */
async getBackgroundableTerminals(): Promise<BackgroundableTerminal[]>
```

#### 2.2 创建 TerminalToolDisplay.vue
```vue
<template>
  <CompactToolCard
    :display-info="displayInfo"
    :supports-background="canBackground"
    @click="expanded = !expanded"
  >
    <template #header-actions>
      <button 
        v-if="showBackgroundButton"
        class="background-btn"
        @click.stop="handleBackground"
      >
        后台运行
      </button>
    </template>
    <template #details>
      <!-- Terminal 输出内容 -->
    </template>
  </CompactToolCard>
</template>
```

#### 2.3 添加全局 Ctrl+B 处理
```typescript
// frontend/src/composables/useTerminalBackground.ts

export function useTerminalBackground() {
  const handleCtrlB = async () => {
    // 1. 获取可后台的终端任务
    const terminals = await jetbrainsRSocket.getBackgroundableTerminals()
    
    // 2. 批量后台
    if (terminals.length > 0) {
      const items = terminals.map(t => ({
        sessionId: t.sessionId,
        toolUseId: t.toolUseId
      }))
      
      jetbrainsRSocket.terminalBackground(
        items,
        (event) => {
          // 更新 UI 状态
          updateTaskStatus(event.toolUseId, event.status)
        },
        () => console.log('All tasks backgrounded'),
        (error) => console.error('Background failed:', error)
      )
    }
    
    // 3. 同时后台 CLI 任务（已有的 SDK 功能）
    await sdkClient.runAllInBackground()
  }
  
  // 注册全局快捷键
  onMounted(() => {
    document.addEventListener('keydown', (e) => {
      if (e.ctrlKey && e.key === 'b') {
        e.preventDefault()
        handleCtrlB()
      }
    })
  })
}
```

---

## 四、文件修改清单

### Protobuf (已完成)
| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `jetbrains_api.proto` | ✅ 已修改 | 添加 Terminal 后台消息定义 |

### 后端 (Kotlin)
| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `TerminalSessionManager.kt` | 修改 | 添加后台任务追踪 |
| `TerminalTool.kt` | 修改 | 记录任务开始、返回后台响应 |
| `TerminalResultFormatter.kt` | 修改 | 添加 formatBackgroundResult |
| `JetBrainsRSocketHandler.kt` | 修改 | 添加 terminalBackground 路由 |

### 前端 (Vue/TypeScript)
| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `ideaRSocket.ts` | 修改 | 添加 terminalBackground API |
| `TerminalToolDisplay.vue` | 新建 | Terminal MCP 工具显示组件 |
| `McpToolDisplay.vue` | 修改 | 添加 Terminal 工具类型分发 |
| `useTerminalBackground.ts` | 新建 | Ctrl+B 快捷键处理 |

---

## 五、实现优先级

### P0 (核心功能)
1. ✅ Protobuf 消息定义
2. ⏳ 生成 Protobuf 代码
3. ⬜ TerminalSessionManager 后台任务追踪
4. ⬜ JetBrainsRSocketHandler 后台路由
5. ⬜ 前端 jetbrainsRSocket.terminalBackground()

### P1 (用户体验)
1. ⬜ TerminalToolDisplay.vue 显示组件
2. ⬜ Ctrl+B 全局快捷键
3. ⬜ 前端状态管理与 UI 更新

### P2 (增强功能)
1. ⬜ 后台任务完成通知 (ServerCallRequest 推送)
2. ⬜ 后台任务列表面板
3. ⬜ 自动后台（可配置超时）

---

## 六、测试计划

### 单元测试
- [ ] TerminalSessionManager.markAsBackground()
- [ ] JetBrainsRSocketHandler.handleTerminalBackground()
- [ ] Protobuf 编解码

### 集成测试
- [ ] 前端 → RSocket → 后端 → 流式响应 → 前端更新
- [ ] Ctrl+B 批量后台
- [ ] TerminalRead 读取后台任务输出

### 手动测试场景
1. 执行长时间命令 (如 `npm install`)
2. 5 秒后显示后台提示
3. 点击后台 → 流式返回 STARTED/SUCCESS
4. 使用 TerminalRead 检查输出
5. Ctrl+B 批量后台多个任务

---

## 七、与回滚功能对比

| 特性 | 回滚功能 | Terminal 后台 |
|------|---------|--------------|
| **Protobuf** | BatchRollbackRequest/Event | TerminalBackgroundRequest/Event |
| **路由** | `jetbrains.batchRollback` | `jetbrains.terminalBackground` |
| **状态** | STARTED/SUCCESS/FAILED | STARTED/SUCCESS/FAILED |
| **前端 API** | `batchRollback()` | `terminalBackground()` |
| **返回值** | 取消函数 `() => void` | 取消函数 `() => void` |
