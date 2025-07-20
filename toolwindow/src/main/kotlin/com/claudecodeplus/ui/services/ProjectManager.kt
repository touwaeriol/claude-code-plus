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

    fun loadSessionsForProject(projectId: String) {
        if (_sessions.value.containsKey(projectId)) {
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
                            ProjectSession(
                                id = sessionId,
                                projectId = projectId,
                                name = sessionName.take(50), // 截断以避免过长
                                createdAt = timestamp
                            )
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
        try {
            // 从第一条用户消息中提取内容
            val userMessageLine = lines.firstOrNull { it.contains("\"type\":\"user\"") }
            if (userMessageLine != null) {
                val messageJson = json.parseToJsonElement(userMessageLine).jsonObject["message"]?.jsonObject
                val content = messageJson?.get("content")?.jsonPrimitive?.content
                if (!content.isNullOrBlank()) {
                    // 生成简短的会话名称
                    val cleanContent = content.trim()
                        .replace("\n", " ")
                        .replace(Regex("\\s+"), " ")
                    
                    return when {
                        // 如果内容太短，直接使用
                        cleanContent.length <= 30 -> cleanContent
                        // 如果包含问号，截取到第一个问号
                        cleanContent.contains("?") -> {
                            val questionPart = cleanContent.substringBefore("?") + "?"
                            if (questionPart.length <= 50) questionPart else questionPart.take(47) + "..."
                        }
                        // 如果包含句号，截取到第一个句号
                        cleanContent.contains("。") -> {
                            val sentencePart = cleanContent.substringBefore("。") + "。"
                            if (sentencePart.length <= 50) sentencePart else sentencePart.take(47) + "..."
                        }
                        // 否则截取前30个字符
                        else -> cleanContent.take(30) + "..."
                    }
                }
            }
            
            // 从 assistant 消息中查找摘要
            val summaryLine = lines.firstOrNull { it.contains("\"type\":\"summary\"") }
            if (summaryLine != null) {
                try {
                    val summaryJson = json.parseToJsonElement(summaryLine).jsonObject
                    val summary = summaryJson["summary"]?.jsonPrimitive?.content
                    if (!summary.isNullOrBlank() && summary.length <= 50) {
                        return summary
                    }
                } catch (e: Exception) {
                    // 忽略解析错误
                }
            }
            
            // 如果都失败了，使用时间戳生成名称
            return if (timestamp.isNotBlank()) {
                try {
                    val date = timestamp.substringBefore("T")
                    val time = timestamp.substringAfter("T").substringBefore(".")
                    "会话 $date $time"
                } catch (e: Exception) {
                    "会话 ${sessionId.take(8)}"
                }
            } else {
                "会话 ${sessionId.take(8)}"
            }
        } catch (e: Exception) {
            println("生成会话名称失败: ${e.message}")
            return "会话 ${sessionId.take(8)}"
        }
    }
    
    /**
     * 手动加载当前工作目录的项目和会话
     */
    fun loadCurrentWorkingDirectoryProject() {
        scope.launch {
            // 硬编码 claude-code-plus 项目路径以确保正确加载
            val claudeCodePlusPath = "C:/Users/16790/IdeaProjects/claude-code-plus"
            println("手动加载 claude-code-plus 项目: $claudeCodePlusPath")
            
            // 清除缓存以强制重新加载
            _sessions.value = _sessions.value.filterKeys { it != claudeCodePlusPath }.toMap()
            
            // 检查是否已经有这个项目
            val existingProject = _projects.value.find { project ->
                val normalizedProjectPath = project.path.replace('\\', '/')
                normalizedProjectPath.equals(claudeCodePlusPath, ignoreCase = true)
            }
            
            if (existingProject != null) {
                println("claude-code-plus 项目已存在，直接加载会话")
                setCurrentProject(existingProject)
            } else {
                println("claude-code-plus 项目不存在，添加到项目列表")
                val newProject = Project(
                    id = claudeCodePlusPath, 
                    path = claudeCodePlusPath,
                    name = "claude-code-plus"
                )
                _projects.value = _projects.value + newProject
                setCurrentProject(newProject)
            }
        }
    }
}