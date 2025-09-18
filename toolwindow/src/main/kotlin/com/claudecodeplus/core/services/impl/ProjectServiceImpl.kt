package com.claudecodeplus.core.services.impl

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logE
import com.claudecodeplus.core.logging.logI
import com.claudecodeplus.core.repositories.ProjectRepository
import com.claudecodeplus.core.services.ProjectService
import com.claudecodeplus.core.types.AppError
import com.claudecodeplus.core.types.Result
import com.claudecodeplus.core.types.suspendResultOf
import com.claudecodeplus.ui.models.LocalProject
import com.claudecodeplus.ui.models.LocalSession
import com.claudecodeplus.ui.models.Project
import com.claudecodeplus.ui.models.ProjectSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 项目服务实现
 * 负责项目管理的业务逻辑
 */
class ProjectServiceImpl(
    private val projectRepository: ProjectRepository
) : ProjectService {
    
    // 项目列表缓存
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    
    // 项目会话列表缓存，键为项目ID
    private val _projectSessions = ConcurrentHashMap<String, MutableStateFlow<List<ProjectSession>>>()
    
    init {
        // 启动时加载所有项目
        loadAllProjects()
    }
    
    override fun observeProjects(): Flow<List<Project>> = _projects.asStateFlow()
    
    override suspend fun getAllProjects(): Result<List<Project>> = suspendResultOf {
        _projects.value
    }
    
    override suspend fun getProjectById(projectId: String): Result<Project?> = suspendResultOf {
        _projects.value.find { it.id == projectId }
    }
    
    override suspend fun getProjectByPath(projectPath: String): Result<Project?> = suspendResultOf {
        _projects.value.find { project ->
            val normalizedProjectPath = project.path.replace('\\', '/')
            val normalizedSearchPath = projectPath.replace('\\', '/')
            normalizedProjectPath.equals(normalizedSearchPath, ignoreCase = true)
        }
    }
    
    override suspend fun createProject(name: String, path: String): Result<Project> = suspendResultOf {
        logI("创建项目: name=$name, path=$path")
        
        // 检查项目是否已存在
        val existingProject = getProjectByPath(path).getOrNull()
        if (existingProject != null) {
            logI("项目已存在，返回现有项目: ${existingProject.name}")
            existingProject
        } else {
        
        // 创建新项目
        val projectId = java.io.File(path).name
        val localProject = LocalProject(
            id = projectId,
            name = name,
            path = path,
            addedAt = Instant.now().toString(),
            lastAccessedAt = Instant.now().toString()
        )
        
        // 保存到数据库
        projectRepository.saveProject(localProject).let { result ->
            if (result.isFailure) {
                throw AppError.ConfigError("保存项目失败: ${result.getOrNull()}")
            }
        }
        
        // 转换为业务模型
        val project = localProject.toProject()
        
        // 更新缓存
        refreshProjectsCache()
        
        // 初始化项目的会话列表
        _projectSessions[projectId] = MutableStateFlow(emptyList())
        
        logI("项目创建成功: ${project.name}")
        project
        }
    }
    
    override suspend fun updateProject(project: Project): Result<Unit> = suspendResultOf {
        logI("更新项目: ${project.name}")
        
        val localProject = project.toLocalProject()
        projectRepository.saveProject(localProject).let { result ->
            if (result.isFailure) {
                throw AppError.ConfigError("更新项目失败")
            }
        }
        
        // 刷新缓存
        refreshProjectsCache()
        
        logI("项目更新成功: ${project.name}")
    }
    
    override suspend fun deleteProject(projectId: String): Result<Unit> = suspendResultOf {
        logI("删除项目: $projectId")
        
        projectRepository.deleteProject(projectId).let { result ->
            if (result.isFailure) {
                throw AppError.ConfigError("删除项目失败")
            }
        }
        
        // 清理缓存
        _projectSessions.remove(projectId)
        refreshProjectsCache()
        
        logI("项目删除成功: $projectId")
    }
    
    override suspend fun loadProjectSessions(projectId: String, forceReload: Boolean): Result<List<ProjectSession>> = suspendResultOf {
        logD("加载项目会话: projectId=$projectId, forceReload=$forceReload")
        
        // 检查缓存
        val cachedFlow = _projectSessions[projectId]
        if (!forceReload && cachedFlow != null && cachedFlow.value.isNotEmpty()) {
            logD("使用缓存的会话列表")
            cachedFlow.value
        } else {
        
        // 从数据库加载
        val localSessionsResult = projectRepository.getProjectSessions(projectId)
        if (localSessionsResult.isFailure) {
            throw AppError.ConfigError("加载项目会话失败")
        }
        
        val localSessions = localSessionsResult.getOrNull() ?: emptyList()
        val sessions = localSessions.map { it.toProjectSession(projectId) }
            .sortedByDescending { it.lastModified }
        
        // 更新缓存
        val sessionFlow = _projectSessions.getOrPut(projectId) { MutableStateFlow(emptyList()) }
        sessionFlow.value = sessions
        
        logD("项目会话加载完成: ${sessions.size}个会话")
        sessions
        }
    }
    
    override fun observeProjectSessions(projectId: String): Flow<List<ProjectSession>> {
        val sessionFlow = _projectSessions.getOrPut(projectId) { MutableStateFlow(emptyList()) }
        return sessionFlow.asStateFlow()
    }
    
    override suspend fun createSession(projectId: String, sessionName: String): Result<ProjectSession> = suspendResultOf {
        logI("创建会话: projectId=$projectId, sessionName=$sessionName")
        
        val sessionId = UUID.randomUUID().toString()
        val now = Instant.now().toString()
        
        val localSession = LocalSession(
            id = sessionId,
            name = sessionName,
            createdAt = now,
            lastAccessedAt = now
        )
        
        // 保存到数据库
        projectRepository.saveSession(projectId, localSession).let { result ->
            if (result.isFailure) {
                throw AppError.ConfigError("保存会话失败")
            }
        }
        
        // 获取项目信息以设置cwd
        val project = getProjectById(projectId).getOrNull()
            ?: throw AppError.ConfigError("项目不存在: $projectId")
        
        val projectSession = localSession.toProjectSession(projectId, project.path)
        
        // 更新缓存
        val sessionFlow = _projectSessions.getOrPut(projectId) { MutableStateFlow(emptyList()) }
        val updatedSessions = listOf(projectSession) + sessionFlow.value
        sessionFlow.value = updatedSessions
        
        logI("会话创建成功: ${projectSession.name}")
        projectSession
    }
    
    override suspend fun updateSession(projectId: String, session: ProjectSession): Result<Unit> = suspendResultOf {
        logD("更新会话: projectId=$projectId, sessionId=${session.id}")
        
        val localSession = session.toLocalSession()
        projectRepository.saveSession(projectId, localSession).let { result ->
            if (result.isFailure) {
                throw AppError.ConfigError("更新会话失败")
            }
        }
        
        // 更新缓存
        val sessionFlow = _projectSessions[projectId]
        if (sessionFlow != null) {
            val updatedSessions = sessionFlow.value.map { existingSession ->
                if (existingSession.id == session.id) session else existingSession
            }
            sessionFlow.value = updatedSessions
        }
        
        logD("会话更新成功: ${session.id}")
    }
    
    override suspend fun deleteSession(projectId: String, sessionId: String): Result<Unit> = suspendResultOf {
        logI("删除会话: projectId=$projectId, sessionId=$sessionId")
        
        projectRepository.deleteSession(projectId, sessionId).let { result ->
            if (result.isFailure) {
                throw AppError.ConfigError("删除会话失败")
            }
        }
        
        // 更新缓存
        val sessionFlow = _projectSessions[projectId]
        if (sessionFlow != null) {
            val updatedSessions = sessionFlow.value.filter { it.id != sessionId }
            sessionFlow.value = updatedSessions
        }
        
        logI("会话删除成功: $sessionId")
    }
    
    override suspend fun setLastSelectedProject(projectId: String): Result<Unit> = suspendResultOf {
        logD("设置最后选中的项目: $projectId")
        projectRepository.setLastSelectedProject(projectId)
    }
    
    override suspend fun getLastSelectedProject(): Result<String?> = suspendResultOf {
        logD("获取最后选中的项目")
        projectRepository.getLastSelectedProject().getOrNull()
    }
    
    override suspend fun setLastSelectedSession(sessionId: String): Result<Unit> = suspendResultOf {
        logD("设置最后选中的会话: $sessionId")
        projectRepository.setLastSelectedSession(sessionId)
    }
    
    override suspend fun getLastSelectedSession(): Result<String?> = suspendResultOf {
        logD("获取最后选中的会话")
        projectRepository.getLastSelectedSession().getOrNull()
    }
    
    /**
     * 加载所有项目（私有方法）
     */
    private fun loadAllProjects() {
        kotlinx.coroutines.MainScope().launch {
            try {
                refreshProjectsCache()
            } catch (e: Exception) {
                logE("加载项目列表失败", e)
            }
        }
    }
    
    /**
     * 刷新项目缓存
     */
    private suspend fun refreshProjectsCache() {
        val localProjectsResult = projectRepository.getAllProjects()
        if (localProjectsResult.isSuccess) {
            val localProjects = localProjectsResult.getOrNull() ?: emptyList()
            val projects = localProjects.map { it.toProject() }
                .sortedByDescending { it.lastAccessedAt }
            _projects.value = projects
            logD("项目缓存已刷新: ${projects.size}个项目")
        } else {
            logE("刷新项目缓存失败")
        }
    }
}

/**
 * 扩展函数：LocalProject 转 Project
 */
private fun LocalProject.toProject(): Project {
    return Project(
        id = this.id,
        name = this.name,
        path = this.path,
        lastAccessedAt = this.lastAccessedAt
    )
}

/**
 * 扩展函数：Project 转 LocalProject
 */
private fun Project.toLocalProject(): LocalProject {
    return LocalProject(
        id = this.id,
        name = this.name,
        path = this.path,
        addedAt = Instant.now().toString(),
        lastAccessedAt = this.lastAccessedAt ?: Instant.now().toString()
    )
}

/**
 * 扩展函数：LocalSession 转 ProjectSession
 */
private fun LocalSession.toProjectSession(projectId: String, cwd: String? = null): ProjectSession {
    return ProjectSession(
        id = this.id,
        projectId = projectId,
        name = this.name,
        createdAt = this.createdAt,
        lastModified = this.lastAccessedAt?.let { 
            try {
                Instant.parse(it).toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        } ?: System.currentTimeMillis(),
        cwd = cwd ?: ""
    )
}

/**
 * 扩展函数：ProjectSession 转 LocalSession
 */
private fun ProjectSession.toLocalSession(): LocalSession {
    return LocalSession(
        id = this.id ?: UUID.randomUUID().toString(),
        name = this.name,
        createdAt = this.createdAt,
        lastAccessedAt = Instant.ofEpochMilli(this.lastModified).toString()
    )
}