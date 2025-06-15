package com.claudecodeplus.ui

import com.claudecodeplus.model.SelectionOption
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

/**
 * 选择列表单元格渲染器
 */
class SelectionListCellRenderer : ColoredListCellRenderer<SelectionOption>() {
    override fun customizeCellRenderer(
        list: JList<out SelectionOption>,
        value: SelectionOption?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        value?.let { option ->
            // 显示编号
            append("${index + 1}. ", SimpleTextAttributes.GRAY_ATTRIBUTES)
            
            // 显示文本
            append(option.label.trim())
        }
    }
}