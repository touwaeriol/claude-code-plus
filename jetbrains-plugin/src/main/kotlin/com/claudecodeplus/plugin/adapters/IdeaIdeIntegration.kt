package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.plugin.handlers.ToolClickManager
import com.claudecodeplus.plugin.handlers.ToolClickConfig
import com.claudecodeplus.ui.models.ToolCall
import com.claudecodeplus.ui.services.IdeIntegration
import com.claudecodeplus.ui.services.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger

/**
 * IntelliJ IDEA 的 IDE 集成实现
 */
class IdeaIdeIntegration(
    private val project: Project
) : IdeIntegration {
    
    companion object {
        private val logger = Logger.getInstance(IdeaIdeIntegration::class.java)
    }
    
    override fun handleToolClick(toolCall: ToolCall): Boolean {
        return try {
            ToolClickManager.handleToolClick(toolCall, project, ToolClickConfig())
        } catch (e: Exception) {
            logger.error("处理工具点击失败", e)
            false
        }
    }
    
    override fun openFile(filePath: String, line: Int?, column: Int?): Boolean {
        return try {
            // 创建一个临时的 Read 工具调用
            val fakeToolCall = ToolCall(
                id = "temp_read",
                name = "Read",
                parameters = mutableMapOf<String, Any>().apply {
                    put("file_path", filePath)
                    line?.let { put("offset", it) }
                },
                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
            )
            
            handleToolClick(fakeToolCall)
        } catch (e: Exception) {
            logger.error("打开文件失败", e)
            false
        }
    }
    
    override fun showDiff(filePath: String, oldContent: String, newContent: String): Boolean {
        return try {
            // 创建一个临时的 Edit 工具调用
            val fakeToolCall = ToolCall(
                id = "temp_edit",
                name = "Edit",
                parameters = mutableMapOf<String, Any>().apply {
                    put("file_path", filePath)
                    put("old_string", oldContent)
                    put("new_string", newContent)
                },
                status = com.claudecodeplus.ui.models.ToolCallStatus.SUCCESS
            )
            
            handleToolClick(fakeToolCall)
        } catch (e: Exception) {
            logger.error("显示差异失败", e)
            false
        }
    }
    
    override fun showNotification(message: String, type: NotificationType) {
        try {
            val intellijType = when (type) {
                NotificationType.INFO -> com.intellij.notification.NotificationType.INFORMATION
                NotificationType.WARNING -> com.intellij.notification.NotificationType.WARNING
                NotificationType.ERROR -> com.intellij.notification.NotificationType.ERROR
            }
            
            val notificationGroup = com.intellij.notification.NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Code Plus")
            
            val notification = notificationGroup.createNotification(
                "Claude Code Plus",
                message,
                intellijType
            )
            
            com.intellij.notification.Notifications.Bus.notify(notification, project)
        } catch (e: Exception) {
            logger.warn("显示通知失败", e)
        }
    }
    
    override fun isSupported(): Boolean = true
}