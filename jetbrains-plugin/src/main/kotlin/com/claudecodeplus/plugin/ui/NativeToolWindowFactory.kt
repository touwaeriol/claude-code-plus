package com.claudecodeplus.plugin.ui

import com.claudecodeplus.plugin.actions.NewSessionAction
import com.claudecodeplus.plugin.actions.ShowHistoryAction
import com.claudecodeplus.plugin.server.HttpServerProjectService
import com.claudecodeplus.plugin.tools.IdeToolsImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Native工具窗口工厂（使用Swing UI）
 * 
 * 这是新的工具窗口实现，使用Swing而不是JCEF+Vue
 * 直接调用IdeTools接口，不通过HTTP
 * 
 * 工具栏放在 ToolWindow 边框上（标题栏）
 */
class NativeToolWindowFactory : ToolWindowFactory, DumbAware {
    
    companion object {
        private val logger = Logger.getInstance(NativeToolWindowFactory::class.java)
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        logger.info("🚀 Creating Native Tool Window (Swing UI)")
        
        try {
            // 创建IdeTools实例
            val ideTools = IdeToolsImpl(project)
            
            // 创建聊天面板 (使用 DisplayItem 架构)
            val chatPanel = ChatPanel(project, ideTools)
            val uiComponent = chatPanel.createUI()
            
            // 创建内容并添加到工具窗口
            val contentFactory = ContentFactory.getInstance()
            val content = contentFactory.createContent(uiComponent, "", false)
            toolWindow.contentManager.addContent(content)
            
            // ✅ 将工具栏添加到 ToolWindow 标题栏
            setupToolWindowToolbar(project, toolWindow)
            
            logger.info("✅ Native Tool Window created successfully (with DisplayItem & StreamEvent)")
        } catch (e: Exception) {
            logger.error("❌ Failed to create Native Tool Window", e)
            
            // 显示错误面板
            val errorPanel = javax.swing.JPanel(java.awt.BorderLayout())
            val errorLabel = javax.swing.JLabel(
                "<html><center>" +
                "<h2>Claude Code Plus</h2>" +
                "<p style='color:red'>初始化失败: ${e.message}</p>" +
                "</center></html>",
                javax.swing.SwingConstants.CENTER
            )
            errorPanel.add(errorLabel, java.awt.BorderLayout.CENTER)
            
            val contentFactory = ContentFactory.getInstance()
            val content = contentFactory.createContent(errorPanel, "Error", false)
            toolWindow.contentManager.addContent(content)
        }
    }
    
    /**
     * 设置 ToolWindow 标题栏工具栏
     * 
     * 布局：[Claude AI | 🌐 http://localhost:8765]  [右对齐：➕ 新会话 | 📋 会话历史]
     */
    private fun setupToolWindowToolbar(project: Project, toolWindow: ToolWindow) {
        val titlePanel = JPanel(BorderLayout())
        titlePanel.border = JBUI.Borders.empty(4, 8)
        titlePanel.background = JBColor.PanelBackground
        
        // 左侧：标题 + 服务器端口指示器
        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.X_AXIS)
        leftPanel.isOpaque = false
        
        // 标题
        val titleLabel = JBLabel("Claude AI")
        titleLabel.font = JBUI.Fonts.label(14f).deriveFont(java.awt.Font.BOLD)
        leftPanel.add(titleLabel)
        
        leftPanel.add(Box.createHorizontalStrut(JBUI.scale(16)))
        
        // 服务器端口指示器
        val serverIndicator = createServerPortIndicator(project)
        leftPanel.add(serverIndicator)
        
        titlePanel.add(leftPanel, BorderLayout.WEST)
        
        // 右侧：Action 工具栏
        val actionGroup = DefaultActionGroup()
        actionGroup.add(NewSessionAction())
        actionGroup.add(ShowHistoryAction())
        
        val actionManager = ActionManager.getInstance()
        val toolbar = actionManager.createActionToolbar(
            "ClaudeCodePlus.ToolWindow",
            actionGroup,
            true
        )
        toolbar.targetComponent = titlePanel
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
        
        val toolbarComponent = toolbar.component
        titlePanel.add(toolbarComponent, BorderLayout.EAST)
        
        // 设置标题组件
        toolWindow.setTitleComponent(titlePanel)
    }
    
    /**
     * 创建服务器端口指示器
     */
    private fun createServerPortIndicator(project: Project): JBLabel {
        val httpService = HttpServerProjectService.getInstance(project)
        val serverUrl = httpService.serverUrl ?: "未启动"
        
        val label = JBLabel("🌐 $serverUrl")
        label.font = JBUI.Fonts.smallFont()
        label.foreground = JBColor(Color(0x2196F3), Color(0x42A5F5))
        label.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        label.toolTipText = "<html>HTTP 服务器地址<br>单击：复制地址<br>双击：在浏览器中打开 Vue 前端</html>"
        
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    // 单击：复制到剪贴板
                    CopyPasteManager.getInstance().setContents(StringSelection(serverUrl))
                    Messages.showInfoMessage(project, "已复制到剪贴板：$serverUrl", "复制成功")
                } else if (e.clickCount == 2) {
                    // 双击：在浏览器中打开
                    openInBrowser(project, serverUrl)
                }
            }
            
            override fun mouseEntered(e: MouseEvent) {
                label.foreground = JBColor(Color(0x1976D2), Color(0x64B5F6))
            }
            
            override fun mouseExited(e: MouseEvent) {
                label.foreground = JBColor(Color(0x2196F3), Color(0x42A5F5))
            }
        })
        
        return label
    }
    
    /**
     * 在浏览器中打开URL
     */
    private fun openInBrowser(project: Project, url: String) {
        try {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
            } else {
                Messages.showErrorDialog(project, "无法打开浏览器", "错误")
            }
        } catch (e: IOException) {
            Messages.showErrorDialog(project, "打开浏览器失败: ${e.message}", "错误")
        }
    }
    
    override fun init(toolWindow: ToolWindow) {
        toolWindow.stripeTitle = "Claude AI"
    }
    
    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}

