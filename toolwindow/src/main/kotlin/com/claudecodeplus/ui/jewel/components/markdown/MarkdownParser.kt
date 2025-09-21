package com.claudecodeplus.ui.jewel.components.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/**
 * Markdown 语法解析器
 * 负责将 Markdown 文本解析为 CommonMark 语法树
 */
class MarkdownParser {

    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                // GFM (GitHub Flavored Markdown) 扩展
                TablesExtension.create(),           // 表格支持
                StrikethroughExtension.create(),    // 删除线支持
                AutolinkExtension.create(),         // 自动链接识别
                TaskListItemsExtension.create(),    // 任务列表支持
                HeadingAnchorExtension.create()     // 标题锚点支持
            )
        )
        .build()

    /**
     * 解析 Markdown 文本
     * @param markdown 原始 Markdown 文本
     * @return CommonMark 语法树根节点
     */
    fun parse(markdown: String): Node {
        return parser.parse(markdown)
    }

    companion object {
        // 单例实例，避免重复创建解析器
        private var instance: MarkdownParser? = null

        fun getInstance(): MarkdownParser {
            if (instance == null) {
                instance = MarkdownParser()
            }
            return instance!!
        }
    }
}