package com.claudecodeplus.ui.models

import com.claudecodeplus.sdk.types.*

/**
 * 工具的 IDE 集成行为定义
 *
 * 定义工具在 IDEA 中的打开方式，使用密封接口保证类型安全
 */
sealed interface IdeIntegration {
    /** 是否要求工具状态为 SUCCESS 才能在 IDE 中打开 */
    val requiresSuccessStatus: Boolean

    /** 在 IDEA 中显示 Diff 对比 */
    data object ShowDiff : IdeIntegration {
        override val requiresSuccessStatus = true
    }

    /** 在 IDEA 中打开文件 */
    data object OpenFile : IdeIntegration {
        override val requiresSuccessStatus = true
    }
}

/**
 * 扩展属性：获取工具的 IDE 集成方式
 *
 * 返回 null 表示该工具不支持 IDE 集成（在 UI 中展开显示）
 */
val SpecificToolUse.ideIntegration: IdeIntegration?
    get() = when (this) {
        is EditToolUse, is MultiEditToolUse -> IdeIntegration.ShowDiff
        is ReadToolUse, is WriteToolUse -> IdeIntegration.OpenFile
        else -> null
    }

/**
 * 扩展函数：生成工具的简洁显示信息（用于 UI 的副标题）
 *
 * 返回 null 表示无需显示副标题
 */
fun SpecificToolUse.getDisplaySubtitle(): String? {
    return when (this) {
        is TodoWriteToolUse -> buildTodoSubtitle()
        is EditToolUse -> buildEditSubtitle()
        is MultiEditToolUse -> buildMultiEditSubtitle()
        is ReadToolUse -> buildReadSubtitle()
        is WriteToolUse -> buildWriteSubtitle()
        else -> buildGenericSubtitle()
    }
}

/**
 * 扩展函数：判断工具是否应该在 IDE 中打开（而不是在 UI 中展开）
 */
fun ToolCall.shouldOpenInIde(): Boolean {
    val integration = specificTool?.ideIntegration ?: return false

    // 如果要求成功状态，检查状态
    if (integration.requiresSuccessStatus) {
        return status == ToolCallStatus.SUCCESS
    }

    return true
}

// ====== 私有辅助函数：构建各工具的显示信息 ======

private fun TodoWriteToolUse.buildTodoSubtitle(): String {
    val total = todos.size
    val completed = todos.count { it.status.equals("completed", ignoreCase = true) }
    return "$completed / $total 已完成"
}

private fun EditToolUse.buildEditSubtitle(): String {
    val fileName = filePath.extractFileName()
    val editType = if (replaceAll) "替换全部" else "单次替换"
    return "$fileName ($editType)"
}

private fun MultiEditToolUse.buildMultiEditSubtitle(): String {
    val fileName = filePath.extractFileName()
    return "$fileName (${edits.size} 处修改)"
}

private fun ReadToolUse.buildReadSubtitle(): String {
    val fileName = filePath.extractFileName()
    val rangeInfo = buildRangeInfo()
    return if (rangeInfo.isEmpty()) fileName else "$fileName $rangeInfo"
}

private fun ReadToolUse.buildRangeInfo(): String {
    if (offset == null && limit == null) return ""

    return buildString {
        append("(")
        if (offset != null) append("offset: $offset")
        if (limit != null) {
            if (offset != null) append(", ")
            append("limit: $limit")
        }
        append(")")
    }
}

private fun WriteToolUse.buildWriteSubtitle(): String {
    return filePath.extractFileName()
}

private fun SpecificToolUse.buildGenericSubtitle(): String? {
    // 其他工具：显示关键参数摘要（前 100 个字符）
    val params = when (this) {
        is BashToolUse -> mapOf("command" to command)
        is GlobToolUse -> mapOf("pattern" to pattern)
        is GrepToolUse -> mapOf("pattern" to pattern)
        // 其他工具类型可以在这里添加
        else -> emptyMap()
    }

    if (params.isEmpty()) return null

    return params.entries
        .joinToString(" ") { "${it.key}=${it.value}" }
        .take(100)
}

// ====== 通用辅助函数 ======

/**
 * 从完整路径中提取文件名（兼容 Unix 和 Windows 路径）
 */
private fun String.extractFileName(): String {
    return substringAfterLast('/').substringAfterLast('\\')
}
