package com.asakii.plugin.mcp.git

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 获取 VCS 状态工具
 *
 * 返回 VCS 状态概览：当前分支、变更数量等
 */
class GetVcsStatusTool(private val project: Project) {

    suspend fun execute(arguments: Map<String, Any?>): String {
        return ReadAction.compute<String, Throwable> {
            try {
                val changeListManager = ChangeListManager.getInstance(project)
                val vcsManager = ProjectLevelVcsManager.getInstance(project)
                val changes = changeListManager.allChanges

                // 获取活跃的 VCS
                val activeVcss = vcsManager.allActiveVcss
                val hasVcs = activeVcss.isNotEmpty()
                val vcsType = activeVcss.firstOrNull()?.name

                // 尝试获取当前分支（通过反射访问 Git4Idea）
                val currentBranch = tryGetCurrentBranch()

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

    /**
     * 尝试通过反射获取 Git 当前分支
     * 这样可以在 Git4Idea 插件可用时获取分支信息，不可用时返回 null
     */
    private fun tryGetCurrentBranch(): String? {
        return try {
            // 通过反射获取 GitRepositoryManager
            val gitRepoManagerClass = Class.forName("git4idea.repo.GitRepositoryManager")
            val getInstanceMethod = gitRepoManagerClass.getMethod("getInstance", Project::class.java)
            val gitRepoManager = getInstanceMethod.invoke(null, project)

            // 获取 repositories
            val getRepositoriesMethod = gitRepoManagerClass.getMethod("getRepositories")
            val repositories = getRepositoriesMethod.invoke(gitRepoManager) as? List<*>

            // 获取第一个仓库的当前分支
            val repo = repositories?.firstOrNull() ?: return null
            val getCurrentBranchMethod = repo.javaClass.getMethod("getCurrentBranch")
            val branch = getCurrentBranchMethod.invoke(repo)

            // 获取分支名称
            if (branch != null) {
                val getNameMethod = branch.javaClass.getMethod("getName")
                getNameMethod.invoke(branch) as? String
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug { "Git4Idea not available or error getting branch: ${e.message}" }
            null
        }
    }
}
