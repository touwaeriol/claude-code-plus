package com.asakii.plugin.types

/**
 * 工具详情基类
 */
sealed interface ToolDetail

/**
 * Edit 工具详情
 */
data class EditToolDetail(
    val filePath: String,
    val oldString: String,
    val newString: String,
    val replaceAll: Boolean = false
) : ToolDetail

/**
 * MultiEdit 工具详情
 */
data class MultiEditToolDetail(
    val filePath: String,
    val edits: List<EditOperation>
) : ToolDetail {
    data class EditOperation(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean = false
    )
}

/**
 * Read 工具详情
 */
data class ReadToolDetail(
    val filePath: String,
    val offset: Int? = null,
    val limit: Int? = null
) : ToolDetail

/**
 * Write 工具详情
 */
data class WriteToolDetail(
    val filePath: String
) : ToolDetail

/**
 * 工具调用视图模型（简化版）
 */
data class ToolCallViewModel(
    val toolDetail: ToolDetail?
)



