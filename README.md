# Claude Code Plus

一个 IntelliJ IDEA 插件，通过集成 Claude Code Python SDK 为开发者提供智能编码助手功能。

## 快速开始

### 运行插件

1. 确保已安装 Python 3
2. 设置 Java 环境（推荐使用 GraalVM）：
   ```bash
   export JAVA_HOME=/path/to/your/jdk
   # 例如：export JAVA_HOME=/Users/erio/Library/Java/JavaVirtualMachines/graalvm-ce-17.0.9/Contents/Home
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

# 3. 安装 Python SDK
pip install claude-code-sdk
```

安装完成后，运行插件即可自动使用 Claude Code SDK。如果未安装，插件会使用 Mock 模式进行测试。

详见 [Claude SDK 安装指南](CLAUDE_SDK_INSTALLATION.md)

## 项目概述

Claude Code Plus 是一个基于 Kotlin 开发的 IDEA 插件，它通过外部 Python 进程集成 Claude Code SDK，在 IDE 中提供类似 JetBrains AI Assistant 的对话界面。插件以会话形式与用户交互，后台通过 Claude Code SDK 进行代理，提供智能编码辅助功能。

## 主要功能

### 1. 对话界面
- 提供类似 JetBrains AI Assistant 的浮动对话窗口
- 支持 Markdown 渲染的对话历史
- 实时显示 Claude Code 的响应

### 2. 智能文件引用
- **@文件引用**：输入 `@` 时自动触发文件搜索和补全
- **智能搜索**：基于文件名、路径和内容的模糊搜索
- **路径转换**：自动将相对路径转换为绝对路径
- **预览支持**：悬停显示文件内容预览

### 3. Claude Code SDK 集成
- 通过外部 Python 进程集成 Claude Code SDK
- 会话管理：支持多会话并发处理
- 双向通信：通过 JSON 协议进行进程间通信
- 错误处理和自动重连机制

### 4. 增强功能
- 代码高亮显示
- 多会话管理
- 历史记录保存
- 快捷键支持

## 技术栈

- **语言**：Kotlin, Python
- **UI 框架**：Swing (IDEA Platform)
- **IDE 平台**：IntelliJ Platform SDK
- **SDK 集成**：Claude Code SDK
- **构建工具**：Gradle
- **最低 IDE 版本**：2023.1
- **Python 版本**：3.10+

## 架构设计

### 组件结构
```
claude-code-plus/
├── src/main/kotlin/
│   ├── com/claudecodeplus/
│   │   ├── ui/              # UI 组件
│   │   │   ├── ChatWindow.kt
│   │   │   ├── MessageList.kt
│   │   │   ├── AdaptiveInputField.kt
│   │   │   ├── SelectionPanel.kt
│   │   │   └── EnhancedChatViewModel.kt
│   │   ├── core/            # 核心功能
│   │   │   ├── ClaudeSession.kt
│   │   │   ├── GraalPythonSession.kt
│   │   │   ├── ClaudeCodeSession.kt
│   │   │   └── GraalPythonClaudeWrapper.kt
│   │   ├── service/         # 后台服务
│   │   │   └── ClaudeCodeService.kt
│   │   └── model/           # 数据模型
│   │       └── SessionModels.kt
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml
│       └── icons/
├── claude-sdk-wrapper/      # Python SDK 包装器
│   ├── claude_sdk_wrapper/
│   │   ├── __init__.py
│   │   ├── wrapper.py
│   │   └── models.py
│   └── setup.py
└── build.gradle.kts
```

### 核心模块

1. **ExternalPythonSession**
   - 管理外部 Python 进程的生命周期
   - 通过 JSON 协议进行进程间通信
   - 处理消息的发送和接收

2. **ClaudeSession 接口**
   - 定义会话的标准操作接口
   - 支持多种实现方式（GraalVM、外部进程等）
   - 管理会话生命周期和消息历史

3. **ChatWindow**
   - 基于 Swing 的聊天界面
   - 支持 ANSI 样式渲染
   - 自适应输入模式（文本/选择）
   - 处理用户输入和显示流式响应

4. **ClaudeCodeService**
   - 作为 IDE 应用级服务运行
   - 管理多个会话实例
   - 协调各组件之间的通信
   - 提供全局配置管理

## 实现方式

### Python SDK 集成
1. **claude_code_sdk_wrapper.py**：Python 脚本，封装 Claude Code SDK 的调用
2. **外部进程通信**：通过 JSON 协议在 Java 和 Python 进程间通信
3. **流式响应**：支持实时流式输出，提供更好的用户体验

### 会话管理
- 支持多会话并发处理
- 会话状态持久化
- 自动恢复中断的会话

### UI 交互
- 自适应输入框：根据 Claude 响应自动切换文本/选择模式
- ANSI 样式支持：保留 Claude 输出的终端样式
- 浮动选择面板：优雅处理多选交互

## 使用说明

### 安装要求
- IntelliJ IDEA 2023.1 或更高版本
- JDK 17 或更高版本
- Python 3.10+
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
- JetBrains 提供的 IntelliJ Platform SDK 和 Compose Multiplatform

## 相关链接

- [Claude Code SDK Python](https://github.com/anthropics/claude-code-sdk-python) - 官方 Python SDK
- [Claude Code CLI](https://github.com/anthropics/claude-code) - 官方命令行工具
