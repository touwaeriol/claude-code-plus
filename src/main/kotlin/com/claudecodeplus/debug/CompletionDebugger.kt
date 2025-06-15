package com.claudecodeplus.debug

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.PlainTextLanguage

/**
 * 用于调试自动完成问题的工具类
 */
object CompletionDebugger {
    private val logger = Logger.getInstance(CompletionDebugger::class.java)
    
    fun debugCompletionContext(
        parameters: CompletionParameters,
        result: CompletionResultSet,
        location: String
    ) {
        val position = parameters.position
        val offset = parameters.offset
        val document = parameters.editor.document
        val text = document.text
        val language = position.language
        
        // 记录调试信息
        val debugInfo = buildString {
            appendLine("=== Completion Debug Info ===")
            appendLine("Location: $location")
            appendLine("Language: ${language.id} (${language.displayName})")
            appendLine("Is PlainText: ${language == PlainTextLanguage.INSTANCE}")
            appendLine("Offset: $offset")
            appendLine("Text length: ${text.length}")
            
            // 获取光标附近的文本
            val start = maxOf(0, offset - 20)
            val end = minOf(text.length, offset + 20)
            val contextText = text.substring(start, end)
            val relativeOffset = offset - start
            
            appendLine("Context (20 chars before/after):")
            appendLine("Text: [$contextText]")
            appendLine("Cursor position: ${" ".repeat(relativeOffset)}^")
            
            // 查找 @ 符号
            var foundAt = false
            for (i in offset - 1 downTo maxOf(0, offset - 100)) {
                if (i < text.length && text[i] == '@') {
                    foundAt = true
                    val hasSpace = i == 0 || text[i - 1] in " \n\t"
                    appendLine("Found @ at position $i, has space before: $hasSpace")
                    if (i + 1 < offset) {
                        appendLine("Text after @: [${text.substring(i + 1, offset)}]")
                    }
                    break
                }
            }
            if (!foundAt) {
                appendLine("No @ symbol found within 100 characters")
            }
        }
        
        // 记录到日志
        logger.info(debugInfo)
        
        // 在开发模式下，也显示通知
        if (System.getProperty("idea.is.internal")?.toBoolean() == true ||
            System.getProperty("idea.debug.mode")?.toBoolean() == true) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Code Plus")
                .createNotification(
                    "Completion Debug",
                    debugInfo,
                    NotificationType.INFORMATION
                )
                .notify(position.project)
        }
    }
    
    fun logLanguageInfo() {
        val plainTextLang = PlainTextLanguage.INSTANCE
        logger.info("PlainTextLanguage ID: ${plainTextLang.id}")
        logger.info("PlainTextLanguage DisplayName: ${plainTextLang.displayName}")
    }
}