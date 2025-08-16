# ClaudeCliWrapper 优化方案

## 一、优化背景

基于对 Claudia 项目的深入分析，我们发现了一个更简洁可靠的会话管理策略：**二元会话策略**。这种策略只使用两种命令模式，避免了复杂的文件监听和状态追踪。

## 二、核心改进

### 2.1 新增 API 方法

```kotlin
// 1. 启动新会话（对应 Claudia 的 executeClaudeCode）
suspend fun startNewSession(prompt: String, options: QueryOptions): QueryResult

// 2. 恢复会话（对应 Claudia 的 resumeClaudeCode）  
suspend fun resumeSession(sessionId: String, prompt: String, options: QueryOptions): QueryResult

// 3. 保留原有方法用于向后兼容（标记为 @Deprecated）
@Deprecated
suspend fun query(prompt: String, options: QueryOptions): QueryResult
```

### 2.2 会话类型枚举

```kotlin
enum class SessionType {
    /** 新会话 - 不使用 --resume 参数 */
    NEW,
    /** 恢复会话 - 使用 --resume sessionId 参数 */
    RESUME
}
```

### 2.3 实现细节

- `startNewSession`: 强制清除 `options.resume` 参数，确保创建新会话
- `resumeSession`: 强制设置 `options.resume = sessionId`，确保恢复指定会话
- 内部统一使用 `executeQuery` 方法，通过 `SessionType` 参数区分处理逻辑

## 三、使用方式改进

### 3.1 在 ChatViewModel 中使用

```kotlin
class ChatViewModel {
    private var isFirstMessage = true
    private var currentSessionId: String? = null
    
    suspend fun sendMessage(message: String) {
        val result = if (isFirstMessage) {
            // 首次对话：创建新会话
            isFirstMessage = false
            wrapper.startNewSession(message, options)
        } else {
            // 后续对话：恢复会话
            currentSessionId?.let { sessionId ->
                wrapper.resumeSession(sessionId, message, options)
            } ?: wrapper.startNewSession(message, options)
        }
        
        // 保存会话 ID
        result.sessionId?.let { 
            currentSessionId = it 
        }
    }
}
```

### 3.2 在 SessionManager 中管理多会话

```kotlin
class SessionManager {
    private val sessions = mutableMapOf<String, SessionObject>()
    
    suspend fun sendMessageToSession(sessionId: String, message: String) {
        val session = sessions[sessionId] ?: return
        
        val result = if (session.isFirstMessage) {
            session.isFirstMessage = false
            wrapper.startNewSession(message, session.options)
        } else {
            wrapper.resumeSession(sessionId, message, session.options)
        }
        
        // 更新会话信息
        session.updateFromResult(result)
    }
}
```

## 四、优势对比

| 特性 | 旧方案（文件监听） | 新方案（二元策略） |
|-----|-----------------|----------------|
| 复杂度 | 高（需要监听文件变化） | 低（简单的 API 调用） |
| 可靠性 | 中（依赖文件系统事件） | 高（直接控制参数） |
| 调试难度 | 困难（异步事件） | 简单（同步调用） |
| 性能 | 有额外开销（文件监听） | 无额外开销 |
| 代码量 | 多（事件处理逻辑） | 少（直接调用） |

## 五、迁移指南

### 5.1 最小改动迁移

如果想保持现有代码基本不变，只需：

1. 保留现有的 `query` 方法调用
2. 方法内部会自动根据 `options.resume` 判断使用哪种策略
3. 逐步迁移到新 API

### 5.2 推荐迁移方式

1. **第一步**：更新 `ClaudeCliWrapper`（已完成）
2. **第二步**：在 `SessionObject` 中添加 `isFirstMessage` 标志
3. **第三步**：修改消息发送逻辑，使用新的 API
4. **第四步**：移除文件监听相关代码

### 5.3 注意事项

1. **会话 ID 管理**：从 SDK 返回的消息中提取 sessionId
2. **状态同步**：确保 `isFirstMessage` 标志正确更新
3. **错误处理**：会话恢复失败时降级为新会话

## 六、实施效果

采用这个优化方案后：

1. **代码更简洁**：移除复杂的文件监听逻辑
2. **更可靠**：不依赖文件系统事件，避免时序问题
3. **更易调试**：清晰的调用流程，容易追踪问题
4. **性能更好**：减少 I/O 操作，降低资源消耗

## 七、后续计划

1. 更新相关文档，说明新的使用方式
2. 在示例代码中展示最佳实践
3. 考虑添加会话历史加载功能（类似 Claudia 的 loadSessionHistory）
4. 优化错误处理和重试机制