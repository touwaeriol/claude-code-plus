package com.asakii.plugin.adapters

import com.asakii.core.interfaces.ProjectService

/**
 * 项目服务适配器
 * 实现 ProjectService 接口的简化版本
 */
class ProjectServiceAdapter(
    private val projectPath: String = "",
    private val projectName: String = "Unknown"
) : ProjectService {
    
    override fun getProjectPath(): String = projectPath
    
    override fun getProjectName(): String = projectName
    
    override fun getRelativePath(absolutePath: String): String {
        return if (absolutePath.startsWith(projectPath)) {
            absolutePath.substring(projectPath.length).trimStart('/', '\\')
        } else {
            absolutePath
        }
    }

    override fun openFile(filePath: String, lineNumber: Int?) {
        // TODO: 实现文件打开逻辑
    }

    override fun showSettings(settingsId: String?) {
        // TODO: 实现设置显示逻辑
    }
}