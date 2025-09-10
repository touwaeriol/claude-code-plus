package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolResult
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Diff 结果展示组件
 * 展示文件编辑的差异
 */
@Composable
fun DiffResultDisplay(
    toolCall: ToolCall,
    modifier: Modifier = Modifier
) {
    val diffLines = parseDiffFromResult(toolCall)
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 文件路径和统计信息
        DiffHeader(toolCall, diffLines)
        
        // Diff 内容
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

/**
 * Diff 头部信息
 */
@Composable
private fun DiffHeader(
    toolCall: ToolCall,
    diffLines: List<DiffLine>
) {
    val filePath = toolCall.parameters["file_path"]?.toString() ?: ""
    val fileName = filePath.substringAfterLast('/').substringAfterLast('\\')
    
    val additions = diffLines.count { it.type == DiffLineType.ADDITION }
    val deletions = diffLines.count { it.type == DiffLineType.DELETION }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "📝 $fileName",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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

/**
 * 单行 Diff 展示
 */
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
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 行号
        if (line.lineNumber != null) {
            Text(
                text = line.lineNumber.toString().padStart(4),
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                ),
                modifier = Modifier.width(32.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(32.dp))
        }
        
        // 符号
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
            modifier = Modifier.width(12.dp)
        )
        
        // 内容
        Text(
            text = line.content,
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = JewelTheme.globalColors.text.normal.copy(
                    alpha = when (line.type) {
                        DiffLineType.DELETION -> 0.7f
                        else -> 1f
                    }
                )
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Diff 行类型
 */
private enum class DiffLineType {
    ADDITION,
    DELETION,
    CONTEXT
}

/**
 * Diff 行数据
 */
private data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val lineNumber: Int? = null
)

/**
 * 从工具结果中解析 Diff
 */
private fun parseDiffFromResult(toolCall: ToolCall): List<DiffLine> {
    val result = toolCall.result ?: return emptyList()
    
    // 从参数中获取原始和新内容
    val oldString = toolCall.parameters["old_string"]?.toString() ?: ""
    val newString = toolCall.parameters["new_string"]?.toString() ?: ""
    
    // 如果是成功的编辑操作，生成diff
    if (result is ToolResult.Success && (oldString.isNotEmpty() || newString.isNotEmpty())) {
        return generateDiff(oldString, newString)
    }
    
    // 如果是MultiEdit，解析多个编辑
    if (toolCall.name.contains("MultiEdit", ignoreCase = true)) {
        val edits = toolCall.parameters["edits"] as? List<*> ?: return emptyList()
        val allDiffs = mutableListOf<DiffLine>()
        
        edits.forEach { edit ->
            if (edit is Map<*, *>) {
                val old = edit["old_string"]?.toString() ?: ""
                val new = edit["new_string"]?.toString() ?: ""
                if (old.isNotEmpty() || new.isNotEmpty()) {
                    if (allDiffs.isNotEmpty()) {
                        allDiffs.add(DiffLine(DiffLineType.CONTEXT, "..."))
                    }
                    allDiffs.addAll(generateDiff(old, new))
                }
            }
        }
        
        return allDiffs
    }
    
    return emptyList()
}

/**
 * 生成简单的 Diff
 */
private fun generateDiff(oldText: String, newText: String): List<DiffLine> {
    val result = mutableListOf<DiffLine>()
    
    // 简单的逐行对比
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    
    // 显示删除的行
    oldLines.forEachIndexed { index, line ->
        if (line.isNotBlank()) {
            result.add(DiffLine(
                type = DiffLineType.DELETION,
                content = line,
                lineNumber = index + 1
            ))
        }
    }
    
    // 显示添加的行
    newLines.forEachIndexed { index, line ->
        if (line.isNotBlank()) {
            result.add(DiffLine(
                type = DiffLineType.ADDITION,
                content = line,
                lineNumber = index + 1
            ))
        }
    }
    
    return result
}