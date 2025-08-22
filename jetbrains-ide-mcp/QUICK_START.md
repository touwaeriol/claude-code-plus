# MCP 快速开始指南

## 🚀 快速启动

### 1. 查看参数格式定义
```bash
./gradlew jetbrains-ide-mcp:run
```
这将运行 `ParameterFormatDemo`，展示所有 5 个 MCP 工具的标准参数格式。

### 2. 选择实现方式

我们提供两种实现方式：

#### 🔹 简化实现 (推荐用于开发和测试)
```kotlin
import com.claudecodeplus.mcp.server.McpServerBuilder

// 启动简化版 MCP 服务器
val server = McpServerBuilder(project)
    .port(8001)
    .useSimpleImplementation()
    .build()
server.start()
```

**特点**:
- ✅ 无需完整 IntelliJ 环境
- ✅ 启动快速，资源占用少
- ✅ 基于文本分析的基础功能
- ❌ 语言检测基于文件扩展名
- ❌ 无法使用 IntelliJ 检查工具

#### 🔸 IntelliJ API 实现 (推荐用于生产环境)
```kotlin
import com.claudecodeplus.mcp.server.McpServerBuilder

// 启动完整 API 版 MCP 服务器
val server = McpServerBuilder(project)
    .port(8001)
    .useIntelliJApiImplementation()
    .build()
server.start()
```

**特点**:
- ✅ 原生语言自动检测 (`psiFile.language`)
- ✅ 精确的 PSI 代码分析
- ✅ 完整的 IntelliJ 检查工具集成
- ✅ 语言特定的深度分析
- ❌ 需要 IntelliJ 插件环境
- ❌ 资源占用较大

### 3. Claude CLI 配置
在 Claude CLI 配置中添加：
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

## 🔧 可用的 MCP 工具

### 1. check_file_errors
检查文件的语法错误和代码问题
```bash
curl -X POST http://localhost:8001/tools/check_file_errors \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "/path/to/your/file.kt",
    "checkLevel": "all",
    "includeInspections": true,
    "maxErrors": 50
  }'
```

### 2. analyze_code_quality  
分析代码质量指标
```bash
curl -X POST http://localhost:8001/tools/analyze_code_quality \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "/path/to/your/file.kt", 
    "metrics": ["complexity", "maintainability"],
    "includeDetails": true
  }'
```

### 3. validate_syntax
验证文件语法正确性
```bash
curl -X POST http://localhost:8001/tools/validate_syntax \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "/path/to/your/file.kt",
    "strict": false,
    "includeWarnings": true
  }'
```

## 📋 服务器状态检查

### 健康检查
```bash
curl http://localhost:8001/health
```

### 工具列表
```bash  
curl http://localhost:8001/tools
```

### 服务器信息
```bash
curl http://localhost:8001/
```

## 📚 文档指南

- `CLAUDE.md` - MCP Java SDK 使用指南
- `INTELLIJ_PLATFORM_API_GUIDE.md` - IntelliJ Platform API 集成指南  
- `IMPLEMENTATION_STATUS.md` - 实现状态和进度报告

## ⚠️ 重要提醒

1. **环境要求**: MCP 服务器需要在 IntelliJ 插件环境中运行
2. **项目依赖**: 确保 `project` 对象可用
3. **文件路径**: 使用绝对路径访问要分析的文件
4. **线程安全**: 服务器内部使用 ReadAction 确保线程安全