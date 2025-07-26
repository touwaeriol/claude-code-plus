package com.claudecodeplus.ui.jewel.components.tools

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.models.ToolCallStatus
import com.claudecodeplus.ui.models.ToolType

/**
 * 智能工具调用展示组件
 * 根据工具类型和参数数量智能选择展示方式
 */
@Composable
fun SmartToolCallDisplay(
    toolCalls: List<ToolCall>,
    modifier: Modifier = Modifier
) {
    // 对工具调用进行智能分组
    val groupedCalls = remember(toolCalls) {
        groupToolCalls(toolCalls)
    }
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 如果有TodoWrite工具，优先展示
        val todoWriteCalls = toolCalls.filter { it.name.contains("TodoWrite", ignoreCase = true) }
        if (todoWriteCalls.isNotEmpty()) {
            todoWriteCalls.forEach { toolCall ->
                EnhancedTodoDisplay(toolCall)
            }
        }
        
        // 展示其他工具调用
        val otherCalls = toolCalls.filterNot { it.name.contains("TodoWrite", ignoreCase = true) }
        if (otherCalls.isNotEmpty()) {
            // 如果工具数量较多，使用分组展示
            if (otherCalls.size > 5) {
                ToolGroupDisplay(otherCalls)
            } else {
                // 否则使用紧凑展示
                CompactToolCallDisplay(otherCalls)
            }
        }
    }
}

/**
 * 对工具调用进行分组
 */
private fun groupToolCalls(toolCalls: List<ToolCall>): Map<ToolType, List<ToolCall>> {
    return toolCalls.groupBy { toolCall ->
        when {
            toolCall.name.contains("Read", ignoreCase = true) ||
            toolCall.name.contains("Write", ignoreCase = true) ||
            toolCall.name.contains("Edit", ignoreCase = true) ||
            toolCall.name.contains("LS", ignoreCase = true) -> ToolType.READ_FILE
            
            toolCall.name.contains("Search", ignoreCase = true) ||
            toolCall.name.contains("Grep", ignoreCase = true) ||
            toolCall.name.contains("Glob", ignoreCase = true) -> ToolType.SEARCH_FILES
            
            toolCall.name.contains("Bash", ignoreCase = true) ||
            toolCall.name.contains("Task", ignoreCase = true) ||
            toolCall.name.contains("ExitPlanMode", ignoreCase = true) -> ToolType.RUN_COMMAND
            
            toolCall.name.contains("TodoWrite", ignoreCase = true) -> ToolType.OTHER
            
            toolCall.name.contains("Git", ignoreCase = true) -> ToolType.GIT_OPERATION
            
            toolCall.name.contains("Web", ignoreCase = true) -> ToolType.WEB_SEARCH
            
            else -> ToolType.OTHER
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