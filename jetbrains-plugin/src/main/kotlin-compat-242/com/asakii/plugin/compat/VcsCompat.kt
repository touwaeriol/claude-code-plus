package com.asakii.plugin.compat

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager

/**
 * VCS 兼容层 - 适用于 2024.2 ~ 2025.2
 *
 * 在这些版本中，使用标准的 allActiveVcss 属性
 */
object VcsCompat {

    /**
     * 获取项目中所有活跃的 VCS
     */
    fun getAllActiveVcss(project: Project): Array<AbstractVcs> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        return vcsManager.allActiveVcss
    }
}
