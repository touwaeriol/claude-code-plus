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
import androidx.compose.foundation.text.selection.SelectionContainer
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
        // 紧凑的单行显示
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
                
                // 工具名称和参数（智能内联显示）
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 工具调用标题行，格式：🔧 ToolName parameter_value
                    val inlineDisplay = getInlineToolDisplay(toolCall)
                    Text(
                        text = inlineDisplay,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 13.sp,
                            color = JewelTheme.globalColors.text.normal,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 如果有多个参数，在第二行显示摘要
                    if (toolCall.parameters.size > 1) {
                        val paramSummary = getParameterSummary(toolCall)
                        if (paramSummary.isNotEmpty()) {
                            Text(
                                text = paramSummary,
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 11.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
        SelectionContainer {
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
 * 获取工具的内联显示格式，例如：LS ./desktop
 */
private fun getInlineToolDisplay(toolCall: ToolCall): String {
    val toolName = toolCall.name
    val primaryParam = getPrimaryParamValue(toolCall)
    
    return when {
        // 对于单参数工具，直接显示：ToolName parameter
        isSingleParamTool(toolName) && primaryParam != null -> {
            when {
                // 文件路径类工具，只显示文件名/目录名
                toolName.contains("Read", ignoreCase = true) ||
                toolName.contains("Write", ignoreCase = true) ||
                toolName.contains("LS", ignoreCase = true) -> {
                    val fileName = primaryParam.substringAfterLast('/').substringAfterLast('\\')
                    "$toolName $fileName"
                }
                // URL类工具，显示域名
                toolName.contains("Web", ignoreCase = true) -> {
                    val domain = primaryParam
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore("/")
                    "$toolName $domain"
                }
                // Bash命令，截取命令的前面部分
                toolName.contains("Bash", ignoreCase = true) -> {
                    val command = if (primaryParam.length > 30) {
                        primaryParam.take(27) + "..."
                    } else {
                        primaryParam
                    }
                    "$toolName $command"
                }
                // Glob工具显示匹配模式
                toolName.contains("Glob", ignoreCase = true) -> {
                    "$toolName $primaryParam"
                }
                // Grep/Search工具显示搜索内容
                toolName.contains("Grep", ignoreCase = true) ||
                toolName.contains("Search", ignoreCase = true) -> {
                    val searchTerm = if (primaryParam.length > 25) {
                        primaryParam.take(22) + "..."
                    } else {
                        primaryParam
                    }
                    "$toolName \"$searchTerm\""
                }
                else -> "$toolName $primaryParam"
            }
        }
        // 对于多参数工具，显示工具名和主要参数
        else -> {
            if (primaryParam != null) {
                val displayParam = if (primaryParam.length > 30) {
                    primaryParam.take(27) + "..."
                } else {
                    primaryParam
                }
                "$toolName $displayParam"
            } else {
                toolName
            }
        }
    }
}

/**
 * 获取参数摘要（用于多参数工具的第二行显示）
 */
private fun getParameterSummary(toolCall: ToolCall): String {
    if (toolCall.parameters.size <= 1) return ""
    
    return when {
        // Edit工具显示编辑数量
        toolCall.name.contains("Edit", ignoreCase = true) -> {
            val editsCount = toolCall.parameters["edits"]?.let {
                if (it is List<*>) it.size else 1
            } ?: 1
            "$editsCount 处修改"
        }
        // Search/Grep工具显示搜索范围
        toolCall.name.contains("Search", ignoreCase = true) ||
        toolCall.name.contains("Grep", ignoreCase = true) -> {
            val glob = toolCall.parameters["glob"]?.toString()
            val type = toolCall.parameters["type"]?.toString()
            when {
                glob != null -> "in $glob"
                type != null -> ".$type files"
                else -> "${toolCall.parameters.size - 1} 个参数"
            }
        }
        // Glob工具显示匹配模式
        toolCall.name.contains("Glob", ignoreCase = true) -> {
            val pattern = toolCall.parameters["pattern"]?.toString()
            if (pattern != null) "pattern: $pattern" else "${toolCall.parameters.size} 个参数"
        }
        // Task工具显示任务类型
        toolCall.name.contains("Task", ignoreCase = true) -> {
            val subagentType = toolCall.parameters["subagent_type"]?.toString()
            if (subagentType != null) "agent: $subagentType" else "${toolCall.parameters.size} 个参数"
        }
        // WebFetch工具显示提示信息
        toolCall.name.contains("WebFetch", ignoreCase = true) -> {
            val prompt = toolCall.parameters["prompt"]?.toString()
            if (prompt != null && prompt.length > 20) {
                "query: ${prompt.take(17)}..."
            } else {
                prompt?.let { "query: $it" } ?: "${toolCall.parameters.size} 个参数"
            }
        }
        // NotebookEdit工具显示操作类型
        toolCall.name.contains("NotebookEdit", ignoreCase = true) -> {
            val editMode = toolCall.parameters["edit_mode"]?.toString()
            val cellType = toolCall.parameters["cell_type"]?.toString()
            when {
                editMode != null && cellType != null -> "$editMode $cellType cell"
                editMode != null -> "$editMode cell"
                else -> "${toolCall.parameters.size} 个参数"
            }
        }
        // MCP工具显示服务器名称
        toolCall.name.startsWith("mcp__", ignoreCase = true) -> {
            val serverName = toolCall.name.substringAfter("mcp__").substringBefore("__")
            "via $serverName"
        }
        // 其他工具显示参数数量
        else -> "${toolCall.parameters.size} 个参数"
    }
}

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
 * Glob 文件匹配结果显示
 */
@Composable
private fun FileMatchResultDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    when (result) {
        is ToolResult.Success -> {
            val output = result.output
            val lines = output.split('\n').filter { it.isNotBlank() }
            
            if (lines.isEmpty()) {
                Text(
                    text = "📂 未找到匹配的文件",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "📂 找到 ${lines.size} 个匹配文件：",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    Column(
                        modifier = Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        lines.take(20).forEach { filePath ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📄",
                                    style = JewelTheme.defaultTextStyle.copy(fontSize = 10.sp)
                                )
                                Text(
                                    text = filePath.substringAfterLast('/').ifEmpty { filePath },
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = JewelTheme.globalColors.text.normal
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        if (lines.size > 20) {
                            Text(
                                text = "... 还有 ${lines.size - 20} 个文件",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }
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
 * 搜索结果显示（Grep/Search）
 */
@Composable
private fun SearchResultDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    when (result) {
        is ToolResult.Success -> {
            val output = result.output
            val lines = output.split('\n').filter { it.isNotBlank() }
            
            if (lines.isEmpty()) {
                Text(
                    text = "🔍 未找到匹配的内容",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                    )
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 搜索统计
                    val pattern = toolCall.parameters["pattern"]?.toString() ?: ""
                    Text(
                        text = "🔍 搜索 \"$pattern\" 找到 ${lines.size} 处匹配：",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    // 搜索结果列表
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        lines.take(15).forEach { line ->
                            val parts = line.split(':', limit = 3)
                            if (parts.size >= 2) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // 文件名
                                    Text(
                                        text = parts[0].substringAfterLast('/'),
                                        style = JewelTheme.defaultTextStyle.copy(
                                            fontSize = 10.sp,
                                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f),
                                            fontWeight = FontWeight.Medium
                                        ),
                                        modifier = Modifier.width(120.dp)
                                    )
                                    
                                    // 行号
                                    if (parts.size >= 3) {
                                        Text(
                                            text = parts[1],
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = 10.sp,
                                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                                            ),
                                            modifier = Modifier.width(40.dp)
                                        )
                                        
                                        // 匹配内容
                                        Text(
                                            text = parts[2].trim(),
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = JewelTheme.globalColors.text.normal
                                            ),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    } else {
                                        Text(
                                            text = parts[1],
                                            style = JewelTheme.defaultTextStyle.copy(
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = JewelTheme.globalColors.text.normal
                                            ),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        
                        if (lines.size > 15) {
                            Text(
                                text = "... 还有 ${lines.size - 15} 处匹配",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                }
            }
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
 * 网页内容显示（WebFetch）
 */
@Composable
private fun WebContentDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    when (result) {
        is ToolResult.Success -> {
            val url = toolCall.parameters["url"]?.toString() ?: ""
            val content = result.output
            
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // URL 标题
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌐",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                    Text(
                        text = url.removePrefix("https://").removePrefix("http://").substringBefore("/"),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                
                // 内容摘要
                Text(
                    text = if (content.length > 500) content.take(497) + "..." else content,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    ),
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                )
                
                // 内容统计
                Text(
                    text = "内容长度：${content.length} 字符",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 10.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                    )
                )
            }
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
 * 子任务处理显示（Task）
 */
@Composable
private fun SubTaskDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    when (result) {
        is ToolResult.Success -> {
            val output = result.output
            val description = toolCall.parameters["description"]?.toString() ?: "执行任务"
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🔧 $description",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Text(
                    text = if (output.length > 300) output.take(297) + "..." else output,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
        is ToolResult.Failure -> {
            Text(
                text = "❌ 任务执行失败：${result.error}",
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
 * Jupyter 操作显示（NotebookEdit）
 */
@Composable
private fun NotebookOperationDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    when (result) {
        is ToolResult.Success -> {
            val notebookPath = toolCall.parameters["notebook_path"]?.toString() ?: ""
            val cellNumber = toolCall.parameters["cell_number"]?.toString()
            val editMode = toolCall.parameters["edit_mode"]?.toString() ?: "replace"
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 操作标题
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📓",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                    Text(
                        text = "${editMode.uppercase()} ${notebookPath.substringAfterLast('/')}",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                
                // 单元格信息
                if (cellNumber != null) {
                    Text(
                        text = "Cell: $cellNumber",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                        )
                    )
                }
                
                // 操作结果
                val output = result.output
                if (output.isNotEmpty()) {
                    Text(
                        text = if (output.length > 200) output.take(197) + "..." else output,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                        ),
                        modifier = Modifier
                            .heightIn(max = 100.dp)
                            .verticalScroll(rememberScrollState())
                    )
                } else {
                    Text(
                        text = "✅ 操作完成",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                    )
                }
            }
        }
        is ToolResult.Failure -> {
            Text(
                text = "❌ Notebook 操作失败：${result.error}",
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
 * MCP 工具统一显示
 */
@Composable
private fun MCPToolDisplay(toolCall: ToolCall) {
    val result = toolCall.result ?: return
    
    when (result) {
        is ToolResult.Success -> {
            val toolName = toolCall.name
            val serverName = toolName.substringAfter("mcp__").substringBefore("__")
            val functionName = toolName.substringAfterLast("__")
            
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // MCP 工具标题
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔗",
                        style = JewelTheme.defaultTextStyle.copy(fontSize = 12.sp)
                    )
                    Text(
                        text = "$serverName.$functionName",
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
                
                // 主要参数
                val mainParams = toolCall.parameters.entries.take(2)
                if (mainParams.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        mainParams.forEach { (key, value) ->
                            Text(
                                text = "$key: ${formatValue(value)}",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 10.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
                
                // 结果摘要
                val output = result.output
                Text(
                    text = if (output.length > 300) output.take(297) + "..." else output,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
        is ToolResult.Failure -> {
            Text(
                text = "❌ MCP 工具执行失败：${result.error}",
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
        
        // Glob 文件匹配结果展示
        toolCall.name.contains("Glob", ignoreCase = true) -> {
            FileMatchResultDisplay(toolCall)
        }
        
        // Grep/Search 搜索结果展示
        toolCall.name.contains("Grep", ignoreCase = true) ||
        toolCall.name.contains("Search", ignoreCase = true) -> {
            SearchResultDisplay(toolCall)
        }
        
        // WebFetch 网页内容展示
        toolCall.name.contains("WebFetch", ignoreCase = true) -> {
            WebContentDisplay(toolCall)
        }
        
        // Task 子任务处理展示
        toolCall.name.contains("Task", ignoreCase = true) -> {
            SubTaskDisplay(toolCall)
        }
        
        // NotebookEdit Jupyter 操作展示
        toolCall.name.contains("NotebookEdit", ignoreCase = true) -> {
            NotebookOperationDisplay(toolCall)
        }
        
        // MCP 工具统一展示（以 mcp__ 开头）
        toolCall.name.startsWith("mcp__", ignoreCase = true) -> {
            MCPToolDisplay(toolCall)
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
            // 对结果内容进行智能过滤和简化
            val cleanedContent = cleanToolResultContent(result.output, toolCall.name)
            
            if (cleanedContent.isNotEmpty()) {
                // 根据工具类型决定是否限制高度
                val shouldLimitHeight = shouldLimitToolHeight(toolCall)
                
                // 创建简化后的工具调用对象
                val simplifiedToolCall = toolCall.copy(
                    result = ToolResult.Success(cleanedContent)
                )
                
                ToolResultContent(
                    toolCall = simplifiedToolCall,
                    limitHeight = shouldLimitHeight,
                    maxHeight = 200.dp
                )
            } else {
                // 如果内容被完全过滤掉，显示简单的成功状态
                Text(
                    text = "✅ 执行成功",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                )
            }
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

/**
 * 清理工具结果内容，过滤技术噪音
 */
private fun cleanToolResultContent(content: String, toolName: String): String {
    if (content.isBlank()) return ""
    
    // MCP 工具统一过滤
    if (toolName.startsWith("mcp__", ignoreCase = true)) {
        return cleanMcpToolResult(content, toolName)
    }
    
    return when {
        // 文件操作工具 - 只显示关键信息
        toolName.contains("LS", ignoreCase = true) -> cleanLsOutput(content)
        toolName.contains("Read", ignoreCase = true) -> cleanReadOutput(content)
        toolName.contains("Write", ignoreCase = true) -> cleanWriteOutput(content)
        toolName.contains("Edit", ignoreCase = true) -> cleanEditOutput(content)
        
        // 系统工具 - 过滤配置和技术信息
        toolName.contains("Bash", ignoreCase = true) -> cleanBashOutput(content)
        toolName.contains("info", ignoreCase = true) -> cleanInfoOutput(content)
        
        // 其他工具保持原有内容但限制长度
        else -> if (content.length > 500) content.take(497) + "..." else content
    }
}

/**
 * 清理 MCP 工具结果
 */
private fun cleanMcpToolResult(content: String, toolName: String): String {
    val serverName = toolName.substringAfter("mcp__").substringBefore("__")
    val functionName = toolName.substringAfterLast("__")
    
    // 过滤常见的 MCP 技术输出
    return when {
        // 数据库操作结果
        serverName.contains("postgres", ignoreCase = true) -> {
            if (content.contains("rows affected", ignoreCase = true)) {
                extractRowsAffected(content)
            } else if (content.contains("error", ignoreCase = true)) {
                "❌ 数据库操作失败"
            } else {
                "✅ 数据库操作成功"
            }
        }
        
        // Redis 操作结果
        serverName.contains("redis", ignoreCase = true) -> {
            if (content.contains("error", ignoreCase = true) || content.contains("fail", ignoreCase = true)) {
                "❌ Redis 操作失败"
            } else {
                "✅ Redis 操作成功"
            }
        }
        
        // Excel 操作结果
        serverName.contains("excel", ignoreCase = true) -> {
            when {
                functionName.contains("read", ignoreCase = true) -> "📊 Excel 文件读取完成"
                functionName.contains("write", ignoreCase = true) -> "📝 Excel 文件写入完成"
                functionName.contains("format", ignoreCase = true) -> "🎨 Excel 格式设置完成"
                else -> "✅ Excel 操作完成"
            }
        }
        
        // 其他 MCP 工具的默认处理
        else -> {
            if (content.length > 200) {
                // 尝试提取关键信息
                val lines = content.lines().filter { it.trim().isNotEmpty() }
                val keyLines = lines.filter { line ->
                    !line.contains("[", ignoreCase = true) &&
                    !line.contains("{", ignoreCase = true) &&
                    !line.contains("server", ignoreCase = true) &&
                    line.length < 100
                }
                
                if (keyLines.isNotEmpty()) {
                    keyLines.take(3).joinToString("\n")
                } else {
                    "✅ $functionName 执行完成"
                }
            } else {
                content
            }
        }
    }
}

/**
 * 清理 LS 命令输出
 */
private fun cleanLsOutput(content: String): String {
    val lines = content.lines().filter { it.trim().isNotEmpty() }
    return if (lines.size > 10) {
        "📁 ${lines.size} 个文件/目录\n${lines.take(10).joinToString("\n")}\n... 还有 ${lines.size - 10} 项"
    } else {
        content
    }
}

/**
 * 清理读文件输出
 */
private fun cleanReadOutput(content: String): String {
    val lines = content.lines()
    return if (lines.size > 20) {
        "📄 文件内容 (${lines.size} 行)\n${lines.take(15).joinToString("\n")}\n... 还有 ${lines.size - 15} 行"
    } else {
        content
    }
}

/**
 * 清理写文件输出
 */
private fun cleanWriteOutput(content: String): String {
    return when {
        content.contains("successfully", ignoreCase = true) -> "✅ 文件写入成功"
        content.contains("created", ignoreCase = true) -> "✅ 文件创建成功"
        content.contains("error", ignoreCase = true) -> "❌ 文件操作失败"
        else -> if (content.length > 100) "✅ 文件操作完成" else content
    }
}

/**
 * 清理编辑文件输出
 */
private fun cleanEditOutput(content: String): String {
    return when {
        content.contains("successfully", ignoreCase = true) -> "✅ 文件编辑成功"
        content.contains("modified", ignoreCase = true) -> "✅ 文件修改完成"
        content.contains("error", ignoreCase = true) -> "❌ 编辑失败"
        else -> if (content.length > 100) "✅ 文件编辑完成" else content
    }
}

/**
 * 清理 Bash 命令输出
 */
private fun cleanBashOutput(content: String): String {
    // 保持 Bash 输出，但限制长度
    return if (content.length > 800) {
        content.take(797) + "..."
    } else {
        content
    }
}

/**
 * 清理信息命令输出（如 MCP info）
 */
private fun cleanInfoOutput(content: String): String {
    // 过滤掉大段的配置和服务器列表
    val lines = content.lines().filter { line ->
        !line.contains("[") && 
        !line.contains("{") &&
        !line.contains("server", ignoreCase = true) &&
        !line.contains("config", ignoreCase = true) &&
        line.trim().isNotEmpty()
    }
    
    return if (lines.isEmpty()) {
        "✅ 信息查询完成"
    } else {
        lines.take(5).joinToString("\n")
    }
}

/**
 * 从数据库输出中提取影响行数
 */
private fun extractRowsAffected(content: String): String {
    val regex = "(\\d+)\\s+rows?\\s+affected".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.find(content)
    return if (match != null) {
        "✅ 操作成功，影响 ${match.groupValues[1]} 行"
    } else {
        "✅ 数据库操作完成"
    }
}