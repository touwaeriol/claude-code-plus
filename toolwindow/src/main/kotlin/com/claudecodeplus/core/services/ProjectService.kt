package com.claudecodeplus.core.services

import com.claudecodeplus.core.types.Result
import com.claudecodeplus.ui.models.Project
import com.claudecodeplus.ui.models.ProjectSession
import kotlinx.coroutines.flow.Flow

/**
 * 项目管理服务接口
 * 负责项目的CRUD操作和会话管理
 */
interface ProjectService {
    
    /**
     * 观察所有项目列表
     * @return 项目列表流
     */
    fun observeProjects(): Flow<List<Project>>
    
    /**
     * 获取所有项目
     * @return 项目列表
     */
    suspend fun getAllProjects(): Result<List<Project>>
    
    /**
     * 根据ID获取项目
     * @param projectId 项目ID
     * @return 项目对象
     */
    suspend fun getProjectById(projectId: String): Result<Project?>
    
    /**
     * 根据路径获取项目
     * @param projectPath 项目路径
     * @return 项目对象
     */
    suspend fun getProjectByPath(projectPath: String): Result<Project?>
    
    /**
     * 创建新项目
     * @param name 项目名称
     * @param path 项目路径
     * @return 创建的项目
     */
    suspend fun createProject(name: String, path: String): Result<Project>
    
    /**
     * 更新项目信息
     * @param project 项目对象
     * @return 更新结果
     */
    suspend fun updateProject(project: Project): Result<Unit>
    
    /**
     * 删除项目
     * @param projectId 项目ID
     * @return 删除结果
     */
    suspend fun deleteProject(projectId: String): Result<Unit>
    
    /**
     * 加载项目的会话列表
     * @param projectId 项目ID
     * @param forceReload 是否强制重新加载
     * @return 会话列表
     */
    suspend fun loadProjectSessions(projectId: String, forceReload: Boolean = false): Result<List<ProjectSession>>
    
    /**
     * 观察项目的会话列表
     * @param projectId 项目ID
     * @return 会话列表流
     */
    fun observeProjectSessions(projectId: String): Flow<List<ProjectSession>>
    
    /**
     * 在项目中创建新会话
     * @param projectId 项目ID
     * @param sessionName 会话名称
     * @return 创建的会话
     */
    suspend fun createSession(projectId: String, sessionName: String): Result<ProjectSession>
    
    /**
     * 更新会话信息
     * @param projectId 项目ID
     * @param session 会话对象
     * @return 更新结果
     */
    suspend fun updateSession(projectId: String, session: ProjectSession): Result<Unit>
    
    /**
     * 删除会话
     * @param projectId 项目ID
     * @param sessionId 会话ID
     * @return 删除结果
     */
    suspend fun deleteSession(projectId: String, sessionId: String): Result<Unit>
    
    /**
     * 设置最后选中的项目
     * @param projectId 项目ID
     * @return 设置结果
     */
    suspend fun setLastSelectedProject(projectId: String): Result<Unit>
    
    /**
     * 获取最后选中的项目
     * @return 项目ID
     */
    suspend fun getLastSelectedProject(): Result<String?>
    
    /**
     * 设置最后选中的会话
     * @param sessionId 会话ID
     * @return 设置结果
     */
    suspend fun setLastSelectedSession(sessionId: String): Result<Unit>
    
    /**
     * 获取最后选中的会话
     * @return 会话ID
     */
    suspend fun getLastSelectedSession(): Result<String?>
}