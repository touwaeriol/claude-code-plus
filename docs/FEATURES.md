# Claude Code Plus 功能说明

## 核心功能

### 1. 流式响应

插件支持实时流式响应，用户可以看到 AI 逐字生成的内容，而不需要等待完整响应。

### 2. 响应打断功能

插件实现了多种方式来打断正在进行的 AI 响应：

#### 2.1 停止按钮

- **位置**：输入框底部中央
- **行为**：点击后立即取消当前的响应生成
- **实现**：`ClaudeCodePlusToolWindowFactory.kt:514-522`

```kotlin
stopButton.addActionListener {
    // 取消当前的流式任务
    currentStreamJob?.cancel()
    logger.info("Stream cancelled by stop button")
    // 切换按钮状态
}
```

#### 2.2 ESC 键快捷方式

- **触发**：按下 ESC 键
- **行为**：如果正在生成响应，立即停止；否则关闭文件引用弹窗
- **实现**：`ClaudeCodePlusToolWindowFactory.kt:561-595`

```kotlin
e.keyCode == KeyEvent.VK_ESCAPE -> {
    if (currentStreamJob?.isActive == true) {
        currentStreamJob?.cancel()
        logger.info("Stream cancelled by user")
        // 更新UI状态
    }
}
```

#### 2.3 实现原理

打断功能的核心是使用 Kotlin 协程的取消机制：

1. **协程任务管理**：
   ```kotlin
   private var currentStreamJob: kotlinx.coroutines.Job? = null
   ```

2. **启动新任务前取消旧任务**：
   ```kotlin
   // 取消之前的任务（如果有）
   currentStreamJob?.cancel()
   ```

3. **进程级别的清理**：
   ```kotlin
   // ClaudeCliWrapper.kt:139
   finally {
       process.destroy()  // 确保进程被终止
   }
   ```

### 3. 文件引用功能

使用 `@` 符号可以引用项目中的文件：

- 输入 `@` 后会弹出文件搜索框
- 支持模糊搜索
- 支持键盘导航（上下箭头）
- 双击或回车选择文件

### 4. 多模型支持

- Claude 4 Opus（默认）
- Claude 4 Sonnet

### 5. 会话管理

- **新建会话**：点击标题栏的 "+ New Chat" 按钮
- **导出会话**：点击工具栏的"导出"按钮，保存为 Markdown 文件
- **清空会话**：点击工具栏的"清空"按钮

### 6. MCP (Model Context Protocol) 支持

支持配置 MCP 服务器来扩展 Claude 的能力：

- 全局配置
- 项目级别配置
- 配置合并机制

## 快捷键汇总

| 快捷键 | 功能 |
|--------|------|
| Enter | 发送消息 |
| Shift+Enter | 输入换行 |
| ESC | 停止生成/关闭弹窗 |
| @ | 触发文件引用 |
| ↑/↓ | 在文件列表中导航 |

## UI 状态管理

### 发送消息时的状态变化

1. **发送前**：
   - 发送按钮可见
   - 停止按钮隐藏

2. **发送中**：
   - 发送按钮隐藏
   - 停止按钮显示
   - 显示 "_Generating..._" 提示

3. **完成/取消后**：
   - 停止按钮隐藏
   - 发送按钮显示
   - 输入框获得焦点

### 错误处理

- 网络错误
- CLI 进程错误
- JSON 解析错误
- 取消操作（显示"已停止生成"）

## 权限模式

默认使用 `default` 模式，允许所有操作。可选模式：

- `default` - 允许所有操作
- `strict` - 限制敏感操作
- `none` - 禁止所有工具使用