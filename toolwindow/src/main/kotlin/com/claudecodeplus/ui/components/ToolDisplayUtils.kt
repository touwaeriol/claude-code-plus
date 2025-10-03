package com.claudecodeplus.ui.jewel.components.tools

import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.viewmodels.tool.BashToolDetail
import com.claudecodeplus.ui.viewmodels.tool.GenericToolDetail
import com.claudecodeplus.ui.viewmodels.tool.GlobToolDetail
import com.claudecodeplus.ui.viewmodels.tool.GrepToolDetail
import com.claudecodeplus.ui.viewmodels.tool.MultiEditToolDetail
import com.claudecodeplus.ui.viewmodels.tool.NotebookEditToolDetail
import com.claudecodeplus.ui.viewmodels.tool.ReadToolDetail
import com.claudecodeplus.ui.viewmodels.tool.TaskToolDetail
import com.claudecodeplus.ui.viewmodels.tool.ToolDetailViewModel
import com.claudecodeplus.ui.viewmodels.tool.TodoWriteToolDetail
import com.claudecodeplus.ui.viewmodels.tool.UiToolType
import com.claudecodeplus.ui.viewmodels.tool.WebFetchToolDetail
import com.claudecodeplus.ui.viewmodels.tool.WebSearchToolDetail
import com.claudecodeplus.ui.viewmodels.tool.WriteToolDetail

/**
 * 工具显示相关的辅助方法
 */

fun getPrimaryParamValue(toolCall: ToolCall): String? {
    val detail = toolCall.viewModel?.toolDetail ?: return null

    return when (detail) {
        is ReadToolDetail -> detail.filePath
        is WriteToolDetail -> detail.filePath
        is MultiEditToolDetail -> detail.filePath
        is GlobToolDetail -> detail.pattern
        is GrepToolDetail -> detail.pattern
        is BashToolDetail -> detail.command
        is WebFetchToolDetail -> detail.url
        is WebSearchToolDetail -> detail.query
        is NotebookEditToolDetail -> detail.notebookPath
        is TaskToolDetail -> detail.description
        is TodoWriteToolDetail -> detail.generateSubtitle()
        is GenericToolDetail -> detail.parameters.entries.firstOrNull()?.let { "${it.key}=${it.value}" }
        else -> when (detail.toolType) {
            UiToolType.EXIT_PLAN_MODE -> detail.generateSubtitle()
            else -> null
        }
    }
}
