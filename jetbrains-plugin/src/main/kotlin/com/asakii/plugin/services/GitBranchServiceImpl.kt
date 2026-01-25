package com.asakii.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import git4idea.repo.GitRepositoryManager
import com.asakii.logging.*

private val logger = getLogger("GitBranchServiceImpl")

/**
 * Git4Idea 实现 - 当 Git4Idea 插件安装时使用
 *
 * 直接使用 Git4Idea 的公开 API，无需反射
 * 此类在 plugin-withGit.xml 中注册，覆盖默认的 NoopGitBranchService
 */
@Service(Service.Level.PROJECT)
class GitBranchServiceImpl(private val project: Project) : GitBranchService {

    /**
     * 获取项目根目录对应的 Git 仓库
     * 使用 getRepositoryForRoot 直接获取，无需遍历
     */
    private fun getProjectRootRepository(): git4idea.repo.GitRepository? {
        val gitRepoManager = GitRepositoryManager.getInstance(project)
        
        // 直接通过项目根目录获取对应的仓库
        val projectDir = project.guessProjectDir()
        if (projectDir != null) {
            val rootRepo = gitRepoManager.getRepositoryForRoot(projectDir)
            if (rootRepo != null) {
                return rootRepo
            }
        }
        
        // 回退：如果项目根目录没有 .git，返回第一个仓库
        return gitRepoManager.repositories.firstOrNull()
    }

    override fun getCurrentBranchName(): String? {
        return try {
            getProjectRootRepository()?.currentBranch?.name
        } catch (e: Exception) {
            logger.debug { "Failed to get current branch: ${e.message}" }
            null
        }
    }

    override fun getLocalBranches(): List<String> {
        return try {
            val repo = getProjectRootRepository() ?: return emptyList()
            repo.branches.localBranches.map { it.name }
        } catch (e: Exception) {
            logger.debug { "Failed to get local branches: ${e.message}" }
            emptyList()
        }
    }

    override fun isGitAvailable(): Boolean {
        return try {
            GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
