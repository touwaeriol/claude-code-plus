package com.asakii.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Git 分支服务接口
 *
 * 使用 JetBrains 官方推荐的可选依赖模式：
 * - 默认实现 (NoopGitBranchService): 当 Git4Idea 未安装时使用
 * - Git4Idea 实现 (GitBranchServiceImpl): 当 Git4Idea 已安装时使用，在 plugin-withGit.xml 中注册
 *
 * 这样可以避免使用反射，使用编译时类型安全的 API
 */
interface GitBranchService {

    /**
     * 获取当前分支名称
     * @return 分支名称，如果不可用则返回 null
     */
    fun getCurrentBranchName(): String?

    /**
     * 获取所有本地分支名称
     * @return 分支名称列表
     */
    fun getLocalBranches(): List<String>

    /**
     * 检查 Git 是否可用
     * @return true 如果项目有 Git 仓库
     */
    fun isGitAvailable(): Boolean

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GitBranchService {
            return project.getService(GitBranchService::class.java)
        }
    }
}

/**
 * 默认实现 - 当 Git4Idea 插件未安装时使用
 * 所有方法返回空/null 值
 */
@Service(Service.Level.PROJECT)
class NoopGitBranchService : GitBranchService {

    override fun getCurrentBranchName(): String? = null

    override fun getLocalBranches(): List<String> = emptyList()

    override fun isGitAvailable(): Boolean = false
}
