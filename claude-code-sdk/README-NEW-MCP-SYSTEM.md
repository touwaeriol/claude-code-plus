# Claude Code SDK - 新 MCP 系统使用指南

## 🎉 功能概述

基于用户需求，我们实现了全新的 MCP (Model Context Protocol) 服务器系统，提供了多种便捷的实现方式：

### 🔑 核心特性
- ✅ **基于注解的工具定义** - 使用 `@McpTool` 和 `@ToolParam` 注解自动注册工具
- ✅ **继承式实现** - 继承 `McpServerBase` 抽象类实现服务器
- ✅ **DSL 构建器** - 使用 Kotlin DSL 流畅地定义服务器和工具  
- ✅ **便捷配置** - 丰富的扩展函数和预配置选项
- ✅ **完整集成** - 与现有 Hook 系统无缝集成
- ✅ **兼容性保证** - 与旧版本完全兼容

## 📚 实现方式对比

| 实现方式 | 优点 | 适用场景 |
|---------|------|---------|
| **注解式** | 自动发现、类型安全、文档化 | 复杂服务器、企业级应用 |
| **DSL 式** | 灵活、简洁、函数式风格 | 快速原型、简单工具 |
| **继承式** | 完全控制、高度定制 | 复杂逻辑、特殊需求 |

## 🚀 快速开始

### 方式1: 基于注解的实现

```kotlin
@McpServerConfig(
    name = "calculator", 
    version = "1.0.0",
    description = "数学计算工具服务器"
)
class CalculatorServer : McpServerBase() {
    @McpTool(description = "计算两个数的和")
    suspend fun add(
        @ToolParam("第一个数") a: Double,
        @ToolParam("第二个数") b: Double
    ): Double {
        return a + b
    }
    
    @McpTool(description = "计算两个数的乘积")
    @RateLimit(maxCallsPerMinute = 60)
    suspend fun multiply(
        @ToolParam("乘数1", min = -1000.0, max = 1000.0) x: Double,
        @ToolParam("乘数2", min = -1000.0, max = 1000.0) y: Double
    ): Map<String, Any> {
        return mapOf(
            "result" to (x * y),
            "operation" to "multiplication"
        )
    }
}
```

### 方式2: DSL 构建器实现

```kotlin
val mathServer = mcpServer {
    name = "math_tools"
    version = "1.0.0"
    description = "数学工具服务器"
    
    tool("add", "计算两个数的和") {
        parameters {
            "a" to ParameterType.NUMBER
            "b" to ParameterType.NUMBER
        }
        
        handler { args ->
            val a = (args["a"] as Number).toDouble()
            val b = (args["b"] as Number).toDouble()
            mapOf("result" to (a + b))
        }
    }
    
    tool("factorial", "计算阶乘") {
        parameters {
            "n" to ParameterType.NUMBER
        }
        
        handler { args ->
            val n = (args["n"] as Number).toInt()
            var result = 1
            for (i in 1..n) result *= i
            result
        }
    }
}
```

### 方式3: 便捷配置和使用

```kotlin
// 使用预配置选项
val options = developerOptions().apply {
    addMcpServer("my_calculator", CalculatorServer())
    addCalculatorServer("math")
    addFileSystemServer("fs")
    
    addSecurityHooks()
    addStatisticsHooks()
}

// 或使用构建器
val options2 = buildClaudeCodeOptions("claude-3-5-sonnet-20241022") {
    advancedTools()
    calculator()
    filesystem() 
    systemInfo()
    
    mcpServerDsl("custom") {
        name = "custom_tools"
        tool("ping") { handler { _ -> "pong" } }
    }
    
    securityHooks()
    hooksDsl {
        onPreToolUse("mcp__.*") { toolCall ->
            println("MCP 工具调用: ${toolCall.toolName}")
            allow("MCP 工具已记录")
        }
    }
}

// 创建客户端并使用
val client = ClaudeCodeSdkClient(options)
client.use {
    query("使用计算器工具计算 25 + 17")
    receiveResponse().collect { message ->
        when (message) {
            is AssistantMessage -> println("Claude: ${message.content}")
            is ResultMessage -> return@collect
        }
    }
}
```

## 📖 详细文档

### 注解系统

#### @McpTool 工具注解
```kotlin
@McpTool(
    name = "custom_name",        // 工具名称（可选，默认使用方法名）
    description = "工具描述",     // 工具描述
    async = true                 // 是否异步工具（默认 true）
)
```

#### @ToolParam 参数注解
```kotlin
@ToolParam(
    description = "参数描述",
    required = true,             // 是否必需（默认 true）
    defaultValue = "默认值",     // 默认值（字符串）
    example = "示例值",          // 示例值
    min = 0.0,                  // 最小值（数值类型）
    max = 100.0,                // 最大值（数值类型）
    minLength = 1,              // 最小长度（字符串）
    maxLength = 255             // 最大长度（字符串）
)
```

#### 其他注解
```kotlin
@McpServerConfig(name, version, description)  // 服务器配置
@ToolGroup(group, description)                // 工具分组
@RequiresPermission(permissions, strategy)    // 权限要求
@RateLimit(maxCallsPerMinute, maxCallsPerHour) // 频率限制
@ExperimentalTool(message)                    // 实验性工具标记
@DeprecatedTool(reason, replaceWith, removeInVersion) // 弃用标记
```

### DSL 构建器 API

#### 服务器配置
```kotlin
mcpServer {
    name = "服务器名称"
    version = "1.0.0"
    description = "服务器描述"
    
    onInitialize {
        // 初始化逻辑
        println("服务器初始化完成")
    }
}
```

#### 工具定义
```kotlin
tool("工具名称", "工具描述") {
    parameters {
        "param1" to ParameterType.STRING
        "param2" to ParameterType.NUMBER
        "param3" to ParameterType.BOOLEAN
    }
    
    handler { args ->
        // 工具处理逻辑
        val param1 = args["param1"] as String
        val param2 = (args["param2"] as Number).toDouble()
        // 返回结果
        mapOf("result" to "处理完成")
    }
}
```

#### 快捷构建函数
```kotlin
// 单工具服务器
val pingServer = simpleTool("ping", "简单ping工具") { _ -> "pong" }

// 预定义服务器
val calc = calculatorServer()        // 计算器
val fs = fileSystemServer()          // 文件系统
val sys = systemInfoServer()         // 系统信息
```

### 便捷配置扩展

#### ClaudeCodeOptions 扩展函数
```kotlin
options
    .addMcpServer("name", server)           // 添加服务器实例
    .addMcpServerDsl("name") { /* DSL */ }  // DSL方式添加
    .addCalculatorServer()                  // 添加计算器
    .addFileSystemServer()                  // 添加文件系统
    .addSystemInfoServer()                  // 添加系统信息
    .addSecurityHooks()                     // 添加安全Hook
    .addStatisticsHooks()                   // 添加统计Hook
    .addAllowedTools("tool1", "tool2")      // 添加工具权限
    .addMcpServerWildcardTools("server")    // 添加通配符权限
```

#### 预配置选项
```kotlin
val dev = developerOptions()        // 开发者友好配置
val secure = secureOptions()        // 安全优先配置  
val math = mathOptions()            // 数学计算特化
val file = fileOperationOptions()   // 文件操作特化
```

#### 自定义构建器
```kotlin
buildClaudeCodeOptions("model") {
    basicTools()          // 基础工具
    advancedTools()       // 高级工具
    allTools()            // 所有工具
    
    calculator()          // 快捷服务器
    filesystem()
    systemInfo()
    
    securityHooks()       // 安全Hook
    statisticsHooks()     // 统计Hook
    
    hooksDsl {           // 自定义Hook
        onPreToolUse(".*") { allow("所有工具允许") }
    }
}
```

## 🧪 测试验证

运行集成测试验证功能：

```bash
./gradlew test --tests "NewMcpSystemIntegrationTest"
```

测试涵盖：
- ✅ 注解服务器功能验证
- ✅ DSL构建器功能验证  
- ✅ 便捷配置构建器验证
- ✅ 完整Claude集成测试
- ✅ 参数验证和错误处理

## 📁 项目结构

```
claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/
├── mcp/
│   ├── McpServer.kt                    # 核心接口
│   ├── McpServerBase.kt               # 抽象基类
│   └── annotations/
│       └── McpAnnotations.kt          # 注解定义
├── builders/
│   ├── HookBuilder.kt                 # Hook构建器
│   ├── McpServerBuilder.kt            # MCP服务器构建器
│   └── ClaudeCodeOptionsExtensions.kt # 配置扩展
├── examples/
│   └── ExampleMcpServers.kt           # 示例实现
├── protocol/
│   └── ControlProtocol.kt             # 协议处理（已增强）
└── test/
    ├── FixedHooksAndMcpIntegrationTest.kt  # 原有集成测试
    └── NewMcpSystemIntegrationTest.kt      # 新系统集成测试
```

## 🔄 与现有系统的兼容性

新系统完全向后兼容：

1. **旧版本配置** 继续正常工作
2. **Hook系统** 无缝集成，支持混用
3. **ControlProtocol** 同时支持新旧接口
4. **渐进式迁移** 可逐步升级到新系统

## 💡 最佳实践

### 1. 选择合适的实现方式
- **简单工具** → DSL 构建器
- **复杂服务器** → 注解式实现  
- **特殊需求** → 继承式自定义

### 2. 工具命名规范
```kotlin
// 良好的工具命名
@McpTool(description = "计算两个数的和")
suspend fun add(a: Double, b: Double): Double

// 避免模糊命名
@McpTool(description = "处理数据")
suspend fun process(data: Any): Any
```

### 3. 参数验证
```kotlin
@McpTool(description = "除法运算")
suspend fun divide(
    @ToolParam("被除数") dividend: Double,
    @ToolParam("除数", min = 0.0001) divisor: Double  // 避免除零
): Double {
    return dividend / divisor
}
```

### 4. 错误处理
```kotlin
@McpTool(description = "文件读取")
suspend fun readFile(
    @ToolParam("文件路径") path: String
): ToolResult {
    return try {
        val content = File(path).readText()
        ToolResult.success(content)
    } catch (e: Exception) {
        ToolResult.error("文件读取失败: ${e.message}")
    }
}
```

### 5. 安全考虑
```kotlin
// 使用安全Hook
val options = secureOptions().apply {
    addSecurityHooks(
        dangerousPatterns = listOf("rm", "del", "format", "sudo"),
        allowedCommands = listOf("ls", "cat", "echo")
    )
}
```

## 🎯 总结

新的 MCP 系统为 Claude Code SDK 提供了：

- 🎨 **多种实现风格** - 注解、DSL、继承三种方式
- 🛡️ **类型安全** - 完整的 Kotlin 类型支持
- 📚 **自动文档化** - 注解提供丰富的元数据
- ⚡ **高性能** - 反射缓存和优化的调用路径
- 🔧 **便捷配置** - 丰富的扩展函数和预配置
- 🧪 **完整测试** - 覆盖所有功能的集成测试

立即开始使用新系统，让自定义工具的创建变得更加简单高效！