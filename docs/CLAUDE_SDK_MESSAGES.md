# Claude SDK 消息类型文档

## 概述

本文档详细说明了 Claude Code Plus 插件与 Node.js 服务之间的消息交互格式，以及 Claude SDK 的消息类型。

## 1. WebSocket 消息格式

### 1.1 客户端发送到服务器

#### 发送消息命令
```json
{
  "command": "message",
  "message": "用户输入的文本",
  "session_id": "会话ID（可选）",
  "new_session": false,
  "options": {
    "cwd": "/工作目录路径",
    "systemPrompt": "系统提示词",
    "maxTurns": 20,
    "allowedTools": ["read", "write", "bash"],
    "permissionMode": "auto",
    "maxThinkingTokens": 10000,
    "model": "Opus"
  }
}
```

#### 健康检查命令
```json
{
  "command": "health"
}
```

#### Ping 命令
```json
{
  "command": "ping"
}
```

### 1.2 服务器响应格式

#### 欢迎消息
```json
{
  "type": "welcome",
  "message": "Connected to Claude SDK Wrapper (WebSocket)",
  "initialized": true
}
```

#### 文本响应
```json
{
  "type": "text",
  "message_type": "assistant",
  "content": "Claude 的回复文本",
  "session_id": "会话ID"
}
```

#### 工具使用消息
```json
{
  "type": "tool_use",
  "message_type": "assistant",
  "content": "{\"type\":\"tool_use\",\"id\":\"toolu_xxx\",\"name\":\"read\",\"input\":{\"path\":\"/file.txt\"}}",
  "session_id": "会话ID"
}
```

#### 错误消息
```json
{
  "type": "error",
  "error": "错误描述"
}
```

#### 完成消息
```json
{
  "type": "done",
  "session_id": "会话ID"
}
```

#### 健康检查响应
```json
{
  "type": "health",
  "status": "ok",
  "initialized": true,
  "sdk_available": true,
  "active_sessions": 1
}
```

## 2. Claude SDK 消息类型

### 2.1 Claude SDK 返回的消息类型

#### Assistant 消息
```javascript
{
  "type": "assistant",
  "message": {
    "role": "assistant",
    "content": [
      {
        "type": "text",
        "text": "回复文本"
      },
      {
        "type": "tool_use",
        "id": "toolu_xxx",
        "name": "工具名称",
        "input": { /* 工具参数 */ }
      }
    ]
  }
}
```

#### User 消息（包含工具结果）
```javascript
{
  "type": "user",
  "message": {
    "role": "user",
    "content": [
      {
        "tool_use_id": "toolu_xxx",
        "type": "tool_result",
        "content": "工具执行结果"
      }
    ]
  }
}
```

#### System 消息
```javascript
{
  "type": "system",
  "subtype": "system_message_subtype"
}
```

#### Result 消息
```javascript
{
  "type": "result",
  "result": "操作结果",
  "total_cost_usd": 0.0123
}
```

### 2.2 消息流程

1. **用户发送消息** → WebSocket 客户端
2. **服务器接收并转发** → Claude SDK
3. **Claude SDK 处理**：
   - 可能返回 `assistant` 消息（包含文本或工具使用）
   - 如果使用工具，会返回 `user` 消息（包含工具结果）
   - 最后返回 `result` 消息（总结）
4. **服务器过滤和转发**：
   - `assistant` 消息中的文本 → 转换为 `type: "text"`
   - `assistant` 消息中的工具使用 → 转换为 `type: "tool_use"`
   - `user` 消息（工具结果）→ 当前被忽略（导致警告）
   - `system` 消息 → 仅记录日志
   - `result` 消息 → 仅记录日志

## 3. 需要修复的问题

### 3.1 处理 User 类型消息

当前 `claudeService.ts` 在第 201 行会对包含 `tool_result` 的 `user` 类型消息发出警告。这些消息应该被正确处理：

```typescript
else if (msg.type === 'user' && msg.message) {
  // 处理用户消息（包含工具结果）
  const userMsg = msg.message;
  if (userMsg.content && Array.isArray(userMsg.content)) {
    for (const block of userMsg.content) {
      if (block.type === 'tool_result') {
        yield {
          type: 'tool_result',
          message_type: 'user',
          content: block.content,
          tool_use_id: block.tool_use_id,
          session_id: sessionId
        };
      }
    }
  }
}
```

### 3.2 UI 层支持工具消息

在 `SimpleMarkdownToolWindowFactory.kt` 中添加对工具相关消息的处理：

```kotlin
when (response.type) {
    "text" -> {
        // 现有的文本处理
    }
    "tool_use" -> {
        // 显示工具使用信息
        response.content?.let { content ->
            val toolData = JSONObject(content)
            responseBuilder.append("\n🔧 **使用工具**: ${toolData.getString("name")}\n")
            responseBuilder.append("```json\n${toolData.getJSONObject("input").toString(2)}\n```\n")
        }
    }
    "tool_result" -> {
        // 显示工具执行结果
        responseBuilder.append("\n📋 **工具结果**:\n")
        responseBuilder.append("```\n${response.content}\n```\n")
    }
    "error" -> {
        // 现有的错误处理
    }
}
```

## 4. 消息类型总结

| 消息类型 | 方向 | 描述 | 当前状态 |
|---------|------|------|---------|
| text | 服务器→客户端 | Claude 的文本回复 | ✅ 已实现 |
| tool_use | 服务器→客户端 | Claude 使用工具 | ⚠️ 服务端已实现，UI未处理 |
| tool_result | 服务器→客户端 | 工具执行结果 | ❌ 未实现，导致警告 |
| error | 服务器→客户端 | 错误消息 | ✅ 已实现 |
| done | 服务器→客户端 | 消息流结束 | ✅ 已实现 |
| welcome | 服务器→客户端 | 连接欢迎消息 | ✅ 已实现 |
| health | 双向 | 健康检查 | ✅ 已实现 |
| pong | 服务器→客户端 | Ping 响应 | ✅ 已实现 |

## 5. Claude SDK Options 参数说明

根据 Claude SDK 源代码（claudeService.js），支持的 options 参数如下：

| 参数名 | 类型 | 描述 | 示例值 |
|--------|------|------|---------|
| systemPrompt | string | 系统提示词 | "You are a helpful assistant." |
| maxTurns | number | 最大对话轮数 | 20 |
| cwd | string | 工作目录路径 | "/path/to/project" |
| allowedTools | string[] | 允许使用的工具列表 | ["read", "write", "bash"] |
| permissionMode | string | 权限模式 | "auto", "manual" |
| maxThinkingTokens | number | 最大思考令牌数 | 10000 |
| model | string | 模型名称 | "Opus", "Sonnet" |

注意：
- 参数名使用驼峰命名法（camelCase）
- model 参数支持 "Opus" 和 "Sonnet" 两个值
- 所有参数都是可选的

## 6. 建议的改进

1. **完善消息类型处理**：在 Node.js 服务端正确处理所有 Claude SDK 消息类型
2. **增强 UI 显示**：在插件 UI 中显示工具使用和结果信息
3. **添加调试模式**：可选择性地显示所有消息类型，便于调试
4. **消息过滤选项**：允许用户选择显示或隐藏某些类型的消息