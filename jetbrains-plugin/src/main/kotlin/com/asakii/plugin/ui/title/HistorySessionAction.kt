package com.asakii.plugin.ui.title

import com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner
import com.asakii.claude.agent.sdk.utils.SessionMetadata
import com.asakii.rpc.api.JetBrainsSessionApi
import com.asakii.rpc.api.JetBrainsSessionCommand
import com.asakii.rpc.api.JetBrainsSessionCommandType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger

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
    private val timeFormat = SimpleDateFormat("HH:mm")
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm")

    override fun actionPerformed(e: AnActionEvent) {
        logger.info("🔍 [HistorySessionAction] 点击历史会话按钮")

        // 异步加载历史会话，避免阻塞 UI
        ApplicationManager.getApplication().executeOnPooledThread {
            val projectPath = project.basePath ?: return@executeOnPooledThread
            logger.info("🔍 [HistorySessionAction] 扫描项目历史会话: $projectPath")

            val sessions = ClaudeSessionScanner.scanHistorySessions(projectPath, 20, 0)
            logger.info("🔍 [HistorySessionAction] 找到 ${sessions.size} 个历史会话")

            // 回到 UI 线程显示弹出菜单
            ApplicationManager.getApplication().invokeLater {
                showSessionPopup(e, sessions)
            }
        }
    }

    private fun showSessionPopup(e: AnActionEvent, sessions: List<SessionMetadata>) {
        if (sessions.isEmpty()) {
            logger.info("[HistorySessionAction] 没有历史会话")
            // 显示空状态
            val emptyGroup = DefaultActionGroup().apply {
                add(object : AnAction("暂无历史会话", null, null) {
                    override fun actionPerformed(e: AnActionEvent) {}
                    override fun update(e: AnActionEvent) {
                        e.presentation.isEnabled = false
                    }
                })
            }
            showPopup(e, emptyGroup, "历史会话")
            return
        }

        // 获取当前活动会话（用于标记）
        val currentState = sessionApi.getState()
        val activeSessionIds = currentState?.sessions?.mapNotNull { it.sessionId }?.toSet() ?: emptySet()

        // 创建弹出菜单
        val actionGroup = DefaultActionGroup()

        // 按日期分组显示
        var lastDateGroup: String? = null
        val now = System.currentTimeMillis()

        sessions.forEach { session ->
            val dateGroup = getDateGroup(session.timestamp, now)

            // 添加日期分组标题
            if (dateGroup != lastDateGroup) {
                if (lastDateGroup != null) {
                    actionGroup.add(Separator.create())
                }
                // 添加分组标题
                actionGroup.add(Separator.create(dateGroup))
                lastDateGroup = dateGroup
            }

            val isActive = activeSessionIds.contains(session.sessionId)
            val icon = if (isActive) AllIcons.Actions.Checked else AllIcons.FileTypes.Any_type
            val timeStr = formatSessionTime(session.timestamp, now)
            val preview = session.firstUserMessage.take(35).replace("\n", " ").trim()
            val displayPreview = if (preview.isEmpty()) "新会话" else preview
            val title = if (isActive) "● $displayPreview" else displayPreview
            val description = "$timeStr · ${session.messageCount} 条消息"

            actionGroup.add(object : AnAction(title, description, icon) {
                override fun actionPerformed(e: AnActionEvent) {
                    logger.info("🔍 [HistorySessionAction] 选择会话: ${session.sessionId}")
                    // 发送命令给前端加载该会话
                    sessionApi.sendCommand(
                        JetBrainsSessionCommand(
                            type = JetBrainsSessionCommandType.SWITCH,
                            sessionId = session.sessionId
                        )
                    )
                }
            })
        }

        showPopup(e, actionGroup, "历史会话 (${sessions.size})")
    }

    private fun showPopup(e: AnActionEvent, actionGroup: DefaultActionGroup, title: String) {
        val popup = JBPopupFactory.getInstance()
            .createActionGroupPopup(
                title,
                actionGroup,
                e.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            )

        val component = e.inputEvent?.component
        if (component != null) {
            // 计算向左展开的位置（避免超出 IDEA 窗口）
            val point = component.locationOnScreen
            // 在组件左下角显示弹出菜单
            val popupX = point.x - popup.content.preferredSize.width + component.width
            val popupY = point.y + component.height
            popup.showInScreenCoordinates(component, java.awt.Point(popupX, popupY))
        } else {
            popup.showInFocusCenter()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }

    /**
     * 获取日期分组标题（今天、昨天、本周、更早）
     */
    private fun getDateGroup(timestamp: Long, now: Long): String {
        val dayMs = 24 * 60 * 60 * 1000L
        val diff = now - timestamp
        val days = diff / dayMs

        return when {
            days < 1 -> "今天"
            days < 2 -> "昨天"
            days < 7 -> "本周"
            days < 30 -> "本月"
            else -> "更早"
        }
    }

    /**
     * 格式化会话时间（今天显示 HH:mm，其他显示 MM-dd HH:mm）
     */
    private fun formatSessionTime(timestamp: Long, now: Long): String {
        val dayMs = 24 * 60 * 60 * 1000L
        val diff = now - timestamp
        val days = diff / dayMs
        val date = Date(timestamp)

        return when {
            days < 1 -> "今天 ${timeFormat.format(date)}"
            days < 2 -> "昨天 ${timeFormat.format(date)}"
            else -> dateTimeFormat.format(date)
        }
    }
}
