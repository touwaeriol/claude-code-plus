package com.claudecodeplus.ui.services

import androidx.compose.runtime.mutableStateOf
import com.claudecodeplus.ui.models.ClaudeConfig
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

data class SessionLoadEvent(val session: ProjectSession)

class ProjectManager {
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
        loadProjectsFromDisk()
    }

    private fun loadProjectsFromDisk() {
        scope.launch {
            val projectsFile = File(System.getProperty("user.home"), ".claude.json")
            println("加载项目配置文件: ${projectsFile.absolutePath}")
            println("配置文件是否存在: ${projectsFile.exists()}")
            
            if (projectsFile.exists()) {
                try {
                    val configJson = projectsFile.readText()
                    println("配置文件内容长度: ${configJson.length}")
                    
                    // 尝试解析项目路径，即使完整配置解析失败
                    val projectPaths = extractProjectPaths(configJson)
                    if (projectPaths.isNotEmpty()) {
                        println("找到 ${projectPaths.size} 个项目路径")
                        val loadedProjects = projectPaths.map { path ->
                            println("加载项目: $path")
                            Project(id = path, path = path)
                        }
                        _projects.value = loadedProjects
                        
                        // 优先选择当前工作目录的项目
                        val currentDir = System.getProperty("user.dir")
                        println("当前工作目录: $currentDir")
                        
                        // 查找匹配的项目，考虑路径格式差异
                        val currentProject = loadedProjects.find { project ->
                            val normalizedProjectPath = project.path.replace('/', '\\')
                            val normalizedCurrentDir = currentDir.replace('/', '\\')
                            normalizedProjectPath.equals(normalizedCurrentDir, ignoreCase = true)
                        } ?: loadedProjects.firstOrNull()
                        
                        if (currentProject != null) {
                            println("找到匹配的当前项目: ${currentProject.id}")
                        } else {
                            println("未找到匹配的当前项目，使用第一个项目")
                        }
                        
                        _currentProject.value = currentProject
                        
                        // 自动加载当前项目的会话
                        currentProject?.let { 
                            println("自动加载当前项目的会话: ${it.id}")
                            loadSessionsForProject(it.id) 
                        }
                    } else {
                        println("无法从配置文件中提取项目路径")
                        loadDefaultProject()
                    }
                } catch (e: Exception) {
                    // 在这里添加更明确的日志，以便调试
                    println("Failed to load or parse .claude.json: ${e.message}")
                    e.printStackTrace()
                    
                    // 如果配置文件解析失败，至少加载当前项目
                    loadDefaultProject()
                }
            } else {
                println("配置文件不存在")
                // 如果配置文件不存在，加载当前项目
                loadDefaultProject()
            }
        }
    }

    private fun extractProjectPaths(jsonContent: String): List<String> {
        // 使用正则表达式从 JSON 中提取项目路径
        val projectPaths = mutableListOf<String>()
        try {
            // 尝试使用 Json 库只解析 projects 部分
            val jsonElement = json.parseToJsonElement(jsonContent)
            val jsonObject = jsonElement.jsonObject
            val projectsElement = jsonObject["projects"]
            if (projectsElement != null) {
                val projectsObject = projectsElement.jsonObject
                projectPaths.addAll(projectsObject.keys)
            }
        } catch (e: Exception) {
            println("使用 JSON 解析失败，尝试正则表达式提取: ${e.message}")
            // 如果 JSON 解析失败，使用正则表达式作为备用方案
            val regex = """"C:/[^"]+"""".toRegex()
            val matches = regex.findAll(jsonContent)
            matches.forEach { match ->
                val path = match.value.trim('"')
                if (path.contains("IdeaProjects") && !projectPaths.contains(path)) {
                    projectPaths.add(path)
                }
            }
        }
        return projectPaths
    }
    
    private fun loadDefaultProject() {
        // 获取当前工作目录作为默认项目
        val currentDir = System.getProperty("user.dir")
        println("加载默认项目: $currentDir")
        
        val project = Project(id = currentDir, path = currentDir)
        _projects.value = listOf(project)
        _currentProject.value = project
        
        // 加载项目的会话
        loadSessionsForProject(project.id)
    }
    
    private fun encodePathToDirectoryName(path: String): String {
        // 使用统一的路径编码方法，与OptimizedSessionManager保持一致
        return com.claudecodeplus.sdk.ProjectPathUtils.projectPathToDirectoryName(path)
    }

    fun findProjectForSession(sessionDirName: String): Project? {
        // 优先使用新格式进行匹配
        val projectByNewFormat = projects.value.find { project ->
            encodePathToDirectoryName(project.path) == sessionDirName
        }
        if (projectByNewFormat != null) {
            return projectByNewFormat
        }

        // 如果新格式未找到，则尝试匹配旧格式
        return projects.value.find { project ->
            // 旧格式 1: 移除了开头的 "-"
            val oldEncodedPath = project.path.replace(File.separator, "-").let {
                if (it.startsWith("-")) it.substring(1) else it
            }
            // 旧格式 2: 保留了开头的 "-"
            val oldEncodedPathWithLeadingDash = project.path.replace(File.separator, "-")

            oldEncodedPath == sessionDirName || oldEncodedPathWithLeadingDash == sessionDirName
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
                val encodedProjectId = encodePathToDirectoryName(projectId)
                println("编码后的项目ID: $encodedProjectId")
                
                // 尝试多种路径格式
                val basePath = File(System.getProperty("user.home"), ".claude/projects")
                val possibleDirs = listOf(
                    File(basePath, encodedProjectId),
                    File(basePath, projectId.replace(":", "").replace("/", "-").replace("\\", "-"))
                )
                
                var sessionsDir: File? = null
                for (dir in possibleDirs) {
                    println("尝试会话目录: ${dir.absolutePath}")
                    if (dir.exists() && dir.isDirectory) {
                        sessionsDir = dir
                        println("找到会话目录: ${dir.absolutePath}")
                        break
                    }
                }

                if (sessionsDir != null && sessionsDir.exists() && sessionsDir.isDirectory) {
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

                            // 解析第一行来获取基本信息
                            val firstLineJson = json.parseToJsonElement(lines.first()).jsonObject
                            val sessionId = firstLineJson["sessionId"]?.jsonPrimitive?.content
                            if (sessionId == null) {
                                println("无法获取sessionId: ${file.name}")
                                return@mapNotNull null
                            }
                            val timestamp = firstLineJson["timestamp"]?.jsonPrimitive?.content ?: ""
                            
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
                            
                            val session = ProjectSession(
                                id = sessionId,
                                projectId = projectId,
                                name = finalSessionName,
                                createdAt = timestamp
                            )
                            println("DEBUG: 创建后的 ProjectSession - ID: ${session.id}, Name: '${session.name}'")
                            
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
                    }.sortedByDescending { it.createdAt }

                    println("成功加载 ${loadedSessions.size} 个会话")
                    val newSessionsMap = _sessions.value.toMutableMap()
                    newSessionsMap[projectId] = loadedSessions
                    _sessions.value = newSessionsMap
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
        // 触发会话历史加载事件
        if (loadHistory) {
            scope.launch {
                _sessionLoadEvent.emit(SessionLoadEvent(session))
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
                            
                            // 生成存储路径
                            val encodedProjectId = encodePathToDirectoryName(projectId)
                            val basePath = File(System.getProperty("user.home"), ".claude/projects")
                            val sessionFile = session.id?.let { id ->
                                File(basePath, "$encodedProjectId/${id}.jsonl")
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
                                    
                                    // 返回更新后的会话
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
            
            // 清除缓存以强制重新加载
            _sessions.value = _sessions.value.filterKeys { it != currentDir }.toMap()
            
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
                val projectName = currentDir.substringAfterLast(File.separator)
                val newProject = Project(
                    id = currentDir, 
                    path = currentDir,
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
        
        // 1. 删除项目的会话目录
        val encodedProjectId = encodePathToDirectoryName(project.id)
        val basePath = File(System.getProperty("user.home"), ".claude/projects")
        val projectDir = File(basePath, encodedProjectId)
        
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
        
        // 1. 删除会话文件
        val encodedProjectId = encodePathToDirectoryName(project.path)
        val basePath = File(System.getProperty("user.home"), ".claude/projects")
        val sessionsDir = File(basePath, encodedProjectId)
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
        
        // 创建项目对象
        val newProject = Project(
            id = path,
            path = path,
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
        
        // 初始化空的会话列表
        val newSessionsMap = _sessions.value.toMutableMap()
        newSessionsMap[path] = emptyList()
        _sessions.value = newSessionsMap
        
        // 可选：更新 .claude.json（目前不实现）
        // updateClaudeConfig(newProject, remove = false)
        
        return newProject
    }
    
    /**
     * 创建新的占位会话
     * @param projectId 项目ID
     * @return 创建的会话对象
     */
    suspend fun createPlaceholderSession(projectId: String): ProjectSession {
        println("创建新的占位会话: projectId=$projectId")
        
        val timestamp = java.time.Instant.now().toString()
        
        // 创建会话对象，id为null表示占位会话
        val newSession = ProjectSession(
            id = null, // 占位会话没有真实ID
            projectId = projectId,
            name = "新会话",
            createdAt = timestamp
        )
        
        // 添加到会话列表
        val projectSessions = _sessions.value[projectId]?.toMutableList() ?: mutableListOf()
        projectSessions.add(0, newSession) // 添加到列表开头
        
        val newSessionsMap = _sessions.value.toMutableMap()
        newSessionsMap[projectId] = projectSessions
        _sessions.value = newSessionsMap
        
        // 不创建任何文件，所有文件操作由Claude CLI负责
        
        println("创建占位会话成功（无ID）")
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
}