package com.claudecodeplus.util

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import javax.swing.JEditorPane
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet
import java.awt.Color
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil

/**
 * Markdown 渲染器，将 Markdown 转换为 HTML 并应用样式
 */
object MarkdownRenderer {
    
    // CommonMark 扩展
    private val extensions: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create()
    )
    
    // Markdown 解析器
    private val parser: Parser = Parser.builder()
        .extensions(extensions)
        .build()
    
    // HTML 渲染器
    private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .softbreak("<br />")
        .build()
    
    /**
     * 将 Markdown 文本转换为 HTML
     */
    fun markdownToHtml(markdown: String): String {
        val document = parser.parse(markdown)
        return htmlRenderer.render(document)
    }
    
    /**
     * 创建支持 Markdown 的 JEditorPane
     */
    fun createMarkdownPane(): JEditorPane {
        val editorPane = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            
            // 设置 HTML 编辑器套件
            val kit = HTMLEditorKit()
            editorKit = kit
            
            // 应用自定义样式
            kit.styleSheet.apply {
                // 基础样式
                addRule("body { font-family: ${UIUtil.getLabelFont().family}; font-size: ${UIUtil.getLabelFont().size}pt; color: ${colorToHex(JBColor.foreground())}; background-color: ${colorToHex(JBColor.background())}; margin: 10px; }")
                
                // 标题样式
                addRule("h1 { font-size: 1.5em; font-weight: bold; margin-top: 10px; margin-bottom: 10px; }")
                addRule("h2 { font-size: 1.3em; font-weight: bold; margin-top: 8px; margin-bottom: 8px; }")
                addRule("h3 { font-size: 1.1em; font-weight: bold; margin-top: 6px; margin-bottom: 6px; }")
                
                // 代码样式
                addRule("pre { background-color: ${colorToHex(JBColor(Color(245, 245, 245), Color(45, 45, 45)))}; padding: 10px; border-radius: 4px; overflow-x: auto; }")
                addRule("code { background-color: ${colorToHex(JBColor(Color(245, 245, 245), Color(45, 45, 45)))}; padding: 2px 4px; border-radius: 3px; font-family: 'Consolas', 'Monaco', monospace; }")
                
                // 引用样式
                addRule("blockquote { border-left: 4px solid ${colorToHex(JBColor.GRAY)}; padding-left: 10px; margin-left: 0; color: ${colorToHex(JBColor.GRAY)}; }")
                
                // 链接样式
                addRule("a { color: ${colorToHex(JBColor(Color(0, 102, 204), Color(64, 128, 255)))}; text-decoration: none; }")
                addRule("a:hover { text-decoration: underline; }")
                
                // 列表样式
                addRule("ul, ol { margin-left: 20px; margin-top: 5px; margin-bottom: 5px; }")
                addRule("li { margin-top: 2px; margin-bottom: 2px; }")
                
                // 表格样式
                addRule("table { border-collapse: collapse; margin: 10px 0; }")
                addRule("th, td { border: 1px solid ${colorToHex(JBColor.GRAY)}; padding: 6px 12px; }")
                addRule("th { background-color: ${colorToHex(JBColor(Color(240, 240, 240), Color(60, 60, 60)))}; font-weight: bold; }")
                
                // 水平线
                addRule("hr { border: none; border-top: 1px solid ${colorToHex(JBColor.GRAY)}; margin: 10px 0; }")
            }
        }
        
        return editorPane
    }
    
    /**
     * 渲染 Markdown 到 JEditorPane
     */
    fun renderMarkdown(editorPane: JEditorPane, markdown: String) {
        val html = wrapHtml(markdownToHtml(markdown))
        editorPane.text = html
        editorPane.caretPosition = 0
    }
    
    /**
     * 包装 HTML 内容
     */
    private fun wrapHtml(htmlContent: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body>
                $htmlContent
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * 将 Color 转换为十六进制字符串
     */
    private fun colorToHex(color: Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }
    
    /**
     * 检测文本是否包含 Markdown 格式
     */
    fun containsMarkdown(text: String): Boolean {
        // 检查常见的 Markdown 标记
        val markdownPatterns = listOf(
            "^#{1,6}\\s+",           // 标题
            "```",                    // 代码块
            "\\*\\*.*?\\*\\*",        // 粗体
            "__.*?__",                // 粗体
            "\\*.*?\\*",              // 斜体
            "_.*?_",                  // 斜体
            "\\[.*?\\]\\(.*?\\)",     // 链接
            "^\\s*[-*+]\\s+",         // 无序列表
            "^\\s*\\d+\\.\\s+",       // 有序列表
            "^>\\s+",                 // 引用
            "\\|.*?\\|",              // 表格
            "^---+$",                 // 水平线
            "~~.*?~~"                 // 删除线
        )
        
        return markdownPatterns.any { pattern ->
            text.contains(Regex(pattern, RegexOption.MULTILINE))
        }
    }
}

/**
 * 扩展函数：为 StyleSheet 添加 CSS 规则
 */
private fun StyleSheet.addRule(rule: String) {
    this.addRule(rule)
}