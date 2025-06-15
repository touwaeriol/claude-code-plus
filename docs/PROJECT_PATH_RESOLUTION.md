# 项目路径解析改进

本文档描述了插件如何可靠地获取 IntelliJ IDEA 项目的工作目录。

## 路径获取策略

`ProjectPathDebugger.getProjectWorkingDirectory()` 使用以下优先级顺序获取项目路径：

### 1. project.basePath（首选）
```kotlin
val basePath = project.basePath
```
- 最常用和可靠的方法
- 返回项目的根目录
- 适用于大多数标准项目

### 2. ProjectRootManager.contentRoots
```kotlin
val contentRoots = ProjectRootManager.getInstance(project).contentRoots
```
- 当 basePath 不可用时的备选方案
- 获取项目的内容根目录
- 对于复杂项目结构更可靠

### 3. 第一个模块的内容根
```kotlin
val modules = ModuleManager.getInstance(project).modules
val contentRoots = ModuleRootManager.getInstance(modules[0]).contentRoots
```
- 适用于多模块项目
- 获取第一个模块的根目录
- 处理模块化项目结构

### 4. 从项目文件路径推断
```kotlin
val projectFilePath = project.projectFilePath
```
- 分析 .ipr 或 .idea/misc.xml 的位置
- 向上查找到项目根目录
- 处理非标准项目配置

### 5. 用户主目录（最后手段）
```kotlin
System.getProperty("user.home")
```
- 当所有其他方法失败时使用
- 确保始终有一个有效路径

## 项目类型检测

插件能够识别以下项目类型：

- **Maven**: 检测 `pom.xml`
- **Gradle**: 检测 `build.gradle` 或 `build.gradle.kts`
- **Node.js/npm**: 检测 `package.json`
- **Python**: 检测 `setup.py`、`requirements.txt` 或 `pyproject.toml`
- **Generic**: 其他项目类型

## 调试功能

### 详细的路径信息
运行插件时，会在 Claude Code 工具窗口显示：

```
=== 项目路径调试信息 ===
项目名称: MyProject
project.basePath: /Users/username/projects/myproject
project.projectFilePath: /Users/username/projects/myproject/.idea/misc.xml

内容根目录 (ContentRoots):
  [0] /Users/username/projects/myproject

模块信息 (Modules):
  [0] 模块名: myproject
       内容根: /Users/username/projects/myproject

项目类型: Maven
推荐使用的工作目录: /Users/username/projects/myproject
```

### 日志记录
所有路径解析过程都会记录到 IDEA 日志中：
- 使用哪种方法获取路径
- 遇到的任何错误
- 最终选择的路径

## 处理特殊情况

### 多模块项目
- 默认使用项目根目录
- 可以扩展为让用户选择特定模块

### 远程项目
- 当前实现假定本地文件系统
- 未来可能需要处理远程路径映射

### 默认项目
- basePath 可能为 null
- 自动降级到其他方法

## 与 Claude SDK 集成

获取的路径通过以下方式传递给 Claude：

1. **初始化时**：
```kotlin
service.initializeWithConfig(
    cwd = projectPath,
    systemPrompt = "Working directory: $projectPath"
)
```

2. **每条消息**：
```kotlin
val options = mapOf("cwd" to projectPath)
service.sendMessageStream(message, false, options)
```

## 故障排查

如果 Claude 仍然使用错误的目录：

1. 检查 Claude Code 工具窗口中的调试信息
2. 确认"推荐使用的工作目录"是否正确
3. 查看 IDEA 日志（Help > Show Log in Finder/Explorer）
4. 检查 Python 服务器日志中的 `[DEBUG] Working directory (cwd):` 行

## 未来改进

1. 支持用户手动指定工作目录
2. 记住每个项目的工作目录偏好
3. 支持工作区和多项目窗口
4. 处理符号链接和路径别名