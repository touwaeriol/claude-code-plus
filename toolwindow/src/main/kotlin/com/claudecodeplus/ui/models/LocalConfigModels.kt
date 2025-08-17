package com.claudecodeplus.ui.models

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
     * 加载本地配置
     */
    fun loadConfig(): LocalProjectConfig {
        return if (configFile.exists()) {
            try {
                val content = configFile.readText()
                json.decodeFromString<LocalProjectConfig>(content)
            } catch (e: Exception) {
                println("加载本地配置失败，使用默认配置: ${e.message}")
                LocalProjectConfig()
            }
        } else {
            LocalProjectConfig()
        }
    }
    
    /**
     * 保存本地配置
     */
    fun saveConfig(config: LocalProjectConfig) {
        try {
            val content = json.encodeToString(LocalProjectConfig.serializer(), config)
            configFile.writeText(content)
        } catch (e: Exception) {
            println("保存本地配置失败: ${e.message}")
        }
    }
    
    /**
     * 添加项目到本地配置
     */
    fun addProject(path: String, name: String? = null, defaultModel: String? = null): LocalProject {
        val config = loadConfig()
        val projectId = com.claudecodeplus.sdk.ProjectPathUtils.projectPathToDirectoryName(path)
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
        
        println("[LocalConfigManager] 会话ID已更新: $oldSessionId -> $newSessionId")
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
            
            println("[LocalConfigManager] 新会话ID已设置: ${sessionToUpdate.id} -> $newSessionId")
        } else {
            println("[LocalConfigManager] 未找到需要更新的新会话记录")
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
        println("[LocalConfigManager] 保存最后选中会话: $sessionId")
    }
    
    /**
     * 获取最后选中的会话ID
     */
    fun getLastSelectedSession(): String? {
        return loadConfig().lastSelectedSession
    }
    
    /**
     * 更新会话元数据（用于消息持久化）
     */
    fun updateSessionMetadata(projectId: String, sessionId: String, updater: (LocalSession) -> LocalSession) {
        try {
            val config = loadConfig()
            val project = config.projects.find { it.id == projectId }
            if (project == null) {
                println("[LocalConfigManager] 未找到项目: $projectId")
                return
            }
            
            val sessionIndex = project.sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex == -1) {
                println("[LocalConfigManager] 未找到会话: $sessionId")
                return
            }
            
            val originalSession = project.sessions[sessionIndex]
            val updatedSession = updater(originalSession)
            
            // 更新会话
            project.sessions[sessionIndex] = updatedSession
            
            // 保存配置
            saveConfig(config)
            
            println("[LocalConfigManager] 会话元数据已更新: sessionId=$sessionId, messageCount=${updatedSession.messageCount}")
        } catch (e: Exception) {
            println("[LocalConfigManager] 更新会话元数据失败: ${e.message}")
            e.printStackTrace()
        }
    }
}