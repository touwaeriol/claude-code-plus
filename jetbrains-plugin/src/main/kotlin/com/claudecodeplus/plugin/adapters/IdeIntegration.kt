package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.plugin.types.ToolCallItem
import com.claudecodeplus.plugin.types.LegacyToolCall
import java.util.*

/**
 * IDE 集成接口
 * 定义 IDE 相关的操作接口
 */
interface IdeIntegration {
    /**
     * 处理工具点击事件
     */
    fun handleToolClick(toolCall: LegacyToolCall): Boolean
    
    /**
     * 打开文件
     */
    fun openFile(filePath: String, line: Int? = null, column: Int? = null): Boolean
    
    /**
     * 显示文件差异
     */
    fun showDiff(filePath: String, oldContent: String, newContent: String): Boolean
    
    /**
     * 显示通知
     */
    fun showNotification(message: String, type: NotificationType)
    
    /**
     * 是否支持
     */
    fun isSupported(): Boolean
    
    /**
     * 获取 IDE 语言设置
     */
    fun getIdeLocale(): Locale
}

/**
 * 通知类型
 */
enum class NotificationType {
    INFO,
    WARNING,
    ERROR
}

