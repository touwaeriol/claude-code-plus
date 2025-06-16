# 输入框超链接实现方案

## 方案一：使用 JTextPane（富文本）

```kotlin
class RichTextInputField(project: Project) : JTextPane() {
    init {
        // 设置为 HTML 编辑器
        contentType = "text/html"
        editorKit = HTMLEditorKit()
        
        // 监听文本变化，将 @文件名 转换为超链接
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                processFileReferences()
            }
        })
    }
    
    private fun processFileReferences() {
        // 将 @文件名 转换为 <a href="file://...">@文件名</a>
    }
}
```

**优点**：
- 可以显示真正的超链接
- 支持富文本格式

**缺点**：
- 实现复杂，需要处理 HTML
- 可能影响输入体验
- 与 IntelliJ 的编辑器风格不一致

## 方案二：使用 EditorTextField with Markup

```kotlin
class MarkupInputField(project: Project) : EditorTextField() {
    init {
        // 使用自定义的高亮显示
        addSettingsProvider { editor ->
            editor.markupModel.addRangeHighlighter(
                startOffset,
                endOffset,
                HighlighterLayer.SYNTAX,
                TextAttributes().apply {
                    foregroundColor = JBColor.BLUE
                    effectType = EffectType.LINE_UNDERSCORE
                    effectColor = JBColor.BLUE
                },
                HighlighterTargetArea.EXACT_RANGE
            )
        }
    }
}
```

**优点**：
- 保持 IntelliJ 编辑器的体验
- 可以自定义文件引用的显示样式

**缺点**：
- 不是真正的超链接，只是视觉效果
- 需要手动处理点击事件

## 方案三：两步式交互（推荐）

保持当前设计，但增强视觉反馈：

1. **输入时**：
   - `@文件名` 显示为特殊颜色（蓝色）
   - 鼠标悬停显示完整路径提示

2. **发送后**：
   - 在聊天记录中显示为真正的超链接
   - 可以点击打开文件

```kotlin
// 在 FileReferenceEditorField 中添加
private fun highlightFileReferences() {
    val text = document.text
    val pattern = "@([^\\s]+(?:\\.[^\\s]+)?)"
    val regex = Regex(pattern)
    
    regex.findAll(text).forEach { match ->
        // 添加高亮
        editor.markupModel.addRangeHighlighter(
            match.range.first,
            match.range.last + 1,
            HighlighterLayer.SYNTAX,
            fileReferenceAttributes,
            HighlighterTargetArea.EXACT_RANGE
        )
    }
}
```

## 建议

推荐使用**方案三**，因为：

1. **用户体验一致**：保持 IntelliJ 的编辑器体验
2. **实现简单**：不需要处理复杂的富文本
3. **清晰的交互**：用户知道发送后会变成可点击的链接
4. **性能好**：避免实时转换 HTML 的开销

这也是大多数聊天应用的做法（如 Slack、Discord 等）：
- 输入时显示纯文本或轻量级高亮
- 发送后转换为富文本/超链接