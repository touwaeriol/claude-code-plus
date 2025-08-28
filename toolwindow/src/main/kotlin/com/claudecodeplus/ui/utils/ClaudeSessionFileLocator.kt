package com.claudecodeplus.ui.utils

import mu.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
// 移除Java Stream相关导入，使用纯Kotlin集合操作

/**
 * Claude 会话文件定位服务
 * 
 * 负责定位和管理 Claude CLI 生成的会话文件。
 * 会话文件存储在 `~/.claude/projects/` 目录下，按项目路径分组。
 */
object ClaudeSessionFileLocator {
    private val logger = KotlinLogging.logger {}
    
    // Claude 会话文件存储根目录
    private val claudeProjectsDir: Path by lazy {
        Paths.get(System.getProperty("user.home"), ".claude", "projects")
    }
    
    /**
     * 根据项目路径和 sessionId 获取会话文件
     * 
     * @param projectPath 项目完整路径
     * @param sessionId Claude 会话 ID
     * @return 会话文件路径，如果文件不存在返回 null
     */
    fun getSessionFile(projectPath: String, sessionId: String): Path? {
        if (projectPath.isBlank() || sessionId.isBlank()) {
            logger.warn("[ClaudeSessionFileLocator] 项目路径或会话ID为空")
            return null
        }
        
        return try {
            val claudeProjectName = ClaudePathConverter.pathToClaudeProjectName(projectPath)
            val sessionFile = claudeProjectsDir
                .resolve(claudeProjectName)
                .resolve("$sessionId.jsonl")
                
            if (Files.exists(sessionFile)) {
                logger.debug("[ClaudeSessionFileLocator] 找到会话文件: $sessionFile")
                sessionFile
            } else {
                logger.debug("[ClaudeSessionFileLocator] 会话文件不存在: $sessionFile")
                null
            }
        } catch (e: Exception) {
            logger.error("[ClaudeSessionFileLocator] 获取会话文件失败: projectPath=$projectPath, sessionId=$sessionId", e)
            null
        }
    }
    
    /**
     * 获取项目的所有会话文件，按最后修改时间倒序排列
     * 
     * @param projectPath 项目完整路径
     * @return 会话文件列表，按最新修改时间排序
     */
    fun getProjectSessionFiles(projectPath: String): List<Path> {
        if (projectPath.isBlank()) {
            logger.warn("[ClaudeSessionFileLocator] 项目路径为空")
            return emptyList()
        }
        
        return try {
            val claudeProjectName = ClaudePathConverter.pathToClaudeProjectName(projectPath)
            val projectDir = claudeProjectsDir.resolve(claudeProjectName)
            
            if (!Files.exists(projectDir)) {
                logger.debug("[ClaudeSessionFileLocator] 项目目录不存在: $projectDir")
                return emptyList()
            }
            
            Files.list(projectDir).use { stream ->
                stream.toList()
                    .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jsonl") }
                    .sortedWith { a, b -> 
                        try {
                            Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a))
                        } catch (e: IOException) {
                            logger.warn("[ClaudeSessionFileLocator] 无法比较文件修改时间: $a vs $b", e)
                            0
                        }
                    }
            }
        } catch (e: Exception) {
            logger.error("[ClaudeSessionFileLocator] 获取项目会话文件失败: projectPath=$projectPath", e)
            emptyList()
        }
    }
    
    /**
     * 获取项目最新的会话文件
     * 
     * @param projectPath 项目完整路径
     * @return 最新的会话文件，如果不存在返回 null
     */
    fun getLatestSessionFile(projectPath: String): Path? {
        return getProjectSessionFiles(projectPath).firstOrNull()
    }
    
    /**
     * 获取项目最新的会话 ID
     * 
     * @param projectPath 项目完整路径
     * @return 最新的会话 ID，如果不存在返回 null
     */
    fun getLatestSessionId(projectPath: String): String? {
        val latestFile = getLatestSessionFile(projectPath) ?: return null
        val fileName = latestFile.fileName.toString()
        return if (fileName.endsWith(".jsonl")) {
            fileName.removeSuffix(".jsonl")
        } else {
            null
        }
    }
    
    /**
     * 检查指定项目是否存在会话文件
     * 
     * @param projectPath 项目完整路径
     * @return 如果存在会话文件返回 true
     */
    fun hasSessionFiles(projectPath: String): Boolean {
        return getProjectSessionFiles(projectPath).isNotEmpty()
    }
    
    /**
     * 获取所有存在会话文件的项目路径
     * 
     * @return 项目路径列表
     */
    fun getAllProjectsWithSessions(): List<String> {
        return try {
            if (!Files.exists(claudeProjectsDir)) {
                logger.debug("[ClaudeSessionFileLocator] Claude 项目目录不存在: $claudeProjectsDir")
                return emptyList()
            }
            
            Files.list(claudeProjectsDir).use { stream ->
                stream.toList().asSequence()
                    .filter { Files.isDirectory(it) }
                    .mapNotNull { projectDir ->
                        try {
                            // 检查目录是否有 .jsonl 文件
                            val hasJsonlFiles = Files.list(projectDir).use { fileStream ->
                                fileStream.toList().any { file ->
                                    Files.isRegularFile(file) && file.fileName.toString().endsWith(".jsonl")
                                }
                            }
                            
                            if (hasJsonlFiles) {
                                val claudeProjectName = projectDir.fileName.toString()
                                // 转换回原始路径
                                ClaudePathConverter.claudeProjectNameToPath(claudeProjectName)
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            logger.warn("[ClaudeSessionFileLocator] 检查项目目录失败: ${projectDir.fileName}", e)
                            null
                        }
                    }
                    .toList()
            }
        } catch (e: Exception) {
            logger.error("[ClaudeSessionFileLocator] 获取所有项目失败", e)
            emptyList()
        }
    }
    
    /**
     * 获取会话文件的统计信息
     * 
     * @param projectPath 项目完整路径
     * @return 会话统计信息
     */
    fun getSessionStats(projectPath: String): SessionStats {
        val sessionFiles = getProjectSessionFiles(projectPath)
        
        var totalSize = 0L
        var newestTime: Long = 0
        var oldestTime: Long = Long.MAX_VALUE
        
        for (file in sessionFiles) {
            try {
                val size = Files.size(file)
                val modTime = Files.getLastModifiedTime(file).toMillis()
                
                totalSize += size
                if (modTime > newestTime) newestTime = modTime
                if (modTime < oldestTime) oldestTime = modTime
            } catch (e: IOException) {
                logger.warn("[ClaudeSessionFileLocator] 获取文件信息失败: $file", e)
            }
        }
        
        return SessionStats(
            sessionCount = sessionFiles.size,
            totalSizeBytes = totalSize,
            newestSessionTime = if (newestTime > 0) newestTime else null,
            oldestSessionTime = if (oldestTime < Long.MAX_VALUE) oldestTime else null
        )
    }
    
    /**
     * 清理指定时间之前的旧会话文件
     * 
     * @param projectPath 项目完整路径
     * @param beforeTimeMillis 时间戳，删除此时间之前的文件
     * @return 删除的文件数量
     */
    fun cleanOldSessions(projectPath: String, beforeTimeMillis: Long): Int {
        val sessionFiles = getProjectSessionFiles(projectPath)
        var deletedCount = 0
        
        for (file in sessionFiles) {
            try {
                val modTime = Files.getLastModifiedTime(file).toMillis()
                if (modTime < beforeTimeMillis) {
                    Files.delete(file)
                    deletedCount++
                    logger.info("[ClaudeSessionFileLocator] 删除旧会话文件: $file")
                }
            } catch (e: Exception) {
                logger.error("[ClaudeSessionFileLocator] 删除文件失败: $file", e)
            }
        }
        
        return deletedCount
    }
    
    /**
     * 会话统计信息数据类
     */
    data class SessionStats(
        val sessionCount: Int,
        val totalSizeBytes: Long,
        val newestSessionTime: Long?,
        val oldestSessionTime: Long?
    ) {
        val totalSizeMB: Double
            get() = totalSizeBytes / (1024.0 * 1024.0)
    }
}