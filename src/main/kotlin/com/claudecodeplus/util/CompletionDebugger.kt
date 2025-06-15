package com.claudecodeplus.util

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.diagnostic.logger

/**
 * 自动完成调试工具
 */
object CompletionDebugger {
    private val LOG = logger<CompletionDebugger>()
    
    fun debugCompletionContext(
        parameters: CompletionParameters,
        result: CompletionResultSet,
        source: String
    ) {
        val position = parameters.position
        val document = parameters.editor.document
        val text = document.text
        val offset = parameters.offset
        
        // 记录上下文信息
        val contextStart = maxOf(0, offset - 20)
        val contextEnd = minOf(text.length, offset + 20)
        val contextText = text.substring(contextStart, contextEnd)
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        
        val debugInfo = buildString {
            appendLine("=== Completion Debug Info from $source ===")
            appendLine("Offset: $offset")
            appendLine("Position: ${position.textRange}")
            appendLine("Language: ${position.language}")
            appendLine("File Type: ${position.containingFile?.fileType?.name}")
            appendLine("Context (±20 chars): '$contextText'")
            appendLine("Cursor position in context: ${offset - contextStart}")
            appendLine("Completion type: ${parameters.completionType}")
            appendLine("Invocation count: ${parameters.invocationCount}")
            appendLine("Auto-popup: ${parameters.isAutoPopup}")
            
            // 查找 @ 符号
            val atIndex = findNearestAt(text, offset)
            if (atIndex >= 0) {
                appendLine("Found @ at index: $atIndex")
                val query = if (offset > atIndex + 1) {
                    text.substring(atIndex + 1, offset)
                } else {
                    ""
                }
                appendLine("Query after @: '$query'")
            } else {
                appendLine("No @ symbol found near cursor")
            }
        }
        
        LOG.info(debugInfo)
        println(debugInfo) // 同时输出到控制台
    }
    
    private fun findNearestAt(text: String, offset: Int): Int {
        // 向前搜索最近的 @ 符号
        for (i in offset - 1 downTo maxOf(0, offset - 100)) {
            if (i < text.length && text[i] == '@') {
                // 检查前面是否有有效的前缀（@ 可以是第一个字符）
                val hasValidPrefix = i == 0 || text[i - 1] in " \n\t\r"
                if (hasValidPrefix) {
                    return i
                }
            }
        }
        return -1
    }
}