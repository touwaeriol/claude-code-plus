# Claude Code Plus 故障排除

## 索引存储错误 (AssertionError in bytecodeAnalysis.storage)

### 问题描述
```
java.lang.AssertionError: -1161946761 in /Users/erio/codes/idea/claude-code-plus/build/idea-sandbox/IC-2025.1.2/system/index/bytecodeanalysis/bytecodeAnalysis.storage.keystream
```

### 解决方法

这是 IntelliJ IDEA 的索引文件损坏问题，与插件代码无关。请按以下步骤解决：

1. **清理索引缓存**
   - 在 IntelliJ IDEA 中，选择菜单：File → Invalidate Caches...
   - 勾选所有选项
   - 点击 "Invalidate and Restart"

2. **手动删除索引文件**（如果上述方法无效）
   - 关闭 IntelliJ IDEA
   - 删除以下目录：
     ```
     /Users/erio/codes/idea/claude-code-plus/build/idea-sandbox/IC-2025.1.2/system/index/
     ```
   - 重新启动 IntelliJ IDEA

3. **重建项目**
   - Build → Clean Project
   - Build → Rebuild Project

## "+ New Chat" 显示为图标而非文字

### 问题描述
标题栏的 "New Chat" 按钮显示为图标而非文字。

### 确认检查
代码中已经正确设置为文字显示：
```kotlin
AnAction("+ New Chat", "开始新会话", null)  // 第三个参数为 null 表示无图标
```

如果仍然显示为图标，可能是 IntelliJ Platform 的渲染问题。尝试：
1. 重启 IDE
2. 清理并重建项目
3. 检查是否有其他插件冲突