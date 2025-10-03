@file:OptIn(ExperimentalFoundationApi::class, org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.services.StringResources
import com.claudecodeplus.ui.services.stringResource
import com.claudecodeplus.ui.jewel.components.tools.TypedToolCallDisplay
import com.claudecodeplus.ui.viewmodels.tool.UiToolType
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

/**
 * 工具调用的紧凑展示列表。
 *
 * - 未展开时保持单行高度，突出工具身份与关键信息。
 * - 展开后在限定高度内滚动查看完整结果。
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
                initialExpanded = expandedTools[toolCall.id] ?: defaultExpanded(toolCall),
                onExpandedChange = onExpandedChange
            )
        }
    }
}

@Composable
private fun CompactToolCallRow(
    toolCall: ToolCall,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration?,
    initialExpanded: Boolean,
    onExpandedChange: ((String, Boolean) -> Unit)?
) {
    var expanded by remember(toolCall.id) { mutableStateOf(initialExpanded) }
    LaunchedEffect(initialExpanded) {
        expanded = initialExpanded
    }

    val visual = toolVisual(toolCall)
    val viewModel = toolCall.viewModel
    val summary = viewModel?.compactSummary
    val toolType = viewModel?.toolDetail?.toolType
    val shouldLimitHeight = when (toolType) {
        UiToolType.TODO_WRITE, UiToolType.TASK -> false
        else -> true
    }
    val toolName = when (viewModel?.toolDetail) {
        is com.claudecodeplus.ui.viewmodels.tool.TodoWriteToolDetail -> stringResource("tool_todowrite")
        else -> toolCall.displayName
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = JewelTheme.globalColors.borders.focused.copy(alpha = 0.18f),
                shape = RoundedCornerShape(8.dp)
            )
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .clickable {
                    handleToolClick(toolCall, ideIntegration) {
                        val newExpanded = !expanded
                        expanded = newExpanded
                        onExpandedChange?.invoke(toolCall.id, newExpanded)
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 24.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(visual.accent)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = visual.icon,
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSummary = !summary.isNullOrBlank()
                val nameModifier = if (hasSummary) Modifier else Modifier.weight(1f)

                Text(
                    text = toolName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = nameModifier
                )

                if (hasSummary) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = summary!!,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.75f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            ToolStatusBadge(status = toolCall.status)

            Spacer(modifier = Modifier.width(6.dp))

            ExpandChevron(expanded = expanded)
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(6.dp))

            val containerModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.06f))

            val innerContent: @Composable () -> Unit = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TypedToolCallDisplay(
                        toolCall = toolCall,
                        showDetails = true,
                        ideIntegration = ideIntegration
                    )
                }
            }

            if (shouldLimitHeight) {
                val scrollState = rememberScrollState()
                VerticallyScrollableContainer(
                    scrollState = scrollState,
                    modifier = containerModifier.heightIn(max = 280.dp)
                ) {
                    innerContent()
                }
            } else {
                Column(
                    modifier = containerModifier,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    innerContent()
                }
            }
        }
    }
}

@Composable
private fun ToolStatusBadge(status: ToolCallStatus) {
    val chipColors = toolStatusChipColors(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(chipColors.background)
    ) {
        Text(
            text = statusLabel(status),
            color = chipColors.content,
            style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ExpandChevron(expanded: Boolean) {
    Text(
        text = if (expanded) "▴" else "▾",
        style = JewelTheme.defaultTextStyle.copy(
            fontSize = 11.sp,
            color = JewelTheme.globalColors.text.disabled
        )
    )
}

private data class ToolVisual(val icon: String, val accent: Color)

@Composable
private fun toolVisual(toolCall: ToolCall): ToolVisual {
    val toolType = toolCall.viewModel?.toolDetail?.toolType
    return when (toolType) {
        UiToolType.READ -> ToolVisual("📖", Color(0xFF6A9AE5))
        UiToolType.WRITE -> ToolVisual("📝", Color(0xFF6A9AE5))
        UiToolType.EDIT -> ToolVisual("✏️", Color(0xFF9B6EF3))
        UiToolType.MULTI_EDIT -> ToolVisual("🧰", Color(0xFF9B6EF3))
        UiToolType.NOTEBOOK_EDIT -> ToolVisual("📒", Color(0xFF9B6EF3))
        UiToolType.BASH, UiToolType.BASH_OUTPUT -> ToolVisual("💻", Color(0xFF4DB6AC))
        UiToolType.KILL_SHELL -> ToolVisual("⛔", Color(0xFFE57373))
        UiToolType.GLOB, UiToolType.GREP -> ToolVisual("🔍", Color(0xFF4DD0E1))
        UiToolType.TODO_WRITE -> ToolVisual("✅", Color(0xFF66BB6A))
        UiToolType.TASK -> ToolVisual("🗂", Color(0xFFFFB74D))
        UiToolType.WEB_FETCH, UiToolType.WEB_SEARCH -> ToolVisual("🌐", Color(0xFF64B5F6))
        UiToolType.MCP, UiToolType.LIST_MCP_RESOURCES, UiToolType.READ_MCP_RESOURCE -> ToolVisual("🧩", Color(0xFF26C6DA))
        UiToolType.EXIT_PLAN_MODE -> ToolVisual("🛑", Color(0xFFEF5350))
        UiToolType.SLASH_COMMAND -> ToolVisual("⌨️", Color(0xFFA1887F))
        else -> ToolVisual("🛠", Color(0xFF8A8D97))
    }
}

private fun statusLabel(status: ToolCallStatus): String = when (status) {
    ToolCallStatus.PENDING -> stringResource(StringResources.TOOL_STATUS_PENDING)
    ToolCallStatus.RUNNING -> stringResource(StringResources.TOOL_STATUS_RUNNING)
    ToolCallStatus.SUCCESS -> stringResource(StringResources.TOOL_STATUS_SUCCESS)
    ToolCallStatus.FAILED -> stringResource(StringResources.TOOL_STATUS_FAILED)
    ToolCallStatus.CANCELLED -> stringResource(StringResources.TOOL_STATUS_CANCELLED)
}

private fun defaultExpanded(toolCall: ToolCall): Boolean = when (toolCall.viewModel?.toolDetail) {
    is com.claudecodeplus.ui.viewmodels.tool.TodoWriteToolDetail,
    is com.claudecodeplus.ui.viewmodels.tool.TaskToolDetail -> true
    else -> false
}

private fun handleToolClick(
    toolCall: ToolCall,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration?,
    onToggleExpand: () -> Unit
) {
    if (toolCall.status == ToolCallStatus.RUNNING) {
        return
    }

    if (toolCall.viewModel?.shouldUseIdeIntegration() == true) {
        ideIntegration?.handleToolClick(toolCall)
        return
    }

    onToggleExpand()
}

private data class StatusChipColors(val background: Color, val content: Color)

@Composable
private fun toolStatusChipColors(status: ToolCallStatus): StatusChipColors = when (status) {
    ToolCallStatus.SUCCESS -> StatusChipColors(
        background = Color(0x332E7D32),
        content = Color(0xFF2E7D32)
    )
    ToolCallStatus.RUNNING -> StatusChipColors(
        background = Color(0x332196F3),
        content = Color(0xFF1976D2)
    )
    ToolCallStatus.FAILED -> StatusChipColors(
        background = Color(0x33E53935),
        content = Color(0xFFD32F2F)
    )
    ToolCallStatus.CANCELLED -> StatusChipColors(
        background = Color(0x33B0BEC5),
        content = Color(0xFF546E7A)
    )
    ToolCallStatus.PENDING -> StatusChipColors(
        background = Color(0x33FFB300),
        content = Color(0xFFFB8C00)
    )
}
