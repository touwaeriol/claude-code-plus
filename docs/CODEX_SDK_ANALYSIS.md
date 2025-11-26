## Codex SDK 结构速记

> 基于 `external/openai-codex/sdk/typescript/src/*`（当前仓库未提供 Python 版本），以下为后续 Kotlin SDK 设计的参照。

### 入口层
- `Codex`  
  - 构造时注入 `CodexExec`（封装 CLI 调用）与 `CodexOptions`。  
  - `startThread(options)` / `resumeThread(id, options)` 会实例化 `Thread`，传入 `CodexExec`、全局 options、线程级 options。

### 线程会话
- `Thread`  
  - `runStreamed(input, turnOptions)` 返回 `AsyncGenerator<ThreadEvent>`，内部调用 `runStreamedInternal`。  
  - `run(input, turnOptions)` 消费 `runStreamedInternal`，累积 `ThreadItem`、记住 `turn.completed` 中的 `Usage`，若遇到 `turn.failed` 则抛错。  
  - 输入规范化：`normalizeInput` 支持纯文本或 `[ {type:"text"| "local_image", ...} ]`。  
  - 线程 ID 在首次收到 `thread.started` 事件后赋值，可用于 resume。  
  - 事件流由 `CodexExec.run` 提供，每行 JSON 解析成 `ThreadEvent`。

### CLI 执行器
- `CodexExec`  
  - 负责拼接 `codex exec --experimental-json ...` 命令行参数：模型、sandbox、工作目录、附加目录、schema、网络/搜索开关、审批策略等。  
  - 将输入 prompt 写入 stdin，逐行读取 stdout（JSONL），stderr 累积用于错误信息。  
  - 自动解析目标平台，定位随包分发的 `vendor/<triple>/codex/codex(.exe)`。  
  - 支持覆写 env（默认继承当前 `process.env`），并注入 `CODEX_API_KEY`、`OPENAI_BASE_URL` 等。

### 事件 & Item 模型
- `events.ts`：定义 `ThreadEvent` 联合类型，包括 `thread.started`, `turn.started|completed|failed`, `item.*`, `error`，以及 `Usage` & `ThreadError`。  
- `items.ts`：定义 `ThreadItem` 联合，覆盖 agent 消息、reasoning、命令执行、文件修改、MCP 调用、web 搜索、待办清单、错误等，并提供状态字段（如 `CommandExecutionStatus`、`PatchApplyStatus`、`McpToolCallStatus`）。

### 其他支撑
- `CodexOptions`：CLI 路径、Base URL、API Key、自定义 env。  
- `ThreadOptions`：模型、Sandbox 模式、工作目录、Git/网络/搜索配置、审批策略、附加目录。  
- `TurnOptions`：结构化输出 schema（JSON Schema）与 `AbortSignal`。  
- `createOutputSchemaFile`：用于将 JSON Schema 写入临时目录并在 turn 结束后清理解耦。  
- `items.ts` 使用 MCP SDK 类型 `ContentBlock` 以兼容工具输出。

### 对 Kotlin 版本的映射建议
1. **入口**：`CodexClient`（类似 `Codex`）+ `CodexSession`（类似 `Thread`），封装 `startThread`/`resumeThread`/`run`/`runStreamed`。  
2. **执行器**：`CodexExecProcess`（协程版 `ProcessBuilder` 封装）暴露 `run(args: CodexExecArgs): Flow<String>`；需要跨平台二进制定位 & env 注入。  
3. **数据模型**：使用 `sealed class CodexEvent`、`sealed class CodexItem`、`data class Usage` 等 Kotlinx Serialization 数据类，字段命名与 TS 保持对齐。  
4. **输入/输出**：定义 `UserInput.Text` / `UserInput.LocalImage`，以及 `RunResult`/`StreamedRun` DTO。  
5. **结构化输出**：提供 `OutputSchemaFile` 工具类，以 `Path` + `suspend fun cleanup()` 形式管理临时目录。  
6. **错误处理**：`TurnFailed`/`ThreadError` 映射为自定义异常，保留原始消息 & cause。  
7. **协程 API**：`suspend fun run(...)`, `fun runStreamed(...): Flow<CodexEvent>`，便于与 Kotlin 应用集成。

该分析可作为 `codex-agent-sdk` 模块的数据建模及 API 设计蓝本，并帮助识别可与 `claude-agent-sdk` 共享的基础设施（流程管理、JSON 序列化、工具调用抽象等）。




















