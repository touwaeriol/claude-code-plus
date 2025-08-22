# jetbrains-ide-mcp 模块

基于 [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) 实现的 IntelliJ Platform MCP 服务器，为 Claude CLI 提供 IDE 级别的代码分析功能。

## 🎯 功能概述

### 核心 MCP 工具
1. **`check_file_errors`** - 文件错误检查
   - 语法错误、类型错误、编译错误检测
   - 支持多种检查级别：error、warning、all

2. **`analyze_code_quality`** - 代码质量分析  
   - 代码复杂度、重复代码、可维护性评估
   - 支持自定义分析指标

3. **`validate_syntax`** - 快速语法验证
   - 轻量级语法正确性检查
   - 快速反馈语法问题

### 技术特性
- ✅ **基于 MCP Java SDK v0.11.2**
- ✅ **HTTP 传输层** (默认端口 8001)
- ✅ **IntelliJ Platform API 集成**
- ✅ **协程支持** (Kotlin Coroutines)
- ✅ **结构化日志**
- ✅ **并发安全**

## 🚀 快速开始

### 1. 模块集成
`jetbrains-ide-mcp` 模块已自动集成到 `jetbrains-plugin` 中：

```kotlin
// jetbrains-plugin/build.gradle.kts
dependencies {
    implementation(project(":jetbrains-ide-mcp"))
}
```

### 2. 在插件中使用
```kotlin
import com.claudecodeplus.plugin.services.McpServerManager

// 获取项目的 MCP 服务器管理器
val mcpManager = project.getService(McpServerManager::class.java)

// 启动 MCP 服务器
mcpManager.startMcpServer(port = 8001)

// 检查服务器状态
if (mcpManager.isServerRunning()) {
    println("MCP 服务器正在运行")
}

// 停止 MCP 服务器
mcpManager.stopMcpServer()
```

### 3. Claude CLI 配置
在 Claude CLI 的 MCP 配置中添加：

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

## 🔧 API 端点

### 服务器信息
```
GET http://localhost:8001/
```
返回服务器基本信息和能力说明。

### 健康检查
```
GET http://localhost:8001/health
```
返回服务器健康状态。

### 工具列表
```
GET http://localhost:8001/tools/
```
返回所有可用的 MCP 工具列表。

### 工具调用示例
```bash
# 检查文件错误
curl -X POST http://localhost:8001/tools/check_file_errors \
  -H "Content-Type: application/json" \
  -d '{"filePath": "/path/to/file.kt", "checkLevel": "all"}'

# 分析代码质量
curl -X POST http://localhost:8001/tools/analyze_code_quality \
  -H "Content-Type: application/json" \
  -d '{"filePath": "/path/to/file.kt", "metrics": ["complexity", "maintainability"]}'

# 验证语法
curl -X POST http://localhost:8001/tools/validate_syntax \
  -H "Content-Type: application/json" \
  -d '{"filePath": "/path/to/file.kt"}'
```

## 📁 项目结构

```
jetbrains-ide-mcp/
├── build.gradle.kts              # 模块构建配置
├── CLAUDE.md                     # MCP SDK 使用指南 
├── README.md                     # 本文档
└── src/main/kotlin/
    └── com/claudecodeplus/mcp/
        ├── server/               # MCP 服务器实现
        │   └── IdeaMcpServer.kt       # 主服务器类
        ├── tools/                # IDE 检查工具
        │   ├── FileAnalysisTool.kt    # 文件分析工具
        │   ├── CodeQualityTool.kt     # 代码质量工具
        │   └── SyntaxValidationTool.kt # 语法验证工具
        ├── services/             # IDE 平台适配
        │   └── IdeAnalysisService.kt  # 分析服务封装
        └── TestMcpServer.kt           # 测试程序
```

## 🔗 依赖关系

### Maven 坐标
```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.11.2</version>
</dependency>
```

### Gradle 依赖
```kotlin
dependencies {
    implementation("io.modelcontextprotocol.sdk:mcp:0.11.2")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
```

## 📖 相关文档

- **MCP SDK 使用指南**: [CLAUDE.md](./CLAUDE.md) 
- **官方文档**: https://modelcontextprotocol.io/sdk/java/mcp-overview
- **GitHub 仓库**: https://github.com/modelcontextprotocol/java-sdk
- **服务器文档**: https://modelcontextprotocol.io/sdk/java/mcp-server
- **客户端文档**: https://modelcontextprotocol.io/sdk/java/mcp-client

## 🚦 开发状态

### ✅ 已完成
- [x] 模块基础架构
- [x] MCP 服务器框架
- [x] IDE 平台集成
- [x] 基础工具实现
- [x] HTTP 传输层
- [x] 项目构建配置

### 🚧 进行中
- [ ] 完善 IDE 分析功能
- [ ] 增强错误检测能力
- [ ] 优化性能和并发

### 📋 待实现
- [ ] 更多代码质量指标
- [ ] 批量文件分析
- [ ] 实时错误监听
- [ ] 配置热重载

## 🤝 贡献指南

1. **开发环境**: IntelliJ IDEA + JDK 17
2. **构建命令**: `./gradlew jetbrains-ide-mcp:build`
3. **测试命令**: `./gradlew jetbrains-ide-mcp:test`
4. **代码规范**: 遵循项目 Kotlin 编码标准

## 📄 许可证

本项目遵循与主项目相同的许可证。MCP Java SDK 使用 MIT 许可证。