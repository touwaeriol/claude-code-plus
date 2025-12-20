package com.asakii.plugin.mcp.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import mu.KotlinLogging
import java.lang.ref.WeakReference

private val logger = KotlinLogging.logger {}

/**
 * Commit 面板访问器（项目级单例）
 *
 * 解决问题：MCP 工具在后台线程运行，无法直接访问 Commit UI 组件
 * 解决方案：通过 CheckinHandlerFactory 捕获面板引用，存储在此访问器中
 *
 * 使用 CheckinProjectPanel 的公开 API（getCommitMessage/setCommitMessage）
 * 而不是通过反射访问内部类
 */
class CommitPanelAccessor(private val project: Project) {

    // 当前活跃的 CheckinProjectPanel（使用 WeakReference 避免内存泄漏）
    // CheckinProjectPanel 继承 CommitMessageI，提供 getCommitMessage/setCommitMessage 公开 API
    @Volatile
    private var checkinPanel: WeakReference<CheckinProjectPanel>? = null

    // 当前选中的变更（由 CheckinHandler 更新）
    @Volatile
    private var selectedChanges: List<Change>? = null

    /**
     * 设置当前的 CheckinProjectPanel（由 CheckinHandlerFactory 调用）
     * 使用公开 API，无需反射
     */
    fun setCheckinPanel(panel: CheckinProjectPanel) {
        checkinPanel = WeakReference(panel)
        logger.info { "CommitPanelAccessor: panel set for ${project.name}" }
    }

    /**
     * 更新选中的变更列表
     */
    fun updateSelectedChanges(changes: Collection<Change>) {
        selectedChanges = changes.toList()
        logger.debug { "CommitPanelAccessor: selectedChanges updated, count=${changes.size}" }
    }

    /**
     * 清除引用（面板关闭时调用）
     */
    fun clear() {
        checkinPanel = null
        selectedChanges = null
        logger.info { "CommitPanelAccessor: cleared" }
    }

    /**
     * 获取所有未提交的变更
     */
    fun getAllChanges(): List<Change> {
        return ReadAction.compute<List<Change>, Throwable> {
            val changeListManager = ChangeListManager.getInstance(project)
            changeListManager.allChanges.toList()
        }
    }

    /**
     * 获取选中的变更（如果 Commit 面板打开且有选中）
     * 如果没有选中或面板未打开，返回 null
     */
    fun getSelectedChanges(): List<Change>? {
        return selectedChanges?.takeIf { it.isNotEmpty() }
    }

    /**
     * 获取当前 commit message
     * 使用 CheckinProjectPanel.getCommitMessage() 公开 API
     */
    fun getCommitMessage(): String? {
        val panel = checkinPanel?.get() ?: return null
        return try {
            ReadAction.compute<String, Throwable> { panel.commitMessage }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get commit message" }
            null
        }
    }

    /**
     * 设置 commit message
     * 使用 CheckinProjectPanel.setCommitMessage() 公开 API
     *
     * @param message 要设置的消息
     * @param append 是否追加（true: 追加到现有消息后，false: 替换）
     */
    fun setCommitMessage(message: String, append: Boolean = false) {
        val panel = checkinPanel?.get()
        if (panel == null) {
            logger.warn { "Cannot set commit message: no active commit panel" }
            return
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                val currentMessage = panel.commitMessage
                if (append && currentMessage.isNotBlank()) {
                    panel.setCommitMessage("$currentMessage\n\n$message")
                } else {
                    panel.setCommitMessage(message)
                }
                logger.info { "Commit message ${if (append) "appended" else "set"}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to set commit message" }
            }
        }
    }

    /**
     * 检查 Commit 面板是否打开
     */
    fun isCommitPanelOpen(): Boolean {
        return checkinPanel?.get() != null
    }

    companion object {
        private val instances = mutableMapOf<Project, CommitPanelAccessor>()

        @JvmStatic
        fun getInstance(project: Project): CommitPanelAccessor {
            return synchronized(instances) {
                instances.getOrPut(project) { CommitPanelAccessor(project) }
            }
        }

        @JvmStatic
        fun dispose(project: Project) {
            synchronized(instances) {
                instances.remove(project)?.clear()
            }
        }
    }
}
