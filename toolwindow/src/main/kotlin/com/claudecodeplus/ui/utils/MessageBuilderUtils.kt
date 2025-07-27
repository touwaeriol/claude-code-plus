package com.claudecodeplus.ui.utils

import com.claudecodeplus.ui.models.ContextReference

/**
 * 消息构建工具类
 * 提供统一的消息构建功能
 */
object MessageBuilderUtils {
    
    /**
     * 构建包含上下文的完整消息
     * 只处理TAG类型上下文（Add Context按钮添加的）
     * @符号添加的上下文不会进入contexts列表，直接在userMessage中
     * 
     * @param contexts 上下文引用列表
     * @param userMessage 用户消息
     * @return 包含上下文的完整消息
     */
    fun buildFinalMessage(contexts: List<ContextReference>, userMessage: String): String {
        if (contexts.isEmpty()) {
            return userMessage
        }
        
        val contextSection = buildString {
            appendLine("> **上下文资料**")
            appendLine("> ")
            
            contexts.forEach { context ->
                val contextLine = when (context) {
                    is ContextReference.FileReference -> {
                        "> - 📄 `${context.path}`"
                    }
                    is ContextReference.WebReference -> {
                        val title = context.title?.let { " ($it)" } ?: ""
                        "> - 🌐 ${context.url}$title"
                    }
                    is ContextReference.FolderReference -> {
                        "> - 📁 `${context.path}` (${context.fileCount}个文件)"
                    }
                    is ContextReference.SymbolReference -> {
                        "> - 🔗 `${context.name}` (${context.type}) - ${context.file}:${context.line}"
                    }
                    is ContextReference.TerminalReference -> {
                        val errorFlag = if (context.isError) " ⚠️" else ""
                        "> - 💻 终端输出 (${context.lines}行)$errorFlag"
                    }
                    is ContextReference.ProblemsReference -> {
                        val severityText = context.severity?.let { " [$it]" } ?: ""
                        "> - ⚠️ 问题报告 (${context.problems.size}个)$severityText"
                    }
                    is ContextReference.GitReference -> {
                        "> - 🔀 Git ${context.type}"
                    }
                    is ContextReference.ImageReference -> {
                        "> - 🖼 `${context.filename}` (${context.size / 1024}KB)"
                    }
                    is ContextReference.SelectionReference -> {
                        "> - ✏️ 当前选择内容"
                    }
                    is ContextReference.WorkspaceReference -> {
                        "> - 🏠 当前工作区"
                    }
                }
                appendLine(contextLine)
            }
            
            appendLine()
        }
        
        return contextSection + userMessage
    }
    
    /**
     * 构建包含上下文的消息 - 保留旧版本作为向后兼容
     * @deprecated 使用 buildFinalMessage 代替
     */
    @Deprecated("Use buildFinalMessage instead", ReplaceWith("buildFinalMessage(contexts, message)"))
    fun buildMessageWithContext(
        message: String,
        contexts: List<ContextReference>
    ): String {
        return buildFinalMessage(contexts, message)
    }
}