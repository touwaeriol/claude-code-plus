package com.claudecodeplus.test

import com.claudecodeplus.core.interfaces.ProjectService
import java.nio.file.Paths
import java.awt.Desktop
import java.io.File

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
    
    fun openFile(filePath: String, lineNumber: Int? = null) {
        val file = File(filePath)
        if (file.exists() && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file)
                println("Opened file: $filePath${lineNumber?.let { " at line $it" } ?: ""}")
            } catch (e: Exception) {
                println("Failed to open file: ${e.message}")
            }
        } else {
            println("File not found: $filePath")
        }
    }
    
    fun showSettings(settingsId: String? = null) {
        println("Would open settings dialog: ${settingsId ?: "general"}")
    }
}