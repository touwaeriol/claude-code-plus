# Claude Code Plus 架构说明

## 项目概述

Claude Code Plus 是一个 IntelliJ IDEA 插件，通过直接调用 Claude CLI 命令行工具来提供 AI 编程助手功能。

## 核心架构

### 1. 直接调用 Claude CLI

本插件采用直接调用系统中已安装的 Claude CLI 的方式，而不是通过 Node.js 服务中转。这种设计具有以下优势：

- **简化架构**：无需维护额外的 Node.js 服务层
- **减少依赖**：不需要 Node.js 运行时
- **提高性能**：减少了进程间通信的开销
- **易于部署**：用户只需安装 Claude CLI 即可使用

### 2. 工作原理

```
用户输入 -> IntelliJ 插件 -> ClaudeCliWrapper -> claude CLI -> Claude API
    ^                                                              |
    |<--------------------- 流式响应 <-----------------------------|
```

## 核心组件

### ClaudeCliWrapper (`/src/main/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapper.kt`)

这是插件的核心组件，负责：

1. **构建命令行参数**：将 Kotlin 数据结构转换为 claude CLI 的命令行参数
2. **进程管理**：使用 `ProcessBuilder` 创建和管理 claude 进程
3. **流式响应处理**：解析 claude CLI 的 JSON 流输出
4. **错误处理**：捕获并处理进程错误和异常

#### 关键实现细节：

```kotlin
// 使用流式 JSON 输出格式
args.addAll(listOf("--output-format", "stream-json", "--verbose"))

// 权限模式设置
// "default" - 允许所有操作（默认）
// "strict" - 限制敏感操作  
// "none" - 禁止所有工具使用
if (options.permissionMode != "default") {
    args.addAll(listOf("--permission-mode", options.permissionMode))
}
```

### ClaudeCodePlusToolWindowFactory (`/src/main/kotlin/com/claudecodeplus/toolwindow/ClaudeCodePlusToolWindowFactory.kt`)

负责创建和管理插件的 UI 界面：

- 创建聊天窗口
- 处理用户输入
- 显示 AI 响应
- 文件引用功能（使用 @ 符号）

### 数据模型 (`/src/main/kotlin/com/claudecodeplus/sdk/DataClasses.kt`)

定义了与 Claude CLI 交互的数据结构：

- `MessageType`：消息类型枚举
- `SDKMessage`：SDK 消息结构
- `MessageData`：消息数据内容

## 配置和设置

### MCP (Model Context Protocol) 支持

插件支持 MCP 配置，允许用户扩展 Claude 的能力：

- **全局配置**：`/src/main/kotlin/com/claudecodeplus/settings/McpSettingsService.kt`
- **项目配置**：`/src/main/kotlin/com/claudecodeplus/settings/ProjectMcpSettingsService.kt`
- **配置界面**：`/src/main/kotlin/com/claudecodeplus/settings/McpConfigurable.kt`

## 测试

### 单元测试 (`/src/test/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapperTest.kt`)

包含了对 ClaudeCliWrapper 的全面测试：

- 基本查询功能测试
- 错误处理测试
- 参数构建测试
- Claude CLI 可用性检查

运行测试：
```bash
# 运行所有测试
./gradlew test

# 运行包含实际 Claude API 调用的测试
RUN_CLAUDE_TESTS=true ./gradlew test
```

## 依赖项

### 运行时依赖

- **Claude CLI**：必须在系统中安装并配置
  ```bash
  npm install -g @anthropic-ai/claude-cli
  claude auth
  ```

### 开发依赖

- Kotlin 2.1.0
- IntelliJ Platform SDK 2025.1.2
- Kotlin Coroutines 1.7.3
- Jackson (JSON 处理)

## 构建和部署

```bash
# 构建插件
./gradlew buildPlugin

# 运行开发环境
./gradlew runIde

# 运行测试
./gradlew test
```

## 支持的功能

除了基本的消息发送和接收，插件还支持以下高级功能：

### 1. 响应打断

- **停止按钮**：UI 中提供停止按钮，可随时中断 AI 响应
- **ESC 快捷键**：按 ESC 键快速停止生成
- **实现机制**：使用 Kotlin 协程的 `Job.cancel()` 和进程的 `process.destroy()`

### 2. 流式响应

- 实时显示 AI 生成的内容
- 使用 `--output-format stream-json` 参数获取流式输出
- 通过 Kotlin Flow 处理异步数据流

### 3. 会话管理

- 支持新建会话
- 支持会话导出
- 未来可扩展支持会话历史

详细功能说明请参考 [FEATURES.md](./FEATURES.md)

## 注意事项

1. **Claude CLI 版本兼容性**：插件依赖于 claude CLI 的命令行接口，需要注意版本兼容性
2. **权限设置**：默认使用 "default" 权限模式，允许所有操作。在生产环境中可能需要调整
3. **错误处理**：CLI 进程可能因各种原因失败（网络、认证等），需要适当的错误处理
4. **进程管理**：确保在打断或错误时正确清理子进程，避免进程泄漏

## 未来改进方向

1. 支持更多的 Claude CLI 功能
2. 改进错误处理和用户提示
3. 添加会话历史管理
4. 支持自定义快捷键
5. 优化流式响应的显示效果