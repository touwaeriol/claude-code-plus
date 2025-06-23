package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.ui.services.ProjectService
import com.claudecodeplus.idea.IdeaProjectService

/**
 * 将 IdeaProjectService 适配到通用的 ProjectService 接口
 */
class IdeaProjectServiceAdapter(
    private val ideaProjectService: IdeaProjectService
) : ProjectService {
    
    override fun getProjectPath(): String {
        return ideaProjectService.getProjectPath()
    }
    
    override fun getProjectName(): String {
        return ideaProjectService.getProjectName()
    }
    
    override fun openFile(filePath: String, lineNumber: Int?) {
        ideaProjectService.openFile(filePath, lineNumber)
    }
    
    override fun showSettings(settingsId: String?) {
        ideaProjectService.showSettings(settingsId)
    }
}