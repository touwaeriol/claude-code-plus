# jetbrains-ide-mcp 模块 - MCP Java SDK 集成指南

本模块基于 [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) 实现，为 Claude CLI 提供基于 IntelliJ Platform 的代码分析和检查功能。

## 📖 官方文档参考

### 核心文档
- **概述和架构**: https://modelcontextprotocol.io/sdk/java/mcp-overview#architecture
- **功能特性**: https://modelcontextprotocol.io/sdk/java/mcp-overview#features  
- **依赖管理**: https://modelcontextprotocol.io/sdk/java/mcp-overview#dependencies
- **MCP 服务器实现**: https://modelcontextprotocol.io/sdk/java/mcp-server
- **MCP 客户端使用**: https://modelcontextprotocol.io/sdk/java/mcp-client

### GitHub 源码
- **项目地址**: https://github.com/modelcontextprotocol/java-sdk
- **当前版本**: v0.11.2 (2025年8月11日发布)
- **许可证**: MIT License

## 🛠 技术栈和架构

### MCP Java SDK 架构层次
```
Client/Server Layer (McpClient/McpServer)
         ↓
Session Layer (McpSession)  
         ↓
Transport Layer (JSON-RPC 消息序列化)
```

### 本模块技术选型
- **MCP SDK**: `io.modelcontextprotocol.sdk:mcp` v0.11.2
- **传输层**: HTTP SSE (Server-Sent Events) - 适配 Web 环境
- **IDE 集成**: IntelliJ Platform SDK
- **编程模式**: 同步 + 异步 API
- **语言**: Kotlin + Java 互操作

## 📋 依赖配置

### Gradle 依赖 (build.gradle.kts)
```kotlin
dependencies {
    // MCP Java SDK 核心依赖
    implementation("io.modelcontextprotocol.sdk:mcp:0.11.2")
    
    // IntelliJ Platform SDK
    compileOnly("com.jetbrains:ideaIC:2024.3")
    
    // HTTP 服务器支持 (可选)
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    
    // 协程支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
```

### Maven 依赖 (如需要)
```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.11.2</version>
</dependency>
```

## 📋 IntelliJ Platform API 集成

### 真实 API 实现
基于深入研究 IntelliJ Platform 插件开发文档，我们实现了真正的 IDE 分析功能：

#### 核心技术栈
- **PSI (Program Structure Interface)**: 代码结构分析的核心 API
- **Code Inspections**: IntelliJ 的静态代码分析工具
- **ReadAction.nonBlocking()**: 线程安全的后台代码分析
- **InspectionManager**: 检查工具管理器

#### 实现的服务类
1. **SimpleAnalysisService**: 简化但功能完整的分析服务
   - 基于 PSI API 的文件分析
   - 线程安全的异步处理
   - 多语言支持 (Kotlin, Java, Python, JavaScript 等)

2. **SimpleMcpServer**: HTTP MCP 服务器
   - Ktor Netty 服务器实现
   - 标准化的 JSON API 接口
   - 完整的错误处理和日志记录

#### API 能力对比

| 功能 | Mock 版本 | 真实 API 版本 | 说明 |
|------|-----------|---------------|------|
| 文件存在检查 | ✅ | ✅ | 基础文件系统操作 |
| PSI 代码解析 | ❌ | ✅ | 真正的代码结构理解 |
| 语法错误检测 | 模拟 | ✅ | 基于 PSI 的真实检测 |
| 代码复杂度分析 | 模拟 | ✅ | 圈复杂度、方法数等指标 |
| 多语言支持 | 有限 | ✅ | 支持 IntelliJ 支持的所有语言 |
| 线程安全 | ❌ | ✅ | ReadAction 保证安全性 |
| IDE 环境依赖 | 无 | 需要 | 需要在 IntelliJ 插件环境运行 |

## 🚀 基础服务器实现

### 1. 同步服务器创建
```kotlin
import io.modelcontextprotocol.sdk.McpServer
import io.modelcontextprotocol.sdk.server.ServerCapabilities

val syncServer = McpServer.sync(transportProvider)
    .serverInfo("jetbrains-ide-mcp", "1.0.0")
    .capabilities(
        ServerCapabilities.builder()
            .tools(true)           // 支持工具调用
            .resources(false)      // 暂不支持资源
            .prompts(false)        // 暂不支持提示模板  
            .logging()             // 启用日志
            .build()
    )
    .build()
```

### 2. 异步服务器创建
```kotlin
val asyncServer = McpServer.async(transportProvider)
    .serverInfo("jetbrains-ide-mcp", "1.0.0")
    .capabilities(
        ServerCapabilities.builder()
            .tools(true)
            .logging()
            .build()
    )
    .build()
```

## 🔧 工具注册和实现

### 核心工具列表
本模块将实现以下 MCP 工具，供 Claude CLI 调用：

1. **`check_file_errors`** - 文件错误检查
   - 检查语法错误、类型错误、编译错误
   - 基于 `DaemonCodeAnalyzer` 实现

2. **`analyze_code_quality`** - 代码质量分析
   - 代码复杂度、重复代码、坏味道检测
   - 基于 `LocalInspectionTool` 实现

3. **`get_inspection_results`** - 获取 IntelliJ 检查结果
   - 返回完整的代码检查报告
   - 支持自定义检查范围

4. **`validate_syntax`** - 快速语法验证
   - 轻量级语法检查
   - 支持多种编程语言

### 工具实现模式
```kotlin
@Tool("check_file_errors")
fun checkFileErrors(
    @Parameter("filePath") filePath: String,
    @Parameter("checkLevel") checkLevel: String = "all"
): ToolResult {
    // 使用 IntelliJ Platform API 检查文件
    // 返回结构化的错误信息
}
```

## 🔌 与 jetbrains-plugin 模块集成

### 依赖关系
```kotlin
// jetbrains-plugin/build.gradle.kts
dependencies {
    implementation(project(":jetbrains-ide-mcp"))
}
```

### 集成方式
1. **服务启动**: 插件启动时自动启动 MCP 服务器
2. **配置管理**: 扩展现有 `McpSettingsService` 
3. **生命周期**: 与插件生命周期同步
4. **端口管理**: 默认使用端口 8001，支持配置

### MCP 配置注册
```json
{
  "mcpServers": {
    "jetbrains-ide": {
      "command": "http://localhost:8001",
      "transport": "http"
    }
  }
}
```

## 🏗 模块结构

```
jetbrains-ide-mcp/
├── build.gradle.kts              # 模块构建配置
├── CLAUDE.md                     # 本文档
└── src/main/kotlin/
    └── com/claudecodeplus/mcp/
        ├── server/               # MCP 服务器实现
        │   ├── IdeaMcpServer.kt       # 主服务器类
        │   ├── ToolRegistry.kt        # 工具注册管理
        │   └── TransportProvider.kt   # 传输层提供者
        ├── tools/                # IDE 检查工具实现
        │   ├── FileAnalysisTool.kt    # 文件分析工具
        │   ├── CodeQualityTool.kt     # 代码质量工具
        │   └── SyntaxValidationTool.kt # 语法验证工具
        └── services/             # IDE 平台适配服务
            ├── IdeAnalysisService.kt  # IDE 分析服务封装
            └── InspectionService.kt   # 检查服务适配器
```

## 🚦 开发指引

### 1. 阅读顺序
建议按以下顺序阅读官方文档：
1. [MCP 概述](https://modelcontextprotocol.io/sdk/java/mcp-overview) - 了解整体架构
2. [服务器实现](https://modelcontextprotocol.io/sdk/java/mcp-server) - 学习服务器创建
3. [功能特性](https://modelcontextprotocol.io/sdk/java/mcp-overview#features) - 掌握核心功能
4. [GitHub 源码](https://github.com/modelcontextprotocol/java-sdk) - 查看实际示例

### 2. 开发步骤
1. 实现基础 MCP 服务器框架
2. 集成 IntelliJ Platform API
3. 实现具体的检查工具
4. 测试工具调用和结果返回
5. 与 jetbrains-plugin 模块集成

### 3. 调试技巧
- 启用 MCP SDK 内置日志功能
- 使用 IntelliJ Platform 的调试工具
- 通过 Claude CLI 测试工具调用

## 🎯 预期效果

完成本模块后，Claude CLI 将能够：
- 实时获取 IDE 的代码分析结果
- 调用 IntelliJ 的静态检查功能
- 获得专业的代码质量评估
- 享受 IDE 级别的错误检测能力

这将显著增强 Claude CLI 在代码分析和质量检查方面的能力。