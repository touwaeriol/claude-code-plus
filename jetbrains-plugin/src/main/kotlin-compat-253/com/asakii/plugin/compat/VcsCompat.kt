package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager

/**
 * VCS 兼容层 - 适用于 2025.3+
 *
 * 在这些版本中，可能使用不同的 API
 * 注意：如果 API 在 2025.3 中有变化，请在此文件中更新
 */
object VcsCompat {

    /**
     * 获取项目中所有活跃的 VCS
     */
    fun getAllActiveVcss(project: Project): Array<AbstractVcs> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        // 2025.3+ 使用新的 getAllActiveVcss() 方法替代已弃用的 allActiveVcss 属性
        return vcsManager.getAllActiveVcss()
    }
}
