package com.claudecodeplus.plugin.listeners

import com.intellij.util.messages.Topic

/**
 * 工具窗口状态变化事件主题
 * 
 * 用于在工具窗口显示/隐藏时通知UI组件刷新状态
 */
interface ToolWindowStateChangedListener {
    /**
     * 工具窗口状态变化时调用
     * 
     * @param isVisible 工具窗口是否可见
     */
    fun onToolWindowStateChanged(isVisible: Boolean)
}

/**
 * 工具窗口状态变化主题
 */
object ToolWindowStateChangedTopic {
    @JvmField
    val TOPIC = Topic.create(
        "ClaudeCodePlus.ToolWindowStateChanged",
        ToolWindowStateChangedListener::class.java,
        Topic.BroadcastDirection.TO_CHILDREN
    )
}