# Swing UI 消息不展示问题调试清单

## 问题重现

1. 用户发送 "1+1="
2. 日志显示：`[MessageParser] 📝 TextBlock内容: 1+1=2`
3. 界面完全不展示任何消息

## 调试检查清单

### ✅ 第1步：确认消息已接收

**日志证据**:
```
[MessageParser] 📝 解析TextBlock，文本长度: 5
[MessageParser] 📝 TextBlock内容: 1+1=2
```

**结论**: ✅ AssistantMessage 已成功接收并解析

---

### ❓ 第2步：检查 ChatViewModel 是否创建了 Message

**相关代码** (`ChatViewModel.kt:91-121`):
```kotlin
is AssistantMessage -> {
    logger.info("📨 收到 AssistantMessage, content blocks: ${sdkMessage.content.size}")
    
    val textContent = sdkMessage.content
        .filterIsInstance<TextBlock>()
        .joinToString("") { it.text }
    
    logger.info("📝 提取的文本内容长度: ${textContent.length}, 内容: ${textContent.take(100)}")
    
    if (textContent.isNotEmpty()) {
        if (currentAssistantMessage == null) {
            currentAssistantMessage = Message(
                type = MessageType.ASSISTANT,
                content = textContent
            )
            addMessage(currentAssistantMessage!!)  // ← 应该触发回调
            logger.info("✅ 添加新助手消息到UI")
        }
    }
}
```

**需要确认的日志**:
- [ ] 是否打印了 `"📨 收到 AssistantMessage"`?
- [ ] 是否打印了 `"📝 提取的文本内容长度: 5"`?
- [ ] 是否打印了 `"✅ 添加新助手消息到UI"`?

**如果没有这些日志** → 说明 `is AssistantMessage` 分支没有执行
**如果有日志但 textContent.isEmpty()** → 说明 TextBlock 提取失败
**如果有日志但没有 "添加新助手消息"** → 说明 textContent.isEmpty() 为 true

---

### ❓ 第3步：检查回调是否触发

**相关代码** (`ChatViewModel.kt:277-280, 402-404`):
```kotlin
private fun addMessage(message: Message) {
    _messages.add(message)
    notifyMessageAdded(_messages.size - 1)  // ← 触发回调
}

private fun notifyMessageAdded(index: Int) {
    messageAddedCallbacks.forEach { it(index) }  // ← 遍历回调列表
}
```

**需要添加的调试日志**:
```kotlin
private fun notifyMessageAdded(index: Int) {
    logger.info("🔔 notifyMessageAdded called, index=$index, callbacks=${messageAddedCallbacks.size}")
    messageAddedCallbacks.forEach { it(index) }
}
```

**可能的问题**:
- [ ] `messageAddedCallbacks` 列表为空（回调未注册）
- [ ] 回调注册晚于消息添加

---

### ❓ 第4步：检查 UI 更新是否执行

**相关代码** (`ChatPanel.kt:90-94, 729-747`):
```kotlin
viewModel.onMessageAdded { index ->
    SwingUtilities.invokeLater {
        addMessageToUI(viewModel.messages[index], index)
    }
}

private fun addMessageToUI(message: Message, index: Int) {
    // 创建消息显示组件
    val messageComponent = createMessageComponent(message, index)
    messageComponents[index] = messageComponent
    messageListPanel.add(messageComponent)  // ← 添加到面板
    messageListPanel.revalidate()
    messageListPanel.repaint()
}
```

**需要添加的调试日志**:
```kotlin
private fun addMessageToUI(message: Message, index: Int) {
    println("🎨 addMessageToUI called: index=$index, type=${message.type}, content=${message.content.take(50)}")
    
    val messageComponent = createMessageComponent(message, index)
    println("🎨 Component created: ${messageComponent.javaClass.simpleName}, isVisible=${messageComponent.isVisible}")
    
    messageComponents[index] = messageComponent
    messageListPanel.add(messageComponent)
    
    println("🎨 Panel children count: ${messageListPanel.componentCount}")
    
    messageListPanel.revalidate()
    messageListPanel.repaint()
}
```

**可能的问题**:
- [ ] `viewModel.messages[index]` 越界
- [ ] `createMessageComponent` 返回 null 或不可见组件
- [ ] `messageListPanel` 没有正确布局

---

### ❓ 第5步：检查 MessageDisplay 组件

**相关代码** (`MessageDisplay.kt:35-53, 96-129`):
```kotlin
fun createComponent(): JComponent {
    val container = JPanel()
    container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
    container.alignmentX = 0f
    
    when (message.type) {
        MessageType.USER -> {
            container.add(createUserMessage())
        }
        MessageType.ASSISTANT -> {
            container.add(createAssistantMessage())  // ← 应该调用这里
        }
        MessageType.SYSTEM -> {
            container.add(createSystemMessage())
        }
    }
    
    return container
}

private fun createAssistantMessage(): JComponent {
    val panel = JPanel(BorderLayout())
    panel.border = EmptyBorder(JBUI.insets(8, 0))
    panel.background = markdownTheme.background
    
    val contentPanel = createMessageBubble(
        content = message.content,
        backgroundColor = ...,
        textColor = ...,
        alignment = SwingConstants.LEFT,
        renderMarkdown = true,  // ← 使用 Markdown 渲染
        isUserMessage = false
    )
    
    // ...
    panel.add(wrapper, BorderLayout.CENTER)
    return panel
}
```

**需要添加的调试日志**:
```kotlin
fun createComponent(): JComponent {
    println("🔨 MessageDisplay.createComponent: type=${message.type}, content=${message.content.take(50)}")
    
    val container = JPanel()
    container.layout = BoxLayout(container, BoxLayout.Y_AXIS)
    container.alignmentX = 0f
    
    when (message.type) {
        MessageType.ASSISTANT -> {
            println("🔨 Creating assistant message component")
            val component = createAssistantMessage()
            container.add(component)
            println("🔨 Component added to container, size=${component.size}")
        }
        // ...
    }
    
    println("🔨 Container created: children=${container.componentCount}")
    return container
}
```

**可能的问题**:
- [ ] `message.type` 不是 `MessageType.ASSISTANT`
- [ ] `createAssistantMessage()` 返回空组件
- [ ] 组件大小为 0

---

### ❓ 第6步：检查 Markdown 渲染

**相关代码** (`MessageDisplay.kt:189-198`):
```kotlin
val contentComponent = if (renderMarkdown && content.isNotBlank()) {
    // 使用 Markdown 渲染
    markdownRenderer.render(content, markdownTheme)  // ← 返回什么？
} else {
    // 简单文本渲染
    val label = JLabel("<html><div style='padding: 4px;'>${escapeHtml(content)}</div></html>")
    label.foreground = textColor
    label.font = markdownTheme.font
    label
}
```

**需要添加的调试日志** (`MarkdownRenderer.kt:57-80`):
```kotlin
fun render(markdown: String, theme: MarkdownTheme = MarkdownTheme.default()): JComponent {
    println("📄 MarkdownRenderer.render: length=${markdown.length}, content=${markdown.take(50)}")
    
    if (markdown.isBlank()) {
        println("⚠️  Markdown is blank, returning empty panel")
        return JPanel()
    }
    
    val document = parser.parse(markdown)
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    panel.background = theme.background
    panel.border = EmptyBorder(JBUI.insets(8))
    
    var child = document.firstChild
    var childCount = 0
    while (child != null) {
        val component = renderNode(child, theme)
        if (component != null) {
            panel.add(component)
            panel.add(Box.createVerticalStrut(4))
            childCount++
        }
        child = child.next
    }
    
    println("📄 Rendered $childCount nodes, panel size=${panel.preferredSize}")
    return panel
}
```

---

## 最可能的问题

根据代码分析，最可能的问题是：

### 问题 A: 回调注册时机（❗高可能性）

**问题代码** (`ChatPanel.kt:89-129`):
```kotlin
// 注册消息回调
viewModel.onMessageAdded { index -> ... }  // ← 1. 注册

// 初始化连接（异步）
CoroutineScope(Dispatchers.Main).launch {
    viewModel.connect()  // ← 2. 连接
    addWelcomeMessage()  // ← 3. 直接添加UI（绕过回调）
}
```

**问题**: `addWelcomeMessage()` 直接调用 `addMessageToUI()`，不通过 ViewModel，所以它能显示。

但是：
- 用户消息是通过 `viewModel.sendMessage()` 添加的
- AI 回复是通过 `receiveResponse()` 接收的

如果在 `sendMessage()` **之前**，回调还没有注册完成呢？

**解决方案**: 确保回调在 connect 之前注册（当前代码已经这样做了，应该没问题）

### 问题 B: 消息内容为空（❗高可能性）

**日志显示**:
```
[MessageParser] 📝 TextBlock内容: 1+1=2
```

这是在 SDK 的 `MessageParser` 中打印的，说明 TextBlock 确实解析成功了。

**但是**，在 `ChatViewModel.kt:95-100` 中：
```kotlin
val textContent = sdkMessage.content
    .filterIsInstance<TextBlock>()
    .joinToString("") { it.text }

logger.info("📝 提取的文本内容长度: ${textContent.length}, 内容: ${textContent.take(100)}")

if (textContent.isNotEmpty()) {  // ← 如果这里是 false？
    ...
}
```

**可能的问题**: `sdkMessage.content` 可能不包含 TextBlock！

**需要确认**: 日志是否打印了 `"📝 提取的文本内容长度"`？

### 问题 C: UI 线程问题（❗中可能性）

**问题**: `SwingUtilities.invokeLater` 可能没有正确执行。

**相关代码**:
```kotlin
viewModel.onMessageAdded { index ->
    SwingUtilities.invokeLater {  // ← EDT 线程
        addMessageToUI(viewModel.messages[index], index)
    }
}
```

但是这个模式是标准的 Swing 最佳实践，应该没问题。

---

## 建议的调试步骤

### 立即行动：添加关键日志

在 `ChatViewModel.kt` 中添加详细日志：

```kotlin
// 在 receiveResponse().collect 前
logger.info("🚀 开始接收响应...")

// 在 is AssistantMessage 分支
is AssistantMessage -> {
    logger.info("📨 收到 AssistantMessage, content blocks: ${sdkMessage.content.size}")
    logger.info("📨 Content block types: ${sdkMessage.content.map { it::class.simpleName }}")
    
    val textBlocks = sdkMessage.content.filterIsInstance<TextBlock>()
    logger.info("📨 TextBlock 数量: ${textBlocks.size}")
    
    val textContent = textBlocks.joinToString("") { it.text }
    logger.info("📝 提取的文本内容: '$textContent' (长度=${textContent.length})")
    
    if (textContent.isEmpty()) {
        logger.warning("⚠️  文本内容为空！跳过添加消息")
    } else {
        logger.info("✅ 准备添加消息")
        // ...
    }
}

// 在 addMessage 方法中
private fun addMessage(message: Message) {
    logger.info("➕ addMessage called: type=${message.type}, content length=${message.content.length}")
    _messages.add(message)
    logger.info("➕ Messages list size: ${_messages.size}")
    notifyMessageAdded(_messages.size - 1)
}

// 在 notifyMessageAdded 方法中
private fun notifyMessageAdded(index: Int) {
    logger.info("🔔 notifyMessageAdded: index=$index, callbacks=${messageAddedCallbacks.size}")
    messageAddedCallbacks.forEach { 
        logger.info("🔔 Calling callback for index=$index")
        it(index) 
    }
}
```

### 在 `ChatPanel.kt` 中添加日志：

```kotlin
viewModel.onMessageAdded { index ->
    println("🔔 ChatPanel received onMessageAdded callback: index=$index")
    SwingUtilities.invokeLater {
        println("🎨 EDT: calling addMessageToUI, index=$index")
        println("🎨 EDT: message = ${viewModel.messages.getOrNull(index)}")
        if (index < viewModel.messages.size) {
            addMessageToUI(viewModel.messages[index], index)
        } else {
            println("❌ EDT: Index out of bounds! index=$index, size=${viewModel.messages.size}")
        }
    }
}

private fun addMessageToUI(message: Message, index: Int) {
    println("🎨 addMessageToUI: index=$index, type=${message.type}, content='${message.content.take(50)}'")
    
    val messageComponent = createMessageComponent(message, index)
    println("🎨 Component created: class=${messageComponent.javaClass.simpleName}")
    println("🎨 Component visible=${messageComponent.isVisible}, size=${messageComponent.size}")
    
    messageComponents[index] = messageComponent
    messageListPanel.add(messageComponent)
    messageListPanel.add(Box.createVerticalStrut(8))
    
    println("🎨 MessageListPanel componentCount=${messageListPanel.componentCount}")
    println("🎨 MessageListPanel size=${messageListPanel.size}")
    
    messageListPanel.revalidate()
    messageListPanel.repaint()
    
    SwingUtilities.invokeLater {
        val vertical = scrollPane.verticalScrollBar
        println("🎨 Scroll value=${vertical.value}, max=${vertical.maximum}")
        vertical.value = vertical.maximum
    }
}
```

---

## 可能的根本原因（按概率排序）

### 1. 文本内容为空（70% 可能性）⭐⭐⭐

**假设**: `textContent.isEmpty()` 为 true，导致消息没有被添加。

**原因**: 
- `sdkMessage.content` 不包含 TextBlock
- 或者 TextBlock.text 为空字符串

**验证方法**: 查看日志中是否有 `"📝 提取的文本内容长度"`

### 2. AssistantMessage 没有被接收（20% 可能性）⭐

**假设**: `when` 分支没有匹配到 `is AssistantMessage`。

**原因**:
- SDK 的消息类型可能有变化
- 或者只收到 StreamEvent，没收到 AssistantMessage

**验证方法**: 查看日志中是否有 `"📨 收到 AssistantMessage"`

### 3. UI 组件不可见或大小为0（5% 可能性）

**假设**: 组件被添加了，但不可见。

**原因**:
- `preferredSize` 设置错误
- 背景色与父容器相同
- 组件被遮挡

**验证方法**: 添加日志检查组件大小和可见性

### 4. 回调未注册（3% 可能性）

**假设**: 回调列表为空。

**原因**: 时序问题

**验证方法**: 打印 `messageAddedCallbacks.size`

### 5. 线程问题（2% 可能性）

**假设**: EDT 线程死锁或异常。

**原因**: Swing 线程问题

**验证方法**: 检查 EDT 是否正常运行

---

## 立即执行的修复步骤

### 步骤 1: 添加完整的调试日志

在以下文件中添加详细日志：
1. `ChatViewModel.kt` - 消息接收和处理
2. `ChatPanel.kt` - UI 更新
3. `MessageDisplay.kt` - 组件创建

### 步骤 2: 重新编译并运行

```bash
./gradlew jetbrains-plugin:build -x test
./gradlew jetbrains-plugin:runIde
```

### 步骤 3: 发送测试消息并查看完整日志

发送 "1+1=" 并收集以下日志：
- [ ] "📨 收到 AssistantMessage"
- [ ] "📝 提取的文本内容"
- [ ] "✅ 添加新助手消息"
- [ ] "🔔 notifyMessageAdded"
- [ ] "🔔 ChatPanel received callback"
- [ ] "🎨 addMessageToUI"
- [ ] "🎨 Component created"

### 步骤 4: 根据日志定位问题

根据缺失的日志消息，快速定位到底是哪一步出了问题。

---

## 快速修复方案（Workaround）

如果调试过程太复杂，可以考虑以下临时方案：

### 方案 A: 简化消息处理

移除 `if (textContent.isNotEmpty())` 检查，强制添加消息：

```kotlin
is AssistantMessage -> {
    val textContent = sdkMessage.content
        .filterIsInstance<TextBlock>()
        .joinToString("") { it.text }
    
    // 即使为空也添加（用于调试）
    if (currentAssistantMessage == null) {
        currentAssistantMessage = Message(
            type = MessageType.ASSISTANT,
            content = textContent.ifEmpty { "[空消息]" }  // ← 添加占位符
        )
        addMessage(currentAssistantMessage!!)
    }
}
```

### 方案 B: 同时处理 StreamEvent

虽然复杂，但这是 Vue 前端的做法：

```kotlin
is StreamEvent -> {
    // 提取文本增量
    val event = sdkMessage.event as? JsonObject
    val type = event?.get("type")?.jsonPrimitive?.content
    
    if (type == "content_block_delta") {
        val delta = event["delta"]?.jsonObject
        val text = delta?.get("text")?.jsonPrimitive?.content
        
        if (!text.isNullOrEmpty()) {
            // 实时更新消息
            if (currentAssistantMessage == null) {
                currentAssistantMessage = Message(
                    type = MessageType.ASSISTANT,
                    content = text
                )
                addMessage(currentAssistantMessage!!)
            } else {
                // 累积文本
                val index = _messages.indexOf(currentAssistantMessage)
                if (index >= 0) {
                    val newContent = _messages[index].content + text
                    val updatedMessage = _messages[index].copy(content = newContent)
                    _messages[index] = updatedMessage
                    currentAssistantMessage = updatedMessage
                    notifyMessageUpdated(index)
                }
            }
        }
    }
}
```

---

## 总结

**立即行动**: 添加完整的调试日志，重新运行，根据日志定位具体问题。

**长期方案**: 参考 Vue 前端实现完整的 StreamEvent 处理逻辑。


