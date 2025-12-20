package com.asakii.plugin.vcs

import com.asakii.plugin.mcp.git.CommitPanelAccessor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.IconLoader
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Generate Commit Message Action
 *
 * 在 Commit 面板的 message 工具栏中添加按钮，点击后使用 Claude 生成 commit message
 */
class GenerateCommitMessageAction : AnAction(
    "Generate Commit Message",
    "Use Claude AI to generate commit message based on selected changes",
    IconLoader.getIcon("/icons/claude-ai.svg", GenerateCommitMessageAction::class.java)
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // 检查是否有选中的变更
        val accessor = CommitPanelAccessor.getInstance(project)
        val hasChanges = accessor.getSelectedChanges()?.isNotEmpty() == true ||
                accessor.getAllChanges().isNotEmpty()

        e.presentation.isEnabledAndVisible = hasChanges
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        logger.info { "GenerateCommitMessageAction: triggered" }

        // 在后台任务中执行生成
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Generating Commit Message...",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing changes..."

                try {
                    val service = project.service<GenerateCommitMessageService>()
                    service.generateCommitMessage(indicator)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to generate commit message" }
                }
            }
        })
    }
}
