package com.claudecodeplus.ui.services

import com.claudecodeplus.ui.models.ToolCall

/**
 * IDE 集成接口
 * 提供与 IDE 平台的抽象集成层
 */
interface IdeIntegration {
    
    /**
     * 处理工具点击事件
     * 
     * @param toolCall 工具调用信息
     * @return true 表示已处理，false 表示使用默认行为
     */
    fun handleToolClick(toolCall: ToolCall): Boolean
    
    /**
     * 打开文件
     * 
     * @param filePath 文件路径
     * @param line 行号（可选）
     * @param column 列号（可选）
     * @return true 表示成功打开
     */
    fun openFile(filePath: String, line: Int? = null, column: Int? = null): Boolean
    
    /**
     * 显示文件差异
     * 
     * @param filePath 文件路径
     * @param oldContent 原始内容
     * @param newContent 新内容
     * @return true 表示成功显示
     */
    fun showDiff(filePath: String, oldContent: String, newContent: String): Boolean
    
    /**
     * 显示通知
     * 
     * @param message 通知消息
     * @param type 通知类型
     */
    fun showNotification(message: String, type: NotificationType = NotificationType.INFO)
    
    /**
     * 检查是否支持 IDE 集成
     */
    fun isSupported(): Boolean
}

/**
 * 通知类型
 */
enum class NotificationType {
    INFO,
    WARNING,
    ERROR
}

/**
 * 空实现（桌面环境使用）
 */
class NoOpIdeIntegration : IdeIntegration {
    override fun handleToolClick(toolCall: ToolCall): Boolean = false
    override fun openFile(filePath: String, line: Int?, column: Int?): Boolean = false
    override fun showDiff(filePath: String, oldContent: String, newContent: String): Boolean = false
    override fun showNotification(message: String, type: NotificationType) {}
    override fun isSupported(): Boolean = false
}