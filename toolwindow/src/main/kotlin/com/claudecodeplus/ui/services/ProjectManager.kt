package com.claudecodeplus.ui.services

import androidx.compose.runtime.mutableStateOf
import com.claudecodeplus.ui.models.ClaudeConfig
import com.claudecodeplus.ui.models.LocalConfigManager
import com.claudecodeplus.ui.models.Project
import com.claudecodeplus.ui.models.ProjectSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 会话加载事件
 * 用于通知其他组件会话已被加载
 */
data class SessionLoadEvent(val session: ProjectSession)

/**
 * 项目管理器 - 项目和会话管理核心组件
 * 
 * 负责管理所有项目及其会话的加载、切换和状态维护。
 * 这是桌面应用中项目列表和会话列表的数据源。
 * 
 * 主要功能：
 * - 从磁盘加载项目列表（扫描 Claude 会话目录）
 * - 管理每个项目的会话列表
 * - 维护当前项目和当前会话状态
 * - 自动选择最近使用的会话
 * - 发布会话加载事件
 * 
 * 数据流：
 * - projects: 所有项目列表（StateFlow）
 * - sessions: 项目 ID 到会话列表的映射（StateFlow）
 * - currentProject: 当前选中的项目
 * - currentSession: 当前选中的会话
 * - sessionLoadEvent: 会话加载事件流（SharedFlow）
 * 
 * 与 Claude CLI 的关系：
 * - 读取 ~/.claude/projects/ 目录结构
 * - 解析 JSONL 格式的会话文件
 * - 保持与 CLI 的目录结构一致
 */
class ProjectManager(
    private val autoLoad: Boolean = true // 是否自动加载项目，false时需要手动选择
) {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects = _projects.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, List<ProjectSession>>>(emptyMap())
    val sessions = _sessions.asStateFlow()

    private val _currentProject = mutableStateOf<Project?>(null)
    val currentProject = _currentProject

    private val _currentSession = mutableStateOf<ProjectSession?>(null)
    val currentSession = _currentSession

    private val _sessionLoadEvent = MutableSharedFlow<SessionLoadEvent>()
    val sessionLoadEvent: SharedFlow<SessionLoadEvent> = _sessionLoadEvent

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    

    init {
        // 总是从本地配置加载项目，不再区分 autoLoad 模式
        loadProjectsFromLocalConfig()
    }
    
    /**
     * 根据指定的工作目录设置当前项目
     * 如果找不到匹配的项目，则使用默认的最近会话选择逻辑
     */
    fun selectProjectByWorkingDirectory(workingDirectory: String) {
        scope.launch {
            // 等待项目加载完成
            kotlinx.coroutines.delay(200)
            
            println("尝试根据工作目录选择项目: $workingDirectory")
            
            // 查找匹配的项目
            val matchingProject = _projects.value.find { project ->
                project.path == workingDirectory
            }
            
            if (matchingProject != null) {
                println("找到匹配的项目: ${matchingProject.name} (${matchingProject.path})")
                _currentProject.value = matchingProject
                
                // 文件监听功能已移除
                println("[ProjectManager] 已启动匹配项目监听: ${matchingProject.name} -> ${matchingProject.path}")
                
                // 加载该项目的会话
                if (!_sessions.value.containsKey(matchingProject.id)) {
                    loadSessionsForProject(matchingProject.id, forceReload = true)
                }
                
                // 优先选择配置文件中记录的最后选中会话
                val projectSessions = _sessions.value[matchingProject.id] ?: emptyList()
                val localConfigManager = LocalConfigManager()
                val lastSelectedSessionId = localConfigManager.getLastSelectedSession()
                
                val sessionToSelect = if (lastSelectedSessionId != null) {
                    // 查找记录的最后选中会话
                    val lastSelectedSession = projectSessions.find { it.id == lastSelectedSessionId }
                    if (lastSelectedSession != null) {
                        println("恢复最后选中的会话: ${lastSelectedSession.name} (${lastSelectedSessionId})")
                        lastSelectedSession
                    } else {
                        println("最后选中的会话不存在，选择最新会话: $lastSelectedSessionId")
                        projectSessions.firstOrNull()
                    }
                } else {
                    println("没有记录的最后选中会话，选择最新会话")
                    projectSessions.firstOrNull()
                }
                
                if (sessionToSelect != null) {
                    setCurrentSession(sessionToSelect, loadHistory = true)
                }
            } else {
                println("未找到匹配工作目录的项目，使用默认选择逻辑")
                findAndSelectLatestSession()
            }
        }
    }
    
    /**
     * 查找所有项目中最新修改的会话
     * 
     * 在应用启动时调用，自动选择用户最近使用的会话。
     * 这提供了更好的用户体验，用户可以继续上次的工作。
     * 
     * 处理流程：
     * 1. 遍历所有项目
     * 2. 加载每个项目的会话列表（如果未加载）
     * 3. 找到最近修改的会话
     * 4. 切换到对应的项目和会话
     */
    private suspend fun findAndSelectLatestSession() {
        println("开始查找所有项目中最新修改的会话...")
        
        var latestSession: ProjectSession? = null
        var latestProject: Project? = null
        var latestModifiedTime = 0L
        
        // 遍历所有项目，查找最新的会话
        for (project in _projects.value) {
            // 确保会话已加载
            if (!_sessions.value.containsKey(project.id)) {
                loadSessionsForProject(project.id, forceReload = true)
                // 等待加载完成
                kotlinx.coroutines.delay(100)
            }
            
            val projectSessions = _sessions.value[project.id] ?: emptyList()
            val latestInProject = projectSessions.firstOrNull() // 已按时间排序
            
            if (latestInProject != null && latestInProject.lastModified > latestModifiedTime) {
                latestSession = latestInProject
                latestProject = project
                latestModifiedTime = latestInProject.lastModified
            }
        }
        
        // 如果找到了最新会话，只在没有当前项目时才切换项目
        if (latestSession != null && latestProject != null) {
            println("找到最新会话: ${latestSession.name} in ${latestProject.name}")
            println("最后修改时间: ${java.time.Instant.ofEpochMilli(latestSession.lastModified)}")
            
            // 只有在没有当前项目时才切换项目，避免强制覆盖已选择的项目
            if (_currentProject.value == null) {
                println("当前无项目，切换到最新会话所在项目: ${latestProject.name}")
                _currentProject.value = latestProject
                
                // 文件监听功能已移除
                println("[ProjectManager] 已启动最新项目监听: ${latestProject.name} -> ${latestProject.path}")
                
                setCurrentSession(latestSession, loadHistory = true)
            } else {
                println("已有当前项目 ${_currentProject.value?.name}，不切换到 ${latestProject.name}")
            }
        } else {
            println("未找到任何会话")
        }
    }
    
    /**
     * 从本地配置文件加载项目列表
     * 并根据 autoLoad 参数决定是否自动选择项目
     */
    private fun loadProjectsFromLocalConfig() {
        scope.launch {
            try {
                println("从本地配置文件加载项目列表...")
                val localConfigManager = com.claudecodeplus.ui.models.LocalConfigManager()
                val localProjects = localConfigManager.getAllProjects()
                
                println("从本地配置加载到 ${localProjects.size} 个项目")
                
                val loadedProjects = localProjects.map { localProject ->
                    Project(
                        id = localProject.id,
                        name = localProject.name,
                        path = localProject.path
                    )
                }
                
                _projects.value = loadedProjects
                
                if (loadedProjects.isNotEmpty()) {
                    println("项目列表加载完成:")
                    loadedProjects.forEach { project ->
                        println("  - ${project.name} (${project.path})")
                    }
                    
                    if (autoLoad) {
                        // 自动加载模式：尝试选择当前工作目录对应的项目
                        val currentDir = System.getProperty("user.dir")
                        println("当前工作目录: $currentDir")
                        
                        val currentDirProject = loadedProjects.find { project ->
                            val normalizedProjectPath = project.path.replace('\\', '/')
                            val normalizedCurrentDir = currentDir.replace('\\', '/')
                            normalizedProjectPath.equals(normalizedCurrentDir, ignoreCase = true)
                        }
                        
                        if (currentDirProject != null) {
                            println("找到匹配当前工作目录的项目: ${currentDirProject.name}")
                            println("[DEBUG] 设置当前项目前: _currentProject.value = ${_currentProject.value?.name}")
                            _currentProject.value = currentDirProject
                            println("[DEBUG] 设置当前项目后: _currentProject.value = ${_currentProject.value?.name}")
                            println("[DEBUG] 触发UI重组...")
                            loadSessionsFromLocalConfig(currentDirProject.id)
                        } else {
                            println("未找到匹配当前工作目录的项目，创建新项目")
                            // 自动创建当前工作目录的项目
                            val projectName = currentDir.substringAfterLast(java.io.File.separator)
                            val newProject = localConfigManager.addProject(currentDir, projectName)
                            
                            val project = Project(
                                id = newProject.id,
                                name = newProject.name,
                                path = newProject.path
                            )
                            
                            _projects.value = loadedProjects + project
                            _currentProject.value = project
                            
                            // 新项目没有会话，初始化为空列表
                            val newSessionsMap = _sessions.value.toMutableMap()
                            newSessionsMap[project.id] = emptyList()
                            _sessions.value = newSessionsMap
                            
                            println("已创建并选择新项目: ${project.name}")
                        }
                    } else {
                        println("手动模式，等待用户选择项目")
                    }
                } else {
                    println("本地配置中没有项目")
                    if (autoLoad) {
                        // 即使没有项目，也自动创建当前工作目录的项目
                        val currentDir = System.getProperty("user.dir")
                        val projectName = currentDir.substringAfterLast(java.io.File.separator)
                        val newProject = localConfigManager.addProject(currentDir, projectName)
                        
                        val project = Project(
                            id = newProject.id,
                            name = newProject.name,
                            path = newProject.path
                        )
                        
                        _projects.value = listOf(project)
                        _currentProject.value = project
                        
                        // 新项目没有会话，初始化为空列表
                        val newSessionsMap = _sessions.value.toMutableMap()
                        newSessionsMap[project.id] = emptyList()
                        _sessions.value = newSessionsMap
                        
                        println("已创建并选择新项目: ${project.name}")
                    }
                }
            } catch (e: Exception) {
                println("从本地配置加载项目失败: ${e.message}")
                e.printStackTrace()
                _projects.value = emptyList()
            }
        }
    }

    fun loadSessionsForProject(projectId: String, forceReload: Boolean = false) {
        if (!forceReload && _sessions.value.containsKey(projectId)) {
            println("项目会话已缓存，跳过加载: $projectId")
            return
        }

        scope.launch {
            try {
                println("开始加载项目会话: $projectId")
                
                // 简化后的逻辑：projectId 就是 Claude 目录名，直接使用
                val basePath = File(System.getProperty("user.home"), ".claude/projects")
                val sessionsDir = File(basePath, projectId)
                
                println("会话目录: ${sessionsDir.absolutePath}")
                println("目录是否存在: ${sessionsDir.exists()}")

                if (sessionsDir.exists() && sessionsDir.isDirectory) {
                    val sessionFiles = sessionsDir.listFiles { _, name -> name.endsWith(".jsonl") } ?: emptyArray()
                    println("找到 ${sessionFiles.size} 个会话文件")
                    
                    val loadedSessions = sessionFiles.mapNotNull { file ->
                        try {
                            println("正在处理会话文件: ${file.name}")
                            val lines = file.readLines()
                            if (lines.isEmpty()) {
                                println("会话文件为空: ${file.name}")
                                return@mapNotNull null
                            }

                            // 使用文件名作为会话ID（这是Claude CLI的约定）
                            val sessionId = file.nameWithoutExtension
                            println("  使用文件名作为sessionId: $sessionId")
                            
                            // 尝试从第一行获取时间戳
                            val timestamp = try {
                                val firstLineJson = json.parseToJsonElement(lines.first()).jsonObject
                                firstLineJson["timestamp"]?.jsonPrimitive?.content ?: ""
                            } catch (e: Exception) {
                                println("  无法解析第一行获取时间戳: ${e.message}")
                                ""
                            }
                            
                            // 生成会话名称
                            val sessionName = generateSessionName(lines, sessionId, timestamp)

                            println("成功加载会话: $sessionId - $sessionName")
                            
                            // 检查生成的会话名称是否是纯数字
                            if (sessionName.matches(Regex("\\d+"))) {
                                println("警告：generateSessionName 返回了纯数字名称！")
                                println("  - sessionName: '$sessionName'")
                                println("  - lines.size: ${lines.size}")
                                println("  - 文件: ${file.name}")
                                // 尝试查看前几行内容
                                println("  - 前3行内容：")
                                lines.take(3).forEachIndexed { index, line ->
                                    println("    ${index + 1}: ${line.take(100)}...")
                                }
                            }
                            
                            // 检查并修正会话名称
                            val validatedSessionName = if (sessionName.matches(Regex("\\d+")) && sessionName.toIntOrNull()?.let { it in 50..10000 } == true) {
                                println("  - 检测到纯数字会话名称: '$sessionName'，使用默认名称")
                                val dateTime = if (timestamp.isNotBlank()) {
                                    try {
                                        val date = timestamp.substringBefore("T")
                                        val time = timestamp.substringAfter("T").substringBefore(".").substringBefore("Z")
                                        "$date $time"
                                    } catch (e: Exception) {
                                        sessionId.take(8)
                                    }
                                } else {
                                    sessionId.take(8)
                                }
                                "会话 $dateTime"
                            } else {
                                sessionName
                            }
                            
                            val finalSessionName = validatedSessionName.take(50)
                            println("DEBUG: 原始会话名称: '$sessionName', 验证后: '$validatedSessionName', 截断后: '$finalSessionName'")
                            // 额外的调试信息
                            println("DEBUG: 即将创建 ProjectSession:")
                            println("  - sessionId: $sessionId")
                            println("  - projectId: $projectId")
                            println("  - name (finalSessionName): '$finalSessionName'")
                            println("  - timestamp: $timestamp")
                            println("  - lines.size: ${lines.size}")
                            
                            // 提取会话的工作目录
                            val sessionCwd = extractCwdFromSessionFile(lines)
                            println("DEBUG: 从会话文件中提取的 cwd: '$sessionCwd'")
                            
                            // 如果无法提取到有效的 cwd，跳过这个会话文件
                            if (sessionCwd.isNullOrBlank()) {
                                println("警告: 会话文件 ${file.name} 无法提取有效的工作目录，已跳过")
                                return@mapNotNull null
                            }
                            
                            val session = ProjectSession(
                                id = sessionId,
                                projectId = projectId,
                                name = finalSessionName,
                                createdAt = timestamp,
                                lastModified = file.lastModified(), // 使用文件的最后修改时间
                                cwd = sessionCwd
                            )
                            println("DEBUG: 创建后的 ProjectSession - ID: ${session.id}, Name: '${session.name}', LastModified: ${file.lastModified()}")
                            
                            // 额外检查：如果名称是纯数字，检查是否等于行数
                            if (session.name.matches(Regex("\\d+"))) {
                                val nameAsNumber = session.name.toIntOrNull()
                                if (nameAsNumber == lines.size) {
                                    println("严重错误：会话名称是文件行数！")
                                    println("  - session.name: '${session.name}'")
                                    println("  - lines.size: ${lines.size}")
                                    println("  - 它们相等！")
                                }
                            }
                            session
                        } catch (e: Exception) {
                            println("处理会话文件失败: ${file.name}, 错误: ${e.message}")
                            e.printStackTrace()
                            null
                        }
                    }.sortedByDescending { it.lastModified } // 按最后修改时间降序排序

                    println("成功加载 ${loadedSessions.size} 个会话")
                    val newSessionsMap = _sessions.value.toMutableMap()
                    newSessionsMap[projectId] = loadedSessions
                    _sessions.value = newSessionsMap
                    
                    // 如果是当前项目，自动选择最新修改的会话
                    if (_currentProject.value?.id == projectId && loadedSessions.isNotEmpty()) {
                        val latestSession = loadedSessions.first() // 已按最后修改时间排序，第一个是最新的
                        println("自动选择最新会话: ${latestSession.name} (最后修改: ${java.time.Instant.ofEpochMilli(latestSession.lastModified)})")
                        setCurrentSession(latestSession, loadHistory = true)
                    } else {
                        println("跳过自动选择会话，因为不是当前项目: 当前=${_currentProject.value?.id}, 加载的=$projectId")
                    }
                } else {
                    println("会话目录不存在或不是目录")
                }
            } catch (e: Exception) {
                println("加载会话失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun setCurrentProject(project: Project) {
        _currentProject.value = project
        _currentSession.value = null
        loadSessionsForProject(project.id)
        
        // 不再需要启动文件监听和设置回调
        println("[ProjectManager] 已选择项目: ${project.name} -> ${project.path}")
    }
    
    /**
     * 强制重新加载所有项目的会话
     */
    fun reloadAllSessions() {
        scope.launch {
            println("强制重新加载所有会话...")
            _sessions.value = emptyMap()
            _projects.value.forEach { project ->
                loadSessionsForProject(project.id, forceReload = true)
            }
        }
    }

    fun setCurrentSession(session: ProjectSession, loadHistory: Boolean = true) {
        _currentSession.value = session
        
        // 保存最后选中的会话到配置文件
        try {
            val localConfigManager = LocalConfigManager()
            localConfigManager.saveLastSelectedSession(session.id)
        } catch (e: Exception) {
            println("[ProjectManager] 保存最后选中会话失败: ${e.message}")
        }
        
        // 触发会话历史加载事件
        if (loadHistory) {
            println("=== ProjectManager.setCurrentSession 发出会话加载事件 ===")
            println("  - session.id: ${session.id}")
            println("  - session.name: ${session.name}")
            println("  - loadHistory: $loadHistory")
            scope.launch {
                println("  - 发出 SessionLoadEvent")
                _sessionLoadEvent.emit(SessionLoadEvent(session))
                println("  - SessionLoadEvent 已发出")
            }
        }
    }
    
    private fun generateSessionName(lines: List<String>, sessionId: String, timestamp: String): String {
        println("\n=== generateSessionName 调试 ===")
        println("  - 输入 lines.size: ${lines.size}")
        println("  - 输入 sessionId: $sessionId")
        println("  - 输入 timestamp: $timestamp")
        
        try {
            // 查找第一条用户消息
            var userMessageCount = 0
            for ((index, line) in lines.withIndex()) {
                if (line.contains("\"type\":\"user\"") && !line.contains("\"isMeta\":true")) {
                    userMessageCount++
                    println("  - 找到用户消息 #$userMessageCount at line $index")
                    
                    val messageJson = json.parseToJsonElement(line).jsonObject["message"]?.jsonObject
                    
                    // 尝试获取 content（可能是字符串或数组）
                    val contentElement = messageJson?.get("content")
                    println("  - contentElement 类型: ${contentElement?.javaClass?.simpleName}")
                    println("  - contentElement 内容: $contentElement")
                    
                    val content = when {
                        contentElement is kotlinx.serialization.json.JsonPrimitive -> {
                            println("  - 处理 JsonPrimitive 类型")
                            contentElement.content
                        }
                        contentElement is kotlinx.serialization.json.JsonArray -> {
                            println("  - 处理 JsonArray 类型，数组大小: ${contentElement.size}")
                            // 处理内容数组（如 [{"type":"text","text":"..."}]）
                            val firstElement = contentElement.firstOrNull()
                            println("  - 第一个元素: $firstElement")
                            firstElement?.jsonObject?.get("text")?.jsonPrimitive?.content
                        }
                        else -> {
                            println("  - 未知的 content 类型")
                            null
                        }
                    }
                    
                    println("  - 提取的 content: '${content?.take(50)}...'")
                    
                    if (!content.isNullOrBlank()) {
                        // 跳过 Caveat 消息和命令消息
                        if (content.startsWith("Caveat:") || 
                            content.contains("<command-name>") || 
                            content.contains("<local-command-stdout>") ||
                            content.startsWith("[Request interrupted")) {
                            println("  - 跳过特殊消息类型")
                            continue
                        }
                        
                        // 清理内容
                        val cleanContent = content.trim()
                            .replace("\n", " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                        
                        println("  - 清理后的 content: '$cleanContent'")
                        
                        // 如果内容有意义（长度>=3），使用它（允许纯数字，因为有些会话可能就是讨论数字）
                        if (cleanContent.length >= 3) {
                            // 但如果是纯数字，需要特别处理
                            if (cleanContent.matches(Regex("\\d+"))) {
                                val numberValue = cleanContent.toIntOrNull()
                                // 如果是可能的行数范围（50-10000），跳过
                                if (numberValue != null && numberValue in 50..10000) {
                                    println("  - 跳过可能是行数的纯数字: $cleanContent (值在 50-10000 范围内)")
                                    continue
                                }
                                // 如果是小数字（<50），可能是真实的对话内容，但加上前缀
                                if (numberValue != null && numberValue < 50) {
                                    val result = "关于 $cleanContent 的讨论"
                                    println("  - ✅ 返回带前缀的数字会话名称: '$result'")
                                    println("=================================\n")
                                    return result
                                }
                            }
                            
                            val result = when {
                                cleanContent.length <= 50 -> cleanContent
                                else -> cleanContent.take(47) + "..."
                            }
                            println("  - ✅ 返回会话名称: '$result'")
                            println("=================================\n")
                            return result
                        }
                    }
                }
            }
            
            // 尝试从摘要获取
            println("  - 没有找到合适的用户消息，尝试从摘要获取")
            val summaryLine = lines.firstOrNull { it.contains("\"type\":\"summary\"") }
            if (summaryLine != null) {
                println("  - 找到摘要行")
                try {
                    val summaryJson = json.parseToJsonElement(summaryLine).jsonObject
                    val summary = summaryJson["summary"]?.jsonPrimitive?.content
                    if (!summary.isNullOrBlank()) {
                        val result = if (summary.length <= 50) summary else summary.take(47) + "..."
                        println("  - ✅ 从摘要返回: '$result'")
                        println("=================================\n")
                        return result
                    }
                } catch (e: Exception) {
                    println("  - 解析摘要失败: ${e.message}")
                }
            }
            
            // 使用时间戳
            println("  - 没有找到合适内容，使用时间戳生成默认名称")
            val result = if (timestamp.isNotBlank()) {
                try {
                    val date = timestamp.substringBefore("T")
                    val time = timestamp.substringAfter("T").substringBefore(".").substringBefore("Z")
                    "会话 $date $time"
                } catch (e: Exception) {
                    "会话 ${sessionId.take(8)}"
                }
            } else {
                "会话 ${sessionId.take(8)}"
            }
            println("  - ✅ 返回默认名称: '$result'")
            println("=================================\n")
            return result
        } catch (e: Exception) {
            val result = "会话 ${sessionId.take(8)}"
            println("  - ❌ 异常: ${e.message}")
            println("  - ✅ 返回异常处理名称: '$result'")
            println("=================================\n")
            return result
        }
    }
    
    /**
     * 刷新所有会话名称（修复纯数字标题问题）
     */
    fun refreshAllSessionNames() {
        scope.launch {
            println("\n=== 开始刷新所有会话名称 ===")
            var fixedCount = 0
            
            // 遍历所有项目的会话
            _sessions.value.forEach { (projectId, sessions) ->
                val project = _projects.value.find { it.id == projectId }
                println("刷新项目 ${project?.name ?: projectId} 的会话...")
                
                val updatedSessions = sessions.map { session ->
                    // 检查是否是纯数字名称
                    if (session.name.matches(Regex("\\d+"))) {
                        val numberValue = session.name.toIntOrNull()
                        if (numberValue != null && numberValue in 50..10000) {
                            println("  - 发现纯数字会话名称: '${session.name}' (ID: ${session.id})")
                            
                            // 生成存储路径（projectId 现在就是目录名）
                            val basePath = File(System.getProperty("user.home"), ".claude/projects")
                            val sessionFile = session.id?.let { id ->
                                File(basePath, "$projectId/${id}.jsonl")
                            }
                            
                            if (sessionFile != null && sessionFile.exists()) {
                                try {
                                    val lines = sessionFile.readLines()
                                    val newName = generateSessionName(lines, session.id ?: "unknown", session.createdAt)
                                    
                                    // 如果新名称仍然是纯数字，使用默认名称
                                    val finalName = if (newName.matches(Regex("\\d+")) && newName.toIntOrNull()?.let { it in 50..10000 } == true) {
                                        val dateTime = if (session.createdAt.isNotBlank()) {
                                            try {
                                                val date = session.createdAt.substringBefore("T")
                                                val time = session.createdAt.substringAfter("T").substringBefore(".").substringBefore("Z")
                                                "$date $time"
                                            } catch (e: Exception) {
                                                session.id?.take(8) ?: "unknown"
                                            }
                                        } else {
                                            session.id?.take(8) ?: "unknown"
                                        }
                                        "会话 $dateTime"
                                    } else {
                                        newName
                                    }
                                    
                                    println("    - 新名称: '$finalName'")
                                    fixedCount++
                                    
                                    // 返回更新后的会话（保持原有的 lastModified）
                                    return@map session.copy(name = finalName)
                                } catch (e: Exception) {
                                    println("    - 刷新失败: ${e.message}")
                                }
                            } else {
                                println("    - 会话文件不存在: ${sessionFile?.absolutePath ?: "无ID"}")
                            }
                        }
                    }
                    // 返回原会话（未修改）
                    session
                }
                
                // 更新会话列表
                if (updatedSessions != sessions) {
                    _sessions.value = _sessions.value + (projectId to updatedSessions)
                }
            }
            
            println("=== 会话名称刷新完成，共修复 $fixedCount 个会话 ===")
            println("注意：标签标题需要在 TabManager 中单独更新\n")
        }
    }
    
    /**
     * 手动加载当前工作目录的项目和会话
     */
    fun loadCurrentWorkingDirectoryProject() {
        println("[DEBUG] loadCurrentWorkingDirectoryProject 方法被调用")
        scope.launch {
            println("[DEBUG] 在协程中执行 loadCurrentWorkingDirectoryProject")
            // 获取当前工作目录
            val currentDir = System.getProperty("user.dir")
            println("加载当前工作目录项目: $currentDir")
            
            // 生成对应的项目ID
            val currentDirProjectId = com.claudecodeplus.sdk.ProjectPathUtils.projectPathToDirectoryName(currentDir)
            println("对应的项目ID: $currentDirProjectId")
            
            // 清除缓存以强制重新加载（使用项目ID作为键）
            _sessions.value = _sessions.value.filterKeys { it != currentDirProjectId }.toMap()
            
            // 检查是否已经有这个项目
            val existingProject = _projects.value.find { project ->
                val normalizedProjectPath = project.path.replace('\\', '/')
                val normalizedCurrentDir = currentDir.replace('\\', '/')
                normalizedProjectPath.equals(normalizedCurrentDir, ignoreCase = true)
            }
            
            if (existingProject != null) {
                println("项目已存在: ${existingProject.name}")
                setCurrentProject(existingProject)
            } else {
                println("项目不存在，添加到项目列表")
                // 从路径中提取项目名称
                val projectName = extractProjectName(currentDir)
                val newProject = Project(
                    id = currentDirProjectId,  // 使用编码后的目录名作为ID
                    path = currentDir,         // 使用原始路径
                    name = projectName
                )
                _projects.value = _projects.value + newProject
                setCurrentProject(newProject)
            }
        }
    }
    
    /**
     * 删除项目及其所有会话
     */
    suspend fun deleteProject(project: Project) {
        println("删除项目: ${project.name}")
        
        // 1. 删除项目的会话目录（project.id 现在就是目录名）
        val basePath = File(System.getProperty("user.home"), ".claude/projects")
        val projectDir = File(basePath, project.id)
        
        if (projectDir.exists() && projectDir.isDirectory) {
            println("删除会话目录: ${projectDir.absolutePath}")
            projectDir.deleteRecursively()
        }
        
        // 2. 从项目列表中移除
        _projects.value = _projects.value.filter { it.id != project.id }
        
        // 3. 从会话缓存中移除
        val newSessionsMap = _sessions.value.toMutableMap()
        newSessionsMap.remove(project.id)
        _sessions.value = newSessionsMap
        
        // 4. 如果删除的是当前项目，切换到其他项目
        if (_currentProject.value?.id == project.id) {
            _currentProject.value = _projects.value.firstOrNull()
            _currentSession.value = null
        }
        
        // 5. 从 .claude.json 中移除项目（如果需要持久化）
        updateClaudeConfig(project, remove = true)
    }
    
    /**
     * 删除单个会话
     */
    suspend fun deleteSession(session: ProjectSession, project: Project) {
        println("删除会话: ${session.name}")
        
        // 1. 删除会话文件（使用 project.id 作为目录名）
        val basePath = File(System.getProperty("user.home"), ".claude/projects")
        val sessionsDir = File(basePath, project.id)
        val sessionFileToDelete = session.id?.let { id ->
            File(sessionsDir, "${id}.jsonl")
        }
        
        if (sessionFileToDelete != null && sessionFileToDelete.exists()) {
            println("删除会话文件: ${sessionFileToDelete.absolutePath}")
            sessionFileToDelete.delete()
        }
        
        // 2. 从会话列表中移除
        val projectSessions = _sessions.value[project.id]?.toMutableList() ?: mutableListOf()
        projectSessions.removeAll { it.id == session.id }
        
        val newSessionsMap = _sessions.value.toMutableMap()
        newSessionsMap[project.id] = projectSessions
        _sessions.value = newSessionsMap
        
        // 3. 如果删除的是当前会话，清空当前会话
        if (_currentSession.value?.id == session.id) {
            _currentSession.value = null
        }
    }
    
    /**
     * 更新 .claude.json 配置文件
     */
    private suspend fun updateClaudeConfig(project: Project, remove: Boolean = false) {
        // 这里可以实现更新 .claude.json 的逻辑
        // 目前暂时不实现，因为可能影响其他 Claude 实例
        println("更新配置文件: ${project.path}, 移除: $remove")
    }
    
    
    /**
     * 创建新项目
     * @param name 项目名称
     * @param path 项目路径
     * @return 创建的项目对象
     */
    suspend fun createProject(name: String, path: String): Project {
        println("创建新项目: $name at $path")
        
        // 生成 Claude 目录名（项目ID）
        val projectId = com.claudecodeplus.sdk.ProjectPathUtils.projectPathToDirectoryName(path)
        println("生成的项目ID（目录名）: $projectId")
        
        // 创建项目对象
        val newProject = Project(
            id = projectId,  // 使用编码后的目录名作为ID
            path = path,     // 使用原始路径
            name = name
        )
        
        // 检查项目是否已存在
        val existingProject = _projects.value.find { project ->
            val normalizedProjectPath = project.path.replace('\\', '/')
            val normalizedNewPath = path.replace('\\', '/')
            normalizedProjectPath.equals(normalizedNewPath, ignoreCase = true)
        }
        
        if (existingProject != null) {
            println("项目已存在，切换到该项目: ${existingProject.name}")
            setCurrentProject(existingProject)
            return existingProject
        }
        
        // 添加到项目列表
        _projects.value = _projects.value + newProject
        
        // 不创建任何目录，所有文件操作由Claude CLI负责
        
        // 设置为当前项目
        setCurrentProject(newProject)
        
        // 初始化空的会话列表（使用项目ID作为键）
        val newSessionsMap = _sessions.value.toMutableMap()
        newSessionsMap[projectId] = emptyList()
        _sessions.value = newSessionsMap
        
        // 可选：更新 .claude.json（目前不实现）
        // updateClaudeConfig(newProject, remove = false)
        
        return newProject
    }
    
    /**
     * 手动选择项目（用于手动选择模式）
     * @param projectId 项目ID
     * @param loadSessions 是否加载该项目的历史会话，false时只显示新建会话选项
     */
    fun selectProject(projectId: String, loadSessions: Boolean = false) {
        scope.launch {
            val project = _projects.value.find { it.id == projectId }
            if (project == null) {
                println("项目不存在: $projectId")
                return@launch
            }
            
            println("手动选择项目: ${project.name} (loadSessions: $loadSessions)")
            _currentProject.value = project
            
            if (loadSessions) {
                // 从本地配置加载历史会话
                loadSessionsFromLocalConfig(projectId)
                
                // 等待会话加载完成后选择最新会话
                kotlinx.coroutines.delay(200)
                val projectSessions = _sessions.value[projectId] ?: emptyList()
                val latestSession = projectSessions.firstOrNull()
                
                if (latestSession != null) {
                    setCurrentSession(latestSession, loadHistory = true)
                }
            } else {
                // 不加载历史会话，清空当前会话列表，只提供新建会话的选项
                val newSessionsMap = _sessions.value.toMutableMap()
                newSessionsMap[projectId] = emptyList()
                _sessions.value = newSessionsMap
                
                _currentSession.value = null
                println("项目已选择，未加载历史会话，仅提供新建会话选项")
            }
        }
    }
    
    /**
     * 从本地配置加载项目的会话列表
     */
    private suspend fun loadSessionsFromLocalConfig(projectId: String) {
        try {
            println("从本地配置加载项目会话: $projectId")
            val localConfigManager = com.claudecodeplus.ui.models.LocalConfigManager()
            val localSessions = localConfigManager.getProjectSessions(projectId)
            
            println("从本地配置加载到 ${localSessions.size} 个会话")
            
            val loadedSessions = localSessions.map { localSession ->
                ProjectSession(
                    id = localSession.id,
                    projectId = projectId,
                    name = localSession.name,
                    createdAt = localSession.createdAt,
                    lastModified = localSession.lastAccessedAt?.let { 
                        try {
                            java.time.Instant.parse(it).toEpochMilli()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    } ?: System.currentTimeMillis(),
                    cwd = _projects.value.find { it.id == projectId }?.path ?: ""
                )
            }.sortedByDescending { it.lastModified }
            
            val newSessionsMap = _sessions.value.toMutableMap()
            newSessionsMap[projectId] = loadedSessions
            _sessions.value = newSessionsMap
            
            if (loadedSessions.isNotEmpty()) {
                println("成功加载 ${loadedSessions.size} 个会话:")
                loadedSessions.forEach { session ->
                    println("  - ${session.name} (${session.id})")
                }
            } else {
                println("该项目没有历史会话")
            }
        } catch (e: Exception) {
            println("从本地配置加载会话失败: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * 手动选择项目并允许浏览文件夹
     * @param projectPath 项目路径
     */
    fun selectProjectByPath(projectPath: String) {
        scope.launch {
            // 检查是否已存在该项目
            val existingProject = _projects.value.find { it.path == projectPath }
            if (existingProject != null) {
                selectProject(existingProject.id, loadSessions = false)
                return@launch
            }
            
            // 创建新项目并添加到本地配置
            val projectName = projectPath.substringAfterLast("/")
            println("创建新项目: $projectName (路径: $projectPath)")
            
            try {
                val localConfigManager = com.claudecodeplus.ui.models.LocalConfigManager()
                val localProject = localConfigManager.addProject(projectPath, projectName)
                
                val newProject = Project(
                    id = localProject.id,
                    name = localProject.name,
                    path = localProject.path
                )
                
                // 添加到项目列表
                val updatedProjects = _projects.value.toMutableList()
                updatedProjects.add(newProject)
                _projects.value = updatedProjects.sortedBy { it.name }
                
                // 选择新创建的项目
                _currentProject.value = newProject
                
                // 不加载历史会话
                val newSessionsMap = _sessions.value.toMutableMap()
                newSessionsMap[localProject.id] = emptyList()
                _sessions.value = newSessionsMap
                
                _currentSession.value = null
                println("成功创建并选择新项目: $projectName (ID: ${localProject.id})")
            } catch (e: Exception) {
                println("创建项目失败: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 创建新的占位会话
     * @param projectId 项目ID
     * @return 创建的会话对象
     */
    suspend fun createPlaceholderSession(projectId: String): ProjectSession {
        println("创建新的占位会话: projectId=$projectId")
        
        // 从项目ID获取项目路径作为cwd
        val project = _projects.value.find { it.id == projectId }
        val projectPath = project?.path ?: throw IllegalArgumentException("无法找到项目: $projectId")
        
        val timestamp = java.time.Instant.now().toString()
        
        // 创建会话对象，直接生成UUID作为会话ID
        val sessionId = java.util.UUID.randomUUID().toString()
        val newSession = ProjectSession(
            id = sessionId, // 新建会话使用UUID作为ID
            projectId = projectId,
            name = "新会话",
            createdAt = timestamp,
            lastModified = System.currentTimeMillis(),
            cwd = projectPath
        )
        
        try {
            // 将新会话记录到本地配置中
            val localConfigManager = com.claudecodeplus.ui.models.LocalConfigManager()
            localConfigManager.addSession(projectId, sessionId, "新会话")
            println("会话已记录到本地配置")
        } catch (e: Exception) {
            println("记录会话到本地配置失败: ${e.message}")
            e.printStackTrace()
        }
        
        // 添加到会话列表
        val projectSessions = _sessions.value[projectId]?.toMutableList() ?: mutableListOf()
        projectSessions.add(0, newSession) // 添加到列表开头
        
        val newSessionsMap = _sessions.value.toMutableMap()
        newSessionsMap[projectId] = projectSessions
        _sessions.value = newSessionsMap
        
        // 不创建任何文件，所有文件操作由Claude CLI负责
        
        println("创建占位会话成功: $sessionId")
        return newSession
    }
    
    /**
     * 更新会话名称（根据第一条消息）
     * @param sessionId 会话ID
     * @param projectId 项目ID
     * @param firstMessage 第一条消息内容
     */
    suspend fun updateSessionName(sessionId: String?, projectId: String, firstMessage: String) {
        println("更新会话名称: sessionId=$sessionId, firstMessage=${firstMessage.take(50)}...")
        
        val projectSessions = _sessions.value[projectId]?.toMutableList() ?: return
        val sessionIndex = projectSessions.indexOfFirst { it.id == sessionId }
        
        if (sessionIndex >= 0) {
            val session = projectSessions[sessionIndex]
            val newName = generateSessionNameFromContent(firstMessage)
            
            // 更新会话名称
            projectSessions[sessionIndex] = session.copy(name = newName)
            
            val newSessionsMap = _sessions.value.toMutableMap()
            newSessionsMap[projectId] = projectSessions
            _sessions.value = newSessionsMap
            
            println("会话名称已更新: '$newName'")
        }
    }
    
    /**
     * 从内容生成会话名称（简化版）
     */
    private fun generateSessionNameFromContent(content: String): String {
        val cleanContent = content.trim()
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        
        return when {
            cleanContent.isEmpty() -> "新会话"
            cleanContent.length <= 50 -> cleanContent
            else -> cleanContent.take(47) + "..."
        }
    }
    
    /**
     * 更新会话ID（当占位会话获得真实的Claude会话ID时）
     */
    suspend fun updateSessionId(oldSessionId: String?, newSessionId: String, projectId: String) {
        println("更新会话ID: oldSessionId=$oldSessionId, newSessionId=$newSessionId")
        
        val projectSessions = _sessions.value[projectId]?.toMutableList() ?: return
        val sessionIndex = projectSessions.indexOfFirst { it.id == oldSessionId }
        
        if (sessionIndex >= 0) {
            val session = projectSessions[sessionIndex]
            // 更新会话ID
            projectSessions[sessionIndex] = session.copy(id = newSessionId)
            
            val newSessionsMap = _sessions.value.toMutableMap()
            newSessionsMap[projectId] = projectSessions
            _sessions.value = newSessionsMap
            
            // 不进行任何文件操作，所有文件由Claude CLI管理
            
            println("会话ID已更新")
        }
    }
    
    /**
     * 从项目路径中提取更有意义的项目名称
     */
    private fun extractProjectName(projectPath: String): String {
        // 直接返回最后一段目录名，保持与 cwd 一致
        return projectPath.substringAfterLast(File.separator)
    }
    
    /**
     * 从会话文件的行中提取 cwd 字段
     */
    private fun extractCwdFromSessionFile(lines: List<String>): String? {
        // 遍历会话文件的行，查找第一个包含 cwd 的用户或助手消息
        for (line in lines) {
            try {
                val jsonElement = json.parseToJsonElement(line)
                if (jsonElement is kotlinx.serialization.json.JsonObject) {
                    val type = jsonElement["type"]?.jsonPrimitive?.content
                    if (type == "user" || type == "assistant") {
                        val cwd = jsonElement["cwd"]?.jsonPrimitive?.content
                        if (!cwd.isNullOrBlank()) {
                            return cwd
                        }
                    }
                }
            } catch (e: Exception) {
                // 忽略无法解析的行
                continue
            }
        }
        return null
    }
    
    // ========== 会话对象管理 ==========
    
    
    /**
     * 处理会话消息更新
     */
    private fun onSessionFileChanged(sessionId: String, projectPath: String) {
        println("[ProjectManager] 🔔 收到文件变更回调 - sessionId: $sessionId, projectPath: $projectPath")
        
        // 根据projectPath找到对应的Project
        val project = _projects.value.find { it.path == projectPath }
        if (project != null) {
            println("[ProjectManager] ✅ 找到对应的项目: ${project.name}")
            
            // 从Project的所有Session中查找匹配sessionId的SessionObject
            project.getAllSessions().find { sessionObject -> 
                sessionObject.sessionId == sessionId 
            }?.let { sessionObject ->
                println("[ProjectManager] ✅ 找到匹配的SessionObject，通知更新: $sessionId")
                scope.launch {
                    println("[ProjectManager] 开始调用 loadNewMessages (增量更新) for sessionId: $sessionId")
                    try {
                        sessionObject.loadNewMessages(forceFullReload = false) // 增量更新
                        println("[ProjectManager] ✅ loadNewMessages 完成 for sessionId: $sessionId")
                    } catch (e: Exception) {
                        println("[ProjectManager] ❌ loadNewMessages 失败 for sessionId: $sessionId, error: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } ?: run {
                println("[ProjectManager] ⚠️ 项目中未找到SessionObject with sessionId: $sessionId")
                
                // 如果是当前项目的会话，尝试重新加载会话列表
                val currentProject = _currentProject.value
                println("[ProjectManager] 当前项目: ${currentProject?.name} (path: ${currentProject?.path})")
                println("[ProjectManager] 文件变更的项目路径: $projectPath")
                
                if (currentProject != null && projectPath == currentProject.path) {
                    println("[ProjectManager] ✅ 这是当前项目的会话，重新加载会话列表")
                    scope.launch {
                        try {
                            loadSessionsForProject(currentProject.id, forceReload = true)
                            println("[ProjectManager] ✅ 会话列表重新加载完成")
                        } catch (e: Exception) {
                            println("[ProjectManager] ❌ 重新加载会话列表失败: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } else {
                    println("[ProjectManager] ❌ 文件变更不属于当前项目，忽略")
                }
            }
        } else {
            println("[ProjectManager] ❌ 未找到对应的项目: $projectPath")
        }
    }
    
}