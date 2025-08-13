# Claude CLI 到 Claude Code SDK 迁移指南

本文档说明了从直接调用 Claude CLI 到使用 Claude Code SDK 的迁移过程和变化。

## 迁移概述

### 迁移前（CLI 方式）
```
Kotlin → ProcessBuilder → claude CLI → Claude API
```

### 迁移后（SDK 方式）
```
Kotlin → ProcessBuilder → Node.js Script → @anthropic-ai/claude-code → Claude API
```

## 主要变化

### 1. 架构变化

| 方面 | 迁移前 | 迁移后 |
|------|--------|--------|
| **依赖** | Claude CLI 二进制 | Node.js + @anthropic-ai/claude-code |
| **通信方式** | 直接进程调用 | Node.js 桥接 |
| **参数格式** | CLI 参数字符串 | JSON 对象 |
| **错误处理** | 进程退出码 + stderr | SDK 标准化错误 |

### 2. 文件变化

#### 新增文件
- `cli-wrapper/package.json` - Node.js 项目配置
- `cli-wrapper/claude-sdk-wrapper.js` - Node.js 桥接脚本
- `cli-wrapper/test-sdk.js` - SDK 集成测试
- `cli-wrapper/README.md` - CLI wrapper 模块文档

#### 修改文件
- `cli-wrapper/src/main/kotlin/com/claudecodeplus/sdk/ClaudeCliWrapper.kt`
  - 移除 CLI 命令构建逻辑
  - 添加 Node.js 脚本调用逻辑
  - 重写参数映射（从 CLI 参数到 JSON）
  - 增强消息解析（处理 Node.js 返回的 JSON 消息）

- `cli-wrapper/build.gradle.kts`
  - 添加 Node.js 依赖安装任务
  - 添加 SDK 测试任务
  - 配置资源打包

## 环境要求变化

### 迁移前要求
- ✅ Claude CLI 已安装且在 PATH 中
- ✅ ANTHROPIC_API_KEY 环境变量

### 迁移后要求
- ✅ Node.js 18+ 已安装且在 PATH 中
- ✅ ANTHROPIC_API_KEY 环境变量
- ✅ npm 包管理器
- ❌ ~~Claude CLI 二进制~~ （不再需要）

## API 变化

### ClaudeCliWrapper.kt

#### 方法名变化
```kotlin
// 迁移前
suspend fun isClaudeCliAvailable(): Boolean

// 迁移后
suspend fun isClaudeCodeSdkAvailable(): Boolean
```

#### 内部实现变化

**参数构建：**
```kotlin
// 迁移前：构建 CLI 参数
val args = mutableListOf<String>()
args.add("--print")
if (options.model != null) args.addAll(listOf("--model", options.model))

// 迁移后：构建 JSON 对象
val jsonInput = buildJsonObject {
    put("prompt", prompt)
    put("options", buildJsonObject {
        options.model?.let { put("model", it) }
        put("cwd", options.cwd)
    })
}.toString()
```

**进程启动：**
```kotlin
// 迁移前：直接调用 claude
val command = buildClaudeCommand(args)
val process = ProcessBuilder(command).start()

// 迁移后：调用 Node.js 脚本
val nodeCommand = buildNodeCommand(scriptPath, jsonInput)
val process = ProcessBuilder(nodeCommand).start()
```

**消息解析：**
```kotlin
// 迁移前：直接解析 JSONL
if (line.trim().startsWith("{")) {
    val json = Json.parseToJsonElement(line.trim())
    processOutputLine(line)
}

// 迁移后：解析包装的消息格式
val jsonMsg = Json.parseToJsonElement(currentLine.trim())
when (jsonMsg["type"]?.jsonPrimitive?.content) {
    "start" -> { /* 处理开始消息 */ }
    "message" -> { /* 处理 Claude 响应 */ }
    "complete" -> { /* 处理完成消息 */ }
    "error" -> { /* 处理错误消息 */ }
}
```

## 配置变化

### Gradle 配置
```kotlin
// 新增任务
tasks.register<Exec>("installNodeDependencies") {
    commandLine("npm", "install")
    workingDir = file(".")
}

tasks.register<Exec>("testSdkIntegration") {
    commandLine("node", "test-sdk.js")
    dependsOn("installNodeDependencies")
}

// JAR 打包配置
tasks.named<Jar>("jar") {
    from(".") {
        include("claude-sdk-wrapper.js")
        include("package.json")
        include("node_modules/**")
        into("nodejs")
    }
}
```

## 使用方式变化

### 开发环境设置

**迁移前：**
```bash
# 安装 Claude CLI
curl -fsSL https://claude.ai/install.sh | sh

# 设置 API Key
export ANTHROPIC_API_KEY="your-key"
```

**迁移后：**
```bash
# 安装 Node.js（如果尚未安装）
# Windows: 下载安装包
# macOS: brew install node
# Linux: apt install nodejs npm

# 安装项目依赖
cd cli-wrapper
npm install

# 设置 API Key
export ANTHROPIC_API_KEY="your-key"
```

### 测试方式

**迁移前：**
```bash
# 测试 CLI 可用性
claude --version

# 运行项目测试
./gradlew :cli-wrapper:test
```

**迁移后：**
```bash
# 测试 Node.js 可用性
node --version

# 测试 SDK 集成
./gradlew :cli-wrapper:testSdkIntegration

# 直接测试 Node.js 脚本
cd cli-wrapper
node test-sdk.js
```

## 故障排除

### 常见迁移问题

1. **Node.js 不可用**
   ```
   Error: Node.js 不可用。请确保已安装 Node.js 18+ 并在 PATH 中。
   ```
   解决：安装 Node.js 18 或更高版本

2. **NPM 依赖缺失**
   ```
   Error: Cannot find module '@anthropic-ai/claude-code'
   ```
   解决：运行 `npm install` 安装依赖

3. **脚本路径不正确**
   ```
   Error: Node.js 脚本不存在: /path/to/claude-sdk-wrapper.js
   ```
   解决：确保构建过程正确执行，脚本文件已生成

4. **API Key 问题**
   ```
   Error: Missing required parameter: ANTHROPIC_API_KEY
   ```
   解决：设置 ANTHROPIC_API_KEY 环境变量

## 向后兼容性

### 保持兼容的部分
- ✅ `ClaudeCliWrapper.QueryOptions` 数据类结构
- ✅ `ClaudeCliWrapper.QueryResult` 返回格式
- ✅ `query()` 方法签名
- ✅ `terminate()` 和 `isProcessAlive()` 方法
- ✅ 输出行回调机制

### 不兼容的部分
- ❌ 直接 CLI 命令访问（现在通过 Node.js 桥接）
- ❌ CLI 特定的错误消息格式
- ❌ `isClaudeCliAvailable()` 方法名（已更名）

## 性能影响

### 预期变化
- **启动延迟**：增加了 Node.js 进程启动时间（约 100-500ms）
- **内存使用**：增加了 Node.js 运行时内存占用（约 20-50MB）
- **稳定性**：提高了错误处理和类型安全性
- **功能性**：获得了 SDK 的完整功能支持

### 优化建议
- 在应用启动时预热 SDK（调用 `isClaudeCodeSdkAvailable()`）
- 考虑进程池化以减少重复启动开销
- 监控 Node.js 进程内存使用

## 总结

迁移到 Claude Code SDK 虽然增加了一些复杂性，但带来了以下优势：
- 🎯 官方支持和维护
- 🔒 更好的类型安全
- 🚀 功能完整性
- 🛠️ 标准化错误处理
- 📈 跟随官方更新

迁移过程对现有 API 影响最小，主要变化集中在底层实现，上层调用代码基本无需修改。