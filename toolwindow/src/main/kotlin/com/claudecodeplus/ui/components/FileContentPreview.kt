package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.MultiEditToolDetail
import com.claudecodeplus.ui.viewmodels.tool.ReadToolDetail
import com.claudecodeplus.ui.viewmodels.tool.ToolDetailViewModel
import com.claudecodeplus.ui.viewmodels.tool.UiToolType
import com.claudecodeplus.ui.viewmodels.tool.WriteToolDetail
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text

/**
 * Read/Write 工具的文件内容预览。
 */
@Composable
fun FileContentPreview(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    val detail = toolCall.viewModel?.toolDetail
    val result = toolCall.result
    var showFullContent by remember { mutableStateOf(false) }

    val filePath = resolveFilePath(detail)
    val readResult = result as? ToolResult.FileReadResult
    val writeDetail = detail as? WriteToolDetail

    val previewContent = when {
        readResult != null -> readResult.content
        writeDetail != null -> writeDetail.content.orEmpty()
        result is ToolResult.Success -> result.output
        else -> ""
    }

    val previewLanguage = readResult?.language ?: filePath?.let(::detectLanguage)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FileInfoHeader(detail, result)

        if (previewContent.isNotEmpty()) {
            ContentPreviewBox(
                content = previewContent,
                language = previewLanguage,
                showFullContent = showFullContent,
                onToggleFullContent = { showFullContent = !showFullContent }
            )
        }
    }
}

@Composable
private fun FileInfoHeader(
    detail: ToolDetailViewModel?,
    result: ToolResult?
) {
    val filePath = resolveFilePath(detail)
    val fileName = filePath?.substringAfterLast('/')?.substringAfterLast('\\') ?: "未指定文件"
    val writeDetail = detail as? WriteToolDetail

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getFileIcon(detail),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
            )
            Text(
                text = fileName,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
        }

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
                else -> {
                    val lineCount = writeDetail?.content?.lines()?.size ?: 0
                    if (lineCount > 0) {
                        Text(
                            text = "写入 $lineCount 行",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    }
}

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
                        Text(
                            text = String.format("%4d", index + 1),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.4f)
                            )
                        )
                        Text(
                            text = "┃",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = line.ifEmpty { " " },
                            style = JewelTheme.defaultTextStyle.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                            )
                        )
                    }
                }

                if (!showFullContent && hasMore) {
                    Text(
                        text = "    ...（还有${lines.size - 10} 行）",
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

        if (hasMore) {
            DefaultButton(
                onClick = onToggleFullContent,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            ) {
                Text(
                    text = if (showFullContent) "收起" else "查看全部",
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                )
            }
        }
    }
}

private fun getFileIcon(detail: ToolDetailViewModel?): String = when (detail?.toolType) {
    UiToolType.READ -> "📄"
    UiToolType.WRITE -> "✍️"
    UiToolType.EDIT, UiToolType.MULTI_EDIT -> "📝"
    UiToolType.GLOB, UiToolType.GREP -> "🔍"
    UiToolType.BASH -> "💻"
    UiToolType.BASH_OUTPUT -> "🖨️"
    UiToolType.NOTEBOOK_EDIT -> "📓"
    else -> "📦"
}

private fun resolveFilePath(detail: ToolDetailViewModel?): String? = when (detail) {
    is ReadToolDetail -> detail.filePath
    is WriteToolDetail -> detail.filePath
    is MultiEditToolDetail -> detail.filePath
    else -> null
}

private fun detectLanguage(filePath: String): String? {
    val extension = filePath.substringAfterLast('.', missingDelimiterValue = "").lowercase()

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
