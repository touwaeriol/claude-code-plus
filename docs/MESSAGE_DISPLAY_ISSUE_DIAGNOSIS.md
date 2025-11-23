# 消息不展示问题诊断报告

## 问题现象

用户发送 "1+1=" 后：
- ✅ 消息成功发送到 Claude
- ✅ 收到响应（日志显示：`TextBlock内容: 1+1=2`）
- ❌ **界面上完全不展示消息**

## 代码流程追踪

### Vue Web 前端的正确流程

```
1. 用户输入 → ChatInput.vue
2. emit('send') → ModernChatView.vue
3. sessionStore.sendMessage()
4. claudeService.query() 发送到后端
5. 收到 StreamEvent → handleStreamEvent()
   ├─ processMessageStart() → 创建空的 assistant 消息
   ├─ processContentBlockDelta() → 实时更新文本
   └─ processMessageStop() → 完成
6. convertToDisplayItems() → 转换为 DisplayItem
7. MessageList.vue → 虚拟滚动列表
8. DisplayItemRenderer.vue → 分发到具体组件
   ├─ UserMessageDisplay.vue
   ├─ AssistantTextDisplay.vue  ← 在这里渲染
   └─ ToolCallDisplay.vue
```

### Swing 插件的当前流程

```
1. 用户输入 → inputArea (JBTextArea)
2. sendButton.click → viewModel.sendMessage()
3. claudeClient.query() 发送
4. receiveResponse().collect { sdkMessage ->
     when (sdkMessage) {
       is AssistantMessage -> {
         // ✅ 提取文本内容
         val textContent = sdkMessage.content
           .filterIsInstance<TextBlock>()
           .joinToString("") { it.text }
         
         // ✅ 创建/更新 Message
         if (currentAssistantMessage == null) {
           currentAssistantMessage = Message(
             type = MessageType.ASSISTANT,
             content = textContent
           )
           addMessage(currentAssistantMessage!!)  // ✅ 触发回调
         }
       }
       is StreamEvent -> {
         logger.info("...")  // ❌ 只打印，不处理
       }
     }
   }
5. notifyMessageAdded(index) → 触发回调
6. ChatPanel.onMessageAdded { index ->
     SwingUtilities.invokeLater {
       addMessageToUI(viewModel.messages[index], index)  // ✅ 应该被调用
     }
   }
7. addMessageToUI() {
     val messageComponent = createMessageComponent(message, index)
     messageListPanel.add(messageComponent)  // ✅ 添加到面板
     messageListPanel.revalidate()  // ✅ 刷新
     messageListPanel.repaint()     // ✅ 重绘
   }
8. MessageDisplay.createComponent() {
     when (message.type) {
       MessageType.ASSISTANT -> createAssistantMessage()  // ✅ 应该创建
     }
   }
9. createAssistantMessage() {
     createMessageBubble(renderMarkdown = true)  // ✅ 使用 Markdown
   }
10. markdownRenderer.render(content, theme)  // ✅ 渲染 Markdown
```

## 可能的问题点

### 问题 1: 回调时机问题 ❓

**怀疑**: `addMessage()` 调用后，回调可能没有正确触发。

**检查点**:
```kotlin
// ChatViewModel.kt:277-280
private fun addMessage(message: Message) {
    _messages.add(message)
    notifyMessageAdded(_messages.size - 1)  // ← 这里调用
}

// ChatViewModel.kt:402-404
private fun notifyMessageAdded(index: Int) {
    messageAddedCallbacks.forEach { it(index) }  // ← 遍历回调
}
```

**检查**: 回调列表是否为空？

### 问题 2: SwingUtilities.invokeLater 延迟问题 ❓

**怀疑**: UI 更新可能在错误的线程上执行。

**检查点**:
```kotlin
// ChatPanel.kt:90-94
viewModel.onMessageAdded { index ->
    SwingUtilities.invokeLater {  // ← 切换到 EDT
        addMessageToUI(viewModel.messages[index], index)
    }
}
```

**可能的问题**: 
- 如果 `viewModel.messages[index]` 在回调时已经被修改？
- 如果 index 越界？

### 问题 3: UI 组件可见性问题 ❓

**怀疑**: 组件被添加了，但不可见。

**检查点**:
```kotlin
// MessageDisplay.kt:183-186
bubble.preferredSize = Dimension(
    minOf(maxWidth, estimateTextWidth(content) + 40),
    Int.MAX_VALUE  // ← 高度无限大？
)
```

**可能的问题**: `Int.MAX_VALUE` 可能导致布局问题

### 问题 4: 消息列表布局问题 ❓

**怀疑**: BoxLayout 可能没有正确计算大小。

**检查点**:
```kotlin
// ChatPanel.kt:70-72
messageListPanel = JPanel()
messageListPanel.layout = BoxLayout(messageListPanel, BoxLayout.Y_AXIS)
```

### 问题 5: 回调注册时机问题 ⚠️

**重要发现**: 回调注册在 `createUI()` 中，但 `connect()` 和 `addWelcomeMessage()` 也在同一个方法中。

**潜在的竞态条件**:
```kotlin
// ChatPanel.kt:89-129
// 注册消息回调
viewModel.onMessageAdded { index -> ... }  // ← 1. 注册回调

// 初始化连接
CoroutineScope(Dispatchers.Main).launch {
    try {
        viewModel.connect()       // ← 2. 连接（异步）
        addWelcomeMessage()       // ← 3. 添加欢迎消息
    }
}
```

`addWelcomeMessage()` 直接调用 `addMessageToUI()`，绕过了 ViewModel，所以欢迎消息应该能显示。

但用户消息和 AI 回复需要通过 ViewModel 的回调机制。

---

## 关键问题：StreamEvent vs AssistantMessage

### 当前配置下的消息流

由于现在设置了 `includePartialMessages = true`，Claude CLI 会发送：

1. **StreamEvent** (多个)
   - `message_start`
   - `content_block_start` 
   - `content_block_delta` (文本逐字增量)
   - `content_block_stop`
   - `message_stop`

2. **AssistantMessage** (最后一个，完整消息)

### Swing 插件的处理方式

```kotlin
client.receiveResponse().collect { sdkMessage ->
  when (sdkMessage) {
    is StreamEvent -> {
      logger.info("📨 Stream event: ...")  // ❌ 只记录，不处理
    }
    is AssistantMessage -> {
      // ✅ 只有在这里才会创建消息
      if (textContent.isNotEmpty()) {
        addMessage(...)  // 触发回调
      }
    }
  }
}
```

### 关键问题

**如果 Claude 只发送 StreamEvent，不发送 AssistantMessage 呢？**

根据 Claude CLI 的行为：
- 使用 `--include-partial-messages` 时，会发送 StreamEvent
- **但是是否还会发送最终的 AssistantMessage？**

让我检查 SDK 的测试代码：

<function_calls>
<invoke name="read_file">
<parameter name="target_file">claude-code-sdk/src/test/kotlin/com/claudecodeplus/sdk/IncludePartialMessagesTest.kt

