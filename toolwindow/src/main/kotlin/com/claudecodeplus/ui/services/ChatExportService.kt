package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant

/**
 * 对话导出服务
 */
class ChatExportService {
    
    /**
     * 导出为 Markdown
     */
    suspend fun exportToMarkdown(
        tab: ChatTab,
        config: ExportConfig = ExportConfig(format = ExportFormat.MARKDOWN)
    ): String = withContext(Dispatchers.Default) {
        buildString {
            // 标题
            appendLine("# ${tab.title}")
            appendLine()
            
            // 元数据
            if (config.includeMetadata) {
                appendLine("## 元数据")
                appendLine()
                appendLine("- **创建时间**: ${formatTime(tab.createdAt)}")
                appendLine("- **最后修改**: ${formatTime(tab.lastModified)}")
                appendLine("- **消息数量**: ${tab.messages.size}")
                
                if (tab.tags.isNotEmpty()) {
                    appendLine("- **标签**: ${tab.tags.joinToString(", ") { it.name }}")
                }
                
                if (tab.summary != null) {
                    appendLine()
                    appendLine("### 摘要")
                    appendLine(tab.summary)
                }
                
                appendLine()
                appendLine("---")
                appendLine()
            }
            
            // 上下文
            if (config.includeContext && tab.context.isNotEmpty()) {
                appendLine("## 上下文")
                appendLine()
                
                tab.context.forEach { context ->
                    when (context) {
                        is ContextItem.File -> {
                            appendLine("- 📄 文件: `${context.path}`")
                        }
                        is ContextItem.Folder -> {
                            appendLine("- 📁 文件夹: `${context.path}`")
                            context.includePattern?.let {
                                appendLine("  - 包含: `$it`")
                            }
                            context.excludePattern?.let {
                                appendLine("  - 排除: `$it`")
                            }
                        }
                        is ContextItem.CodeBlock -> {
                            appendLine("- 💻 代码块: ${context.description ?: context.language}")
                        }
                    }
                }
                
                appendLine()
                appendLine("---")
                appendLine()
            }
            
            // 对话内容
            appendLine("## 对话内容")
            appendLine()
            
            tab.messages.forEach { message ->
                // 角色标识
                val roleEmoji = when (message.role) {
                    MessageRole.USER -> "👤"
                    MessageRole.ASSISTANT -> "🤖"
                    MessageRole.SYSTEM -> "⚙️"
                    MessageRole.ERROR -> "❌"
                }
                
                val roleName = when (message.role) {
                    MessageRole.USER -> "用户"
                    MessageRole.ASSISTANT -> "AI"
                    MessageRole.SYSTEM -> "系统"
                    MessageRole.ERROR -> "错误"
                }
                
                appendLine("### $roleEmoji $roleName")
                
                if (config.includeTimestamps) {
                    appendLine("*${formatTime(Instant.ofEpochMilli(message.timestamp))}*")
                    appendLine()
                }
                
                // 消息内容
                appendLine(message.content)
                
                // 工具调用（替代附件）
                if (message.toolCalls.isNotEmpty()) {
                    appendLine()
                    appendLine("#### 工具调用:")
                    message.toolCalls.forEach { toolCall ->
                        appendLine("- **${toolCall.name}** (${toolCall.status})")
                        when (val result = toolCall.result) {
                            is ToolResult.Success -> {
                                if (result.output.isNotEmpty()) {
                                    appendLine("  ```")
                                    appendLine("  ${result.output.take(200)}${if (result.output.length > 200) "..." else ""}")
                                    appendLine("  ```")
                                }
                            }
                            is ToolResult.Failure -> {
                                appendLine("  错误: ${result.error}")
                            }
                            is ToolResult.CommandResult -> {
                                appendLine("  命令输出:")
                                appendLine("  ```")
                                appendLine("  ${result.output.take(200)}${if (result.output.length > 200) "..." else ""}")
                                appendLine("  ```")
                                appendLine("  退出码: ${result.exitCode}")
                            }
                            is ToolResult.FileSearchResult -> {
                                appendLine("  找到 ${result.totalCount} 个文件")
                            }
                            is ToolResult.FileReadResult -> {
                                appendLine("  读取 ${result.lineCount} 行")
                            }
                            is ToolResult.FileEditResult -> {
                                appendLine("  修改行: ${result.changedLines}")
                            }
                            null -> {}
                        }
                    }
                }
                
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }
    
    /**
     * 导出为 HTML
     */
    suspend fun exportToHtml(
        tab: ChatTab,
        config: ExportConfig = ExportConfig(format = ExportFormat.HTML)
    ): String = withContext(Dispatchers.Default) {
        val markdown = exportToMarkdown(tab, config)
        val htmlContent = convertMarkdownToHtml(markdown)
        
        """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${tab.title}</title>
            <style>
                ${getHtmlStyles(config)}
            </style>
        </head>
        <body>
            <div class="container">
                $htmlContent
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    /**
     * 导出为 JSON
     */
    suspend fun exportToJson(
        tab: ChatTab,
        config: ExportConfig = ExportConfig(format = ExportFormat.JSON)
    ): String = withContext(Dispatchers.Default) {
        val json = buildString {
            appendLine("{")
            appendLine("  \"title\": \"${escapeJson(tab.title)}\",")
            appendLine("  \"id\": \"${tab.id}\",")
            appendLine("  \"createdAt\": \"${tab.createdAt}\",")
            appendLine("  \"lastModified\": \"${tab.lastModified}\",")
            
            if (config.includeMetadata) {
                appendLine("  \"metadata\": {")
                appendLine("    \"sessionId\": ${tab.sessionId?.let { "\"$it\"" } ?: "null"},")
                appendLine("    \"groupId\": ${tab.groupId?.let { "\"$it\"" } ?: "null"},")
                appendLine("    \"status\": \"${tab.status}\",")
                appendLine("    \"summary\": ${tab.summary?.let { "\"${escapeJson(it)}\"" } ?: "null"},")
                appendLine("    \"tags\": [")
                tab.tags.forEachIndexed { index, tag ->
                    append("      { \"id\": \"${tag.id}\", \"name\": \"${escapeJson(tag.name)}\" }")
                    if (index < tab.tags.size - 1) append(",")
                    appendLine()
                }
                appendLine("    ]")
                appendLine("  },")
            }
            
            if (config.includeContext) {
                appendLine("  \"context\": [")
                tab.context.forEachIndexed { index, context ->
                    append("    ")
                    when (context) {
                        is ContextItem.File -> {
                            append("{ \"type\": \"file\", \"path\": \"${escapeJson(context.path)}\" }")
                        }
                        is ContextItem.Folder -> {
                            append("{ \"type\": \"folder\", \"path\": \"${escapeJson(context.path)}\"")
                            context.includePattern?.let {
                                append(", \"includePattern\": \"${escapeJson(it)}\"")
                            }
                            context.excludePattern?.let {
                                append(", \"excludePattern\": \"${escapeJson(it)}\"")
                            }
                            append(" }")
                        }
                        is ContextItem.CodeBlock -> {
                            append("{ \"type\": \"code\", \"language\": \"${context.language}\", ")
                            append("\"content\": \"${escapeJson(context.content)}\"")
                            context.description?.let {
                                append(", \"description\": \"${escapeJson(it)}\"")
                            }
                            append(" }")
                        }
                    }
                    if (index < tab.context.size - 1) append(",")
                    appendLine()
                }
                appendLine("  ],")
            }
            
            appendLine("  \"messages\": [")
            tab.messages.forEachIndexed { index, message ->
                appendLine("    {")
                appendLine("      \"id\": \"${message.id}\",")
                appendLine("      \"role\": \"${message.role}\",")
                appendLine("      \"content\": \"${escapeJson(message.content)}\",")
                if (config.includeTimestamps) {
                    appendLine("      \"timestamp\": \"${message.timestamp}\",")
                }
                appendLine("      \"toolCalls\": [")
                message.toolCalls.forEachIndexed { toolIndex, toolCall ->
                    append("        ")
                    append("{ \"id\": \"${toolCall.id}\", ")
                    append("\"name\": \"${toolCall.name}\", ")
                    append("\"status\": \"${toolCall.status}\"")
                    when (val result = toolCall.result) {
                        is ToolResult.Success -> {
                            append(", \"result\": { ")
                            append("\"type\": \"success\", ")
                            append("\"output\": \"${escapeJson(result.output.take(200))}\"")
                            append(" }")
                        }
                        is ToolResult.Failure -> {
                            append(", \"result\": { ")
                            append("\"type\": \"failure\", ")
                            append("\"error\": \"${escapeJson(result.error)}\"")
                            append(" }")
                        }
                        is ToolResult.CommandResult -> {
                            append(", \"result\": { ")
                            append("\"type\": \"command\", ")
                            append("\"output\": \"${escapeJson(result.output.take(200))}\", ")
                            append("\"exitCode\": ${result.exitCode}")
                            append(" }")
                        }
                        is ToolResult.FileSearchResult -> {
                            append(", \"result\": { ")
                            append("\"type\": \"file_search\", ")
                            append("\"totalCount\": ${result.totalCount}")
                            append(" }")
                        }
                        is ToolResult.FileReadResult -> {
                            append(", \"result\": { ")
                            append("\"type\": \"file_read\", ")
                            append("\"lineCount\": ${result.lineCount}")
                            append(" }")
                        }
                        is ToolResult.FileEditResult -> {
                            append(", \"result\": { ")
                            append("\"type\": \"file_edit\", ")
                            append("\"changedLines\": \"${result.changedLines}\"")
                            append(" }")
                        }
                        null -> {}
                    }
                    append(" }")
                    if (toolIndex < message.toolCalls.size - 1) append(",")
                    appendLine()
                }
                appendLine("      ]")
                append("    }")
                if (index < tab.messages.size - 1) append(",")
                appendLine()
            }
            appendLine("  ]")
            appendLine("}")
        }
        
        json
    }
    
    /**
     * 批量导出
     */
    suspend fun exportMultiple(
        tabs: List<ChatTab>,
        format: ExportFormat,
        outputDir: String
    ): ExportResult = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExportFileResult>()
        var successCount = 0
        var failureCount = 0
        
        tabs.forEach { tab ->
            try {
                val filename = sanitizeFilename(tab.title)
                val extension = when (format) {
                    ExportFormat.MARKDOWN -> "md"
                    ExportFormat.HTML -> "html"
                    ExportFormat.JSON -> "json"
                    ExportFormat.PDF -> "pdf"
                }
                
                val content = when (format) {
                    ExportFormat.MARKDOWN -> exportToMarkdown(tab)
                    ExportFormat.HTML -> exportToHtml(tab)
                    ExportFormat.JSON -> exportToJson(tab)
                    else -> throw UnsupportedOperationException("Format $format not yet implemented")
                }
                
                val file = File(outputDir, "$filename.$extension")
                file.writeText(content)
                
                results.add(ExportFileResult(
                    tabId = tab.id,
                    tabTitle = tab.title,
                    filePath = file.absolutePath,
                    success = true
                ))
                successCount++
            } catch (e: Exception) {
                results.add(ExportFileResult(
                    tabId = tab.id,
                    tabTitle = tab.title,
                    filePath = "",
                    success = false,
                    error = e.message
                ))
                failureCount++
            }
        }
        
        ExportResult(
            totalCount = tabs.size,
            successCount = successCount,
            failureCount = failureCount,
            files = results
        )
    }
    
    /**
     * 获取 HTML 样式
     */
    private fun getHtmlStyles(config: ExportConfig): String {
        val isDark = config.theme == "dark"
        
        return """
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                line-height: 1.6;
                color: ${if (isDark) "#e0e0e0" else "#333"};
                background-color: ${if (isDark) "#1e1e1e" else "#fff"};
                margin: 0;
                padding: 0;
            }
            
            .container {
                max-width: 800px;
                margin: 0 auto;
                padding: 20px;
            }
            
            h1, h2, h3 {
                color: ${if (isDark) "#fff" else "#000"};
            }
            
            h1 { border-bottom: 2px solid ${if (isDark) "#444" else "#e0e0e0"}; padding-bottom: 10px; }
            h2 { margin-top: 30px; }
            h3 { margin-top: 20px; }
            
            code {
                background-color: ${if (isDark) "#2d2d2d" else "#f4f4f4"};
                padding: 2px 4px;
                border-radius: 3px;
                font-family: 'Consolas', 'Monaco', monospace;
            }
            
            pre {
                background-color: ${if (isDark) "#2d2d2d" else "#f4f4f4"};
                padding: 15px;
                border-radius: 5px;
                overflow-x: auto;
            }
            
            blockquote {
                border-left: 4px solid ${if (isDark) "#666" else "#ddd"};
                margin-left: 0;
                padding-left: 20px;
                color: ${if (isDark) "#ccc" else "#666"};
            }
            
            hr {
                border: none;
                border-top: 1px solid ${if (isDark) "#444" else "#e0e0e0"};
                margin: 20px 0;
            }
            
            .timestamp {
                color: ${if (isDark) "#999" else "#666"};
                font-size: 0.9em;
                font-style: italic;
            }
            
            .role-user { color: #2196F3; }
            .role-assistant { color: #4CAF50; }
            .role-system { color: #FF9800; }
            .role-error { color: #F44336; }
            
            ${config.customCss ?: ""}
        """.trimIndent()
    }
    
    /**
     * Markdown 转 HTML（简单实现）
     */
    private fun convertMarkdownToHtml(markdown: String): String {
        // 这是一个简化的实现，实际项目中应使用专门的 Markdown 解析库
        return markdown
            .replace(Regex("^# (.+)$", RegexOption.MULTILINE), "<h1>$1</h1>")
            .replace(Regex("^## (.+)$", RegexOption.MULTILINE), "<h2>$1</h2>")
            .replace(Regex("^### (.+)$", RegexOption.MULTILINE), "<h3>$1</h3>")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("`(.+?)`"), "<code>$1</code>")
            .replace(Regex("^---$", RegexOption.MULTILINE), "<hr>")
            .replace(Regex("^- (.+)$", RegexOption.MULTILINE), "<li>$1</li>")
            .replace("\n\n", "</p>\n<p>")
            .let { "<p>$it</p>" }
    }
    
    /**
     * 转义 JSON 字符串
     */
    private fun escapeJson(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
    
    /**
     * 清理文件名
     */
    private fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(100) // 限制长度
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(instant: java.time.Instant): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        return formatter.format(instant)
    }
}

/**
 * 导出结果
 */
data class ExportResult(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val files: List<ExportFileResult>
)

/**
 * 单个文件导出结果
 */
data class ExportFileResult(
    val tabId: String,
    val tabTitle: String,
    val filePath: String,
    val success: Boolean,
    val error: String? = null
)