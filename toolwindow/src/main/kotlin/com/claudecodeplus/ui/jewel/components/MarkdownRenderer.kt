package com.claudecodeplus.ui.jewel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import androidx.compose.material.LocalContentColor

/**
 * Markdown 渲染器组件
 * 支持基本的 Markdown 格式渲染
 */
@Composable
fun MarkdownRenderer(
    markdown: String,
    onCodeAction: (code: String, language: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val blocks = parseMarkdown(markdown)
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Text -> {
                    SelectionContainer {
                        Text(
                            text = block.content,
                            style = JewelTheme.defaultTextStyle
                        )
                    }
                }
                
                is MarkdownBlock.Header -> {
                    SelectionContainer {
                        Text(
                            text = block.content,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = when (block.level) {
                                    1 -> 24.sp
                                    2 -> 20.sp
                                    3 -> 18.sp
                                    else -> 16.sp
                                },
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                
                is MarkdownBlock.Code -> {
                    CodeBlock(
                        code = block.content,
                        language = block.language,
                        onCopy = { /* Handled internally */ },
                        onInsert = { onCodeAction(block.content, block.language) }
                    )
                }
                
                is MarkdownBlock.ListBlock -> {
                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for ((index, item) in block.items.withIndex()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (block.ordered) "${index + 1}." else "•",
                                    style = JewelTheme.defaultTextStyle
                                )
                                SelectionContainer {
                                    Text(
                                        text = item,
                                        style = JewelTheme.defaultTextStyle
                                    )
                                }
                            }
                        }
                    }
                }
                
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF5C5C5C))
                        )
                        SelectionContainer {
                            Text(
                                text = block.content,
                                style = JewelTheme.defaultTextStyle.copy(
                                    color = LocalContentColor.current.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 代码块组件
 */
@Composable
fun CodeBlock(
    code: String,
    language: String,
    onCopy: () -> Unit,
    onInsert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3C3C3C), RoundedCornerShape(8.dp))
    ) {
        // 头部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B2B2B))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 语言标识
            Text(
                text = language.ifEmpty { "text" },
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = LocalContentColor.current.copy(alpha = 0.6f)
                )
            )
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        onCopy()
                        copied = true
                    }
                ) {
                    Text(
                        text = if (copied) "✓ 已复制" else "复制",
                        fontSize = 12.sp
                    )
                }
                
                OutlinedButton(
                    onClick = { onInsert() }
                ) {
                    Text(
                        text = "插入",
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // 代码内容
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
    
    // 重置复制状态
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }
}

/**
 * 简单的 Markdown 解析
 */
private sealed class MarkdownBlock {
    data class Text(val content: String) : MarkdownBlock()
    data class Header(val level: Int, val content: String) : MarkdownBlock()
    data class Code(val content: String, val language: String) : MarkdownBlock()
    data class ListBlock(val items: List<String>, val ordered: Boolean) : MarkdownBlock()
    data class Blockquote(val content: String) : MarkdownBlock()
}

private fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.lines()
    var i = 0
    
    while (i < lines.size) {
        val line = lines[i]
        
        when {
            // 代码块
            line.startsWith("```") -> {
                val language = line.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                blocks.add(MarkdownBlock.Code(codeLines.joinToString("\n"), language))
            }
            
            // 标题
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                val content = line.drop(level).trim()
                blocks.add(MarkdownBlock.Header(level, content))
            }
            
            // 引用
            line.startsWith(">") -> {
                val content = line.removePrefix(">").trim()
                blocks.add(MarkdownBlock.Blockquote(content))
            }
            
            // 无序列表
            line.trim().startsWith("- ") || line.trim().startsWith("* ") -> {
                val items = mutableListOf<String>()
                while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                    items.add(lines[i].trim().removePrefix("- ").removePrefix("* "))
                    i++
                }
                i--
                blocks.add(MarkdownBlock.ListBlock(items, ordered = false))
            }
            
            // 有序列表
            line.trim().matches(Regex("^\\d+\\.\\s.*")) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s.*"))) {
                    items.add(lines[i].trim().substringAfter(". "))
                    i++
                }
                i--
                blocks.add(MarkdownBlock.ListBlock(items, ordered = true))
            }
            
            // 普通文本
            line.isNotBlank() -> {
                blocks.add(MarkdownBlock.Text(line))
            }
        }
        
        i++
    }
    
    return blocks
}