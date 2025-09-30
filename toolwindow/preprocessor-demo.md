# /model 命令拦截功能演示

## 功能概述

实现了一个解耦的消息预处理系统，支持在消息发送给 Claude 之前进行拦截和处理。

## 架构设计

```
用户输入
  ↓
MessagePreprocessorChain (责任链模式)
  ├─ SlashCommandInterceptor (/model, /cost 等)
  ├─ ContextReferenceResolver (@file:path) [未来扩展]
  └─ ... (可扩展)
  ↓
SessionServiceImpl.sendMessage()
  ↓
ClaudeCodeSdkClient.query()
```

## 核心组件

### 1. MessagePreprocessor (接口)
```kotlin
interface MessagePreprocessor {
    suspend fun preprocess(
        message: String,
        client: ClaudeCodeSdkClient,
        sessionId: String
    ): PreprocessResult
}
```

### 2. PreprocessResult (密封类)
```kotlin
sealed class PreprocessResult {
    data class Continue(val message: String) : PreprocessResult()
    data class Intercepted(
        val handled: Boolean = true,
        val feedback: String? = null
    ) : PreprocessResult()
}
```

### 3. SlashCommandInterceptor (实现类)
- 拦截 `/model` 命令
- 支持模型别名：opus, sonnet, sonnet-4.5, haiku
- 调用 `client.setModel()` API
- 返回用户友好的反馈消息

### 4. MessagePreprocessorChain (责任链)
- 按顺序执行多个预处理器
- 支持拦截和继续传递
- 工厂方法 `createDefault()`

## 使用示例

### 用户输入 `/model opus`

**流程**:
```
1. 用户输入: "/model opus"
2. SessionServiceImpl.sendMessage() 调用
3. preprocessorChain.process() 执行
4. SlashCommandInterceptor 识别命令
5. 调用 client.setModel("claude-opus-4-20250514")
6. 返回 PreprocessResult.Intercepted
7. 显示反馈: "✅ 已切换到模型: claude-opus-4-20250514"
8. 不再调用 client.query()
```

### 用户输入普通消息

**流程**:
```
1. 用户输入: "帮我优化这段代码"
2. SessionServiceImpl.sendMessage() 调用
3. preprocessorChain.process() 执行
4. SlashCommandInterceptor 返回 Continue
5. 返回 PreprocessResult.Continue(message)
6. 调用 client.query("帮我优化这段代码")
7. 正常发送给 Claude
```

## 支持的命令

### /model <模型名>
切换 AI 模型

**别名支持**:
- `opus` → `claude-opus-4-20250514`
- `sonnet` → `claude-sonnet-4-20250514`
- `sonnet-4.5` → `claude-sonnet-4-5-20250929`
- `haiku` → `claude-haiku-4-20250514`

**用法示例**:
```
/model opus
/model sonnet-4.5
/model haiku
/model claude-opus-4-20250514  (完整 ID)
```

**错误处理**:
```
/model                         → 显示帮助信息
/model invalid-model           → 尝试切换，显示错误
/model opus (连接失败)         → 捕获异常，显示友好错误
```

## 测试覆盖

### 单元测试 (SlashCommandInterceptorTest.kt)
- ✅ 普通消息继续发送
- ✅ 未知命令继续发送
- ✅ /model 无参数显示帮助
- ✅ /model opus 正确解析
- ✅ /model sonnet-4.5 正确解析
- ✅ /model haiku 正确解析
- ✅ 完整模型 ID 正确使用
- ✅ 错误处理
- ✅ 空格处理
- ✅ 大小写不敏感
- ✅ 多参数只使用第一个

### 集成测试
```kotlin
@Test
fun `integration test model switching via slash command`() = runBlocking {
    // Given
    val mockClient = mockk<ClaudeCodeSdkClient>()
    val service = SessionServiceImpl(
        preprocessorChain = MessagePreprocessorChain.createDefault()
    )

    // When
    service.sendMessage(sessionId, "/model opus", ...)

    // Then
    verify { mockClient.setModel("claude-opus-4-20250514") }
}
```

## 优势

### 1. 解耦设计
- **单一职责**: SessionServiceImpl 只管理会话，不处理命令逻辑
- **清晰边界**: 预处理器独立于服务层
- **模块化**: 每个预处理器独立实现

### 2. 可扩展性
轻松添加新功能：
```kotlin
class ContextReferenceResolver : MessagePreprocessor { ... }
class MacroExpander : MessagePreprocessor { ... }
class TemplateProcessor : MessagePreprocessor { ... }
```

### 3. 可测试性
- 每个组件独立测试
- Mock 友好
- 依赖注入支持

### 4. 用户体验
- 即时反馈
- 友好的错误消息
- 支持别名简化输入

## 未来扩展

### 计划中的命令
```
/cost            → 查询会话成本
/clear           → 清空会话历史
/context add     → 添加上下文引用
/context list    → 列出当前上下文
/export          → 导出会话历史
/permission      → 切换权限模式
```

### 计划中的预处理器
```
ContextReferenceResolver  → 解析 @file:path 引用
MacroExpander            → 展开用户自定义宏
TemplateProcessor        → 处理消息模板
```

## 实现文件

### 新建文件 (4个)
1. `toolwindow/src/main/kotlin/com/claudecodeplus/core/preprocessor/MessagePreprocessor.kt`
2. `toolwindow/src/main/kotlin/com/claudecodeplus/core/preprocessor/SlashCommandInterceptor.kt`
3. `toolwindow/src/main/kotlin/com/claudecodeplus/core/preprocessor/MessagePreprocessorChain.kt`
4. `toolwindow/src/test/kotlin/com/claudecodeplus/core/preprocessor/SlashCommandInterceptorTest.kt`

### 修改文件 (1个)
1. `toolwindow/src/main/kotlin/com/claudecodeplus/core/services/impl/SessionServiceImpl.kt`
   - 添加 `preprocessorChain` 构造函数参数
   - 在 `sendMessage()` 中集成预处理逻辑

## 技术亮点

- ✅ **责任链模式**: MessagePreprocessorChain
- ✅ **策略模式**: 不同的 MessagePreprocessor 实现
- ✅ **工厂模式**: `MessagePreprocessorChain.createDefault()`
- ✅ **依赖注入**: 构造函数注入，测试友好
- ✅ **密封类**: PreprocessResult 类型安全
- ✅ **协程支持**: suspend 函数，异步处理
- ✅ **日志记录**: 完整的日志追踪
- ✅ **错误处理**: 优雅的异常处理和用户反馈

## 总结

这个实现提供了一个**解耦、可扩展、易测试**的消息预处理系统，完美解决了斜杠命令拦截的需求，同时为未来功能扩展奠定了良好的基础。