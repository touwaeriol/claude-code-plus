package com.asakii.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Git4Idea 实现 - 当 Git4Idea 插件安装时使用
 *
 * 直接使用 Git4Idea 的公开 API，无需反射
 * 此类在 plugin-withGit.xml 中注册，覆盖默认的 NoopGitBranchService
 */
@Service(Service.Level.PROJECT)
class GitBranchServiceImpl(private val project: Project) : GitBranchService {

    override fun getCurrentBranchName(): String? {
        return try {
            val gitRepoManager = GitRepositoryManager.getInstance(project)
            val repo = gitRepoManager.repositories.firstOrNull()
            repo?.currentBranch?.name
        } catch (e: Exception) {
            logger.debug { "Failed to get current branch: ${e.message}" }
            null
        }
    }

    override fun getLocalBranches(): List<String> {
        return try {
            val gitRepoManager = GitRepositoryManager.getInstance(project)
            val repo = gitRepoManager.repositories.firstOrNull() ?: return emptyList()
            repo.branches.localBranches.map { it.name }
        } catch (e: Exception) {
            logger.debug { "Failed to get local branches: ${e.message}" }
            emptyList()
        }
    }

    override fun isGitAvailable(): Boolean {
        return try {
            val gitRepoManager = GitRepositoryManager.getInstance(project)
            gitRepoManager.repositories.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
