package com.claudecodeplus.ui.jewel.components

import com.claudecodeplus.core.logging.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.claudecodeplus.ui.models.EnhancedMessage

/**
 * 用户消息显示组件
 * 直接使用 UnifiedInputArea 的 DISPLAY 模式，确保与输入框完全一致
 */
@Composable
fun UserMessageDisplay(
    message: EnhancedMessage,
    modifier: Modifier = Modifier
) {
    // 直接使用 UnifiedInputArea 的 DISPLAY 模式来显示用户消息
    // 这样可以确保显示效果与输入框完全一致，包括主题适配
    UnifiedInputArea(
        modifier = modifier,
        mode = InputAreaMode.DISPLAY,
        message = message,
        contexts = message.contexts,
        onContextClick = { uri ->
            // 消息列表中的引用点击处理（只读，可以显示文件信息等）
    logD("点击了上下文引用: $uri")
        }
    )
}

/**
 * 检查文本是否包含内联引用
 */
private fun containsInlineReferences(text: String): Boolean {
    // 检查 Markdown 格式的内联引用 [@filename](file://path)
    val markdownPattern = Regex("""(\[@([^\]]+)\]\(file://([^)]+)\))""")
    if (markdownPattern.find(text) != null) {
        return true
    }
    
    // 检查旧格式的内联引用 @scheme://path
    val legacyRefs = InlineReferenceDetector.extractReferences(text)
    return legacyRefs.isNotEmpty()
}
