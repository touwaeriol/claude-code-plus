当你修改此文档时，必须修改 [CLAUDE.md](CLAUDE.md) 保证两个文件内容一致

# Claude Code Plus 项目文档索引

本文件提供项目内主要文档和配置文件的索引
开发时自行阅读文档，基于文档完成工作
你需要在我提出对应的修改要求时**自动更新**相关文档



## 组件库

编写 compose ui 代码时，先检查 @.claude/rules/jewel-component-index.md 是否满足我们的需求
如果需要详细阅读某个组件的具体使用方式，到 @.claude/rules/jewel-components.md 中阅读详细说明
只要jewel组件库中有，必须使用 jewel组件来实现功能，实验性质的组件也使用

### 组件使用优先级

1. **优先使用标准Jewel组件** - Button, Text, TextField等
2. **避免自定义实现** - 如果Jewel有对应组件，不要用Box+clickable
3. **实验性组件必须使用** - LazyTree, EditableComboBox等即使需要@ExperimentalJewelApi也必须使用
4. **主题系统统一** - 使用JewelTheme.globalColors而非硬编码颜色

### 常用组件速查

* 按钮: Button, IconButton, IconActionButton  
* 输入: TextField, TextArea, ComboBox
* 布局: ScrollableContainer, Divider, SplitLayout
* 弹窗: Popup, PopupContainer, Tooltip
* 进度: HorizontalProgressBar(确定进度), CircularProgressIndicator(旋转动画)

## Claude Agent SDK 模块

### SDK 参考资源

- **官方文档**: [Claude Agent SDK - Python](https://docs.claude.com/en/api/agent-sdk/python)
- **源代码仓库**: [claude-agent-sdk-python](https://github.com/anthropics/claude-agent-sdk-python)
- **本地源码**: `/Users/erio/codes/python/claude-agent-sdk-python`
- **最新版本**: v0.1.0 (2025-09-30 更新)
- **包名**: `claude-agent-sdk` (原 `claude-code-sdk`)
- **更新分析**: 详见 [SDK_UPDATE_ANALYSIS.md](./SDK_UPDATE_ANALYSIS.md)

### SDK 架构概览

claude-code-sdk 是一个独立的 Kotlin 模块，提供与 Claude CLI 进行双向交互的完整功能。本实现参考官方 Python Agent SDK (v0.1.0) 设计，提供 Kotlin 原生实现。

### 最新更新状态 (2025-09-30)

#### 官方 v0.1.0 主要变更
1. **重命名**: `claude-code-sdk` → `claude-agent-sdk`
2. **类型重命名**: `ClaudeCodeOptions` → `ClaudeAgentOptions`
3. **系统提示简化**: 合并 `custom_system_prompt` 和 `append_system_prompt` 为单一 `system_prompt` 字段
4. **设置隔离**: 默认不加载文件系统设置，需要显式配置 `setting_sources`
5. **新功能**:
   - 编程式子代理 (`agents`)
   - 会话分叉 (`fork_session`)
   - 部分消息流 (`include_partial_messages`, `StreamEvent`)
   - 细粒度设置控制 (`setting_sources`)

#### 我们的 Kotlin SDK 需要更新的内容

**P0 - 关键破坏性变更**:
- [ ] 重命名 `ClaudeCodeOptions` → `ClaudeAgentOptions`
- [ ] 添加 `SystemPromptPreset` 类型
- [ ] 合并系统提示字段

**P1 - 重要新功能**:
- [ ] 添加 `AgentDefinition` 和 `agents` 字段
- [ ] 添加 `SettingSource` 和 `setting_sources` 字段
- [ ] 添加 `StreamEvent` 消息类型
- [ ] 添加 `include_partial_messages` 字段

**P2 - 增强功能**:
- [ ] 添加 `fork_session` 字段
- [ ] 添加 `stderr` 回调，标记 `debugStderr` 为弃用

详细的更新分析、优先级规划和实现建议请查看 [SDK_UPDATE_ANALYSIS.md](./SDK_UPDATE_ANALYSIS.md)。

#### 核心组件

1. **ClaudeCodeSdkClient** (`claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/ClaudeCodeSdkClient.kt`)
   - 主要客户端类，管理与 Claude 的所有交互
   - 支持连接管理、消息发送、响应接收
   - 提供简单查询和流式交互两种模式
   - 关键方法：`connect()`, `query()`, `receiveResponse()`, `interrupt()`, `disconnect()`

2. **SubprocessTransport** (`claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/transport/SubprocessTransport.kt`)
   - 传输层实现，通过子进程与 Claude CLI 通信
   - 处理进程启动、I/O 流管理、错误处理
   - 支持 Windows/Unix 跨平台运行
   - 自动处理 stream-json 模式的消息流

3. **ControlProtocol** (`claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/protocol/ControlProtocol.kt`)
   - 控制协议处理器，管理双向通信协议
   - 路由消息到正确的处理器
   - 支持 Hook 回调、MCP 服务器、权限请求
   - 管理请求/响应的异步处理

4. **MessageParser** (`claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/protocol/MessageParser.kt`)
   - 解析来自 Claude CLI 的 JSON 流消息
   - 将 JSON 转换为强类型的消息对象
   - 支持所有 Claude 消息类型：User、Assistant、System、Result

### 关键功能特性

#### 1. 双向通信模式
```kotlin
// 简单查询模式 - 一次性请求/响应
val messages = client.simpleQuery("Hello Claude")

// 流式交互模式 - 持续对话
client.connect()
client.query("Tell me a story")
client.receiveResponse().collect { message ->
    // 处理每个消息片段
}
```

#### 2. MCP (Model Context Protocol) 服务器支持
- **McpServer 接口** (`claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/mcp/McpServer.kt`)
  - 定义标准 MCP 服务器接口
  - 支持工具列表查询 `listTools()`
  - 支持工具调用执行 `callTool()`
- **McpServerBase** - 提供基础实现和工具注册
- 支持三种服务器类型：stdio、sse、http

#### 3. 消息类型系统
- **Messages.kt** - 定义所有消息类型
  - UserMessage - 用户输入
  - AssistantMessage - Claude 响应
  - SystemMessage - 系统元数据
  - ResultMessage - 会话结果和统计
- **ContentBlocks.kt** - 内容块类型
  - TextBlock - 文本内容
  - ToolUseBlock - 工具调用
  - ToolResultBlock - 工具执行结果

#### 4. 配置选项 (ClaudeCodeOptions)
```kotlin
ClaudeCodeOptions(
    model = "claude-3-5-sonnet",          // 模型选择
    allowedTools = listOf("Read", "Write"), // 允许的工具
    systemPrompt = "You are helpful",      // 系统提示
    mcpServers = mapOf(...),              // MCP 服务器配置
    permissionMode = PermissionMode.AUTO,  // 权限模式
    maxTurns = 10,                        // 最大对话轮次
    cwd = Path.of("/project"),            // 工作目录
    hooks = mapOf(...)                    // 事件钩子
)
```

### 使用模式和最佳实践

#### 1. 基本使用流程
```kotlin
// 必须按顺序：连接 -> 查询 -> 接收响应 -> 断开
val client = ClaudeCodeSdkClient(options)
client.connect()
client.query("Your question")
val responses = client.receiveResponse().toList()
client.disconnect()
```

#### 2. 自动资源管理
```kotlin
// 使用 use 函数自动管理连接生命周期
client.use {
    query("Question")
    receiveResponse().collect { ... }
}
```

#### 3. 响应流特性
- **自动完成**: ResultMessage 后流自动结束
- **可中断**: 支持 `interrupt()` 中断当前操作
- **错误处理**: 通过 ResultMessage.isError 判断错误

#### 4. MCP 服务器集成
```kotlin
// 创建自定义 MCP 服务器
class MyMcpServer : McpServerBase("my-server") {
    init {
        registerTool("my_tool") { args ->
            // 工具实现
        }
    }
}

// 注册到选项中
val options = ClaudeCodeOptions(
    mcpServers = mapOf("my-server" to MyMcpServer())
)
```

### 与 IntelliJ 插件集成

#### 服务层集成
- **ProjectSessionStateService** - 项目级会话状态管理
- **SessionStateSyncImpl** - 会话状态同步实现
- **ClaudeCodePlusBackgroundService** - 后台服务管理

#### 关键集成点
1. SDK 作为独立模块，通过 Gradle 依赖引入
2. 插件通过服务层包装 SDK 功能
3. 支持多项目多会话管理
4. 提供会话状态持久化

### 开发注意事项

1. **连接管理**
   - 必须先 `connect()` 才能发送消息
   - 使用 `isConnected()` 检查连接状态
   - 记得调用 `disconnect()` 释放资源

2. **消息处理**
   - receiveResponse() 返回 Flow，支持协程
   - 每个响应以 ResultMessage 结束
   - 支持流式和批量两种处理方式

3. **错误处理**
   - 捕获 ClientNotConnectedException
   - 检查 ResultMessage.isError
   - Transport 层自动重试机制

4. **性能优化**
   - 使用流式处理避免内存占用
   - 支持消息批处理
   - 异步非阻塞设计

### 调试技巧

1. **日志输出**
   - SDK 使用 java.util.logging
   - 关键操作都有详细日志
   - 通过 debugStderr 参数输出调试信息

2. **消息追踪**
   - MessageParser 记录所有收到的消息
   - ControlProtocol 记录消息路由
   - Transport 层记录原始 I/O

3. **常见问题**
   - CLI 进程启动失败：检查 Claude CLI 是否安装
   - 连接超时：检查网络和 API 密钥
   - 消息解析错误：查看原始 JSON 输出

## Toolwindow 模块 - UI 层

### 模块架构概览

toolwindow 是 Claude Code Plus 的 UI 层，使用 Compose Desktop 和 Jewel UI 组件库构建现代化界面。

#### 核心架构设计

**三层架构**：
```
UI层 (Compose组件)
  ↓
会话层 (SessionManager/SessionObject)
  ↓
核心层 (Services/SDK集成)
```

### 主要组件

#### 1. 会话管理系统

**SessionObject** (`toolwindow/src/main/kotlin/com/claudecodeplus/ui/models/SessionObject.kt`)
- 单一数据源设计，管理会话状态
- 支持消息历史、工具调用、上下文引用
- 响应式状态更新（MutableState）

**SessionManager** (`toolwindow/src/main/kotlin/com/claudecodeplus/ui/session/SessionManager.kt`)
- 多会话生命周期管理
- 会话持久化和恢复
- 消息发送和接收协调

#### 2. UI 组件系统

**主界面组件**：
- `MainPanelV2` - 主面板，协调各子组件
- `ChatDisplay` - 对话显示区域
- `InputArea` - 输入区域，支持多行和上下文引用
- `SessionTabBar` - 会话标签栏

**消息显示组件**：
- `AssistantMessageDisplay` - AI 回复显示
- `UserMessageDisplay` - 用户消息显示
- `SimpleMarkdownRenderer` - Markdown 渲染器
- `CodeBlockRenderer` - 代码块渲染，支持语法高亮

#### 3. 工具调用显示系统

**架构演进**：从字符串匹配到类型安全的专业化组件

**工具显示组件** (`toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/tools/`):
- `CompactToolCallDisplay` - 紧凑模式显示
- `ExpandedToolCallDisplay` - 展开模式显示
- 专业化显示器：
  - `ReadToolDisplay` - 文件读取
  - `EditToolDisplay` - 文件编辑
  - `WriteToolDisplay` - 文件写入
  - `BashToolDisplay` - 命令执行
  - `GrepToolDisplay` - 搜索操作
  - `TodoWriteDisplay` - 任务列表

#### 4. 消息转换系统

**SdkMessageConverter** (`toolwindow/src/main/kotlin/com/claudecodeplus/ui/services/SdkMessageConverter.kt`)
- SDK 消息类型到 UI 消息类型的转换
- 工具调用解析和类型识别
- 内容块处理和格式化

### 关键特性

#### 1. Markdown 渲染系统

**自定义渲染器特性**：
- 支持 GFM (GitHub Flavored Markdown)
- 代码块语法高亮（使用 IntelliJ 高亮器）
- 表格、列表、引用块渲染
- 与 Jewel 主题深度集成

#### 2. 上下文引用系统

**支持的引用类型**：
- 文件引用（支持行号范围）
- 文件夹引用
- URL 引用
- 代码片段引用

**智能路径处理**：
- 相对路径自动解析
- 路径验证和提示
- 拖放文件支持

#### 3. 性能优化

- **虚拟滚动**：LazyColumn 实现消息列表
- **缓存机制**：remember 缓存计算结果
- **分页加载**：大量消息时的性能保证
- **动画优化**：流畅的展开/折叠动画

### 依赖注入系统

**自定义 DI 容器** (`toolwindow/src/main/kotlin/com/claudecodeplus/ui/DependencyContainer.kt`)
```kotlin
object DependencyContainer {
    lateinit var projectService: ProjectService
    lateinit var ideIntegration: IdeIntegration
    lateinit var sessionStateSync: SessionStateSync
}
```

### 主题系统集成

**SwingBridgeTheme**：
- Swing 和 Compose 主题桥接
- 自动适配 IDE 主题变化
- 支持亮色/暗色模式切换

## JetBrains Plugin 模块 - 集成层

### 模块职责

jetbrains-plugin 是 IntelliJ IDEA 集成层，负责将 Claude Code Plus 功能无缝集成到 IDE 中。

### 核心组件

#### 1. 工具窗口工厂

**ClaudeCodePlusToolWindowFactory** (`jetbrains-plugin/src/main/kotlin/com/claudecodeplus/intellij/toolwindow/ClaudeCodePlusToolWindowFactory.kt`)
- 实现 `ToolWindowFactory` 接口
- DumbAware 设计（索引期间可用）
- 创建和管理 Compose UI 面板
- 主题变化监听和同步

#### 2. 适配器系统

**IdeaIdeIntegration** - IDE 功能适配：
- 文件操作（打开、跳转）
- 通知显示
- 设置对话框
- 国际化支持

**IdeaProjectServiceAdapter** - 项目服务适配：
- 文件索引服务
- 路径解析
- 项目上下文管理

#### 3. Action 系统

**用户操作 Actions**：
- `NewSessionAction` - 创建新会话
- `ChatInputActions` - 输入快捷键
  - Ctrl+U - 删除到行首
  - Shift+Enter - 插入换行
  - Ctrl+J - 备用换行

#### 4. IDEA 平台服务

**IdeaPlatformService** (`jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/services/IdeaPlatformService.kt`)

统一的 IDEA 平台操作封装类，提供所有与 IntelliJ IDEA 平台交互的功能：

**文件操作**：
- `openFile(filePath, line, column, selectContent, content)` - 在编辑器中打开文件并定位
- `findVirtualFile(filePath)` - 查找虚拟文件（支持绝对路径和相对路径）
- `refreshFile(filePath)` - 刷新文件系统并查找文件
- `showDiff(filePath, oldContent, newContent, title)` - 显示文件差异对比

**通知操作**：
- `showInfo(message)` - 显示信息通知
- `showWarning(message)` - 显示警告通知
- `showError(message)` - 显示错误通知

**设计优势**：
- ✅ 功能集中，易于维护和扩展
- ✅ 统一的错误处理和日志记录
- ✅ 支持异步操作，不阻塞 UI
- ✅ 便于测试（可以 mock 服务）
- ✅ 后续可扩展更多功能（文件重命名、移动、删除等）

**使用示例**：
```kotlin
val platformService = IdeaPlatformService(project)

// 打开文件
platformService.openFile(filePath = "/path/to/file.kt", line = 42)

// 显示diff
platformService.showDiff(
    filePath = "/path/to/file.kt",
    oldContent = "old text",
    newContent = "new text"
)

// 显示通知
platformService.showInfo("操作成功")
```

#### 5. 工具点击处理

**ToolClickManager** - 统一管理工具点击：
```kotlin
// 注册处理器
ToolClickManager.registerHandler("read", ReadToolHandler())
ToolClickManager.registerHandler("edit", EditToolHandler())
ToolClickManager.registerHandler("write", WriteToolHandler())
```

**专业化处理器**（所有处理器现在都使用 IdeaPlatformService）：
- `ReadToolHandler` - 使用平台服务打开文件并定位，支持内容选择
- `EditToolHandler` - 使用平台服务显示 diff 对比视图
- `WriteToolHandler` - 使用平台服务刷新并打开新文件

**重要改进**：
- ✅ 移除了 `status == SUCCESS` 的限制，任何状态的工具都可以点击
- ✅ 使用统一的 IdeaPlatformService，代码更简洁（从 300+ 行简化到 ~80 行）
- ✅ 更好的错误处理和用户反馈

#### 6. 服务层

**应用级服务**：
- `McpSettingsService` - MCP 配置管理
- `ClaudeCodePlusBackgroundService` - 后台任务

**项目级服务**：
- `ProjectSessionStateService` - 会话状态管理
- `SimpleFileIndexService` - 文件索引（降级支持）

### 配置管理

#### 三级配置系统

```
优先级：Project > Local > User
```

1. **用户级**：全局默认配置
2. **本地级**：机器特定配置
3. **项目级**：项目工作区配置

**McpSettingsService** - 配置持久化：
```kotlin
@State(
    name = "ClaudeCodePlusMcpSettings",
    storages = [Storage("claude-code-plus-mcp.xml")]
)
```

### 监听器系统

**ClaudeToolWindowListener**：
- 工具窗口显示/隐藏事件
- 会话状态恢复
- 协程作用域管理

### 插件配置（plugin.xml）

**关键配置**：
- 插件 ID：`com.claudecodeplus`
- 支持版本：IntelliJ IDEA 2024.3-2025.2
- 依赖：Platform、Markdown 插件

**扩展点实现**：
```xml
<extensions defaultExtensionNs="com.intellij">
    <toolWindow id="Claude Code Plus" anchor="right"/>
    <applicationConfigurable id="claude.code.plus.settings"/>
    <applicationService serviceImplementation="McpSettingsService"/>
    <projectService serviceImplementation="ProjectSessionStateService"/>
</extensions>
```

## 项目整体架构总结

### 模块间交互

```
用户交互 → jetbrains-plugin (适配层)
         ↓
    toolwindow (UI层)
         ↓
    claude-code-sdk (核心层)
         ↓
    Claude CLI → Claude API
```

### 关键设计模式

1. **适配器模式**：Platform API 封装
2. **单例模式**：服务和管理器
3. **观察者模式**：状态更新和事件监听
4. **工厂模式**：组件创建和初始化
5. **策略模式**：工具处理器系统

### 技术亮点

1. **类型安全**：全面使用 Kotlin 强类型
2. **响应式编程**：Compose State + Flow
3. **协程并发**：异步非阻塞设计
4. **模块化架构**：清晰的层次和边界
5. **性能优化**：多层次缓存和优化策略

### 开发建议

1. **修改 UI**：在 toolwindow 模块的 components 目录
2. **添加工具处理**：在 jetbrains-plugin 的 handlers 目录
3. **扩展 SDK 功能**：在 claude-code-sdk 的相应包
4. **配置管理**：通过 McpSettingsService
5. **调试技巧**：启用日志，使用 debugStderr 参数
