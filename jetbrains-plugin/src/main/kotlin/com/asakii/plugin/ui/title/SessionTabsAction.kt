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

    // 可见标签和隐藏标签
    private var visibleTabs: List<JetBrainsSessionSummary> = emptyList()
    private var hiddenTabs: List<JetBrainsSessionSummary> = emptyList()

    // 滚动按钮
    private val scrollLeftButton = createScrollButton(isLeft = true)
    private val scrollRightButton = createScrollButton(isLeft = false)

    // 滚动位置
    private var scrollOffset = 0

    // 最大可见标签数（防止占用过多标题栏空间）
    private val maxVisibleTabsLimit = 5

    // 滚动按钮宽度
    private val scrollButtonWidth = JBUI.scale(22)

    // 主面板 - 使用自定义布局实现滚动
    private val tabsPanel = object : JBPanel<JBPanel<*>>(null) {
        private val tabComponents = mutableListOf<JComponent>()
        private var lastKnownWidth = 0

        init {
            isOpaque = false

            // 监听大小变化，重新计算可见标签
            addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) {
                    if (width != lastKnownWidth && width > 0) {
                        lastKnownWidth = width
                        SwingUtilities.invokeLater { relayout() }
                    }
                }
            })

            // 监听组件添加到层次结构（确保初始化时触发布局）
            addHierarchyListener { e ->
                if ((e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L) {
                    if (isShowing) {
                        SwingUtilities.invokeLater {
                            // 延迟执行确保父容器已完成布局
                            Timer(50) {
                                (it.source as? Timer)?.stop()
                                relayout()
                            }.start()
                        }
                    }
                }
            }
        }

        fun setTabs(tabs: List<JComponent>) {
            tabComponents.clear()
            tabComponents.addAll(tabs)
            // 重置滚动位置
            scrollOffset = 0
            // 延迟布局，确保组件尺寸已确定
            SwingUtilities.invokeLater {
                relayout()
            }
        }

        fun relayout() {
            removeAll()

            if (tabComponents.isEmpty()) {
                revalidate()
                repaint()
                return
            }

            // 固定最多显示 maxVisibleTabsLimit 个标签，不使用滚动
            val maxVisibleTabs = minOf(tabComponents.size, maxVisibleTabsLimit)

            var x = 0

            // 添加可见标签（最多 5 个）
            visibleTabs = sessions.take(maxVisibleTabs)
            hiddenTabs = sessions.drop(maxVisibleTabs)

            for (i in 0 until maxVisibleTabs) {
                val tab = tabComponents[i]
                add(tab)
                tab.setBounds(x, 0, tabFixedWidth, tabHeight)
                x += tabFixedWidth
            }

            revalidate()
            repaint()
        }

        fun scrollLeft() {
            if (scrollOffset > 0) {
                scrollOffset--
                relayout()
            }
        }

        fun scrollRight() {
            val availableWidth = width
            val tabsAreaWidth = availableWidth - scrollButtonWidth * 2
            val maxVisibleTabs = maxOf(1, tabsAreaWidth / tabFixedWidth)
            val maxOffset = maxOf(0, tabComponents.size - maxVisibleTabs)
            if (scrollOffset < maxOffset) {
                scrollOffset++
                relayout()
            }
        }

        override fun getPreferredSize(): Dimension {
            val visibleCount = minOf(tabComponents.size, maxVisibleTabsLimit)
            return Dimension(visibleCount * tabFixedWidth, tabHeight)
        }

        override fun getMinimumSize(): Dimension {
            return Dimension(tabFixedWidth, tabHeight)
        }

        override fun getMaximumSize(): Dimension {
            return getPreferredSize()
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

    private fun createScrollButton(isLeft: Boolean): JButton {
        return object : JButton() {
            private var hovered = false

            init {
                isFocusPainted = false
                isBorderPainted = false
                isContentAreaFilled = false
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                toolTipText = if (isLeft) "Scroll left" else "Scroll right"

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
                        if (isEnabled) {
                            if (isLeft) {
                                tabsPanel.scrollLeft()
                            } else {
                                tabsPanel.scrollRight()
                            }
                        }
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
                when {
                    !isEnabled -> {
                        // 禁用状态：淡色背景
                        g2.color = JBUI.CurrentTheme.DefaultTabs.borderColor()
                        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f)
                        g2.fill(RoundRectangle2D.Float(1f, 1f, w - 2, h - 2, arc, arc))
                        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)
                    }
                    hovered -> {
                        // 悬停状态：高亮背景
                        g2.color = JBUI.CurrentTheme.Focus.focusColor()
                        g2.fill(RoundRectangle2D.Float(1f, 1f, w - 2, h - 2, arc, arc))
                    }
                    else -> {
                        // 正常状态：边框
                        g2.color = JBUI.CurrentTheme.DefaultTabs.borderColor()
                        g2.stroke = BasicStroke(1f)
                        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))
                    }
                }

                // 绘制箭头
                val arrowColor = when {
                    !isEnabled -> UIUtil.getLabelDisabledForeground()
                    hovered -> Color.WHITE
                    else -> UIUtil.getLabelForeground()
                }
                g2.color = arrowColor
                g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)

                val centerX = w / 2
                val centerY = h / 2
                val arrowSize = JBUI.scale(4).toFloat()

                if (isLeft) {
                    // 左箭头 <
                    g2.drawLine(
                        (centerX + arrowSize / 2).toInt(),
                        (centerY - arrowSize).toInt(),
                        (centerX - arrowSize / 2).toInt(),
                        centerY.toInt()
                    )
                    g2.drawLine(
                        (centerX - arrowSize / 2).toInt(),
                        centerY.toInt(),
                        (centerX + arrowSize / 2).toInt(),
                        (centerY + arrowSize).toInt()
                    )
                } else {
                    // 右箭头 >
                    g2.drawLine(
                        (centerX - arrowSize / 2).toInt(),
                        (centerY - arrowSize).toInt(),
                        (centerX + arrowSize / 2).toInt(),
                        centerY.toInt()
                    )
                    g2.drawLine(
                        (centerX + arrowSize / 2).toInt(),
                        centerY.toInt(),
                        (centerX - arrowSize / 2).toInt(),
                        (centerY + arrowSize).toInt()
                    )
                }

                g2.dispose()
            }
        }
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
