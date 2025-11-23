package com.claudecodeplus.plugin.ui.markdown

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.*
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

/**
 * Markdown 渲染器
 * 
 * 使用 commonmark-java 将 Markdown 文本渲染为 Swing 组件
 */
class MarkdownRenderer(private val project: Project? = null) {
    
    private val parser: Parser
    private val htmlRenderer: HtmlRenderer
    private val codeHighlighter = CodeHighlighter(project)
    
    init {
        val extensions: List<Extension> = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create()
        )
        
        parser = Parser.builder()
            .extensions(extensions)
            .build()
        
        htmlRenderer = HtmlRenderer.builder()
            .extensions(extensions)
            .build()
    }
    
    /**
     * 将 Markdown 文本渲染为 Swing 组件
     * 
     * @param markdown Markdown 文本
     * @param theme 主题信息（用于代码块语法高亮）
     * @return 渲染后的 Swing 组件
     */
    fun render(markdown: String, theme: MarkdownTheme = MarkdownTheme.default()): JComponent {
        if (markdown.isBlank()) {
            return JPanel()
        }
        
        val document = parser.parse(markdown)
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = theme.background
        panel.border = EmptyBorder(JBUI.insets(8))
        
        // 遍历文档节点并渲染
        var child = document.firstChild
        while (child != null) {
            val component = renderNode(child, theme)
            if (component != null) {
                panel.add(component)
                panel.add(Box.createVerticalStrut(4))
            }
            child = child.next
        }
        
        return panel
    }
    
    /**
     * 渲染单个节点
     */
    private fun renderNode(node: Node, theme: MarkdownTheme): JComponent? {
        return when (node) {
            is Heading -> renderHeading(node, theme)
            is Paragraph -> renderParagraph(node, theme)
            is Code -> renderInlineCode(node, theme)
            is FencedCodeBlock -> renderCodeBlock(node, theme)
            is BlockQuote -> renderBlockQuote(node, theme)
            is BulletList -> renderBulletList(node, theme)
            is OrderedList -> renderOrderedList(node, theme)
            is ListItem -> renderListItem(node, theme)
            is ThematicBreak -> renderThematicBreak(theme)
            is StrongEmphasis -> renderStrongEmphasis(node, theme)
            is Emphasis -> renderEmphasis(node, theme)
            is Link -> renderLink(node, theme)
            is Text -> renderText(node, theme)
            is HardLineBreak -> renderLineBreak()
            is SoftLineBreak -> renderLineBreak()
            is TableBlock -> renderTable(node, theme)
            else -> null
        }
    }
    
    /**
     * 渲染标题
     */
    private fun renderHeading(heading: Heading, theme: MarkdownTheme): JComponent {
        val level = heading.level
        val text = extractText(heading)
        
        val label = JLabel(text)
        label.font = when (level) {
            1 -> theme.font.deriveFont(Font.BOLD, theme.font.size + 6f)
            2 -> theme.font.deriveFont(Font.BOLD, theme.font.size + 4f)
            3 -> theme.font.deriveFont(Font.BOLD, theme.font.size + 2f)
            else -> theme.font.deriveFont(Font.BOLD)
        }
        label.foreground = theme.headingColor
        label.border = EmptyBorder(JBUI.insets(if (level == 1) 8 else 4, 0, 4, 0))
        
        return label
    }
    
    /**
     * 渲染段落
     */
    private fun renderParagraph(paragraph: Paragraph, theme: MarkdownTheme): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.background = theme.background
        panel.alignmentX = 0f
        
        val text = extractText(paragraph)
        val label = JLabel(formatInlineMarkdown(text, theme))
        label.font = theme.font
        label.foreground = theme.textColor
        label.verticalAlignment = SwingConstants.TOP
        
        panel.add(label)
        panel.add(Box.createHorizontalGlue())
        
        return panel
    }
    
    /**
     * 渲染行内代码
     */
    private fun renderInlineCode(code: Code, theme: MarkdownTheme): JComponent {
        val label = JLabel(code.literal)
        label.font = Font(Font.MONOSPACED, Font.PLAIN, theme.font.size)
        label.foreground = theme.codeTextColor
        label.background = theme.codeBackground
        label.isOpaque = true
        label.border = EmptyBorder(JBUI.insets(2, 4, 2, 4))
        
        return label
    }
    
    /**
     * 渲染代码块（带语法高亮）
     */
    private fun renderCodeBlock(codeBlock: FencedCodeBlock, theme: MarkdownTheme): JComponent {
        val language = codeBlock.info
        val code = codeBlock.literal
        
        // 使用 JTextPane 以支持样式化文本
        val textPane = JTextPane()
        textPane.font = Font(Font.MONOSPACED, Font.PLAIN, theme.font.size - 1)
        textPane.background = theme.codeBackground
        textPane.isEditable = false
        textPane.isOpaque = true
        textPane.border = EmptyBorder(JBUI.insets(8))
        
        // 应用语法高亮
        val doc = textPane.styledDocument
        val segments = codeHighlighter.highlight(code, language)
        
        for (segment in segments) {
            val attrs = SimpleAttributeSet()
            StyleConstants.setForeground(attrs, segment.color)
            StyleConstants.setFontFamily(attrs, Font.MONOSPACED)
            StyleConstants.setFontSize(attrs, theme.font.size - 1)
            if (segment.fontStyle == Font.BOLD) {
                StyleConstants.setBold(attrs, true)
            } else if (segment.fontStyle == Font.ITALIC) {
                StyleConstants.setItalic(attrs, true)
            }
            
            doc.insertString(doc.length, segment.text, attrs)
        }
        
        // 添加语言标签
        val panel = JPanel(BorderLayout())
        panel.background = theme.codeBackground
        
        if (!language.isNullOrBlank()) {
            val langLabel = JLabel(language.uppercase())
            langLabel.font = theme.font.deriveFont(Font.BOLD, (theme.font.size - 2).toFloat())
            langLabel.foreground = theme.codeTextColor.brighter()
            langLabel.border = EmptyBorder(JBUI.insets(4, 8, 4, 8))
            panel.add(langLabel, BorderLayout.NORTH)
        }
        
        val scrollPane = JScrollPane(textPane)
        scrollPane.border = EmptyBorder(JBUI.insets(0))
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.borderColor),
            EmptyBorder(JBUI.insets(4))
        )
        
        return panel
    }
    
    /**
     * 渲染引用块
     */
    private fun renderBlockQuote(blockQuote: BlockQuote, theme: MarkdownTheme): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 4, 0, 0, theme.quoteBorderColor),
            EmptyBorder(JBUI.insets(4, 8, 4, 8))
        )
        panel.background = theme.quoteBackground
        
        var child = blockQuote.firstChild
        while (child != null) {
            val component = renderNode(child, theme)
            if (component != null) {
                panel.add(component)
            }
            child = child.next
        }
        
        return panel
    }
    
    /**
     * 渲染无序列表
     */
    private fun renderBulletList(list: BulletList, theme: MarkdownTheme): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = theme.background
        panel.alignmentX = 0f
        
        var child = list.firstChild
        while (child != null) {
            if (child is ListItem) {
                val item = renderListItem(child, theme)
                panel.add(item)
            }
            child = child.next
        }
        
        return panel
    }
    
    /**
     * 渲染有序列表
     */
    private fun renderOrderedList(list: OrderedList, theme: MarkdownTheme): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = theme.background
        panel.alignmentX = 0f
        
        var index = list.startNumber
        var child = list.firstChild
        while (child != null) {
            if (child is ListItem) {
                val item = renderListItem(child, theme, index.toString() + ".")
                panel.add(item)
                index++
            }
            child = child.next
        }
        
        return panel
    }
    
    /**
     * 渲染列表项
     */
    private fun renderListItem(item: ListItem, theme: MarkdownTheme, prefix: String = "•"): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.background = theme.background
        panel.alignmentX = 0f
        
        val prefixLabel = JLabel("$prefix ")
        prefixLabel.font = theme.font
        prefixLabel.foreground = theme.textColor
        panel.add(prefixLabel)
        
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.background = theme.background
        
        var child = item.firstChild
        while (child != null) {
            val component = renderNode(child, theme)
            if (component != null) {
                contentPanel.add(component)
            }
            child = child.next
        }
        
        panel.add(contentPanel)
        panel.add(Box.createHorizontalGlue())
        
        return panel
    }
    
    /**
     * 渲染水平分割线
     */
    private fun renderThematicBreak(theme: MarkdownTheme): JComponent {
        val separator = JSeparator(SwingConstants.HORIZONTAL)
        separator.foreground = theme.borderColor
        separator.border = EmptyBorder(JBUI.insets(8, 0))
        return separator
    }
    
    /**
     * 渲染粗体
     */
    private fun renderStrongEmphasis(emphasis: StrongEmphasis, theme: MarkdownTheme): JComponent {
        val text = extractText(emphasis)
        val label = JLabel(text)
        label.font = theme.font.deriveFont(Font.BOLD)
        label.foreground = theme.textColor
        return label
    }
    
    /**
     * 渲染斜体
     */
    private fun renderEmphasis(emphasis: Emphasis, theme: MarkdownTheme): JComponent {
        val text = extractText(emphasis)
        val label = JLabel(text)
        label.font = theme.font.deriveFont(Font.ITALIC)
        label.foreground = theme.textColor
        return label
    }
    
    /**
     * 渲染链接
     */
    private fun renderLink(link: Link, theme: MarkdownTheme): JComponent {
        val text = extractText(link)
        val label = JLabel("<html><a href='${link.destination}'>$text</a></html>")
        label.font = theme.font
        label.foreground = theme.linkColor
        label.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        return label
    }
    
    /**
     * 渲染文本
     */
    private fun renderText(text: Text, theme: MarkdownTheme): JComponent {
        val label = JLabel(text.literal)
        label.font = theme.font
        label.foreground = theme.textColor
        return label
    }
    
    /**
     * 渲染换行
     */
    private fun renderLineBreak(): JComponent {
        return Box.createVerticalStrut(4) as JComponent
    }
    
    /**
     * 提取节点的文本内容
     */
    private fun extractText(node: Node): String {
        val builder = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> builder.append(child.literal)
                is Code -> builder.append(child.literal)
                else -> builder.append(extractText(child))
            }
            child = child.next
        }
        return builder.toString()
    }
    
    /**
     * 格式化行内 Markdown（简化版，主要用于粗体、斜体、链接）
     */
    private fun formatInlineMarkdown(text: String, theme: MarkdownTheme): String {
        // 简单的 HTML 格式化
        var formatted = text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("\\*(.+?)\\*"), "<i>$1</i>")
            .replace(Regex("`(.+?)`"), "<code style='background: ${toHex(theme.codeBackground)}; color: ${toHex(theme.codeTextColor)}; padding: 2px 4px; border-radius: 3px;'>$1</code>")
        
        return "<html>$formatted</html>"
    }
    
    /**
     * 渲染表格
     */
    private fun renderTable(table: TableBlock, theme: MarkdownTheme): JComponent {
        val tablePanel = JPanel()
        tablePanel.layout = BoxLayout(tablePanel, BoxLayout.Y_AXIS)
        tablePanel.background = theme.background
        tablePanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(theme.borderColor, 1),
            EmptyBorder(JBUI.insets(4))
        )
        
        var child = table.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> {
                    val headRow = renderTableRow(child.firstChild as? TableRow, theme, true)
                    if (headRow != null) {
                        tablePanel.add(headRow)
                    }
                }
                is TableBody -> {
                    var bodyChild = child.firstChild
                    while (bodyChild != null) {
                        if (bodyChild is TableRow) {
                            val bodyRow = renderTableRow(bodyChild, theme, false)
                            if (bodyRow != null) {
                                tablePanel.add(bodyRow)
                            }
                        }
                        bodyChild = bodyChild.next
                    }
                }
            }
            child = child.next
        }
        
        return tablePanel
    }
    
    /**
     * 渲染表格行
     */
    private fun renderTableRow(row: TableRow?, theme: MarkdownTheme, isHeader: Boolean): JComponent? {
        if (row == null) return null
        
        val rowPanel = JPanel()
        rowPanel.layout = GridBagLayout()
        rowPanel.background = if (isHeader) theme.codeBackground else theme.background
        rowPanel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, theme.borderColor)
        
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.BOTH
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.gridy = 0
        gbc.gridx = 0
        
        var cell = row.firstChild
        while (cell != null) {
            if (cell is TableCell) {
                val cellComponent = renderTableCell(cell, theme, isHeader)
                rowPanel.add(cellComponent, gbc)
                gbc.gridx++
            }
            cell = cell.next
        }
        
        return rowPanel
    }
    
    /**
     * 渲染表格单元格
     */
    private fun renderTableCell(cell: TableCell, theme: MarkdownTheme, isHeader: Boolean): JComponent {
        val text = extractText(cell)
        val label = JLabel(text)
        label.font = if (isHeader) {
            theme.font.deriveFont(Font.BOLD)
        } else {
            theme.font
        }
        label.foreground = if (isHeader) theme.headingColor else theme.textColor
        label.border = EmptyBorder(JBUI.insets(4, 8, 4, 8))
        label.horizontalAlignment = SwingConstants.LEFT
        
        return label
    }
    
    /**
     * 将 Color 转换为十六进制字符串
     */
    private fun toHex(color: Color): String {
        return String.format("#%02x%02x%02x", color.red, color.green, color.blue)
    }
}

/**
 * Markdown 主题配置
 */
data class MarkdownTheme(
    val background: Color,
    val textColor: Color,
    val headingColor: Color,
    val codeBackground: Color,
    val codeTextColor: Color,
    val linkColor: Color,
    val borderColor: Color,
    val quoteBackground: Color,
    val quoteBorderColor: Color,
    val font: Font
) {
    companion object {
        /**
         * 创建默认主题（根据 IDEA 主题自动适配）
         */
        fun default(): MarkdownTheme {
            val isDark = com.intellij.util.ui.UIUtil.isUnderDarcula()
            
            return if (isDark) {
                MarkdownTheme(
                    background = JBColor(Color(0x2B2B2B), Color(0x2B2B2B)),
                    textColor = JBColor(Color(0xCCCCCC), Color(0xCCCCCC)),
                    headingColor = JBColor(Color(0xFFFFFF), Color(0xFFFFFF)),
                    codeBackground = JBColor(Color(0x3C3C3C), Color(0x3C3C3C)),
                    codeTextColor = JBColor(Color(0xE6E6E6), Color(0xE6E6E6)),
                    linkColor = JBColor(Color(0x4A9EFF), Color(0x4A9EFF)),
                    borderColor = JBColor(Color(0x515658), Color(0x515658)),
                    quoteBackground = JBColor(Color(0x3C3C3C), Color(0x3C3C3C)),
                    quoteBorderColor = JBColor(Color(0x6A6A6A), Color(0x6A6A6A)),
                    font = UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)
                )
            } else {
                MarkdownTheme(
                    background = JBColor(Color(0xFFFFFF), Color(0xFFFFFF)),
                    textColor = JBColor(Color(0x000000), Color(0x000000)),
                    headingColor = JBColor(Color(0x000000), Color(0x000000)),
                    codeBackground = JBColor(Color(0xF5F5F5), Color(0xF5F5F5)),
                    codeTextColor = JBColor(Color(0x333333), Color(0x333333)),
                    linkColor = JBColor(Color(0x0066CC), Color(0x0066CC)),
                    borderColor = JBColor(Color(0xCCCCCC), Color(0xCCCCCC)),
                    quoteBackground = JBColor(Color(0xF9F9F9), Color(0xF9F9F9)),
                    quoteBorderColor = JBColor(Color(0xCCCCCC), Color(0xCCCCCC)),
                    font = UIManager.getFont("Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)
                )
            }
        }
    }
}

