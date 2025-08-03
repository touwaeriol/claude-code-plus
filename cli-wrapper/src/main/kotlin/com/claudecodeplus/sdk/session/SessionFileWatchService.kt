package com.claudecodeplus.sdk.session

import cn.hutool.core.io.watch.WatchMonitor
import cn.hutool.core.io.watch.Watcher
import com.claudecodeplus.sdk.ProjectPathUtils
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.io.File
import java.nio.file.Path
import java.nio.file.WatchEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * 简化的会话文件监听服务
 * 
 * 管理最多3个项目的文件夹监听，基于LRU策略自动清理
 * 使用 Hutool 的 WatchMonitor 实现文件系统监听
 */
class SessionFileWatchService(
    private val scope: CoroutineScope
) {
    private val logger = KotlinLogging.logger {}
    private val gson = Gson()
    
    companion object {
        private const val MAX_WATCHED_PROJECTS = 3
        private const val PROJECT_CLEANUP_DELAY = 10 * 60 * 1000L // 10分钟
    }
    
    // 项目路径 -> WatchMonitor 的映射（按访问时间排序）
    private val projectWatchers = LinkedHashMap<String, WatchMonitor>()
    
    // 项目最后访问时间
    private val projectLastAccess = ConcurrentHashMap<String, Long>()
    
    // 复合键 (projectPath:sessionId) -> SessionFileTracker 的映射
    private val sessionTrackers = ConcurrentHashMap<String, SessionFileTracker>()
    
    // 消息流，用于发布文件变化事件
    private val messageFlow = MutableSharedFlow<FileChangeEvent>()
    
    /**
     * 文件变化事件
     */
    data class FileChangeEvent(
        val sessionId: String,
        val projectPath: String,
        val messages: List<ClaudeFileMessage>
    )
    
    /**
     * 智能开始监听项目（最多3个）
     */
    fun startWatchingProject(projectPath: String) {
        // 更新访问时间
        projectLastAccess[projectPath] = System.currentTimeMillis()
        
        if (projectWatchers.containsKey(projectPath)) {
            logger.debug { "Project $projectPath is already being watched" }
            return
        }
        
        // 如果超过限制，移除最久未访问的项目
        if (projectWatchers.size >= MAX_WATCHED_PROJECTS) {
            cleanupOldestProject()
        }
        
        val sessionsDir = getSessionsDirectory(projectPath)
        if (!sessionsDir.exists()) {
            logger.info { "Creating sessions directory: ${sessionsDir.absolutePath}" }
            sessionsDir.mkdirs()
        }
        
        logger.info { "Starting to watch sessions directory: ${sessionsDir.absolutePath}" }
        
        val monitor = WatchMonitor.create(
            sessionsDir.toPath(), 
            WatchMonitor.ENTRY_MODIFY, 
            WatchMonitor.ENTRY_CREATE,
            WatchMonitor.ENTRY_DELETE
        )
        
        monitor.setWatcher(object : Watcher {
            override fun onModify(event: WatchEvent<*>, currentPath: Path) {
                val fileName = event.context().toString()
                logger.debug { "File modified: $fileName in $projectPath" }
                handleFileChange(fileName, projectPath)
            }
            
            override fun onCreate(event: WatchEvent<*>, currentPath: Path) {
                val fileName = event.context().toString()
                logger.debug { "File created: $fileName in $projectPath" }
                handleNewFile(fileName, projectPath)
            }
            
            override fun onDelete(event: WatchEvent<*>, currentPath: Path) {
                val fileName = event.context().toString()
                logger.debug { "File deleted: $fileName in $projectPath" }
                handleFileDelete(fileName, projectPath)
            }
            
            override fun onOverflow(event: WatchEvent<*>, currentPath: Path) {
                logger.warn { "Watch event overflow in $projectPath" }
                // 溢出时可能需要重新扫描目录
            }
        })
        
        // 在协程中启动监听器
        scope.launch(Dispatchers.IO) {
            try {
                monitor.start()
            } catch (e: Exception) {
                logger.error(e) { "Failed to start watch monitor for $projectPath" }
                projectWatchers.remove(projectPath)
            }
        }
        
        projectWatchers[projectPath] = monitor
    }
    
    /**
     * 清理最久未访问的项目
     */
    private fun cleanupOldestProject() {
        val oldestProject = projectLastAccess.minByOrNull { it.value }?.key
        if (oldestProject != null) {
            logger.info { "Cleaning up oldest project: $oldestProject" }
            stopWatchingProject(oldestProject)
        }
    }
    
    /**
     * 定时清理不活跃的项目
     */
    fun cleanupInactiveProjects() {
        val now = System.currentTimeMillis()
        val inactiveProjects = projectLastAccess.filter { (_, lastAccess) ->
            now - lastAccess > PROJECT_CLEANUP_DELAY
        }
        
        inactiveProjects.keys.forEach { projectPath ->
            logger.info { "Cleaning up inactive project: $projectPath" }
            stopWatchingProject(projectPath)
        }
    }
    
    /**
     * 获取或创建指定会话的文件追踪器
     */
    fun getOrCreateTracker(sessionId: String, projectPath: String): SessionFileTracker {
        val key = "$projectPath:$sessionId"
        return sessionTrackers.getOrPut(key) {
            val filePath = getSessionFilePath(projectPath, sessionId)
            logger.debug { "Creating tracker for session $sessionId at $filePath" }
            SessionFileTracker(sessionId, filePath, projectPath)
        }
    }
    
    /**
     * 订阅指定会话的消息更新
     */
    fun subscribeToSession(sessionId: String): Flow<List<ClaudeFileMessage>> {
        return messageFlow
            .filter { it.sessionId == sessionId }
            .map { it.messages }
            .catch { e ->
                logger.error(e) { "Error in session subscription for $sessionId" }
                emitAll(emptyFlow())
            }
    }
    
    /**
     * 订阅所有会话的消息更新
     */
    fun subscribeToAll(): Flow<FileChangeEvent> = messageFlow.asSharedFlow()
    
    /**
     * 处理文件变化
     */
    private fun handleFileChange(fileName: String, projectPath: String) {
        if (!fileName.endsWith(".jsonl")) return
        
        val sessionId = fileName.removeSuffix(".jsonl")
        
        scope.launch(Dispatchers.IO) {
            try {
                val tracker = getOrCreateTracker(sessionId, projectPath)
                val newMessages = tracker.readNewMessages()
                
                if (newMessages.isNotEmpty()) {
                    logger.debug { "Found ${newMessages.size} new messages for session $sessionId" }
                    messageFlow.emit(FileChangeEvent(sessionId, projectPath, newMessages))
                }
            } catch (e: Exception) {
                logger.error(e) { "Error handling file change for $fileName" }
            }
        }
    }
    
    /**
     * 处理新文件创建
     */
    private fun handleNewFile(fileName: String, projectPath: String) {
        if (!fileName.endsWith(".jsonl")) return
        
        val sessionId = fileName.removeSuffix(".jsonl")
        logger.info { "New session file detected: $sessionId" }
        
        // 新文件创建时，立即创建追踪器但不读取内容
        // 等待下一次修改事件再读取
        getOrCreateTracker(sessionId, projectPath)
    }
    
    /**
     * 处理文件删除
     */
    private fun handleFileDelete(fileName: String, projectPath: String) {
        if (!fileName.endsWith(".jsonl")) return
        
        val sessionId = fileName.removeSuffix(".jsonl")
        val key = "$projectPath:$sessionId"
        
        logger.info { "Session file deleted: $sessionId" }
        sessionTrackers.remove(key)?.also { tracker ->
            tracker.close()
        }
    }
    
    /**
     * 停止监听指定项目
     */
    fun stopWatchingProject(projectPath: String) {
        projectWatchers.remove(projectPath)?.also { monitor ->
            logger.info { "Stopping watch for project: $projectPath" }
            monitor.close()
            
            // 清理该项目的所有追踪器
            sessionTrackers.keys
                .filter { it.startsWith("$projectPath:") }
                .forEach { key ->
                    sessionTrackers.remove(key)?.close()
                }
        }
        
        // 清理访问时间记录
        projectLastAccess.remove(projectPath)
    }
    
    /**
     * 停止所有监听
     */
    fun stopAll() {
        logger.info { "Stopping all file watchers" }
        
        projectWatchers.values.forEach { it.close() }
        projectWatchers.clear()
        
        sessionTrackers.values.forEach { it.close() }
        sessionTrackers.clear()
    }
    
    /**
     * 获取会话目录路径
     */
    fun getSessionsDirectory(projectPath: String): File {
        val encodedPath = ProjectPathUtils.projectPathToDirectoryName(projectPath)
        val homeDir = System.getProperty("user.home")
        return File(homeDir, ".claude/projects/$encodedPath")
    }
    
    /**
     * 获取会话文件路径
     */
    fun getSessionFilePath(projectPath: String, sessionId: String): String {
        return File(getSessionsDirectory(projectPath), "$sessionId.jsonl").absolutePath
    }
    
    /**
     * 检查会话文件是否存在
     */
    fun sessionFileExists(projectPath: String, sessionId: String): Boolean {
        return File(getSessionFilePath(projectPath, sessionId)).exists()
    }
    
    /**
     * 获取项目的所有会话ID
     */
    fun getProjectSessions(projectPath: String): List<String> {
        val sessionsDir = getSessionsDirectory(projectPath)
        if (!sessionsDir.exists()) return emptyList()
        
        return sessionsDir.listFiles { file -> 
            file.isFile && file.extension == "jsonl" 
        }?.map { 
            it.nameWithoutExtension 
        } ?: emptyList()
    }
}