package com.claudecodeplus.ui

/**
 * 自定义 Markdown 样式，用于渲染不同类型的消息块
 */
object CustomMarkdownStyles {
    
    /**
     * 获取自定义 CSS 样式
     */
    fun getCustomCSS(): String = """
        <style>
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                line-height: 1.6;
                color: #333;
                padding: 10px;
            }
            
            /* 通用代码块样式 */
            pre {
                background-color: #f5f5f5;
                padding: 10px;
                border-radius: 5px;
                overflow-x: auto;
                margin: 10px 0;
            }
            
            code {
                background-color: #f5f5f5;
                padding: 2px 4px;
                border-radius: 3px;
                font-family: 'JetBrains Mono', monospace;
            }
            
            /* 工具调用样式 */
            .tool-use {
                background-color: #e3f2fd;
                border-left: 4px solid #2196f3;
                padding: 8px 12px;
                margin: 10px 0;
                border-radius: 4px;
            }
            
            .tool-use-title {
                font-weight: bold;
                color: #1976d2;
                margin-bottom: 5px;
            }
            
            /* 工具结果样式 */
            .tool-result {
                background-color: #f3e5f5;
                border-left: 4px solid #9c27b0;
                padding: 8px 12px;
                margin: 10px 0;
                border-radius: 4px;
            }
            
            .tool-result-title {
                font-weight: bold;
                color: #7b1fa2;
                margin-bottom: 5px;
            }
            
            /* 错误消息样式 */
            .error {
                background-color: #ffebee;
                border-left: 4px solid #f44336;
                padding: 8px 12px;
                margin: 10px 0;
                border-radius: 4px;
                color: #c62828;
            }
            
            /* 系统消息样式 */
            .system {
                background-color: #fafafa;
                border-left: 4px solid #9e9e9e;
                padding: 8px 12px;
                margin: 10px 0;
                border-radius: 4px;
                color: #616161;
                font-style: italic;
            }
            
            /* 文件引用样式 */
            a[href^="file://"] {
                color: #4caf50;
                text-decoration: none;
                font-weight: 500;
            }
            
            a[href^="file://"]:hover {
                text-decoration: underline;
            }
            
            /* 用户消息样式 */
            .user-message {
                background-color: #e8f5e9;
                border-radius: 8px;
                padding: 10px 15px;
                margin: 10px 0;
                border: 1px solid #c8e6c9;
            }
            
            /* AI 响应样式 */
            .assistant-message {
                background-color: #ffffff;
                border-radius: 8px;
                padding: 10px 15px;
                margin: 10px 0;
                border: 1px solid #e0e0e0;
            }
            
            /* 紧凑的工具调用样式（类似 Claude Code CLI） */
            .compact-tool-call {
                background-color: #f5f5f5;
                border-radius: 4px;
                padding: 4px 8px;
                margin: 5px 0;
                font-size: 0.9em;
                color: #666;
                display: inline-block;
            }
            
            /* Markdown 元素样式 */
            h1, h2, h3, h4, h5, h6 {
                margin-top: 16px;
                margin-bottom: 8px;
            }
            
            ul, ol {
                padding-left: 20px;
            }
            
            blockquote {
                border-left: 4px solid #ddd;
                padding-left: 16px;
                margin-left: 0;
                color: #666;
            }
            
            table {
                border-collapse: collapse;
                width: 100%;
                margin: 10px 0;
            }
            
            table th, table td {
                border: 1px solid #ddd;
                padding: 8px;
                text-align: left;
            }
            
            table th {
                background-color: #f5f5f5;
                font-weight: bold;
            }
        </style>
    """.trimIndent()
    
    /**
     * 处理自定义代码块类型
     */
    fun processCustomCodeBlocks(html: String): String {
        return html
            // 处理工具调用块
            .replace(
                Regex("""<pre><code class="language-tool-use">(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL),
                """<div class="tool-use">$1</div>"""
            )
            // 处理工具结果块
            .replace(
                Regex("""<pre><code class="language-tool-result">(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL),
                """<div class="tool-result">$1</div>"""
            )
            // 处理错误块
            .replace(
                Regex("""<pre><code class="language-error">(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL),
                """<div class="error">$1</div>"""
            )
            // 处理系统消息块
            .replace(
                Regex("""<pre><code class="language-system">(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL),
                """<div class="system">$1</div>"""
            )
    }
    
    /**
     * 包装用户消息
     */
    fun wrapUserMessage(content: String): String {
        return """<div class="user-message">$content</div>"""
    }
    
    /**
     * 包装 AI 响应
     */
    fun wrapAssistantMessage(content: String): String {
        return """<div class="assistant-message">$content</div>"""
    }
}