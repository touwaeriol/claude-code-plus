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
9. [会话压缩](#会话压缩)
   - [压缩流程](#压缩流程)
   - [压缩命令格式](#压缩命令格式)
   - [压缩后的会话结构](#压缩后的会话结构)

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
  isCompactSummary?: boolean   // 是否为 /compact 命令生成的摘要
}
```

**注意：** Summary 消息不使用 content blocks。

### 6. Compact Command Message (压缩命令消息)

当用户使用 `/compact` 命令时，会产生特殊的用户和助手消息。

#### 用户发起压缩命令
```typescript
// 用户消息中包含压缩命令
{
  type: "user",
  message: {
    role: "user",
    content: [{
      type: "text",
      text: `<local-command>
<command-name>/compact</command-name>
<command-message>compact</command-message>
</local-command>`
    }]
  }
}
```

#### 助手确认压缩命令
```typescript
// 助手消息显示压缩状态
{
  type: "assistant",
  message: {
    role: "assistant",
    content: [{
      type: "text",
      text: `<local-command>
<command-name>/compact</command-name>
<command-message>compact</command-message>
<local-command-stdout>Compacted. ctrl+r to see full summary</local-command-stdout>
</local-command>

Caveat: Claude doesn't see your local commands; if a command makes changes to the directory it's viewing, ask Claude to use tools to see the result, or add the result to the conversation`
    }]
  }
}
```

#### 压缩后的新会话
压缩完成后，会创建一个新的会话文件，其中第一条消息是包含 `isCompactSummary: true` 标记的摘要消息：

```typescript
{
  type: "summary",
  summary: "压缩后的对话摘要内容...",
  leafUuid: "原始对话的最后一条消息UUID",
  isCompactSummary: true,      // 标记这是 /compact 命令生成的摘要
  uuid: "新的UUID",
  sessionId: "新的会话ID",
  timestamp: "压缩时间"
}

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

工具调用遵循请求-响应模式，**UI显示时需要将工具调用和结果组合展示**：

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
         "id": "toolu_01abc123",
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
         "type": "tool_result",
         "tool_use_id": "toolu_01abc123",  // 关联工具调用ID
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

### **关键实现要点：工具调用结果映射**

**UI展示逻辑**：
- 工具调用（`tool_use`）和工具结果（`tool_result`）通过 `id` 和 `tool_use_id` 关联
- 在助手消息中显示工具调用时，需要查找后续消息中对应的工具结果
- 工具调用状态应根据结果的存在性和成功性更新：
  - `PENDING`: 尚未找到对应结果
  - `SUCCESS`: 找到结果且 `is_error` 为 false 或未设置
  - `FAILED`: 找到结果且 `is_error` 为 true

**实现参考**（基于 Claudia 项目）：
```typescript
// 工具调用结果映射机制
const [toolResults, setToolResults] = useState<Map<string, any>>(new Map());

// 从所有消息中提取工具结果
useEffect(() => {
  const results = new Map<string, any>();
  
  streamMessages.forEach(msg => {
    if (msg.type === "user" && msg.message?.content) {
      msg.message.content.forEach((content: any) => {
        if (content.type === "tool_result" && content.tool_use_id) {
          results.set(content.tool_use_id, content);
        }
      });
    }
  });
  
  setToolResults(results);
}, [streamMessages]);

// 获取特定工具调用的结果
const getToolResult = (toolId: string): any => {
  return toolResults.get(toolId) || null;
};
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

## 会话压缩

### 压缩流程

1. **用户触发**：用户在对话中输入 `/compact` 命令
2. **命令执行**：Claude CLI 处理压缩命令，分析当前会话历史
3. **生成摘要**：AI 生成对话的压缩摘要（约需 30 秒）
4. **创建新会话**：系统创建新的会话文件，包含压缩后的摘要
5. **会话切换**：用户可以选择切换到新的压缩会话继续对话

### 压缩命令格式

#### 用户输入压缩命令
```json
{
  "type": "user",
  "message": {
    "content": [{
      "type": "text",
      "text": "<local-command>\n<command-name>/compact</command-name>\n<command-message>compact</command-message>\n</local-command>"
    }]
  }
}
```

#### 系统确认压缩完成
```json
{
  "type": "assistant",
  "message": {
    "content": [{
      "type": "text",
      "text": "<local-command>\n<command-name>/compact</command-name>\n<command-message>compact</command-message>\n<local-command-stdout>Compacted. ctrl+r to see full summary</local-command-stdout>\n</local-command>\n\nCaveat: Claude doesn't see your local commands..."
    }]
  }
}
```

### 压缩后的会话结构

压缩完成后，新会话文件的结构：

1. **摘要消息**（第一条消息）
   ```json
   {
     "type": "summary",
     "summary": "这是一个关于 Claude Code Plus 项目的对话。用户请求修复项目标签显示和会话计数问题...",
     "leafUuid": "原始会话最后一条消息的UUID",
     "isCompactSummary": true,
     "uuid": "新生成的UUID",
     "sessionId": "新的会话ID",
     "timestamp": "2025-01-20T10:30:00Z"
   }
   ```

2. **系统初始化消息**
   ```json
   {
     "type": "system",
     "subtype": "init",
     // ... 标准系统初始化信息
   }
   ```

3. **后续对话**：正常的 User-Assistant 消息交互

### UI 处理建议

1. **识别压缩会话**：通过 `isCompactSummary: true` 标记识别
2. **特殊标记**：在会话列表中为压缩会话添加特殊图标或标记
3. **摘要预览**：悬停时显示摘要内容的预览
4. **自动切换**：压缩完成后提示用户是否切换到新会话

## Claudia 项目消息序列化和反序列化实现

### 消息解析核心逻辑

基于对 Claudia 项目的深入研究，发现了完整的 JSONL 消息处理机制：

#### 1. JSONL 流处理架构

**Rust 后端流处理**：
```rust
// src-tauri/src/commands/claude.rs
async fn spawn_claude_process(app: AppHandle, mut cmd: Command) -> Result<(), String> {
    // 启动 Claude CLI 进程，使用 --output-format stream-json
    let mut child = cmd
        .arg("--output-format")
        .arg("stream-json")
        .spawn()?;

    // 获取 stdout 和 stderr
    let stdout = child.stdout.take().ok_or("Failed to get stdout")?;
    let stderr = child.stderr.take().ok_or("Failed to get stderr")?;

    // 创建异步读取器
    let stdout_reader = BufReader::new(stdout);
    let stderr_reader = BufReader::new(stderr);

    // 逐行读取 JSONL 输出
    let stdout_task = tokio::spawn(async move {
        let mut lines = stdout_reader.lines();
        while let Ok(Some(line)) = lines.next_line().await {
            // 发送每一行到前端
            let _ = app_handle.emit("claude-output", &line);
        }
    });
}
```

#### 2. TypeScript 前端消息解析

**消息类型定义**：
```typescript
// src/components/AgentExecution.tsx
export interface ClaudeStreamMessage {
  type: "system" | "assistant" | "user" | "result";
  subtype?: string;
  message?: {
    content?: any[];
    usage?: {
      input_tokens: number;
      output_tokens: number;
    };
  };
  usage?: {
    input_tokens: number;
    output_tokens: number;
  };
  [key: string]: any;
}
```

**实时流解析**：
```typescript
// 监听来自 Rust 后端的事件
const outputUnlisten = await listen<string>(`agent-output:${runId}`, (event) => {
  try {
    // 存储原始 JSONL
    setRawJsonlOutput(prev => [...prev, event.payload]);
    
    // 解析并显示
    const message = JSON.parse(event.payload) as ClaudeStreamMessage;
    setMessages(prev => [...prev, message]);
  } catch (err) {
    console.error("Failed to parse message:", err, event.payload);
  }
});
```

#### 3. 消息渲染和处理

**StreamMessage 组件处理逻辑**：
```typescript
// src/components/StreamMessage.tsx
const StreamMessageComponent: React.FC<StreamMessageProps> = ({ message }) => {
  // 处理不同消息类型
  if (message.type === "assistant" && message.message) {
    const msg = message.message;
    
    // 处理内容块数组
    msg.content && Array.isArray(msg.content) && msg.content.map((content, idx) => {
      // 文本内容 - 渲染为 Markdown
      if (content.type === "text") {
        return <ReactMarkdown>{content.text}</ReactMarkdown>;
      }
      
      // 工具调用 - 渲染专用小部件
      if (content.type === "tool_use") {
        const toolName = content.name?.toLowerCase();
        const input = content.input;
        const toolId = content.id;
        
        // 获取工具结果
        const toolResult = getToolResult(toolId);
        
        // 根据工具名称渲染不同的小部件
        switch(toolName) {
          case "read":
            return <ReadWidget filePath={input.file_path} result={toolResult} />;
          case "write":
            return <WriteWidget filePath={input.file_path} content={input.content} result={toolResult} />;
          case "bash":
            return <BashWidget command={input.command} result={toolResult} />;
          // ... 更多工具类型
        }
      }
    });
  }
};
```

#### 4. 历史记录加载和度量计算

**JSONL 文件读取和分析**：
```rust
// src-tauri/src/commands/agents.rs
impl AgentRunMetrics {
    /// 从 JSONL 内容计算运行指标
    pub fn from_jsonl(jsonl_content: &str) -> Self {
        let mut total_tokens = 0i64;
        let mut cost_usd = 0.0f64;
        let mut message_count = 0i64;
        let mut start_time: Option<chrono::DateTime<chrono::Utc>> = None;
        let mut end_time: Option<chrono::DateTime<chrono::Utc>> = None;

        for line in jsonl_content.lines() {
            if let Ok(json) = serde_json::from_str::<JsonValue>(line) {
                message_count += 1;

                // 提取时间戳
                if let Some(timestamp_str) = json.get("timestamp").and_then(|t| t.as_str()) {
                    if let Ok(timestamp) = chrono::DateTime::parse_from_rfc3339(timestamp_str) {
                        let utc_time = timestamp.with_timezone(&chrono::Utc);
                        if start_time.is_none() || utc_time < start_time.unwrap() {
                            start_time = Some(utc_time);
                        }
                        if end_time.is_none() || utc_time > end_time.unwrap() {
                            end_time = Some(utc_time);
                        }
                    }
                }

                // 提取 token 使用统计 - 检查顶层和嵌套的 message.usage
                let usage = json
                    .get("usage")
                    .or_else(|| json.get("message").and_then(|m| m.get("usage")));

                if let Some(usage) = usage {
                    if let Some(input_tokens) = usage.get("input_tokens").and_then(|t| t.as_i64()) {
                        total_tokens += input_tokens;
                    }
                    if let Some(output_tokens) = usage.get("output_tokens").and_then(|t| t.as_i64()) {
                        total_tokens += output_tokens;
                    }
                }

                // 提取成本信息
                if let Some(cost) = json.get("cost").and_then(|c| c.as_f64()) {
                    cost_usd += cost;
                }
            }
        }

        let duration_ms = match (start_time, end_time) {
            (Some(start), Some(end)) => Some((end - start).num_milliseconds()),
            _ => None,
        };

        Self {
            duration_ms,
            total_tokens: if total_tokens > 0 { Some(total_tokens) } else { None },
            cost_usd: if cost_usd > 0.0 { Some(cost_usd) } else { None },
            message_count: if message_count > 0 { Some(message_count) } else { None },
        }
    }
}
```

#### 5. 会话历史加载

**JSONL 历史记录读取**：
```rust
// src-tauri/src/commands/claude.rs
#[tauri::command]
pub async fn load_session_history(
    session_id: String,
    project_id: String,
) -> Result<Vec<serde_json::Value>, String> {
    let claude_dir = get_claude_dir().map_err(|e| e.to_string())?;
    let session_path = claude_dir
        .join("projects")
        .join(&project_id)
        .join(format!("{}.jsonl", session_id));

    if !session_path.exists() {
        return Err(format!("Session file not found: {}", session_id));
    }

    let file = fs::File::open(&session_path)
        .map_err(|e| format!("Failed to open session file: {}", e))?;

    let reader = BufReader::new(file);
    let mut messages = Vec::new();

    for line in reader.lines() {
        if let Ok(line) = line {
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&line) {
                messages.push(json);
            }
        }
    }

    Ok(messages)
}
```

#### 6. 工具调用结果映射

**工具调用 ID 映射机制**：
```typescript
// src/components/StreamMessage.tsx
const StreamMessageComponent = ({ message, streamMessages }) => {
  // 状态：跟踪按工具调用 ID 映射的工具结果
  const [toolResults, setToolResults] = useState<Map<string, any>>(new Map());
  
  // 从流消息中提取所有工具结果
  useEffect(() => {
    const results = new Map<string, any>();
    
    // 遍历所有消息查找工具结果
    streamMessages.forEach(msg => {
      if (msg.type === "user" && msg.message?.content && Array.isArray(msg.message.content)) {
        msg.message.content.forEach((content: any) => {
          if (content.type === "tool_result" && content.tool_use_id) {
            results.set(content.tool_use_id, content);
          }
        });
      }
    });
    
    setToolResults(results);
  }, [streamMessages]);
  
  // 获取特定工具调用 ID 的工具结果的辅助函数
  const getToolResult = (toolId: string | undefined): any => {
    if (!toolId) return null;
    return toolResults.get(toolId) || null;
  };
};
```

### 关键发现和技术要点

#### 1. Claude CLI 命令格式
```bash
# Claudia 使用的 Claude CLI 标准格式
claude -p "用户提示" \
  --system-prompt "系统提示" \
  --model "sonnet" \
  --output-format "stream-json" \
  --verbose \
  --dangerously-skip-permissions
```

#### 2. 会话文件路径规则
```typescript
// 项目路径编码规则
const encoded_project = project_path.replace('/', "-");
const session_file = `~/.claude/projects/${encoded_project}/${session_id}.jsonl`;
```

#### 3. 消息去重和过滤逻辑
```typescript
const displayableMessages = React.useMemo(() => {
  return messages.filter((message, index) => {
    // 跳过没有意义内容的元消息
    if (message.isMeta && !message.leafUuid && !message.summary) {
      return false;
    }

    // 跳过空用户消息
    if (message.type === "user" && message.isMeta) return false;
    
    // 检查是否有可见内容
    if (Array.isArray(msg.content)) {
      let hasVisibleContent = false;
      for (const content of msg.content) {
        if (content.type === "text") {
          hasVisibleContent = true;
          break;
        } else if (content.type === "tool_result") {
          // 检查此工具结果是否会被小部件跳过
          if (!willBeSkipped) {
            hasVisibleContent = true;
            break;
          }
        }
      }
      
      if (!hasVisibleContent) {
        return false;
      }
    }

    return true;
  });
}, [messages]);
```

## 注意事项

1. **消息顺序**：会话必须遵循 User-Assistant 交替的模式
2. **工具调用ID**：每个工具调用都有唯一的ID，结果必须引用相同的ID
3. **时间戳**：所有消息都包含 ISO 8601 格式的时间戳
4. **会话压缩**：当上下文过长时，系统会插入 Summary 消息并重写会话文件
5. **版本兼容**：不同版本的 Claude Code 可能有细微的格式差异
6. **压缩标记**：`/compact` 命令生成的摘要会包含 `isCompactSummary: true` 标记
7. **流处理**：实时 JSONL 流需要逐行解析，每行都是完整的 JSON 对象
8. **错误处理**：解析失败的行应该记录但不中断整个流处理过程
9. **度量计算**：token 使用统计可能出现在顶层 `usage` 字段或嵌套的 `message.usage` 字段

## 历史会话与实时会话的消息格式差异分析

### 问题背景

在 Claude Code Plus 项目中发现了一个关键问题：**历史会话和实时会话使用不同的消息格式**，导致只能正确处理其中一种类型的消息。

### 格式差异详细对比

#### 1. 实时会话消息格式（Claude CLI 直接输出）

实时会话直接来自 Claude CLI 的 `--output-format stream-json` 输出：

```json
{
  "type": "assistant",
  "message": {
    "id": "msg_01abc123",
    "type": "message", 
    "role": "assistant",
    "model": "claude-opus-4-1-20250805",
    "content": [
      {
        "type": "text",
        "text": "我来帮你查看项目结构。"
      },
      {
        "type": "tool_use",
        "id": "toolu_01GReBR1qZqBsaLLApUjZKr2",
        "name": "LS",
        "input": {
          "path": "/Users/erio/codes/idea/claude-code-plus/desktop"
        }
      }
    ],
    "stop_reason": null,
    "stop_sequence": null,
    "usage": {
      "input_tokens": 52,
      "cache_creation_input_tokens": 6628,
      "cache_read_input_tokens": 423352,
      "output_tokens": 45,
      "service_tier": "standard"
    }
  }
}
```

**特点**：
- 结构简单，`message` 字段直接包含 Anthropic API 格式
- `content` 数组包含 `text` 和 `tool_use` 类型的内容块
- `usage` 信息位于 `message.usage`
- 没有额外的会话元数据

#### 2. 历史会话消息格式（JSONL 文件存储）

历史会话来自 `~/.claude/projects/{project}/{sessionId}.jsonl` 文件：

```json
{
  "parentUuid": "129d3a24-d688-4bab-95fb-13a60893b8b4",
  "isSidechain": false,
  "userType": "external",
  "cwd": "/Users/erio/codes/idea/claude-code-plus/desktop",
  "sessionId": "f4ce77bf-7148-415f-9f0b-bce9f5eeca46",
  "version": "1.0.69",
  "gitBranch": "main",
  "message": {
    "id": "msg_01LhHd7KtRa8vHWeWzFxDkvS",
    "type": "message",
    "role": "assistant", 
    "model": "claude-opus-4-1-20250805",
    "content": [
      {
        "type": "text",
        "text": "我来帮你查看脚本中的问题。让我先检查脚本文件的内容。"
      }
    ],
    "stop_reason": null,
    "stop_sequence": null,
    "usage": {
      "input_tokens": 4,
      "cache_creation_input_tokens": 14036,
      "cache_read_input_tokens": 30844,
      "output_tokens": 1,
      "service_tier": "standard"
    }
  },
  "type": "assistant",
  "uuid": "b466dda0-c75f-4f37-94cc-1f370a02cc29",
  "timestamp": "2025-08-07T06:41:17.502Z"
}
```

**特点**：
- 包含完整的会话元数据（`uuid`, `sessionId`, `parentUuid`, `cwd` 等）
- `message` 字段内容与实时格式相同
- 额外包含版本、分支、时间戳等信息
- 使用 UUID 进行消息链接

### 3. 处理方式差异

#### 实时消息处理（正常工作）
```kotlin
// SessionObject.kt:644
private fun parseClaudeCliMessage(jsonObject: JsonObject, jsonLine: String): EnhancedMessage? {
    val messageType = jsonObject["type"]?.jsonPrimitive?.content
    
    when (messageType) {
        "assistant" -> {
            val messageObj = jsonObject["message"]?.jsonObject
            val contentArray = messageObj?.get("content")?.jsonArray
            
            // 直接处理 Claude CLI 输出格式
            val textContent = contentArray?.mapNotNull { contentElement ->
                val contentObj = contentElement.jsonObject
                val type = contentObj["type"]?.jsonPrimitive?.content
                if (type == "text") {
                    contentObj["text"]?.jsonPrimitive?.content
                } else null
            }?.joinToString("") ?: ""
            
            // 提取工具调用...
        }
    }
}
```

#### 历史消息处理（存在问题）
```kotlin
// SessionObject.kt:1167
// 创建临时的SDKMessage对象来使用MessageConverter（适用于历史消息）
val gson = com.google.gson.Gson()
val jsonLine = gson.toJson(sessionMessage)

val sdkMessage = com.claudecodeplus.sdk.SDKMessage(
    messageId = sessionMessage.uuid ?: java.util.UUID.randomUUID().toString(),
    timestamp = sessionMessage.timestamp ?: java.time.Instant.now().toString(),
    content = jsonLine,  // 将整个历史消息序列化为content
    type = when (sessionMessage.type) {
        "assistant" -> com.claudecodeplus.sdk.MessageType.TEXT
        "user" -> com.claudecodeplus.sdk.MessageType.TEXT
        "error" -> com.claudecodeplus.sdk.MessageType.ERROR
        else -> com.claudecodeplus.sdk.MessageType.TEXT
    },
    data = com.claudecodeplus.sdk.MessageData(text = jsonLine)
)

val enhancedMessage = MessageConverter.run { sdkMessage.toEnhancedMessage() }
```

### 4. 问题根源

**问题**：历史消息处理使用了错误的数据格式转换方式。

1. **格式不匹配**：将整个 JSONL 历史消息（包含元数据）序列化后传给 `MessageConverter`
2. **双重包装**：`MessageConverter` 期望的是简化格式，而接收到的是完整的会话元数据
3. **工具调用丢失**：`MessageConverter.extractToolCalls()` 无法正确解析被双重包装的工具调用数据

### 5. 解决方案

#### 方案A：统一使用实时消息解析器
为历史消息创建适配器，将历史格式转换为实时格式后使用 `parseClaudeCliMessage()`：

```kotlin
private fun convertHistoryToRealtime(sessionMessage: ClaudeSessionMessage): JsonObject? {
    return try {
        // 提取内部的 message 对象，构造实时格式
        val realTimeFormat = buildJsonObject {
            put("type", sessionMessage.type)
            sessionMessage.message?.let { message ->
                putJsonObject("message") {
                    put("id", message.id ?: "")
                    put("type", "message")
                    put("role", message.role ?: "assistant")
                    put("model", message.model ?: "")
                    
                    // 复制 content 数组
                    message.content?.let { content ->
                        putJsonArray("content") {
                            content.forEach { contentItem ->
                                add(Json.parseToJsonElement(contentItem.toString()))
                            }
                        }
                    }
                    
                    // 复制 usage 信息
                    message.usage?.let { usage ->
                        putJsonObject("usage") {
                            put("input_tokens", usage.inputTokens ?: 0)
                            put("output_tokens", usage.outputTokens ?: 0)
                            put("cache_creation_input_tokens", usage.cacheCreationInputTokens ?: 0)
                            put("cache_read_input_tokens", usage.cacheReadInputTokens ?: 0)
                        }
                    }
                }
            }
        }
        realTimeFormat
    } catch (e: Exception) {
        null
    }
}
```

#### 方案B：修复 MessageConverter
改进 `MessageConverter.extractToolCalls()` 以正确处理历史消息格式。

### 6. 推荐实现

**推荐采用方案A**，因为：
1. **一致性**：使用同一套解析逻辑，减少维护成本
2. **可靠性**：实时解析器已经验证工作正常
3. **扩展性**：未来消息格式变化只需要维护一个解析器

### 7. 修复后的历史消息处理逻辑

```kotlin
// 替换 SessionObject.kt:1162-1184 的历史消息处理逻辑
sessionMessages.forEach { sessionMessage ->
    try {
        println("[SessionObject] 📥 处理历史消息: ${sessionMessage.type} - ${sessionMessage.uuid?.take(8) ?: "unknown"}...")
        
        // 将历史格式转换为实时格式
        val realtimeFormat = convertHistoryToRealtime(sessionMessage)
        
        if (realtimeFormat != null) {
            // 使用实时消息解析器
            val enhancedMessage = parseClaudeCliMessage(realtimeFormat, realtimeFormat.toString())
            
            if (enhancedMessage != null && (enhancedMessage.content.isNotEmpty() || enhancedMessage.toolCalls.isNotEmpty())) {
                println("[SessionObject] ✅ 历史消息解析成功: content长度=${enhancedMessage.content.length}, toolCalls=${enhancedMessage.toolCalls.size}")
                addMessage(enhancedMessage)
            }
        }
    } catch (e: Exception) {
        println("[SessionObject] 处理历史消息异常: ${e.message}")
        e.printStackTrace()
    }
}
```

## 参考资源

- [Claude Code SDK Python 类型定义](https://github.com/anthropics/claude-code-sdk-python/blob/main/src/claude_code_sdk/types.py)
- [Anthropic SDK 文档](https://docs.anthropic.com/claude/docs)
- 本地会话文件示例：`~/.claude/projects/`
- **Claudia 项目源码**：`/Users/erio/codes/webstorm/claudia` - 完整的消息处理实现参考