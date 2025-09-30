# 🔍 模型切换功能分析报告

## 📋 问题追踪

用户问题: **"你测试了吗 真的切换模型了？"**

## ✅ 单元测试情况

### 已完成的单元测试
位置: `toolwindow/src/test/kotlin/com/claudecodeplus/core/preprocessor/SlashCommandInterceptorTest.kt`

**测试结果**: ✅ 11/11 通过 (100%)

**测试内容**:
1. ✅ 拦截器识别 `/model` 命令
2. ✅ 解析模型别名 (opus, sonnet, sonnet-4.5, haiku)
3. ✅ 调用 `client.setModel()` API
4. ✅ 错误处理和用户反馈

**Mock 验证**:
```kotlin
coVerify(exactly = 1) { mockClient.setModel("claude-opus-4-20250514") }
```

## ⚠️ 实际运行验证 - 尚未完成

### 问题：之前的"测试"不是真正的测试

检查了以下测试文件：
1. `RealModelSwitchTest.kt` - ❌ 将 `/model opus` 当作**文本**发送
2. `ModelSwitchTest.kt` - ❌ 将 `/model opus` 当作**文本**发送
3. `CompleteModelSwitchTest.kt` - ❌ 将 `/model opus` 当作**文本**发送

**核心问题**：
```kotlin
// ❌ 错误的测试方式（之前的测试）
client.query("/model opus")  // 这会把 /model 当文本发给 Claude

// ✅ 正确的测试方式（应该做的）
client.setModel("claude-opus-4-20250514")  // 直接调用 SDK API
```

### 为什么之前的测试无效？

1. **SDK 层没有拦截逻辑**
   - `ClaudeCodeSdkClient.query()` 直接发送消息给 Claude CLI
   - SDK 不知道 `/model` 是特殊命令

2. **拦截器在 toolwindow 模块**
   - `SlashCommandInterceptor` 位于 `toolwindow/src/main/kotlin/`
   - 集成在 `SessionServiceImpl` 的 `sendMessage()` 方法中
   - SDK 测试无法触发拦截器

3. **两层架构**:
   ```
   UI层 (toolwindow)                SDK层 (claude-code-sdk)
   ↓                                ↓
   SessionServiceImpl               ClaudeCodeSdkClient
   ↓                                ↓
   MessagePreprocessorChain   →     直接调用 query()
   ↓
   SlashCommandInterceptor
   ↓
   client.setModel() ————————————→  发送控制请求
   ```

## 🔬 SDK setModel() API 分析

### 控制协议实现

查看 `ClaudeCodeSdkClient.kt:294-302`:

```kotlin
suspend fun setModel(model: String?) {
    ensureConnected()
    logger.info("🤖 设置模型: ${model ?: "default"}")

    val request = SetModelRequest(model = model)
    controlProtocol!!.sendControlRequest(request)  // 发送控制请求

    logger.info("✅ 模型已更新为: ${model ?: "default"}")
}
```

### 控制请求格式

查看 `ControlProtocol.kt:448-471`:

```kotlin
private suspend fun sendControlRequestInternal(request: JsonObject): ControlResponse {
    val requestId = "req_${requestCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    val deferred = CompletableDeferred<ControlResponse>()
    pendingRequests[requestId] = deferred

    val requestMessage = buildJsonObject {
        put("type", "control_request")
        put("request_id", requestId)
        put("request", request)
    }

    transport.write(requestMessage.toString())
    return withTimeout(30000) { // 30秒超时
        deferred.await()  // 等待响应
    }
}
```

**关键点**:
- ✅ `setModel()` 发送控制请求到 Claude CLI
- ✅ 使用 `control_request` 消息类型
- ✅ 等待 `control_response` 确认（30秒超时）
- ✅ 请求格式: `{"type":"control_request","request_id":"...","request":{"type":"set_model","model":"..."}}`

## ❓ 需要验证的问题

### 1. Claude CLI 是否支持 set_model 控制命令？

**待验证**: Claude Code CLI 是否实现了 `set_model` 控制请求处理？

**如何验证**:
```bash
# 方法1: 查看 Claude Code CLI 文档
claude help

# 方法2: 运行真实测试（需要 API Key）
./gradlew :claude-code-sdk:test --tests "DirectSetModelTest.test setModel API with real CLI"

# 方法3: 手动测试（使用 JSON 流模式）
echo '{"type":"control_request","request_id":"test1","request":{"type":"set_model","model":"claude-opus-4-20250514"}}' | claude --stream-json
```

### 2. 模型切换后是否真的生效？

**待验证**: 调用 `setModel()` 后，下次查询是否使用新模型？

**验证方法**:
1. 连接 Claude CLI (初始模型: Sonnet)
2. 发送查询1，查看响应中的 `model` 字段
3. 调用 `setModel("claude-opus-4-20250514")`
4. 发送查询2，检查响应中的 `model` 字段是否变为 Opus

### 3. SlashCommandInterceptor 在实际应用中是否触发？

**待验证**: 在 IntelliJ 插件中输入 `/model opus` 时，是否被拦截？

**验证方法**:
1. 启动插件 (`./gradlew :jetbrains-plugin:runIde`)
2. 打开 Claude Code Plus 工具窗口
3. 输入 `/model opus`
4. 查看日志，确认拦截器被触发
5. 检查是否调用了 `client.setModel()`

## 📊 测试覆盖率总结

| 测试类型 | 状态 | 说明 |
|---------|------|------|
| 单元测试 | ✅ 100% | 11/11 通过，Mock 验证正确 |
| 集成测试 (SDK) | ⚠️ 未完成 | 需要真实 API Key |
| 端到端测试 (UI) | ❌ 未测试 | 需要运行插件 |
| Claude CLI 支持 | ❓ 未知 | 待验证 CLI 是否支持 set_model |

## 🎯 下一步行动

### 选项A: 快速验证（推荐）
```bash
# 如果有 CLAUDE_API_KEY
export CLAUDE_API_KEY="your-key"
./gradlew :claude-code-sdk:test --tests "DirectSetModelTest"
```

### 选项B: 手动测试
1. 运行插件: `./gradlew :jetbrains-plugin:runIde`
2. 在 IDE 中测试 `/model opus` 命令
3. 观察日志输出

### 选项C: 查阅文档
```bash
claude --help
claude code --help
```

## 💡 结论

**单元测试层面**: ✅ 功能完全正确
- 拦截器识别命令 ✅
- 调用正确的 API ✅
- 错误处理完善 ✅
- Mock 验证通过 ✅

**实际运行层面**: ⚠️ 需要验证
- SDK `setModel()` 发送正确的控制请求格式 ✅
- **但未验证 Claude CLI 是否支持 set_model** ❓
- **未验证模型切换后是否真的生效** ❓

**关键问题**: Claude Code CLI 是否实现了 `set_model` 控制命令？这需要：
1. 查阅 Claude Code CLI 官方文档
2. 运行带 API Key 的真实测试
3. 或查看 Claude Code CLI 源码（如果开源）

---

**创建时间**: 2025-09-30
**作者**: Claude Assistant
**文件**: MODEL-SWITCH-ANALYSIS.md