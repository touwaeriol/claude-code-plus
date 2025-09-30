# Claude Agent SDK 更新分析报告

**日期**: 2025-09-30
**官方版本**: v0.1.0 (Python)
**本地版本**: 基于 v0.0.x (Kotlin)

## 📋 执行摘要

官方 SDK 从 `claude-code-sdk` 重命名为 `claude-agent-sdk`，进行了重大架构升级（v0.1.0）。主要变更包括类型重命名、系统提示配置简化、设置隔离、以及新增编程式子代理和会话分叉功能。

## 🔄 重大破坏性变更 (Breaking Changes)

### 1. 核心类型重命名

| Python SDK (v0.1.0) | 我们的 Kotlin SDK | 状态 | 优先级 |
|---------------------|------------------|------|--------|
| `ClaudeAgentOptions` | `ClaudeCodeOptions` | ❌ 需要重命名 | **P0** |
| `claude-agent-sdk` (包名) | `claude-code-sdk` | ❌ 需要重命名 | **P0** |

**影响**: 所有使用 `ClaudeCodeOptions` 的代码需要更新

### 2. 系统提示配置变更

#### Python SDK v0.1.0 (新)
```python
# 单一字段，支持字符串或预设
system_prompt: str | SystemPromptPreset | None = None

# SystemPromptPreset 结构
class SystemPromptPreset(TypedDict):
    type: Literal["preset"]
    preset: Literal["claude_code"]
    append: NotRequired[str]
```

#### 我们的 Kotlin SDK (旧)
```kotlin
// 两个独立字段
val systemPrompt: String? = null
val appendSystemPrompt: String? = null
```

**变更要求**:
- ✅ 已有 `ThinkingBlock` - 无需添加
- ❌ 缺少 `SystemPromptPreset` 类型
- ❌ 需要合并 `systemPrompt` 和 `appendSystemPrompt` 为单一字段

### 3. 默认行为变更

#### Python SDK v0.1.0
- **无默认系统提示**: 需要显式指定
- **无默认设置加载**: 不自动读取 `settings.json`, `CLAUDE.md`
- **无默认子代理**: 不自动加载斜杠命令

#### 我们的 Kotlin SDK (当前)
- 可能依赖默认行为
- 需要确认是否有隐式依赖

**影响**: 需要显式配置才能获得 Claude Code 行为

## 🆕 新增功能

### 1. 编程式子代理 (Programmatic Agents)

#### Python SDK v0.1.0
```python
@dataclass
class AgentDefinition:
    description: str
    prompt: str
    tools: list[str] | None = None
    model: Literal["sonnet", "opus", "haiku", "inherit"] | None = None

# 在选项中使用
ClaudeAgentOptions(
    agents: dict[str, AgentDefinition] | None = None
)
```

#### 我们的 Kotlin SDK
- ❌ **缺失**: 完全没有 `AgentDefinition` 类型
- ❌ **缺失**: `agents` 字段

**优先级**: **P1** - 重要新功能

### 2. 设置源控制 (Setting Sources)

#### Python SDK v0.1.0
```python
SettingSource = Literal["user", "project", "local"]

ClaudeAgentOptions(
    setting_sources: list[SettingSource] | None = None
)
```

#### 我们的 Kotlin SDK
- ❌ **缺失**: `SettingSource` 类型
- ❌ **缺失**: `setting_sources` 字段

**优先级**: **P1** - 重要的隔离控制

### 3. 会话分叉 (Session Forking)

#### Python SDK v0.1.0
```python
ClaudeAgentOptions(
    fork_session: bool = False  # 恢复会话时创建新分支
)
```

#### 我们的 Kotlin SDK
- ❌ **缺失**: `fork_session` 字段

**优先级**: **P2** - 有用但非关键

### 4. 部分消息流 (Partial Message Streaming)

#### Python SDK v0.1.0
```python
ClaudeAgentOptions(
    include_partial_messages: bool = False  # 启用流式部分消息
)

@dataclass
class StreamEvent:
    uuid: str
    session_id: str
    event: dict[str, Any]  # 原始 Anthropic API 流事件
    parent_tool_use_id: str | None = None

Message = UserMessage | AssistantMessage | SystemMessage | ResultMessage | StreamEvent
```

#### 我们的 Kotlin SDK
- ❌ **缺失**: `include_partial_messages` 字段
- ❌ **缺失**: `StreamEvent` 消息类型

**优先级**: **P1** - 提升用户体验的关键功能

### 5. stderr 回调

#### Python SDK v0.1.0
```python
ClaudeAgentOptions(
    debug_stderr: Any = sys.stderr,  # 已弃用
    stderr: Callable[[str], None] | None = None  # 新的回调方式
)
```

#### 我们的 Kotlin SDK
```kotlin
val debugStderr: Any? = null  // 仅有旧方式
```

**优先级**: **P2** - 改进调试体验

### 6. 自定义传输层

#### Python SDK v0.1.0
- 支持通过 `ClaudeSDKClient` 注入自定义传输层
- 见 PR #187

#### 我们的 Kotlin SDK
- ✅ 已有 `Transport` 接口
- ✅ 已有 `SubprocessTransport` 实现
- ⚠️ 需要确认是否支持自定义注入

**优先级**: **P2** - 高级功能

## 📊 类型完整性对比

### 内容块类型

| 类型 | Python SDK | Kotlin SDK | 状态 |
|------|-----------|------------|------|
| `TextBlock` | ✅ | ✅ | ✅ 完整 |
| `ThinkingBlock` | ✅ | ✅ | ✅ 完整 |
| `ToolUseBlock` | ✅ | ✅ | ✅ 完整 |
| `ToolResultBlock` | ✅ | ✅ | ✅ 完整 |

### 消息类型

| 类型 | Python SDK | Kotlin SDK | 状态 |
|------|-----------|------------|------|
| `UserMessage` | ✅ | ✅ | ✅ 完整 |
| `AssistantMessage` | ✅ | ✅ | ✅ 完整 |
| `SystemMessage` | ✅ | ✅ | ✅ 完整 |
| `ResultMessage` | ✅ | ✅ | ✅ 完整 |
| `StreamEvent` | ✅ | ❌ | ❌ 缺失 |

### Hook 类型

| 类型 | Python SDK | Kotlin SDK | 状态 |
|------|-----------|------------|------|
| `HookEvent` | ✅ (Literal) | ✅ (Enum) | ✅ 完整 |
| `HookCallback` | ✅ | ✅ | ✅ 完整 |
| `HookMatcher` | ✅ | ✅ | ✅ 完整 |
| `HookContext` | ✅ | ✅ | ✅ 完整 |
| `HookJSONOutput` | ✅ | ✅ | ✅ 完整 |

### 权限类型

| 类型 | Python SDK | Kotlin SDK | 需要确认 |
|------|-----------|------------|----------|
| `PermissionMode` | ✅ | ✅ | ✅ |
| `PermissionUpdate` | ✅ | ⚠️ | 需要检查 |
| `PermissionResult` | ✅ | ⚠️ | 需要检查 |
| `CanUseTool` | ✅ | ✅ | ✅ |

## 🎯 优先级任务清单

### P0 - 关键破坏性变更 (必须完成)

- [ ] 重命名 `ClaudeCodeOptions` → `ClaudeAgentOptions`
- [ ] 添加 `SystemPromptPreset` 数据类
- [ ] 合并 `systemPrompt` 和 `appendSystemPrompt` 为单一字段
  ```kotlin
  val systemPrompt: SystemPromptOrString? = null
  // where SystemPromptOrString = String | SystemPromptPreset
  ```

### P1 - 重要新功能 (强烈推荐)

- [ ] 添加 `AgentDefinition` 数据类
- [ ] 添加 `agents: Map<String, AgentDefinition>?` 字段
- [ ] 添加 `SettingSource` 枚举
- [ ] 添加 `setting_sources: List<SettingSource>?` 字段
- [ ] 添加 `StreamEvent` 消息类型
- [ ] 添加 `include_partial_messages: Boolean` 字段

### P2 - 增强功能 (建议完成)

- [ ] 添加 `fork_session: Boolean` 字段
- [ ] 添加 `stderr: ((String) -> Unit)?` 回调字段
- [ ] 标记 `debugStderr` 为 `@Deprecated`
- [ ] 验证自定义传输层注入支持

### P3 - 文档和示例

- [ ] 更新 CLAUDE.md 说明 v0.1.0 变更
- [ ] 添加迁移指南
- [ ] 更新示例代码
- [ ] 添加新功能示例

## 📝 实现建议

### 1. 类型定义更新 (Options.kt)

```kotlin
// 系统提示预设
data class SystemPromptPreset(
    val type: String = "preset",
    val preset: String = "claude_code",
    val append: String? = null
)

// 代理定义
data class AgentDefinition(
    val description: String,
    val prompt: String,
    val tools: List<String>? = null,
    val model: String? = null  // "sonnet" | "opus" | "haiku" | "inherit"
)

// 设置源
enum class SettingSource {
    USER, PROJECT, LOCAL
}

// 重命名选项类
data class ClaudeAgentOptions(
    // 工具配置
    val allowedTools: List<String> = emptyList(),
    val disallowedTools: List<String> = emptyList(),

    // 系统提示 - 新的统一字段
    val systemPrompt: Any? = null,  // String | SystemPromptPreset

    // 代理配置 - 新增
    val agents: Map<String, AgentDefinition>? = null,

    // 设置控制 - 新增
    val settingSources: List<SettingSource>? = null,

    // 会话控制
    val continueConversation: Boolean = false,
    val resume: String? = null,
    val forkSession: Boolean = false,  // 新增
    val maxTurns: Int? = null,

    // 流式配置 - 新增
    val includePartialMessages: Boolean = false,

    // MCP 服务器
    val mcpServers: Map<String, Any> = emptyMap(),

    // 权限配置
    val permissionMode: PermissionMode? = null,
    val permissionPromptToolName: String? = null,
    val canUseTool: CanUseTool? = null,

    // 模型配置
    val model: String? = null,

    // 环境配置
    val cwd: Path? = null,
    val settings: String? = null,
    val addDirs: List<Path> = emptyList(),
    val env: Map<String, String> = emptyMap(),

    // Hook 配置
    val hooks: Map<HookEvent, List<HookMatcher>>? = null,

    // 调试配置
    @Deprecated("Use stderr callback instead")
    val debugStderr: Any? = null,
    val stderr: ((String) -> Unit)? = null,  // 新增

    // 其他配置
    val extraArgs: Map<String, String?> = emptyMap(),
    val user: String? = null,
    val maxBufferSize: Int? = null
)
```

### 2. 消息类型更新 (Messages.kt)

```kotlin
@Serializable
@SerialName("stream_event")
data class StreamEvent(
    val uuid: String,
    @SerialName("session_id")
    val sessionId: String,
    val event: JsonElement,  // 原始 Anthropic API 事件
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null
) : Message
```

### 3. 向后兼容性

```kotlin
// 提供兼容性别名和构建器
@Deprecated("Use ClaudeAgentOptions instead", ReplaceWith("ClaudeAgentOptions"))
typealias ClaudeCodeOptions = ClaudeAgentOptions

// 提供迁移辅助函数
fun ClaudeAgentOptions.withSystemPromptPreset(
    preset: String = "claude_code",
    append: String? = null
): ClaudeAgentOptions {
    return copy(
        systemPrompt = SystemPromptPreset(
            type = "preset",
            preset = preset,
            append = append
        )
    )
}
```

## 🔗 相关资源

- **官方文档**: https://docs.claude.com/en/api/agent-sdk/python
- **官方仓库**: https://github.com/anthropics/claude-agent-sdk-python
- **CHANGELOG**: https://github.com/anthropics/claude-agent-sdk-python/blob/main/CHANGELOG.md
- **迁移指南**: https://docs.claude.com/en/docs/claude-code/sdk/migration-guide

## 📅 建议时间表

### 第一阶段 (立即) - P0 任务
- 完成核心类型重命名
- 保持向后兼容性（别名）

### 第二阶段 (本周) - P1 任务
- 实现新功能类型
- 更新 SDK 客户端支持新功能

### 第三阶段 (下周) - P2-P3 任务
- 完善增强功能
- 更新文档和示例

## ⚠️ 风险提示

1. **破坏性变更**: 类型重命名会影响所有现有代码
2. **默认行为**: 新版本不再自动加载设置，需要显式配置
3. **测试覆盖**: 所有变更需要充分测试
4. **文档更新**: 必须同步更新所有文档

## ✅ 验证检查清单

- [ ] 所有类型定义与 Python SDK 一致
- [ ] 向后兼容性别名正常工作
- [ ] 单元测试全部通过
- [ ] 集成测试覆盖新功能
- [ ] 文档已更新
- [ ] 示例代码可运行
- [ ] CHANGELOG 已更新