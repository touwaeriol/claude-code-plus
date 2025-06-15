package com.claudecodeplus.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * 项目路径调试工具
 */
object ProjectPathDebugger {
    private val LOG = logger<ProjectPathDebugger>()
    
    fun debugProjectPath(project: Project): String {
        val builder = StringBuilder()
        
        builder.appendLine("=== 项目路径调试信息 ===")
        builder.appendLine("项目名称: ${project.name}")
        builder.appendLine("project.basePath: ${project.basePath}")
        builder.appendLine("project.projectFilePath: ${project.projectFilePath}")
        builder.appendLine("project.projectFile: ${project.projectFile}")
        builder.appendLine("project.workspaceFile: ${project.workspaceFile}")
        
        // 获取内容根目录
        try {
            val contentRoots = ProjectRootManager.getInstance(project).contentRoots
            builder.appendLine("\n内容根目录 (ContentRoots):")
            contentRoots.forEachIndexed { index, vf ->
                val file = File(vf.path)
                val type = if (file.isDirectory) "[目录]" else "[文件]"
                builder.appendLine("  [$index] $type ${vf.path}")
                if (file.isFile) {
                    builder.appendLine("       父目录: ${file.parentFile?.absolutePath}")
                }
            }
        } catch (e: Exception) {
            builder.appendLine("获取内容根目录失败: ${e.message}")
        }
        
        // 获取模块信息
        try {
            val modules = ModuleManager.getInstance(project).modules
            builder.appendLine("\n模块信息 (Modules):")
            modules.forEachIndexed { index, module ->
                builder.appendLine("  [$index] 模块名: ${module.name}")
                val moduleRoots = ModuleRootManager.getInstance(module).contentRoots
                moduleRoots.forEach { root ->
                    builder.appendLine("       内容根: ${root.path}")
                }
            }
        } catch (e: Exception) {
            builder.appendLine("获取模块信息失败: ${e.message}")
        }
        
        // 检查路径是否存在
        project.basePath?.let { basePath ->
            val file = File(basePath)
            builder.appendLine("basePath 存在: ${file.exists()}")
            builder.appendLine("basePath 是目录: ${file.isDirectory}")
            builder.appendLine("basePath 绝对路径: ${file.absolutePath}")
            builder.appendLine("basePath 规范路径: ${file.canonicalPath}")
            
            // 列出目录内容
            if (file.exists() && file.isDirectory) {
                builder.appendLine("目录内容 (前10个):")
                file.listFiles()?.take(10)?.forEach { f ->
                    builder.appendLine("  ${if (f.isDirectory) "[D]" else "[F]"} ${f.name}")
                }
            }
        } ?: builder.appendLine("警告: project.basePath 为 null")
        
        // 系统属性
        builder.appendLine("\n系统属性:")
        builder.appendLine("user.dir: ${System.getProperty("user.dir")}")
        builder.appendLine("user.home: ${System.getProperty("user.home")}")
        
        // 当前工作目录
        val currentDir = File(".")
        builder.appendLine("\n当前工作目录:")
        builder.appendLine("相对路径 '.' : ${currentDir.absolutePath}")
        builder.appendLine("规范路径: ${currentDir.canonicalPath}")
        
        // 添加项目类型检测
        builder.appendLine("\n项目类型: ${getProjectType(project)}")
        
        // 推荐的工作目录
        val recommendedDir = getProjectWorkingDirectory(project)
        builder.appendLine("\n推荐使用的工作目录: $recommendedDir")
        
        builder.appendLine("=== 调试信息结束 ===")
        
        // 同时记录到日志
        LOG.info(builder.toString())
        
        return builder.toString()
    }
    
    /**
     * 获取项目的实际工作目录
     * 使用多种方法确保获取到正确的路径
     */
    fun getProjectWorkingDirectory(project: Project): String {
        // 方法1: 优先使用 ProjectRootManager 的内容根
        try {
            val contentRoots = ProjectRootManager.getInstance(project).contentRoots
            if (contentRoots.isNotEmpty()) {
                val rootPath = contentRoots[0].path
                val rootFile = File(rootPath)
                
                val workingDir = when {
                    rootFile.isDirectory -> {
                        // 如果内容根是文件夹，直接使用
                        LOG.info("内容根是目录，直接使用: $rootPath")
                        rootPath
                    }
                    rootFile.isFile -> {
                        // 如果内容根是文件，使用其父目录
                        val parentDir = rootFile.parentFile?.absolutePath ?: rootPath
                        LOG.info("内容根是文件 ($rootPath)，使用父目录: $parentDir")
                        parentDir
                    }
                    else -> {
                        // 其他情况（可能不存在），直接使用路径
                        LOG.warn("内容根路径不是文件也不是目录: $rootPath")
                        rootPath
                    }
                }
                
                LOG.info("最终使用的工作目录: $workingDir")
                return workingDir
            }
        } catch (e: Exception) {
            LOG.warn("从 ProjectRootManager 获取路径失败: ${e.message}")
        }
        
        // 方法2: 使用 basePath（如果可用）
        val basePath = project.basePath
        if (basePath != null) {
            val file = File(basePath)
            if (file.exists() && file.isDirectory) {
                LOG.info("使用 project.basePath: $basePath")
                return try {
                    file.canonicalPath
                } catch (e: Exception) {
                    file.absolutePath
                }
            }
        }
        
        // 方法3: 尝试从第一个模块获取
        try {
            val modules = ModuleManager.getInstance(project).modules
            if (modules.isNotEmpty()) {
                val contentRoots = ModuleRootManager.getInstance(modules[0]).contentRoots
                if (contentRoots.isNotEmpty()) {
                    val modulePath = contentRoots[0].path
                    LOG.info("使用第一个模块的内容根: $modulePath")
                    return modulePath
                }
            }
        } catch (e: Exception) {
            LOG.warn("从模块获取路径失败: ${e.message}")
        }
        
        // 方法4: 尝试从项目文件路径推断
        val projectFilePath = project.projectFilePath
        if (projectFilePath != null) {
            val projectFile = File(projectFilePath)
            val parentDir = projectFile.parentFile
            if (parentDir != null && parentDir.exists()) {
                // 如果是 .idea 目录，再往上一级
                val workDir = if (parentDir.name == ".idea") {
                    parentDir.parentFile ?: parentDir
                } else {
                    parentDir
                }
                LOG.info("从项目文件路径推断: ${workDir.absolutePath}")
                return workDir.absolutePath
            }
        }
        
        // 最后手段: 使用用户主目录
        val userHome = System.getProperty("user.home")
        LOG.warn("无法获取项目路径，使用用户主目录: $userHome")
        return userHome
    }
    
    /**
     * 检查项目类型
     */
    fun getProjectType(project: Project): String {
        val basePath = project.basePath ?: return "Unknown"
        
        // 检查 Maven
        if (File(basePath, "pom.xml").exists()) {
            return "Maven"
        }
        
        // 检查 Gradle
        if (File(basePath, "build.gradle").exists() || 
            File(basePath, "build.gradle.kts").exists()) {
            return "Gradle"
        }
        
        // 检查 npm/Node.js
        if (File(basePath, "package.json").exists()) {
            return "Node.js/npm"
        }
        
        // 检查 Python
        if (File(basePath, "setup.py").exists() || 
            File(basePath, "requirements.txt").exists() ||
            File(basePath, "pyproject.toml").exists()) {
            return "Python"
        }
        
        return "Generic"
    }
}