package com.asakii.plugin.mcp.tools.terminal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.JBTerminalWidget
import mu.KotlinLogging
import org.jetbrains.plugins.terminal.ShellTerminalWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Terminal 会话信息
 */
data class TerminalSession(
    val id: String,
    val name: String,
    val shellType: String,
    val widget: ShellTerminalWidget,
    val createdAt: Long = System.currentTimeMillis(),
    var lastCommandAt: Long = System.currentTimeMillis(),
    var isBackground: Boolean = false
) {
    /**
     * 检查是否有正在运行的命令
     */
    fun hasRunningCommands(): Boolean {
        return try {
            widget.hasRunningCommands()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check running commands for session $id" }
            false
        }
    }

    /**
     * 获取终端输出内容
     */
    fun getOutput(maxLines: Int = 1000): String {
        return try {
            val buffer = widget.terminalTextBuffer
            val screenLines = buffer.screenLinesCount
            val historyLines = buffer.historyLinesCount
            val totalLines = screenLines + historyLines

            val startLine = if (totalLines > maxLines) {
                totalLines - maxLines
            } else {
                0
            }

            val sb = StringBuilder()
            for (i in startLine until totalLines) {
                val line = if (i < historyLines) {
                    buffer.getLine(i - historyLines) // 历史行用负索引
                } else {
                    buffer.getLine(i - historyLines) // 屏幕行
                }
                sb.append(line.text.trimEnd())
                if (i < totalLines - 1) {
                    sb.append("\n")
                }
            }
            sb.toString()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get output for session $id" }
            ""
        }
    }

    /**
     * 搜索输出内容
     */
    fun searchOutput(pattern: String, contextLines: Int = 2): List<SearchMatch> {
        val output = getOutput()
        val lines = output.split("\n")
        val matches = mutableListOf<SearchMatch>()
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            Regex(Regex.escape(pattern))
        }

        lines.forEachIndexed { index, line ->
            if (regex.containsMatchIn(line)) {
                val startLine = maxOf(0, index - contextLines)
                val endLine = minOf(lines.size - 1, index + contextLines)
                val context = lines.subList(startLine, endLine + 1).joinToString("\n")
                matches.add(SearchMatch(
                    lineNumber = index + 1,
                    line = line,
                    context = context
                ))
            }
        }
        return matches
    }
}

/**
 * 搜索匹配结果
 */
data class SearchMatch(
    val lineNumber: Int,
    val line: String,
    val context: String
)

/**
 * Shell 类型
 */
enum class ShellType(val displayName: String, val command: String?) {
    // Windows
    GIT_BASH("Git Bash", "git-bash"),
    POWERSHELL("PowerShell", "powershell"),
    CMD("Command Prompt", "cmd"),
    WSL("WSL", "wsl"),

    // Unix
    BASH("Bash", "bash"),
    ZSH("Zsh", "zsh"),
    FISH("Fish", "fish"),
    SH("Shell", "sh"),

    // 自动检测
    AUTO("Auto", null);

    companion object {
        fun fromString(value: String?): ShellType {
            if (value.isNullOrBlank()) return AUTO
            return entries.find {
                it.name.equals(value, ignoreCase = true) ||
                it.command.equals(value, ignoreCase = true) ||
                it.displayName.equals(value, ignoreCase = true)
            } ?: AUTO
        }

        fun getAvailableTypes(): List<ShellType> {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            return if (isWindows) {
                listOf(GIT_BASH, POWERSHELL, CMD, WSL, AUTO)
            } else {
                listOf(BASH, ZSH, FISH, SH, AUTO)
            }
        }

        fun getDefaultType(): ShellType {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            return if (isWindows) GIT_BASH else BASH
        }
    }
}

/**
 * Terminal 会话管理器
 *
 * 管理 IDEA 内置终端的会话，支持命令执行、输出读取、会话管理等功能。
 */
class TerminalSessionManager(private val project: Project) {

    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    private val sessionCounter = AtomicInteger(0)

    /**
     * 创建新终端会话
     */
    fun createSession(
        name: String? = null,
        shellType: ShellType = ShellType.AUTO
    ): TerminalSession? {
        return try {
            val sessionId = "terminal-${sessionCounter.incrementAndGet()}"
            val sessionName = name ?: "Claude Terminal ${sessionCounter.get()}"

            var widget: ShellTerminalWidget? = null

            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val manager = TerminalToolWindowManager.getInstance(project)
                    val basePath = project.basePath ?: System.getProperty("user.home")

                    // 使用 createLocalShellWidget 创建新终端
                    val jbWidget = manager.createLocalShellWidget(basePath, sessionName)

                    if (jbWidget is ShellTerminalWidget) {
                        widget = jbWidget
                    } else {
                        logger.warn { "Created widget is not ShellTerminalWidget: ${jbWidget?.javaClass?.name}" }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Failed to create terminal widget" }
                }
            }

            widget?.let { w ->
                val actualShellType = if (shellType == ShellType.AUTO) {
                    detectShellType(w)
                } else {
                    shellType
                }

                val session = TerminalSession(
                    id = sessionId,
                    name = sessionName,
                    shellType = actualShellType.name,
                    widget = w
                )
                sessions[sessionId] = session
                logger.info { "Created terminal session: $sessionId ($sessionName)" }
                session
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create terminal session" }
            null
        }
    }

    /**
     * 获取或创建会话
     */
    fun getOrCreateSession(sessionId: String?): TerminalSession? {
        return if (sessionId != null && sessions.containsKey(sessionId)) {
            sessions[sessionId]
        } else {
            createSession()
        }
    }

    /**
     * 获取会话
     */
    fun getSession(sessionId: String): TerminalSession? {
        return sessions[sessionId]
    }

    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<TerminalSession> {
        return sessions.values.toList()
    }

    /**
     * 执行命令
     */
    fun executeCommand(
        sessionId: String?,
        command: String,
        background: Boolean = false
    ): ExecuteResult {
        val session = getOrCreateSession(sessionId) ?: return ExecuteResult(
            success = false,
            sessionId = "",
            error = "Failed to create terminal session"
        )

        return try {
            session.isBackground = background
            session.lastCommandAt = System.currentTimeMillis()

            ApplicationManager.getApplication().invokeAndWait {
                session.widget.executeCommand(command)
            }

            ExecuteResult(
                success = true,
                sessionId = session.id,
                sessionName = session.name,
                background = background
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute command in session ${session.id}" }
            ExecuteResult(
                success = false,
                sessionId = session.id,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 读取会话输出
     */
    fun readOutput(
        sessionId: String,
        maxLines: Int = 1000,
        search: String? = null,
        contextLines: Int = 2
    ): ReadResult {
        val session = getSession(sessionId) ?: return ReadResult(
            success = false,
            sessionId = sessionId,
            error = "Session not found: $sessionId"
        )

        return try {
            val isRunning = session.hasRunningCommands()

            if (search != null) {
                val matches = session.searchOutput(search, contextLines)
                ReadResult(
                    success = true,
                    sessionId = sessionId,
                    isRunning = isRunning,
                    searchMatches = matches
                )
            } else {
                val output = session.getOutput(maxLines)
                ReadResult(
                    success = true,
                    sessionId = sessionId,
                    output = output,
                    isRunning = isRunning,
                    lineCount = output.split("\n").size
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to read output from session $sessionId" }
            ReadResult(
                success = false,
                sessionId = sessionId,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 终止会话
     */
    fun killSession(sessionId: String): Boolean {
        val session = sessions.remove(sessionId) ?: return false

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    // 关闭终端 widget
                    val toolWindow = ToolWindowManager.getInstance(project)
                        .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

                    toolWindow?.contentManager?.let { contentManager ->
                        contentManager.contents.find { content ->
                            content.component == session.widget ||
                            (content.component as? JBTerminalWidget) == session.widget
                        }?.let { content ->
                            contentManager.removeContent(content, true)
                        }
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to remove terminal content" }
                }
            }
            logger.info { "Killed terminal session: $sessionId" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to kill session $sessionId" }
            false
        }
    }

    /**
     * 重命名会话
     */
    fun renameSession(sessionId: String, newName: String): Boolean {
        val session = sessions[sessionId] ?: return false

        return try {
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val toolWindow = ToolWindowManager.getInstance(project)
                        .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)

                    toolWindow?.contentManager?.let { contentManager ->
                        contentManager.contents.find { content ->
                            content.displayName == session.name
                        }?.let { content ->
                            content.displayName = newName
                        }
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to rename terminal tab" }
                }
            }

            // 更新内部会话记录
            sessions[sessionId] = session.copy(name = newName)
            logger.info { "Renamed terminal session $sessionId to: $newName" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to rename session $sessionId" }
            false
        }
    }

    /**
     * 获取可用的 Shell 类型
     */
    fun getAvailableShellTypes(): List<ShellTypeInfo> {
        return ShellType.getAvailableTypes().map { shellType ->
            ShellTypeInfo(
                name = shellType.name,
                displayName = shellType.displayName,
                command = shellType.command,
                isDefault = shellType == ShellType.getDefaultType()
            )
        }
    }

    /**
     * 检测 Shell 类型
     */
    private fun detectShellType(widget: ShellTerminalWidget): ShellType {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        return if (isWindows) ShellType.GIT_BASH else ShellType.BASH
    }

    /**
     * 清理所有会话
     */
    fun dispose() {
        sessions.keys.toList().forEach { killSession(it) }
        sessions.clear()
    }
}

/**
 * 命令执行结果
 */
data class ExecuteResult(
    val success: Boolean,
    val sessionId: String,
    val sessionName: String? = null,
    val background: Boolean = false,
    val error: String? = null
)

/**
 * 输出读取结果
 */
data class ReadResult(
    val success: Boolean,
    val sessionId: String,
    val output: String? = null,
    val isRunning: Boolean = false,
    val lineCount: Int = 0,
    val searchMatches: List<SearchMatch>? = null,
    val error: String? = null
)

/**
 * Shell 类型信息
 */
data class ShellTypeInfo(
    val name: String,
    val displayName: String,
    val command: String?,
    val isDefault: Boolean
)
