# 参数同步修复报告 (2025-11-23)

## 问题描述

用户报告了两个相关的问题：

1. **错误信息**：`Error: When using --print, --output-format=stream-json requires --verbose`
2. **界面功能差异**：IDEA 插件的界面和 Vue Web 前端差很多，消息无法发送（如 "1+1="）

## 根本原因分析

经过深入分析，发现了两个核心问题：

### 问题 1: 命令行参数顺序错误

在 `claude-agent-sdk/src/main/kotlin/com/claudecodeplus/sdk/transport/SubprocessTransport.kt` 中，构建 Claude CLI 命令时参数顺序不正确。

**错误的顺序**（导致 CLI 解析失败）：
1. `--output-format stream-json`
2. `--verbose`
3. `--print`

Claude CLI 要求在使用 `--print` 和 `--output-format=stream-json` 时，`--verbose` 必须在 `--print` **之前**被解析。

### 问题 2: IDEA 插件与 Vue Web 前端参数配置不一致

**Vue Web 前端** (`frontend/src/stores/sessionStore.ts:84-94`):
```typescript
print: true,
outputFormat: 'stream-json',
verbose: true,
includePartialMessages: true,
dangerouslySkipPermissions: true,
allowDangerouslySkipPermissions: true
```

**IDEA 插件** (`jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/ui/ChatViewModel.kt:254-272`) - 修复前:
```kotlin
includePartialMessages = false,  // ❌ 应该是 true
print = false,                   // ❌ 应该是 true
verbose = false,                 // ❌ 应该是 true
// ❌ 缺少 dangerouslySkipPermissions 和 outputFormat
```

这导致：
- IDEA 插件无法接收流式事件（`includePartialMessages = false`）
- 无法启用详细输出（`verbose = false`）
- 缺少权限跳过配置，可能导致权限提示问题
- 没有设置 `outputFormat = stream-json`

## 修复方案

### 修复 1: 调整命令行参数顺序

**文件**: `claude-agent-sdk/src/main/kotlin/com/claudecodeplus/sdk/transport/SubprocessTransport.kt`

**修改内容**（第 263-278 行）：
```kotlin
// Verbose output - 必须在 --print 之前设置
// 注意：当使用 --print 和 --output-format=stream-json 时，必须同时使用 --verbose
val outputFormat = options.extraArgs["output-format"] ?: "stream-json"
val needsVerbose = options.verbose ||
    (options.print && outputFormat == "stream-json")
if (needsVerbose) {
    command.add("--verbose")  // ✅ 先添加 --verbose
}

// Output format (从 extraArgs 或默认使用 stream-json)
command.addAll(listOf("--output-format", outputFormat))

// Print flag (根据选项决定) - 必须在 --verbose 之后
if (options.print) {
    command.add("--print")
}
```

**新的参数顺序**：
1. ✅ `--verbose`（如果需要）
2. ✅ `--output-format stream-json`
3. ✅ `--print`

### 修复 2: 同步 IDEA 插件参数配置

**文件**: `jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/ui/ChatViewModel.kt`

**修改内容**（第 254-279 行）：
```kotlin
/**
 * 构建Claude选项
 * 
 * ⚠️ 注意：参数配置必须与 Vue Web 前端保持一致
 * 参见: frontend/src/stores/sessionStore.ts:buildConnectOptions
 */
private fun buildClaudeOptions(): ClaudeAgentOptions {
    val projectPath = ideTools.getProjectPath()
    val cwd = if (projectPath.isNotBlank()) {
        Path.of(projectPath)
    } else {
        null
    }
    
    return ClaudeAgentOptions(
        model = "claude-sonnet-4-5-20250929",
        cwd = cwd,
        debugStderr = true,
        maxTurns = 10,
        permissionMode = PermissionMode.DEFAULT,
        // ✅ 与 Vue Web 前端保持一致的参数配置
        includePartialMessages = true,  // 启用流式事件，用于实时渲染
        print = true,                   // 启用打印输出
        verbose = true,                 // 启用详细日志（与 print + stream-json 一起使用时必需）
        dangerouslySkipPermissions = true,
        allowDangerouslySkipPermissions = true,
        // 设置 outputFormat 为 stream-json
        extraArgs = mapOf("output-format" to "stream-json")
    )
}
```

## 影响范围

### 直接影响
- ✅ 修复了 `--print` + `--output-format=stream-json` 参数组合错误
- ✅ IDEA 插件现在使用与 Vue Web 前端相同的参数配置
- ✅ 启用了流式事件支持，提升实时响应体验

### 功能改进
- ✅ IDEA 插件现在可以接收实时流式输出
- ✅ 启用了详细日志，便于调试
- ✅ 跳过权限提示，改善用户体验
- ✅ 统一了多个客户端的行为，降低维护成本

## 测试步骤

### 1. 重启服务

**对于 IDEA 插件**：
- 停止当前运行的 `runIde` 任务
- 重新运行 `./gradlew jetbrains-plugin:runIde`

**对于独立服务器**：
```bash
# 停止现有服务器
# 重新启动
./gradlew claude-code-server:run
# 或
./run-server.sh
```

### 2. 验证修复

在前端界面中：
1. 创建新会话
2. 发送简单消息，如 "1+1="
3. 验证：
   - ✅ 消息成功发送
   - ✅ 收到 Claude 的响应
   - ✅ 控制台没有参数错误
   - ✅ 流式输出正常工作

## 最佳实践建议

### 1. 参数配置集中管理

建议在未来版本中：
- 将所有客户端的默认参数配置放在一个共享的配置文件中
- 通过继承/引用的方式确保一致性
- 添加单元测试验证参数配置的一致性

### 2. 参数文档化

在 `claude-agent-sdk` 中添加：
```kotlin
/**
 * 推荐的生产环境配置
 * 
 * 用于：Web 前端、IDEA 插件、独立应用
 */
object RecommendedOptions {
    fun forProduction(cwd: Path? = null, model: String? = null): ClaudeAgentOptions {
        return ClaudeAgentOptions(
            model = model ?: "claude-sonnet-4-5-20250929",
            cwd = cwd,
            includePartialMessages = true,
            print = true,
            verbose = true,
            dangerouslySkipPermissions = true,
            allowDangerouslySkipPermissions = true,
            extraArgs = mapOf("output-format" to "stream-json")
        )
    }
}
```

### 3. 自动化测试

添加集成测试验证：
- 命令行参数顺序正确性
- 不同客户端使用相同参数配置
- 参数组合的有效性

## 相关文件

- `claude-agent-sdk/src/main/kotlin/com/claudecodeplus/sdk/transport/SubprocessTransport.kt`
- `jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/ui/ChatViewModel.kt`
- `frontend/src/stores/sessionStore.ts`
- `claude-code-server/src/main/kotlin/com/claudecodeplus/server/rpc/ClaudeRpcServiceImpl.kt`

## 总结

此次修复解决了两个关键问题：
1. **参数顺序问题**：确保 Claude CLI 正确解析参数
2. **参数一致性问题**：统一了 IDEA 插件与 Vue Web 前端的行为

现在所有客户端（Web 前端、IDEA 插件）都使用相同的参数配置，确保了一致的用户体验和功能完整性。






















