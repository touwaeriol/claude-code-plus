package com.asakii.plugin.mcp.git

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.intellij.vcs.commit.CommitMessageUi
import mu.KotlinLogging
import java.lang.ref.WeakReference

private val logger = KotlinLogging.logger {}

/**
 * Commit 面板访问器（项目级单例）
 *
 * 解决问题：MCP 工具在后台线程运行，无法直接访问 Commit UI 组件
 * 解决方案：通过 CheckinHandlerFactory 捕获面板引用，存储在此访问器中
 */
class CommitPanelAccessor(private val project: Project) {

    // 当前活跃的 Commit 面板（使用 WeakReference 避免内存泄漏）
    @Volatile
    private var commitWorkflowHandler: WeakReference<AbstractCommitWorkflowHandler<*, *>>? = null

    // CommitMessageUi 引用
    @Volatile
    private var commitMessageUi: WeakReference<CommitMessageUi>? = null

    // 当前选中的变更（由 CheckinHandler 更新）
    @Volatile
    private var selectedChanges: List<Change>? = null

    /**
     * 设置当前的 CommitWorkflowHandler（由 CheckinHandlerFactory 调用）
     */
    fun setCommitWorkflowHandler(handler: AbstractCommitWorkflowHandler<*, *>) {
        commitWorkflowHandler = WeakReference(handler)
        logger.info { "CommitPanelAccessor: handler set for ${project.name}" }
    }

    /**
     * 设置 CommitMessageUi（由 CheckinHandler 调用）
     */
    fun setCommitMessageUi(ui: CommitMessageUi) {
        commitMessageUi = WeakReference(ui)
        logger.info { "CommitPanelAccessor: commitMessageUi set" }
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
        commitWorkflowHandler = null
        commitMessageUi = null
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
     */
    fun getCommitMessage(): String? {
        val ui = commitMessageUi?.get()
        if (ui != null) {
            return try {
                ReadAction.compute<String, Throwable> { ui.text }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to get commit message from ui" }
                null
            }
        }

        // 尝试从 handler 获取
        val handler = commitWorkflowHandler?.get() ?: return null
        return try {
            // AbstractCommitWorkflowHandler 有 getCommitMessage 方法
            ReadAction.compute<String, Throwable> {
                handler.javaClass.methods
                    .find { it.name == "getCommitMessage" && it.parameterCount == 0 }
                    ?.invoke(handler) as? String
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get commit message from handler" }
            null
        }
    }

    /**
     * 设置 commit message
     * @param message 要设置的消息
     * @param append 是否追加（true: 追加到现有消息后，false: 替换）
     */
    fun setCommitMessage(message: String, append: Boolean = false) {
        val ui = commitMessageUi?.get()
        if (ui == null) {
            logger.warn { "Cannot set commit message: no active commit panel" }
            return
        }

        ApplicationManager.getApplication().invokeLater {
            try {
                if (append && ui.text.isNotBlank()) {
                    ui.setText("${ui.text}\n\n$message")
                } else {
                    ui.setText(message)
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
        return commitMessageUi?.get() != null || commitWorkflowHandler?.get() != null
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
