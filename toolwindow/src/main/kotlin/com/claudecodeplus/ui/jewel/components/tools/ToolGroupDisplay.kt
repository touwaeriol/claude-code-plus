package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.*

/**
 * 工具分组显示组件
 * 将工具调用按类型分组展示
 */
@Composable
fun ToolGroupDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    // 将工具调用按类型分组
    val groupedTools = remember(toolCalls) {
        groupTools(toolCalls)
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedTools.forEach { (group, tools) ->
            ToolGroup(
                groupName = group.displayName,
                groupIcon = group.icon,
                groupColor = group.color,
                toolCalls = tools
            )
        }
    }
}

/**
 * 单个工具分组
 */
@Composable
private fun ToolGroup(
    groupName: String,
    groupIcon: String,
    groupColor: Color,
    toolCalls: List<ToolCall>
) {
    var expanded by remember { mutableStateOf(true) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.05f))
    ) {
        // 分组头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分组图标
                Text(
                    text = groupIcon,
                    style = JewelTheme.defaultTextStyle.copy(fontSize = 16.sp)
                )
                
                // 分组名称
                Text(
                    text = groupName,
                    style = JewelTheme.defaultTextStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = groupColor
                    )
                )
                
                // 工具数量徽章
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(groupColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = toolCalls.size.toString(),
                        style = JewelTheme.defaultTextStyle.copy(
                            fontSize = 11.sp,
                            color = groupColor
                        )
                    )
                }
            }
            
            // 展开/折叠图标
            val rotation by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = tween(200)
            )
            
            Text(
                text = "▼",
                style = JewelTheme.defaultTextStyle.copy(
                    fontSize = 10.sp,
                    color = JewelTheme.globalColors.text.normal.copy(alpha = 0.5f)
                ),
                modifier = Modifier.rotate(rotation)
            )
        }
        
        // 工具列表
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // TodoWrite 工具特殊处理
                val todoTools = toolCalls.filter { it.name.contains("TodoWrite", ignoreCase = true) }
                val otherTools = toolCalls.filter { !it.name.contains("TodoWrite", ignoreCase = true) }
                
                // 显示 TodoWrite 工具
                todoTools.forEach { toolCall ->
                    TodoListDisplay(
                        toolCall = toolCall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // 显示其他工具
                if (otherTools.isNotEmpty()) {
                    CompactToolCallDisplay(
                        toolCalls = otherTools,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 工具分组信息
 */
private data class ToolGroup(
    val type: ToolGroupType,
    val displayName: String,
    val icon: String,
    val color: Color
)

/**
 * 工具分组类型
 */
private enum class ToolGroupType {
    FILE_OPERATIONS,
    SEARCH_TOOLS,
    EXECUTION_TOOLS,
    MANAGEMENT_TOOLS,
    NETWORK_TOOLS,
    OTHER
}

/**
 * 对工具进行分组
 */
private fun groupTools(toolCalls: List<ToolCall>): Map<ToolGroup, List<ToolCall>> {
    val groups = mutableMapOf<ToolGroup, MutableList<ToolCall>>()
    
    toolCalls.forEach { toolCall ->
        val group = determineToolGroup(toolCall.name)
        groups.getOrPut(group) { mutableListOf() }.add(toolCall)
    }
    
    return groups
}

/**
 * 根据工具名称确定分组
 */
private fun determineToolGroup(toolName: String): ToolGroup {
    return when {
        toolName.contains("Read", ignoreCase = true) ||
        toolName.contains("Write", ignoreCase = true) ||
        toolName.contains("Edit", ignoreCase = true) ||
        toolName.contains("LS", ignoreCase = true) ||
        toolName.contains("MultiEdit", ignoreCase = true) ||
        toolName.contains("NotebookRead", ignoreCase = true) ||
        toolName.contains("NotebookEdit", ignoreCase = true) -> {
            ToolGroup(
                type = ToolGroupType.FILE_OPERATIONS,
                displayName = "文件操作",
                icon = "📁",
                color = Color(0xFF4CAF50)
            )
        }
        
        toolName.contains("Search", ignoreCase = true) ||
        toolName.contains("Grep", ignoreCase = true) ||
        toolName.contains("Glob", ignoreCase = true) -> {
            ToolGroup(
                type = ToolGroupType.SEARCH_TOOLS,
                displayName = "搜索工具",
                icon = "🔍",
                color = Color(0xFF2196F3)
            )
        }
        
        toolName.contains("Bash", ignoreCase = true) ||
        toolName.contains("Task", ignoreCase = true) ||
        toolName.contains("ExitPlanMode", ignoreCase = true) -> {
            ToolGroup(
                type = ToolGroupType.EXECUTION_TOOLS,
                displayName = "执行工具",
                icon = "💻",
                color = Color(0xFF9C27B0)
            )
        }
        
        toolName.contains("Todo", ignoreCase = true) ||
        toolName.contains("Git", ignoreCase = true) -> {
            ToolGroup(
                type = ToolGroupType.MANAGEMENT_TOOLS,
                displayName = "管理工具",
                icon = "📋",
                color = Color(0xFFFF9800)
            )
        }
        
        toolName.contains("Web", ignoreCase = true) ||
        toolName.contains("ListMcp", ignoreCase = true) ||
        toolName.contains("ReadMcp", ignoreCase = true) -> {
            ToolGroup(
                type = ToolGroupType.NETWORK_TOOLS,
                displayName = "网络工具",
                icon = "🌐",
                color = Color(0xFF00BCD4)
            )
        }
        
        else -> {
            ToolGroup(
                type = ToolGroupType.OTHER,
                displayName = "其他工具",
                icon = "🔧",
                color = Color(0xFF607D8B)
            )
        }
    }
}