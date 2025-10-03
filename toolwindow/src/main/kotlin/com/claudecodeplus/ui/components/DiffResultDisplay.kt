package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.ui.viewmodels.tool.EditToolDetail
import com.claudecodeplus.ui.viewmodels.tool.MultiEditToolDetail
import com.claudecodeplus.ui.viewmodels.tool.MultiEditToolDetail.EditOperationVm
import com.claudecodeplus.ui.viewmodels.tool.WriteToolDetail
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DiffResultDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    val diffLines = parseDiffFromDetail(toolCall)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DiffHeader(toolCall, diffLines)

        if (diffLines.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    diffLines.forEach { line ->
                        DiffLine(line)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffHeader(
    toolCall: ToolCall,
    diffLines: List<DiffLine>
) {
    val filePath = resolveFilePath(toolCall)
    val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')

    val additions = diffLines.count { it.type == DiffLineType.ADDITION }
    val deletions = diffLines.count { it.type == DiffLineType.DELETION }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "📑 $fileName",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (additions > 0) {
                Text(
                    text = "+$additions",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50)
                    )
                )
            }
            if (deletions > 0) {
                Text(
                    text = "-$deletions",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = Color(0xFFFF5252)
                    )
                )
            }
        }
    }
}

@Composable
private fun DiffLine(line: DiffLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(
                when (line.type) {
                    DiffLineType.ADDITION -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                    DiffLineType.DELETION -> Color(0xFFFF5252).copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = when (line.type) {
                DiffLineType.ADDITION -> "+"
                DiffLineType.DELETION -> "-"
                else -> " "
            },
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = when (line.type) {
                    DiffLineType.ADDITION -> Color(0xFF4CAF50)
                    DiffLineType.DELETION -> Color(0xFFFF5252)
                    else -> JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier.padding(end = 4.dp)
        )

        Text(
            text = line.content,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = JewelTheme.globalColors.text.normal.copy(
                    alpha = if (line.type == DiffLineType.DELETION) 0.7f else 1f
                )
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

private enum class DiffLineType {
    ADDITION,
    DELETION,
    CONTEXT
}

private data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val lineNumber: Int? = null
)

private fun parseDiffFromDetail(toolCall: ToolCall): List<DiffLine> {
    val detail = toolCall.viewModel?.toolDetail
    val result = toolCall.result

    val detailDiff = when (detail) {
        is EditToolDetail -> {
            val oldText = detail.oldString.orEmpty()
            val newText = detail.newString.orEmpty()
            if (oldText.isNotEmpty() || newText.isNotEmpty()) {
                generateDiff(oldText, newText)
            } else {
                emptyList()
            }
        }
        is MultiEditToolDetail -> buildList {
            detail.edits.forEach { edit ->
                appendDiffFromEdit(edit, this)
            }
        }
        is WriteToolDetail -> {
            val newText = detail.content.orEmpty()
            if (newText.isNotEmpty()) {
                generateDiff("", newText)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    if (detailDiff.isNotEmpty()) {
        return detailDiff
    }

    if (result is ToolResult.FileEditResult) {
        return generateDiff(result.oldContent, result.newContent)
    }

    return emptyList()
}

private fun appendDiffFromEdit(edit: EditOperationVm, accumulator: MutableList<DiffLine>) {
    val oldText = edit.oldString ?: ""
    val newText = edit.newString ?: ""
    if (oldText.isEmpty() && newText.isEmpty()) {
        return
    }

    if (accumulator.isNotEmpty()) {
        accumulator.add(DiffLine(DiffLineType.CONTEXT, "..."))
    }
    accumulator.addAll(generateDiff(oldText, newText))
}

private fun resolveFilePath(toolCall: ToolCall): String {
    val detail = toolCall.viewModel?.toolDetail
    val path = when (detail) {
        is EditToolDetail -> detail.filePath
        is MultiEditToolDetail -> detail.filePath
        is WriteToolDetail -> detail.filePath
        else -> null
    }
    return path.orEmpty()
}

private fun generateDiff(oldText: String, newText: String): List<DiffLine> {
    val result = mutableListOf<DiffLine>()

    val oldLines = oldText.lines()
    val newLines = newText.lines()

    oldLines.forEachIndexed { index, line ->
        if (line.isNotBlank()) {
            result.add(
                DiffLine(
                    type = DiffLineType.DELETION,
                    content = line,
                    lineNumber = index + 1
                )
            )
        }
    }

    newLines.forEachIndexed { index, line ->
        if (line.isNotBlank()) {
            result.add(
                DiffLine(
                    type = DiffLineType.ADDITION,
                    content = line,
                    lineNumber = index + 1
                )
            )
        }
    }

    return result
}
