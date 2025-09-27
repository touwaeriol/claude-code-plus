@file:OptIn(ExperimentalFoundationApi::class, org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.sdk.types.TaskToolUse
import com.claudecodeplus.sdk.types.TodoWriteToolUse
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.services.StringResources
import com.claudecodeplus.ui.services.formatStringResource
import com.claudecodeplus.ui.services.stringResource
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 紧凑的工具调用显示（最小实现，先恢复编译与基本功能）
 */
@Composable
fun CompactToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,
    expandedTools: Map<String, Boolean?> = emptyMap(),
    onExpandedChange: ((String, Boolean) -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        toolCalls.forEach { toolCall ->
            CompactToolCallRow(
                toolCall = toolCall,
                ideIntegration = ideIntegration,
                isExpanded = expandedTools[toolCall.id] ?: defaultExpanded(toolCall),
                onExpandedChange = onExpandedChange
            )
        }
    }
}

@Composable
private fun CompactToolCallRow(
    toolCall: ToolCall,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration?,
    isExpanded: Boolean,
    onExpandedChange: ((String, Boolean) -> Unit)?
) {
    var expanded by remember(toolCall.id) { mutableStateOf(isExpanded) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.08f))
            .clickable {
                // 先尝试 IDE 集成
                val handled = ideIntegration?.handleToolClick(toolCall) ?: false
                if (!handled) {
                    expanded = !expanded
                    onExpandedChange?.invoke(toolCall.id, expanded)
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = if (toolCall.specificTool is TodoWriteToolUse) {
                    // 本地化 TodoWrite 名称
                    stringResource("tool_todowrite")
                } else toolCall.name

                Text(
                    text = title,
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                )

                // 子标题：对 TodoWrite 显示进度，其他工具保留参数摘要
                when (val st = toolCall.specificTool) {
                    is TodoWriteToolUse -> {
                        val total = st.todos.size
                        val completed = st.todos.count { it.status.equals("completed", ignoreCase = true) }
                        Text(
                            text = formatStringResource(StringResources.TASK_COMPLETED_COUNT, completed, total),
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 10.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                            )
                        )
                    }
                    else -> {
                        toolCall.parameters.takeIf { it.isNotEmpty() }?.let {
                            val summary = it.entries
                                .joinToString(" ") { e -> "${e.key}=${e.value}" }
                                .take(160)
                            Text(
                                text = summary,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }
            Text(
                text = statusLabel(toolCall.status, expanded),
                style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                TypedToolCallDisplay(
                    toolCall = toolCall,
                    showDetails = true,
                    ideIntegration = ideIntegration
                )
            }
        }
    }
}

private fun statusLabel(status: ToolCallStatus, expanded: Boolean): String {
    val statusText = when (status) {
        ToolCallStatus.PENDING -> stringResource(StringResources.TOOL_STATUS_PENDING)
        ToolCallStatus.RUNNING -> stringResource(StringResources.TOOL_STATUS_RUNNING)
        ToolCallStatus.SUCCESS -> stringResource(StringResources.TOOL_STATUS_SUCCESS)
        ToolCallStatus.FAILED -> stringResource(StringResources.TOOL_STATUS_FAILED)
        ToolCallStatus.CANCELLED -> stringResource(StringResources.TOOL_STATUS_CANCELLED)
    }
    val expandText = if (expanded) stringResource(StringResources.UI_EXPANDED)
    else stringResource(StringResources.UI_COLLAPSED)
    return "$statusText/$expandText"
}

private fun defaultExpanded(toolCall: ToolCall): Boolean = when (toolCall.specificTool) {
    is TodoWriteToolUse, is TaskToolUse -> true
    else -> false
}
