package com.claudecodeplus.ui.models

import com.claudecodeplus.core.logging.*
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * 本地配置数据模型
 * 用于管理 ~/.claude-code-plus/desktop-project.json 中的数据
 */

@Serializable
data class LocalProjectConfig(
    val projects: MutableList<LocalProject> = mutableListOf(),
    val lastOpenedProject: String? = null, // 最后打开的项目ID
    val lastSelectedSession: String? = null, // 最后选中的会话ID
    val version: String = "1.0"
)

@Serializable
data class LocalProject(
    val id: String, // 项目唯一ID，使用编码后的路径
    val name: String, // 项目显示名称
    val path: String, // 项目实际路径
    val addedAt: String, // 添加到本地配置的时间
    val lastAccessedAt: String? = null, // 最后访问时间
    val defaultModel: String? = null, // 项目默认模型（可选）
    val sessions: MutableList<LocalSession> = mutableListOf() // 该项目下的会话记录
)

@Serializable
data class LocalSession(
    val id: String, // 会话UUID
    val name: String, // 会话名称
    val createdAt: String, // 创建时间
    val lastAccessedAt: String? = null, // 最后访问时间
    val lastUpdated: Long = 0L, // 最后更新时间戳
    val messageCount: Int = 0, // 消息数量
    val description: String? = null, // 会话描述（可选）
    val model: String? = null // 会话使用的模型（可选）
)

/**
 * 本地配置管理器
 */
class LocalConfigManager {
    companion object {
        private val homeDir = System.getProperty("user.home")
        private val configDir = java.io.File(homeDir, ".claude-code-plus")
        private val configFile = java.io.File(configDir, "desktop-project.json")
        private val logDir = java.io.File(configDir, "log")
        
        fun getConfigDir(): java.io.File = configDir
        fun getLogDir(): java.io.File = logDir
        fun getConfigFile(): java.io.File = configFile
    }
    
    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    init {
        // 确保目录存在
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
    }
    
    /**
     * 加载本地配置（带备份恢复机制）
     */
    fun loadConfig(): LocalProjectConfig {
        if (!configFile.exists()) {
            return LocalProjectConfig()
        }
        
        // 主配置文件
        var lastException: Exception? = null
        try {
            val content = configFile.readText().trim()
            if (content.isNotEmpty()) {
                return json.decodeFromString<LocalProjectConfig>(content)
            } else {
    //                 logD("[LocalConfigManager] 配置文件为空")
            }
        } catch (e: Exception) {
            lastException = e
    //             logD("[LocalConfigManager] 加载主配置文件失败: ${e.message}")
        }
        
        // 尝试临时文件（可能是未完成的写入）
        val tempFile = java.io.File(configFile.parent, "${configFile.name}.tmp")
        if (tempFile.exists()) {
            try {
                val content = tempFile.readText().trim()
                if (content.isNotEmpty()) {
    //                     logD("[LocalConfigManager] 从临时文件恢复配置")
                    val config = json.decodeFromString<LocalProjectConfig>(content)
                    // 恢复后保存到主文件
                    saveConfig(config)
                    return config
                }
            } catch (e: Exception) {
    //                 logD("[LocalConfigManager] 从临时文件恢复失败: ${e.message}")
                tempFile.delete() // 清理损坏的临时文件
            }
        }
        
        // 所有恢复尝试都失败，使用默认配置
    //         logD("[LocalConfigManager] 无法恢复配置，使用默认配置")
        lastException?.printStackTrace()
        return LocalProjectConfig()
    }
    
    /**
     * 保存本地配置（带文件锁防止并发冲突）
     */
    fun saveConfig(config: LocalProjectConfig) {
        var attempts = 0
        val maxAttempts = 3
        
        while (attempts < maxAttempts) {
            try {
                attempts++
                
                // 使用原子写入：先写到临时文件，再重命名
                val tempFile = java.io.File(configFile.parent, "${configFile.name}.tmp")
                val content = json.encodeToString(LocalProjectConfig.serializer(), config)
                
                // 写入临时文件
                tempFile.writeText(content)
                
                // 原子性重命名（在大多数文件系统上是原子操作）
                val success = tempFile.renameTo(configFile)
                if (success) {
    //                     logD("[LocalConfigManager] 配置保存成功 (attempt $attempts)")
                    return
                } else {
    //                     logD("[LocalConfigManager] 配置重命名失败 (attempt $attempts)")
                    tempFile.delete() // 清理临时文件
                }
            } catch (e: Exception) {
    //                 logD("[LocalConfigManager] 保存本地配置失败 (attempt $attempts): ${e.message}")
                if (attempts >= maxAttempts) {
                    logE("Exception caught", e)
                }
            }
            
            // 短暂等待后重试
            if (attempts < maxAttempts) {
                try {
                    Thread.sleep(100) // 等待100ms
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
    }
    
    /**
     * 添加项目到本地配置
     */
    fun addProject(path: String, name: String? = null, defaultModel: String? = null): LocalProject {
        val config = loadConfig()
        val projectId = com.claudecodeplus.sdk.utils.ProjectPathUtils.projectPathToDirectoryName(path)
        val projectName = name ?: path.substringAfterLast("/")
        val now = Instant.now().toString()
        
        // 检查项目是否已存在
        val existingProject = config.projects.find { it.id == projectId }
        if (existingProject != null) {
            // 更新访问时间和默认模型
            val updatedProject = existingProject.copy(
                lastAccessedAt = now,
                defaultModel = defaultModel ?: existingProject.defaultModel
            )
            val updatedProjects = config.projects.toMutableList()
            updatedProjects.removeIf { it.id == projectId }
            updatedProjects.add(updatedProject)
            
            val updatedConfig = config.copy(
                projects = updatedProjects,
                lastOpenedProject = projectId
            )
            saveConfig(updatedConfig)
            return updatedProject
        }
        
        val newProject = LocalProject(
            id = projectId,
            name = projectName,
            path = path,
            addedAt = now,
            lastAccessedAt = now,
            defaultModel = defaultModel
        )
        
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.add(newProject)
        val updatedConfig = config.copy(
            projects = updatedProjects,
            lastOpenedProject = projectId
        )
        saveConfig(updatedConfig)
        
        return newProject
    }
    
    /**
     * 添加会话到项目
     */
    fun addSession(projectId: String, sessionId: String, sessionName: String, description: String? = null, model: String? = null): LocalSession {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId }
            ?: throw IllegalArgumentException("项目不存在: $projectId")
        
        val now = Instant.now().toString()
        val newSession = LocalSession(
            id = sessionId,
            name = sessionName,
            createdAt = now,
            lastAccessedAt = now,
            description = description,
            model = model
        )
        
        val updatedSessions = project.sessions.toMutableList()
        updatedSessions.add(0, newSession) // 添加到开头
        val updatedProject = project.copy(sessions = updatedSessions)
        
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.removeIf { it.id == projectId }
        updatedProjects.add(updatedProject)
        
        val updatedConfig = config.copy(projects = updatedProjects)
        saveConfig(updatedConfig)
        
        return newSession
    }
    
    /**
     * 获取项目的会话列表
     */
    fun getProjectSessions(projectId: String): List<LocalSession> {
        val config = loadConfig()
        return config.projects.find { it.id == projectId }?.sessions ?: emptyList()
    }
    
    /**
     * 更新会话访问时间
     */
    fun updateSessionAccess(projectId: String, sessionId: String) {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId } ?: return
        val session = project.sessions.find { it.id == sessionId } ?: return
        
        val now = Instant.now().toString()
        val updatedSession = session.copy(lastAccessedAt = now)
        
        val updatedSessions = project.sessions.toMutableList()
        updatedSessions.removeIf { it.id == sessionId }
        updatedSessions.add(0, updatedSession) // 移到开头
        val updatedProject = project.copy(sessions = updatedSessions)
        
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.removeIf { it.id == projectId }
        updatedProjects.add(updatedProject)
        
        val updatedConfig = config.copy(projects = updatedProjects)
        saveConfig(updatedConfig)
    }
    
    /**
     * 更新会话ID（当Claude CLI返回新的sessionId时）
     */
    fun updateSessionId(projectId: String, oldSessionId: String, newSessionId: String) {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId } ?: return
        val session = project.sessions.find { it.id == oldSessionId } ?: return
        
        val now = Instant.now().toString()
        val updatedSession = session.copy(
            id = newSessionId,
            lastAccessedAt = now
        )
        
        val updatedSessions = project.sessions.toMutableList()
        updatedSessions.removeIf { it.id == oldSessionId }
        updatedSessions.add(0, updatedSession) // 移到开头，使用新ID
        val updatedProject = project.copy(sessions = updatedSessions)
        
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.removeIf { it.id == projectId }
        updatedProjects.add(updatedProject)
        
        val updatedConfig = config.copy(projects = updatedProjects)
        saveConfig(updatedConfig)
        
    //         logD("[LocalConfigManager] 会话ID已更新: $oldSessionId -> $newSessionId")
    }
    
    /**
     * 为新会话设置sessionId（当第一次获得真实sessionId时）
     * @param projectId 项目ID
     * @param newSessionId 新的会话ID
     */
    fun updateNewSessionId(projectId: String, newSessionId: String) {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId } ?: return
        
        // 查找最新的会话记录进行更新（通常是最近修改的）
        val sessionToUpdate = project.sessions.maxByOrNull { 
            Instant.parse(it.lastAccessedAt ?: it.createdAt).toEpochMilli() 
        }
        
        if (sessionToUpdate != null) {
            val now = Instant.now().toString()
            val updatedSession = sessionToUpdate.copy(
                id = newSessionId,
                lastAccessedAt = now
            )
            
            val updatedSessions = project.sessions.toMutableList()
            updatedSessions.removeIf { it.id == sessionToUpdate.id }
            updatedSessions.add(0, updatedSession) // 移到开头，使用新ID
            val updatedProject = project.copy(sessions = updatedSessions)
            
            val updatedProjects = config.projects.toMutableList()
            updatedProjects.removeIf { it.id == projectId }
            updatedProjects.add(updatedProject)
            
            val updatedConfig = config.copy(projects = updatedProjects)
            saveConfig(updatedConfig)
            
    //             logD("[LocalConfigManager] 新会话ID已设置: ${sessionToUpdate.id} -> $newSessionId")
        } else {
    //             logD("[LocalConfigManager] 未找到需要更新的新会话记录")
        }
    }
    
    /**
     * 获取所有项目
     */
    fun getAllProjects(): List<LocalProject> {
        return loadConfig().projects.sortedByDescending { it.lastAccessedAt }
    }
    
    /**
     * 删除项目
     */
    fun removeProject(projectId: String) {
        val config = loadConfig()
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.removeIf { it.id == projectId }
        
        val updatedConfig = config.copy(
            projects = updatedProjects,
            lastOpenedProject = if (config.lastOpenedProject == projectId) null else config.lastOpenedProject
        )
        saveConfig(updatedConfig)
    }
    
    /**
     * 删除会话
     */
    fun removeSession(projectId: String, sessionId: String) {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId } ?: return
        
        val updatedSessions = project.sessions.toMutableList()
        updatedSessions.removeIf { it.id == sessionId }
        val updatedProject = project.copy(sessions = updatedSessions)
        
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.removeIf { it.id == projectId }
        updatedProjects.add(updatedProject)
        
        val updatedConfig = config.copy(projects = updatedProjects)
        saveConfig(updatedConfig)
    }
    
    /**
     * 更新项目默认模型
     */
    fun updateProjectDefaultModel(projectId: String, model: String?) {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId } ?: return
        val updatedProject = project.copy(defaultModel = model)
        
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.removeIf { it.id == projectId }
        updatedProjects.add(updatedProject)
        
        val updatedConfig = config.copy(projects = updatedProjects)
        saveConfig(updatedConfig)
    }
    
    /**
     * 更新会话模型
     */
    fun updateSessionModel(projectId: String, sessionId: String, model: String?) {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId } ?: return
        val session = project.sessions.find { it.id == sessionId } ?: return
        
        val updatedSession = session.copy(model = model)
        val sessionIndex = project.sessions.indexOfFirst { it.id == sessionId }.takeIf { it >= 0 } ?: 0
        
        val updatedSessions = project.sessions.toMutableList()
        updatedSessions.removeIf { it.id == sessionId }
        updatedSessions.add(sessionIndex, updatedSession)
        val updatedProject = project.copy(sessions = updatedSessions)
        
        val updatedProjects = config.projects.toMutableList()
        updatedProjects.removeIf { it.id == projectId }
        updatedProjects.add(updatedProject)
        
        val updatedConfig = config.copy(projects = updatedProjects)
        saveConfig(updatedConfig)
    }
    
    /**
     * 获取会话应该使用的模型（优先使用会话模型，否则使用项目默认模型）
     */
    fun getEffectiveModel(projectId: String, sessionId: String): String? {
        val config = loadConfig()
        val project = config.projects.find { it.id == projectId } ?: return null
        val session = project.sessions.find { it.id == sessionId }
        
        return session?.model ?: project.defaultModel
    }
    
    /**
     * 保存最后选中的会话
     */
    fun saveLastSelectedSession(sessionId: String) {
        val config = loadConfig()
        val updatedConfig = config.copy(lastSelectedSession = sessionId)
        saveConfig(updatedConfig)
    //         logD("[LocalConfigManager] 保存最后选中会话: $sessionId")
    }
    
    /**
     * 获取最后选中的会话ID
     */
    fun getLastSelectedSession(): String? {
        return loadConfig().lastSelectedSession
    }
    
    /**
     * 清理无效的最后选中会话ID
     * 当会话不存在时调用
     */
    fun clearLastSelectedSession() {
        val config = loadConfig()
        val updatedConfig = config.copy(lastSelectedSession = null)
        saveConfig(updatedConfig)
    //         logD("[LocalConfigManager] 已清理无效的最后选中会话")
    }
    
    /**
     * 验证最后选中的会话是否仍然存在
     * @return 如果存在返回会话ID，否则返回null并清理配置
     */
    fun validateLastSelectedSession(): String? {
        val config = loadConfig()
        val lastSelectedSessionId = config.lastSelectedSession
        
        if (lastSelectedSessionId != null) {
            // 检查这个会话是否在任何项目中存在
            val sessionExists = config.projects.any { project ->
                project.sessions.any { session -> session.id == lastSelectedSessionId }
            }
            
            if (sessionExists) {
                return lastSelectedSessionId
            } else {
    //                 logD("[LocalConfigManager] 最后选中的会话不存在: $lastSelectedSessionId，清理配置")
                clearLastSelectedSession()
                return null
            }
        }
        
        return null
    }
    
    /**
     * 验证最后选中的会话是否属于指定的工作目录
     * @param workingDirectory 当前工作目录
     * @return 如果属于当前工作目录返回会话ID，否则返回null并清理配置
     */
    fun validateLastSelectedSessionForProject(workingDirectory: String): String? {
        val config = loadConfig()
        val lastSelectedSessionId = config.lastSelectedSession
        
        if (lastSelectedSessionId != null) {
            // 生成当前工作目录对应的项目ID
            val expectedProjectId = com.claudecodeplus.sdk.utils.ProjectPathUtils.projectPathToDirectoryName(workingDirectory)
            
            // 检查会话是否属于当前工作目录的项目
            val currentProject = config.projects.find { it.id == expectedProjectId }
            val sessionBelongsToCurrentProject = currentProject?.sessions?.any { 
                session -> session.id == lastSelectedSessionId 
            } ?: false
            
            if (sessionBelongsToCurrentProject) {
    //                 logD("[LocalConfigManager] 最后选中的会话属于当前项目: $lastSelectedSessionId")
                return lastSelectedSessionId
            } else {
    //                 logD("[LocalConfigManager] 最后选中的会话不属于当前项目 (workingDir: $workingDirectory, projectId: $expectedProjectId, sessionId: $lastSelectedSessionId)，清理配置")
                clearLastSelectedSession()
                return null
            }
        }
        
        return null
    }
    
    /**
     * 更新会话元数据（用于消息持久化）
     */
    fun updateSessionMetadata(projectId: String, sessionId: String, updater: (LocalSession) -> LocalSession) {
        try {
            val config = loadConfig()
            val project = config.projects.find { it.id == projectId }
            if (project == null) {
    //                 logD("[LocalConfigManager] 未找到项目: $projectId")
                return
            }
            
            val sessionIndex = project.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex == -1) {
    //                 logD("[LocalConfigManager] 未找到会话: $sessionId")
                return
            }
            
            val originalSession = project.sessions[sessionIndex]
            val updatedSession = updater(originalSession)
            
            // 更新会话
            project.sessions[sessionIndex] = updatedSession
            
            // 保存配置
            saveConfig(config)
            
    //             logD("[LocalConfigManager] 会话元数据已更新: sessionId=$sessionId, messageCount=${updatedSession.messageCount}")
        } catch (e: Exception) {
    //             logD("[LocalConfigManager] 更新会话元数据失败: ${e.message}")
            logE("Exception caught", e)
        }
    }
}
