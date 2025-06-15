package com.claudecodeplus.startup

import com.claudecodeplus.util.ProjectPathDebugger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * 项目启动时记录路径信息
 */
class ProjectStartupLogger : ProjectActivity {
    companion object {
        private val LOG = logger<ProjectStartupLogger>()
    }
    
    override suspend fun execute(project: Project) {
        LOG.info("=== Claude Code Plus - 项目启动 ===")
        LOG.info("项目名称: ${project.name}")
        LOG.info("项目基础路径: ${project.basePath}")
        LOG.info("项目文件路径: ${project.projectFilePath}")
        
        // 打印详细的调试信息
        val debugInfo = ProjectPathDebugger.debugProjectPath(project)
        LOG.info("项目路径详细信息:\n$debugInfo")
        
        // 获取并记录推荐的工作目录
        val workingDir = ProjectPathDebugger.getProjectWorkingDirectory(project)
        LOG.info("推荐的工作目录: $workingDir")
        LOG.info("项目类型: ${ProjectPathDebugger.getProjectType(project)}")
        
        // 打印到控制台（调试用）
        println("=== Claude Code Plus ===")
        println("项目: ${project.name}")
        println("路径: ${project.basePath}")
        println("工作目录: $workingDir")
        println("=====================")
    }
}