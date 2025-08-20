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
import java.awt.event.MouseEvent
import javax.swing.Icon
import com.intellij.icons.AllIcons

/**
 * 状态栏 Widget - 显示 Claude 状态和快速访问
 */
class ClaudeStatusBarWidget(private val project: Project) : StatusBarWidget {
    
    override fun ID(): String = "ClaudeCodePlus"
    
    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return ClaudePresentation()
    }
    
    override fun install(statusBar: StatusBar) {
        // Widget 安装时的初始化
    }
    
    override fun dispose() {
        // 清理资源
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
        // TODO: 从服务获取实际的 Token 使用情况
        return TokenUsage("2.5k", "200k", 2500, 200000)
    }
    
    private fun getActiveSessionCount(): Int {
        // TODO: 从服务获取活动会话数
        return 3
    }
    
    private fun getConnectionStatus(): ConnectionStatus {
        // TODO: 从服务获取连接状态
        return ConnectionStatus.CONNECTED
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
            // 显示详细状态信息
        }
    }
    
    private inner class OpenToolWindowAction : AnAction("打开主窗口") {
        override fun actionPerformed(e: AnActionEvent) {
            openToolWindow()
        }
    }
    
    private inner class NewSessionAction : AnAction("新建会话") {
        override fun actionPerformed(e: AnActionEvent) {
            // 创建新会话
        }
    }
    
    private inner class ViewHistoryAction : AnAction("查看历史") {
        override fun actionPerformed(e: AnActionEvent) {
            // 打开历史记录
        }
    }
    
    private inner class OpenSettingsAction : AnAction("设置...") {
        override fun actionPerformed(e: AnActionEvent) {
            // 打开设置
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