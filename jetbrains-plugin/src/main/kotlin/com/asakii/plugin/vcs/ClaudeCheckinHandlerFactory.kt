package com.asakii.plugin.vcs

import com.asakii.plugin.mcp.git.CommitPanelAccessor
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.intellij.vcs.commit.CommitMessageUi
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Claude Checkin Handler Factory
 *
 * 用于捕获 Commit 面板引用，使 Git MCP 工具能够访问 commit message 输入框
 */
class ClaudeCheckinHandlerFactory : CheckinHandlerFactory() {

    override fun createHandler(
        panel: CheckinProjectPanel,
        commitContext: CommitContext
    ): CheckinHandler {
        logger.info { "ClaudeCheckinHandlerFactory: creating handler for ${panel.project.name}" }
        return ClaudeCheckinHandler(panel, commitContext)
    }
}

/**
 * Claude Checkin Handler
 *
 * 在 Commit 流程中捕获面板引用并更新选中的变更
 */
class ClaudeCheckinHandler(
    private val panel: CheckinProjectPanel,
    private val commitContext: CommitContext
) : CheckinHandler() {

    private val project = panel.project
    private val accessor = CommitPanelAccessor.getInstance(project)

    init {
        logger.info { "ClaudeCheckinHandler: initializing for ${project.name}" }

        // 尝试获取 CommitWorkflowHandler（通过反射，因为 WORKFLOW_HANDLER 在某些版本不可用）
        tryGetWorkflowHandler()

        // 更新选中的变更
        updateSelectedChanges()
    }

    /**
     * 尝试获取 CommitWorkflowHandler
     */
    private fun tryGetWorkflowHandler() {
        try {
            // 尝试通过反射获取 WORKFLOW_HANDLER 常量
            val workflowHandlerKeyClass = AbstractCommitWorkflowHandler::class.java
            val workflowHandlerField = workflowHandlerKeyClass.declaredFields.find {
                it.name == "WORKFLOW_HANDLER" || it.name.contains("WORKFLOW", ignoreCase = true)
            }

            if (workflowHandlerField != null) {
                workflowHandlerField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val key = workflowHandlerField.get(null) as? com.intellij.openapi.util.Key<AbstractCommitWorkflowHandler<*, *>>
                if (key != null) {
                    val workflowHandler = commitContext.getUserData(key)
                    if (workflowHandler != null) {
                        accessor.setCommitWorkflowHandler(workflowHandler)
                        logger.info { "ClaudeCheckinHandler: workflow handler captured" }
                        tryGetCommitMessageUi(workflowHandler)
                        return
                    }
                }
            }

            // 如果无法通过 key 获取，尝试从 panel 直接获取
            tryGetWorkflowHandlerFromPanel()

        } catch (e: Exception) {
            logger.warn(e) { "Failed to get workflow handler" }
        }
    }

    /**
     * 尝试从 panel 获取 workflow handler
     */
    private fun tryGetWorkflowHandlerFromPanel() {
        try {
            // CheckinProjectPanel 可能有 getCommitMessage 方法
            val commitMessageMethod = panel.javaClass.methods.find {
                it.name == "getCommitMessage" && it.parameterCount == 0
            }
            if (commitMessageMethod != null) {
                // 如果有，说明可以直接从 panel 获取 commit message
                logger.info { "ClaudeCheckinHandler: panel has getCommitMessage method" }
            }
        } catch (e: Exception) {
            logger.debug { "Failed to get workflow handler from panel: ${e.message}" }
        }
    }

    /**
     * 尝试从 workflow handler 获取 CommitMessageUi
     */
    private fun tryGetCommitMessageUi(workflowHandler: AbstractCommitWorkflowHandler<*, *>) {
        try {
            // 通过反射获取 commitMessageUi 字段
            val uiField = workflowHandler.javaClass.declaredFields.find {
                CommitMessageUi::class.java.isAssignableFrom(it.type) ||
                it.name.contains("commitMessage", ignoreCase = true)
            }

            if (uiField != null) {
                uiField.isAccessible = true
                val ui = uiField.get(workflowHandler)
                if (ui is CommitMessageUi) {
                    accessor.setCommitMessageUi(ui)
                    logger.info { "ClaudeCheckinHandler: CommitMessageUi captured via field ${uiField.name}" }
                    return
                }
            }

            // 尝试通过方法获取
            val uiMethod = workflowHandler.javaClass.declaredMethods.find {
                it.name.contains("getCommitMessage", ignoreCase = true) &&
                it.parameterCount == 0 &&
                CommitMessageUi::class.java.isAssignableFrom(it.returnType)
            }

            if (uiMethod != null) {
                uiMethod.isAccessible = true
                val ui = uiMethod.invoke(workflowHandler)
                if (ui is CommitMessageUi) {
                    accessor.setCommitMessageUi(ui)
                    logger.info { "ClaudeCheckinHandler: CommitMessageUi captured via method ${uiMethod.name}" }
                    return
                }
            }

            logger.warn { "ClaudeCheckinHandler: Could not find CommitMessageUi" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get CommitMessageUi" }
        }
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
