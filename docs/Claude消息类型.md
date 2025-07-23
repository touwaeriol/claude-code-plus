# Claude Code SDK 消息类型和数据模型

本文档详细记录了 Claude Code SDK 中的所有消息类型、内容类型以及它们的数据结构。

## 目录

1. [概述](#概述)
2. [消息类型 (Message Types)](#消息类型-message-types)
3. [内容块类型 (Content Block Types)](#内容块类型-content-block-types)
4. [消息类型与内容块的关系](#消息类型与内容块的关系)
5. [完整数据模型](#完整数据模型)
6. [工具调用流程](#工具调用流程)
7. [实际示例](#实际示例)
8. [上下文消息格式](#上下文消息格式)
   - [概述](#概述-1)
   - [上下文显示格式](#上下文显示格式)
   - [上下文类型和图标](#上下文类型和图标)
   - [消息示例](#消息示例)
   - [设计原则](#设计原则)
   - [解析规则](#解析规则)

## 概述

Claude Code 使用 JSONL（JSON Lines）格式存储会话历史，每行是一个独立的 JSON 对象，代表一条消息。消息分为多种类型，每种类型有其特定的结构和用途。

## 消息类型 (Message Types)

### 1. User Message (用户消息)

用户发送的消息，包含用户输入或工具执行结果。

```typescript
interface UserMessage {
  type: "user"
  uuid: string                    // 消息唯一标识符
  sessionId: string              // 会话ID
  timestamp: string              // ISO 8601 时间戳
  parentUuid: string | null      // 父消息ID（用于对话树）
  isSidechain: boolean           // 是否为侧链对话
  userType: string               // 用户类型（如 "external"）
  cwd: string                    // 当前工作目录
  version: string                // Claude Code 版本
  message: {
    role: "user"
    content: ContentBlock[]      // 内容块数组
  }
  toolUseResult?: {              // 工具执行结果元数据（可选）
    oldTodos?: any[]
    newTodos?: any[]
    // 其他工具特定的结果数据
  }
}
```

**可包含的内容块类型：**
- `TextBlock` - 用户的文本输入
- `ToolResultBlock` - 工具执行结果

### 2. Assistant Message (助手消息)

AI 助手的响应，可包含文本回复或工具调用请求。

```typescript
interface AssistantMessage {
  type: "assistant"
  uuid: string
  sessionId: string
  timestamp: string
  parentUuid: string | null
  isSidechain: boolean
  userType: string
  cwd: string
  version: string
  message: {
    id: string                   // 消息ID (msg_xxx)
    type: "message"
    role: "assistant"
    model: string                // 模型名称
    content: ContentBlock[]      // 内容块数组
    stop_reason: string | null   // 停止原因
    stop_sequence: string | null // 停止序列
    usage: {                     // 使用统计
      input_tokens: number
      output_tokens: number
      cache_creation_input_tokens: number
      cache_read_input_tokens: number
      service_tier: string
      server_tool_use?: {
        web_search_requests: number
      }
    }
  }
  requestId?: string             // 请求ID (req_xxx)
  isApiErrorMessage?: boolean    // 是否为API错误消息
}
```

**可包含的内容块类型：**
- `TextBlock` - AI 的文本响应
- `ToolUseBlock` - AI 请求调用的工具

### 3. System Message (系统消息)

系统级消息，通常用于会话初始化。

```typescript
interface SystemMessage {
  type: "system"
  subtype: "init"               // 子类型
  uuid: string
  sessionId: string
  timestamp: string
  apiKeySource: "user" | "project" | "org" | "temporary"
  cwd: string                   // 当前工作目录
  version: string
  tools: string[]               // 可用工具列表
  mcp_servers: {                // MCP 服务器配置
    name: string
    status: string
  }[]
  model: string                 // 使用的模型
  permissionMode: "default" | "acceptEdits" | "bypassPermissions" | "plan"
}
```

**注意：** System 消息不使用 content blocks。

### 4. Result Message (结果消息)

会话结束时的统计信息。

```typescript
interface ResultMessage {
  type: "result"
  subtype: "success" | "error_max_turns" | "error_during_execution"
  uuid: string
  sessionId: string
  timestamp: string
  duration_ms: number           // 执行时长（毫秒）
  duration_api_ms: number       // API 调用时长
  num_turns: number             // 对话轮数
  is_error: boolean             // 是否出错
  result?: string               // 结果描述
  total_cost_usd: number        // 总成本（美元）
  usage: {                      // 使用统计
    input_tokens: number
    output_tokens: number
    cache_creation_input_tokens: number
    cache_read_input_tokens: number
  }
}
```

**注意：** Result 消息不使用 content blocks。

### 5. Summary Message (摘要消息)

用于上下文压缩时保存对话摘要。

```typescript
interface SummaryMessage {
  type: "summary"
  summary: string               // 对话摘要
  leafUuid: string             // 叶子节点UUID
  uuid?: string
  sessionId?: string
  timestamp?: string
}
```

**注意：** Summary 消息不使用 content blocks。

## 内容块类型 (Content Block Types)

内容块是消息内容的基本单位，只在 User 和 Assistant 消息中使用。

### 1. TextBlock (文本块)

纯文本内容。

```typescript
interface TextBlock {
  type: "text"
  text: string                  // 文本内容
}
```

### 2. ToolUseBlock (工具使用块)

AI 请求调用工具。

```typescript
interface ToolUseBlock {
  type: "tool_use"
  id: string                    // 工具调用的唯一标识符 (toolu_xxx)
  name: string                  // 工具名称
  input: any                    // 工具参数（根据具体工具而定）
}
```

### 3. ToolResultBlock (工具结果块)

工具执行的结果。

```typescript
interface ToolResultBlock {
  type: "tool_result"
  tool_use_id: string          // 对应的工具调用ID
  content: string | any        // 工具执行结果
  is_error?: boolean           // 是否为错误结果
}
```

## 消息类型与内容块的关系

| 消息类型 | 可包含的内容块类型 | 说明 |
|---------|------------------|------|
| User | TextBlock, ToolResultBlock | 用户输入或工具结果 |
| Assistant | TextBlock, ToolUseBlock | AI响应或工具调用 |
| System | 无 | 使用特定字段 |
| Result | 无 | 使用特定字段 |
| Summary | 无 | 使用特定字段 |

## 完整数据模型

### 基础类型定义

```typescript
// 内容块联合类型
type ContentBlock = TextBlock | ToolUseBlock | ToolResultBlock

// 消息联合类型
type Message = UserMessage | AssistantMessage | SystemMessage | ResultMessage | SummaryMessage

// API 消息格式（来自 Anthropic SDK）
interface APIUserMessage {
  role: "user"
  content: string | ContentBlock[]
}

interface APIAssistantMessage {
  id: string
  type: "message"
  role: "assistant"
  model: string
  content: ContentBlock[]
  stop_reason: string | null
  stop_sequence: string | null
  usage: Usage
}

// 使用统计
interface Usage {
  input_tokens: number
  output_tokens: number
  cache_creation_input_tokens?: number
  cache_read_input_tokens?: number
  service_tier?: string
  server_tool_use?: {
    web_search_requests: number
  }
}
```

## 工具调用流程

工具调用遵循请求-响应模式：

1. **用户发起请求**
   ```json
   {
     "type": "user",
     "message": {
       "content": [{"type": "text", "text": "请帮我创建一个TODO列表"}]
     }
   }
   ```

2. **AI 请求调用工具**
   ```json
   {
     "type": "assistant",
     "message": {
       "content": [{
         "type": "tool_use",
         "id": "toolu_01abc...",
         "name": "TodoWrite",
         "input": {"todos": [...]}
       }]
     }
   }
   ```

3. **系统执行工具并返回结果**
   ```json
   {
     "type": "user",
     "message": {
       "content": [{
         "tool_use_id": "toolu_01abc...",
         "type": "tool_result",
         "content": "Todos have been modified successfully"
       }]
     }
   }
   ```

4. **AI 基于结果继续响应**
   ```json
   {
     "type": "assistant",
     "message": {
       "content": [{"type": "text", "text": "我已经为您创建了TODO列表。"}]
     }
   }
   ```

## 实际示例

### 简单对话示例

```json
// 用户输入
{
  "type": "user",
  "uuid": "9e503a42-53d3-4c76-a7ec-31e89bbb1ebb",
  "sessionId": "350d4477-5c09-4e94-8c8b-afd2616c048e",
  "message": {
    "role": "user",
    "content": [{"type": "text", "text": "1+1="}]
  }
}

// AI 响应
{
  "type": "assistant",
  "uuid": "44c59e05-9c06-4dad-abd6-7e2d0c11b1d5",
  "sessionId": "350d4477-5c09-4e94-8c8b-afd2616c048e",
  "message": {
    "role": "assistant",
    "content": [{"type": "text", "text": "2"}]
  }
}
```

### 工具调用示例

```json
// AI 请求读取文件
{
  "type": "assistant",
  "message": {
    "content": [{
      "type": "tool_use",
      "id": "toolu_01GReBR1qZqBsaLLApUjZKr2",
      "name": "Read",
      "input": {"file_path": "/path/to/file.txt"}
    }]
  }
}

// 工具执行结果
{
  "type": "user",
  "message": {
    "content": [{
      "tool_use_id": "toolu_01GReBR1qZqBsaLLApUjZKr2",
      "type": "tool_result",
      "content": "文件内容..."
    }]
  }
}
```

### 混合内容示例

AI 可以在一条消息中同时包含文本和工具调用：

```json
{
  "type": "assistant",
  "message": {
    "content": [
      {"type": "text", "text": "我来检查一下您的代码。"},
      {"type": "tool_use", "id": "toolu_01abc", "name": "Read", "input": {...}},
      {"type": "tool_use", "id": "toolu_02def", "name": "Bash", "input": {...}}
    ]
  }
}
```

## 上下文消息格式

### 概述

Claude Code Plus 支持两种方式添加上下文：
1. **Add Context 按钮**：在工具栏点击添加
2. **@ 符号触发**：在输入框中输入 `@` 符号触发选择

两种方式添加的上下文都会统一显示在输入框上方的标签区域，并在发送消息时以 Markdown 引用块格式插入到消息开头。

### 上下文显示格式

#### UI 显示
在聊天界面中，上下文在两个地方显示：

1. **输入区域**：与 Add Context 按钮同一行显示为标签
   ```
   [📎 Add Context]        [@build.gradle.kts] [@Main.kt]...
   ┌─────────────────────────────────────────────────┐
   │  输入框区域...                                  │
   └─────────────────────────────────────────────────┘
   ```

2. **聊天消息**：用户消息上方单独显示上下文区域
   ```
   📄 build.gradle.kts
   🌐 https://example.com (示例网页)
   ┌─────────────────────────────────────────────────┐
   │ 用户的实际消息内容...                           │
   └─────────────────────────────────────────────────┘
   ```

#### 发送给 AI 的格式

发送给 AI 的消息使用 Markdown 引用块格式标记上下文：

```markdown
> **上下文资料**
> 
> - 📄 `build.gradle.kts`
> - 🌐 https://example.com (示例网页)
> - 📁 `src/main/kotlin` (15个文件)

用户的实际消息内容...
```

### 上下文类型和图标

| 类型 | 图标 | 格式示例 |
|------|------|----------|
| 文件引用 | 📄 | `> - 📄 \`src/main/kotlin/Main.kt\`` |
| 网页引用 | 🌐 | `> - 🌐 https://example.com (网页标题)` |
| 文件夹引用 | 📁 | `> - 📁 \`src/main\` (25个文件)` |
| 符号引用 | 🔗 | `> - 🔗 \`MyClass.method()\` (FUNCTION) - Main.kt:45` |
| 终端输出 | 💻 | `> - 💻 终端输出 (50行) ⚠️` |
| 问题报告 | ⚠️ | `> - ⚠️ 问题报告 (3个) [ERROR]` |
| Git引用 | 🔀 | `> - 🔀 Git DIFF` |
| 选择内容 | ✏️ | `> - ✏️ 当前选择内容` |
| 工作区 | 🏠 | `> - 🏠 当前工作区` |

### 消息示例

#### 带上下文的用户消息

```json
{
  "type": "user",
  "uuid": "9e503a42-53d3-4c76-a7ec-31e89bbb1ebb",
  "sessionId": "350d4477-5c09-4e94-8c8b-afd2616c048e",
  "message": {
    "role": "user",
    "content": [{
      "type": "text", 
      "text": "> **上下文资料**\n> \n> - 📄 `build.gradle.kts`\n> - 📄 `src/main/kotlin/Main.kt`\n\n请帮我分析这两个文件的关系"
    }]
  },
  "contexts": [
    {
      "type": "FileReference",
      "path": "build.gradle.kts",
      "fullPath": "/Users/user/project/build.gradle.kts",
      "displayType": "TAG"
    },
    {
      "type": "FileReference", 
      "path": "src/main/kotlin/Main.kt",
      "fullPath": "/Users/user/project/src/main/kotlin/Main.kt",
      "displayType": "TAG"
    }
  ]
}
```

### 设计原则

1. **统一显示**：所有上下文都显示为标签，避免混合显示方式
2. **Markdown 友好**：使用引用块格式，渲染美观且易于解析
3. **日志友好**：以 `> **上下文资料**` 开头，便于搜索和解析
4. **视觉区分**：使用图标和格式化文本区分不同类型的上下文
5. **一次性标记**：每个消息最多在开头有一个上下文区域

### 解析规则

从消息中提取上下文的正则表达式：

```regex
^> \*\*上下文资料\*\*\n(?:> \n)?((?:> - .+\n)+)\n
```

这样可以轻松分离上下文部分和实际用户消息部分，便于：
- 日志分析和调试
- 上下文统计和管理  
- 消息重新格式化
- 自动化处理

## 注意事项

1. **消息顺序**：会话必须遵循 User-Assistant 交替的模式
2. **工具调用ID**：每个工具调用都有唯一的ID，结果必须引用相同的ID
3. **时间戳**：所有消息都包含 ISO 8601 格式的时间戳
4. **会话压缩**：当上下文过长时，系统会插入 Summary 消息并重写会话文件
5. **版本兼容**：不同版本的 Claude Code 可能有细微的格式差异

## 参考资源

- [Claude Code SDK Python 类型定义](https://github.com/anthropics/claude-code-sdk-python/blob/main/src/claude_code_sdk/types.py)
- [Anthropic SDK 文档](https://docs.anthropic.com/claude/docs)
- 本地会话文件示例：`~/.claude/projects/`