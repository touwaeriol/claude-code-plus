package com.asakii.plugin.mcp.git

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 获取 VCS 变更工具
 *
 * 返回未提交的文件变更列表，支持获取选中文件和 diff 内容
 */
class GetVcsChangesTool(private val project: Project) {

    suspend fun execute(arguments: Map<String, Any?>): String {
        val selectedOnly = arguments["selectedOnly"] as? Boolean ?: false
        val includeDiff = arguments["includeDiff"] as? Boolean ?: true
        val maxFiles = (arguments["maxFiles"] as? Number)?.toInt() ?: 50
        val maxDiffLines = (arguments["maxDiffLines"] as? Number)?.toInt() ?: 100

        val accessor = CommitPanelAccessor.getInstance(project)

        // 获取变更列表
        val changes: List<Change> = if (selectedOnly) {
            accessor.getSelectedChanges() ?: accessor.getAllChanges()
        } else {
            accessor.getAllChanges()
        }.take(maxFiles)

        val hasSelectedChanges = selectedOnly && accessor.getSelectedChanges() != null

        val result = buildJsonObject {
            put("commitPanelOpen", accessor.isCommitPanelOpen())
            put("selectedOnly", hasSelectedChanges)
            put("totalChanges", changes.size)

            putJsonArray("changes") {
                for (change in changes) {
                    addJsonObject {
                        val path = change.virtualFile?.path
                            ?: change.afterRevision?.file?.path
                            ?: change.beforeRevision?.file?.path
                            ?: "unknown"
                        put("path", path)
                        put("type", change.type.name)  // NEW, MODIFICATION, DELETED, MOVED

                        if (includeDiff) {
                            val diff = getDiff(change, maxDiffLines)
                            if (diff != null) {
                                put("diff", diff)
                            }
                        }
                    }
                }
            }
        }

        return result.toString()
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
                                    appendLine("--- Before (${beforeLines.size} lines):")
                                    beforeLines.forEach { appendLine("- $it") }
                                }
                            }
                            if (!afterContent.isNullOrEmpty()) {
                                val afterLines = afterContent.lines().take(maxLines / 2)
                                if (afterLines.isNotEmpty()) {
                                    appendLine("+++ After (${afterLines.size} lines):")
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
