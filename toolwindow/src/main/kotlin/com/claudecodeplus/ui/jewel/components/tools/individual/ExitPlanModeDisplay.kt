package com.claudecodeplus.ui.jewel.components.tools.individual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.sdk.types.ExitPlanModeToolUse
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolHeaderDisplay
import com.claudecodeplus.ui.jewel.components.tools.shared.ToolResultDisplay
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * ExitPlanMode工具专用展示组件
 *
 * 🎯 职责：专门处理ExitPlanMode工具的展示
 * 🔧 特点：显示计划内容、用户确认状态
 */
@Composable
fun ExitPlanModeDisplay(
    toolCall: ToolCall,
    exitPlanModeTool: ExitPlanModeToolUse,
    showDetails: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 工具头部信息
        ToolHeaderDisplay(
            icon = "📋",
            toolName = "ExitPlanMode",
            subtitle = "计划展示和用户确认",
            status = toolCall.status
        )

        // 显示计划内容
        if (showDetails) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 计划标题
                Text(
                    text = "📋 实施计划",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JewelTheme.globalColors.text.normal
                    )
                )

                // 计划内容
                SelectionContainer {
                    Text(
                        text = exitPlanModeTool.plan,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.9f)
                        ),
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            // 显示结果（用户是否批准）
            if (toolCall.result != null) {
                ToolResultDisplay(toolCall.result!!)
            }
        }
    }
}