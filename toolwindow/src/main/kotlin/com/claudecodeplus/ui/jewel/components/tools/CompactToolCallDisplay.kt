@file:OptIn(ExperimentalFoundationApi::class)

package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolResult
import com.claudecodeplus.sdk.ToolType
import com.claudecodeplus.sdk.Tool
import com.claudecodeplus.sdk.ToolParser
import com.claudecodeplus.ui.jewel.components.tools.*
import com.claudecodeplus.ui.jewel.components.tools.output.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * 紧凑的工具调用显示组件
 * 默认单行显示，点击展开详情
 */
@Composable
fun CompactToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    println("[CompactToolCallDisplay] 工具调用数量：${toolCalls.size}")
    toolCalls.forEach { tool ->
        println("  - ${tool.name} (${tool.id}): ${tool.status}, result=${tool.result?.let { it::class.simpleName } ?: "null"}")
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        toolCalls.forEach { toolCall ->
            CompactToolCallItem(toolCall)
        }
    }
}

/**
 * 单个工具调用的紧凑显示
 */
@Composable
private fun CompactToolCallItem(
    toolCall: ToolCall
) {
    println("[CompactToolCallItem] 渲染工具：${toolCall.name}, ID：${toolCall.id}")
    
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // 背景色动画
    val backgroundColor by animateColorAsState(
        targetValue = when {
            expanded -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)
            isHovered -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f)
            else -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.1f)
        },
        animationSpec = tween(150)
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .hoverable(interactionSource)
    ) {
        // 紧凑的单行显示 - 包裹在 Tooltip 中
        Tooltip(
            tooltip = {
                // 悬浮时显示所有参数
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "参数：",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    if (toolCall.parameters.isEmpty()) {
                        Text(
                            "无参数",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                            )
                        )
                    } else {
                        toolCall.parameters.forEach { (key, value) ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "$key:",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = JewelTheme.globalColors.text.normal
                                    ),
                                    modifier = Modifier.widthIn(min = 80.dp)
                                )
                                Text(
                                    formatTooltipValue(value),
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 11.sp,
                                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.9f),
                                        fontFamily = if (key == "command") FontFamily.Monospace else FontFamily.Default
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧内容
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 工具图标
                Text(
                    text = getToolIcon(toolCall),
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
                )
                
                // 工具名称和参数（智能显示）
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val displayInfo = getToolDisplayInfo(toolCall)
                    
                    // 工具名称
                    Text(
                        text = toolCall.name,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.normal,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    )
                    
                    // 参数值
                    if (displayInfo.briefValue.isNotEmpty()) {
                        Text(
                            text = displayInfo.briefValue,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 13.sp,
                                color = JewelTheme.globalColors.text.normal
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // 完整路径（如果有）
                    if (displayInfo.fullPath.isNotEmpty() && displayInfo.fullPath != displayInfo.briefValue) {
                        Text(
                            text = displayInfo.fullPath,
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 11.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            // 右侧状态和时间
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 执行时间（如果已完成）
                if (toolCall.status != ToolCallStatus.PENDING && toolCall.endTime != null) {
                    val duration = toolCall.endTime - toolCall.startTime
                    Text(
                        text = formatDuration(duration),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                        )
                    )
                }
                
                // 状态指示器
                ToolStatusIndicator(
                    status = when (toolCall.status) {
                        ToolCallStatus.PENDING -> ToolExecutionStatus.PENDING
                        ToolCallStatus.RUNNING -> ToolExecutionStatus.RUNNING
                        ToolCallStatus.SUCCESS -> ToolExecutionStatus.SUCCESS
                        ToolCallStatus.FAILED -> ToolExecutionStatus.ERROR
                        ToolCallStatus.CANCELLED -> ToolExecutionStatus.ERROR
                    },
                    size = 14.dp
                )
                
                // 展开/折叠图标
                Text(
                    text = if (expanded) "▼" else "▶",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                    )
                )
            }
        }
        }
        
        // 展开的详细内容
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
        ) {
            ToolCallDetails(
                toolCall = toolCall,
                onClose = { expanded = false }
            )
        }
    }
}

/**
 * 工具调用的详细信息
 */
@Composable
private fun ToolCallDetails(
    toolCall: ToolCall,
    onClose: () -> Unit = {}
) {
    println("[ToolCallDetails] 工具：${toolCall.name}, 结果：${toolCall.result?.let { it::class.simpleName } ?: "null"}")
    
    // 判断是否需要显示详细结果
    val shouldShowDetails = shouldShowToolDetails(toolCall)
    
    println("[ToolCallDetails] shouldShowDetails for ${toolCall.name} = $shouldShowDetails")
    
    if (!shouldShowDetails) {
        // 对于不需要显示详细结果的工具，不渲染任何内容
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
    ) {
        // 固定的顶部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 工具名称和图标
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getToolIcon(toolCall),
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 14.sp)
                )
                Text(
                    text = toolCall.name,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            
            // 关闭按钮
            Text(
                text = "✕",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 14.sp,
                    color = JewelTheme.globalColors.text.normal
                ),
                modifier = Modifier
                    .clickable { onClose() }
                    .padding(4.dp)
            )
        }
        
        // 详细内容 - 直接显示结果，无需额外标题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 直接显示结果
            toolCall.result?.let { result ->
                formatToolResult(toolCall)
            }
        }
    }
}

/**
 * 判断是否需要显示工具的详细结果
 */
private fun shouldShowToolDetails(toolCall: ToolCall): Boolean {
    // 所有工具都显示结果
    return true
}

/**
 * 获取工具图标
 */
private fun getToolIcon(toolCall: ToolCall): String {
    // 优先使用新的 Tool 对象
    return toolCall.tool?.icon ?: run {
        // 回退到旧的 ToolType 系统
        val toolType = ToolType.fromName(toolCall.name)
        ToolType.getIcon(toolType)
    }
}

/**
 * 工具显示信息
 */
private data class ToolDisplayInfo(
    val briefValue: String = "",
    val fullPath: String = ""
)

/**
 * 获取工具的显示信息
 */
private fun getToolDisplayInfo(toolCall: ToolCall): ToolDisplayInfo {
    // 对于单参数工具
    if (isSingleParamTool(toolCall.name)) {
        val paramValue = getPrimaryParamValue(toolCall)
        if (paramValue != null) {
            return when {
                // 文件路径类工具
                toolCall.name.contains("Read", ignoreCase = true) ||
                toolCall.name.contains("Write", ignoreCase = true) ||
                toolCall.name.contains("LS", ignoreCase = true) -> {
                    val fileName = paramValue.substringAfterLast('/').substringAfterLast('\\')
                    ToolDisplayInfo(
                        briefValue = fileName,
                        fullPath = paramValue
                    )
                }
                // URL类工具
                toolCall.name.contains("Web", ignoreCase = true) -> {
                    val domain = paramValue
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore("/")
                    ToolDisplayInfo(
                        briefValue = domain,
                        fullPath = paramValue
                    )
                }
                // 其他单参数工具
                else -> ToolDisplayInfo(
                    briefValue = if (paramValue.length > 40) {
                        paramValue.take(37) + "..."
                    } else {
                        paramValue
                    }
                )
            }
        }
    }
    
    // 对于多参数工具，使用摘要格式
    val briefInfo = formatToolBriefInfo(toolCall)
    return ToolDisplayInfo(briefValue = briefInfo)
}

/**
 * 格式化参数显示
 */
private fun formatParameters(parameters: Map<String, Any>): String {
    return parameters.entries.joinToString("\n") { (key, value) ->
        "$key: ${formatValue(value)}"
    }
}

private fun formatValue(value: Any): String {
    return when (value) {
        is String -> if (value.length > 80) "\"${value.take(80)}...\"" else "\"$value\""
        is List<*> -> "[${value.size} items]"
        is Map<*, *> -> "{${value.size} entries}"
        else -> value.toString()
    }
}

/**
 * 格式化时间长度
 */
private fun formatDuration(millis: Long): String {
    return when {
        millis < 1000 -> "${millis}ms"
        millis < 60000 -> "${millis / 1000}.${(millis % 1000) / 100}s"
        else -> "${millis / 60000}m ${(millis % 60000) / 1000}s"
    }
}

/**
 * 格式化字节数
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

/**
 * 根据工具类型格式化结果展示
 */
@Composable
private fun formatToolResult(toolCall: ToolCall) {
    println("[formatToolResult] 格式化工具结果：${toolCall.name}, 有结果：${toolCall.result != null}")
    
    when {
        // Edit/MultiEdit 使用 Diff 展示
        toolCall.name.contains("Edit", ignoreCase = true) -> {
            println("[formatToolResult] 使用 DiffResultDisplay")
            DiffResultDisplay(toolCall)
        }
        
        // Read/Write 使用内容预览
        toolCall.name.contains("Read", ignoreCase = true) ||
        toolCall.name.contains("Write", ignoreCase = true) -> {
            println("[formatToolResult] 使用 FileContentPreview")
            FileContentPreview(toolCall)
        }
        
        // LS 使用文件列表展示
        toolCall.name.contains("LS", ignoreCase = true) -> {
            println("[formatToolResult] 使用 FileListDisplay")
            FileListDisplay(toolCall)
        }
        
        // Bash 命令使用命令结果展示
        toolCall.name.contains("Bash", ignoreCase = true) -> {
            CommandResultDisplay(toolCall)
        }
        
        // TodoWrite 使用看板展示
        toolCall.name.contains("TodoWrite", ignoreCase = true) -> {
            EnhancedTodoDisplay(toolCall)
        }
        
        // 其他工具使用默认展示
        else -> {
            DefaultResultDisplay(toolCall)
        }
    }
}

/**
 * 通用的工具结果显示组件
 * @param toolCall 工具调用信息
 * @param limitHeight 是否限制高度
 * @param maxHeight 最大高度（仅在 limitHeight = true 时生效）
 */
@Composable
private fun ToolResultContent(
    toolCall: ToolCall,
    limitHeight: Boolean = false,
    maxHeight: Dp = 200.dp
) {
    val result = toolCall.result ?: return
    
    if (result is ToolResult.Success) {
        val modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
            .padding(8.dp)
            .then(
                if (limitHeight) {
                    Modifier.heightIn(max = maxHeight)
                } else {
                    Modifier
                }
            )
        
        Box(modifier = modifier) {
            val scrollState = if (limitHeight) rememberScrollState() else null
            
            Text(
                text = result.output,
                style = JewelTheme.defaultTextStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                ),
                modifier = if (scrollState != null) {
                    Modifier.verticalScroll(scrollState)
                } else {
                    Modifier
                }
            )
        }
    } else if (result is ToolResult.Failure) {
        Text(
            text = "❌ ${result.error}",
            style = JewelTheme.defaultTextStyle.copy(
                fontSize = 12.sp,
                color = Color(0xFFFF6B6B)
            )
        )
    }
}

/**
 * 文件内容预览
 */
@Composable
private fun FileContentPreview(toolCall: ToolCall) {
    // 使用通用组件，不限制高度
    ToolResultContent(
        toolCall = toolCall,
        limitHeight = false
    )
}

/**
 * 文件列表展示
 */
@Composable
private fun FileListDisplay(toolCall: ToolCall) {
    println("[FileListDisplay] 显示LS结果")
    
    // 使用通用组件，不限制高度
    ToolResultContent(
        toolCall = toolCall,
        limitHeight = false
    )
}

/**
 * 命令结果展示 - 使用 ANSI 终端显示
 */
@Composable
private fun CommandResultDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    // 执行结果
    when (result) {
        is ToolResult.Success -> {
            val output = result.output
            
            // 直接使用 ANSI 终端显示输出 - 增加到30行以显示更多内容
            AnsiOutputView(
                text = output,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 30,  // 从默认的10行增加到30行
                onCopy = { copiedText ->
                    // TODO: 实现复制到剪贴板
                }
            )
        }
        is ToolResult.Failure -> {
            Text(
                text = "❌ ${result.error}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B6B)
                )
            )
        }
        else -> {}
    }
}

/**
 * 默认结果展示
 */
@Composable
private fun DefaultResultDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    when (result) {
        is ToolResult.Success -> {
            // 根据工具类型决定是否限制高度
            val shouldLimitHeight = shouldLimitToolHeight(toolCall)
            ToolResultContent(
                toolCall = toolCall,
                limitHeight = shouldLimitHeight,
                maxHeight = 200.dp
            )
        }
        is ToolResult.Failure -> {
            Text(
                text = "❌ ${result.error}",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 12.sp,
                    color = Color(0xFFFF6B6B)
                )
            )
        }
        is ToolResult.FileSearchResult -> {
            Text(
                text = "📁 找到 ${result.files.size} 个文件 (总计 ${result.totalCount})",
                style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
            )
        }
        else -> {}
    }
}

/**
 * 判断工具是否应该限制高度
 */
private fun shouldLimitToolHeight(toolCall: ToolCall): Boolean {
    // 优先使用新的 Tool 对象
    return toolCall.tool?.shouldLimitHeight() ?: run {
        // 回退到旧的 ToolType 系统
        val toolType = ToolType.fromName(toolCall.name)
        ToolType.shouldLimitHeight(toolType)
    }
}

/**
 * 格式化 Tooltip 中的参数值显示
 */
private fun formatTooltipValue(value: Any): String {
    return when (value) {
        is String -> {
            when {
                value.length > 200 -> value.take(197) + "..."
                else -> value
            }
        }
        is List<*> -> "[${value.size} items]"
        is Map<*, *> -> "{${value.size} entries}"
        else -> value.toString()
    }
}