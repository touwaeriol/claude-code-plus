package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolType
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * 智能工具调用展示组件
 * 按照新需求：简洁直观，每个工具调用独立显示，不进行分组
 * FR-2.14: 工具调用必须直接展示，不使用分组标题或计数显示
 */
@Composable
fun SmartToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    // 如果没有工具调用，不显示任何内容
    if (toolCalls.isEmpty()) return
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 按照需求FR-2.14，去掉分组标题，直接显示每个工具调用
        toolCalls.forEach { toolCall ->
            when {
                // TodoWrite 工具特殊展示
                toolCall.name.contains("TodoWrite", ignoreCase = true) -> {
                    EnhancedTodoDisplay(
                        toolCall = toolCall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 其他所有工具都使用统一的紧凑展示
                else -> {
                    CompactToolCallDisplay(
                        toolCalls = listOf(toolCall),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}


/**
 * 单参数工具列表
 */
val SINGLE_PARAM_TOOLS = setOf(
    "Read", "LS", "Write", "Glob", "ExitPlanMode",
    "WebFetch", "NotebookRead"
)

/**
 * 主要参数名映射
 */
val PRIMARY_PARAM_MAPPING = mapOf(
    "Read" to "file_path",
    "Write" to "file_path",
    "LS" to "path",
    "Glob" to "pattern",
    "WebFetch" to "url",
    "Bash" to "command",
    "Search" to "pattern",
    "Grep" to "pattern",
    "Edit" to "file_path",
    "MultiEdit" to "file_path",
    "NotebookRead" to "notebook_path",
    "NotebookEdit" to "notebook_path"
)

/**
 * 判断是否为单参数工具
 */
fun isSingleParamTool(toolName: String): Boolean {
    return SINGLE_PARAM_TOOLS.any { toolName.contains(it, ignoreCase = true) }
}

/**
 * 获取工具的主要参数值
 */
fun getPrimaryParamValue(toolCall: ToolCall): String? {
    // 首先检查是否有映射的主要参数
    val primaryParam = PRIMARY_PARAM_MAPPING.entries
        .firstOrNull { toolCall.name.contains(it.key, ignoreCase = true) }
        ?.value
    
    if (primaryParam != null) {
        return toolCall.parameters[primaryParam]?.toString()
    }
    
    // 如果是单参数工具且只有一个参数，直接返回该参数值
    if (isSingleParamTool(toolCall.name) && toolCall.parameters.size == 1) {
        return toolCall.parameters.values.firstOrNull()?.toString()
    }
    
    return null
}

/**
 * 格式化工具调用的简要信息
 */
fun formatToolBriefInfo(toolCall: ToolCall): String {
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
        
        // Edit/MultiEdit 显示修改数量
        toolCall.name.contains("Edit", ignoreCase = true) -> {
            val editsCount = toolCall.parameters["edits"]?.let {
                if (it is List<*>) it.size else 1
            } ?: 1
            val fileName = primaryValue?.substringAfterLast('/')?.substringAfterLast('\\') ?: ""
            "$fileName ($editsCount changes)"
        }
        
        // Search/Grep 显示搜索模式
        toolCall.name.contains("Search", ignoreCase = true) ||
        toolCall.name.contains("Grep", ignoreCase = true) -> {
            val pattern = toolCall.parameters["pattern"] ?: toolCall.parameters["query"] ?: ""
            val glob = toolCall.parameters["glob"]?.toString()?.let { " in $it" } ?: ""
            "\"$pattern\"$glob"
        }
        
        // TodoWrite 显示任务信息
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