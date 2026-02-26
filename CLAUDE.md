特别提醒：请使用简体中文进行交流
特别提醒：git commit、changelog 不使用中文，而是使用英文

# Claude Code Plus - 架构说明



## 📋 项目概述

Claude Code Plus 是一个 IntelliJ IDEA 插件，集成了 Claude AI 助手，提供智能代码编辑、文件操作、终端执行等功能。

## 🏗️ 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端 (Vue 3)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  Components:                           Services:                             │
│  ├── TerminalBackgroundBar.vue         ├── RSocketSession.ts (Claude SDK)   │
│  ├── FileRollbackBar.vue               ├── ideaRSocket.ts (IDE 功能)         │
│  ├── ChatInput.vue (Ctrl+B)            └── sessionStore.ts (状态管理)        │
│  └── ...                                                                     │
└────────────────────────┬───────────────────────────────┬────────────────────┘
                         │ RSocket                        │ RSocket
                         ▼                                ▼
┌────────────────────────────────────┐  ┌──────────────────────────────────────┐
│     ai-agent-server (Kotlin)       │  │   jetbrains-plugin (Kotlin)          │
│     RSocketHandler.kt              │  │   JetBrainsRSocketHandler.kt         │
├────────────────────────────────────┤  ├──────────────────────────────────────┤
│ • agent.connect / query / interrupt│  │ • idea.openFile / showDiff           │
│ • agent.runToBackground            │  │ • idea.terminalBackground            │
│ • agent.setModel / setPermission   │  │ • idea.batchRollback                 │
│ • agent.getMcpStatus / reconnectMcp│  │ • idea.getBackgroundableTerminals    │
└────────────────────────┬───────────┘  └─────────────────────┬────────────────┘
                         │                                    │
                         ▼                                    ▼
┌────────────────────────────────────┐  ┌──────────────────────────────────────┐
│     claude-agent-sdk (Kotlin)      │  │   TerminalMcpServerImpl              │
│     ControlProtocol.kt             │  │   (Terminal Session Manager)          │
└────────────────────────┬───────────┘  └──────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────┐
│       Official Claude CLI          │
└────────────────────────────────────┘
```

### 模块说明

| 模块 | 路径 | 职责 |
|------|------|------|
| **frontend** | `frontend/` | Vue 3 前端，聊天界面、工具展示 |
| **ai-agent-server** | `ai-agent-server/` | HTTP/RSocket 服务器，连接前端与 SDK |
| **claude-agent-sdk** | `claude-agent-sdk/` | Claude CLI 封装，官方 CLI 集成 |
| **jetbrains-plugin** | `jetbrains-plugin/` | IDEA 插件，IDE 集成功能 |
| **ai-agent-proto** | `ai-agent-proto/` | Protobuf 定义，RSocket 通信协议 |

---

## 🔧 CLI 版本管理

CLI 版本由 `claude-agent-sdk/cli-version.properties` 统一管理：

```properties
cli.version=2.1.56
npm.version=0.2.56
```

**构建流程**：
- `downloadCli` Gradle 任务从 npm 下载官方 CLI 到 `claude-agent-sdk/src/main/resources/bundled/claude-cli-{version}.mjs`
- `copyToVsCodeExtension` 任务自动复制到 `vscode-extension/resources/bundled/claude-cli.mjs`（固定文件名）
- JetBrains 插件通过 Kotlin SDK 从 JAR 中提取 CLI
- VS Code 扩展直接使用 bundled CLI JS 文件

**升级 CLI**：
1. 修改 `cli-version.properties` 中的版本号
2. 运行 `./gradlew :claude-agent-sdk:downloadCli`
3. 删除旧版本文件
4. 提交变更

---

## 📡 RSocket 路由表

### ai-agent-server 路由 (Claude SDK 通信)

| 路由 | 模式 | 功能 |
|------|------|------|
| `agent.connect` | Request-Response | 建立会话连接 |
| `agent.query` | Request-Stream | 发送消息（流式响应） |
| `agent.queryWithContent` | Request-Stream | 发送带内容的消息 |
| `agent.interrupt` | Request-Response | 中断当前操作 |
| `agent.runToBackground` | Request-Response | 统一后台化 |
| `agent.bashRunToBackground` | Request-Response | Bash 后台化 |
| `agent.setModel` | Request-Response | 切换模型 |
| `agent.setPermissionMode` | Request-Response | 设置权限模式 |
| `agent.setMaxThinkingTokens` | Request-Response | 设置思考 token 上限 |
| `agent.getMcpStatus` | Request-Response | 获取 MCP 状态 |
| `agent.reconnectMcp` | Request-Response | MCP 重连 |
| `agent.getMcpTools` | Request-Response | 获取 MCP 工具列表 |

### jetbrains-plugin 路由 (IDE 功能)

| 路由 | 模式 | 功能 |
|------|------|------|
| `jetbrains.openFile` | Request-Response | 打开文件 |
| `jetbrains.showDiff` | Request-Response | 显示 Diff |
| `jetbrains.showMultiEditDiff` | Request-Response | 多处编辑 Diff |
| `jetbrains.getBackgroundableTerminals` | Request-Response | 获取可后台化终端任务 |
| `jetbrains.terminalBackground` | Request-Stream | 终端任务后台化（流式） |
| `jetbrains.batchRollback` | Request-Stream | 批量文件回滚（流式） |
| `jetbrains.rollbackFile` | Request-Response | 单文件回滚 |
| `jetbrains.getTheme` | Request-Response | 获取 IDE 主题 |
| `jetbrains.getSettings` | Request-Response | 获取插件设置 |
| `jetbrains.getProjectPath` | Request-Response | 获取项目路径 |

---

## 🔌 三种通信方式


### 2️⃣ IDEA 集成通信 (纯 HTTP)

**用途**: 打开文件、显示 Diff、搜索文件等 IDEA 原生功能

**前端**:
```typescript
// frontend/src/services/ideaBridge.ts
import { ideService } from '@/services/ideaBridge'

// 打开文件
await ideService.openFile('/path/to/file.ts', { line: 10 })

// 显示 Diff
await ideService.showDiff({
    filePath: '/path/to/file.ts',
    oldContent: '...',
    newContent: '...'
})
```

**后端**:
```kotlin
// claude-code-server/src/main/kotlin/com/claudecodeplus/server/HttpApiServer.kt
post("/api/") {
    when (action) {
        "ide.openFile" -> ideActionBridge.openFile(request)
        "ide.showDiff" -> ideActionBridge.showDiff(request)
        "ide.searchFiles" -> ideActionBridge.searchFiles(query, maxResults)
        "ide.getFileContent" -> // 读取文件内容
    }
}
```

**协议**: HTTP POST
- 请求-响应模式
- 同步调用
- 简单可靠

---

### 3️⃣ 通用 Web 功能 (纯 HTTP)

**用途**: 其他不需要流式响应的功能

**协议**: HTTP GET/POST
- RESTful API
- 标准 HTTP 请求

---

## 🔧 关键技术细节

### 随机端口机制

**问题**: 多个 IDEA 项目同时打开时，端口冲突

**解决方案**:
```kotlin
// claude-code-server/src/main/kotlin/com/claudecodeplus/server/HttpApiServer.kt
fun start(port: Int = 8765): String {
    val actualPort = try {
        embeddedServer(Netty, port = port) { ... }.start()
        port
    } catch (e: BindException) {
        val availablePort = findAvailablePort()
        embeddedServer(Netty, port = availablePort) { ... }.start()
        availablePort
    }
    return "http://localhost:$actualPort"
}
```

### 前端获取后端地址 & 环境检测

**IDEA 插件模式**: 通过 URL 参数 `?ide=true` 触发后端注入

```kotlin
// jetbrains-plugin/.../VueToolWindowFactory.kt
val ideUrl = "$serverUrl?ide=true"  // 带上 ide=true 参数
browser.loadURL(ideUrl)
```

```kotlin
// claude-code-server/.../HttpApiServer.kt
get("/") {
    val isIdeMode = call.request.queryParameters["ide"] == "true"

    if (isIdeMode) {
        // IDEA 插件模式：注入 window.__serverUrl
        val injection = """
            <script>
                window.__serverUrl = 'http://localhost:$serverPort';
                console.log('✅ Environment: IDEA Plugin Mode');
            </script>
        """.trimIndent()
        html = html.replace("</head>", "$injection\n</head>")
    }
}
```

**浏览器模式**: 通过统一解析器获取地址
```typescript
// frontend/src/services/ideaBridge.ts
import { resolveServerHttpUrl } from '@/utils/serverUrl'

private getBaseUrl(): string {
    return resolveServerHttpUrl()
}

// 环境检测
getMode(): 'ide' | 'browser' {
    return (window as any).__serverUrl ? 'ide' : 'browser'
}
```

`resolveServerHttpUrl()` 的优先级：
1. `window.__serverUrl`（IDEA 注入或提前设置）
2. `VITE_SERVER_URL`
3. `VITE_BACKEND_PORT`（默认 `http://localhost:<port>`）
4. 回退到 `http://localhost:8765`

**优势**:
- ✅ **时序可靠**: HTML 加载时就已注入，Vue 初始化前就能读取
- ✅ **无需额外请求**: 不需要前端主动检测
- ✅ **简单明确**: 通过 `window.__serverUrl` 的存在判断环境

---

## 📋 日志架构

### 设计目标

本项目使用**统一日志框架**，实现以下目标：
1. **统一 API**: 所有模块使用一致的 lambda 风格日志调用 `logger.info { "message" }`
2. **环境自适应**: IDEA 插件模式输出到 `idea.log`，独立运行时输出到控制台
3. **延迟计算**: Lambda 形式只有在日志级别启用时才执行字符串拼接，提升性能

### 核心组件

#### 1. unified-logging 模块 (SLF4J 扩展)

**位置**: `unified-logging/src/main/kotlin/com/asakii/logging/Logging.kt`

**功能**:
- 基于 SLF4J 的日志门面
- 提供 Kotlin lambda 扩展函数
- 同时输出到 SLF4J 和控制台（可配置）

**使用方式**:
```kotlin
import com.asakii.logging.getLogger
import com.asakii.logging.info
import com.asakii.logging.debug
import com.asakii.logging.warn
import com.asakii.logging.error

class MyClass {
    companion object {
        private val logger = getLogger<MyClass>()
    }

    fun doSomething() {
        logger.info { "Starting operation" }
        logger.debug { "Debug details: $data" }
        logger.warn { "Warning message" }
        logger.error(exception) { "Error occurred" }
    }
}
```

#### 2. IdeaLoggerExtensions (IDEA Logger 扩展)

**位置**: `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/logging/IdeaLoggerExtensions.kt`

**功能**:
- 为 IDEA 原生 `com.intellij.openapi.diagnostic.Logger` 提供 lambda 扩展
- 与 unified-logging 保持一致的 API 风格
- 仅在 jetbrains-plugin 模块内使用

**使用方式**:
```kotlin
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.info
import com.asakii.plugin.logging.debug
import com.asakii.plugin.logging.warn
import com.asakii.plugin.logging.error

class MyPluginClass {
    private val logger = Logger.getInstance(MyPluginClass::class.java)

    fun doSomething() {
        logger.info { "Plugin operation" }
        logger.debug { "Debug: $variable" }
        logger.error(exception) { "Error occurred" }
    }
}
```

### 模块日志使用规范

| 模块 | Logger 类型 | 扩展函数来源 | 说明 |
|------|------------|-------------|------|
| `unified-logging` | `org.slf4j.Logger` | `com.asakii.logging.*` | 日志框架核心模块 |
| `ai-agent-server` | `org.slf4j.Logger` | `com.asakii.logging.*` | 依赖 unified-logging |
| `claude-agent-sdk` | `org.slf4j.Logger` | `com.asakii.logging.*` | 依赖 unified-logging |
| `codex-agent-sdk` | `org.slf4j.Logger` | `com.asakii.logging.*` | 依赖 unified-logging |
| `jetbrains-plugin` | `com.intellij.openapi.diagnostic.Logger` | `com.asakii.plugin.logging.*` | 使用 IDEA 原生 Logger |

### SLF4J/Logback 配置

**unified-logging / ai-agent-server / SDK 模块**:
- 使用 `logback-classic` 作为 SLF4J 实现
- 配置文件: `logback.xml`

**jetbrains-plugin**:
- 排除所有 SLF4J/Logback 依赖，避免与 IDEA 内置版本冲突
- 使用 IDEA 原生 Logger + IdeaLoggerExtensions

```kotlin
// jetbrains-plugin/build.gradle.kts
configurations.all {
    exclude(group = "ch.qos.logback", module = "logback-classic")
    exclude(group = "ch.qos.logback", module = "logback-core")
    exclude(group = "org.slf4j", module = "slf4j-api")
    exclude(group = "org.slf4j", module = "jul-to-slf4j")
}
```

### 日志级别

| 级别 | 方法 | 用途 |
|------|------|------|
| INFO | `logger.info { }` | 重要业务事件 |
| DEBUG | `logger.debug { }` | 调试信息 |
| WARN | `logger.warn { }` | 警告（可恢复的异常情况） |
| ERROR | `logger.error(e) { }` | 错误（需要关注的异常） |

### 查看日志

**IDEA 插件模式**:
- 菜单: Help → Show Log in Explorer/Finder
- 日志文件: `idea.log`

**独立运行模式**:
- 日志直接输出到控制台 (stdout)

---

## 📁 核心文件说明

### 前端核心文件

#### `frontend/src/services/ideaBridge.ts`
**职责**: 前端与后端的 HTTP 通信桥接

**导出**:
- `ideaBridge`: 单例服务，提供 `query()` 方法
- `ideService`: 便捷 API，封装常用 IDEA 集成功能
    - `openFile()`: 打开文件
    - `showDiff()`: 显示 Diff
    - `searchFiles()`: 搜索文件
    - `getFileContent()`: 获取文件内容
    - `getTheme()`: 获取主题
- `claudeService`: Claude 会话相关 API（通过 HTTP，非 WebSocket）

**示例**:
```typescript
import { ideService } from '@/services/ideaBridge'

// 打开文件并跳转到指定行
await ideService.openFile('/src/App.vue', { line: 42, column: 10 })

// 显示 Diff（支持多处修改）
await ideService.showDiff({
    filePath: '/src/utils/helper.ts',
    oldContent: 'old code',
    newContent: 'new code',
    rebuildFromFile: true,  // 从文件重建完整 Diff
    edits: [
        { oldString: 'foo', newString: 'bar', replaceAll: false }
    ]
})
```

#### `frontend/src/services/ClaudeSession.ts`
**职责**: Claude 会话管理（WebSocket RPC）

**功能**:
- 建立 WebSocket 连接
- 发送消息并接收流式响应
- 中断正在进行的会话
- 管理会话状态

#### `frontend/src/components/tools/`
**职责**: 工具调用显示组件

**组件列表**:
- `ReadToolDisplay.vue`: 读取文件工具
- `WriteToolDisplay.vue`: 写入文件工具
- `EditToolDisplay.vue`: 编辑文件工具
- `BashToolDisplay.vue`: 终端命令工具
- `MultiEditToolDisplay.vue`: 多处编辑工具
- `CompactToolCard.vue`: 可复用的工具卡片组件

**设计原则**:
- 折叠模式：显示关键参数（文件名、路径、命令）
- 展开模式：显示完整细节
- 状态指示：彩色圆点（绿色=成功，红色=失败，灰色=进行中）
- IDEA 集成：点击文件路径打开文件，点击卡片显示 Diff

---

### 后端核心文件

#### `claude-code-server/src/main/kotlin/com/claudecodeplus/server/HttpApiServer.kt`
**职责**: HTTP 服务器主入口

**功能**:
- 启动 Ktor 服务器（随机端口）
- 配置 WebSocket 端点 (`/ws`)
- 配置 HTTP API 端点 (`/api/`)
- 提供静态文件服务（前端资源）

**关键代码**:
```kotlin
// WebSocket RPC 端点
webSocket("/ws") {
    val rpcHandler = WebSocketRpcHandler(this, claudeRpcService)
    rpcHandler.handle()
}

// HTTP API 端点
post("/api/") {
    val requestBody = call.receiveText()
    val json = Json { ignoreUnknownKeys = true }
    val request = json.decodeFromString<FrontendRequest>(requestBody)
    val action = request.action

    when (action) {
        "ide.openFile" -> {
            val response = ideActionBridge.openFile(request)
            call.respondText(json.encodeToString(response), ContentType.Application.Json)
        }
        "ide.showDiff" -> {
            val response = ideActionBridge.showDiff(request)
            call.respondText(json.encodeToString(response), ContentType.Application.Json)
        }
        // ... 其他 API
    }
}
```

#### `jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/bridge/IdeActionBridgeImpl.kt`
**职责**: IDEA 平台 API 调用实现

**功能**:
- `openFile()`: 使用 `FileEditorManager` 打开文件
- `showDiff()`: 使用 `DiffManager` 显示 Diff
- `searchFiles()`: 使用 `FilenameIndex` 搜索文件
- `getFileContent()`: 读取文件内容

**关键代码**:
```kotlin
override fun openFile(request: FrontendRequest): FrontendResponse {
    val filePath = request.data?.jsonObject?.get("filePath")?.jsonPrimitive?.contentOrNull
    val line = request.data?.jsonObject?.get("line")?.jsonPrimitive?.intOrNull

    ApplicationManager.getApplication().invokeLater {
        val file = LocalFileSystem.getInstance().findFileByIoFile(File(filePath))
        if (file != null) {
            val descriptor = OpenFileDescriptor(project, file, line - 1, column - 1)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }
    }

    return FrontendResponse(success = true)
}
```

---

## 🎨 设计决策

### 为什么后端使用随机端口？

**原因**:
1. **多项目支持**: 用户可能同时打开多个 IDEA 项目
2. **避免冲突**: 固定端口可能被其他应用占用
3. **灵活性**: 自动选择可用端口，无需用户配置

---

## 🚀 启动流程

### IDEA 插件模式

1. **用户打开 IDEA 项目**
2. **插件初始化** (`HttpServerProjectService`)
    - 启动后端 HTTP 服务器（随机端口）
    - 记录服务器 URL
3. **打开聊天工具窗口** (`ChatToolWindowFactory`)
    - 加载前端资源（Vue 应用）
    - 注入 `window.__serverUrl`
4. **前端初始化**
    - `ideaBridge` 读取 `window.__serverUrl`
    - 建立 HTTP 连接
5. **用户开始对话**
    - 前端通过 WebSocket RPC 发送消息
    - 后端调用 Claude SDK
    - 流式返回响应

### 浏览器模式

1. **启动后端服务器** (手动或脚本)
2. **启动前端开发服务器** (`npm run dev`)
3. **打开浏览器** (`http://localhost:5173`)
4. **前端通过解析器解析 URL**（若无注入，则回退 `http://localhost:8765`）
5. **功能受限**: IDEA 集成功能不可用（打开文件、显示 Diff）

**很重要**
开发时如果需要调试界面：
通过启动 com.asakii.server.StandaloneServerKt 来得到一个 运行在 8765 端口的后端
运行 前端的 dev 任务，可以得到一个运行在端口 5174 的前端
使用mcp 访问 5174 即可测试相关功能
如果 相关端口被占用，停止占用端口的进程，而不是使用新端口


---

## 📝 开发指南

### 添加新的 IDEA 集成功能

1. **定义接口** (`IdeActionBridge.kt`)
```kotlin
interface IdeActionBridge {
    fun myNewFeature(request: FrontendRequest): FrontendResponse
}
```

2. **实现接口** (`IdeActionBridgeImpl.kt`)
```kotlin
override fun myNewFeature(request: FrontendRequest): FrontendResponse {
    // 调用 IDEA Platform API
    return FrontendResponse(success = true)
}
```

3. **添加 HTTP 端点** (`HttpApiServer.kt`)
```kotlin
when (action) {
    "ide.myNewFeature" -> {
        val response = ideActionBridge.myNewFeature(request)
        call.respondText(json.encodeToString(response), ContentType.Application.Json)
    }
}
```

4. **添加前端 API** (`ideaBridge.ts`)
```typescript
export const ideService = {
    async myNewFeature(params: any) {
        return ideaBridge.query('ide.myNewFeature', params)
    }
}
```

5. **在组件中使用**
```typescript
import { ideService } from '@/services/ideaBridge'

await ideService.myNewFeature({ foo: 'bar' })
```

### 路径解析工具 (PathResolver)

处理文件路径时，**必须使用 `PathResolver` 将相对路径转换为绝对路径**。

**位置**: `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/util/PathResolver.kt`

**使用方式**:
```kotlin
import com.asakii.plugin.util.PathResolver
import com.asakii.plugin.util.toAbsolutePath

// 方式1：静态方法 + Project
val absolutePath = PathResolver.resolve("src/main/App.kt", project)

// 方式2：静态方法 + basePath
val absolutePath = PathResolver.resolve("src/main/App.kt", basePath)

// 方式3：扩展函数
val absolutePath = "src/main/App.kt".toAbsolutePath(project)

// 相对路径转换
val relativePath = absolutePath.toRelativePath(project)
```

**为什么需要**:
- IDEA VFS API（如 `LocalFileSystem.findFileByPath()`）只接受绝对路径
- 前端传递的通常是项目相对路径
- `java.io.File(相对路径)` 会相对于 JVM 工作目录解析，而非项目根目录

---

## 🔍 调试技巧

### IDEA 日志路径

查看 IDEA 日志是调试问题的关键。日志路径取决于运行环境：

**开发调试模式（idea-sandbox）**：
```
jetbrains-plugin/build/idea-sandbox/{IDEA版本}/log/idea.log
```
例如：`jetbrains-plugin/build/idea-sandbox/IU-2025.3.1.1/log/idea.log`

**正式安装的 IDEA**：
- Windows: `C:\Users\{用户名}\AppData\Local\JetBrains\{产品版本}\log\idea.log`
- macOS: `~/Library/Logs/JetBrains/{产品版本}/idea.log`
- Linux: `~/.local/share/JetBrains/{产品版本}/log/idea.log`

**快速访问**：IDEA 菜单 → Help → Show Log in Explorer/Finder

**关键日志标签**：
- `[MCP]` - MCP 服务器相关日志
- `[RSocket]` - RSocket 通信日志
- `[Claude]` - Claude CLI 相关日志

### 查看 HTTP 请求

### 常见问题

**问题**: 前端无法连接后端
- 检查 `window.__serverUrl` 是否正确注入
- 检查后端服务器是否启动
- 检查端口是否被占用

**问题**: IDEA 集成功能不工作
- 确认在 IDEA 插件模式下运行（不是浏览器）
- 检查 `IdeActionBridgeImpl` 是否正确注入
- 查看 IDEA 日志中的错误信息

**问题**: MCP 终端工具报错
- 检查日志中 `[MCP] Incoming request` 的 `connectId` 和 `provider` 是否为 null
- 确认 MCP 配置中的 headers 是否正确传递




---

## 🔀 多版本兼容架构

### 概述

插件支持 IntelliJ Platform 2024.2 ~ 2025.3 (platformMajor: 242 ~ 253)，通过**编译时源码分离**实现跨版本兼容。

### 目录结构

```
jetbrains-plugin/src/main/
├── kotlin/                      # 通用代码（所有版本共享）
├── kotlin-compat-242/           # 2024.2 ~ 2025.2 兼容层
│   └── com/asakii/plugin/compat/
│       ├── TerminalCompat.kt    # createLocalShellWidget API
│       └── BrowseButtonCompat.kt # 4-param addBrowseFolderListener API
├── kotlin-compat-253/           # 2025.3+ 兼容层
│   └── com/asakii/plugin/compat/
│       ├── TerminalCompat.kt    # createNewSession API
│       └── BrowseButtonCompat.kt # 2-param addBrowseFolderListener API
├── kotlin-compat-diff-242/      # Diff API (2024.2)
└── kotlin-compat-diff-243/      # Diff API (2024.3+)
```

### 构建配置

在 `build.gradle.kts` 中，根据目标平台版本选择对应的兼容层目录：

```kotlin
// 主兼容层目录选择
val mainCompatDir = when {
    platformMajor >= 253 -> "kotlin-compat-253"  // 2025.3+
    else -> "kotlin-compat-242"                   // 2024.2 ~ 2025.2
}

// Diff API 兼容层目录选择
val diffCompatDir = when {
    platformMajor >= 243 -> "kotlin-compat-diff-243"  // 2024.3+
    else -> "kotlin-compat-diff-242"                   // 2024.2
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin")           // 通用代码
            srcDir("src/main/$mainCompatDir")   // 主兼容层
            srcDir("src/main/$diffCompatDir")   // Diff API 兼容层
        }
    }
}
```

### 发布新版本流程

发布新版本需要按以下步骤进行：

#### Step 1: 更新版本号

编辑 `gradle.properties`：
```properties
pluginVersion = 2.0.3  # 修改为新版本号
```

#### Step 2: 更新 CHANGELOG

编辑 `CHANGELOG.md`，在文件开头添加新版本的变更记录（**使用英文**）：
```markdown
## [2.0.3] - 2026-01-26

### Added
- New feature description

### Fixed
- Bug fix description

### Changed
- Change description
```

#### Step 3: 提交版本变更

```bash
git add gradle.properties CHANGELOG.md
git commit -m "chore: bump version to 2.0.3"
```

#### Step 4: 构建所有平台版本

```bash
./gradlew :jetbrains-plugin:buildAllVersions
```

#### Step 5: 发布到 JetBrains Marketplace

```bash
./gradlew :jetbrains-plugin:publishAllVersions
```

#### Step 6: 创建 Git Tag（可选）

```bash
git tag v2.0.3
git push origin v2.0.3
```

### 构建命令

**构建所有版本** (生成 5 个平台特定的 zip 文件):
```bash
./gradlew :jetbrains-plugin:buildAllVersions
```

**构建特定版本**:
```bash
./gradlew :jetbrains-plugin:buildPlugin -PplatformMajor=253 -PplatformSpecific=true
./gradlew :jetbrains-plugin:buildPlugin -PplatformMajor=242 -PplatformSpecific=true
```

**构建通用版本** (使用 242 SDK，声明兼容 242-253):
```bash
./gradlew :jetbrains-plugin:buildPlugin
```

### 发布命令

**发布所有版本到 JetBrains Marketplace** (推荐，发布 5 个平台特定版本):
```bash
./gradlew :jetbrains-plugin:publishAllVersions
```

**发布单个版本** (默认发布 253 版本):
```bash
./gradlew :jetbrains-plugin:publishPlugin
```

**发布特定平台版本**:
```bash
./gradlew :jetbrains-plugin:publishPlugin -PplatformMajor=242
./gradlew :jetbrains-plugin:publishPlugin -PplatformMajor=243
./gradlew :jetbrains-plugin:publishPlugin -PplatformMajor=251
./gradlew :jetbrains-plugin:publishPlugin -PplatformMajor=252
./gradlew :jetbrains-plugin:publishPlugin -PplatformMajor=253
```

**版本号规则**:
- 版本号格式: `{baseVersion}.{platformMajor}`，例如 `2.0.2.253`
- 每个平台版本独立发布，IDE 会自动选择匹配的版本

**发布前提**:
- 需要在环境变量或 `gradle.properties` 中配置 JetBrains Marketplace API Token
- Token 配置项: `JETBRAINS_TOKEN` 或 `intellijPublishToken`

### API 变更点

| 版本 | Terminal API | BrowseButton API | Diff API |
|------|-------------|------------------|----------|
| 242 | `createLocalShellWidget()` | 4-param | `DiffRequestProcessorEditor` |
| 243 | `createLocalShellWidget()` | 4-param | `DiffEditorViewerFileEditor` |
| 251-252 | `createLocalShellWidget()` | 4-param | `DiffEditorViewerFileEditor` |
| 253 | `createNewSession()` | 2-param | `DiffEditorViewerFileEditor` |

### 添加新的兼容层

1. 在对应目录创建文件 (如 `kotlin-compat-242/` 或 `kotlin-compat-253/`)
2. 使用相同的包名和类名，提供统一的 API 签名
3. 内部实现调用各版本特定的 IntelliJ API
4. 使用 `@Suppress("DEPRECATION")` 抑制旧 API 警告

---

## ⏸️ 后台化功能数据流

用户按 **Ctrl+B** 触发统一后台化，系统自动判断任务类型并分发：

### Claude Bash/Agent 后台化

```
用户按 Ctrl+B
    ↓
sessionStore.runToBackground()
    ↓
RSocketSession.runToBackground()
    ↓ RSocket: agent.runToBackground
RSocketHandler.handleRunToBackground()
    ↓
ControlProtocol.runToBackground()
    ↓ JSON-RPC control_request
Official CLI
    ↓ 内置后台化支持
```

### Terminal MCP 后台化

```
用户按 Ctrl+B
    ↓
sessionStore.runToBackground()
    ↓
jetbrainsRSocket.terminalBackground()
    ↓ RSocket: jetbrains.terminalBackground
JetBrainsRSocketHandler.handleTerminalBackground()
    ↓
TerminalMcpServerImpl.sessionManager.markTaskAsBackground()
```

### 前端统一调度逻辑

```typescript
// frontend/src/stores/sessionStore.ts
async function runToBackground(taskId?: string, toolType?: string) {
  // 单任务模式 - Terminal MCP
  if (taskId && toolType?.startsWith('mcp__terminal__')) {
    return await runTerminalBackground(taskId)  // RSocket: jetbrains.terminalBackground
  }
  
  // 单任务模式 - Claude Bash/Agent
  if (taskId) {
    return await currentTab.runToBackground(taskId)  // RSocket: agent.runToBackground
  }
  
  // 批量模式 - 同时调用两个 API
  const [claudeResult, terminalResult] = await Promise.all([
    currentTab.runToBackground(),      // RSocket: agent.runToBackground
    runTerminalBackground()            // RSocket: jetbrains.terminalBackground
  ])
}
```

---

## 📚 相关文档

- [HTTP API 架构](docs/HTTP_API_ARCHITECTURE.md)
- [前端重构设计](docs/frontend-refactoring-design.md)
- [工具显示规范](docs/tool-display-specification.md)
- [主题系统](docs/THEME_SYSTEM.md)

---

## 📦 外部子模块

- `external/openai-codex`
    - 来源仓库：`org-14957082@github.com:openai/codex.git`
    - 管理方式：作为 git submodule 引入，位于 `external/` 目录，后续可通过 `git submodule update --init --recursive` 同步。

- [?????????](docs/MESSAGE_RENDERING_SPEC.md)????????????????????



