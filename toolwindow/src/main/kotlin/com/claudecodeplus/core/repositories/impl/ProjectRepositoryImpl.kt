package com.claudecodeplus.core.repositories.impl

import com.claudecodeplus.core.logging.logD
import com.claudecodeplus.core.logging.logE
import com.claudecodeplus.core.repositories.ProjectRepository
import com.claudecodeplus.core.types.AppError
import com.claudecodeplus.core.types.Result
import com.claudecodeplus.core.types.suspendResultOf
import com.claudecodeplus.ui.models.LocalConfigManager
import com.claudecodeplus.ui.models.LocalProject
import com.claudecodeplus.ui.models.LocalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 项目数据仓库实现
 * 基于LocalConfigManager进行数据持久化
 */
class ProjectRepositoryImpl(
    private val configManager: LocalConfigManager = LocalConfigManager()
) : ProjectRepository {
    
    override suspend fun getAllProjects(): Result<List<LocalProject>> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("获取所有项目")
            configManager.getAllProjects()
        }
    }
    
    override suspend fun getProjectById(id: String): Result<LocalProject?> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("根据ID获取项目: $id")
            val projects = configManager.getAllProjects()
            projects.find { it.id == id }
        }
    }
    
    override suspend fun getProjectByPath(path: String): Result<LocalProject?> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("根据路径获取项目: $path")
            val projects = configManager.getAllProjects()
            projects.find { project ->
                val normalizedProjectPath = project.path.replace('\\', '/')
                val normalizedSearchPath = path.replace('\\', '/')
                normalizedProjectPath.equals(normalizedSearchPath, ignoreCase = true)
            }
        }
    }
    
    override suspend fun saveProject(project: LocalProject): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("保存项目: ${project.name}")
            configManager.addProject(project.path, project.name)
            Unit
        }
    }
    
    override suspend fun deleteProject(id: String): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("删除项目: $id")
            configManager.removeProject(id)
        }
    }
    
    override suspend fun getProjectSessions(projectId: String): Result<List<LocalSession>> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("获取项目会话: $projectId")
            configManager.getProjectSessions(projectId)
        }
    }
    
    override suspend fun saveSession(projectId: String, session: LocalSession): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("保存会话: projectId=$projectId, sessionId=${session.id}")
            configManager.addSession(
                projectId = projectId,
                sessionId = session.id,
                sessionName = session.name,
                description = session.description,
                model = session.model
            )
            Unit
        }
    }
    
    override suspend fun deleteSession(projectId: String, sessionId: String): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("删除会话: projectId=$projectId, sessionId=$sessionId")
            configManager.removeSession(projectId, sessionId)
        }
    }
    
    override suspend fun updateSessionAccess(projectId: String, sessionId: String): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("更新会话访问时间: projectId=$projectId, sessionId=$sessionId")
            configManager.updateSessionAccess(projectId, sessionId)
        }
    }
    
    override suspend fun updateSessionId(
        projectId: String, 
        oldSessionId: String, 
        newSessionId: String
    ): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("更新会话ID: $oldSessionId -> $newSessionId")
            configManager.updateSessionId(projectId, oldSessionId, newSessionId)
        }
    }
    
    override suspend fun getLastSelectedProject(): Result<String?> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("获取最后选中的项目")
            val config = configManager.loadConfig()
            config.lastOpenedProject
        }
    }
    
    override suspend fun setLastSelectedProject(projectId: String): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("设置最后选中的项目: $projectId")
            val config = configManager.loadConfig()
            val updatedConfig = config.copy(lastOpenedProject = projectId)
            configManager.saveConfig(updatedConfig)
        }
    }
    
    override suspend fun getLastSelectedSession(): Result<String?> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("获取最后选中的会话")
            configManager.getLastSelectedSession()
        }
    }
    
    override suspend fun setLastSelectedSession(sessionId: String): Result<Unit> = suspendResultOf {
        withContext(Dispatchers.IO) {
            logD("设置最后选中的会话: $sessionId")
            configManager.saveLastSelectedSession(sessionId)
        }
    }
}