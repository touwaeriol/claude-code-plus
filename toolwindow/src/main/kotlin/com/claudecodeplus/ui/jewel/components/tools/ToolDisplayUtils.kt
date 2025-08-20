package com.claudecodeplus.ui.jewel.components.tools

import com.claudecodeplus.ui.models.ToolCall

/**
 * 工具显示相关的工具函数
 */

/**
 * 单参数工具列表
 */
val SINGLE_PARAM_TOOLS = setOf(
    "Read", "LS", "Write", "Glob", "ExitPlanMode",
    "WebFetch", "NotebookRead", "Bash"
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