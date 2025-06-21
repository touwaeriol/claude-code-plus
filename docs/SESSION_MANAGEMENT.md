# 会话管理机制

## 概述

Claude Code Plus 插件现在基于 Claude CLI 的原生会话管理机制，不再需要插件自己记录会话历史。

## 工作原理

### 1. 会话存储位置

Claude CLI 将会话存储在：
```
~/.claude/projects/{projectDirName}/{sessionId}.jsonl
```

其中：
- `projectDirName` 是项目路径将 `/` 替换为 `-` 后的结果
  - 例如：`/home/user/project` → `-home-user-project`
- `sessionId` 是会话的唯一标识符（UUID）
- 每行是一个 JSON 对象，记录一条消息

### 2. 会话管理流程

```
插件启动
    ↓
查找项目的会话目录
    ↓
获取最新的会话ID (最近修改的 .jsonl 文件)
    ↓
读取会话历史并显示
    ↓
启动文件监听器
    ↓
用户发送消息
    ↓
使用 --resume {sessionId} 恢复会话
    ↓
Claude CLI 自动记录到会话文件
    ↓
文件监听器检测到变化
    ↓
自动更新UI显示
```

## 核心组件

### ClaudeSessionManager

负责：
- 计算项目哈希值
- 查找会话文件
- 解析 JSONL 格式
- 监听文件变化

关键方法：
```kotlin
// 获取最新会话ID
fun getLatestSessionId(projectPath: String): String?

// 读取会话历史
fun readSessionHistory(projectPath: String, sessionId: String): List<SessionMessage>

// 监听会话文件变化
fun watchSessionFile(projectPath: String, sessionId: String): Flow<SessionMessage>
```

### ClaudeCliWrapper

支持 `resume` 参数：
```kotlin
QueryOptions(
    resume = sessionId  // 恢复特定会话
)
```

### ClaudeCodePlusToolWindowFactory

整合会话管理：
- 启动时加载会话历史
- 监听会话文件变化
- 自动更新UI

## 优势

1. **无缝集成**：利用 Claude CLI 的原生会话管理
2. **实时同步**：通过文件监听实现实时更新
3. **持久化**：会话自动保存，重启后可恢复
4. **跨工具兼容**：与命令行 claude 工具共享会话

## 会话文件格式

每行是一个 JSON 对象，实际格式：
```json
{
  "parentUuid": null,
  "sessionId": "298bce1e-832a-49fe-ab69-f03d8993bbab",
  "type": "user",
  "message": {
    "role": "user",
    "content": "你好"
  },
  "timestamp": "2025-06-21T06:47:15.871Z"
}
{
  "parentUuid": "21c5ee69-63e8-4574-a8bc-c306d2afd522",
  "type": "assistant",
  "message": {
    "role": "assistant",
    "model": "claude-opus-4-20250514",
    "content": [
      {
        "type": "text",
        "text": "你好！有什么可以帮助你的吗？"
      }
    ]
  },
  "timestamp": "2025-06-21T06:47:19.552Z"
}
```

## 注意事项

1. **性能优化**
   - 使用 RandomAccessFile 增量读取新内容
   - 维护缓存避免重复解析
   - 500ms 轮询间隔平衡性能和响应速度

2. **上下文压缩**
   - Claude CLI 会在上下文过长时压缩会话
   - 通过检测文件大小减小来识别压缩
   - 压缩后自动重新加载整个文件

3. **资源管理**
   - 使用 `cleanup()` 方法释放资源
   - 切换会话时停止旧的监听器
   - 协程取消时自动清理