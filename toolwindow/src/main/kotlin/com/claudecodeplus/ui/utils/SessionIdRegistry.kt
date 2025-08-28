package com.claudecodeplus.ui.utils

import mu.KotlinLogging
import java.util.prefs.Preferences

/**
 * 会话 ID 注册表
 * 
 * 负责管理项目标签页与 Claude 会话 ID 之间的映射关系。
 * 使用 Java Preferences API 进行持久化存储，确保数据在应用重启后仍然可用。
 * 
 * 存储格式：
 * - Key: "projectPath:tabId"
 * - Value: "sessionId"
 * 
 * 示例：
 * - "/Users/erio/codes/idea/claude-code-plus:main" -> "00adc9a3-e233-46d9-8dd4-bf6acda7bf20"
 */
object SessionIdRegistry {
    private val logger = KotlinLogging.logger {}
    
    // 使用 Preferences 进行持久化存储
    private const val PREF_NODE = "claude_code_plus/session_registry"
    private const val LATEST_SESSION_PREFIX = "latest_session:"
    private const val SESSION_METADATA_PREFIX = "session_meta:"
    
    private val prefs: Preferences = Preferences.userRoot().node(PREF_NODE)
    
    /**
     * 记录会话 ID 映射
     * 
     * @param projectPath 项目完整路径
     * @param tabId 标签页 ID
     * @param sessionId Claude 会话 ID
     */
    fun recordSessionId(projectPath: String, tabId: String, sessionId: String) {
        if (projectPath.isBlank() || tabId.isBlank() || sessionId.isBlank()) {
            logger.warn("[SessionIdRegistry] 参数为空，跳过记录: projectPath=$projectPath, tabId=$tabId, sessionId=$sessionId")
            return
        }
        
        try {
            val key = createSessionKey(projectPath, tabId)
            val currentTime = System.currentTimeMillis()
            
            prefs.put(key, sessionId)
            
            // 记录最新的会话（按项目分组）
            val latestKey = LATEST_SESSION_PREFIX + projectPath
            prefs.put(latestKey, sessionId)
            
            // 记录会话元数据
            val metaKey = SESSION_METADATA_PREFIX + sessionId
            val metadata = SessionMetadata(
                projectPath = projectPath,
                tabId = tabId,
                sessionId = sessionId,
                createTime = currentTime,
                lastAccessTime = currentTime
            )
            prefs.put(metaKey, metadata.toJsonString())
            
            prefs.flush()
            
            logger.debug("[SessionIdRegistry] 记录会话映射: $key -> $sessionId")
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 记录会话ID失败", e)
        }
    }
    
    /**
     * 获取指定标签页的会话 ID
     * 
     * @param projectPath 项目完整路径
     * @param tabId 标签页 ID
     * @return 会话 ID，如果不存在返回 null
     */
    fun getSessionId(projectPath: String, tabId: String): String? {
        if (projectPath.isBlank() || tabId.isBlank()) {
            return null
        }
        
        return try {
            val key = createSessionKey(projectPath, tabId)
            val sessionId = prefs.get(key, null)
            
            if (sessionId != null) {
                // 更新最后访问时间
                updateLastAccessTime(sessionId)
            }
            
            logger.debug("[SessionIdRegistry] 获取会话映射: $key -> $sessionId")
            sessionId
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 获取会话ID失败", e)
            null
        }
    }
    
    /**
     * 获取项目最新的会话 ID
     * 
     * @param projectPath 项目完整路径
     * @return 最新的会话 ID，如果不存在返回 null
     */
    fun getLatestSessionForProject(projectPath: String): String? {
        if (projectPath.isBlank()) {
            return null
        }
        
        return try {
            val latestKey = LATEST_SESSION_PREFIX + projectPath
            val sessionId = prefs.get(latestKey, null)
            
            // 如果 Preferences 中没有记录，尝试从文件系统获取最新会话
            if (sessionId == null) {
                val latestFromFile = ClaudeSessionFileLocator.getLatestSessionId(projectPath)
                if (latestFromFile != null) {
                    // 更新注册表
                    prefs.put(latestKey, latestFromFile)
                    prefs.flush()
                    logger.info("[SessionIdRegistry] 从文件系统发现最新会话: $projectPath -> $latestFromFile")
                }
                return latestFromFile
            }
            
            logger.debug("[SessionIdRegistry] 获取最新会话: $projectPath -> $sessionId")
            sessionId
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 获取最新会话失败", e)
            null
        }
    }
    
    /**
     * 移除会话映射
     * 
     * @param projectPath 项目完整路径
     * @param tabId 标签页 ID
     */
    fun removeSession(projectPath: String, tabId: String) {
        if (projectPath.isBlank() || tabId.isBlank()) {
            return
        }
        
        try {
            val key = createSessionKey(projectPath, tabId)
            val sessionId = prefs.get(key, null)
            
            // 移除主映射
            prefs.remove(key)
            
            // 如果这是最新的会话，也要更新最新会话记录
            if (sessionId != null) {
                val latestKey = LATEST_SESSION_PREFIX + projectPath
                val latestSession = prefs.get(latestKey, null)
                if (sessionId == latestSession) {
                    // 寻找其他会话作为最新会话
                    val otherSession = findOtherSessionForProject(projectPath, sessionId)
                    if (otherSession != null) {
                        prefs.put(latestKey, otherSession)
                    } else {
                        prefs.remove(latestKey)
                    }
                }
                
                // 移除会话元数据
                val metaKey = SESSION_METADATA_PREFIX + sessionId
                prefs.remove(metaKey)
            }
            
            prefs.flush()
            
            logger.debug("[SessionIdRegistry] 移除会话映射: $key")
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 移除会话失败", e)
        }
    }
    
    /**
     * 获取项目的所有会话映射
     * 
     * @param projectPath 项目完整路径
     * @return 标签页 ID 到会话 ID 的映射
     */
    fun getProjectSessions(projectPath: String): Map<String, String> {
        if (projectPath.isBlank()) {
            return emptyMap()
        }
        
        return try {
            val prefix = "$projectPath:"
            val result = mutableMapOf<String, String>()
            
            for (key in prefs.keys()) {
                if (key.startsWith(prefix) && !key.startsWith(LATEST_SESSION_PREFIX) && !key.startsWith(SESSION_METADATA_PREFIX)) {
                    val tabId = key.removePrefix(prefix)
                    val sessionId = prefs.get(key, null)
                    if (sessionId != null) {
                        result[tabId] = sessionId
                    }
                }
            }
            
            logger.debug("[SessionIdRegistry] 获取项目会话: $projectPath -> ${result.size} 个会话")
            result
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 获取项目会话失败", e)
            emptyMap()
        }
    }
    
    /**
     * 获取所有已注册的项目路径
     * 
     * @return 项目路径列表
     */
    fun getAllProjects(): Set<String> {
        return try {
            val projects = mutableSetOf<String>()
            
            for (key in prefs.keys()) {
                if (!key.startsWith(LATEST_SESSION_PREFIX) && !key.startsWith(SESSION_METADATA_PREFIX)) {
                    val colonIndex = key.lastIndexOf(':')
                    if (colonIndex > 0) {
                        val projectPath = key.substring(0, colonIndex)
                        projects.add(projectPath)
                    }
                }
            }
            
            logger.debug("[SessionIdRegistry] 获取所有项目: ${projects.size} 个项目")
            projects
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 获取所有项目失败", e)
            emptySet()
        }
    }
    
    /**
     * 清理指定项目的所有会话映射
     * 
     * @param projectPath 项目完整路径
     * @return 清理的会话数量
     */
    fun clearProject(projectPath: String): Int {
        if (projectPath.isBlank()) {
            return 0
        }
        
        return try {
            val prefix = "$projectPath:"
            val latestKey = LATEST_SESSION_PREFIX + projectPath
            val keysToRemove = mutableListOf<String>()
            
            // 收集要删除的键
            for (key in prefs.keys()) {
                if (key.startsWith(prefix) || key == latestKey) {
                    keysToRemove.add(key)
                    
                    // 如果是会话映射，还要删除对应的元数据
                    if (key.startsWith(prefix) && !key.startsWith(LATEST_SESSION_PREFIX)) {
                        val sessionId = prefs.get(key, null)
                        if (sessionId != null) {
                            val metaKey = SESSION_METADATA_PREFIX + sessionId
                            keysToRemove.add(metaKey)
                        }
                    }
                }
            }
            
            // 删除键
            for (key in keysToRemove) {
                prefs.remove(key)
            }
            
            prefs.flush()
            
            val removedCount = keysToRemove.size
            logger.info("[SessionIdRegistry] 清理项目会话: $projectPath, 删除 $removedCount 个记录")
            removedCount
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 清理项目会话失败", e)
            0
        }
    }
    
    /**
     * 更新会话的最后访问时间
     */
    private fun updateLastAccessTime(sessionId: String) {
        try {
            val metaKey = SESSION_METADATA_PREFIX + sessionId
            val metaJson = prefs.get(metaKey, null)
            if (metaJson != null) {
                val metadata = SessionMetadata.fromJsonString(metaJson)
                if (metadata != null) {
                    val updatedMetadata = metadata.copy(lastAccessTime = System.currentTimeMillis())
                    prefs.put(metaKey, updatedMetadata.toJsonString())
                    // 不需要立即 flush，访问时间更新不是关键操作
                }
            }
        } catch (e: Exception) {
            logger.debug("[SessionIdRegistry] 更新访问时间失败: $sessionId", e)
        }
    }
    
    /**
     * 查找项目的其他会话（用于更新最新会话记录）
     */
    private fun findOtherSessionForProject(projectPath: String, excludeSessionId: String): String? {
        val projectSessions = getProjectSessions(projectPath)
        return projectSessions.values.firstOrNull { it != excludeSessionId }
    }
    
    /**
     * 创建会话键
     */
    private fun createSessionKey(projectPath: String, tabId: String): String {
        return "$projectPath:$tabId"
    }
    
    /**
     * 获取注册表统计信息
     */
    fun getRegistryStats(): RegistryStats {
        return try {
            var totalSessions = 0
            var totalProjects = 0
            val projects = mutableSetOf<String>()
            
            for (key in prefs.keys()) {
                if (!key.startsWith(LATEST_SESSION_PREFIX) && !key.startsWith(SESSION_METADATA_PREFIX)) {
                    totalSessions++
                    val colonIndex = key.lastIndexOf(':')
                    if (colonIndex > 0) {
                        val projectPath = key.substring(0, colonIndex)
                        projects.add(projectPath)
                    }
                }
            }
            
            totalProjects = projects.size
            
            RegistryStats(
                totalSessions = totalSessions,
                totalProjects = totalProjects,
                projects = projects.toList()
            )
        } catch (e: Exception) {
            logger.error("[SessionIdRegistry] 获取统计信息失败", e)
            RegistryStats(0, 0, emptyList())
        }
    }
    
    /**
     * 会话元数据
     */
    data class SessionMetadata(
        val projectPath: String,
        val tabId: String,
        val sessionId: String,
        val createTime: Long,
        val lastAccessTime: Long
    ) {
        fun toJsonString(): String {
            return """{"projectPath":"$projectPath","tabId":"$tabId","sessionId":"$sessionId","createTime":$createTime,"lastAccessTime":$lastAccessTime}"""
        }
        
        companion object {
            fun fromJsonString(json: String): SessionMetadata? {
                return try {
                    val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
                    SessionMetadata(
                        projectPath = obj.get("projectPath").asString,
                        tabId = obj.get("tabId").asString,
                        sessionId = obj.get("sessionId").asString,
                        createTime = obj.get("createTime").asLong,
                        lastAccessTime = obj.get("lastAccessTime").asLong
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    
    /**
     * 注册表统计信息
     */
    data class RegistryStats(
        val totalSessions: Int,
        val totalProjects: Int,
        val projects: List<String>
    )
}