package com.asakii.plugin.ui.title

import com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner
import com.asakii.claude.agent.sdk.utils.SessionMetadata
import com.asakii.rpc.api.JetBrainsSessionApi
import com.asakii.rpc.api.JetBrainsSessionCommand
import com.asakii.rpc.api.JetBrainsSessionCommandType
import com.asakii.rpc.api.JetBrainsSessionSummary
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger
import javax.swing.*

/**
 * 历史会话列表项类型
 */
sealed class SessionListItem {
    data class GroupHeader(val title: String) : SessionListItem()
    data class SessionItem(
        val session: SessionMetadata,
        val isActive: Boolean,
        val timeStr: String,
        val preview: String
    ) : SessionListItem()

    data object LoadMore : SessionListItem()
}

/**
 * 自定义会话列表项渲染器 - 双行显示
 */
class SessionListCellRenderer : ListCellRenderer<SessionListItem> {

    override fun getListCellRendererComponent(
        list: JList<out SessionListItem>,
        value: SessionListItem,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return when (value) {
            is SessionListItem.GroupHeader -> createGroupHeader(value, isSelected)
            is SessionListItem.SessionItem -> createSessionItem(value, isSelected)
            is SessionListItem.LoadMore -> createLoadMore(isSelected)
        }
    }

    private fun createSessionItem(item: SessionListItem.SessionItem, isSelected: Boolean): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8)
            background = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()

            // 左侧图标
            val iconLabel = JLabel(
                if (item.isActive) AllIcons.Actions.Checked else AllIcons.FileTypes.Any_type
            )
            add(iconLabel, BorderLayout.WEST)

            // 右侧文字区域（双行）
            val textPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.emptyLeft(8)

                // 第一行：标题
                add(JLabel(item.preview).apply {
                    font = JBUI.Fonts.label()
                    foreground = if (isSelected) UIUtil.getListSelectionForeground(true)
                    else UIUtil.getLabelForeground()
                })

                // 第二行：时间 + 消息数
                add(JLabel("${item.timeStr} · ${item.session.messageCount} 条消息").apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = if (isSelected) UIUtil.getListSelectionForeground(true)
                    else UIUtil.getLabelDisabledForeground()
                })
            }
            add(textPanel, BorderLayout.CENTER)
        }
    }

    private fun createGroupHeader(header: SessionListItem.GroupHeader, isSelected: Boolean): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 8, 4, 8)
            isOpaque = false
            add(JLabel(header.title).apply {
                font = JBUI.Fonts.miniFont()
                foreground = UIUtil.getLabelDisabledForeground()
            }, BorderLayout.WEST)
        }
    }

    private fun createLoadMore(isSelected: Boolean): JPanel {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            background = if (isSelected) UIUtil.getListSelectionBackground(true) else UIUtil.getListBackground()
            add(JLabel("加载更多...", AllIcons.General.ArrowDown, SwingConstants.LEFT).apply {
                foreground = if (isSelected) UIUtil.getListSelectionForeground(true)
                else JBColor.BLUE
            }, BorderLayout.CENTER)
        }
    }
}

/**
 * 历史会话按钮 - 显示在 ToolWindow 标题栏右侧
 *
 * 点击后显示 IDEA 弹出菜单，列出项目的历史会话（从 ~/.claude/projects/ 扫描）
 * 用户选择后，反向调用前端加载该会话
 */
class HistorySessionAction(
    private val sessionApi: JetBrainsSessionApi,
    private val project: Project
) : AnAction("历史会话", "查看历史会话", AllIcons.Actions.Search) {

    private val logger = Logger.getLogger(HistorySessionAction::class.java.name)
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm")

    // 分页状态
    private var currentOffset = 0
    private var hasMore = true
    private val pageSize = 10  // 总显示数量（激活 + 历史）
    private var cachedSessions: MutableList<SessionMetadata> = mutableListOf()
    private var lastEvent: AnActionEvent? = null
    private var currentPopup: JBPopup? = null
    private var isLoading = false

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("🔍 [HistorySessionAction] 点击历史会话按钮")
        lastEvent = e

        // 重置分页状态
        currentOffset = 0
        hasMore = true
        cachedSessions.clear()
        isLoading = true

        // 先显示弹窗（带加载状态），再异步加载数据
        showLoadingPopup(e)
        loadSessions(e, reset = true)
    }

    /**
     * 显示加载中状态的弹窗（先显示激活会话，然后显示加载中）
     */
    private fun showLoadingPopup(e: AnActionEvent) {
        // 获取当前活动会话（即使在加载中也可以显示）
        val currentState = sessionApi.getState()
        val activeSessions = currentState?.sessions ?: emptyList()

        val items = mutableListOf<SessionListItem>()

        // 先显示激活会话
        if (activeSessions.isNotEmpty()) {
            val now = System.currentTimeMillis()
            items.add(SessionListItem.GroupHeader("激活中"))
            activeSessions.forEach { session ->
                val displayTitle = session.title.take(35).replace("\n", " ").trim().ifEmpty { "新会话" }
                val metadata = SessionMetadata(
                    sessionId = session.sessionId ?: session.id,
                    timestamp = now,
                    messageCount = 0,
                    firstUserMessage = session.title,
                    projectPath = project.basePath ?: "",
                    customTitle = null
                )
                items.add(
                    SessionListItem.SessionItem(
                        session = metadata,
                        isActive = true,
                        timeStr = if (session.isGenerating) "生成中" else if (session.isConnecting) "连接中" else "已连接",
                        preview = displayTitle
                    )
                )
            }
        }

        // 历史会话加载中
        items.add(SessionListItem.GroupHeader("历史加载中..."))

        val sessionCount = items.filterIsInstance<SessionListItem.SessionItem>().size
        showPopupWithItems(e, items, sessionCount)
    }

    /**
     * 加载历史会话
     */
    private fun loadSessions(e: AnActionEvent, reset: Boolean = false) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val projectPath = project.basePath ?: return@executeOnPooledThread

            // 获取当前激活会话数量
            val currentState = sessionApi.getState()
            val activeSessionIds = currentState?.sessions?.mapNotNull { it.sessionId }?.toSet() ?: emptySet()
            val activeCount = activeSessionIds.size

            // 历史会话需要加载的数量 = pageSize - 激活会话数量
            val historyToLoad = maxOf(pageSize - activeCount, 1)

            logger.info("🔍 [HistorySessionAction] 扫描项目历史会话: $projectPath, offset=$currentOffset, historyToLoad=$historyToLoad (activeCount=$activeCount)")

            val sessions = ClaudeSessionScanner.scanHistorySessions(projectPath, historyToLoad, currentOffset)
            logger.info("🔍 [HistorySessionAction] 找到 ${sessions.size} 个历史会话")

            // 更新分页状态
            hasMore = sessions.size >= historyToLoad
            if (reset) {
                cachedSessions.clear()
            }
            cachedSessions.addAll(sessions)
            currentOffset += sessions.size
            isLoading = false

            // 回到 UI 线程显示弹出菜单
            ApplicationManager.getApplication().invokeLater {
                // 关闭加载中的弹窗
                currentPopup?.cancel()
                showSessionPopup(e, cachedSessions.toList())
            }
        }
    }

    /**
     * 加载更多会话
     */
    private fun loadMoreSessions() {
        lastEvent?.let { e ->
            isLoading = true
            // 关闭当前弹窗
            currentPopup?.cancel()
            // 显示加载中状态
            showLoadingPopupWithCurrent(e)
            // 加载下一页
            loadSessions(e, reset = false)
        }
    }

    /**
     * 显示加载中状态（保留当前已加载的数据）
     */
    private fun showLoadingPopupWithCurrent(e: AnActionEvent) {
        val currentState = sessionApi.getState()
        val activeSessions = currentState?.sessions ?: emptyList()
        val activeSessionIds = activeSessions.mapNotNull { it.sessionId }.toSet()

        // 历史会话排除激活的
        val filteredHistory = cachedSessions.filter { !activeSessionIds.contains(it.sessionId) }

        val items = buildListItems(activeSessions, filteredHistory, hasMore = false)
        val mutableItems = items.toMutableList()
        mutableItems.add(SessionListItem.GroupHeader("加载更多中..."))

        val sessionCount = mutableItems.filterIsInstance<SessionListItem.SessionItem>().size
        showPopupWithItems(e, mutableItems, sessionCount)
    }

    private fun showSessionPopup(e: AnActionEvent, historySessions: List<SessionMetadata>) {
        // 获取当前活动会话
        val currentState = sessionApi.getState()
        val activeSessions = currentState?.sessions ?: emptyList()
        val activeSessionIds = activeSessions.mapNotNull { it.sessionId }.toSet()

        // 历史会话排除激活的
        val filteredHistory = historySessions.filter { !activeSessionIds.contains(it.sessionId) }

        // 如果激活会话和历史会话都为空
        if (activeSessions.isEmpty() && filteredHistory.isEmpty()) {
            logger.info("[HistorySessionAction] 没有历史会话")
            val emptyItems = listOf(SessionListItem.GroupHeader("暂无历史会话"))
            showPopupWithItems(e, emptyItems, 0)
            return
        }

        // 构建列表项
        val items = buildListItems(activeSessions, filteredHistory, hasMore)
        val sessionCount = items.filterIsInstance<SessionListItem.SessionItem>().size

        showPopupWithItems(e, items, sessionCount)
    }

    /**
     * 使用 PopupChooserBuilder 显示弹窗
     */
    private fun showPopupWithItems(e: AnActionEvent, items: List<SessionListItem>, sessionCount: Int) {
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle("历史会话 ($sessionCount)")
            .setRenderer(SessionListCellRenderer())
            .setItemChosenCallback { selected ->
                when (selected) {
                    is SessionListItem.SessionItem -> {
                        logger.info("🔍 [HistorySessionAction] 选择会话: ${selected.session.sessionId}")
                        sessionApi.sendCommand(
                            JetBrainsSessionCommand(
                                type = JetBrainsSessionCommandType.SWITCH,
                                sessionId = selected.session.sessionId
                            )
                        )
                    }

                    is SessionListItem.LoadMore -> {
                        loadMoreSessions()
                    }

                    else -> {}
                }
            }
            .setNamerForFiltering { item ->
                when (item) {
                    is SessionListItem.SessionItem -> item.preview
                    else -> ""
                }
            }
            .setMovable(true)
            .setResizable(true)
            .createPopup()

        currentPopup = popup

        // 显示弹窗
        val component = e.inputEvent?.component
        if (component != null) {
            popup.showUnderneathOf(component)
        } else {
            popup.showInFocusCenter()
        }
    }

    /**
     * 构建列表项（带分组）
     * @param activeSessions 激活中的会话（从 sessionApi 获取）
     * @param historySessions 历史会话（从文件扫描获取，已排除激活会话）
     * @param hasMore 是否有更多历史会话
     */
    private fun buildListItems(
        activeSessions: List<JetBrainsSessionSummary>,
        historySessions: List<SessionMetadata>,
        hasMore: Boolean
    ): List<SessionListItem> {
        val items = mutableListOf<SessionListItem>()
        val now = System.currentTimeMillis()

        // 激活中分组
        if (activeSessions.isNotEmpty()) {
            items.add(SessionListItem.GroupHeader("激活中"))
            activeSessions.forEach { session ->
                val displayTitle = session.title.take(35).replace("\n", " ").trim().ifEmpty { "新会话" }
                // 创建一个虚拟的 SessionMetadata 用于兼容现有的 SessionListItem
                val metadata = SessionMetadata(
                    sessionId = session.sessionId ?: session.id,
                    timestamp = now,
                    messageCount = 0,
                    firstUserMessage = session.title,
                    projectPath = project.basePath ?: "",
                    customTitle = null
                )
                items.add(
                    SessionListItem.SessionItem(
                        session = metadata,
                        isActive = true,
                        timeStr = if (session.isGenerating) "生成中" else if (session.isConnecting) "连接中" else "已连接",
                        preview = displayTitle
                    )
                )
            }
        }

        // 历史分组
        if (historySessions.isNotEmpty()) {
            items.add(SessionListItem.GroupHeader("历史"))
            historySessions.forEach { session ->
                // 优先使用 customTitle，否则使用 firstUserMessage
                val displayTitle = (session.customTitle ?: session.firstUserMessage)
                    .take(35).replace("\n", " ").trim()
                    .ifEmpty { "新会话" }
                items.add(
                    SessionListItem.SessionItem(
                        session = session,
                        isActive = false,
                        timeStr = formatRelativeTime(session.timestamp, now),
                        preview = displayTitle
                    )
                )
            }
        }

        // 加载更多
        if (hasMore) {
            items.add(SessionListItem.LoadMore)
        }

        return items
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }

    /**
     * 格式化相对时间（参考 Web 端）
     */
    private fun formatRelativeTime(timestamp: Long, now: Long): String {
        val diff = now - timestamp
        val minutes = diff / 60000
        val hours = diff / 3600000
        val days = diff / 86400000

        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 7 -> "${days}天前"
            else -> dateTimeFormat.format(Date(timestamp))
        }
    }
}
