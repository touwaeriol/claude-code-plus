package com.claudecodeplus.plugin.services

import com.claudecodeplus.mcp.server.IdeaMcpServer
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP 服务器管理器
 * 负责管理项目级别的 MCP 服务器实例
 */
@Service(Service.Level.PROJECT)
class McpServerManager(private val project: Project) {
    private val logger = Logger.getInstance(McpServerManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 存储每个项目的 MCP 服务器实例
    private var mcpServer: IdeaMcpServer? = null
    private var isServerRunning = false
    
    /**
     * 启动 MCP 服务器
     */
    suspend fun startMcpServer(port: Int = 8001): Boolean {
        if (isServerRunning) {
            logger.warn("MCP 服务器已在运行，项目: ${project.name}")
            return true
        }
        
        return try {
            logger.info("正在为项目 '${project.name}' 启动 MCP 服务器，端口: $port")
            
            mcpServer = IdeaMcpServer(project, port)
            mcpServer?.start()
            
            isServerRunning = true
            logger.info("MCP 服务器启动成功，项目: ${project.name}, 端口: $port")
            true
            
        } catch (e: Exception) {
            logger.error("启动 MCP 服务器失败，项目: ${project.name}", e)
            false
        }
    }
    
    /**
     * 停止 MCP 服务器
     */
    suspend fun stopMcpServer() {
        if (!isServerRunning) {
            logger.warn("MCP 服务器未在运行，项目: ${project.name}")
            return
        }
        
        try {
            logger.info("正在停止项目 '${project.name}' 的 MCP 服务器")
            
            mcpServer?.stop()
            mcpServer = null
            
            isServerRunning = false
            logger.info("MCP 服务器已停止，项目: ${project.name}")
            
        } catch (e: Exception) {
            logger.error("停止 MCP 服务器失败，项目: ${project.name}", e)
        }
    }
    
    /**
     * 检查 MCP 服务器是否正在运行
     */
    fun isServerRunning(): Boolean = isServerRunning && mcpServer?.isRunning?.get() == true
    
    /**
     * 获取 MCP 服务器信息
     */
    fun getServerInfo(): Map<String, Any>? {
        return if (isServerRunning) {
            mapOf(
                "project" to project.name,
                "status" to "running",
                "server" to "jetbrains-ide-mcp",
                "version" to "1.0.0"
            )
        } else {
            null
        }
    }
    
    /**
     * 项目关闭时自动停止服务器
     */
    fun dispose() {
        scope.launch {
            if (isServerRunning) {
                stopMcpServer()
            }
        }
        scope.cancel()
    }
}

/**
 * 全局 MCP 服务器管理器
 * 管理应用级别的 MCP 服务器状态
 */
@Service(Service.Level.APP)
class GlobalMcpServerManager {
    private val logger = Logger.getInstance(GlobalMcpServerManager::class.java)
    private val projectServers = ConcurrentHashMap<String, McpServerManager>()
    
    /**
     * 注册项目的 MCP 服务器管理器
     */
    fun registerProjectManager(project: Project, manager: McpServerManager) {
        projectServers[project.name] = manager
        logger.info("已注册项目 MCP 服务器管理器: ${project.name}")
    }
    
    /**
     * 注销项目的 MCP 服务器管理器
     */
    fun unregisterProjectManager(project: Project) {
        projectServers.remove(project.name)
        logger.info("已注销项目 MCP 服务器管理器: ${project.name}")
    }
    
    /**
     * 获取所有正在运行的 MCP 服务器信息
     */
    fun getAllServerInfo(): List<Map<String, Any>> {
        return projectServers.values.mapNotNull { it.getServerInfo() }
    }
    
    /**
     * 获取指定项目的 MCP 服务器管理器
     */
    fun getProjectManager(projectName: String): McpServerManager? {
        return projectServers[projectName]
    }
}