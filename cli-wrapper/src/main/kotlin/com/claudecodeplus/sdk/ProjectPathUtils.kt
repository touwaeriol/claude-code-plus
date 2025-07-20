package com.claudecodeplus.sdk

import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

/**
 * 项目路径工具类
 * 提供项目路径和项目目录名之间的转换
 */
object ProjectPathUtils {
    
    /**
     * 将项目路径转换为 Claude CLI 使用的目录名
     * 
     * Claude CLI 的命名规则：
     * 1. Windows 盘符冒号移除，盘符后加 -
     * 2. 将路径分隔符替换为 -
     * 3. 保留开头的 - (Unix 路径)
     * 
     * 例如：
     * - /home/erio/codes/claude-code-plus → -home-erio-codes-claude-code-plus
     * - C:\Users\user\project → C--Users-user-project
     * 
     * @param projectPath 项目的绝对路径
     * @return Claude CLI 使用的目录名
     */
    fun projectPathToDirectoryName(projectPath: String): String {
        // 规范化路径（处理不同操作系统的路径分隔符）
        val normalizedPath = Paths.get(projectPath).normalize().toString()
        
        // Claude CLI 的实际编码规则：
        // 1. 先移除 Windows 盘符冒号
        // 2. 将所有路径分隔符替换为 -
        // 3. Windows 路径会在盘符后产生双横线（例如 C:\Users → C--Users）
        var dirName = normalizedPath
        
        // 处理 Windows 盘符（例如 "C:" → "C-"）
        if (dirName.length >= 2 && dirName[1] == ':') {
            dirName = dirName[0] + "-" + dirName.substring(2)
        }
        
        // 替换路径分隔符
        dirName = dirName
            .replace('\\', '-')  // Windows 路径分隔符
            .replace('/', '-')   // Unix 路径分隔符
        
        // Claude CLI 保留开头的 -，只移除结尾的 -
        return dirName.trimEnd('-')
    }
    
    /**
     * 获取项目的简短名称（用于显示）
     * 
     * @param projectPath 项目路径
     * @return 项目名称（最后一级目录名）
     */
    fun getProjectName(projectPath: String): String {
        return Paths.get(projectPath).fileName?.toString() ?: "Unknown"
    }
    
    /**
     * 生成项目的唯一标识符
     * 用于需要更短的标识符的场景
     * 
     * @param projectPath 项目路径
     * @return 8字符的唯一标识
     */
    fun generateProjectId(projectPath: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(projectPath.toByteArray())
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 从 Claude 目录名反推可能的项目路径
     * 注意：这个转换可能不是唯一的，仅用于调试
     * 
     * @param directoryName Claude 使用的目录名
     * @return 可能的项目路径列表
     */
    fun directoryNameToProjectPaths(directoryName: String): List<String> {
        val paths = mutableListOf<String>()
        
        // Unix 风格路径 - 处理开头的 -
        if (directoryName.startsWith("-")) {
            paths.add(directoryName.replace('-', '/'))
        }
        
        // Windows 风格路径（尝试常见的盘符）
        listOf("C", "D", "E").forEach { drive ->
            if (directoryName.startsWith(drive, ignoreCase = true)) {
                val pathPart = directoryName.substring(1).replace('-', '\\')
                paths.add("$drive:$pathPart")
            }
        }
        
        return paths
    }
    
    /**
     * 验证项目路径是否有效
     */
    fun isValidProjectPath(projectPath: String): Boolean {
        return try {
            val path = Paths.get(projectPath)
            path.isAbsolute
        } catch (e: Exception) {
            false
        }
    }
}