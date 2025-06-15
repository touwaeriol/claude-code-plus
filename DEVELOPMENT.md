# Claude Code Plus 开发进度

## 项目架构更新（2025-01-14）

### 技术方案变更
项目从原本的 PTY 终端仿真方案转换为基于 Claude Code Python SDK 的实现：

1. **原方案**：使用 pty4j 创建伪终端，运行 claude 命令行工具
2. **新方案**：通过 GraalVM Python 运行时集成 Claude Code Python SDK

### 变更原因
- SDK 方案提供更稳定的 API 接口
- 避免终端仿真的复杂性
- 更好的错误处理和状态管理
- 支持流式响应和更丰富的交互

## 当前实现状态

### 已完成的核心组件

1. **数据模型** (SessionModels.kt)
   - SessionConfig - 会话配置
   - SessionMessage - 消息模型
   - SessionState - 会话状态
   - ContentBlock - 内容块（文本、代码、ANSI、选择）

2. **会话接口** (ClaudeSession.kt)
   - 定义标准的会话操作接口
   - 支持初始化、发送消息、停止、终止等操作
   - 使用 Kotlin Flow 实现流式响应

3. **GraalVM 集成** (GraalPythonSession.kt)
   - 实现 ClaudeSession 接口
   - 使用 GraalVM Polyglot API 运行 Python 代码
   - 调用 claude-sdk-wrapper 进行 SDK 交互

4. **Python SDK 包装器** (claude-sdk-wrapper/)
   - 封装 Claude Code Python SDK
   - 提供简化的 API 接口
   - 处理消息格式转换

### UI 组件状态
- ChatWindow - 主聊天窗口（需要更新以适配新架构）
- MessageList - 消息列表（需要适配新的消息格式）
- AdaptiveInputField - 自适应输入框
- SelectionPanel - 选择面板（用于处理 Claude 的选择交互）

### 待完成任务
1. ~~实现 AnsiParser 和 SelectionModeDetector~~ ✅
2. ~~创建 ToolWindowFactory 配置插件入口~~ ✅
3. ~~完善 GraalPythonManager 初始化逻辑~~ ✅
4. 更新 UI 组件以适配新的会话模型（部分完成）
5. 添加插件配置界面
6. 测试插件基本功能

## 最新进展（2025-01-14）

### 已完成的实现

1. **基础工具类**
   - `AnsiParser` - ANSI 转义序列解析器，支持颜色和样式
   - `SelectionModeDetector` - 检测 Claude 输出中的选择模式
   - `GraalPythonManager` - GraalVM Python 环境管理器

2. **插件入口**
   - `ClaudeCodeToolWindowFactory` - 工具窗口工厂
   - 更新 `plugin.xml` 配置工具窗口

3. **UI 组件修复**
   - 修复 `ChatWindow` 的编译错误
   - 修复 `SelectionPanel` 的属性引用问题
   - 创建 `SimpleChatWindow` 简化版聊天窗口

4. **Python 代码缩进修复**
   - 修复 `GraalPythonManager` 中所有 Python 代码的缩进问题
   - 修复 `GraalPythonSession` 中的 Python 代码缩进
   - 更新了 wrapper 类名引用（ClaudeWrapper -> ClaudeSDKWrapper）

### 当前运行状态

✅ **插件已成功运行！**
- 插件可以在 IDEA 中加载
- 工具窗口 "ClaudeCode" 可以正常显示
- SimpleChatWindow 界面已创建
- GraalVM Python 环境可以初始化（需要安装 GraalVM）

### 已知问题

1. **GraalVM Python 环境**
   - 需要安装支持 Python 的 GraalVM
   - 可能需要配置 GRAALVM_HOME 环境变量

2. **Claude SDK 依赖**
   - 需要安装 `claudecode` Python 包
   - claude-sdk-wrapper 需要正确配置

### 下一步计划

1. **完善错误处理**：添加更友好的错误提示
2. **测试 Claude SDK 集成**：确保能正确调用 Claude API
3. **优化 UI 界面**：改进聊天界面的用户体验
4. **添加配置界面**：让用户可以配置 API Key 等参数

## 历史开发记录

### 第一阶段：PTY 代理实现（已废弃）

### 2025-01-13 更新
- [x] 创建基础 PTY 代理类 (ClaudeCodePtyProxy)
- [x] 实现基于 pty4j 的会话管理系统
- [x] 创建会话状态管理和事件系统

#### 实现的主要组件：
1. **PtySession** - 单个 PTY 会话的封装
   - 支持启动/停止会话
   - 发送输入和接收输出
   - 终端大小调整
   - 状态管理（IDLE, STARTING, RUNNING, STOPPING, STOPPED, ERROR）
   - 使用 Flow API 进行消息传递

2. **SessionManager** - 会话管理器
   - 管理多个会话
   - 活动会话切换
   - 会话生命周期管理
   - 事件通知系统

3. **SessionMessage** - 会话消息类型
   - Output - 输出文本
   - Error - 错误信息
   - StateChanged - 状态变更
   - ProcessExited - 进程退出

### 技术架构
- 使用 pty4j 创建真正的伪终端
- 基于 Kotlin 协程的异步 I/O 处理
- Flow API 用于消息传递
- 支持多会话管理

### 测试结果（2025-01-13）

#### 成功测试了与 Claude 的通信
1. **管道方式** - 使用 `echo '问题' | claude` 可以成功获取回复
2. **交互式会话** - 通过 ProcessBuilder 创建交互式会话，可以连续发送多个问题并获取回复
3. **关键发现**：
   - Claude 命令支持管道输入
   - 交互式会话需要正确处理输入输出流
   - 回复格式清晰，易于解析

#### 测试代码示例
```kotlin
// 成功的交互式会话
val process = ProcessBuilder("claude").start()
val writer = PrintWriter(process.outputStream, true)
writer.println("你是什么模型")
// 成功获得回复: "我是 Claude Opus 4（claude-opus-4-20250514）。"
```

### 当前状态（使用 pty4j 实现）

#### 核心实现
1. **ClaudeSession.kt** - 基于 pty4j 的完整 PTY 会话实现
   - 使用 pty4j 创建真正的伪终端
   - 支持终端大小调整
   - 完整的进程生命周期管理
   - 使用 Flow API 传递消息
   - 状态管理（IDLE, STARTING, RUNNING, STOPPING, STOPPED）

2. **SimpleClaudeSession.kt** - 简化版实现（用于对比测试）

#### 测试结果（2025-01-13 更新）
✅ **成功使用 pty4j 启动 Claude PTY 会话**
- 进程成功启动并获取 PID
- 收到完整的 Claude 欢迎界面
- 成功发送用户输入
- 正确处理 ANSI 转义序列（颜色、光标控制）

✅ **问题已解决！（2025-01-13 晚上更新）**
- **关键发现**：在 PTY 模式下需要发送 `\r`（回车符）而不是 `\n`（换行符）
- **原因**：终端中按下回车键实际发送的是 CR (Carriage Return, ASCII 13)
- **解决方案**：在 `sendInput()` 方法中使用 `\r` 结尾
- **测试结果**：成功接收到 Claude 的回复 "4"（对于输入 "2+2"）

#### 技术细节
- Unix/Linux 终端默认发送 `\r`，通过 `icrnl` 设置转换为 `\n`
- PTY 环境需要原始的 `\r` 字符来触发输入处理
- Claude 显示 "Doing..." 等处理状态，然后返回答案

### 下一步计划
1. ✅ ~~研究如何在 Java/Kotlin 中正确创建 PTY 环境~~ - 已完成，使用 `\r` 解决
2. ✅ ~~更新 `ClaudeSession.sendInput()` 方法，确保使用 `\r` 结尾~~ - 已完成（2025-01-13）
3. ✅ ~~创建 UI 界面来显示会话输出~~ - 已完成（2025-01-13）
4. 实现 ANSI 转义序列处理
5. 添加文件引用功能（@符号触发）
6. 优化输出解析，提取真正的回复内容（过滤掉 "Doing..." 等状态信息）
7. 解决协程版本冲突问题

### 最新更新（2025-01-13）
- ✅ 更新了 `ClaudeSession.sendInput()` 方法
  - 自动将输入文本的结尾转换为 `\r`（回车符）
  - 处理不同的换行符情况：
    - 无结尾符：自动添加 `\r`
    - `\n` 结尾：替换为 `\r`
    - `\r` 结尾：保持不变
  - 确保在 PTY 环境下能够正确触发 Claude 的输入处理

- ✅ 创建了基于 Compose Multiplatform 的 UI 界面
  - 实现了 ChatWindow 主界面
  - 集成 ClaudeSession 到 UI，支持实时会话
  - 创建 MessageList 组件显示对话历史
  - 创建 InputField 组件支持消息输入
  - 添加会话状态显示（未连接、启动中、已连接等）
  - 支持错误消息的特殊显示
  - 自动滚动到最新消息

### 已知问题
1. **协程版本冲突**
   - Compose Multiplatform 和 IntelliJ Platform 的协程版本不兼容
   - 导致运行时出现 `NoClassDefFoundError: CoroutineExceptionHandlerImplKt`
   - 需要进一步研究解决方案

### 使用方法
1. 运行 `./gradlew runIde` 启动插件测试环境
2. 在 IDE 右侧找到 "Claude Code" 工具窗口
3. 点击打开即可开始与 Claude 对话

## 第二阶段：终端仿真器实现（2025-01-14）

### 完成的功能
1. **完整的 ANSI 终端仿真器**
   - 实现了 AnsiTerminalEmulator 类，支持常见的 ANSI 转义序列
   - 支持光标控制、颜色、文本属性（粗体、斜体、下划线等）
   - 支持清屏、行操作、滚动区域等高级功能
   - 实现了主屏幕和备用屏幕切换

2. **终端显示组件**
   - 创建了 TerminalDisplay 组件，基于 JComponent 实现
   - 支持中文输入（通过 InputMethodRequests）
   - 实现了光标闪烁和焦点管理
   - 支持终端大小动态调整

3. **PTY 终端窗口**
   - 创建了 PtyTerminalWindow，集成了 pty4j 和终端仿真器
   - 支持日志记录（输入、输出、事件）
   - 完整的进程生命周期管理

### 修复的问题（2025-01-14）

#### 终端显示重叠问题
**问题描述**：Terminal 标签页显示多个重叠的 "to interrupt" 按钮和错误提示框

**根本原因**：
1. 双重的 SwingUtilities.invokeLater 调用导致竞态条件
2. 背景渲染逻辑不正确，只在背景色不同时才绘制
3. 清屏操作的模式处理不正确

**解决方案**：
1. 移除了 TerminalDisplay.processOutput 中的 invokeLater（保留 PtyTerminalWindow 中的）
2. 修改 drawCells 方法，始终绘制背景以确保没有残留
3. 修复了 eraseDisplay 方法，正确区分模式2（清屏保持光标）和模式3（清屏重置光标）

### 技术实现细节
1. **ANSI 颜色支持**
   - 实现了完整的 16 色基本调色板
   - 支持 256 色扩展
   - 颜色值基于 VS Code 终端配色方案

2. **字符属性管理**
   - 使用 CharacterAttribute 数据类管理文本属性
   - 支持前景色、背景色、粗体、斜体、下划线等
   - 正确处理属性的组合和重置

3. **性能优化**
   - 批量绘制相同属性的字符
   - 使用双缓冲避免闪烁
   - 只重绘光标区域以优化性能

### 当前架构
```
PtyTerminalWindow (管理 PTY 进程)
    ├── TerminalDisplay (UI 组件)
    │   └── AnsiTerminalEmulator (ANSI 处理)
    │       ├── TerminalLine (行管理)
    │       └── TerminalCell (单元格)
    └── PtyProcess (pty4j 进程)
```

## 第三阶段：外部 Python 进程方案（2025-01-14）

### 背景
由于 GraalVM CE 不支持 Python（只有商业版支持），我们实现了外部 Python 进程方案。

### 实现方案
1. **ExternalPythonSession** - 通过外部 Python 进程与 Claude SDK 通信
   - 使用 ProcessBuilder 启动 Python 脚本
   - 通过 stdin/stdout 进行 JSON 通信
   - 支持错误流重定向到 stderr

2. **claude_wrapper.py** - Python 端的包装脚本
   - 处理 JSON 命令（initialize、send_message、exit）
   - 集成 claude-sdk-wrapper 或使用 mock 模式
   - 返回标准化的 JSON 响应

### 最新状态（2025-01-14 晚上）

#### ✅ 已完成
1. **外部 Python 进程通信成功**
   - Python 脚本可以正常启动并返回 ready 信号
   - 初始化命令成功执行
   - Mock 模式正常工作，可以返回模拟响应

2. **插件成功运行**
   - 工具窗口正常显示
   - SimpleChatWindow 界面加载成功
   - Python 进程初始化成功

3. **错误处理优化**
   - 添加了 Python 错误流读取
   - 改进了日志输出，避免干扰 JSON 通信
   - 处理了 SDK 未安装的情况（自动使用 mock）

#### 🔄 进行中
1. **Claude SDK 集成**
   - 需要安装 `pip install claudecode`
   - 配置 API 密钥环境变量

#### ❌ 待完成
1. API 密钥配置界面
2. 完整的聊天 UI（Markdown 渲染、代码高亮）
3. 文件引用功能（@ 补全）
4. 错误重试机制
5. 用户设置持久化

### 如何测试
1. 确保 Python 3 已安装
2. 运行 `./gradlew runIde`（需要设置 JAVA_HOME）
3. 在 IDEA 中打开右侧的 "ClaudeCode" 工具窗口
4. 输入消息并按回车发送

### 技术细节
- 使用 Gson 进行 JSON 序列化/反序列化
- 使用 Kotlin 协程处理异步 IO
- 支持流式响应（通过 Flow API）

## Claude SDK 集成更新（2025-01-14）

### 发现的正确包名
- **官方 Claude Code SDK**: `claude-code-sdk`
- GitHub 仓库: https://github.com/anthropics/claude-code-sdk-python
- 需要先安装 Claude Code CLI: `npm install -g @anthropic-ai/claude-code`

### 创建的新脚本
1. **claude_code_sdk_wrapper.py** - 使用官方 claude-code-sdk
2. **claude_anthropic_sdk.py** - 使用 Anthropic 通用 SDK 作为备选
3. **claude_sdk_direct.py** - 初始尝试（已弃用）

### 更新的文件
- `wrapper.py` - 将导入从 `claudecode` 改为 `claude_code_sdk`
- `setup.py` - 更新依赖包名
- `CLAUDE_SDK_INSTALLATION.md` - 完整的安装指南
- `README.md` - 添加快速开始和SDK安装说明