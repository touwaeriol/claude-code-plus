package com.claudecodeplus.ui.models

import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * SessionId 同步服务
 * 
 * 解决 Claude CLI --resume 操作中临时 sessionId 导致的同步问题。
 * 
 * 问题背景：
 * Claude CLI 的 --resume 功能会创建临时 sessionId，导致：
 * 1. 本地配置中保存了临时 sessionId
 * 2. 实际 session 文件使用原始 sessionId
 * 3. 重启应用后无法找到正确的 session 文件
 * 
 * 解决方案：
 * 1. 跟踪 sessionId 变化模式，识别临时 ID
 * 2. 延迟保存策略，避免立即保存临时 ID
 * 3. 验证 sessionId 对应的文件是否存在
 * 4. 智能回退到最后已知的有效 sessionId
 */
class SessionIdSyncService {
    
    companion object {
        private val instance = SessionIdSyncService()
        fun getInstance(): SessionIdSyncService = instance
        
        // 临时 sessionId 的特征识别
        private const val TEMP_ID_DELAY_MS = 2000L // 延迟2秒保存，让临时ID有时间稳定
        private const val MAX_ID_CHANGES_PER_MINUTE = 5 // 每分钟最多5次ID变化，超过则可能是临时ID
    }
    
    // sessionId 变化跟踪
    private val sessionIdHistory = ConcurrentHashMap<String, MutableList<SessionIdChange>>()
    private val pendingUpdates = ConcurrentHashMap<String, Pair<SessionIdUpdate, Job>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * SessionId 变化记录
     */
    private data class SessionIdChange(
        val oldId: String?,
        val newId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val source: String // "CLI" 或 "CONFIG"
    )
    
    /**
     * 待处理的 SessionId 更新
     */
    private data class SessionIdUpdate(
        val projectId: String,
        val oldSessionId: String?,
        val newSessionId: String,
        val projectPath: String?
    )
    
    /**
     * 智能更新 sessionId
     * 
     * 此方法会分析 sessionId 变化模式，延迟保存可能的临时 ID，
     * 并验证新 sessionId 对应的文件是否存在。
     * 
     * @param projectId 项目ID
     * @param oldSessionId 旧的sessionId（可能为null，表示新会话）
     * @param newSessionId 新的sessionId
     * @param projectPath 项目路径，用于验证session文件存在性
     */
    fun smartUpdateSessionId(
        projectId: String,
        oldSessionId: String?,
        newSessionId: String,
        projectPath: String? = null
    ) {
        val key = "$projectId:${oldSessionId ?: "new"}"
        
        println("[SessionIdSyncService] 🎯 智能更新sessionId请求:")
        println("  - projectId: $projectId")
        println("  - oldSessionId: $oldSessionId") 
        println("  - newSessionId: $newSessionId")
        println("  - projectPath: $projectPath")
        
        // 记录 sessionId 变化
        recordSessionIdChange(key, oldSessionId, newSessionId, "CLI")
        
        // 检查是否可能是临时 ID
        val isLikelyTemporary = isLikelyTemporary(key, newSessionId)
        println("[SessionIdSyncService] 📊 临时ID分析: isLikelyTemporary=$isLikelyTemporary")
        
        // 取消之前的待处理更新
        pendingUpdates[key]?.second?.cancel()
        
        val update = SessionIdUpdate(projectId, oldSessionId, newSessionId, projectPath)
        
        if (isLikelyTemporary) {
            // 延迟更新，等待 sessionId 稳定
            println("[SessionIdSyncService] ⏳ 检测到可能的临时ID，延迟${TEMP_ID_DELAY_MS}ms后更新")
            val job = scope.launch {
                delay(TEMP_ID_DELAY_MS)
                executeUpdate(update)
            }
            pendingUpdates[key] = update to job
        } else {
            // 立即更新
            println("[SessionIdSyncService] ⚡ 立即执行sessionId更新")
            scope.launch {
                executeUpdate(update)
            }
        }
    }
    
    /**
     * 执行实际的更新操作
     */
    private suspend fun executeUpdate(update: SessionIdUpdate) {
        try {
            println("[SessionIdSyncService] 🔧 开始执行sessionId更新:")
            println("  - projectId: ${update.projectId}")
            println("  - oldSessionId: ${update.oldSessionId}")
            println("  - newSessionId: ${update.newSessionId}")
            
            // 验证新 sessionId 对应的文件是否存在
            val isValidSession = update.projectPath?.let { path ->
                validateSessionFile(path, update.newSessionId)
            } ?: true // 如果没有项目路径，默认认为有效
            
            println("[SessionIdSyncService] 📁 Session文件验证: isValidSession=$isValidSession")
            
            if (!isValidSession) {
                println("[SessionIdSyncService] ⚠️ Session文件不存在，尝试使用最后已知的有效sessionId")
                
                // 尝试找到最后一个有效的 sessionId
                val lastValidSessionId = findLastValidSessionId(update.projectId, update.projectPath)
                if (lastValidSessionId != null && lastValidSessionId != update.newSessionId) {
                    println("[SessionIdSyncService] 🔄 回退到有效sessionId: $lastValidSessionId")
                    updateLocalConfig(update.projectId, update.oldSessionId, lastValidSessionId)
                    return
                }
            }
            
            // 执行更新
            updateLocalConfig(update.projectId, update.oldSessionId, update.newSessionId)
            
            // 清理历史记录（保留最近的记录）
            cleanupHistory()
            
        } catch (e: Exception) {
            println("[SessionIdSyncService] ❌ 执行sessionId更新失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 判断是否为可能的临时 ID
     */
    private fun isLikelyTemporary(key: String, newSessionId: String): Boolean {
        val history = sessionIdHistory[key] ?: return false
        val recentChanges = history.filter { 
            System.currentTimeMillis() - it.timestamp < 60_000 // 最近1分钟的变化
        }
        
        // 如果最近1分钟内变化次数过多，可能是临时ID
        if (recentChanges.size >= MAX_ID_CHANGES_PER_MINUTE) {
            println("[SessionIdSyncService] 🔍 检测到频繁ID变化: ${recentChanges.size}次/分钟")
            return true
        }
        
        // 检查是否存在A->B->A的模式（临时ID的典型特征）
        if (history.size >= 3) {
            val last3 = history.takeLast(3)
            val pattern = last3.map { it.newId }
            
            // 检查 original -> temp -> original 模式
            if (pattern[0] == pattern[2] && pattern[1] != pattern[0]) {
                println("[SessionIdSyncService] 🔍 检测到A->B->A模式: ${pattern.joinToString(" -> ")}")
                return newSessionId == pattern[1] // 当前ID是中间的临时ID
            }
        }
        
        return false
    }
    
    /**
     * 记录 sessionId 变化
     */
    private fun recordSessionIdChange(key: String, oldId: String?, newId: String, source: String) {
        val history = sessionIdHistory.getOrPut(key) { mutableListOf() }
        val change = SessionIdChange(oldId, newId, source = source)
        
        history.add(change)
        println("[SessionIdSyncService] 📝 记录sessionId变化: ${oldId} -> ${newId} (源: $source)")
        
        // 限制历史记录大小
        if (history.size > 20) {
            history.removeAt(0)
        }
    }
    
    /**
     * 验证 session 文件是否存在
     */
    private fun validateSessionFile(projectPath: String, sessionId: String): Boolean {
        return try {
            // 使用 ClaudeSessionManager 的逻辑获取session文件路径
            val encodedPath = com.claudecodeplus.sdk.ProjectPathUtils.projectPathToDirectoryName(projectPath)
            val homeDir = System.getProperty("user.home")
            val sessionFile = File(homeDir, ".claude/projects/$encodedPath/$sessionId.jsonl")
            
            val exists = sessionFile.exists()
            println("[SessionIdSyncService] 📁 验证session文件: ${sessionFile.absolutePath} -> 存在: $exists")
            exists
        } catch (e: Exception) {
            println("[SessionIdSyncService] ❌ 验证session文件失败: ${e.message}")
            false
        }
    }
    
    /**
     * 查找最后一个有效的 sessionId
     */
    private fun findLastValidSessionId(projectId: String, projectPath: String?): String? {
        return try {
            val configManager = LocalConfigManager()
            val config = configManager.loadConfig()
            val project = config.projects.find { it.id == projectId }
            
            if (project != null && projectPath != null) {
                // 按访问时间倒序检查每个session
                for (session in project.sessions.sortedByDescending { it.lastAccessedAt }) {
                    if (validateSessionFile(projectPath, session.id)) {
                        println("[SessionIdSyncService] ✅ 找到有效sessionId: ${session.id}")
                        return session.id
                    }
                }
            }
            
            println("[SessionIdSyncService] ❌ 未找到有效sessionId")
            null
        } catch (e: Exception) {
            println("[SessionIdSyncService] ❌ 查找有效sessionId失败: ${e.message}")
            null
        }
    }
    
    /**
     * 更新本地配置 - 使用新的双ID策略
     */
    private fun updateLocalConfig(projectId: String, oldSessionId: String?, newSessionId: String) {
        try {
            val configManager = LocalConfigManager()
            
            if (oldSessionId.isNullOrEmpty()) {
                // 新会话 - 查找没有Claude sessionId的本地会话记录
                val config = configManager.loadConfig()
                val project = config.projects.find { it.id == projectId }
                val unlinkedSession = project?.sessions?.find { it.sessionId == null }
                
                if (unlinkedSession != null) {
                    println("[SessionIdSyncService] 💾 为新会话设置Claude sessionId: localId=${unlinkedSession.id} -> claudeId=$newSessionId")
                    configManager.updateSessionClaudeId(projectId, unlinkedSession.id, newSessionId)
                    
                    // 使用本地会话ID作为lastSelectedSession（稳定标识符）
                    configManager.saveLastSelectedSessionByLocalId(unlinkedSession.id)
                    println("[SessionIdSyncService] 💾 已保存本地会话ID为最后选中: ${unlinkedSession.id}")
                } else {
                    println("[SessionIdSyncService] ⚠️ 未找到未关联的本地会话记录")
                }
            } else {
                // 现有会话 - 查找并更新Claude sessionId
                val existingSession = configManager.findSessionByClaudeId(projectId, oldSessionId)
                if (existingSession != null) {
                    println("[SessionIdSyncService] 💾 更新现有会话的Claude sessionId: ${existingSession.id} (${oldSessionId} -> $newSessionId)")
                    configManager.updateSessionClaudeId(projectId, existingSession.id, newSessionId)
                } else {
                    println("[SessionIdSyncService] ⚠️ 未找到Claude sessionId为 $oldSessionId 的本地会话记录")
                    // 尝试按本地ID查找并更新sessionId字段（保持本地ID不变）
                    val sessionByLocalId = configManager.getProjectSessions(projectId)
                        .find { it.id == oldSessionId }
                    if (sessionByLocalId != null) {
                        println("[SessionIdSyncService] 💾 找到本地ID匹配的会话，更新其Claude sessionId: ${sessionByLocalId.id}")
                        configManager.updateSessionClaudeId(projectId, sessionByLocalId.id, newSessionId)
                    } else {
                        println("[SessionIdSyncService] ❌ 完全找不到匹配的会话记录，跳过更新")
                    }
                }
            }
            
        } catch (e: Exception) {
            println("[SessionIdSyncService] ❌ 更新本地配置失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 清理历史记录
     */
    private fun cleanupHistory() {
        val cutoffTime = System.currentTimeMillis() - 300_000 // 5分钟前
        
        sessionIdHistory.values.forEach { history ->
            history.removeAll { it.timestamp < cutoffTime }
        }
        
        // 删除空的历史记录
        sessionIdHistory.entries.removeAll { it.value.isEmpty() }
    }
    
    /**
     * 强制同步特定项目的 sessionId
     * 在应用启动时调用，确保配置与实际文件同步
     */
    fun forceSyncProject(projectId: String, projectPath: String) {
        scope.launch {
            try {
                println("[SessionIdSyncService] 🔄 强制同步项目sessionId: $projectId")
                
                val configManager = LocalConfigManager()
                val config = configManager.loadConfig()
                val project = config.projects.find { it.id == projectId }
                
                if (project != null) {
                    val sessionsToRemove = mutableListOf<String>()
                    
                    // 检查每个session的Claude sessionId是否有对应的文件
                    for (session in project.sessions) {
                        val claudeSessionId = session.sessionId ?: session.id // 向后兼容
                        val isValid = validateSessionFile(projectPath, claudeSessionId)
                        if (!isValid) {
                            println("[SessionIdSyncService] 🗑️ 发现无效session: localId=${session.id}, claudeId=$claudeSessionId")
                            sessionsToRemove.add(session.id)
                        } else {
                            // 如果session.sessionId为null，但文件存在，说明是旧格式，需要迁移
                            if (session.sessionId == null && session.id != claudeSessionId) {
                                println("[SessionIdSyncService] 🔄 迁移旧格式session: ${session.id}")
                                configManager.updateSessionClaudeId(projectId, session.id, session.id)
                            }
                        }
                    }
                    
                    // 删除无效sessions
                    sessionsToRemove.forEach { localSessionId ->
                        configManager.removeSession(projectId, localSessionId)
                        println("[SessionIdSyncService] 🗑️ 已删除无效session: $localSessionId")
                    }
                    
                    // 如果最后选中的session无效，清理它
                    val lastSelected = config.lastSelectedSession
                    if (lastSelected != null && sessionsToRemove.contains(lastSelected)) {
                        configManager.clearLastSelectedSession()
                        println("[SessionIdSyncService] 🗑️ 已清理无效的最后选中session: $lastSelected")
                    }
                }
                
            } catch (e: Exception) {
                println("[SessionIdSyncService] ❌ 强制同步失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 释放资源
     */
    fun cleanup() {
        scope.cancel()
        pendingUpdates.clear()
        sessionIdHistory.clear()
    }
}