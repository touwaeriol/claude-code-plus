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
     * 3. 将点号 (.) 替换为 -
     * 4. 将下划线 (_) 替换为 -
     * 5. 保留开头的 - (Unix 路径)
     * 
     * 例如：
     * - /home/username/codes/claude-code-plus → -home-username-codes-claude-code-plus
     * - /Users/username/.claude-code-router → -Users-username--claude-code-router
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
        // 3. 将点号替换为 -
        // 4. 将下划线替换为 -
        // 5. Windows 路径会在盘符后产生双横线（例如 C:\Users → C--Users）
        var dirName = normalizedPath
        
        // 处理 Windows 盘符（例如 "C:" → "C-"）
        if (dirName.length >= 2 && dirName[1] == ':') {
            dirName = dirName[0] + "-" + dirName.substring(2)
        }
        
        // 替换路径分隔符、点号和下划线
        dirName = dirName
            .replace('\\', '-')  // Windows 路径分隔符
            .replace('/', '-')   // Unix 路径分隔符
            .replace('.', '-')   // 点号
            .replace('_', '-')   // 下划线
        
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