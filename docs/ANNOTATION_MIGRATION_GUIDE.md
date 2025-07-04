# 注解系统迁移指南

本文档介绍如何从当前的 `BasicTextField + VisualTransformation` 方案迁移到新的 `Jewel + AnnotatedString` 方案。

## 概述

### 旧方案
- 使用 `BasicTextField` 与 `VisualTransformation`
- 复杂的 offset mapping 逻辑
- 拼音输入法兼容性问题

### 新方案
- 使用 Jewel TextField 输入 Markdown 格式
- 使用 Jewel Text 显示 AnnotatedString
- 简单直观的点击处理

## 迁移步骤

### 1. 替换输入组件

**旧代码：**
```kotlin
BasicTextField(
    value = value,
    onValueChange = onValueChange,
    visualTransformation = InlineReferenceVisualTransformation(),
    // ... 复杂的键盘处理逻辑
)
```

**新代码：**
```kotlin
SimpleChatInputField(
    value = value,
    onValueChange = onValueChange,
    onSend = onSend,
    onShowContextSelector = { position ->
        // 显示上下文选择器
    }
)
```

### 2. 替换显示组件

**旧代码：**
```kotlin
ClickableText(
    text = transformedText.text,
    onClick = { offset ->
        // 复杂的点击处理
    }
)
```

**新代码：**
```kotlin
AnnotatedMessageDisplay(
    message = markdownText,
    onContextClick = { uri ->
        // 简单的 URI 处理
    }
)
```

### 3. 更新上下文引用格式

**旧格式：**
```
@file://path/to/file.txt
```

**新格式（Markdown）：**
```
[@file.txt](claude-context://file/path/to/file.txt)
```

### 4. 更新消息存储

**旧方式：**
- 存储带有内联引用的纯文本
- 依赖 VisualTransformation 转换显示

**新方式：**
- 存储 Markdown 格式文本
- 解析时转换为 AnnotatedString

## URI 格式规范

### 基础格式
```
claude-context://<type>/<encoded-path>[?params][#fragment]
```

### 支持的类型
| 类型 | URI 格式 | 示例 |
|------|---------|------|
| 文件 | `claude-context://file/<path>` | `claude-context://file/src/Main.kt#L10` |
| 网页 | `claude-context://web/<url>` | `claude-context://web/https%3A%2F%2Fgithub.com?title=GitHub` |
| 文件夹 | `claude-context://folder/<path>` | `claude-context://folder/src/main` |
| 符号 | `claude-context://symbol/<name>` | `claude-context://symbol/com.example.MyClass?file=Main.kt` |
| 图片 | `claude-context://image/<path>` | `claude-context://image/screenshot.png?mime=image/png` |
| 终端 | `claude-context://terminal` | `claude-context://terminal` |
| 问题 | `claude-context://problems` | `claude-context://problems` |
| Git | `claude-context://git` | `claude-context://git` |
| 选择 | `claude-context://selection` | `claude-context://selection` |
| 工作区 | `claude-context://workspace` | `claude-context://workspace` |

## 代码示例

### 完整的迁移示例

```kotlin
// 1. 创建统一输入区域
@Composable
fun MyChat() {
    var messages by remember { mutableStateOf(listOf<Message>()) }
    
    Column {
        // 消息列表
        messages.forEach { message ->
            SimpleUnifiedInputArea(
                mode = InputAreaMode.DISPLAY,
                message = message,
                onContextClick = { uri ->
                    handleContextClick(uri)
                }
            )
        }
        
        // 输入区域
        SimpleUnifiedInputArea(
            mode = InputAreaMode.INPUT,
            onSend = { markdownText ->
                // 发送包含 Markdown 格式的消息
                messages = messages + Message(markdownText)
            }
        )
    }
}

// 2. 处理上下文点击
fun handleContextClick(uri: String) {
    val contextUri = parseContextUri(uri) ?: return
    
    when (contextUri) {
        is ContextUri.FileUri -> {
            // 打开文件
            openFile(contextUri.path, contextUri.line)
        }
        is ContextUri.WebUri -> {
            // 打开网页
            openUrl(contextUri.url)
        }
        // ... 其他类型
    }
}

// 3. 生成上下文引用
fun ContextReference.toMarkdownLink(): String {
    val uri = toContextUri()
    return "[@${uri.toDisplayName()}](${uri.toUriString()})"
}
```

## 性能优化

### 使用缓存解析器
```kotlin
// 在 Composable 中使用
val annotatedText = rememberParsedMarkdown(
    markdown = message,
    linkColor = customColor
)

// 在非 Composable 中使用
val annotatedText = SyncCachedMarkdownParser.parse(
    markdown = message
)
```

### 缓存管理
```kotlin
// 清空缓存（在适当时机）
SyncCachedMarkdownParser.clearCache()
```

## 兼容性处理

### 历史消息迁移
```kotlin
fun migrateOldMessage(content: String): String {
    // 将旧格式转换为新格式
    return content.replace(Regex("@file://([^\\s]+)")) { match ->
        val path = match.groupValues[1]
        val fileName = path.substringAfterLast('/')
        "[@$fileName](claude-context://file/$path)"
    }
}
```

### 向后兼容
新的解析器同时支持：
- 新格式：`[@name](claude-context://...)`
- 旧格式：`[@name](file://...)`（自动转换）
- 标准 URL：`[@name](https://...)`

## 注意事项

1. **文本存储**：确保数据库/存储层保存的是 Markdown 格式
2. **输入验证**：在发送前验证 Markdown 格式的正确性
3. **错误处理**：对无效的 URI 进行降级处理
4. **性能考虑**：对于长消息，考虑分段解析
5. **UI 一致性**：确保所有使用场景都迁移到新方案

## 测试检查清单

- [ ] 输入普通文本
- [ ] 输入包含上下文引用的文本
- [ ] 点击不同类型的上下文引用
- [ ] 拼音输入法正常工作
- [ ] 历史消息正确显示
- [ ] 性能无明显退化
- [ ] 样式和图标正确显示

## 常见问题

### Q: 为什么要迁移？
A: 新方案解决了拼音输入法问题，简化了代码，提升了性能。

### Q: 迁移后数据会丢失吗？
A: 不会。通过迁移函数可以将旧格式转换为新格式。

### Q: 可以逐步迁移吗？
A: 可以。新旧组件可以共存，建议先在新功能中使用新方案。

### Q: 性能会受影响吗？
A: 不会。新方案使用了缓存机制，性能更好。