package com.asakii.plugin.ui.title

import com.asakii.plugin.messages.ClaudeCodePlusBundle
import com.asakii.rpc.api.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
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
 * ToolWindow 标题栏上的会话标签组件（类似 Web 端）。
 *
 * 功能：
 * - 状态圆点（蓝色=连接中，绿色=已连接/生成中，红色=断开）
 * - 会话名称
 * - 悬停显示关闭按钮
 * - 双击重命名
 * - 右键菜单（重命名、复制 SessionID）
 * - 拖拽排序（暂未实现，Swing 标题栏拖拽较复杂）
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
        innerTabsPanel.repaint()
    }

    // 会话列表
    private var sessions: List<JetBrainsSessionSummary> = emptyList()
    private var activeSessionId: String? = null

    // Tab 固定宽度
    private val tabFixedWidth = JBUI.scale(100)
    // 最大可见宽度（超过此宽度显示滚动箭头）- 约3个Tab
    private val maxVisibleWidth = JBUI.scale(320)

    // 内部标签容器（实际存放标签）- 使用 BoxLayout 水平排列
    private val innerTabsPanel = JBPanel<JBPanel<*>>().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
    }

    // 滚动面板 - 限制最大宽度
    private val scrollPane = object : JScrollPane(innerTabsPanel) {
        init {
            isOpaque = false
            viewport.isOpaque = false
            border = null
            horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = VERTICAL_SCROLLBAR_NEVER
        }

        override fun getPreferredSize(): Dimension {
            val contentWidth = innerTabsPanel.preferredSize.width
            // 限制最大宽度，超过时需��滚动
            val visibleWidth = minOf(contentWidth, maxVisibleWidth)
            return Dimension(visibleWidth, JBUI.scale(26))
        }
    }

    // 主面板（包含左右箭头和标签容器）
    private val tabsPanel = object : JBPanel<JBPanel<*>>(BorderLayout()) {
        init {
            isOpaque = false
        }

        override fun getPreferredSize(): Dimension {
            val contentWidth = innerTabsPanel.preferredSize.width
            val needsScroll = contentWidth > maxVisibleWidth
            val arrowWidth = if (needsScroll) JBUI.scale(36) else 0
            val visibleWidth = minOf(contentWidth, maxVisibleWidth)
            return Dimension(visibleWidth + arrowWidth, JBUI.scale(26))
        }
    }

    // 左右箭头按钮
    private val leftArrow = createArrowButton("◀", -1)
    private val rightArrow = createArrowButton("▶", 1)

    init {
        // 组装主面板
        tabsPanel.add(leftArrow, BorderLayout.WEST)
        tabsPanel.add(scrollPane, BorderLayout.CENTER)
        tabsPanel.add(rightArrow, BorderLayout.EAST)

        logger.info("🏷️ [SessionTabsAction] Registering session state listener")
        removeListener = sessionApi.addStateListener { state ->
            logger.info("🏷️ [SessionTabsAction] Received state update: ${state.sessions.size} sessions, active=${state.activeSessionId}")
            SwingUtilities.invokeLater { render(state) }
        }
        val latestState = sessionApi.getState()
        logger.info("🏷️ [SessionTabsAction] Initial state: ${latestState?.sessions?.size ?: 0} sessions")
        render(latestState)
    }

    private fun createArrowButton(text: String, direction: Int): JButton {
        return JButton(text).apply {
            font = JBUI.Fonts.smallFont()
            preferredSize = Dimension(JBUI.scale(18), JBUI.scale(22))
            minimumSize = Dimension(JBUI.scale(18), JBUI.scale(22))
            maximumSize = Dimension(JBUI.scale(18), JBUI.scale(22))
            isFocusPainted = false
            isBorderPainted = false
            isContentAreaFilled = false
            foreground = UIUtil.getLabelDisabledForeground()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            isVisible = false  // 默认隐藏

            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    foreground = UIUtil.getLabelForeground()
                }

                override fun mouseExited(e: MouseEvent) {
                    foreground = UIUtil.getLabelDisabledForeground()
                }
            })

            addActionListener {
                scroll(direction)
            }
        }
    }

    private fun scroll(direction: Int) {
        val step = JBUI.scale(80)
        val viewport = scrollPane.viewport
        val currentPos = viewport.viewPosition
        val newX =
            (currentPos.x + direction * step).coerceIn(0, maxOf(0, innerTabsPanel.preferredSize.width - viewport.width))
        viewport.viewPosition = Point(newX, 0)
        updateArrowVisibility()
    }

    private fun updateArrowVisibility() {
        val totalWidth = innerTabsPanel.preferredSize.width
        val viewportWidth = scrollPane.viewport.width

        // 如果视口宽度为 0（未布局完成），检查是否需要滚动
        val needsScroll = totalWidth > maxVisibleWidth

        if (viewportWidth <= 0) {
            // 布局未完成时，根据内容宽度预判
            leftArrow.isVisible = false
            rightArrow.isVisible = needsScroll
            return
        }

        val currentX = scrollPane.viewport.viewPosition.x
        val maxScrollX = maxOf(0, totalWidth - viewportWidth)

        // 左箭头：已经向右滚动了才显示
        leftArrow.isVisible = needsScroll && currentX > 0
        // 右箭头：还有更多内容在右边才显示
        rightArrow.isVisible = needsScroll && currentX < maxScrollX
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

        innerTabsPanel.removeAll()

        if (sessions.isEmpty()) {
            val placeholder = createTabComponent(
                session = null,
                title = ClaudeCodePlusBundle.message("session.noSession"),
                isActive = false,
                isConnected = false,
                isConnecting = false,
                isGenerating = false,
                canClose = false
            )
            innerTabsPanel.add(placeholder)
        } else {
            for (session in sessions) {
                val tab = createTabComponent(
                    session = session,
                    title = session.title,
                    isActive = session.id == activeSessionId,
                    isConnected = session.isConnected,
                    isConnecting = session.isConnecting,
                    isGenerating = session.isGenerating,
                    canClose = sessions.size > 1
                )
                innerTabsPanel.add(tab)
            }
        }

        val needsAnimation = sessions.any { it.isGenerating || it.isConnecting }
        if (needsAnimation && !pulseTimer.isRunning) {
            pulseTimer.start()
        } else if (!needsAnimation && pulseTimer.isRunning) {
            pulseTimer.stop()
            pulseScale = 1.0f
            pulseOpacity = 1.0f
        }

        // 重置滚动位置并更新箭头
        innerTabsPanel.revalidate()
        scrollPane.revalidate()
        tabsPanel.revalidate()

        // 延迟更新箭头可见性（等布局完成）
        SwingUtilities.invokeLater {
            scrollPane.viewport.viewPosition = Point(0, 0)
            updateArrowVisibility()
        }

        tabsPanel.repaint()
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
                border = JBUI.Borders.empty(2, 4)  // 内边距

                // 设置 tooltip，显示 sessionId（如果有）
                toolTipText = if (session?.sessionId != null) {
                    "<html>Session ID: <b>${session.sessionId}</b><br>双击重命名 | 右键菜单</html>"
                } else {
                    "<html>$title<br>双击重命名 | 右键菜单</html>"
                }

                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (session == null) return

                        // 检查是否点击了关闭按钮
                        if (canClose && hovered && isInCloseButton(e.point)) {
                            handleClose(session.id)
                            return
                        }

                        // 右键菜单
                        if (SwingUtilities.isRightMouseButton(e)) {
                            showContextMenu(e, session)
                            return
                        }

                        // 双击重命名
                        if (e.clickCount == 2) {
                            handleRename(session)
                            return
                        }

                        // 单击切换
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

            override fun getPreferredSize(): Dimension {
                // 使用固定宽度
                return Dimension(tabFixedWidth, JBUI.scale(22))
            }

            override fun getMinimumSize(): Dimension = preferredSize
            override fun getMaximumSize(): Dimension = preferredSize

            // 根据可用宽度截断标题
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
                val arc = JBUI.scale(6).toFloat()  // IDEA 风格：更方正的圆角

                // 边框颜色 - 使用主题颜色
                val borderColor = JBUI.CurrentTheme.DefaultTabs.borderColor()
                val activeBorderColor = JBUI.CurrentTheme.Focus.focusColor()

                // 背景 - 所有 Tab 都有边框，类似浏览器标签页
                when {
                    isActive -> {
                        // 激活状态：填充背景 + 高亮边框
                        g2.color = UIUtil.getListSelectionBackground(true)
                        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
                        g2.color = activeBorderColor
                        g2.stroke = BasicStroke(1f)
                        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))
                    }

                    hovered -> {
                        // 悬停状态：浅背景 + 普通边框
                        g2.color = UIUtil.getListSelectionBackground(false)
                        g2.fill(RoundRectangle2D.Float(0f, 0f, w, h, arc, arc))
                        g2.color = borderColor
                        g2.stroke = BasicStroke(1f)
                        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))
                    }

                    else -> {
                        // 普通状态：只有边框
                        g2.color = borderColor
                        g2.stroke = BasicStroke(1f)
                        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc))
                    }
                }

                val fm = g2.fontMetrics
                var x = JBUI.scale(8).toFloat()
                val centerY = h / 2

                // 状态圆点
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

                // 会话名称 - 使用 IDEA 主题文字颜色
                g2.color = when {
                    isActive -> UIUtil.getListSelectionForeground(true)
                    hovered -> UIUtil.getLabelForeground()
                    else -> UIUtil.getLabelForeground()
                }
                g2.font = font
                val textY = centerY + fm.ascent / 2 - fm.descent / 2 + 1
                // 计算文字可用宽度：总宽度 - 当前x位置 - 右边距 - 关闭按钮空间
                val closeSpace = if (canClose) closeButtonSize + closeButtonPadding else 0
                val availableTextWidth = width - x.toInt() - JBUI.scale(8) - closeSpace
                val displayTitle = getTruncatedTitle(availableTextWidth, fm)
                g2.drawString(displayTitle, x, textY)

                // 关闭按钮（悬停时显示）
                if (canClose && hovered) {
                    val closeX = width - closeButtonSize - closeButtonPadding
                    val closeY = (height - closeButtonSize) / 2

                    if (closeHovered) {
                        // 悬停在关闭按钮上：红色背景
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
                        // 普通状态：半透明背景
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

                    // 绘制 X
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
            // 发送重命名命令到前端
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

        // 只有在有真实 sessionId 时才显示复制选项
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
        // 在鼠标点击位置显示菜单
        popup.show(com.intellij.ui.awt.RelativePoint(e))
    }

    private fun copySessionId(session: JetBrainsSessionSummary, component: JComponent) {
        val sessionId = session.sessionId ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(sessionId))
        logger.info("Copied session ID: $sessionId")

        // 显示气泡提示
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
