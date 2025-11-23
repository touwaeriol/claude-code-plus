# Claude Code Plus

一个现代化的 IntelliJ IDEA 插件，基于 Kotlin + Swing + IntelliJ JB UI 组件构建，通过集成 Claude Agent SDK 为开发者提供强大的 AI 智能编码助手功能。

## 快速开始

### 运行插件

1. 确保已安装 Python 🐍 3
2. 设置 Java 环境（推荐使用 GraalVM）：
   ```bash
   export JAVA_HOME=/path/to/your/jdk
   # 例如：export JAVA_HOME=$HOME/Library/Java/JavaVirtualMachines/graalvm-ce-17.0.9/Contents/Home
   ```
3. 运行插件：
   ```bash
   ./gradlew runIde
   ```
4. 在 IDEA 中打开右侧的 "ClaudeCode" 工具窗口
5. 开始与 Claude 对话

### 安装 Claude Code SDK

要使用真正的 Claude 功能，请安装 Claude Code SDK：

```bash
# 1. 安装 Claude Code CLI
npm install -g @anthropic-ai/claude-code
# 注意：安装后命令是 claude，而不是 claude-code

# 2. 登录 Claude
claude login

# 3. 安装 Python 🐍 SDK
pip install claude-code-sdk
```

安装完成后，运行插件即可自动使用 Claude Code SDK。如果未安装，插件会使用 Mock 模式进行测试。

详见 [Claude SDK 安装指南](CLAUDE_SDK_INSTALLATION.md)

## 项目概述

Claude Code Plus 是一个基于 Kotlin 开发的 IDEA 插件，它通过外部 Python 🐍 进程集成 Claude Code SDK，在 IDE 中提供类似 JetBrains AI Assistant 的对话界面。插件以会话形式与用户交互，后台通过 Claude Code SDK 进行代理，提供智能编码辅助功能。

## 主要功能 ✨

### 1. 现代化对话界面 💬
- 🎨 基于 Swing + IntelliJ JB UI 组件的原生界面
- 📝 完整的 Markdown 渲染支持（GFM 语法）
- ⚡ 实时流式响应显示
- 🎯 智能代码块高亮（使用 IDE 原生高亮器）

### 2. 智能上下文引用系统 🔗
- **@文件引用**：输入 `@` 时自动触发文件搜索和补全
- **智能搜索**：基于文件名、路径和内容的模糊搜索
- **多种引用类型**：
  - 📄 文件引用（支持行号范围）
  - 📁 文件夹引用
  - 🌐 URL 引用
  - 📋 代码片段引用
- **智能路径处理**：相对路径自动解析 + 路径验证
- **拖放支持**：直接拖拽文件到输入框

### 3. Claude Agent SDK 核心集成 🤖
- **Kotlin 原生实现**：无需外部 Python 🐍 进程，纯 Kotlin SDK
- **双向通信协议**：完整实现 Claude CLI 通信协议
- **MCP 服务器支持**：Model Context Protocol 集成
- **高级特性**：
  - 🔄 会话管理与状态持久化
  - 🎣 Hooks 事件系统
  - 🛠️ 自定义工具集成
  - ⚡ 流式响应处理
  - 🔒 权限管理系统

### 4. 增强功能
- 代码高亮显示
- 多会话管理
- 历史记录保存
- 快捷键支持

## 技术栈 🛠️

### 核心技术
- **开发语言**：Kotlin 1.9+
- **UI 框架**：Swing + IntelliJ JB UI 组件库（官方推荐）
  - 标准 Swing 组件：`JPanel`, `JButton`, `JLabel` 等
  - IntelliJ JB UI 组件：`JBTextArea`, `JBScrollPane`, `JBList` 等
  - IntelliJ UI 工具类：`JBUI`, `UIUtil` 等
  - 可选增强：Kotlin UI DSL（`com.intellij.ui.dsl.builder.*`）- 平台内置，无需额外依赖
- **IDE 平台**：IntelliJ Platform SDK 2024.3+
- **AI 集成**：Claude Agent SDK (Kotlin 原生实现)
- **构建工具**：Gradle 8.0+

### 架构特点
- ✅ 纯 Kotlin 实现，类型安全
- ✅ 响应式编程（StateFlow + Kotlin Flow）
- ✅ 协程并发，异步非阻塞
- ✅ 模块化设计，清晰分层
- ✅ 与 IntelliJ IDE 深度集成，原生用户体验

## 架构设计

### UI 框架选择

本项目使用 **Swing + IntelliJ JB UI 组件**（IntelliJ 插件开发官方推荐方案）：

- **为什么选择 Swing？**
  - IntelliJ Platform SDK 原生基于 Swing
  - 与 IDE 深度集成，提供一致的用户体验
  - 成熟稳定，有丰富的 IntelliJ UI 组件库支持
  - 官方推荐并广泛使用的插件 UI 方案

- **为什么不使用 Compose Multiplatform？**
  - 不在 IntelliJ 插件开发推荐方案中
  - 与 IntelliJ Platform 的集成尚未成熟
  - 可能导致兼容性问题和额外的复杂度

### 组件结构
```
jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/
├── ui/                              # UI 组件层（Swing）
│   ├── chat/                        # 主聊天界面
│   │   ├── ModernChatView.kt        # 主聊天面板
│   │   ├── ChatHeader.kt            # 聊天头部栏
│   │   ├── MessageListPanel.kt      # 消息列表面板
│   │   ├── ChatInputPanel.kt        # 输入组件面板
│   │   ├── SessionListOverlay.kt    # 会话列表覆盖层
│   │   └── components/              # 子组件
│   │       ├── UserMessageBubble.kt
│   │       ├── AssistantTextDisplay.kt
│   │       ├── ToolCallDisplay.kt
│   │       ├── ContextUsageIndicator.kt
│   │       └── StreamingStatusIndicator.kt
│   ├── tools/                       # 工具显示组件
│   │   ├── ReadToolDisplay.kt
│   │   ├── EditToolDisplay.kt
│   │   └── ... (20+ 工具组件)
│   ├── session/                     # 会话管理 UI
│   │   ├── SessionList.kt
│   │   ├── SessionListWithGroups.kt
│   │   └── SessionSearch.kt
│   ├── markdown/                    # Markdown 渲染
│   │   └── MarkdownRenderer.kt
│   ├── toast/                       # Toast 通知
│   │   └── ToastContainer.kt
│   └── settings/                    # 设置面板
│       └── SettingsPanel.kt
├── viewmodel/                       # 视图模型层
│   └── ChatViewModel.kt             # 聊天视图模型
├── service/                         # 服务层
│   ├── ClaudeService.kt             # Claude API 服务
│   ├── FileSearchService.kt         # 文件搜索服务
│   └── ThemeService.kt              # 主题服务
├── model/                           # 数据模型层
│   ├── SessionModels.kt
│   ├── MessageModels.kt
│   └── ToolModels.kt
└── tools/                           # IDE 工具抽象层
    ├── IdeTools.kt                  # IDE 工具接口
    └── IdeToolsImpl.kt              # IDE 工具实现
```

### 核心模块

1. **ModernChatView（主聊天界面）**
   - 基于 Swing + IntelliJ JB UI 组件的聊天界面
   - 使用 `JPanel` + `BorderLayout` 布局管理
   - 集成消息列表、输入框、头部栏等组件
   - 支持会话切换和错误处理

2. **ChatViewModel（视图模型）**
   - 管理聊天状态（会话、消息、工具调用）
   - 使用 `StateFlow` 实现响应式 UI 更新
   - 处理流式事件和工具调用状态
   - 协调服务层和 UI 层的交互

3. **ClaudeService（Claude API 服务）**
   - 管理 Claude Agent SDK 集成
   - 处理消息发送和流式响应接收
   - 实现 MCP（Model Context Protocol）支持
   - 管理会话生命周期和状态持久化

4. **IdeTools（IDE 工具抽象层）**
   - 统一的 IDE 工具接口
   - 封装文件操作、编辑器操作、Diff 显示等
   - 提供类型安全的 API
   - 支持测试和生产环境的不同实现

5. **消息显示组件**
   - UserMessageBubble：用户消息气泡
   - AssistantTextDisplay：助手文本显示（Markdown 渲染）
   - ToolCallDisplay：工具调用显示（20+ 种工具）
   - SystemMessageDisplay：系统消息显示

## 实现方式

### Python 🐍 SDK 集成
1. **claude_code_sdk_wrapper.py**：Python 🐍 脚本，封装 Claude Code SDK 的调用
2. **外部进程通信**：通过 JSON 协议在 Java 和 Python 🐍 进程间通信
3. **流式响应**：支持实时流式输出，提供更好的用户体验

### 会话管理
- 支持多会话并发处理
- 会话状态持久化
- 自动恢复中断的会话

### UI 交互
- 响应式状态管理：使用 StateFlow + SwingUtilities.invokeLater 确保线程安全
- Markdown 渲染：完整支持 GFM 语法，使用 CommonMark 库
- 主题适配：自动适配 IDE 的暗色/亮色主题
- 高 DPI 支持：使用 JBUI.scale() 适配高分辨率屏幕

## 使用说明

### 安装要求
- IntelliJ IDEA 2023.1 或更高版本
- JDK 17 或更高版本
- Python 🐍 3.10+
- Node.js 14.0+
- Claude Code CLI 和 SDK（见快速开始部分）

### 快捷键
- `Ctrl+Shift+C` (Windows/Linux) 或 `Cmd+Shift+C` (macOS)：打开对话窗口
- `@` + 文件名：触发文件补全
- `Esc`：关闭对话窗口

## 贡献指南

欢迎提交 Issue 和 Pull Request。开发前请确保：
1. 遵循 Kotlin 编码规范
2. 添加必要的测试
3. 更新相关文档

## 许可证

MIT License

## 致谢

- Claude Code 团队提供的优秀命令行工具
- JetBrains 提供的 IntelliJ Platform SDK 和完善的 UI 组件库

## 相关链接

- [Claude Code SDK Python 🐍](https://github.com/anthropics/claude-code-sdk-python) - 官方 Python 🐍 SDK
- [Claude Code CLI](https://github.com/anthropics/claude-code) - 官方命令行工具

## 架构设计原则

### 业务逻辑分离

**重要：**`toolwindow-test` 模块不应该包含任何业务逻辑，所有业务逻辑都应该在 `toolwindow` 模块中实现。

#### 模块职责划分

- **`toolwindow` 模块**：
  - 包含所有 UI 组件的完整实现
  - 包含与 Claude API 交互的业务逻辑
  - 包含消息处理、会话管理、错误处理等核心功能
  - 提供可复用的聊天应用组件 (`JewelChatApp`)

- **`toolwindow-test` 模块**：
  - **仅负责**：创建测试窗口和基本的测试环境
  - **仅使用**：`toolwindow` 模块提供的现成组件
  - **不包含**：任何业务逻辑、API 调用、消息处理等功能
  - **目的**：提供独立的测试环境，验证 UI 组件的功能

#### 示例对比

❌ **错误做法**（在测试应用中包含业务逻辑）：
```kotlin
// toolwindow-test 中不应该有这样的代码
fun sendMessage(...) {
    val response = cliWrapper.sendMessage(...)
    // 处理响应逻辑...
}
```

✅ **正确做法**（测试应用只使用现成组件）：
```kotlin
// toolwindow-test 中应该这样使用
JewelChatApp(
    cliWrapper = cliWrapper,
    workingDirectory = workingDirectory,
    themeProvider = themeProvider,
    showToolbar = true,
    onThemeChange = { newTheme -> /* 简单的主题切换 */ }
)
```

#### 好处

1. **代码复用**：业务逻辑只需要在一个地方实现
2. **测试简化**：测试应用专注于 UI 测试，不需要维护复杂的业务逻辑
3. **维护性**：修改业务逻辑时只需要修改 `toolwindow` 模块
4. **插件集成**：插件可以直接使用 `toolwindow` 模块的组件，无需重复实现

#### 主题配置

应用自动适配 IntelliJ IDE 的主题系统：

- **主题获取**：通过 `IdeTools.getTheme()` 获取当前 IDE 主题信息
- **颜色系统**：使用 `UIUtil.getPanelBackground()` 等工具方法获取主题颜色
- **自动适配**：UI 组件自动跟随 IDE 主题变化（暗色/亮色）
- **高 DPI 支持**：使用 `JBUI.scale()` 适配高分辨率屏幕

## 相关文档

- [架构迁移指南](docs/ARCHITECTURE_MIGRATION.md) - 从 Vue 到 Swing 的迁移详细说明
- [UI 框架选型说明](docs/UI_FRAMEWORK_DECISION.md) - 技术选型依据和对比
- [IDEA 原生 UI 与浏览器方案](docs/IDEA_NATIVE_UI_AND_BROWSER_PLAN.md) - 两种方案的对比和实施计划
