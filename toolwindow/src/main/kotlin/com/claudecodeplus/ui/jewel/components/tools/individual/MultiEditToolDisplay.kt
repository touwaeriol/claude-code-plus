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
import com.claudecodeplus.ui.viewmodels.tool.MultiEditToolDetail
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
    showDetails: Boolean = true,
    onFileClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 从 ViewModel 获取工具详情
    val toolDetail = toolCall.viewModel?.toolDetail as? MultiEditToolDetail
    if (toolDetail == null) {
        Text("错误：无法获取 MultiEdit 工具详情")
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!showDetails) {
            val fileName = toolDetail.filePath.substringAfterLast('/')
                .substringAfterLast('\\')
            ToolHeaderDisplay(
                icon = "MULTI",
                toolName = "MultiEdit",
                subtitle = "$fileName - ${toolDetail.edits.size} 处修改",
                status = toolCall.status,
                onHeaderClick = onFileClick
            )
        }

        if (showDetails) {
            Text(
                text = toolDetail.filePath,
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                )
            )

            toolDetail.edits.forEachIndexed { index, edit ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "�޸� #${index + 1}${if (edit.replaceAll) " (ȫ���滻)" else ""}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "������:",
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
                                text = "������:",
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
