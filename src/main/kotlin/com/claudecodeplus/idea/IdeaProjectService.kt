package com.claudecodeplus.idea

import com.claudecodeplus.core.interfaces.ProjectService
import com.intellij.openapi.project.Project

/**
 * IDEA平台的项目服务实现
 */
class IdeaProjectService(
    private val project: Project
) : ProjectService {
    
    override fun getProjectPath(): String {
        return project.basePath ?: System.getProperty("user.dir")
    }
    
    override fun getProjectName(): String {
        return project.name
    }
    
    override fun getRelativePath(absolutePath: String): String {
        val basePath = project.basePath ?: return absolutePath
        return if (absolutePath.startsWith(basePath)) {
            absolutePath.substring(basePath.length + 1)
        } else {
            absolutePath
        }
    }
}