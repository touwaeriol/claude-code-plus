package com.claudecodeplus.core.repositories

import com.claudecodeplus.core.types.Result
import com.claudecodeplus.ui.models.LocalProject
import com.claudecodeplus.ui.models.LocalSession

/**
 * 项目数据仓库接口
 * 负责项目和会话的数据持久化
 */
interface ProjectRepository {
    
    /**
     * 获取所有项目
     */
    suspend fun getAllProjects(): Result<List<LocalProject>>
    
    /**
     * 根据ID获取项目
     */
    suspend fun getProjectById(id: String): Result<LocalProject?>
    
    /**
     * 根据路径获取项目
     */
    suspend fun getProjectByPath(path: String): Result<LocalProject?>
    
    /**
     * 保存项目
     */
    suspend fun saveProject(project: LocalProject): Result<Unit>
    
    /**
     * 删除项目
     */
    suspend fun deleteProject(id: String): Result<Unit>
    
    /**
     * 获取项目的会话列表
     */
    suspend fun getProjectSessions(projectId: String): Result<List<LocalSession>>
    
    /**
     * 保存会话
     */
    suspend fun saveSession(projectId: String, session: LocalSession): Result<Unit>
    
    /**
     * 删除会话
     */
    suspend fun deleteSession(projectId: String, sessionId: String): Result<Unit>
    
    /**
     * 更新会话访问时间
     */
    suspend fun updateSessionAccess(projectId: String, sessionId: String): Result<Unit>
    
    /**
     * 更新会话ID
     */
    suspend fun updateSessionId(projectId: String, oldSessionId: String, newSessionId: String): Result<Unit>
    
    /**
     * 获取最后选中的项目
     */
    suspend fun getLastSelectedProject(): Result<String?>
    
    /**
     * 设置最后选中的项目
     */
    suspend fun setLastSelectedProject(projectId: String): Result<Unit>
    
    /**
     * 获取最后选中的会话
     */
    suspend fun getLastSelectedSession(): Result<String?>
    
    /**
     * 设置最后选中的会话
     */
    suspend fun setLastSelectedSession(sessionId: String): Result<Unit>
}