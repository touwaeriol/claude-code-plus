package com.claudecodeplus.plugin.ui.display

import com.claudecodeplus.plugin.types.*
import com.claudecodeplus.plugin.ui.tools.ToolDisplayFactory
import com.claudecodeplus.server.tools.IdeTools
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * DisplayItem 渲染器
 * 
 * 对应 frontend/src/components/chat/DisplayItemRenderer.vue
 * 根据 DisplayItem 类型分发到不同的展示组件
 */
class DisplayItemRenderer(
    private val item: DisplayItem,
    private val ideTools: IdeTools
) {
    
    /**
     * 创建对应的展示组件
     */
    fun create(): JComponent {
        return when (item) {
            is UserMessageItem -> UserMessageDisplay(item, ideTools).create()
            is AssistantTextItem -> AssistantTextDisplay(item, ideTools).create()
            is ToolCallItem -> ToolDisplayFactory.create(item, ideTools)
            is SystemMessageItem -> SystemMessageDisplay(item, ideTools).create()
        }
    }
}


