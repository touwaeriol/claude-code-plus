package com.claudecodeplus.ui.utils

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Claude 路径转换工具类
 * 
 * 负责在完整项目路径和 Claude 项目目录名之间进行转换。
 * 支持跨平台路径处理，包括 Windows 和 Unix/Linux/macOS 系统。
 * 
 * 转换规则：
 * 1. 路径分隔符替换：`/` 和 `\` → `-`
 * 2. Windows 驱动器字母冒号替换：`:` → `-`
 * 3. 点号替换：`.` → `-` 
 * 4. 开头添加前缀：项目目录名以 `-` 开头
 * 5. 特殊字符处理：连续的 `-` 保持不变
 * 
 * 示例：
 * - `/Users/erio/codes/idea/claude-code-plus` → `-Users-erio-codes-idea-claude-code-plus`
 * - `C:\Users\erio\codes\idea\claude-code-plus` → `-C-Users-erio-codes-idea-claude-code-plus`
 * - `/Users/erio/.claude-code-router` → `-Users-erio--claude-code-router`
 */
object ClaudePathConverter {
    
    /**
     * 将完整项目路径转换为 Claude 项目目录名
     * 
     * @param fullPath 完整项目路径 如: `/Users/erio/codes/idea/claude-code-plus`
     * @return Claude 项目目录名 如: `-Users-erio-codes-idea-claude-code-plus`
     */
    fun pathToClaudeProjectName(fullPath: String): String {
        // 处理空路径
        if (fullPath.isBlank()) {
            return "-"
        }
        
        // 标准化路径（使用系统默认分隔符）
        val normalizedPath = Paths.get(fullPath).normalize().toString()
        
        // 移除开头的路径分隔符，避免双短横线
        val pathWithoutLeadingSeparator = normalizedPath.trimStart('/', '\\')
        
        // 替换路径分隔符和特殊字符
        val converted = pathWithoutLeadingSeparator
            .replace('/', '-')      // Unix/Linux/macOS 路径分隔符
            .replace('\\', '-')     // Windows 路径分隔符
            .replace(':', '-')      // Windows 驱动器字母冒号
            .replace('.', '-')      // 点号也替换为短横线
        
        // 添加前缀并返回
        return "-$converted"
    }
    
    /**
     * 将 Claude 项目目录名转换回原始路径
     * 
     * 注意：由于路径分隔符和冒号都被替换为 `-`，逆向转换可能存在歧义。
     * 此方法主要用于调试和显示目的。
     * 
     * @param claudeProjectName Claude 项目目录名
     * @return 尝试还原的完整路径（可能不完全准确）
     */
    fun claudeProjectNameToPath(claudeProjectName: String): String {
        if (claudeProjectName.isBlank()) {
            return ""
        }
        
        // 移除前缀
        val withoutPrefix = claudeProjectName.removePrefix("-")
        
        // 检测操作系统类型，使用对应的路径分隔符
        val separator = System.getProperty("file.separator")
        
        return when {
            // Windows 系统：尝试恢复驱动器字母格式
            System.getProperty("os.name").lowercase().contains("windows") -> {
                val parts = withoutPrefix.split('-')
                if (parts.size >= 2 && parts[0].length == 1 && parts[0].matches(Regex("[A-Za-z]"))) {
                    // 第一部分是驱动器字母，第二部分开始是路径
                    val driveLetter = parts[0] + ":"
                    val pathParts = parts.drop(1)
                    driveLetter + separator + pathParts.joinToString(separator)
                } else {
                    withoutPrefix.replace('-', separator[0])
                }
            }
            // Unix/Linux/macOS 系统
            else -> {
                withoutPrefix.replace('-', separator[0])
            }
        }
    }
    
    /**
     * 验证 Claude 项目目录名格式是否正确
     * 
     * @param claudeProjectName 待验证的 Claude 项目目录名
     * @return 如果格式正确返回 true，否则返回 false
     */
    fun isValidClaudeProjectName(claudeProjectName: String): Boolean {
        return claudeProjectName.startsWith("-") && claudeProjectName.length > 1
    }
    
    /**
     * 从路径中提取项目名称（最后一个目录名）
     * 
     * @param fullPath 完整项目路径
     * @return 项目名称
     */
    fun extractProjectName(fullPath: String): String {
        if (fullPath.isBlank()) {
            return ""
        }
        
        return try {
            Paths.get(fullPath).fileName?.toString() ?: ""
        } catch (e: Exception) {
            // 如果路径解析失败，使用字符串操作
            val normalized = fullPath.trimEnd('/', '\\')
            val lastSlash = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
            if (lastSlash >= 0) normalized.substring(lastSlash + 1) else normalized
        }
    }
    
    /**
     * 获取路径的父目录路径
     * 
     * @param fullPath 完整路径
     * @return 父目录路径，如果没有父目录则返回空字符串
     */
    fun getParentPath(fullPath: String): String {
        if (fullPath.isBlank()) {
            return ""
        }
        
        return try {
            Paths.get(fullPath).parent?.toString() ?: ""
        } catch (e: Exception) {
            val normalized = fullPath.trimEnd('/', '\\')
            val lastSlash = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
            if (lastSlash > 0) normalized.substring(0, lastSlash) else ""
        }
    }
}