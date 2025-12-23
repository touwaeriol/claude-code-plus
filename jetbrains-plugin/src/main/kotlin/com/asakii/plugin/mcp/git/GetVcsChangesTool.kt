package com.asakii.plugin.mcp.git

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 获取 VCS 变更工具
 *
 * 返回未提交的文件变更列表，支持获取选中文件和 diff 内容
 * 返回格式：Markdown
 */
class GetVcsChangesTool(private val project: Project) {

    suspend fun execute(arguments: Map<String, Any?>): String {
        val selectedOnly = arguments["selectedOnly"] as? Boolean ?: false
        val includeDiff = arguments["includeDiff"] as? Boolean ?: true
        val maxFiles = (arguments["maxFiles"] as? Number)?.toInt() ?: 50
        val maxDiffLines = (arguments["maxDiffLines"] as? Number)?.toInt() ?: 100

        val accessor = CommitPanelAccessor.getInstance(project)

        // 获取所有变更和选中的变更
        val allChanges = accessor.getAllChanges()
        val selectedChanges = accessor.getSelectedChanges()

        // 根据 selectedOnly 参数决定返回哪些变更
        val changes: List<Change> = if (selectedOnly) {
            selectedChanges ?: allChanges
        } else {
            allChanges
        }.take(maxFiles)

        val hasSelectedChanges = selectedChanges != null && selectedChanges.isNotEmpty()
        val selectedPaths = selectedChanges?.map { getChangePath(it) }?.toSet() ?: emptySet()

        return buildString {
            appendLine("# VCS Changes")
            appendLine()

            // 状态信息
            appendLine("## Status")
            appendLine("- **Commit Panel Open**: ${if (accessor.isCommitPanelOpen()) "Yes" else "No"}")
            appendLine("- **Total Changes**: ${allChanges.size}")
            if (hasSelectedChanges) {
                appendLine("- **Selected Changes**: ${selectedChanges!!.size}")
            }
            appendLine("- **Showing**: ${if (selectedOnly && hasSelectedChanges) "Selected only" else "All changes"}")
            appendLine()

            if (changes.isEmpty()) {
                appendLine("*No changes found.*")
                return@buildString
            }

            // 变更列表
            appendLine("## Changes (${changes.size} files)")
            appendLine()

            for (change in changes) {
                val path = getChangePath(change)
                val isSelected = path in selectedPaths
                val selectedMarker = if (hasSelectedChanges) {
                    if (isSelected) "☑" else "☐"
                } else ""

                appendLine("### $selectedMarker `$path`")
                appendLine("- **Type**: ${change.type.name}")

                if (includeDiff) {
                    val diff = getDiff(change, maxDiffLines)
                    if (!diff.isNullOrBlank()) {
                        appendLine()
                        appendLine("```diff")
                        appendLine(diff)
                        appendLine("```")
                    }
                }
                appendLine()
            }
        }
    }

    private fun getChangePath(change: Change): String {
        return change.virtualFile?.path
            ?: change.afterRevision?.file?.path
            ?: change.beforeRevision?.file?.path
            ?: "unknown"
    }

    private fun getDiff(change: Change, maxLines: Int): String? {
        return try {
            ReadAction.compute<String?, Throwable> {
                val beforeContent = change.beforeRevision?.content
                val afterContent = change.afterRevision?.content

                when (change.type) {
                    Change.Type.NEW -> {
                        // 新文件：显示全部内容
                        afterContent?.lines()?.take(maxLines)?.joinToString("\n") { "+ $it" }
                    }
                    Change.Type.DELETED -> {
                        // 删除文件：显示被删除的内容
                        beforeContent?.lines()?.take(maxLines)?.joinToString("\n") { "- $it" }
                    }
                    Change.Type.MODIFICATION, Change.Type.MOVED -> {
                        // 修改或移动：显示简单的前后对比
                        buildString {
                            if (!beforeContent.isNullOrEmpty()) {
                                val beforeLines = beforeContent.lines().take(maxLines / 2)
                                if (beforeLines.isNotEmpty()) {
                                    appendLine("--- Before (${beforeLines.size} lines)")
                                    beforeLines.forEach { appendLine("- $it") }
                                }
                            }
                            if (!afterContent.isNullOrEmpty()) {
                                val afterLines = afterContent.lines().take(maxLines / 2)
                                if (afterLines.isNotEmpty()) {
                                    appendLine("+++ After (${afterLines.size} lines)")
                                    afterLines.forEach { appendLine("+ $it") }
                                }
                            }
                        }.takeIf { it.isNotBlank() }
                    }
                }
            }?.take(10000)  // 限制总长度
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get diff for ${change.virtualFile?.path}" }
            null
        }
    }
}
