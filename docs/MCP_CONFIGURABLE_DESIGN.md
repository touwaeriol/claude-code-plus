# MCP Configuration Page Design

## Overview

MCP (Model Context Protocol) 配置页面用于管理 Claude Code Plus 中的 MCP 服务器配置，包括内置服务器和自定义服务器。

**配置页面 ID**: `com.asakii.settings.mcp`
**显示名称**: MCP
**父级配置**: Claude Code Plus (`com.asakii.settings`)

---

## Page Structure

```
MCP Settings (List-based UI)
┌─────────────────────────────────────────────────────────────────┐
│ [+] [-] [Edit]                                                   │
├──────────┬──────────────────────┬─────────────────┬─────────────┤
│ Status   │ Name                 │ Configuration   │ Level       │
├──────────┼──────────────────────┼─────────────────┼─────────────┤
│ [✓]      │ User Interaction MCP │ Allows Claude...│ Built-in    │
│ [✓]      │ JetBrains IDE MCP    │ Code search...  │ Built-in    │
│ [ ]      │ Context7 MCP         │ Library docs... │ Built-in    │
│ [✓]      │ playwright           │ command: docker │ Global      │
│ [✓]      │ postgres             │ command: docker │ Project     │
└──────────┴──────────────────────┴─────────────────┴─────────────┘
```

- **Status**: 启用/禁用复选框（直接在表格中切换）
- **Name**: 服务器名称
- **Configuration**: 配置摘要
- **Level**: 服务器级别（Built-in/Global/Project）
- **双击**: 编辑服务器配置

---

## Built-in Servers

### User Interaction MCP

**功能**: 允许 Claude 在对话中向用户提问并提供可选选项（AskUserQuestion 工具）

| 配置项 | 类型 | 说明 |
|--------|------|------|
| Enable | Checkbox | 启用/禁用此 MCP 服务器 |
| Appended System Prompt | TextArea | 追加的系统提示词，留空使用默认值 |
| Reset to Default | Button | 重置为默认提示词 |

**存储字段**:
- `AgentSettingsService.enableUserInteractionMcp` (Boolean)
- `AgentSettingsService.userInteractionInstructions` (String)

**默认值**:
- enabled: `true`
- instructions: `""` (使用 `McpDefaults.USER_INTERACTION_INSTRUCTIONS`)

---

### JetBrains IDE MCP

**功能**: 提供快速代码搜索、文件索引和符号查找（使用 IDE 内置索引）

**可用工具**:
- `mcp__jetbrains__DirectoryTree` - 项目目录结构
- `mcp__jetbrains__FileProblems` - 静态分析结果
- `mcp__jetbrains__FileIndex` - 文件/类/符号搜索
- `mcp__jetbrains__CodeSearch` - 代码内容搜索
- `mcp__jetbrains__FindUsages` - 查找引用
- `mcp__jetbrains__Rename` - 重命名重构

| 配置项 | 类型 | 说明 |
|--------|------|------|
| Enable | Checkbox | 启用/禁用此 MCP 服务器 |
| Appended System Prompt | TextArea | 追加的系统提示词，留空使用默认值 |
| Reset to Default | Button | 重置为默认提示词 |

**存储字段**:
- `AgentSettingsService.enableJetBrainsMcp` (Boolean)
- `AgentSettingsService.jetbrainsInstructions` (String)

**默认值**:
- enabled: `true`
- instructions: `""` (使用 `McpDefaults.JETBRAINS_INSTRUCTIONS`)

---

### Context7 MCP

**功能**: 获取任何库的最新文档（React、Vue、Ktor 等）

| 配置项 | 类型 | 说明 |
|--------|------|------|
| Enable | Checkbox | 启用/禁用此 MCP 服务器 |
| API Key | TextField | 可选的 API 密钥（用于更高的速率限制）|
| Appended System Prompt | TextArea | 追加的系统提示词，留空使用默认值 |
| Reset to Default | Button | 重置为默认提示词 |

**存储字段**:
- `AgentSettingsService.enableContext7Mcp` (Boolean)
- `AgentSettingsService.context7ApiKey` (String)
- `AgentSettingsService.context7Instructions` (String)

**默认值**:
- enabled: `false` (默认禁用)
- apiKey: `""`
- instructions: `""` (使用 `McpDefaults.CONTEXT7_INSTRUCTIONS`)

---

## Custom Servers

自定义 MCP 服务器配置，支持两个级别的配置层次。

### Configuration Levels

| 级别 | 作用范围 | 存储位置 |
|------|----------|----------|
| Global | 所有项目 | `McpSettingsService.userConfig` (应用级别) |
| Project | 当前项目 | `ProjectMcpSettingsService.config` (项目级别) |

**注意**: Project 级别配置仅在当前项目可见，其他项目看不到。

### Custom Server Dialog

| 配置项 | 类型 | 说明 |
|--------|------|------|
| Enable | Checkbox | 启用/禁用此 MCP 服务器 |
| JSON configuration | TextArea | MCP 服务器配置（JSON 格式）|
| Appended System Prompt | TextArea | 追加的系统提示词（可选）|
| Server level | Radio | Global（所有项目）或 Project（当前项目）|

### Storage Format (Internal)

存储格式将 MCP 配置与我们的元数据分开：

```json
{
  "server-name": {
    "config": {
      "command": "executable-path",
      "args": ["arg1", "arg2", "..."],
      "env": {
        "ENV_VAR_NAME": "value"
      }
    },
    "enabled": true,
    "instructions": "Optional appended system prompt..."
  }
}
```

- `config`: 纯净的 MCP 服务器配置（传递给 Claude CLI）
- `enabled`: 我们的元数据，控制是否启用
- `instructions`: 我们的元数据，追加的系统提示词

### User Input Format (Dialog)

用户在对话框中输入的是纯净的 MCP 配置：

```json
{
  "server-name": {
    "command": "executable-path",
    "args": ["arg1", "arg2", "..."]
  }
}
```

**注意**: 用户不需要关心 `enabled` 和 `instructions`，这些通过对话框的其他字段配置。

### Example Configurations

**Playwright MCP (stdio)**:
```json
{
  "playwright": {
    "command": "docker",
    "args": ["run", "-i", "--rm", "--init", "--label", "com.docker.compose.project=mcps", "--pull=always", "mcr.microsoft.com/playwright/mcp"]
  }
}
```

**Word MCP (stdio)**:
```json
{
  "word-mcp": {
    "command": "/opt/homebrew/bin/uvx",
    "args": ["--from", "office-word-mcp-server", "word_mcp_server"]
  }
}
```

**PostgreSQL MCP (stdio with env)**:
```json
{
  "postgres@host.docker.internal": {
    "command": "docker",
    "args": [
      "run", "-i", "--rm", "--label", "com.docker.compose.project=mcps",
      "-e", "DATABASE_URI",
      "crystaldba/postgres-mcp:latest",
      "--access-mode=unrestricted"
    ],
    "env": {
      "DATABASE_URI": "postgresql://username:password@host.docker.internal:5432/database"
    }
  }
}
```

**HTTP MCP Server**:
```json
{
  "my-http-mcp": {
    "type": "http",
    "url": "https://example.com/mcp"
  }
}
```

---

## Data Storage Services

### AgentSettingsService (Application Level)

存储内置 MCP 服务器的配置：

```kotlin
// MCP 服务器启用状态
var enableUserInteractionMcp: Boolean = true
var enableJetBrainsMcp: Boolean = true
var enableContext7Mcp: Boolean = false
var context7ApiKey: String = ""

// 自定义系统提示词（空字符串表示使用默认值）
var userInteractionInstructions: String = ""
var jetbrainsInstructions: String = ""
var context7Instructions: String = ""
```

### McpSettingsService (Application Level)

存储自定义 MCP 服务器配置：

```kotlin
data class State(
    var userConfig: String = "",   // User 级别配置
    var localConfig: String = ""   // Local 级别配置
)
```

### ProjectMcpSettingsService (Project Level)

存储项目级别的 MCP 配置：

```kotlin
data class State(
    var config: String = ""        // Project 级别配置
)
```

---

## UI Components

| 组件 | 用途 |
|------|------|
| `JBTabbedPane` | 主选项卡（Built-in/Custom）和内部选项卡（User/Local/Project）|
| `JBCheckBox` | 启用/禁用 MCP 服务器 |
| `JBTextField` | API Key 输入 |
| `JBTextArea` | 系统提示词和 JSON 配置输入 |
| `JBScrollPane` | 包裹文本区域实现滚动 |
| `JButton` | Reset to Default 按钮 |

---

## Lifecycle Methods

### createComponent()
1. 创建主面板和选项卡
2. 构建 Built-in Servers 面板
3. 构建 Custom Servers 面板
4. 调用 `reset()` 加载配置

### isModified()
比较 UI 组件值与存储值，判断是否有修改

### apply()
1. 保存 Built-in 服务器配置到 `AgentSettingsService`
2. 验证并保存 Custom 服务器 JSON 配置
3. 调用 `settings.notifyChange()` 通知监听器

### reset()
从 `AgentSettingsService` 和 `McpSettingsService` 加载配置到 UI

### disposeUIResources()
清理所有 UI 组件引用

---

## Validation

- JSON 配置使用 Jackson `ObjectMapper` 验证
- 空白字符串视为有效（不配置）
- 无效 JSON 抛出 `ConfigurationException`

---

## Default Values Source

默认提示词存储在 `McpDefaults` 对象中：
- `McpDefaults.USER_INTERACTION_INSTRUCTIONS`
- `McpDefaults.JETBRAINS_INSTRUCTIONS`
- `McpDefaults.CONTEXT7_INSTRUCTIONS`

---

## Configuration Usage (配置如何被使用)

### 数据流架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Settings UI (McpConfigurable)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │ User Inter.  │  │  JetBrains   │  │   Context7   │  + Custom Servers │
│  │    MCP       │  │     MCP      │  │     MCP      │                   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                   │
└─────────┼─────────────────┼─────────────────┼───────────────────────────┘
          │                 │                 │
          ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    AgentSettingsService (Application Level)              │
│  • enableUserInteractionMcp          • enableContext7Mcp                │
│  • enableJetBrainsMcp                • context7ApiKey                   │
│  • userInteractionInstructions       • context7Instructions             │
│  • jetbrainsInstructions                                                │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│               HttpServerProjectService.serviceConfigProvider             │
│  每次 Claude 会话连接时调用，获取最新配置                                  │
│                                                                         │
│  AiAgentServiceConfig {                                                 │
│    claude = ClaudeDefaults(                                             │
│      enableUserInteractionMcp = settings.enableUserInteractionMcp,      │
│      enableJetBrainsMcp = settings.enableJetBrainsMcp,                  │
│      enableContext7Mcp = settings.enableContext7Mcp,                    │
│      mcpServersConfig = loadMcpServersConfig(settings),                 │
│      mcpInstructions = loadMcpInstructions(settings),                   │
│      ...                                                                │
│    )                                                                    │
│  }                                                                      │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                AiAgentRpcServiceImpl.buildClaudeOverrides                │
│                                                                         │
│  根据配置注册 MCP 服务器到 Claude CLI:                                    │
│                                                                         │
│  if (defaults.enableUserInteractionMcp) {                               │
│      mcpServers["user_interaction"] = userInteractionServer             │
│  }                                                                      │
│                                                                         │
│  if (defaults.enableJetBrainsMcp) {                                     │
│      mcpServers["jetbrains"] = jetBrainsMcpServer                       │
│  }                                                                      │
│                                                                         │
│  // 添加外部 MCP 服务器（Context7、自定义等）                              │
│  for (mcpConfig in defaults.mcpServersConfig) { ... }                   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 内置 MCP 服务器的注册

#### User Interaction MCP

```kotlin
// AiAgentRpcServiceImpl.kt
if (defaults.enableUserInteractionMcp) {
    mcpServers["user_interaction"] = userInteractionServer
    // userInteractionServer 是 UserInteractionMcpServer 实例
}
```

**系统提示词注入**: 通过 `McpDefaults.USER_INTERACTION_INSTRUCTIONS` 或用户自定义值

#### JetBrains IDE MCP

```kotlin
// AiAgentRpcServiceImpl.kt
if (defaults.enableJetBrainsMcp) {
    jetBrainsMcpServerProvider.getServer()?.let { jetbrainsMcp ->
        mcpServers["jetbrains"] = jetbrainsMcp
        // jetbrainsMcp 是 JetBrainsMcpServerImpl 实例
    }
}
```

**系统提示词获取**:
```kotlin
// JetBrainsMcpServerImpl.kt
override fun getInstructions(): String {
    return AgentSettingsService.getInstance().effectiveJetbrainsInstructions
    // 如果用户未自定义，返回 McpDefaults.JETBRAINS_INSTRUCTIONS
}
```

#### Context7 MCP

```kotlin
// HttpServerProjectService.loadMcpServersConfig()
if (settings.enableContext7Mcp) {
    configs.add(McpServerConfig(
        name = "context7",
        type = "http",
        url = McpDefaults.Context7Server.URL,  // "https://mcp.context7.com/mcp"
        headers = mapOf("CONTEXT7_API_KEY" to apiKey),  // 如果有 API Key
    ))
}
```

**系统提示词加载**:
```kotlin
// HttpServerProjectService.loadMcpInstructions()
if (settings.enableContext7Mcp) {
    instructions.add(settings.effectiveContext7Instructions)
    // 如果用户未自定义，返回 McpDefaults.CONTEXT7_INSTRUCTIONS
}
```

### 自定义 MCP 服务器的处理

自定义服务器配置通过 `mcpServersConfig` 列表传递给 Claude CLI：

```kotlin
// AiAgentRpcServiceImpl.buildClaudeOverrides()
for (mcpConfig in defaults.mcpServersConfig) {
    if (!mcpConfig.enabled) continue

    val serverConfig = when (mcpConfig.type) {
        "http" -> mapOf(
            "type" to "http",
            "url" to mcpConfig.url,
            "headers" to mcpConfig.headers
        )
        "stdio" -> mapOf(
            "type" to "stdio",
            "command" to mcpConfig.command,
            "args" to mcpConfig.args,
            "env" to mcpConfig.env
        )
    }
    mcpServers[mcpConfig.name] = serverConfig
}
```

### 配置生效时机

| 操作 | 生效时机 |
|------|----------|
| 修改配置并点击 Apply | 立即保存到 `AgentSettingsService` |
| 新会话连接 | `serviceConfigProvider()` 获取最新配置 |
| 已有会话 | 不受影响，需要新建会话才能使用新配置 |

### 配置变更通知

```kotlin
// McpConfigurable.apply()
settings.notifyChange()  // 通知所有监听器配置已变更

// AgentSettingsService
private val changeListeners = mutableListOf<(AgentSettingsService) -> Unit>()

fun addChangeListener(listener: (AgentSettingsService) -> Unit)
fun removeChangeListener(listener: (AgentSettingsService) -> Unit)
fun notifyChange() {
    changeListeners.forEach { it(this) }
}
```

---

## Related Files

| 文件 | 作用 |
|------|------|
| `McpConfigurable.kt` | 配置页面 UI |
| `AgentSettingsService.kt` | 应用级别设置存储 |
| `McpSettingsService.kt` | 自定义 MCP 配置存储 |
| `McpDefaults.kt` | 默认值和 Schema 定义 |
| `HttpServerProjectService.kt` | 配置加载和传递 |
| `AiAgentRpcServiceImpl.kt` | MCP 服务器注册 |
| `JetBrainsMcpServerImpl.kt` | JetBrains MCP 实现 |
| `UserInteractionMcpServer.kt` | User Interaction MCP 实现 |
| `plugin.xml` | 配置页面注册 |
