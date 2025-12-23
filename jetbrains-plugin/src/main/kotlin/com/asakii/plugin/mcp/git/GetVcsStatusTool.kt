package com.asakii.plugin.mcp.git

import com.asakii.plugin.compat.VcsCompat
import com.asakii.plugin.services.GitBranchService
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 获取 VCS 状态工具
 *
 * 返回 VCS 状态概览：当前分支、变更数量等
 * 返回格式：Markdown
 *
 * 使用 GitBranchService 获取 Git 分支信息（无需反射）：
 * - 当 Git4Idea 已安装: 使用 GitBranchServiceImpl
 * - 当 Git4Idea 未安装: 使用 NoopGitBranchService
 */
class GetVcsStatusTool(private val project: Project) {

    suspend fun execute(arguments: Map<String, Any?>): String {
        return ReadAction.compute<String, Throwable> {
            try {
                val changeListManager = ChangeListManager.getInstance(project)
                val changes = changeListManager.allChanges

                // 获取活跃的 VCS (使用兼容层以支持不同 IDE 版本)
                val activeVcss = VcsCompat.getAllActiveVcss(project)
                val hasVcs = activeVcss.isNotEmpty()
                val vcsType = activeVcss.firstOrNull()?.name

                // 使用 GitBranchService 获取当前分支（无反射，使用可选依赖模式）
                val gitBranchService = GitBranchService.getInstance(project)
                val currentBranch = gitBranchService.getCurrentBranchName()

                // 按变更类型统计
                val newCount = changes.count { it.type == Change.Type.NEW }
                val modifiedCount = changes.count { it.type == Change.Type.MODIFICATION }
                val deletedCount = changes.count { it.type == Change.Type.DELETED }
                val movedCount = changes.count { it.type == Change.Type.MOVED }
                val unversionedCount = changeListManager.unversionedFilesPaths.size

                buildString {
                    appendLine("# VCS Status")
                    appendLine()

                    // 基本信息
                    appendLine("## Overview")
                    appendLine("- **VCS Enabled**: ${if (hasVcs) "Yes" else "No"}")
                    if (vcsType != null) {
                        appendLine("- **VCS Type**: $vcsType")
                    }
                    if (currentBranch != null) {
                        appendLine("- **Current Branch**: `$currentBranch`")
                    }
                    appendLine()

                    // 变更统计
                    appendLine("## Changes Summary")
                    appendLine("| Type | Count |")
                    appendLine("|------|-------|")
                    appendLine("| New | $newCount |")
                    appendLine("| Modified | $modifiedCount |")
                    appendLine("| Deleted | $deletedCount |")
                    appendLine("| Moved | $movedCount |")
                    appendLine("| **Total** | **${changes.size}** |")
                    if (unversionedCount > 0) {
                        appendLine("| Unversioned | $unversionedCount |")
                    }
                    appendLine()

                    // 变更列表信息
                    val changeLists = changeListManager.changeLists
                    if (changeLists.isNotEmpty()) {
                        appendLine("## Change Lists")
                        for (changeList in changeLists) {
                            val defaultMarker = if (changeList.isDefault) " (default)" else ""
                            appendLine("- **${changeList.name}**$defaultMarker: ${changeList.changes.size} changes")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to get VCS status" }
                buildString {
                    appendLine("# VCS Status")
                    appendLine()
                    appendLine("**Error**: ${e.message ?: "Unknown error"}")
                    appendLine()
                    appendLine("- **VCS Enabled**: No")
                }
            }
        }
    }
}
