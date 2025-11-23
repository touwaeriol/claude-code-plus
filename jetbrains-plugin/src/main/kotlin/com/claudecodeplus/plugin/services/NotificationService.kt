package com.claudecodeplus.plugin.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * 通知服务
 * 
 * 使用 IntelliJ Notification API 处理后台任务通知
 */
class NotificationService(private val project: Project) {
    
    companion object {
        private const val NOTIFICATION_GROUP_ID = "Claude Code Plus Notifications"
    }
    
    /**
     * 显示信息通知
     */
    fun showInfo(title: String, content: String = "") {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }
    
    /**
     * 显示警告通知
     */
    fun showWarning(title: String, content: String = "") {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.WARNING)
            .notify(project)
    }
    
    /**
     * 显示错误通知
     */
    fun showError(title: String, content: String = "") {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }
    
    /**
     * 显示工具调用需要批准的通知
     */
    fun notifyToolApprovalNeeded(toolName: String, onApprove: () -> Unit, onReject: () -> Unit) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "工具调用需要批准",
                "Claude 请求执行工具: $toolName",
                NotificationType.INFORMATION
            )
        
        notification.addAction(object : com.intellij.openapi.actionSystem.AnAction("批准") {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                onApprove()
                notification.expire()
            }
        })
        
        notification.addAction(object : com.intellij.openapi.actionSystem.AnAction("拒绝") {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                onReject()
                notification.expire()
            }
        })
        
        notification.notify(project)
    }
    
    /**
     * 显示文件编辑成功通知
     */
    fun notifyFileEdited(filePath: String) {
        showInfo("文件已编辑", filePath)
    }
    
    /**
     * 显示后台任务完成通知
     */
    fun notifyTaskCompleted(taskName: String) {
        showInfo("任务完成", taskName)
    }
    
    /**
     * 显示后台任务失败通知
     */
    fun notifyTaskFailed(taskName: String, error: String) {
        showError("任务失败", "$taskName: $error")
    }
}


