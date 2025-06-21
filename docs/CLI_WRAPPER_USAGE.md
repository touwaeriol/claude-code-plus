# ClaudeCliWrapper 功能使用情况

## 已实现的功能

### 1. 基本查询功能 ✅
- 使用 `cliWrapper.query()` 发送消息
- 处理流式响应
- 错误处理

### 2. 模型选择 ✅
```kotlin
val options = ClaudeCliWrapper.QueryOptions(
    model = when (model) {
        "sonnet" -> "claude-4-sonnet-20250514"
        "opus" -> "claude-opus-4-20250514"
        else -> "claude-opus-4-20250514"
    }
)
```

### 3. MCP 服务器配置 ✅
```kotlin
mcpServers = mcpConfig,
```

### 4. 工作目录设置 ✅
```kotlin
cwd = project.basePath
```

### 5. 会话继续功能 ✅
```kotlin
continueConversation = !shouldStartNewSession
```
- 默认延续上一个会话（`shouldStartNewSession = false`）
- 点击 "+ New Chat" 开始新会话
- 支持多轮对话上下文记忆

## 未实现的功能

### 1. 多轮对话限制 ❌
- `maxTurns` - 未使用（限制对话轮数）
- `resume` - 未使用（恢复特定会话）

### 2. 系统提示词自定义 ❌
- `customSystemPrompt` - 未使用
- `appendSystemPrompt` - 未使用

### 3. 工具权限控制 ❌
- `permissionMode` - 使用默认值 "default"
- `allowedTools` - 未使用
- `disallowedTools` - 未使用

### 4. 备用模型 ❌
- `fallbackModel` - 未使用

## 实现建议

### 1. 添加会话继续功能

在 ClaudeCodePlusToolWindowFactory 中添加：
```kotlin
private var isNewSession = true

val options = ClaudeCliWrapper.QueryOptions(
    model = selectedModel,
    mcpServers = mcpConfig,
    cwd = project.basePath,
    continueConversation = !isNewSession  // 使用会话继续
)
```

### 2. 添加系统提示词设置

在设置界面中添加系统提示词配置：
```kotlin
val customPrompt = settings.customSystemPrompt
val options = ClaudeCliWrapper.QueryOptions(
    customSystemPrompt = customPrompt,
    // 或者
    appendSystemPrompt = "Always respond in Chinese"
)
```

### 3. 添加工具权限控制

根据用户需求限制工具使用：
```kotlin
val options = ClaudeCliWrapper.QueryOptions(
    permissionMode = "strict",  // 限制敏感操作
    allowedTools = listOf("Read", "Edit"),  // 只允许读写文件
    disallowedTools = listOf("Bash")  // 禁止执行命令
)
```

### 4. 添加多轮对话控制

```kotlin
val options = ClaudeCliWrapper.QueryOptions(
    maxTurns = 5,  // 限制最多5轮对话
)
```

## 当前实现位置

主要实现在 `ClaudeCodePlusToolWindowFactory.kt` 的 `sendToClaudeAPIWithModel` 方法中：
- 行号：917-1112
- 关键代码：957-965 行

## 优先级建议

1. **高优先级**：会话继续功能（提升用户体验）
2. **中优先级**：系统提示词自定义（个性化需求）
3. **低优先级**：工具权限控制（安全性考虑）