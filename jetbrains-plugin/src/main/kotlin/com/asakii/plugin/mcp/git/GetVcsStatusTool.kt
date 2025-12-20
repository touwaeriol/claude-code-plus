package com.asakii.plugin.mcp.git

import com.asakii.plugin.compat.VcsCompat
import com.asakii.plugin.services.GitBranchService
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 获取 VCS 状态工具
 *
 * 返回 VCS 状态概览：当前分支、变更数量等
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

                buildJsonObject {
                    put("hasVcs", hasVcs)
                    put("vcsType", vcsType)
                    put("currentBranch", currentBranch)
                    put("totalChanges", changes.size)
                    put("unversionedFiles", changeListManager.unversionedFilesPaths.size)

                    // 按变更类型统计
                    putJsonObject("changesByType") {
                        put("new", changes.count { it.type == Change.Type.NEW })
                        put("modified", changes.count { it.type == Change.Type.MODIFICATION })
                        put("deleted", changes.count { it.type == Change.Type.DELETED })
                        put("moved", changes.count { it.type == Change.Type.MOVED })
                    }

                    // 变更列表信息
                    putJsonArray("changeLists") {
                        for (changeList in changeListManager.changeLists) {
                            addJsonObject {
                                put("name", changeList.name)
                                put("isDefault", changeList.isDefault)
                                put("changesCount", changeList.changes.size)
                            }
                        }
                    }
                }.toString()
            } catch (e: Exception) {
                logger.error(e) { "Failed to get VCS status" }
                buildJsonObject {
                    put("error", e.message ?: "Unknown error")
                    put("hasVcs", false)
                }.toString()
            }
        }
    }
}
