# Codex Agent SDK (Kotlin)

> Kotlin 版 Codex SDK，接口与 `external/openai-codex/sdk/typescript` 保持一致，便于 JVM 应用直接驱动 Codex CLI。

## 快速开始

```kotlin
val client = CodexClient(
    CodexClientOptions(
        apiKey = System.getenv("CODEX_API_KEY"),
    )
)

val session = client.startThread(
    ThreadOptions(
        model = "claude-3.5-sonnet",
        sandboxMode = SandboxMode.WORKSPACE_WRITE,
    )
)

val result = session.run("请列出项目 README 的要点")
println(result.finalResponse)
```

* `run`：执行一个完整回合，返回 Agent 响应、事件列表与 token 用量。
* `runStreamed`：返回 `Flow<ThreadEvent>`，可用于实时 UI。
* `ThreadOptions`：设置模型、沙箱、附加目录、审批策略等。
* `TurnOptions.outputSchema`：传入 JSON Schema，即可要求结构化输出。

## 架构

| 组件 | 说明 |
| --- | --- |
| `CodexClient` | SDK 入口，负责创建/恢复线程 |
| `CodexSession` | 线程上下文，封装 `run` 与 `runStreamed` |
| `CodexExecProcess` | 子进程执行器，封装 `codex exec --experimental-json` |
| `ThreadEvent` / `ThreadItem` | 对齐 TypeScript SDK 的事件 / 项目结构 |
| `UserInput` | 支持文本与本地图片输入 |

## TODO

- [ ] 更多测试覆盖（特别是 CLI 参数与错误处理）
- [ ] 与 `claude-agent-sdk` 共享通用工具包
- [ ] 扩展到 WebSocket / HTTP 模式的 Codex API




















