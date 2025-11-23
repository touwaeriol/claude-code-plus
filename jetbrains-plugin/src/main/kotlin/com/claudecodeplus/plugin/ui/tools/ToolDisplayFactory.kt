package com.claudecodeplus.plugin.ui.tools

import com.claudecodeplus.plugin.types.*
import com.claudecodeplus.server.tools.IdeTools
import java.awt.BorderLayout
import javax.swing.*

/**
 * 工具展示组件工厂
 * 
 * 根据 ToolCallItem 类型创建对应的专用展示组件
 */
object ToolDisplayFactory {
    
    /**
     * 创建工具展示组件
     */
    fun create(toolCall: ToolCallItem, ideTools: IdeTools): JComponent {
        return when (toolCall) {
            is ReadToolCall -> ReadToolDisplay(toolCall, ideTools).create()
            is WriteToolCall -> WriteToolDisplay(toolCall, ideTools).create()
            is EditToolCall -> EditToolDisplay(toolCall, ideTools).create()
            is MultiEditToolCall -> MultiEditToolDisplay(toolCall, ideTools).create()
            is BashToolCall -> BashToolDisplay(toolCall, ideTools).create()
            is GrepToolCall -> GrepToolDisplay(toolCall, ideTools).create()
            is GlobToolCall -> GlobToolDisplay(toolCall, ideTools).create()
            is WebSearchToolCall -> WebSearchToolDisplay(toolCall, ideTools).create()
            is WebFetchToolCall -> WebFetchToolDisplay(toolCall, ideTools).create()
            is TodoWriteToolCall -> TodoWriteToolDisplay(toolCall, ideTools).create()
            is TaskToolCall -> TaskToolDisplay(toolCall, ideTools).create()
            is NotebookEditToolCall -> NotebookEditToolDisplay(toolCall, ideTools).create()
            is BashOutputToolCall -> BashOutputToolDisplay(toolCall, ideTools).create()
            is KillShellToolCall -> KillShellToolDisplay(toolCall, ideTools).create()
            is ExitPlanModeToolCall -> ExitPlanModeToolDisplay(toolCall, ideTools).create()
            is AskUserQuestionToolCall -> AskUserQuestionToolDisplay(toolCall, ideTools).create()
            is SkillToolCall -> SkillToolDisplay(toolCall, ideTools).create()
            is SlashCommandToolCall -> SlashCommandToolDisplay(toolCall, ideTools).create()
            is ListMcpResourcesToolCall -> ListMcpResourcesToolDisplay(toolCall, ideTools).create()
            is ReadMcpResourceToolCall -> ReadMcpResourceToolDisplay(toolCall, ideTools).create()
            is GenericToolCall -> GenericToolDisplay(toolCall, ideTools).create()
        }
    }
}

/**
 * 通用工具展示组件（用于未知工具）
 */
class GenericToolDisplay(
    toolCall: ToolCallItem,
    ideTools: IdeTools
) : BaseToolDisplay(toolCall, ideTools) {
    
    override fun create(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(createToolHeader(), BorderLayout.NORTH)
        
        // 显示输入参数
        val inputs = toolCall.input.map { (k, v) -> k to (v?.toString() ?: "") }
        createInputPanel(inputs)?.let {
            panel.add(it, BorderLayout.CENTER)
        }
        
        // 显示结果
        createResultPanel()?.let {
            panel.add(it, BorderLayout.SOUTH)
        }
        
        return panel
    }
}

