# 内容根优先的项目路径策略

## 问题背景

在 IntelliJ IDEA 中，当打开单个文件或非标准项目时，`project.basePath` 可能不可用或不正确。例如：
- 打开单个文件：内容根是 `/Users/erio/test.json`
- `project.basePath` 可能为 null 或指向错误位置

## 解决方案

### 新的路径获取优先级

1. **优先使用 ContentRoots**（内容根）
   ```kotlin
   val contentRoots = ProjectRootManager.getInstance(project).contentRoots
   ```
   - 始终反映实际打开的文件/目录
   - 对单文件项目更准确
   - 如果内容根是文件，自动使用其父目录

2. **备选 project.basePath**
   - 用于标准项目结构
   - 当内容根不可用时使用

3. **模块内容根**
   - 多模块项目的备选方案

4. **从项目文件推断**
   - 分析 .idea 目录位置

5. **用户主目录**
   - 最后的备选方案

### 实现细节

```kotlin
val workingDir = when {
    rootFile.isDirectory -> {
        // 如果内容根是文件夹，直接使用
        rootPath
    }
    rootFile.isFile -> {
        // 如果内容根是文件，使用其父目录
        rootFile.parentFile?.absolutePath ?: rootPath
    }
    else -> {
        // 其他情况，直接使用路径
        rootPath
    }
}
```

### 调试信息增强

现在会显示：
```
内容根目录 (ContentRoots):
  [0] [文件] /Users/erio/test.json
       父目录: /Users/erio
  [1] [目录] /Users/erio/project
```

## 使用场景

### 场景 1：打开单个文件
- 内容根：`/Users/erio/test.json`
- 工作目录：`/Users/erio`（父目录）

### 场景 2：打开项目目录
- 内容根：`/Users/erio/myproject`
- 工作目录：`/Users/erio/myproject`

### 场景 3：多内容根项目
- 使用第一个内容根
- 未来可扩展为让用户选择

## 验证方法

1. 运行插件
2. 查看 Claude Code 工具窗口的调试信息
3. 确认"推荐使用的工作目录"是否正确
4. 检查 IDEA 日志中的路径信息

## 日志位置

- **macOS**: `~/Library/Logs/JetBrains/IntelliJIdea2024.3/idea.log`
- **Windows**: `%USERPROFILE%\AppData\Local\JetBrains\IntelliJIdea2024.3\log\idea.log`
- **Linux**: `~/.cache/JetBrains/IntelliJIdea2024.3/log/idea.log`

搜索关键字：
- "Claude Code Plus"
- "ProjectPathDebugger"
- "推荐的工作目录"