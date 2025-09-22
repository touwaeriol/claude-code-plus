package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.types.MultiEditToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * MultiEdit 工具专用展示组件
 */
@Composable
fun MultiEditToolDisplay(
    toolCall: ToolCall,
    multiEditTool: MultiEditToolUse,
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            val fileName = multiEditTool.filePath.substringAfterLast('/')
                .substringAfterLast('\\')
            ToolHeaderDisplay(
                icon = "MULTI",
                toolName = "MultiEdit",
                subtitle = "$fileName · ${multiEditTool.edits.size} 处修改",
                status = toolCall.status,
                onHeaderClick = onFileClick
            )
        }

        if (showDetails) {
            Text(
                text = multiEditTool.filePath,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                )
            )

            multiEditTool.edits.forEachIndexed { index, edit ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "修改 #${index + 1}${if (edit.replaceAll) " (全部替换)" else ""}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "旧内容:",
                                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                            )
                            Text(
                                text = edit.oldString,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                                )
                            )
                            Text(
                                text = "新内容:",
                                style = JewelTheme.defaultTextStyle.copy(fontSize = 11.sp)
                            )
                            Text(
                                text = edit.newString,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            toolCall.result?.let { result ->
                ToolResultDisplay(result)
            }
        }
    }
}
