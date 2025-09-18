package com.claudecodeplus.plugin.adapters

import com.claudecodeplus.core.services.ProjectService
import com.claudecodeplus.core.types.Result
import com.claudecodeplus.core.types.AppError
import com.claudecodeplus.ui.models.Project
import com.claudecodeplus.ui.models.ProjectSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 将简单的 IdeaProjectServiceAdapter 适配到复杂的 ProjectService 接口
 * 为 PluginComposeFactory 提供兼容性
 */
class ProjectServiceAdapter : ProjectService {

    override fun observeProjects(): Flow<List<Project>> {
        return flowOf(emptyList())
    }

    override suspend fun getAllProjects(): Result<List<Project>> {
        return Result.Success(emptyList())
    }

    override suspend fun getProjectById(projectId: String): Result<Project?> {
        return Result.Success(null)
    }

    override suspend fun getProjectByPath(projectPath: String): Result<Project?> {
        return Result.Success(null)
    }

    override suspend fun createProject(name: String, path: String): Result<Project> {
        return Result.Failure(AppError.UnknownError("Not implemented"))
    }

    override suspend fun updateProject(project: Project): Result<Unit> {
        return Result.Success(Unit)
    }

    override suspend fun deleteProject(projectId: String): Result<Unit> {
        return Result.Success(Unit)
    }

    override suspend fun loadProjectSessions(projectId: String, forceReload: Boolean): Result<List<ProjectSession>> {
        return Result.Success(emptyList())
    }

    override fun observeProjectSessions(projectId: String): Flow<List<ProjectSession>> {
        return flowOf(emptyList())
    }

    override suspend fun createSession(projectId: String, sessionName: String): Result<ProjectSession> {
        return Result.Failure(AppError.UnknownError("Not implemented"))
    }

    override suspend fun updateSession(projectId: String, session: ProjectSession): Result<Unit> {
        return Result.Success(Unit)
    }

    override suspend fun deleteSession(projectId: String, sessionId: String): Result<Unit> {
        return Result.Success(Unit)
    }

    override suspend fun setLastSelectedProject(projectId: String): Result<Unit> {
        return Result.Success(Unit)
    }

    override suspend fun getLastSelectedProject(): Result<String?> {
        return Result.Success(null)
    }

    override suspend fun setLastSelectedSession(sessionId: String): Result<Unit> {
        return Result.Success(Unit)
    }

    override suspend fun getLastSelectedSession(): Result<String?> {
        return Result.Success(null)
    }
}