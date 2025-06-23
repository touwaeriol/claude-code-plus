# Claude Code Plus 工具窗口重新设计文档

## 设计目标

基于 Cursor、GitHub Copilot Chat 等先进 AI 编程工具的最佳实践，重新设计 Claude Code Plus 的工具窗口，提供更强大、更直观的 AI 编程体验。

## 核心功能

### 1. 上下文引用系统 (@-mentions)

参考 Cursor 的设计，支持多种上下文引用：

- **@文件** - 引用项目中的特定文件
  - `@MainActivity.kt` - 引用特定文件
  - `@src/...` - 浏览并选择文件
  - 支持模糊搜索和最近使用文件

- **@符号** - 引用代码符号
  - `@MyClass` - 引用类
  - `@myFunction` - 引用函数
  - `@variable` - 引用变量
  - 支持跨文件符号搜索

- **@文件夹** - 引用整个文件夹
  - `@src/main/kotlin` - 引用文件夹下所有文件
  - 自动过滤二进制文件和大文件

- **@终端** - 引用终端输出
  - `@terminal` - 最近的终端输出
  - `@terminal:error` - 最近的错误输出
  - 支持指定行数范围

- **@问题** - 引用 IDE 检测到的问题
  - `@problems` - 当前文件的所有问题
  - `@errors` - 仅错误
  - `@warnings` - 仅警告

- **@git** - 引用版本控制信息
  - `@diff` - 未提交的更改
  - `@staged` - 已暂存的更改
  - `@commits:5` - 最近5次提交

### 2. 模型选择器

提供直观的模型切换功能：

```
┌─────────────────────────────┐
│ 🤖 Claude 4 Opus     ▼     │  <- 下拉选择
├─────────────────────────────┤
│ • Claude 4 Opus            │  <- 深度推理，复杂任务
│ • Claude 4 Sonnet          │  <- 平衡性能，日常编码
│ • Claude 3.5 Sonnet        │  <- 快速响应，简单任务
└─────────────────────────────┘
```

### 3. 工具调用可视化

清晰展示 AI 使用的工具：

```
┌─ 工具调用 ─────────────────────┐
│ 🔍 搜索文件: "MainActivity"    │
│    找到 3 个匹配文件           │
│                               │
│ 📖 读取文件:                   │
│    src/main/kotlin/Main...    │
│    (287 行)                   │
│                               │
│ ✏️ 编辑文件:                   │
│    第 45-52 行                │
│    + 添加了错误处理           │
└───────────────────────────────┘
```

### 4. 终端集成

支持直接在对话中执行命令：

```
┌─ 终端输出 ─────────────────────┐
│ $ ./gradlew build             │
│ > Task :compileKotlin         │
│ > Task :jar                   │
│ BUILD SUCCESSFUL              │
│                               │
│ [在终端中打开] [复制输出]      │
└───────────────────────────────┘
```

### 5. 智能输入框

- **多行编辑** - 支持 Shift+Enter 换行
- **自动补全** - @ 触发上下文建议
- **快捷操作** - 
  - Cmd/Ctrl + K: 清空输入
  - Cmd/Ctrl + Enter: 发送消息
  - Tab: 接受建议

## UI 布局设计

```
┌────────────────────────────────────────────────┐
│  Claude Code Plus          [🔄] [➕] [⚙️]      │  <- 工具栏
├────────────────────────────────────────────────┤
│  [Claude 4 Opus ▼]  [清除对话]  [历史记录]     │  <- 控制栏
├────────────────────────────────────────────────┤
│                                                │
│  欢迎使用 Claude Code Plus！                   │
│                                                │
│  您可以：                                      │
│  • 使用 @ 引用文件、符号或终端输出              │
│  • 选择不同的 AI 模型                          │
│  • 直接运行命令并查看结果                      │
│                                                │
├────────────────────────────────────────────────┤
│  👤 用户                                       │
│  能帮我修复 @MainActivity.kt 中的错误吗？      │
│                                                │
│  🤖 Claude (Opus)                             │
│  我来查看 MainActivity.kt 中的错误。           │
│                                                │
│  ┌─ 工具调用 ─────────────┐                   │
│  │ 🔍 读取文件...         │                   │
│  └───────────────────────┘                   │
│                                                │
│  发现了以下问题：                              │
│  1. 第 23 行：未处理的空指针异常               │
│  2. 第 45 行：类型不匹配                       │
│                                                │
│  我可以帮您修复这些问题...                     │
│                                                │
├────────────────────────────────────────────────┤
│ [@] 输入消息... 使用 @ 引用上下文              │
│                                   [📎] [🚀]    │
└────────────────────────────────────────────────┘
```

## 组件架构

### 1. 核心组件

```kotlin
// 主视图
JewelConversationView
├── ToolBar                    // 顶部工具栏
├── ControlBar                 // 模型选择和控制
├── MessageList                // 消息列表
│   ├── UserMessage           // 用户消息
│   ├── AssistantMessage      // AI 消息
│   ├── ToolCallDisplay       // 工具调用展示
│   └── TerminalOutput        // 终端输出展示
└── SmartInputArea            // 智能输入区
    ├── ContextMenu           // @ 上下文菜单
    ├── ModelSelector         // 模型选择器
    └── ActionButtons         // 操作按钮
```

### 2. 上下文系统

```kotlin
interface ContextProvider {
    fun searchFiles(query: String): List<FileContext>
    fun searchSymbols(query: String): List<SymbolContext>
    fun getTerminalOutput(lines: Int = 50): TerminalContext
    fun getProblems(filter: ProblemFilter): List<ProblemContext>
    fun getGitInfo(type: GitInfoType): GitContext
}

sealed class ContextReference {
    data class File(val path: String, val lines: IntRange?)
    data class Symbol(val name: String, val type: SymbolType)
    data class Terminal(val lines: Int, val filter: String?)
    data class Problems(val severity: Severity?)
    data class Git(val type: GitType)
}
```

### 3. 消息增强

```kotlin
data class EnhancedMessage(
    val role: MessageRole,
    val content: String,
    val contexts: List<ContextReference>,  // 引用的上下文
    val toolCalls: List<ToolCall>,        // 工具调用
    val model: String,                     // 使用的模型
    val timestamp: Long
)

data class ToolCall(
    val tool: String,
    val parameters: Map<String, Any>,
    val result: ToolResult?
)
```

## 交互流程

### 1. @ 引用流程

```
用户输入 @ 
    ↓
显示上下文菜单
    ↓
用户选择类型（文件/符号/终端等）
    ↓
显示搜索/浏览界面
    ↓
用户选择具体项目
    ↓
插入引用标记
    ↓
发送时解析并附加上下文
```

### 2. 工具调用流程

```
AI 请求工具调用
    ↓
显示工具调用卡片（折叠状态）
    ↓
执行工具
    ↓
实时更新执行状态
    ↓
显示结果摘要
    ↓
用户可展开查看详情
```

## 实现计划

### 第一阶段：基础重构
1. 创建新的 UI 组件结构
2. 实现消息列表的新渲染方式
3. 添加模型选择器

### 第二阶段：上下文系统
1. 实现 @ 触发机制
2. 创建文件搜索功能
3. 添加符号搜索支持
4. 集成终端输出

### 第三阶段：高级功能
1. 实现工具调用可视化
2. 添加 Git 集成
3. 支持问题面板集成
4. 实现命令执行功能

### 第四阶段：优化体验
1. 添加快捷键支持
2. 实现拖拽文件引用
3. 添加历史记录搜索
4. 性能优化

## 技术栈

- **UI 框架**: Jetpack Compose + Jewel UI
- **状态管理**: Kotlin StateFlow + ViewModel
- **异步处理**: Kotlin Coroutines + Flow
- **依赖注入**: Manual DI / Service Locator
- **数据持久化**: 会话缓存 + 文件存储

## 设计原则

1. **直观性** - 用户应该能够立即理解如何使用每个功能
2. **高效性** - 最少的点击和输入完成任务
3. **可发现性** - 功能应该容易被发现和学习
4. **一致性** - 与 IntelliJ IDEA 的设计语言保持一致
5. **响应性** - 实时反馈，流畅的交互体验