package com.claudecodeplus.ui.jewel.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import com.claudecodeplus.ui.models.*

/**
 * 用户消息显示组件
 * 使用统一的 UnifiedInputArea 组件的显示模式
 */
@Composable
fun UserMessageDisplay(
    message: EnhancedMessage,
    contexts: List<ContextReference> = emptyList(),
    modifier: Modifier = Modifier
) {
    // 解析消息内容
    val (_, userText) = parseMessageContent(message.content)
    
    UnifiedInputArea(
        mode = InputAreaMode.DISPLAY,
        value = TextFieldValue(userText),
        contexts = contexts,
        selectedModel = message.model ?: AiModel.OPUS,
        message = message,
        modifier = modifier
    )
}

/**
 * 解析消息内容，分离上下文和用户文本
 */
private fun parseMessageContent(content: String): Pair<List<String>, String> {
    val contextRegex = "^> \\*\\*上下文资料\\*\\*\\n(?:> \\n)?((?:> - .+\\n)+)\\n".toRegex()
    val match = contextRegex.find(content)
    
    return if (match != null) {
        val contextSection = match.groupValues[1]
        val contexts = contextSection
            .split('\n')
            .filter { it.startsWith("> - ") }
            .map { it.substring(4) } // 移除 "> - " 前缀
        
        val userMessage = content.substring(match.range.last + 1)
        Pair(contexts, userMessage)
    } else {
        Pair(emptyList(), content)
    }
}



/**
 * 从消息内容中提取上下文引用
 */
fun extractContextReferences(content: String): List<ContextReference> {
    val (contexts, _) = parseMessageContent(content)
    return contexts.map { contextText ->
        // 简单地解析上下文文本并创建对应的ContextReference
        when {
            contextText.contains("📄") -> {
                val path = contextText.substringAfter('`').substringBefore('`')
                ContextReference.FileReference(path = path, fullPath = path)
            }
            contextText.contains("🌐") -> {
                val url = contextText.substringAfter("🌐 ").substringBefore(" (")
                val title = if (contextText.contains(" (") && contextText.contains(")")) {
                    contextText.substringAfter(" (").substringBefore(")")
                } else null
                ContextReference.WebReference(url = url, title = title)
            }
            contextText.contains("📁") -> {
                val path = contextText.substringAfter('`').substringBefore('`')
                val fileCountText = contextText.substringAfter("(").substringBefore("个文件)")
                val fileCount = fileCountText.toIntOrNull() ?: 0
                ContextReference.FolderReference(path = path, fileCount = fileCount)
            }
            contextText.contains("🖼") -> {
                val filename = contextText.substringAfter('`').substringBefore('`')
                ContextReference.ImageReference(path = filename, filename = filename)
            }
            else -> {
                // 默认作为文件引用处理
                ContextReference.FileReference(path = contextText, fullPath = contextText)
            }
        }
    }
} 