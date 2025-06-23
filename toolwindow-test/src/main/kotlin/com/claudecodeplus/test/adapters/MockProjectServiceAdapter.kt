package com.claudecodeplus.test.adapters

import com.claudecodeplus.ui.services.ProjectService
import com.claudecodeplus.test.MockProjectService

/**
 * 将 MockProjectService 适配到通用的 ProjectService 接口
 */
class MockProjectServiceAdapter(
    private val mockProjectService: MockProjectService
) : ProjectService {
    
    override fun getProjectPath(): String {
        return mockProjectService.getProjectPath()
    }
    
    override fun getProjectName(): String {
        return mockProjectService.getProjectName()
    }
    
    override fun openFile(filePath: String, lineNumber: Int?) {
        mockProjectService.openFile(filePath, lineNumber)
    }
    
    override fun showSettings(settingsId: String?) {
        mockProjectService.showSettings(settingsId)
    }
}