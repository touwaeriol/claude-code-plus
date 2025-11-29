package com.asakii.plugin.ui.title

import com.asakii.plugin.bridge.IdeSessionBridge
import com.asakii.plugin.messages.ClaudeCodePlusBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * ToolWindow 标题栏上的会话选择器组件（下拉菜单形式）。
 *
 * 设计：
 * - 显示当前会话名称 + 下拉箭头
 * - 点击弹出下拉菜单，列出所有会话
 * - 正在生成的会话用绿点标记
 */
class SessionTabsAction(
    private val sessionBridge: IdeSessionBridge
) : AnAction("Claude 会话", "管理 Claude 会话", null), CustomComponentAction, Disposable {

    private val logger = Logger.getInstance(SessionTabsAction::class.java)

    // 当前会话显示面板
    private val selectorPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    // 当前会话标签（鼠标事件穿透到父组件）
    private val currentSessionLabel = object : JBLabel(ClaudeCodePlusBundle.message("session.noSession")) {
        override fun contains(x: Int, y: Int) = false
    }.apply {
        foreground = JBColor(Color(0x24292e), Color(0xe6edf3))
    }

    // 生成中指示器（鼠标事件穿透到父组件）
    private val generatingIndicator = object : JBLabel("●") {
        override fun contains(x: Int, y: Int) = false
    }.apply {
        foreground = JBColor(Color(0x28a745), Color(0x3fb950))
        isVisible = false
        border = JBUI.Borders.emptyLeft(4)
    }

    // 下拉箭头（鼠标事件穿透到父组件）
    private val dropdownArrow = object : JBLabel("▼") {
        override fun contains(x: Int, y: Int) = false
    }.apply {
        foreground = JBColor(Color(0x6a737d), Color(0x8b949e))
        font = font.deriveFont(8f)
        border = JBUI.Borders.emptyLeft(4)
    }

    // 当前状态
    private var currentState: IdeSessionBridge.SessionState? = null
    private var removeListener: (() -> Unit)? = null

    init {
        // 组装选择器面板
        selectorPanel.add(currentSessionLabel)
        selectorPanel.add(generatingIndicator)
        selectorPanel.add(dropdownArrow)

        // 添加点击事件（子组件已通过 contains()=false 让事件穿透到父组件）
        selectorPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showSessionPopup(selectorPanel)
            }

            override fun mouseEntered(e: MouseEvent) {
                currentSessionLabel.foreground = JBColor(Color(0x0366d6), Color(0x58a6ff))
            }

            override fun mouseExited(e: MouseEvent) {
                currentSessionLabel.foreground = JBColor(Color(0x24292e), Color(0xe6edf3))
            }
        })

        // 监听会话状态变化
        removeListener = sessionBridge.addSessionStateListener { state ->
            SwingUtilities.invokeLater { render(state) }
        }
        render(sessionBridge.latestState())
    }

    override fun actionPerformed(e: AnActionEvent) = Unit

    override fun createCustomComponent(
        presentation: com.intellij.openapi.actionSystem.Presentation,
        place: String
    ): JComponent = selectorPanel

    /**
     * 渲染当前会话状态
     */
    private fun render(state: IdeSessionBridge.SessionState?) {
        currentState = state
        val sessions = state?.sessions.orEmpty()
        val activeSession = sessions.find { it.id == state?.activeSessionId }

        if (activeSession != null) {
            currentSessionLabel.text = activeSession.title
            currentSessionLabel.toolTipText = ClaudeCodePlusBundle.message("session.switchTo")
            generatingIndicator.isVisible = activeSession.isGenerating
            generatingIndicator.toolTipText = if (activeSession.isGenerating) ClaudeCodePlusBundle.message("session.generating") else null
        } else if (sessions.isNotEmpty()) {
            currentSessionLabel.text = sessions.first().title
            currentSessionLabel.toolTipText = ClaudeCodePlusBundle.message("session.switchTo")
            generatingIndicator.isVisible = sessions.first().isGenerating
        } else {
            currentSessionLabel.text = ClaudeCodePlusBundle.message("session.noSession")
            currentSessionLabel.toolTipText = null
            generatingIndicator.isVisible = false
        }

        selectorPanel.revalidate()
        selectorPanel.repaint()
    }

    /**
     * 显示会话选择弹出菜单（只显示激活的会话）
     */
    private fun showSessionPopup(component: JComponent) {
        val sessions = currentState?.sessions.orEmpty()

        // 只显示激活（已连接）的会话
        val activeSessions = sessions.filter { it.isConnected }

        if (activeSessions.isEmpty()) {
            // 没有激活会话时不显示弹窗
            return
        }

        val popupStep = object : BaseListPopupStep<IdeSessionBridge.SessionSummary>(
            ClaudeCodePlusBundle.message("session.select"),
            activeSessions
        ) {
            override fun getTextFor(value: IdeSessionBridge.SessionSummary): String {
                // 当前选中的加 ✓
                val activeMarker = if (value.id == currentState?.activeSessionId) " ✓" else ""
                return "${value.title}$activeMarker"
            }

            override fun onChosen(selectedValue: IdeSessionBridge.SessionSummary, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    // 切换会话（点击当前激活的会话不做任何操作，和 tab 行为一致）
                    if (selectedValue.id != currentState?.activeSessionId) {
                        logger.debug("Switching session to ${selectedValue.id}")
                        sessionBridge.switchSession(selectedValue.id)
                    }
                }
                return FINAL_CHOICE
            }

            override fun isSpeedSearchEnabled(): Boolean = true
        }

        JBPopupFactory.getInstance()
            .createListPopup(popupStep)
            .showUnderneathOf(component)
    }

    override fun dispose() {
        removeListener?.invoke()
        removeListener = null
    }
}








