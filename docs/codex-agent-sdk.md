# Codex Agent SDK (Kotlin) 详细文档

> 本文档详细说明了 `codex-agent-sdk` 模块的实现情况、功能对比、数据模型和使用方式。

## 1. 概述

`codex-agent-sdk` 是 OpenAI Codex TypeScript SDK 的 Kotlin 移植版本，专为 JVM 应用设计，允许开发者以编程方式控制 Codex CLI 代理。

### 1.1 官方 SDK 对比

| 功能特性 | 官方 TypeScript SDK | Kotlin 实现 | 状态 |
|---------|---------------------|-------------|------|
| **核心类** | `Codex` | `CodexClient` | ✅ 完整实现 |
| **会话类** | `Thread` | `CodexSession` | ✅ 完整实现 |
| **进程执行器** | `CodexExec` | `CodexExecProcess` | ✅ 完整实现 |
| **新建线程** | `startThread()` | `startThread()` | ✅ 完整实现 |
| **恢复线程** | `resumeThread(id)` | `resumeThread(id)` | ✅ 完整实现 |
| **同步执行** | `run()` | `run()` (suspend) | ✅ 完整实现 |
| **流式执行** | `runStreamed()` | `runStreamed()` | ✅ 完整实现 |
| **文本输入** | `string` | `String` | ✅ 完整实现 |
| **图片输入** | `local_image` | `UserInput.LocalImage` | ✅ 完整实现 |
| **输出模式** | `outputSchema` | `outputSchema` | ✅ 完整实现 |
| **取消支持** | `AbortSignal` | `Job` (协程) | ✅ 完整实现 |
| **沙盒模式** | 3种模式 | 3种模式 | ✅ 完整实现 |
| **审批策略** | 4种策略 | 4种策略 | ✅ 完整实现 |
| **推理强度** | 4级 | 4级 | ✅ 完整实现 |
| **网络访问** | `networkAccessEnabled` | `networkAccessEnabled` | ✅ 完整实现 |
| **Web 搜索** | `webSearchEnabled` | `webSearchEnabled` | ✅ 完整实现 |
| **附加目录** | `additionalDirectories` | `additionalDirectories` | ✅ 完整实现 |
| **跳过Git检查** | `skipGitRepoCheck` | `skipGitRepoCheck` | ✅ 完整实现 |

### 1.2 实现完整性评估

**完整性评分: 100%**

Kotlin 实现完整覆盖了官方 TypeScript SDK 的所有核心功能：
- ✅ 所有 API 方法
- ✅ 所有配置选项
- ✅ 所有数据模型
- ✅ 所有事件类型
- ✅ 所有线程项目类型

---

## 2. 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      CodexClient                             │
│  (SDK 入口点，管理客户端配置和会话创建)                        │
├─────────────────────────────────────────────────────────────┤
│                      CodexSession                            │
│  (会话上下文，封装 run/runStreamed 操作)                      │
├─────────────────────────────────────────────────────────────┤
│                    CodexExecProcess                          │
│  (子进程执行器，启动 codex exec --experimental-json)          │
├─────────────────────────────────────────────────────────────┤
│                      Codex CLI                               │
│  (底层命令行工具)                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 核心组件

| 组件 | 职责 |
|------|------|
| `CodexClient` | SDK 入口，负责创建/恢复会话线程 |
| `CodexSession` | 会话上下文，封装 `run` 与 `runStreamed` 方法 |
| `CodexExecProcess` | 子进程执行器，管理 Codex CLI 生命周期 |
| `ThreadEvent` | 事件模型，对齐 TypeScript SDK 的事件结构 |
| `ThreadItem` | 项目模型，表示各类执行结果 |
| `UserInput` | 输入类型，支持文本和本地图片 |

---

## 3. 数据模型

### 3.1 配置选项

#### CodexClientOptions
```kotlin
data class CodexClientOptions(
    val codexPathOverride: Path? = null,  // Codex CLI 路径覆盖
    val baseUrl: String? = null,          // API 基础 URL
    val apiKey: String? = null,           // API 密钥
    val env: Map<String, String>? = null, // 环境变量覆盖
)
```

#### ThreadOptions
```kotlin
data class ThreadOptions(
    val model: String? = null,                           // 模型名称
    val sandboxMode: SandboxMode? = null,                // 沙盒模式
    val workingDirectory: String? = null,                // 工作目录
    val skipGitRepoCheck: Boolean = false,               // 跳过 Git 仓库检查
    val modelReasoningEffort: ModelReasoningEffort? = null, // 推理强度
    val networkAccessEnabled: Boolean? = null,           // 网络访问
    val webSearchEnabled: Boolean? = null,               // Web 搜索
    val approvalPolicy: ApprovalMode? = null,            // 审批策略
    val additionalDirectories: List<String> = emptyList(), // 附加目录
)
```

#### TurnOptions
```kotlin
data class TurnOptions(
    val outputSchema: JsonObject? = null,  // 结构化输出 JSON Schema
    val cancellation: Job? = null,         // 取消信号
)
```

### 3.2 枚举类型

#### SandboxMode (沙盒模式)
```kotlin
enum class SandboxMode(val wireValue: String) {
    READ_ONLY("read-only"),           // 只读模式（默认）
    WORKSPACE_WRITE("workspace-write"), // 工作区写入模式
    DANGER_FULL_ACCESS("danger-full-access"), // 危险：完全访问
}
```

#### ApprovalMode (审批策略)
```kotlin
enum class ApprovalMode(val wireValue: String) {
    NEVER("never"),           // 从不请求审批
    ON_REQUEST("on-request"), // 按请求审批
    ON_FAILURE("on-failure"), // 失败时审批
    UNTRUSTED("untrusted"),   // 不信任模式
}
```

#### ModelReasoningEffort (推理强度)
```kotlin
enum class ModelReasoningEffort(val wireValue: String) {
    MINIMAL("minimal"), // 最小
    LOW("low"),         // 低
    MEDIUM("medium"),   // 中等
    HIGH("high"),       // 高
}
```

### 3.3 事件类型

#### ThreadEvent
```kotlin
@Serializable
data class ThreadEvent(
    val type: String,              // 事件类型
    val threadId: String? = null,  // 线程 ID (thread.started 事件)
    val item: ThreadItem? = null,  // 项目 (item.* 事件)
    val usage: Usage? = null,      // 使用量 (turn.completed 事件)
    val error: ThreadError? = null, // 错误 (turn.failed 事件)
)
```

**事件类型列表：**

| 事件类型 | 说明 |
|---------|------|
| `thread.started` | 线程启动，包含 `thread_id` |
| `turn.started` | 回合开始 |
| `turn.completed` | 回合完成，包含 `usage` |
| `turn.failed` | 回合失败，包含 `error` |
| `item.started` | 项目开始 |
| `item.updated` | 项目更新 |
| `item.completed` | 项目完成 |
| `error` | 流级别错误 |

### 3.4 线程项目类型

#### ThreadItem
```kotlin
@Serializable
data class ThreadItem(
    val id: String? = null,
    val type: String,                  // 项目类型
    val text: String? = null,          // agent_message/reasoning
    val command: String? = null,       // command_execution
    val aggregatedOutput: String? = null, // 命令输出
    val exitCode: Int? = null,         // 命令退出码
    val status: String? = null,        // 状态
    val changes: List<FileChange>? = null, // file_change
    val result: McpToolCallResult? = null, // mcp_tool_call
    val error: McpToolCallError? = null,   // MCP 错误
    val query: String? = null,         // web_search
    val items: List<TodoEntry>? = null, // todo_list
    val arguments: JsonElement? = null, // MCP 参数
    val content: JsonElement? = null,   // 内容
)
```

**项目类型列表：**

| 类型 | 说明 | 关键字段 |
|-----|------|---------|
| `agent_message` | Agent 响应消息 | `text` |
| `reasoning` | 推理摘要 | `text` |
| `command_execution` | 命令执行 | `command`, `aggregatedOutput`, `exitCode`, `status` |
| `file_change` | 文件变更 | `changes` (path, kind) |
| `mcp_tool_call` | MCP 工具调用 | `server`, `tool`, `arguments`, `result`, `error` |
| `web_search` | Web 搜索 | `query` |
| `todo_list` | 待办列表 | `items` (text, completed) |
| `error` | 错误项目 | `message` |

### 3.5 结果类型

#### TurnResult
```kotlin
data class TurnResult(
    val items: List<ThreadItem>,  // 回合产生的所有项目
    val finalResponse: String,    // 最终文本响应
    val usage: Usage?,            // Token 使用统计
)
```

#### Usage
```kotlin
@Serializable
data class Usage(
    val inputTokens: Int,         // 输入 Token 数
    val cachedInputTokens: Int,   // 缓存输入 Token 数
    val outputTokens: Int,        // 输出 Token 数
)
```

#### StreamedTurn
```kotlin
data class StreamedTurn(
    val events: Flow<ThreadEvent>, // 事件流
)
```

### 3.6 用户输入类型

#### UserInput
```kotlin
sealed interface UserInput {
    data class Text(val text: String) : UserInput
    data class LocalImage(val path: Path) : UserInput
}
```

---

## 4. 使用方式

### 4.1 基础用法 (Kotlin)

```kotlin
import com.asakii.codex.agent.sdk.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // 1. 创建客户端
    val client = CodexClient(
        CodexClientOptions(
            apiKey = System.getenv("CODEX_API_KEY"),
        )
    )

    // 2. 启动新会话
    val session = client.startThread(
        ThreadOptions(
            model = "gpt-4o",
            sandboxMode = SandboxMode.WORKSPACE_WRITE,
        )
    )

    // 3. 执行任务
    val result = session.run("请列出当前目录下的所有文件")

    // 4. 处理结果
    println("响应: ${result.finalResponse}")
    println("Token 使用: ${result.usage}")
}
```

### 4.2 流式响应

```kotlin
import kotlinx.coroutines.flow.collect

val session = client.startThread()
val streamedTurn = session.runStreamed("分析这段代码")

streamedTurn.events.collect { event ->
    when (event.type) {
        "thread.started" -> println("线程启动: ${event.threadId}")
        "item.started" -> println("项目开始: ${event.item?.type}")
        "item.completed" -> {
            when (event.item?.type) {
                "agent_message" -> println("消息: ${event.item.text}")
                "command_execution" -> println("命令: ${event.item.command}")
            }
        }
        "turn.completed" -> println("完成! 使用: ${event.usage}")
    }
}
```

### 4.3 多模态输入（文本 + 图片）

```kotlin
import java.nio.file.Paths

val inputs = listOf(
    UserInput.Text("请描述这张图片中的内容:"),
    UserInput.LocalImage(Paths.get("/path/to/image.png")),
)

val result = session.run(inputs)
println(result.finalResponse)
```

### 4.4 结构化输出

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

val schema = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("summary") {
            put("type", "string")
        }
        putJsonObject("keywords") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "string")
            }
        }
    }
    putJsonArray("required") {
        add("summary")
        add("keywords")
    }
}

val result = session.run(
    "分析这段代码",
    TurnOptions(outputSchema = schema)
)

// result.finalResponse 将是符合 schema 的 JSON 字符串
```

### 4.5 取消执行

```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin

val cancellationJob = Job()

// 在另一个协程中 5 秒后取消
launch {
    delay(5000)
    cancellationJob.cancel()
}

val result = session.run(
    "执行一个长时间任务",
    TurnOptions(cancellation = cancellationJob)
)
```

### 4.6 恢复会话

```kotlin
// 保存会话 ID
val threadId = session.id

// 稍后恢复会话
val resumedSession = client.resumeThread(threadId!!)
val result = resumedSession.run("继续之前的任务")
```

### 4.7 Java 使用示例

```java
import com.asakii.codex.agent.sdk.*;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;

public class JavaExample {
    public static void main(String[] args) throws Exception {
        // 创建客户端
        CodexClientOptions options = new CodexClientOptions(
            null,
            null,
            System.getenv("CODEX_API_KEY"),
            null
        );
        CodexClient client = new CodexClient(options);

        // 启动会话
        ThreadOptions threadOptions = new ThreadOptions(
            "gpt-4o",
            SandboxMode.WORKSPACE_WRITE,
            null, false, null, null, null, null,
            java.util.Collections.emptyList()
        );
        CodexSession session = client.startThread(threadOptions);

        // 执行任务 (需要在协程上下文中)
        BuildersKt.runBlocking(Dispatchers.getDefault(), (scope, cont) -> {
            return session.run("列出文件", new TurnOptions(null, null), cont);
        });
    }
}
```

---

## 5. CLI 参数映射

SDK 方法参数与 Codex CLI 参数的对应关系：

| SDK 参数 | CLI 参数 | 说明 |
|---------|---------|------|
| `model` | `--model` | 模型名称 |
| `sandboxMode` | `--sandbox` | 沙盒模式 |
| `workingDirectory` | `--cd` | 工作目录 |
| `additionalDirectories` | `--add-dir` | 附加目录（可多个） |
| `skipGitRepoCheck` | `--skip-git-repo-check` | 跳过 Git 检查 |
| `outputSchemaFile` | `--output-schema` | 输出模式文件 |
| `modelReasoningEffort` | `--config model_reasoning_effort` | 推理强度 |
| `networkAccessEnabled` | `--config sandbox_workspace_write.network_access` | 网络访问 |
| `webSearchEnabled` | `--config features.web_search_request` | Web 搜索 |
| `approvalPolicy` | `--config approval_policy` | 审批策略 |
| `images` | `--image` | 图片路径（可多个） |
| `threadId` | `resume <id>` | 恢复会话 |

---

## 6. 错误处理

### 6.1 异常类型

| 异常类 | 说明 |
|-------|------|
| `CodexExecutionException` | CLI 进程执行失败（非零退出码） |
| `CodexTurnFailedException` | 回合执行失败（turn.failed 事件） |

### 6.2 错误处理示例

```kotlin
try {
    val result = session.run("执行任务")
    println(result.finalResponse)
} catch (e: CodexTurnFailedException) {
    println("回合失败: ${e.threadError.message}")
} catch (e: CodexExecutionException) {
    println("执行失败: ${e.message}")
}
```

---

## 7. 平台支持

SDK 自动检测平台并查找对应的 Codex 可执行文件：

| 平台 | 架构 | Target Triple |
|-----|------|---------------|
| Windows | x64 | `x86_64-pc-windows-msvc` |
| Windows | ARM64 | `aarch64-pc-windows-msvc` |
| macOS | x64 | `x86_64-apple-darwin` |
| macOS | ARM64 | `aarch64-apple-darwin` |
| Linux | x64 | `x86_64-unknown-linux-musl` |
| Linux | ARM64 | `aarch64-unknown-linux-musl` |

查找顺序：
1. 首先尝试从系统 PATH 查找 `codex` 命令
2. 回退到 `external/openai-codex/sdk/vendor/<triple>/codex/` 目录

---

## 8. 依赖项

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8+")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6+")
}
```

---

## 9. TODO / 未来计划

- [ ] 更多测试覆盖（特别是 CLI 参数与错误处理）
- [ ] 与 `claude-agent-sdk` 共享通用工具包
- [ ] 扩展到 WebSocket / HTTP 模式的 Codex API
- [ ] 添加更多 Java 友好的包装方法

---

## 10. 版本历史

| 版本 | 日期 | 变更 |
|-----|------|------|
| 0.1.0 | 2025-12 | 初始版本，完整实现官方 TypeScript SDK 功能 |
