# 项目路径处理

本文档说明插件如何在与 Claude SDK 服务器通信时处理项目路径。

## 概述

插件会自动将当前 IntelliJ IDEA 项目的路径作为工作目录（cwd）传递给 Claude SDK，这使得 Claude 能够：
- 正确理解相对文件路径
- 读取和编辑项目中的文件
- 执行项目相关的命令

## 实现细节

### 1. 初始化阶段

在 `SimpleChatWindow.initializeSession()` 中：

```kotlin
val projectPath = project.basePath ?: System.getProperty("user.dir")
val initialized = service.initializeWithConfig(
    cwd = projectPath,
    skipUpdateCheck = true
)
```

- 优先使用 `project.basePath`（IntelliJ 项目根目录）
- 如果项目路径不可用，回退到当前用户目录
- 通过 `cwd` 参数传递给服务器

### 2. 消息发送阶段

在 `SimpleChatWindow.sendMessage()` 中：

```kotlin
val projectPath = project.basePath
val options = projectPath?.let {
    mapOf("cwd" to it)
}
service.sendMessageStream(message, forceNewSession, options)
```

- 每条消息都包含当前项目路径
- 通过 `options` 参数传递
- 确保 Claude 始终在正确的上下文中工作

### 3. 服务器端处理

Python 服务器（unified_server.py）接收并处理 cwd：

```python
# HTTP 请求
{
  "message": "用户消息",
  "options": {
    "cwd": "/path/to/project"
  }
}

# WebSocket 消息
{
  "command": "message",
  "message": "用户消息",
  "options": {
    "cwd": "/path/to/project"
  }
}
```

服务器将 cwd 传递给 ClaudeCodeOptions，使 Claude SDK 在指定目录下工作。

## 使用场景

### 1. 文件操作
当用户请求读取或编辑文件时，Claude 会相对于项目根目录解析路径：
- "读取 src/main.py" → 解析为 "/project/path/src/main.py"
- "创建 config.json" → 创建在 "/project/path/config.json"

### 2. 命令执行
执行命令时会在项目目录下运行：
- "运行测试" → 在项目根目录执行测试命令
- "安装依赖" → 在项目目录安装包

### 3. 代码分析
分析代码结构时基于项目路径：
- 导入路径解析
- 模块查找
- 配置文件定位

## 故障排查

### 问题：Claude 找不到文件
**原因**：项目路径未正确传递
**解决**：
1. 检查 IntelliJ 是否正确识别项目根目录
2. 查看服务器日志确认收到的 cwd 参数
3. 确保文件路径相对于项目根目录

### 问题：命令在错误的目录执行
**原因**：cwd 参数丢失或错误
**解决**：
1. 确保每个请求都包含 options.cwd
2. 验证 project.basePath 不为 null
3. 检查服务器端 ClaudeCodeOptions 配置

### 问题：初始化失败
**原因**：项目路径包含特殊字符或不存在
**解决**：
1. 确保项目路径存在且可访问
2. 处理路径中的空格和特殊字符
3. 使用绝对路径而非相对路径

## 最佳实践

1. **始终传递项目路径**：即使 Claude 可能记住之前的路径，每次请求都应包含 cwd
2. **使用绝对路径**：避免相对路径带来的歧义
3. **验证路径存在**：在发送前检查项目路径是否有效
4. **处理多项目**：如果用户同时打开多个项目，确保使用正确的项目路径
5. **日志记录**：在关键位置记录项目路径，便于调试

## 未来改进

1. **路径缓存**：缓存项目路径以提高性能
2. **路径验证**：在发送前验证路径的有效性
3. **多项目支持**：更好地处理多个项目窗口
4. **路径映射**：支持远程开发时的路径映射