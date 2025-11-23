package com.claudecodeplus.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Consumer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.ui.Messages
import com.claudecodeplus.plugin.services.ChatSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.Timer
import com.intellij.icons.AllIcons

/**
 * 状态栏 Widget - 显示 Claude 状态和快速访问
 */
class ClaudeStatusBarWidget(private val project: Project) : StatusBarWidget {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val chatSessionService = ChatSessionService.getInstance(project)
    private val sessionManager = SessionManager.getInstance(project)
    private var statusBar: StatusBar? = null
    
    // 定时更新状态
    private val updateTimer = Timer(1000) {
        statusBar?.updateWidget(ID())
    }
    
    override fun ID(): String = "ClaudeCodePlus"
    
    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return ClaudePresentation()
    }
    
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // 启动定时更新
        updateTimer.start()
    }
    
    override fun dispose() {
        updateTimer.stop()
        statusBar = null
    }
    
    private inner class ClaudePresentation : StatusBarWidget.MultipleTextValuesPresentation {
        
        override fun getSelectedValue(): String? {
            return getStatusText()
        }
        
        override fun getIcon(): Icon? {
            return AllIcons.General.BalloonInformation // 使用默认图标，实际应使用 Claude 图标
        }
        
        override fun getTooltipText(): String {
            return buildString {
                appendLine("Claude Code Plus")
                appendLine("Token 使用: ${getTokenUsage()}")
                appendLine("活动会话: ${getActiveSessionCount()}")
                appendLine("点击查看更多选项")
            }
        }
        
        override fun getPopupStep(): ListPopup? {
            val group = createPopupActionGroup()
            val context = com.intellij.ide.DataManager.getInstance().getDataContext()
            
            return JBPopupFactory.getInstance()
                .createActionGroupPopup(
                    "Claude Code Plus",
                    group,
                    context,
                    JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    false
                )
        }
        
        override fun getClickConsumer(): Consumer<MouseEvent>? {
            return Consumer { event ->
                // 左键点击显示弹出菜单
                if (event.button == MouseEvent.BUTTON1) {
                    getPopupStep()?.showInCenterOf(event.component)
                }
                // 右键点击直接打开工具窗口
                else if (event.button == MouseEvent.BUTTON3) {
                    openToolWindow()
                }
            }
        }
        
        private fun getStatusText(): String {
            val usage = getTokenUsage()
            val status = getConnectionStatus()
            
            return when (status) {
                ConnectionStatus.CONNECTED -> "🤖 Claude: ${usage.used}/${usage.total}"
                ConnectionStatus.PROCESSING -> "🤖 Claude: 处理中..."
                ConnectionStatus.DISCONNECTED -> "🤖 Claude: 离线"
                ConnectionStatus.ERROR -> "🤖 Claude: 错误"
            }
        }
        
        private fun createPopupActionGroup(): ActionGroup {
            return object : ActionGroup("Claude Actions", true) {
                override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                    return arrayOf(
                        StatusInfoAction(),
                        Separator.getInstance(),
                        OpenToolWindowAction(),
                        NewSessionAction(),
                        ViewHistoryAction(),
                        Separator.getInstance(),
                        OpenSettingsAction()
                    )
                }
            }
        }
    }
    
    private fun getTokenUsage(): TokenUsage {
        // 从 ChatViewModel 获取实际的 Token 使用情况
        val viewModel = chatSessionService.getActiveChatViewModel()
        // TODO: 实现实际的 token 统计
        // 目前返回占位符数据
        return TokenUsage("0", "200k", 0, 200000)
    }
    
    private fun getActiveSessionCount(): Int {
        return sessionManager.getAllSessions().size
    }
    
    private fun getConnectionStatus(): ConnectionStatus {
        val viewModel = chatSessionService.getActiveChatViewModel()
        
        return when {
            viewModel == null -> ConnectionStatus.DISCONNECTED
            viewModel.isConnected.value && viewModel.isStreaming.value -> ConnectionStatus.PROCESSING
            viewModel.isConnected.value -> ConnectionStatus.CONNECTED
            else -> ConnectionStatus.DISCONNECTED
        }
    }
    
    private fun openToolWindow() {
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("ClaudeCodePlus")
        toolWindow?.show()
    }
    
    // 数据类
    data class TokenUsage(
        val used: String,
        val total: String,
        val usedNum: Int,
        val totalNum: Int
    )
    
    enum class ConnectionStatus {
        CONNECTED, PROCESSING, DISCONNECTED, ERROR
    }
    
    // Action 实现
    private inner class StatusInfoAction : AnAction("状态信息") {
        override fun actionPerformed(e: AnActionEvent) {
            val status = getConnectionStatus()
            val sessions = getActiveSessionCount()
            val usage = getTokenUsage()
            
            val message = buildString {
                appendLine("连接状态: ${getStatusDisplayName(status)}")
                appendLine("活动会话数: $sessions")
                appendLine("Token 使用: ${usage.used} / ${usage.total}")
                appendLine()
                appendLine("会话列表:")
                sessionManager.getAllSessions().forEachIndexed { index, session ->
                    val marker = if (session.id == sessionManager.getCurrentSessionId()) "●" else "○"
                    appendLine("  $marker ${session.name} (${session.messageCount} 条消息)")
                }
            }
            
            Messages.showInfoMessage(project, message, "Claude Code Plus 状态")
        }
        
        private fun getStatusDisplayName(status: ConnectionStatus): String {
            return when (status) {
                ConnectionStatus.CONNECTED -> "已连接"
                ConnectionStatus.PROCESSING -> "处理中"
                ConnectionStatus.DISCONNECTED -> "未连接"
                ConnectionStatus.ERROR -> "错误"
            }
        }
    }
    
    private inner class OpenToolWindowAction : AnAction("打开主窗口") {
        override fun actionPerformed(e: AnActionEvent) {
            openToolWindow()
        }
    }
    
    private inner class NewSessionAction : AnAction("新建会话") {
        override fun actionPerformed(e: AnActionEvent) {
            // 弹出对话框让用户输入会话名称
            val sessionName = Messages.showInputDialog(
                project,
                "请输入新会话的名称:",
                "新建会话",
                Messages.getQuestionIcon()
            )
            
            if (!sessionName.isNullOrBlank()) {
                sessionManager.createSession(sessionName)
                
                // 打开工具窗口
                openToolWindow()
                
                Messages.showInfoMessage(
                    project,
                    "会话 \"$sessionName\" 已创建",
                    "成功"
                )
            }
        }
    }
    
    private inner class ViewHistoryAction : AnAction("查看历史") {
        override fun actionPerformed(e: AnActionEvent) {
            val sessions = sessionManager.getAllSessions()
            
            if (sessions.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "没有历史会话",
                    "会话历史"
                )
                return
            }
            
            val sessionList = sessions.joinToString("\n") { session ->
                val marker = if (session.id == sessionManager.getCurrentSessionId()) "●" else "○"
                val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(session.lastActiveAt)
                "$marker ${session.name}\n   创建于: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(session.createdAt)}\n   最后活跃: $date\n   消息数: ${session.messageCount}"
            }
            
            Messages.showInfoMessage(
                project,
                sessionList,
                "会话历史"
            )
        }
    }
    
    private inner class OpenSettingsAction : AnAction("设置...") {
        override fun actionPerformed(e: AnActionEvent) {
            // 打开设置对话框
            Messages.showInfoMessage(
                project,
                "设置面板将在后续任务中实现",
                "提示"
            )
        }
    }
}

/**
 * 状态栏 Widget 工厂
 */
class ClaudeStatusBarWidgetFactory : StatusBarWidgetFactory {
    
    override fun getId(): String = "ClaudeCodePlus"
    
    override fun getDisplayName(): String = "Claude Code Plus"
    
    override fun isAvailable(project: Project): Boolean = true
    
    override fun createWidget(project: Project): StatusBarWidget {
        return ClaudeStatusBarWidget(project)
    }
    
    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }
    
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}