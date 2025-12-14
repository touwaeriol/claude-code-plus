package com.asakii.plugin.ui.title

import com.asakii.rpc.api.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.*
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*

/**
 * ToolWindow 标题栏上的会话标签组件。
 *
 * 功能：
 * - 状态圆点（蓝色=连接中，绿色=已连接/生成中，红色=断开）
 * - 会话名称
 * - 悬停显示关闭按钮
 * - 双击重命名
 * - 右键菜单（重命名、复制 SessionID）
 * - 自适应布局：标签过多时显示溢出菜单
 */
class SessionTabsAction(
    private val sessionApi: JetBrainsSessionApi
) : AnAction("Claude 会话", "管理 Claude 会话", null), CustomComponentAction, Disposable {

    private val logger = Logger.getInstance(SessionTabsAction::class.java)

    // 颜色定义 - 使用 IDEA 主题颜色
    private val colorConnected = JBColor(Color(0x59A869), Color(0x499C54))  // 绿色
    private val colorDisconnected = JBColor(Color(0xDB5860), Color(0xDB5860))  // 红色
    private val colorConnecting = JBColor(Color(0x3592C4), Color(0x3592C4))  // 蓝色（连接中）
    private val colorCloseHover = JBColor(Color(0xDB5860), Color(0xDB5860))

    // 当前状态
    private var currentState: JetBrainsSessionState? = null
    private var removeListener: (() -> Unit)? = null

    // 脉冲动画
    private var pulseScale = 1.0f
    private var pulseOpacity = 1.0f
    private val pulseTimer = Timer(50) {
        pulseScale += 0.05f
        pulseOpacity -= 0.03f
        if (pulseScale > 1.5f) {
            pulseScale = 1.0f
            pulseOpacity = 1.0f
        }
        tabsPanel.repaint()
    }

    // 会话列表
    private var sessions: List<JetBrainsSessionSummary> = emptyList()
    private var activeSessionId: String? = null

    // Tab 固定宽度
    private val tabFixedWidth = JBUI.scale(100)
    private val tabHeight = JBUI.scale(22)
    private val overflowButtonWidth = JBUI.scale(24)

    // 可见标签和隐藏标签
    private var visibleTabs: List<JetBrainsSessionSummary> = emptyList()
    private var hiddenTabs: List<JetBrainsSessionSummary> = emptyList()

    // 溢出按钮
    private val overflowButton = createOverflowButton()

    // 主面板 - 使用自定义布局实现自适应
    private val tabsPanel = object : JBPanel<JBPanel<*>>(null) {
        private val tabComponents = mutableListOf<JComponent>()

        init {
            isOpaque = false
            // 监听大小变化，重新计算可见标签
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    SwingUtilities.invokeLater { relayout() }
                }
            })
        }

        fun setTabs(tabs: List<JComponent>) {
            tabComponents.clear()
            tabComponents.addAll(tabs)
            relayout()
        }

        private fun relayout() {
            removeAll()

            if (tabComponents.isEmpty()) {
                revalidate()
                repaint()
                return
            }

            val availableWidth = width
            if (availableWidth <= 0) {
                // 布局未完成，添加所有组件
                tabComponents.forEach { add(it) }
                revalidate()
                repaint()
                return
            }

            // 计算可以显示多少个标签
            val maxVisibleTabs = (availableWidth - overflowButtonWidth) / tabFixedWidth
            val needsOverflow = tabComponents.size > maxVisibleTabs && maxVisibleTabs > 0

            var x = 0
            val visibleCount = if (needsOverflow) maxOf(1, maxVisibleTabs) else tabComponents.size

            // 更新可见/隐藏标签列表
            visibleTabs = sessions.take(visibleCount)
            hiddenTabs = if (needsOverflow) sessions.drop(visibleCount) else emptyList()

            // 添加可见标签
            for (i in 0 until minOf(visibleCount, tabComponents.size)) {
                val tab = tabComponents[i]
                add(tab)
                tab.setBounds(x, 0, tabFixedWidth, tabHeight)
                x += tabFixedWidth
            }

            // 添加溢出按钮
            if (needsOverflow) {
                add(overflowButton)
                overflowButton.setBounds(x, 0, overflowButtonWidth, tabHeight)
                overflowButton.isVisible = true
                updateOverflowButtonText()
            } else {
                overflowButton.isVisible = false
            }

            revalidate()
            repaint()
        }

        override fun getPreferredSize(): Dimension {
            // 返回所有标签的总宽度
            val totalWidth = tabComponents.size * tabFixedWidth + overflowButtonWidth
            return Dimension(totalWidth, tabHeight)
        }

        override fun getMinimumSize(): Dimension {
            // 最小显示 1 个标签 + 溢出按钮
            return Dimension(tabFixedWidth + overflowButtonWidth, tabHeight)
        }
    }

    init {
        logger.info("🏷️ [SessionTabsAction] Registering session state listener")
        removeListener = sessionApi.addStateListener { state ->
            logger.info("🏷️ [SessionTabsAction] Received state update: ${state.sessions.size} sessions, active=${state.activeSessionId}")
            SwingUtilities.invokeLater { render(state) }
        }
        val latestState = sessionApi.getState()
        logger.info("🏷️ [SessionTabsAction] Initial state: ${latestState?.sessions?.size ?: 0} sessions")
        render(latestState)
    }

    private fun createOverflowButton(): JButton {
        return object : JButton() {
            private var hovered = false

            init {
                font = JBUI.Fonts.smallFont()
                isFocusPainted = false
                isBorderPainted = false
                isContentAreaFilled = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = "More sessions..."

                addMouseListener(object : MouseAdapter() {
                    override fun mouseEntered(e: MouseEvent) {
                        hovered = true
                        repaint()
                    }

                    override fun mouseExited(e: MouseEvent) {
                        hovered = false
                        repaint()
                    }

                    override fun mouseClicked(e: MouseEvent) {
                        showOverflowMenu(e)
                    }
                })
            }

            override fun paintComponent(g: Graphics) {
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

                val w = width.toFloat()
                val h = height.toFloat()
                val arc = JBUI.scale(6).toFloat()

                // 背景
                if (hovered) {
                    g2.color = UIUtil.getListSelectionBackground(false)
                    g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
                }

                // 边框
                g2.color = JBUI.CurrentTheme.DefaultTabs.borderColor()
                g2.stroke = BasicStroke(1f)
                g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))

                // 绘制文字
                g2.color = UIUtil.getLabelForeground()
                g2.font = font
                val fm = g2.fontMetrics
                val text = text ?: "▼"
                val textX = (w - fm.stringWidth(text)) / 2
                val textY = (h + fm.ascent - fm.descent) / 2
                g2.drawString(text, textX, textY)

                g2.dispose()
            }
        }
    }

    private fun updateOverflowButtonText() {
        overflowButton.text = "+${hiddenTabs.size}"
    }

    private fun showOverflowMenu(e: MouseEvent) {
        if (hiddenTabs.isEmpty()) return

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<JetBrainsSessionSummary>("Hidden Sessions", hiddenTabs) {
                override fun getTextFor(value: JetBrainsSessionSummary): String {
                    val prefix = when {
                        value.isGenerating -> "● "
                        value.isConnecting -> "◐ "
                        value.isConnected -> "● "
                        else -> "○ "
                    }
                    return prefix + value.title
                }

                override fun onChosen(selectedValue: JetBrainsSessionSummary, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        sessionApi.sendCommand(
                            JetBrainsSessionCommand(
                                type = JetBrainsSessionCommandType.SWITCH,
                                sessionId = selectedValue.id
                            )
                        )
                    }
                    return FINAL_CHOICE
                }
            }
        )
        popup.show(com.intellij.ui.awt.RelativePoint(e.component, Point(0, e.component.height)))
    }

    override fun actionPerformed(e: AnActionEvent) = Unit

    override fun createCustomComponent(
        presentation: com.intellij.openapi.actionSystem.Presentation,
        place: String
    ): JComponent = tabsPanel

    private fun render(state: JetBrainsSessionState?) {
        currentState = state
        sessions = state?.sessions.orEmpty()
        activeSessionId = state?.activeSessionId

        val tabs = mutableListOf<JComponent>()

        if (sessions.isEmpty()) {
            tabs.add(
                createTabComponent(
                    session = null,
                    title = "No Session",
                    isActive = false,
                    isConnected = false,
                    isConnecting = false,
                    isGenerating = false,
                    canClose = false
                )
            )
        } else {
            for (session in sessions) {
                tabs.add(
                    createTabComponent(
                        session = session,
                        title = session.title,
                        isActive = session.id == activeSessionId,
                        isConnected = session.isConnected,
                        isConnecting = session.isConnecting,
                        isGenerating = session.isGenerating,
                        canClose = sessions.size > 1
                    )
                )
            }
        }

        tabsPanel.setTabs(tabs)

        val needsAnimation = sessions.any { it.isGenerating || it.isConnecting }
        if (needsAnimation && !pulseTimer.isRunning) {
            pulseTimer.start()
        } else if (!needsAnimation && pulseTimer.isRunning) {
            pulseTimer.stop()
            pulseScale = 1.0f
            pulseOpacity = 1.0f
        }
    }

    private fun createTabComponent(
        session: JetBrainsSessionSummary?,
        title: String,
        isActive: Boolean,
        isConnected: Boolean,
        isConnecting: Boolean,
        isGenerating: Boolean,
        canClose: Boolean
    ): JComponent {
        return object : JBPanel<JBPanel<*>>() {
            private var hovered = false
            private var closeHovered = false
            private val closeButtonSize = JBUI.scale(14)
            private val closeButtonPadding = JBUI.scale(4)

            init {
                isOpaque = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = JBUI.Borders.empty(2, 4)

                toolTipText = if (session?.sessionId != null) {
                    "<html>Session ID: <b>${session.sessionId}</b><br>双击重命名 | 右键菜单</html>"
                } else {
                    "<html>$title<br>双击重命名 | 右键菜单</html>"
                }

                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (session == null) return

                        if (canClose && hovered && isInCloseButton(e.point)) {
                            handleClose(session.id)
                            return
                        }

                        if (SwingUtilities.isRightMouseButton(e)) {
                            showContextMenu(e, session)
                            return
                        }

                        if (e.clickCount == 2) {
                            handleRename(session)
                            return
                        }

                        if (session.id != activeSessionId) {
                            sessionApi.sendCommand(
                                JetBrainsSessionCommand(
                                    type = JetBrainsSessionCommandType.SWITCH,
                                    sessionId = session.id
                                )
                            )
                        }
                    }

                    override fun mouseEntered(e: MouseEvent) {
                        hovered = true
                        repaint()
                    }

                    override fun mouseExited(e: MouseEvent) {
                        hovered = false
                        closeHovered = false
                        repaint()
                    }
                })

                addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent) {
                        if (canClose && hovered) {
                            val wasCloseHovered = closeHovered
                            closeHovered = isInCloseButton(e.point)
                            if (wasCloseHovered != closeHovered) {
                                repaint()
                            }
                        }
                    }
                })
            }

            private fun isInCloseButton(point: Point): Boolean {
                val closeX = width - closeButtonSize - closeButtonPadding
                val closeY = (height - closeButtonSize) / 2
                return point.x >= closeX && point.x <= closeX + closeButtonSize &&
                        point.y >= closeY && point.y <= closeY + closeButtonSize
            }

            override fun getPreferredSize(): Dimension = Dimension(tabFixedWidth, tabHeight)
            override fun getMinimumSize(): Dimension = preferredSize
            override fun getMaximumSize(): Dimension = preferredSize

            private fun getTruncatedTitle(availableWidth: Int, fm: FontMetrics): String {
                if (fm.stringWidth(title) <= availableWidth) return title
                var truncated = title
                while (truncated.isNotEmpty() && fm.stringWidth(truncated + "…") > availableWidth) {
                    truncated = truncated.dropLast(1)
                }
                return if (truncated.isEmpty()) "…" else truncated + "…"
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g.create() as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)

                val w = width.toFloat()
                val h = height.toFloat()
                val arc = JBUI.scale(6).toFloat()

                val borderColor = JBUI.CurrentTheme.DefaultTabs.borderColor()
                val activeBorderColor = JBUI.CurrentTheme.Focus.focusColor()

                when {
                    isActive -> {
                        g2.color = UIUtil.getListSelectionBackground(true)
                        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
                        g2.color = activeBorderColor
                        g2.stroke = BasicStroke(1f)
                        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))
                    }

                    hovered -> {
                        g2.color = UIUtil.getListSelectionBackground(false)
                        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
                        g2.color = borderColor
                        g2.stroke = BasicStroke(1f)
                        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))
                    }

                    else -> {
                        g2.color = borderColor
                        g2.stroke = BasicStroke(1f)
                        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))
                    }
                }

                val fm = g2.fontMetrics
                var x = JBUI.scale(8).toFloat()
                val centerY = h / 2

                val dotSize = JBUI.scale(8).toFloat()
                val dotY = centerY - dotSize / 2

                when {
                    isConnecting -> {
                        val pulseSize = dotSize * pulseScale
                        val pulseX = x + (dotSize - pulseSize) / 2
                        val pulseY = centerY - pulseSize / 2
                        g2.color = Color(
                            colorConnecting.red,
                            colorConnecting.green,
                            colorConnecting.blue,
                            (pulseOpacity * 100).toInt()
                        )
                        g2.fill(Ellipse2D.Float(pulseX, pulseY, pulseSize, pulseSize))
                        g2.color = colorConnecting
                        g2.fill(Ellipse2D.Float(x, dotY, dotSize, dotSize))
                    }

                    isGenerating -> {
                        val pulseSize = dotSize * pulseScale
                        val pulseX = x + (dotSize - pulseSize) / 2
                        val pulseY = centerY - pulseSize / 2
                        g2.color = Color(
                            colorConnected.red,
                            colorConnected.green,
                            colorConnected.blue,
                            (pulseOpacity * 100).toInt()
                        )
                        g2.fill(Ellipse2D.Float(pulseX, pulseY, pulseSize, pulseSize))
                        g2.color = colorConnected
                        g2.fill(Ellipse2D.Float(x, dotY, dotSize, dotSize))
                    }

                    else -> {
                        g2.color = if (isConnected) colorConnected else colorDisconnected
                        g2.fill(Ellipse2D.Float(x, dotY, dotSize, dotSize))
                    }
                }
                x += dotSize + JBUI.scale(6)

                g2.color = when {
                    isActive -> UIUtil.getListSelectionForeground(true)
                    else -> UIUtil.getLabelForeground()
                }
                g2.font = font
                val textY = centerY + fm.ascent / 2 - fm.descent / 2 + 1
                val closeSpace = if (canClose) closeButtonSize + closeButtonPadding else 0
                val availableTextWidth = width - x.toInt() - JBUI.scale(8) - closeSpace
                val displayTitle = getTruncatedTitle(availableTextWidth, fm)
                g2.drawString(displayTitle, x, textY)

                if (canClose && hovered) {
                    val closeX = width - closeButtonSize - closeButtonPadding
                    val closeY = (height - closeButtonSize) / 2

                    if (closeHovered) {
                        g2.color = colorCloseHover
                        g2.fill(
                            Ellipse2D.Float(
                                closeX.toFloat(),
                                closeY.toFloat(),
                                closeButtonSize.toFloat(),
                                closeButtonSize.toFloat()
                            )
                        )
                        g2.color = Color.WHITE
                    } else {
                        g2.color = Color(128, 128, 128, 80)
                        g2.fill(
                            Ellipse2D.Float(
                                closeX.toFloat(),
                                closeY.toFloat(),
                                closeButtonSize.toFloat(),
                                closeButtonSize.toFloat()
                            )
                        )
                        g2.color = UIUtil.getLabelDisabledForeground()
                    }

                    g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    val padding = JBUI.scale(4)
                    g2.drawLine(
                        closeX + padding,
                        closeY + padding,
                        closeX + closeButtonSize - padding,
                        closeY + closeButtonSize - padding
                    )
                    g2.drawLine(
                        closeX + closeButtonSize - padding,
                        closeY + padding,
                        closeX + padding,
                        closeY + closeButtonSize - padding
                    )
                }

                g2.dispose()
            }
        }
    }

    private fun handleClose(sessionId: String) {
        if (sessions.size <= 1) return
        sessionApi.sendCommand(
            JetBrainsSessionCommand(
                type = JetBrainsSessionCommandType.CLOSE,
                sessionId = sessionId
            )
        )
    }

    private fun handleRename(session: JetBrainsSessionSummary) {
        val newName = Messages.showInputDialog(
            tabsPanel,
            "Enter new session name:",
            "Rename Session",
            null,
            session.title,
            null
        )
        if (!newName.isNullOrBlank() && newName != session.title) {
            sessionApi.sendCommand(
                JetBrainsSessionCommand(
                    type = JetBrainsSessionCommandType.RENAME,
                    sessionId = session.id,
                    newName = newName
                )
            )
        }
    }

    private fun showContextMenu(e: MouseEvent, session: JetBrainsSessionSummary) {
        val menuItems = mutableListOf<Pair<String, () -> Unit>>(
            "Rename Session" to { handleRename(session) }
        )

        if (!session.sessionId.isNullOrBlank()) {
            menuItems.add("Copy Session ID" to { copySessionId(session, e.component as JComponent) })
        }

        val popup = JBPopupFactory.getInstance().createListPopup(
            object : BaseListPopupStep<Pair<String, () -> Unit>>("Session Actions", menuItems) {
                override fun getTextFor(value: Pair<String, () -> Unit>): String = value.first

                override fun onChosen(selectedValue: Pair<String, () -> Unit>, finalChoice: Boolean): PopupStep<*>? {
                    if (finalChoice) {
                        selectedValue.second()
                    }
                    return FINAL_CHOICE
                }
            }
        )
        popup.show(com.intellij.ui.awt.RelativePoint(e))
    }

    private fun copySessionId(session: JetBrainsSessionSummary, component: JComponent) {
        val sessionId = session.sessionId ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(sessionId))
        logger.info("Copied session ID: $sessionId")

        JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
                "Session ID copied: <b>${sessionId.takeLast(12)}</b>",
                com.intellij.openapi.ui.MessageType.INFO,
                null
            )
            .setFadeoutTime(2500)
            .createBalloon()
            .show(
                com.intellij.ui.awt.RelativePoint.getCenterOf(component),
                com.intellij.openapi.ui.popup.Balloon.Position.below
            )
    }

    override fun dispose() {
        removeListener?.invoke()
        removeListener = null
        pulseTimer.stop()
    }
}
