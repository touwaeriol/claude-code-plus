package com.claudecodeplus.plugin.ui.components

import com.claudecodeplus.server.HttpServerProjectService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.IOException
import javax.swing.Box
import javax.swing.BoxLayout

/**
 * 聊天工具栏
 * 
 * 布局：
 * [Claude AI | 🌐 http://localhost:8765]  [右对齐：➕ 新会话 | 📋 会话历史]
 */
class ChatToolbar(
    private val project: Project,
    private val onNewSession: () -> Unit,
    private val onShowHistory: () -> Unit
) {
    
    fun create(): JBPanel<JBPanel<*>> {
        val toolbar = JBPanel<JBPanel<*>>(BorderLayout())
        toolbar.background = JBColor.PanelBackground
        toolbar.border = JBUI.Borders.empty(8, 12)
        
        // 左侧：标题 + 服务器端口
        val leftPanel = JBPanel<JBPanel<*>>()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.X_AXIS)
        leftPanel.isOpaque = false
        
        // 标题
        val titleLabel = JBLabel("Claude AI")
        titleLabel.font = JBUI.Fonts.label(14f).deriveFont(Font.BOLD)
        leftPanel.add(titleLabel)
        
        leftPanel.add(Box.createHorizontalStrut(JBUI.scale(16)))
        
        // 服务器端口指示器
        val serverIndicator = createServerPortIndicator()
        leftPanel.add(serverIndicator)
        
        toolbar.add(leftPanel, BorderLayout.WEST)
        
        // 右侧：新会话 + 会话历史按钮
        val rightPanel = JBPanel<JBPanel<*>>()
        rightPanel.layout = BoxLayout(rightPanel, BoxLayout.X_AXIS)
        rightPanel.isOpaque = false
        
        // 新会话按钮
        val newSessionButton = createActionButton("➕ 新会话") {
            onNewSession()
        }
        rightPanel.add(newSessionButton)
        
        rightPanel.add(Box.createHorizontalStrut(JBUI.scale(8)))
        
        // 会话历史按钮
        val historyButton = createActionButton("📋 会话历史") {
            onShowHistory()
        }
        rightPanel.add(historyButton)
        
        toolbar.add(rightPanel, BorderLayout.EAST)
        
        return toolbar
    }
    
    private fun createServerPortIndicator(): JBLabel {
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
                    openInBrowser(serverUrl)
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
    
    private fun createActionButton(text: String, action: () -> Unit): JBLabel {
        val button = JBLabel(text)
        button.font = JBUI.Fonts.label(12f)
        button.foreground = JBColor(Color(0x666666), Color(0xAAAAAA))
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.border = JBUI.Borders.empty(4, 8)
        
        button.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                action()
            }
            
            override fun mouseEntered(e: MouseEvent) {
                button.background = JBColor(Color(0xF5F5F5), Color(0x3C3F41))
                button.isOpaque = true
            }
            
            override fun mouseExited(e: MouseEvent) {
                button.isOpaque = false
            }
        })
        
        return button
    }
    
    private fun openInBrowser(url: String) {
        try {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(java.net.URI(url))
            } else {
                Messages.showErrorDialog(project, "无法打开浏览器", "错误")
            }
        } catch (e: IOException) {
            Messages.showErrorDialog(project, "打开浏览器失败: ${e.message}", "错误")
        }
    }
}


