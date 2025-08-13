# Claude Code SDK Wrapper

本模块提供了基于 Node.js 的 Claude Code SDK 包装器，用于在 Kotlin 项目中调用 Anthropic 的 Claude Code SDK。

## 架构说明

### 原架构（直接调用 CLI）
```
Kotlin ClaudeCliWrapper → ProcessBuilder → claude CLI → stdout/stderr
```

### 新架构（基于 SDK）
```
Kotlin ClaudeCliWrapper → ProcessBuilder → Node.js 脚本 → @anthropic-ai/claude-code SDK → JSON 输出
```

## 文件结构

- `claude-sdk-wrapper.js` - Node.js 桥接脚本，使用 @anthropic-ai/claude-code SDK
- `package.json` - Node.js 项目配置和依赖
- `test-sdk.js` - 测试脚本
- `ClaudeCliWrapper.kt` - Kotlin 包装器类

## 环境要求

1. **Node.js 18+** - 必须安装并在 PATH 中可用
2. **@anthropic-ai/claude-code** - SDK 依赖包（通过 npm install 自动安装）
3. **API Key** - 需要设置 ANTHROPIC_API_KEY 环境变量

## 使用方法

### 1. 安装依赖

```bash
cd cli-wrapper
npm install
```

### 2. 设置 API Key

```bash
export ANTHROPIC_API_KEY="your-api-key-here"
```

### 3. 测试 SDK

```bash
# 直接测试 Node.js 脚本
node claude-sdk-wrapper.js '{"prompt": "Hello", "options": {"cwd": ".", "maxTurns": 1}}'

# 或使用测试脚本
node test-sdk.js
```

### 4. 在 Kotlin 中使用

```kotlin
import com.claudecodeplus.sdk.ClaudeCliWrapper

val wrapper = ClaudeCliWrapper()
val options = ClaudeCliWrapper.QueryOptions(
    cwd = "/path/to/project",
    model = "sonnet",
    maxTurns = 5
)

val result = wrapper.query("Hello, Claude!", options)
println("Session ID: ${result.sessionId}")
```

## 主要变化

### Kotlin 端

1. **移除了 CLI 命令构建逻辑** - 不再直接调用 `claude` 命令
2. **改为调用 Node.js 脚本** - 通过 ProcessBuilder 启动 Node.js 脚本
3. **JSON 参数传递** - 将所有参数序列化为 JSON 传递给 Node.js
4. **增强的消息解析** - 解析 Node.js 脚本返回的 JSON 消息格式

### Node.js 端

1. **SDK 集成** - 使用官方 @anthropic-ai/claude-code SDK
2. **参数映射** - 将 Kotlin 参数映射到 SDK 选项
3. **消息转发** - 将 SDK 返回的消息转换为 JSON 格式输出
4. **错误处理** - 完善的错误处理和日志记录

## 优势

1. **类型安全** - 使用官方 SDK，获得更好的类型检查
2. **更好的错误处理** - SDK 提供了更清晰的错误信息
3. **功能完整性** - 支持 SDK 的所有功能
4. **维护性** - 跟随 SDK 版本更新，无需维护 CLI 参数映射

## 故障排除

### 1. Node.js 不可用
```
Error: Node.js 不可用。请确保已安装 Node.js 18+ 并在 PATH 中。
```
解决方案：安装 Node.js 18 或更高版本

### 2. SDK 脚本不存在
```
Error: Node.js 脚本不存在: /path/to/claude-sdk-wrapper.js
```
解决方案：确保 claude-sdk-wrapper.js 文件存在并且具有正确的权限

### 3. API Key 缺失
```
Error: Missing required parameter: ANTHROPIC_API_KEY
```
解决方案：设置 ANTHROPIC_API_KEY 环境变量

### 4. 依赖包缺失
```
Error: Cannot find module '@anthropic-ai/claude-code'
```
解决方案：运行 `npm install` 安装依赖包

## 日志

- Kotlin 日志：通过 LoggerFactory 输出到标准日志系统
- Node.js 日志：写入 `claude-wrapper.log` 文件，避免干扰 stdout

## 开发调试

启用调试模式：

```kotlin
val options = ClaudeCliWrapper.QueryOptions(
    cwd = "/path/to/project",
    debug = true,  // 启用调试
    verbose = true
)
```

查看 Node.js 日志：
```bash
tail -f cli-wrapper/claude-wrapper.log
```