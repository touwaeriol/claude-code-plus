@file:OptIn(ExperimentalFoundationApi::class, org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

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
import com.claudecodeplus.ui.jewel.components.tools.*
import com.claudecodeplus.ui.jewel.components.tools.output.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.styling.TooltipStyle
import org.jetbrains.jewel.ui.theme.tooltipStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.delay
import com.claudecodeplus.ui.services.stringResource
import com.claudecodeplus.ui.services.formatStringResource
import com.claudecodeplus.ui.services.StringResources

/**
 * 紧凑的工具调用显示组件
 * 默认单行显示，点击展开详情
 * 已简化：移除复杂的固定显示逻辑，直接控制展开高度
 */
@Composable
fun CompactToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,  // IDE 集成接口
    onExpandedChange: ((String, Boolean) -> Unit)? = null
) {
    
    // 简化的普通显示模式 - 移除复杂的固定显示逻辑
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        toolCalls.forEach { toolCall ->
            CompactToolCallItem(
                toolCall = toolCall,
                ideIntegration = ideIntegration,
                onExpandedChange = onExpandedChange
            )
        }
    }
}

/**
 * 单个工具调用的紧凑显示
 */
@Composable
private fun CompactToolCallItem(
    toolCall: ToolCall,
    ideIntegration: com.claudecodeplus.ui.services.IdeIntegration? = null,
    onExpandedChange: ((String, Boolean) -> Unit)? = null
) {
    
    // TodoWrite 工具默认展开显示任务列表
    var expanded by remember { 
        mutableStateOf(toolCall.name.contains("TodoWrite", ignoreCase = true)) 
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // 展开状态变化时通知上级组件
    LaunchedEffect(expanded) {
        delay(100)  // 简单防抖
        onExpandedChange?.invoke(toolCall.id, expanded)
    }
    
    // 背景色动画（更平滑的过渡）
    val backgroundColor by animateColorAsState(
        targetValue = when {
            expanded -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.4f)
            isHovered -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.2f)
            else -> JewelTheme.globalColors.panelBackground.copy(alpha = 0.05f)
        },
        animationSpec = tween(200, easing = EaseInOut),
        label = "background_color"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))  // 减少圆角半径
            .background(backgroundColor)
            .hoverable(interactionSource)
    ) {
        // 紧凑的单行显示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)  // 确保最小点击高度
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,  // 使用hover效果替代ripple
                    onClick = {
                        // 尝试使用 IDE 集成处理工具点击
                        val handled = if (ideIntegration != null) {
                            try {
                                ideIntegration.handleToolClick(toolCall)
                            } catch (e: Exception) {
                                false
                            }
                        } else {
                            false
                        }

                        if (!handled) {
                            // 回退到默认展开行为
                            expanded = !expanded
                        }
                    }
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),  // 增加内边距提升点击体验
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
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,  // 减少图标大小
                        lineHeight = 12.sp  // 减少行高
                    )
                )
                
                // 工具名称和参数（单行紧凑显示）
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 工具调用标题行，格式：🔧 ToolName: parameter_value
                    val inlineDisplay = getInlineToolDisplay(toolCall)
                    Text(
                        text = inlineDisplay,
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,  // 进一步减少字体大小
                            color = JewelTheme.globalColors.text.normal,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            lineHeight = 11.sp  // 设置行高等于字体大小，减少行间距
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // 右侧状态指示器
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 简化状态指示器（避免StackOverflow）
                Text(
                    text = when (toolCall.status) {
                        ToolCallStatus.PENDING -> "⏳"
                        ToolCallStatus.RUNNING -> "🔄"
                        ToolCallStatus.SUCCESS -> "✅"
                        ToolCallStatus.FAILED -> "❌"
                        ToolCallStatus.CANCELLED -> "⚠️"
                    },
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 9.sp)
                )
                
                // 简化的展开/折叠图标
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = expanded,
                        transitionSpec = {
                            scaleIn(animationSpec = tween(200)) + fadeIn() togetherWith
                            scaleOut(animationSpec = tween(200)) + fadeOut()
                        },
                        label = "expand_icon"
                    ) { isExpanded ->
                        Text(
                            text = if (isExpanded) "⌄" else "›",
                            style = JewelTheme.defaultTextStyle.copy(
                                fontSize = 10.sp,
                                color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f),
                                lineHeight = 10.sp
                            )
                        )
                    }
                }
            }
        }
        
        // 展开的详细内容 - 🎯 优化动画性能，使用 animateContentSize
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(
                animationSpec = tween(300, delayMillis = 50)
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut(
                animationSpec = tween(200)
            )
        ) {
            ToolCallDetails(
                toolCall = toolCall
            )
        }
    }
}

/**
 * 工具调用的详细信息
 */
@Composable
private fun ToolCallDetails(
    toolCall: ToolCall
) {
    // 判断是否需要显示详细结果
    val shouldShowDetails = shouldShowToolDetails(toolCall)
    
    if (!shouldShowDetails) {
        // 对于不需要显示详细结果的工具，不渲染任何内容
        return
    }
    
    // 🎯 设置最大高度为300dp（约等于视窗40%）
    val maxExpandHeight = 300.dp
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.05f))  // 更淡的背景
    ) {
        // 详细内容区域 - 🔑 添加高度限制和内部滚动
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxExpandHeight)  // 限制最大高度为视窗40%
        ) {
            // 使用带滚动条的垂直滚动
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    // 根据工具调用状态显示对应内容
                    when {
                        // 运行中的工具调用显示进度状态
                        toolCall.status == ToolCallStatus.RUNNING -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.claudecodeplus.ui.jewel.components.tools.JumpingDots()
                                Text(
                                    text = "工具执行中...",
                                    style = JewelTheme.defaultTextStyle.copy(
                                        fontSize = 12.sp,
                                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                        
                        // 等待中的工具调用
                        toolCall.status == ToolCallStatus.PENDING -> {
                            Text(
                                text = "⏳ 等待执行...",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                )
                            )
                        }
                        
                        // 取消的工具调用
                        toolCall.status == ToolCallStatus.CANCELLED -> {
                            Text(
                                text = "⚠️ 工具执行已取消",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                                )
                            )
                        }
                        
                        // 有结果的工具调用显示格式化结果
                        toolCall.result != null -> {
                            formatToolResult(toolCall)
                        }
                        
                        // 失败状态但没有结果对象的情况
                        toolCall.status == ToolCallStatus.FAILED -> {
                            Text(
                                text = "❌ 工具执行失败",
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF6B6B)
                                )
                            )
                        }
                        
                        // 其他情况显示状态
                        else -> {
                            Text(
                                text = formatStringResource(StringResources.TOOL_STATUS, toolCall.status),
                                style = JewelTheme.defaultTextStyle.copy(
                                    fontSize = 12.sp,
                                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                } // 结束 Column
            } // 结束 SelectionContainer
            
            // 复制按钮 - 浮动在右上角
            val clipboardManager = LocalClipboardManager.current
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = "📋",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .clickable {
                            // 复制工具调用结果到剪贴板 - 只复制纯文本结果
                            val content = buildString {
                                toolCall.result?.let { result ->
                                    when (result) {
                                        is ToolResult.Success -> {
                                            // 去除行号和格式信息，只保留纯文本内容
                                            val cleanOutput = result.output
                                                .lines()
                                                .map { line ->
                                                    // 去除行号前缀（格式如：123→ 或 123： 等）
                                                    line.replace(Regex("^\\s*\\d+[→:→]"), "").trimStart()
                                                }
                                                .joinToString("\n")
                                            append(cleanOutput)
                                        }
                                        is ToolResult.Failure -> {
                                            append(result.error)
                                        }
                                        else -> {
                                            // 处理其他类型的结果
                                            val resultStr = result.toString()
                                            val cleanResult = resultStr
                                                .lines()
                                                .map { line ->
                                                    line.replace(Regex("^\\s*\\d+[→:→]"), "").trimStart()
                                                }
                                                .joinToString("\n")
                                            append(cleanResult)
                                        }
                                    }
                                } ?: append("无结果")
                            }
                            clipboardManager.setText(AnnotatedString(content))
                        }
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * 判断是否需要显示工具的详细结果
 * 修复：确保所有状态的工具都可以展开显示
 */
private fun shouldShowToolDetails(toolCall: ToolCall): Boolean {
    return when {
        // 运行中的工具调用应该显示进度状态
        toolCall.status == ToolCallStatus.RUNNING -> true
        
        // 失败的工具调用必须显示错误信息
        toolCall.status == ToolCallStatus.FAILED -> true
        toolCall.result is ToolResult.Failure -> true
        
        // 成功的工具调用显示结果
        toolCall.result is ToolResult.Success -> true
        
        // 取消的工具调用显示状态
        toolCall.status == ToolCallStatus.CANCELLED -> true
        
        // 等待中的工具调用显示等待状态
        toolCall.status == ToolCallStatus.PENDING -> true
        
        // 默认显示，确保用户能看到所有工具调用的状态
        else -> true
    }
}

/**
 * 获取工具图标
 */
private fun getToolIcon(toolCall: ToolCall): String {
    return when {
        toolCall.name.contains("Read", ignoreCase = true) -> "📖"
        toolCall.name.contains("Write", ignoreCase = true) -> "✏️"
        toolCall.name.contains("Edit", ignoreCase = true) -> "✏️"
        toolCall.name.contains("Bash", ignoreCase = true) -> "💻"
        toolCall.name.contains("Web", ignoreCase = true) -> "🌐"
        toolCall.name.contains("Glob", ignoreCase = true) -> "🔍"
        toolCall.name.contains("Grep", ignoreCase = true) -> "🔍"
        toolCall.name.contains("Task", ignoreCase = true) -> "🤖"
        toolCall.name.contains("Todo", ignoreCase = true) -> "📝"
        toolCall.name.startsWith("mcp__", ignoreCase = true) -> "🔧"
        else -> "🔧"
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
        // 对于单参数工具，使用冒号格式：ToolName: parameter
        isSingleParamTool(toolName) && primaryParam != null -> {
            when {
                // 文件路径类工具，只显示文件名/目录名
                toolName.contains("Read", ignoreCase = true) ||
                toolName.contains("Write", ignoreCase = true) ||
                toolName.contains("LS", ignoreCase = true) -> {
                    val fileName = primaryParam.substringAfterLast('/').substringAfterLast('\\')
                    "$toolName: $fileName"
                }
                // URL类工具，显示域名
                toolName.contains("Web", ignoreCase = true) -> {
                    val domain = primaryParam
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .substringBefore("/")
                    "$toolName: $domain"
                }
                // Bash命令，截取命令的前面部分
                toolName.contains("Bash", ignoreCase = true) -> {
                    val command = if (primaryParam.length > 25) {
                        primaryParam.take(22) + "..."
                    } else {
                        primaryParam
                    }
                    "$toolName: $command"
                }
                // Glob工具显示匹配模式
                toolName.contains("Glob", ignoreCase = true) -> {
                    "$toolName: $primaryParam"
                }
                // Grep/Search工具显示搜索内容
                toolName.contains("Grep", ignoreCase = true) ||
                toolName.contains("Search", ignoreCase = true) -> {
                    val searchTerm = if (primaryParam.length > 20) {
                        primaryParam.take(17) + "..."
                    } else {
                        primaryParam
                    }
                    "$toolName: $searchTerm"
                }
                else -> "$toolName: $primaryParam"
            }
        }
        // 对于多参数工具，显示工具名和主要参数
        else -> {
            if (primaryParam != null) {
                val displayParam = if (primaryParam.length > 25) {
                    primaryParam.take(22) + "..."
                } else {
                    primaryParam
                }
                "$toolName: $displayParam"
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
    
    
    // 回退到原有的基于名称匹配的逻辑（兼容性）
    return when {
        // MCP工具显示服务器名称
        toolCall.name.startsWith("mcp__", ignoreCase = true) -> {
            val serverName = toolCall.name.substringAfter("mcp__").substringBefore("__")
            "via $serverName"
        }
        // Edit工具显示编辑数量（回退逻辑）
        toolCall.name.contains("Edit", ignoreCase = true) -> {
            val editsCount = toolCall.parameters["edits"]?.let {
                if (it is List<*>) it.size else 1
            } ?: 1
            formatStringResource(StringResources.EDIT_CHANGES, editsCount)
        }
        // Search/Grep工具显示搜索范围（回退逻辑）
        toolCall.name.contains("Search", ignoreCase = true) ||
        toolCall.name.contains("Grep", ignoreCase = true) -> {
            val glob = toolCall.parameters["glob"]?.toString()
            val type = toolCall.parameters["type"]?.toString()
            when {
                glob != null -> "in $glob"
                type != null -> ".$type files"
                else -> formatStringResource(StringResources.PARAMETERS_COUNT, toolCall.parameters.size - 1)
            }
        }
        // Glob工具显示匹配模式（回退逻辑）
        toolCall.name.contains("Glob", ignoreCase = true) -> {
            val pattern = toolCall.parameters["pattern"]?.toString()
            if (pattern != null) "pattern: $pattern" else "${toolCall.parameters.size} 个参数"
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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatStringResource(StringResources.FILES_FOUND, lines.size),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    Column(
                        modifier = Modifier
                            .heightIn(max = 120.dp)  // 减少最大高度
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
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
                                text = formatStringResource(StringResources.FILES_MORE, lines.size - 20),
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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 搜索统计
                    val pattern = toolCall.parameters["pattern"]?.toString() ?: ""
                    Text(
                        text = formatStringResource(StringResources.SEARCH_RESULTS, pattern, lines.size),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    
                    // 搜索结果列表
                    Column(
                        modifier = Modifier
                            .heightIn(max = 140.dp)  // 减少最大高度
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        lines.take(15).forEach { line ->
                            val parts = line.split(':', limit = 3)
                            if (parts.size >= 2) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                text = formatStringResource(StringResources.SEARCH_MORE, lines.size - 15),
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
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // URL 标题
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    text = if (content.length > 400) content.take(397) + "..." else content,  // 减少字符数
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f),
                        lineHeight = 15.sp  // 减少行高
                    ),
                    modifier = Modifier
                        .heightIn(max = 100.dp)  // 减少最大高度
                        .verticalScroll(rememberScrollState())
                )
                
                // 内容统计
                Text(
                    text = formatStringResource(StringResources.CONTENT_LENGTH, content.length),
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "🔧 $description",
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Text(
                    text = if (output.length > 250) output.take(247) + "..." else output,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = JewelTheme.globalColors.text.normal.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier
                        .heightIn(max = 100.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
        is ToolResult.Failure -> {
            Text(
                text = formatStringResource(StringResources.TASK_EXECUTION_FAILED, result.error),
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 操作标题
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                            .heightIn(max = 80.dp)
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
                text = formatStringResource(StringResources.NOTEBOOK_OPERATION_FAILED, result.error),
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // MCP 工具标题
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                        verticalArrangement = Arrangement.spacedBy(1.dp)
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
            }
        }
        is ToolResult.Failure -> {
            Text(
                text = formatStringResource(StringResources.MCP_TOOL_FAILED, result.error),
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
    when {
        // Edit/MultiEdit 使用 Diff 展示
        toolCall.name.contains("Edit", ignoreCase = true) -> {
            DiffResultDisplay(toolCall)
        }

        // Read/Write 使用内容预览
        toolCall.name.contains("Read", ignoreCase = true) ||
        toolCall.name.contains("Write", ignoreCase = true) -> {
            FileContentPreview(toolCall)
        }

        // LS 使用文件列表展示
        toolCall.name.contains("LS", ignoreCase = true) -> {
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
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
            .padding(4.dp)
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
            
            // 过滤掉空行，只保留有内容的行
            val cleanedOutput = output.lines()
                .filter { it.trim().isNotEmpty() }
                .joinToString("\n")
            
            // 直接使用 ANSI 终端显示输出 - 进一步减少显示行数节省空间
            AnsiOutputView(
                text = cleanedOutput,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 8,  // 进一步减少到8行
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
                    maxHeight = 120.dp
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
    return when {
        toolCall.name.contains("Grep", ignoreCase = true) -> true
        toolCall.name.contains("Glob", ignoreCase = true) -> true
        toolCall.name.contains("Read", ignoreCase = true) -> true
        toolCall.name.contains("LS", ignoreCase = true) -> true
        else -> false
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
 * 清理 MCP 工具结果 - 增强版，更精准的内容过滤
 */
private fun cleanMcpToolResult(content: String, toolName: String): String {
    val serverName = toolName.substringAfter("mcp__").substringBefore("__")
    val functionName = toolName.substringAfterLast("__")
    
    // 过滤常见的 MCP 技术输出
    return when {
        // 数据库操作结果
        serverName.contains("postgres", ignoreCase = true) -> {
            when {
                content.contains("rows affected", ignoreCase = true) -> extractRowsAffected(content)
                content.contains("SELECT", ignoreCase = true) -> {
                    val lines = content.lines().filter { it.trim().isNotEmpty() }
                    "📊 查询结果 (${lines.size} 行数据)"
                }
                content.contains("error", ignoreCase = true) -> "❌ 数据库操作失败"
                functionName.contains("list", ignoreCase = true) -> {
                    val count = content.lines().filter { it.trim().isNotEmpty() }.size
                    "📋 列出 $count 项"
                }
                else -> "✅ 数据库操作成功"
            }
        }
        
        // Redis 操作结果
        serverName.contains("redis", ignoreCase = true) -> {
            when {
                content.contains("error", ignoreCase = true) || content.contains("fail", ignoreCase = true) -> 
                    "❌ Redis 操作失败"
                functionName.contains("get", ignoreCase = true) && content.length > 50 ->
                    "📤 获取数据 (${content.length} 字符)"
                functionName.contains("set", ignoreCase = true) -> "📥 数据写入成功"
                functionName.contains("search", ignoreCase = true) -> {
                    val matches = content.lines().filter { it.trim().isNotEmpty() }.size
                    "🔍 搜索到 $matches 项结果"
                }
                else -> "✅ Redis 操作成功"
            }
        }
        
        // Excel 操作结果
        serverName.contains("excel", ignoreCase = true) -> {
            when {
                functionName.contains("read", ignoreCase = true) -> {
                    if (content.contains("rows", ignoreCase = true)) {
                        "📊 Excel 数据读取完成"
                    } else {
                        "📊 Excel 文件读取完成"
                    }
                }
                functionName.contains("write", ignoreCase = true) -> "📝 Excel 数据写入完成"
                functionName.contains("format", ignoreCase = true) -> "🎨 Excel 格式设置完成"
                functionName.contains("create", ignoreCase = true) -> "📄 Excel 文件创建完成"
                else -> "✅ Excel 操作完成"
            }
        }
        
        // XMind 操作结果
        serverName.contains("xmind", ignoreCase = true) -> {
            when {
                functionName.contains("read", ignoreCase = true) -> "🧠 思维导图解析完成"
                functionName.contains("search", ignoreCase = true) -> "🔍 思维导图搜索完成"
                functionName.contains("extract", ignoreCase = true) -> "📤 节点提取完成"
                else -> "✅ XMind 操作完成"
            }
        }
        
        // Gradle 类查找结果
        serverName.contains("gradle", ignoreCase = true) -> {
            when {
                functionName.contains("find_class", ignoreCase = true) -> {
                    if (content.contains("找到", ignoreCase = true)) {
                        "🔍 类查找完成"
                    } else {
                        "❌ 未找到指定类"
                    }
                }
                functionName.contains("get_source", ignoreCase = true) -> "📄 源码获取完成"
                else -> "✅ Gradle 操作完成"
            }
        }
        
        // 其他 MCP 工具的智能处理
        else -> {
            when {
                content.length > 500 -> {
                    // 长内容，智能提取摘要
                    val lines = content.lines().filter { it.trim().isNotEmpty() }
                    val dataLines = lines.filter { line ->
                        !line.contains("server", ignoreCase = true) &&
                        !line.contains("config", ignoreCase = true) &&
                        !line.contains("debug", ignoreCase = true) &&
                        line.length < 120
                    }
                    
                    if (dataLines.isNotEmpty()) {
                        "${dataLines.take(2).joinToString("\n")}\n... (${lines.size} 行数据)"
                    } else {
                        "✅ $functionName 执行完成 (${lines.size} 行输出)"
                    }
                }
                content.length > 100 -> {
                    // 中等长度内容，保留关键信息
                    val lines = content.lines().filter { it.trim().isNotEmpty() }.take(3)
                    lines.joinToString("\n")
                }
                else -> content // 短内容保持原样
            }
        }
    }
}

/**
 * 清理 LS 命令输出 - 增强版，更智能的分类统计
 */
private fun cleanLsOutput(content: String): String {
    val lines = content.lines().filter { it.trim().isNotEmpty() }
    
    if (lines.size <= 8) {
        return content  // 内容不多，保持原样
    }
    
    // 分析文件类型
    val directories = lines.count { line ->
        line.startsWith("d") || line.endsWith("/") || 
        (!line.contains(".") && !line.contains(" "))
    }
    val files = lines.size - directories
    
    val summary = buildString {
        append("📁 ")
        if (directories > 0 && files > 0) {
            append("${directories} 个目录, ${files} 个文件")
        } else if (directories > 0) {
            append("${directories} 个目录")
        } else {
            append("${files} 个文件")
        }
    }
    
    return buildString {
        append(summary)
        append("\n")
        append(lines.take(6).joinToString("\n"))
        if (lines.size > 6) {
            append("\n... 还有 ${lines.size - 6} 项")
        }
    }
}

/**
 * 清理读文件输出 - 增强版，智能内容摘要
 */
private fun cleanReadOutput(content: String): String {
    val lines = content.lines()
    
    // 短文件内容，保持原样
    if (lines.size <= 12 && content.length <= 800) {
        return content
    }
    
    // 长文件，显示摘要信息
    val nonEmptyLines = lines.filter { it.trim().isNotEmpty() }
    val fileType = when {
        lines.any { it.trim().startsWith("{") || it.trim().startsWith("[") } -> "JSON"
        lines.any { it.trim().startsWith("<") } -> "XML/HTML"
        lines.any { it.contains("function") || it.contains("class") } -> "代码"
        lines.any { it.startsWith("#") } -> "配置"
        else -> "文本"
    }
    
    return buildString {
        append("📄 $fileType 文件内容 (${lines.size} 行, ${content.length} 字符)")
        append("\n")
        append(lines.take(8).joinToString("\n"))
        if (lines.size > 8) {
            append("\n... 还有 ${lines.size - 8} 行")
        }
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
 * 清理 Bash 命令输出 - 增强版，更激进的过滤
 */
private fun cleanBashOutput(content: String): String {
    val lines = content.lines().filter { it.trim().isNotEmpty() }
    
    // 对不同类型的命令输出采用不同的处理策略
    return when {
        // 文件列表类命令 (ls, find等)
        lines.any { it.contains(".") && (it.contains("/") || it.contains("\\")) } -> {
            val fileLines = lines.filter { 
                it.contains(".") && (it.contains("/") || it.contains("\\"))
            }
            if (fileLines.size > 10) {
                "📁 找到 ${fileLines.size} 个文件\n${fileLines.take(8).joinToString("\n")}\n... 还有 ${fileLines.size - 8} 个文件"
            } else {
                fileLines.joinToString("\n")
            }
        }
        
        // 统计信息类命令
        lines.any { it.contains("total") || it.contains("count") || it.contains("=") } -> {
            lines.filter { 
                it.contains("total") || it.contains("count") || 
                it.contains("=") || it.length < 50 
            }.take(5).joinToString("\n")
        }
        
        // 长输出内容，只保留关键信息
        lines.size > 15 -> {
            val keyLines = lines.filter { line ->
                line.length < 100 && 
                !line.startsWith("#") && 
                !line.startsWith("//") &&
                !line.trim().startsWith("*") &&
                line.trim().isNotEmpty()
            }.take(8)
            
            if (keyLines.isNotEmpty()) {
                "${keyLines.joinToString("\n")}\n... (${lines.size - keyLines.size} 行省略)"
            } else {
                "✅ 命令执行完成 (${lines.size} 行输出)"
            }
        }
        
        // 短输出，保持原样但限制长度
        else -> {
            val cleanedContent = lines.joinToString("\n")
            if (cleanedContent.length > 400) {
                cleanedContent.take(397) + "..."
            } else {
                cleanedContent
            }
        }
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

/**
 * 格式化工具调用的简要信息
 */
private fun formatToolBriefInfo(toolCall: ToolCall): String {
    val primaryValue = getPrimaryParamValue(toolCall)
    
    return when {
        // 单参数工具，直接显示参数值
        isSingleParamTool(toolCall.name) && primaryValue != null -> {
            when {
                toolCall.name.contains("Read", ignoreCase = true) ||
                toolCall.name.contains("Write", ignoreCase = true) ||
                toolCall.name.contains("LS", ignoreCase = true) -> {
                    // 对于文件路径，只显示文件名
                    primaryValue.substringAfterLast('/').substringAfterLast('\\')
                }
                else -> primaryValue.take(40)
            }
        }
        
        
        // Edit/MultiEdit 显示修改数量（回退逻辑）
        toolCall.name.contains("Edit", ignoreCase = true) -> {
            val editsCount = toolCall.parameters["edits"]?.let {
                if (it is List<*>) it.size else 1
            } ?: 1
            val fileName = primaryValue?.substringAfterLast('/')?.substringAfterLast('\\') ?: ""
            "$fileName ($editsCount changes)"
        }
        
        // Search/Grep 显示搜索模式（回退逻辑）
        toolCall.name.contains("Search", ignoreCase = true) ||
        toolCall.name.contains("Grep", ignoreCase = true) -> {
            val pattern = toolCall.parameters["pattern"] ?: toolCall.parameters["query"] ?: ""
            val glob = toolCall.parameters["glob"]?.toString()?.let { " in $it" } ?: ""
            "\"$pattern\"$glob"
        }
        
        // TodoWrite 显示任务信息（回退逻辑）
        toolCall.name.contains("TodoWrite", ignoreCase = true) -> {
            val todos = toolCall.parameters["todos"] as? List<*>
            if (todos != null) {
                "更新 ${todos.size} 个任务"
            } else {
                "任务管理"
            }
        }
        
        // 其他工具显示第一个参数
        else -> {
            toolCall.parameters.values.firstOrNull()?.toString()?.take(40) ?: ""
        }
    }
}


