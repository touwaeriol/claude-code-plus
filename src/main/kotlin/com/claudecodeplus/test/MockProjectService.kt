package com.claudecodeplus.test

import com.claudecodeplus.core.interfaces.ProjectService
import java.nio.file.Paths

/**
 * 测试用的项目服务实现
 */
class MockProjectService(
    private val projectPath: String = "/Users/erio/codes/idea/claude-code-plus"
) : ProjectService {
    
    override fun getProjectPath(): String = projectPath
    
    override fun getProjectName(): String = "claude-code-plus"
    
    override fun getRelativePath(absolutePath: String): String {
        return if (absolutePath.startsWith(projectPath)) {
            absolutePath.substring(projectPath.length + 1)
        } else {
            absolutePath
        }
    }
}