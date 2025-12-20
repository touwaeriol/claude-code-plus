package com.asakii.plugin.vcs

import com.asakii.plugin.mcp.git.CommitPanelAccessor
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Claude Checkin Handler Factory
 *
 * 用于捕获 Commit 面板引用，使 Git MCP 工具能够访问 commit message 输入框
 *
 * 使用 CheckinProjectPanel 的公开 API（getCommitMessage/setCommitMessage）
 * 而不是通过反射访问内部类
 */
class ClaudeCheckinHandlerFactory : CheckinHandlerFactory() {

    override fun createHandler(
        panel: CheckinProjectPanel,
        commitContext: CommitContext
    ): CheckinHandler {
        logger.info { "ClaudeCheckinHandlerFactory: creating handler for ${panel.project.name}" }
        return ClaudeCheckinHandler(panel)
    }
}

/**
 * Claude Checkin Handler
 *
 * 在 Commit 流程中捕获面板引用并更新选中的变更
 * 使用 CheckinProjectPanel 公开 API，无需反射
 */
class ClaudeCheckinHandler(
    private val panel: CheckinProjectPanel
) : CheckinHandler() {

    private val project = panel.project
    private val accessor = CommitPanelAccessor.getInstance(project)

    init {
        logger.info { "ClaudeCheckinHandler: initializing for ${project.name}" }

        // 直接存储 panel 引用，使用其公开 API
        accessor.setCheckinPanel(panel)
        logger.info { "ClaudeCheckinHandler: panel captured (using public API)" }

        // 更新选中的变更
        updateSelectedChanges()
    }

    private fun updateSelectedChanges() {
        try {
            val selectedChanges = panel.selectedChanges
            accessor.updateSelectedChanges(selectedChanges)
            logger.debug { "ClaudeCheckinHandler: updated ${selectedChanges.size} selected changes" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update selected changes" }
        }
    }

    override fun beforeCheckin(): ReturnResult {
        // 在提交前更新一次选中状态
        updateSelectedChanges()
        return ReturnResult.COMMIT
    }

    override fun checkinSuccessful() {
        logger.info { "ClaudeCheckinHandler: checkin successful, clearing accessor" }
        accessor.clear()
    }

    override fun checkinFailed(exception: MutableList<VcsException>) {
        logger.info { "ClaudeCheckinHandler: checkin failed, clearing accessor" }
        accessor.clear()
    }
}
