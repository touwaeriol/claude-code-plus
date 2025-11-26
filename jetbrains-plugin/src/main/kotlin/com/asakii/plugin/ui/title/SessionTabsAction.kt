package com.asakii.plugin.ui.title

import com.asakii.plugin.bridge.IdeSessionBridge
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

    // 当前会话标签
    private val currentSessionLabel = JBLabel("暂无会话").apply {
        foreground = JBColor(Color(0x24292e), Color(0xe6edf3))
    }

    // 生成中指示器
    private val generatingIndicator = JBLabel("●").apply {
        foreground = JBColor(Color(0x28a745), Color(0x3fb950))
        isVisible = false
        border = JBUI.Borders.emptyLeft(4)
    }

    // 下拉箭头
    private val dropdownArrow = JBLabel("▼").apply {
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

        // 添加点击事件
        selectorPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                showSessionPopup(e.component as JComponent)
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
            currentSessionLabel.toolTipText = "点击切换会话"
            generatingIndicator.isVisible = activeSession.isGenerating
            generatingIndicator.toolTipText = if (activeSession.isGenerating) "正在生成中..." else null
        } else if (sessions.isNotEmpty()) {
            currentSessionLabel.text = sessions.first().title
            currentSessionLabel.toolTipText = "点击切换会话"
            generatingIndicator.isVisible = sessions.first().isGenerating
        } else {
            currentSessionLabel.text = "暂无会话"
            currentSessionLabel.toolTipText = null
            generatingIndicator.isVisible = false
        }

        selectorPanel.revalidate()
        selectorPanel.repaint()
    }

    /**
     * 会话列表项（包含分隔符）
     */
    private sealed class SessionListItem {
        data class Header(val title: String) : SessionListItem()
        data class Session(val summary: IdeSessionBridge.SessionSummary) : SessionListItem()
    }

    /**
     * 显示会话选择弹出菜单（分组：进行中 / 历史）
     */
    private fun showSessionPopup(component: JComponent) {
        val sessions = currentState?.sessions.orEmpty()
        if (sessions.isEmpty()) {
            return
        }

        // 分组：进行中（已连接）和历史（未连接）
        val ingressSessions = sessions.filter { it.isConnected }
        val historySessions = sessions.filter { !it.isConnected }

        // 构建带分组标题的列表
        val items = mutableListOf<SessionListItem>()

        if (ingressSessions.isNotEmpty()) {
            items.add(SessionListItem.Header("进行中"))
            ingressSessions.forEach { items.add(SessionListItem.Session(it)) }
        }

        if (historySessions.isNotEmpty()) {
            items.add(SessionListItem.Header("历史会话"))
            historySessions.forEach { items.add(SessionListItem.Session(it)) }
        }

        val popupStep = object : BaseListPopupStep<SessionListItem>("选择会话", items) {
            override fun getTextFor(value: SessionListItem): String {
                return when (value) {
                    is SessionListItem.Header -> "── ${value.title} ──"
                    is SessionListItem.Session -> {
                        val summary = value.summary
                        val prefix = when {
                            summary.isGenerating -> "🟢 "
                            summary.isConnected -> "🔵 "
                            else -> "📝 "
                        }
                        val activeMarker = if (summary.id == currentState?.activeSessionId) " ✓" else ""
                        "$prefix${summary.title}$activeMarker"
                    }
                }
            }

            override fun isSelectable(value: SessionListItem): Boolean {
                return value is SessionListItem.Session
            }

            override fun onChosen(selectedValue: SessionListItem, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice && selectedValue is SessionListItem.Session) {
                    val summary = selectedValue.summary
                    if (summary.id != currentState?.activeSessionId) {
                        logger.debug("Switching session to ${summary.id}")
                        sessionBridge.switchSession(summary.id)
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








