package com.claudecodeplus.plugin.ui

import com.claudecodeplus.plugin.types.DisplayItem
import com.claudecodeplus.plugin.ui.display.DisplayItemRenderer
import com.claudecodeplus.plugin.ui.input.ContextManager
import com.claudecodeplus.plugin.ui.input.ContextTagPanel
import com.claudecodeplus.plugin.ui.input.ModelSelectorPanel
import com.claudecodeplus.plugin.ui.input.PermissionSelectorPanel
import com.claudecodeplus.plugin.ui.input.TokenStatsPanel
import com.claudecodeplus.plugin.ui.input.UnifiedChatInputContainer
import com.claudecodeplus.server.tools.IdeTools
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.BorderFactory
import javax.swing.border.EmptyBorder

/**
 * 主聊天面板 - 使用 DisplayItem 架构
 * 
 * 完整复刻 Vue 前端的功能
 */
class ChatPanel(
    private val project: Project,
    private val ideTools: IdeTools
) {
    
    private val viewModel = ChatViewModel(project, ideTools)
    private val contextManager = ContextManager()
    private lateinit var messageListPanel: JPanel
    private lateinit var inputArea: JBTextArea
    private lateinit var sendButton: JButton
    private lateinit var scrollPane: JBScrollPane
    private val displayItemComponents = mutableMapOf<String, JComponent>()
    
    // 输入增强组件
    private val modelSelector = ModelSelectorPanel()
    private val permissionSelector = PermissionSelectorPanel()
    private val contextTagPanel = ContextTagPanel(contextManager)
    private lateinit var tokenStatsPanel: TokenStatsPanel
    private lateinit var streamingIndicator: com.claudecodeplus.plugin.ui.indicators.StreamingIndicator
    private lateinit var connectionIndicator: com.claudecodeplus.plugin.ui.indicators.ConnectionStatusIndicator
    
    /**
     * 创建UI组件
     */
    fun createUI(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = EmptyBorder(JBUI.insets(0))
        
        // 设置背景色
        val theme = ideTools.getTheme()
        try {
            mainPanel.background = java.awt.Color.decode(theme.panelBackground)
        } catch (e: Exception) {
            mainPanel.background = com.intellij.util.ui.UIUtil.getPanelBackground()
        }
        
        // ✅ 工具栏已移到 ToolWindow 边框上，不再需要在这里创建
        
        // 创建消息列表区域
        messageListPanel = JPanel()
        messageListPanel.layout = BoxLayout(messageListPanel, BoxLayout.Y_AXIS)
        messageListPanel.border = EmptyBorder(JBUI.insets(8))
        
        try {
            messageListPanel.background = java.awt.Color.decode(theme.panelBackground)
        } catch (e: Exception) {
            messageListPanel.background = com.intellij.util.ui.UIUtil.getPanelBackground()
        }
        
        scrollPane = JBScrollPane(messageListPanel)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        scrollPane.border = BorderFactory.createEmptyBorder()
        
        // 创建输入区域
        val inputPanel = createInputPanel()
        
        // ✅ 监听 DisplayItems 变化（响应式状态管理）
        viewModel.displayItems.onEach { items ->
            // 确保在 EDT 线程更新 UI
            SwingUtilities.invokeLater {
                updateDisplayItems(items)
            }
        }.launchIn(CoroutineScope(Dispatchers.Main))
        
        // ✅ 监听流式状态变化
        viewModel.isStreaming.onEach { isStreaming ->
            SwingUtilities.invokeLater {
                sendButton.isEnabled = !isStreaming
                if (isStreaming) {
                    sendButton.text = "生成中..."
                } else {
                    sendButton.text = "发送 (Enter)"
                }
            }
        }.launchIn(CoroutineScope(Dispatchers.Main))
        
        // 初始化连接
        CoroutineScope(Dispatchers.Main).launch {
            try {
                viewModel.connect()
                addWelcomeMessage()
            } catch (e: Exception) {
                addErrorMessage("连接失败: ${e.message}")
            }
        }
        
        // 创建流式状态指示器（浮动层）
        streamingIndicator = com.claudecodeplus.plugin.ui.indicators.StreamingIndicator(
            viewModel.isStreaming,
            viewModel.inputTokens,
            viewModel.outputTokens
        )
        
        // 组装主面板
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        mainPanel.add(inputPanel, BorderLayout.SOUTH)
        
        // 添加流式状态指示器（作为覆盖层）
        val layeredPane = JLayeredPane()
        layeredPane.layout = null
        layeredPane.add(mainPanel, JLayeredPane.DEFAULT_LAYER)
        
        val indicatorPanel = streamingIndicator.getPanel()
        layeredPane.add(indicatorPanel, JLayeredPane.PALETTE_LAYER)
        
        // 布局管理
        layeredPane.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                val size = layeredPane.size
                mainPanel.setBounds(0, 0, size.width, size.height)
                
                // 将指示器放在底部中央
                val indicatorSize = indicatorPanel.preferredSize
                val x = (size.width - indicatorSize.width) / 2
                val y = size.height - indicatorSize.height - 120  // 距离底部120px
                indicatorPanel.setBounds(x, y, indicatorSize.width, indicatorSize.height)
            }
        })
        
        return layeredPane
    }
    
    /**
     * 更新 DisplayItems
     */
    private fun updateDisplayItems(items: List<DisplayItem>) {
        // 清空旧组件
        messageListPanel.removeAll()
        displayItemComponents.clear()
        
        // 渲染新的 DisplayItems
        for (item in items) {
            val renderer = DisplayItemRenderer(item, ideTools)
            val component = renderer.create()
            
            displayItemComponents[item.id] = component
            messageListPanel.add(component)
            messageListPanel.add(Box.createVerticalStrut(8))
        }
        
        // 刷新UI
        messageListPanel.revalidate()
        messageListPanel.repaint()
        
        // 滚动到底部
        scrollToBottom()
    }
    
    /**
     * 创建输入面板（完整版，完全复刻Vue样式）
     * 
     * 布局结构（对应 frontend/src/components/chat/ChatInput.vue）:
     * - UnifiedChatInputContainer (统一容器，圆角12px，边框1.5px)
     *   - Top Toolbar (上下文管理工具栏)
     *   - Input Area (输入区域，内边距8px 12px)
     *   - Bottom Toolbar (底部工具栏)
     */
    private fun createInputPanel(): JPanel {
        // 创建统一输入容器（完全复刻Vue样式）
        val unifiedContainer = UnifiedChatInputContainer()
        val containerPanel = unifiedContainer.getContainer()
        
        // Top Toolbar（上下文管理工具栏）- 在容器内部
        val contextPanel = contextTagPanel.create()
        containerPanel.add(contextPanel, BorderLayout.NORTH)
        
        // Input Area（输入区域）
        inputArea = JBTextArea()
        inputArea.lineWrap = true
        inputArea.wrapStyleWord = true
        inputArea.font = inputArea.font.deriveFont(14f)  // 14px字体
        inputArea.border = EmptyBorder(JBUI.insets(8, 12))  // 内边距 8px 12px
        inputArea.background = unifiedContainer.getContainer().background
        inputArea.foreground = JBColor(java.awt.Color(0x24292E), java.awt.Color(0xE0E0E0))
        
        // 设置最小和最大高度
        inputArea.minimumSize = java.awt.Dimension(0, 40)  // 最小高度40px
        inputArea.maximumSize = java.awt.Dimension(Int.MAX_VALUE, 300)  // 最大高度300px
        
        // 添加焦点监听器（用于更新容器边框样式）
        unifiedContainer.addFocusListener(inputArea)
        
        // 快捷键：Enter发送，Shift+Enter换行
        inputArea.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    sendMessage()
                }
            }
        })
        
        // 自动调整高度
        inputArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) {
                adjustTextAreaHeight()
            }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) {
                adjustTextAreaHeight()
            }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) {
                adjustTextAreaHeight()
            }
        })
        
        val inputScrollPane = JBScrollPane(inputArea)
        inputScrollPane.border = BorderFactory.createEmptyBorder()
        inputScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        inputScrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        
        containerPanel.add(inputScrollPane, BorderLayout.CENTER)
        
        // Bottom Toolbar（底部工具栏）- 在容器内部
        val toolbarPanel = createBottomToolbar()
        containerPanel.add(toolbarPanel, BorderLayout.SOUTH)
        
        // 外层包装面板（用于外边距）
        val wrapperPanel = JPanel(BorderLayout())
        wrapperPanel.border = EmptyBorder(JBUI.insets(8, 0, 0, 0))
        wrapperPanel.add(containerPanel, BorderLayout.CENTER)
        
        return wrapperPanel
    }
    
    /**
     * 自动调整文本区域高度
     */
    private fun adjustTextAreaHeight() {
        SwingUtilities.invokeLater {
            val doc = inputArea.document
            val root = doc.defaultRootElement
            val lineCount = root.elementCount
            
            // 计算所需高度（每行约20px，加上内边距）
            val lineHeight = inputArea.fontMetrics.height
            val padding = 16  // 上下内边距
            val preferredHeight = (lineCount * lineHeight) + padding
            
            // 限制在最小和最大高度之间
            val minHeight = 40
            val maxHeight = 300
            val newHeight = preferredHeight.coerceIn(minHeight, maxHeight)
            
            inputArea.preferredSize = java.awt.Dimension(inputArea.preferredSize.width, newHeight)
            inputArea.revalidate()
        }
    }
    
    /**
     * 创建底部工具栏（完全复刻Vue样式）
     * 
     * 对应 frontend/src/components/chat/ChatInput.vue 第107-310行的 bottom-toolbar
     */
    private fun createBottomToolbar(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor(java.awt.Color(0xE1E4E8), java.awt.Color(0x3C3C3C))),  // 顶部边框
            EmptyBorder(JBUI.insets(6, 12))  // 内边距 6px 12px
        )
        panel.background = JBColor(java.awt.Color(0xF6F8FA), java.awt.Color(0x2B2B2B))
        
        // 左侧：模型选择器 + 权限选择器 + Token统计
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0))
        leftPanel.isOpaque = false
        
        // 模型选择器
        leftPanel.add(modelSelector.create())
        
        // 权限选择器
        leftPanel.add(permissionSelector.create())
        
        // Token 统计
        tokenStatsPanel = TokenStatsPanel(viewModel.inputTokens, viewModel.outputTokens)
        leftPanel.add(tokenStatsPanel.create())
        
        panel.add(leftPanel, BorderLayout.WEST)
        
        // 右侧：发送按钮
        sendButton = JButton("📤 发送")
        sendButton.font = sendButton.font.deriveFont(14f)  // 14px字体
        sendButton.preferredSize = java.awt.Dimension(100, 36)  // 高度36px
        sendButton.background = JBColor(java.awt.Color(0x0366D6), java.awt.Color(0x0366D6))
        sendButton.foreground = java.awt.Color.WHITE
        sendButton.border = BorderFactory.createEmptyBorder(8, 16, 8, 16)  // 内边距
        sendButton.isOpaque = true
        sendButton.isContentAreaFilled = true
        sendButton.focusPainted = false
        
        // 悬停效果
        sendButton.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent) {
                sendButton.background = JBColor(java.awt.Color(0x0256C2), java.awt.Color(0x0256C2))
            }
            override fun mouseExited(e: java.awt.event.MouseEvent) {
                sendButton.background = JBColor(java.awt.Color(0x0366D6), java.awt.Color(0x0366D6))
            }
        })
        
        sendButton.addActionListener { sendMessage() }
        
        panel.add(sendButton, BorderLayout.EAST)
        
        // 监听 token 变化并更新显示
        viewModel.inputTokens.onEach {
            SwingUtilities.invokeLater { tokenStatsPanel.updateStats() }
        }.launchIn(CoroutineScope(Dispatchers.Main))
        
        viewModel.outputTokens.onEach {
            SwingUtilities.invokeLater { tokenStatsPanel.updateStats() }
        }.launchIn(CoroutineScope(Dispatchers.Main))
        
        return panel
    }
    
    /**
     * 发送消息
     */
    private fun sendMessage() {
        val text = inputArea.text.trim()
        if (text.isBlank()) return
        
        // 清空输入框
        inputArea.text = ""
        
        // 禁用发送按钮
        sendButton.isEnabled = false
        
        // 发送消息
        CoroutineScope(Dispatchers.Main).launch {
            try {
                viewModel.sendMessage(text)
            } catch (e: Exception) {
                addErrorMessage("发送失败: ${e.message}")
            } finally {
                SwingUtilities.invokeLater {
                    sendButton.isEnabled = true
                }
            }
        }
    }
    
    /**
     * 添加欢迎消息
     */
    private fun addWelcomeMessage() {
        val welcomeLabel = JLabel("<html><div style='text-align: center; color: #666; font-style: italic; padding: 20px;'>" +
            "欢迎使用 Claude Code Plus！<br><br>" +
            "💡 输入您的问题开始对话<br>" +
            "⌨️ Enter 发送 | Shift+Enter 换行" +
            "</div></html>")
        welcomeLabel.horizontalAlignment = SwingConstants.CENTER
        
        messageListPanel.add(welcomeLabel)
        messageListPanel.revalidate()
        messageListPanel.repaint()
    }
    
    /**
     * 添加错误消息
     */
    private fun addErrorMessage(text: String) {
        val errorLabel = JLabel("<html><div style='color: #D32F2F; text-align: center; padding: 8px;'>❌ $text</div></html>")
        errorLabel.horizontalAlignment = SwingConstants.CENTER
        
        messageListPanel.add(errorLabel)
        messageListPanel.revalidate()
        messageListPanel.repaint()
    }
    
    /**
     * 滚动到底部
     */
    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val vertical = scrollPane.verticalScrollBar
            vertical.value = vertical.maximum
        }
    }
    
    /**
     * 处理新会话
     */
    private fun handleNewSession() {
        // TODO: 实现新会话逻辑
        // 1. 保存当前会话
        // 2. 清空消息列表
        // 3. 重新连接
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "新会话功能开发中...",
            "提示",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        )
    }
    
    /**
     * 显示会话历史
     */
    private fun handleShowHistory() {
        // TODO: 打开会话列表对话框
        javax.swing.JOptionPane.showMessageDialog(
            null,
            "会话历史功能开发中...",
            "提示",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        )
    }
}

