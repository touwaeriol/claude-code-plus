package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.DefaultButton

/**
 * Read/Write 工具的文件内容预览组件
 */
@Composable
fun FileContentPreview(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    val result = toolCall.result
    var showFullContent by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 文件信息
        FileInfoHeader(toolCall, result)
        
        // 内容预览
        when (result) {
            is ToolResult.FileReadResult -> {
                ContentPreviewBox(
                    content = result.content,
                    language = result.language,
                    showFullContent = showFullContent,
                    onToggleFullContent = { showFullContent = !showFullContent }
                )
            }
            is ToolResult.Success -> {
                // Write 操作的内容预览
                if (toolCall.name.contains("Write", ignoreCase = true)) {
                    val content = toolCall.parameters["content"]?.toString() ?: result.output
                    ContentPreviewBox(
                        content = content,
                        language = detectLanguage(toolCall),
                        showFullContent = showFullContent,
                        onToggleFullContent = { showFullContent = !showFullContent }
                    )
                }
            }
            else -> {}
        }
    }
}

/**
 * 文件信息头部
 */
@Composable
private fun FileInfoHeader(
    toolCall: ToolCall,
    result: ToolResult?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 文件名和图标
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getFileIcon(toolCall),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
            )
            Text(
                text = getFileName(toolCall),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
        }
        
        // 文件统计信息
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (result) {
                is ToolResult.FileReadResult -> {
                    Text(
                        text = "${result.lineCount} 行",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
                    result.language?.let { lang ->
                        Text(
                            text = lang,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
                is ToolResult.Success -> {
                    // Write 操作显示写入的行数
                    val content = toolCall.parameters["content"]?.toString() ?: ""
                    val lineCount = content.lines().size
                    Text(
                        text = "写入 $lineCount 行",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                        )
                    )
                }
                else -> {}
            }
        }
    }
}

/**
 * 内容预览框
 */
@Composable
private fun ContentPreviewBox(
    content: String,
    language: String?,
    showFullContent: Boolean,
    onToggleFullContent: () -> Unit
) {
    val lines = content.lines()
    val displayLines = if (showFullContent) lines else lines.take(10)
    val hasMore = lines.size > 10
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 内容区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = if (showFullContent) 400.dp else 200.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (showFullContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .horizontalScroll(rememberScrollState())
            ) {
                displayLines.forEachIndexed { index, line ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 行号
                        Text(
                            text = String.format("%4d", index + 1),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.4f)
                            )
                        )
                        
                        // 分隔符
                        Text(
                            text = "│",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.3f)
                            )
                        )
                        
                        // 内容
                        Text(
                            text = line.ifEmpty { " " }, // 空行显示一个空格以保持高度
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
                
                // 如果有更多内容但未展开，显示省略提示
                if (!showFullContent && hasMore) {
                    Text(
                        text = "    ...（还有 ${lines.size - 10} 行）",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        
        // 展开/收起按钮
        if (hasMore) {
            DefaultButton(
                onClick = onToggleFullContent,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
            ) {
                Text(
                    text = if (showFullContent) "收起" else "查看全部",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * 获取文件图标
 */
private fun getFileIcon(toolCall: ToolCall): String {
    return when {
        toolCall.name.contains("Read", ignoreCase = true) -> "📖"
        toolCall.name.contains("Write", ignoreCase = true) -> "✏️"
        else -> "📄"
    }
}

/**
 * 获取文件名
 */
private fun getFileName(toolCall: ToolCall): String {
    val filePath = toolCall.parameters["file_path"]?.toString() ?: ""
    return filePath.substringAfterLast('/').substringAfterLast('\\')
}

/**
 * 检测语言类型
 */
private fun detectLanguage(toolCall: ToolCall): String? {
    val filePath = toolCall.parameters["file_path"]?.toString() ?: ""
    val extension = filePath.substringAfterLast('.').lowercase()
    
    return when (extension) {
        "kt", "kts" -> "Kotlin"
        "java" -> "Java"
        "js" -> "JavaScript"
        "ts", "tsx" -> "TypeScript"
        "py" -> "Python"
        "cpp", "cc", "cxx" -> "C++"
        "c" -> "C"
        "cs" -> "C#"
        "rb" -> "Ruby"
        "go" -> "Go"
        "rs" -> "Rust"
        "swift" -> "Swift"
        "php" -> "PHP"
        "html", "htm" -> "HTML"
        "css" -> "CSS"
        "scss", "sass" -> "SCSS"
        "xml" -> "XML"
        "json" -> "JSON"
        "yaml", "yml" -> "YAML"
        "md" -> "Markdown"
        "sh", "bash" -> "Shell"
        "sql" -> "SQL"
        "gradle" -> "Gradle"
        "properties" -> "Properties"
        else -> null
    }
}