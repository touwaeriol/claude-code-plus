package com.claudecodeplus.ui.services

import androidx.compose.runtime.mutableStateOf
import com.claudecodeplus.ui.models.ClaudeConfig
import com.claudecodeplus.ui.models.Project
import com.claudecodeplus.ui.models.ProjectSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class ProjectManager {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects = _projects.asStateFlow()

    private val _sessions = MutableStateFlow<Map<String, List<ProjectSession>>>(emptyMap())
    val sessions = _sessions.asStateFlow()

    private val _currentProject = mutableStateOf<Project?>(null)
    val currentProject = _currentProject

    private val _currentSession = mutableStateOf<ProjectSession?>(null)
    val currentSession = _currentSession

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadProjectsFromDisk()
    }

    private fun loadProjectsFromDisk() {
        scope.launch {
            val projectsFile = File(System.getProperty("user.home"), ".claude.json")
            if (projectsFile.exists()) {
                try {
                    val configJson = projectsFile.readText()
                    val config = json.decodeFromString<ClaudeConfig>(configJson)
                    val loadedProjects = config.projects.map { (path, _) -> 
                        Project(id = path, path = path)
                    }
                    _projects.value = loadedProjects
                    _currentProject.value = loadedProjects.firstOrNull()
                    // 自动加载第一个项目的会话
                    loadedProjects.firstOrNull()?.let { loadSessionsForProject(it.id) }
                } catch (e: Exception) {
                    // 在这里添加更明确的日志，以便调试
                    println("Failed to load or parse .claude.json: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun encodePathToDirectoryName(path: String): String {
        // 使用统一的路径编码方法，与OptimizedSessionManager保持一致
        return com.claudecodeplus.sdk.ProjectPathUtils.projectPathToDirectoryName(path)
    }

    fun loadSessionsForProject(projectId: String) {
        if (_sessions.value.containsKey(projectId)) return

        scope.launch {
            try {
                val encodedProjectId = encodePathToDirectoryName(projectId)
                val sessionsDir = File(File(System.getProperty("user.home"), ".claude/projects"), encodedProjectId)

                if (sessionsDir.exists() && sessionsDir.isDirectory) {
                    val sessionFiles = sessionsDir.listFiles { _, name -> name.endsWith(".jsonl") } ?: emptyArray()
                    val loadedSessions = sessionFiles.mapNotNull { file ->
                        try {
                            val lines = file.readLines()
                            if (lines.isEmpty()) return@mapNotNull null

                            // 解析第一行来获取基本信息
                            val firstLineJson = json.parseToJsonElement(lines.first()).jsonObject
                            val sessionId = firstLineJson["sessionId"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val timestamp = firstLineJson["timestamp"]?.jsonPrimitive?.content ?: ""
                            
                            // 从第一条用户消息中提取会话名称
                            val userMessageLine = lines.firstOrNull { it.contains("\"type\":\"user\"") }
                            val sessionName = if (userMessageLine != null) {
                                val messageJson = json.parseToJsonElement(userMessageLine).jsonObject["message"]?.jsonObject
                                messageJson?.get("content")?.jsonPrimitive?.content ?: "Untitled Session"
                            } else {
                                "Untitled Session"
                            }

                            ProjectSession(
                                id = sessionId,
                                projectId = projectId,
                                name = sessionName.take(50), // 截断以避免过长
                                createdAt = timestamp
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }.sortedByDescending { it.createdAt }

                    val newSessionsMap = _sessions.value.toMutableMap()
                    newSessionsMap[projectId] = loadedSessions
                    _sessions.value = newSessionsMap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setCurrentProject(project: Project) {
        _currentProject.value = project
        _currentSession.value = null
        loadSessionsForProject(project.id)
    }

    fun setCurrentSession(session: ProjectSession) {
        _currentSession.value = session
    }
}