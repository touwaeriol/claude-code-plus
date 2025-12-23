package com.asakii.plugin.vcs

import com.asakii.plugin.mcp.git.CommitPanelAccessor
import com.asakii.settings.AgentSettingsService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.IconLoader
import mu.KotlinLogging
import java.util.concurrent.Executors

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

        val settings = AgentSettingsService.getInstance()

        if (settings.gitGenerateShowProgress) {
            // 显示详细进度对话框
            runWithProgressDialog(project)
        } else {
            // 使用后台任务（简单模式）
            runWithBackgroundTask(project)
        }
    }

    /**
     * 使用进度对话框运行（详细模式）
     */
    private fun runWithProgressDialog(project: com.intellij.openapi.project.Project) {
        // 在 EDT 上创建并显示对话框
        ApplicationManager.getApplication().invokeLater {
            val dialog = GitGenerateProgressDialog(project)

            // 在后台线程执行 Claude 调用
            val executor = Executors.newSingleThreadExecutor()
            executor.submit {
                try {
                    val service = project.service<GenerateCommitMessageService>()
                    service.generateCommitMessageWithDialog(dialog)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to generate commit message" }
                    dialog.appendError(e.message ?: "Unknown error")
                    dialog.markComplete(false)
                } finally {
                    executor.shutdown()
                }
            }

            // 显示对话框（非模态，允许取消）
            dialog.show()
        }
    }

    /**
     * 使用后台任务运行（简单模式）
     */
    private fun runWithBackgroundTask(project: com.intellij.openapi.project.Project) {
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
