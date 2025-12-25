package com.asakii.plugin.mcp.tools.terminal

import com.asakii.settings.AgentSettingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.asakii.plugin.compat.CommandWaitResult
import com.asakii.plugin.compat.TerminalCompat
import com.asakii.plugin.compat.TerminalWidgetWrapper
import com.asakii.plugin.compat.createShellWidget
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = Logger.getInstance("com.asakii.plugin.mcp.tools.terminal.TerminalSessionManager")

/**
 * Terminal 会话信息
 */
data class TerminalSession(
    val id: String,
    val name: String,
    val shellType: String,
    val widgetWrapper: TerminalWidgetWrapper,
    val createdAt: Long = System.currentTimeMillis(),
    var lastCommandAt: Long = System.currentTimeMillis(),
    var isBackground: Boolean = false
) {
    /**
     * 检查是否有正在运行的命令
     *
     * @return true 表示有命令正在运行，false 表示没有，null 表示 API 不可用
     */
    fun hasRunningCommands(): Boolean? {
        return try {
            widgetWrapper.hasRunningCommands()
        } catch (e: Exception) {
            logger.warn("Failed to check running commands for session $id: ${e.message}")
            null
        }
    }

    /**
     * 等待命令执行完成
     *
     * 使用兼容层的实现（依赖 Shell Integration）：
     * - 2024.x ~ 2025.2: hasRunningCommands() API
     * - 2025.3+: isCommandRunning() API
     *
     * 如果 API 不可用，返回 ApiUnavailable
     *
     * @param timeoutMs 超时时间
     * @param initialDelayMs 初始等待时间
     * @param pollIntervalMs 轮询间隔
     * @return 等待结果
     */
    fun waitForCommandCompletion(
        timeoutMs: Long = 300_000,
        initialDelayMs: Long = 300,
        pollIntervalMs: Long = 100
    ): CommandWaitResult {
        return widgetWrapper.waitForCommandCompletion(
            timeoutMs = timeoutMs,
            initialDelayMs = initialDelayMs,
            pollIntervalMs = pollIntervalMs
        )
    }

    /**
     * 获取终端输出内容（使用 wrapper 的统一 API）
     */
    fun getOutput(maxLines: Int = 1000): String {
        return try {
            widgetWrapper.getOutput(maxLines)
        } catch (e: Exception) {
            logger.error("Failed to get output for session $id", e)
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
 * Shell 解析器
 *
 * 使用 IDEA 检测到的 shell 列表，通过名称查找对应路径。
 * 不再使用硬编码枚举。
 */
object ShellResolver {
    private val logger = Logger.getInstance("com.asakii.plugin.mcp.tools.terminal.ShellResolver")

    /**
     * 根据 shell 名称获取可执行路径
     *
     * @param shellName shell 名称（如 "git-bash", "powershell", "bash"）
     * @return shell 路径，找不到时返回 null
     */
    fun getShellPath(shellName: String): String? {
        val detectedShells = TerminalCompat.detectInstalledShells()

        // 尝试精确匹配
        val matched = detectedShells.find { shell ->
            normalizeShellName(shell.name).equals(shellName, ignoreCase = true) ||
            shell.name.equals(shellName, ignoreCase = true)
        }

        if (matched != null) {
            logger.info("Found shell '$shellName' at path: ${matched.path}")
            return matched.path
        }

        logger.warn("Shell '$shellName' not found in detected shells: ${detectedShells.map { it.name }}")
        return null
    }

    /**
     * 获取 shell 命令列表（用于 IDEA Terminal API）
     */
    fun getShellCommand(shellName: String): List<String>? {
        val path = getShellPath(shellName) ?: return null
        return listOf(path)
    }

    /**
     * 标准化 shell 名称
     */
    private fun normalizeShellName(name: String): String {
        val lowerName = name.lowercase()
        return when {
            lowerName.contains("git bash") -> "git-bash"
            lowerName.contains("powershell") -> "powershell"
            lowerName.contains("command prompt") || lowerName == "cmd" -> "cmd"
            lowerName.contains("wsl") || lowerName.contains("ubuntu") || lowerName.contains("debian") -> "wsl"
            lowerName.contains("zsh") -> "zsh"
            lowerName.contains("fish") -> "fish"
            lowerName.contains("bash") -> "bash"
            else -> lowerName.replace(" ", "-")
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
     *
     * @param name 会话名称
     * @param shellName shell 名称（如 "git-bash", "powershell"），为 null 时使用配置的默认终端
     */
    fun createSession(
        name: String? = null,
        shellName: String? = null
    ): TerminalSession? {
        return try {
            val sessionId = "terminal-${sessionCounter.incrementAndGet()}"
            val sessionName = name ?: "Claude Terminal ${sessionCounter.get()}"

            // 确定实际使用的 shell 名称：传入的 > 配置的默认
            val actualShellName = shellName ?: getDefaultShellName()

            // 获取 shell 命令（用于 IDEA Terminal API）
            val shellCommand = ShellResolver.getShellCommand(actualShellName)
            logger.info("=== [TerminalSessionManager] createSession ===")
            logger.info("  requested shellName: $shellName")
            logger.info("  actualShellName: $actualShellName")
            logger.info("  shellCommand: $shellCommand")

            var wrapper: TerminalWidgetWrapper? = null

            ApplicationManager.getApplication().invokeAndWait {
                try {
                    val basePath = project.basePath ?: System.getProperty("user.home")

                    // 使用兼容层创建终端（传递 shellCommand 以指定 shell 类型）
                    wrapper = createShellWidget(project, basePath, sessionName, shellCommand)

                    if (wrapper == null) {
                        logger.warn("Failed to create TerminalWidgetWrapper")
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to create terminal widget", e)
                }
            }

            wrapper?.let { w ->
                val session = TerminalSession(
                    id = sessionId,
                    name = sessionName,
                    shellType = actualShellName,
                    widgetWrapper = w
                )
                sessions[sessionId] = session
                logger.info("Created terminal session: $sessionId ($sessionName), shell=$actualShellName, widget type: ${w.widgetClassName}")
                session
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to create terminal session", e)
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
     * 异步执行命令（立即返回，不等待完成）
     *
     * @param sessionId 会话 ID
     * @param command 要执行的命令
     * @return 执行结果（仅表示命令是否成功发送）
     */
    fun executeCommandAsync(sessionId: String, command: String): ExecuteResult {
        val session = getSession(sessionId) ?: return ExecuteResult(
            success = false,
            sessionId = sessionId,
            error = "Session not found: $sessionId"
        )

        return try {
            session.lastCommandAt = System.currentTimeMillis()

            ApplicationManager.getApplication().invokeAndWait {
                session.widgetWrapper.executeCommand(command)
            }

            ExecuteResult(
                 success = true,
                sessionId = session.id,
                sessionName = session.name,
                background = true  // 始终视为后台执行
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to execute command in session ${session.id}", e)
            ExecuteResult(
                success = false,
                sessionId = session.id,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 执行命令（兼容旧 API，保留但不推荐使用）
     *
     * @param sessionId 会话 ID，为空时创建新会话
     * @param command 要执行的命令
     * @param background 是否后台执行。false 时等待命令完成并返回输出
     * @param timeoutMs 前台执行时的超时时间（毫秒），默认 5 分钟
     * @deprecated 使用 executeCommandAsync 代替
     */
    @Deprecated("Use executeCommandAsync instead", ReplaceWith("executeCommandAsync(sessionId, command)"))
    fun executeCommand(
        sessionId: String?,
        command: String,
        background: Boolean = false,
        timeoutMs: Long = 300_000
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
                session.widgetWrapper.executeCommand(command)
            }

            if (background) {
                // 后台执行：立即返回
                ExecuteResult(
                    success = true,
                    sessionId = session.id,
                    sessionName = session.name,
                    background = true
                )
            } else {
                // 前台执行：等待命令完成
                val settings = AgentSettingsService.getInstance()
                val maxOutputLines = settings.terminalMaxOutputLines
                val maxOutputChars = settings.terminalMaxOutputChars

                // 等待命令完成（依赖 Shell Integration）
                val waitResult = session.waitForCommandCompletion(
                    timeoutMs = timeoutMs,
                    initialDelayMs = 300,
                    pollIntervalMs = 100
                )

                when (waitResult) {
                    is CommandWaitResult.ApiUnavailable -> {
                        // Shell Integration 不可用，无法检测命令状态
                        // 返回当前输出，并告知用户
                        val fullOutput = session.getOutput()
                        return ExecuteResult(
                            success = false,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            output = fullOutput,
                            error = "Cannot detect command completion (Shell Integration unavailable). Use background=true for long-running commands, or use TerminalRead to check output."
                        )
                    }
                    is CommandWaitResult.Timeout -> {
                        return ExecuteResult(
                            success = false,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            error = "Command timed out after ${timeoutMs / 1000} seconds. Use TerminalRead to check output."
                        )
                    }
                    is CommandWaitResult.Interrupted -> {
                        return ExecuteResult(
                            success = false,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            error = "Command wait was interrupted. Use TerminalRead to check output."
                        )
                    }
                    is CommandWaitResult.Completed -> {
                        // 命令完成，读取输出（可能截断）
                        val fullOutput = session.getOutput()
                        val lines = fullOutput.split("\n")
                        val totalLines = lines.size
                        val totalChars = fullOutput.length

                        val (output, truncated) = when {
                            totalLines > maxOutputLines -> {
                                // 行数超限：取最后 maxOutputLines 行
                                lines.takeLast(maxOutputLines).joinToString("\n") to true
                            }
                            totalChars > maxOutputChars -> {
                                // 字符数超限：取最后 maxOutputChars 字符
                                fullOutput.takeLast(maxOutputChars) to true
                            }
                            else -> fullOutput to false
                        }

                        ExecuteResult(
                            success = true,
                            sessionId = session.id,
                            sessionName = session.name,
                            background = false,
                            output = output,
                            truncated = truncated,
                            totalLines = totalLines,
                            totalChars = totalChars
                        )
                    }
                }
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to execute command in session ${session.id}", e)
            ExecuteResult(
                success = false,
                sessionId = session.id,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 读取会话输出
     *
     * @param sessionId 会话 ID
     * @param maxLines 最大行数
     * @param search 搜索模式（正则表达式）
     * @param contextLines 搜索结果上下文行数
     * @param waitForIdle 是否等待命令执行完成
     * @param timeoutMs 等待超时时间（毫秒）
     */
    fun readOutput(
        sessionId: String,
        maxLines: Int = 1000,
        search: String? = null,
        contextLines: Int = 2,
        waitForIdle: Boolean = false,
        timeoutMs: Long = 30_000
    ): ReadResult {
        val session = getSession(sessionId) ?: return ReadResult(
            success = false,
            sessionId = sessionId,
            error = "Session not found: $sessionId"
        )

        return try {
            // 如果需要等待命令完成
            if (waitForIdle) {
                val waitResult = session.waitForCommandCompletion(
                    timeoutMs = timeoutMs,
                    initialDelayMs = 100,
                    pollIntervalMs = 100
                )

                when (waitResult) {
                    is CommandWaitResult.Timeout -> {
                        // 超时但仍然返回当前输出
                        val output = session.getOutput(maxLines)
                        return ReadResult(
                            success = true,
                            sessionId = sessionId,
                            output = output,
                            isRunning = true,
                            lineCount = output.split("\n").size,
                            waitTimedOut = true,
                            waitMessage = "Timed out waiting for command to complete after ${timeoutMs / 1000} seconds. Returning current output."
                        )
                    }
                    is CommandWaitResult.ApiUnavailable -> {
                        // API 不可用，返回当前输出并提示
                        val output = session.getOutput(maxLines)
                        return ReadResult(
                            success = true,
                            sessionId = sessionId,
                            output = output,
                            isRunning = null,
                            lineCount = output.split("\n").size,
                            waitMessage = "Cannot detect command completion (Shell Integration unavailable). Returning current output."
                        )
                    }
                    is CommandWaitResult.Interrupted -> {
                        val output = session.getOutput(maxLines)
                        return ReadResult(
                            success = true,
                            sessionId = sessionId,
                            output = output,
                            isRunning = null,
                            lineCount = output.split("\n").size,
                            waitMessage = "Wait was interrupted. Returning current output."
                        )
                    }
                    is CommandWaitResult.Completed -> {
                        // 命令完成，继续读取
                        logger.info("Command completed, reading output...")
                    }
                }
            }

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
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to read output from session $sessionId", e)
            ReadResult(
                success = false,
                sessionId = sessionId,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 中断当前正在执行的命令
     *
     * @param sessionId 会话 ID
     * @param signal 信号类型: SIGINT (Ctrl+C), SIGQUIT (Ctrl+\), SIGTSTP (Ctrl+Z)
     */
    fun interruptCommand(sessionId: String, signal: String = "SIGINT"): InterruptResult {
        val session = getSession(sessionId) ?: return InterruptResult(
            success = false,
            sessionId = sessionId,
            signal = signal,
            error = "Session not found: $sessionId"
        )

        return try {
            val wasRunning = session.hasRunningCommands()

            ApplicationManager.getApplication().invokeAndWait {
                session.widgetWrapper.sendInterrupt(signal)
            }

            // 等待命令停止
            Thread.sleep(100)
            val isStillRunning = session.hasRunningCommands()

            val signalDesc = when (signal.uppercase()) {
                "SIGINT" -> "SIGINT (Ctrl+C)"
                "SIGQUIT" -> "SIGQUIT (Ctrl+\\)"
                "SIGTSTP" -> "SIGTSTP (Ctrl+Z)"
                else -> signal
            }

            InterruptResult(
                success = true,
                sessionId = sessionId,
                signal = signal,
                wasRunning = wasRunning,
                isStillRunning = isStillRunning,
                message = when {
                    wasRunning == null || isStillRunning == null -> "$signalDesc sent (command status unknown)"
                    wasRunning == false -> "No command was running"
                    isStillRunning == false -> "Command stopped by $signalDesc"
                    else -> "$signalDesc sent, command may still be stopping"
                }
            )
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to send $signal to session $sessionId", e)
            InterruptResult(
                success = false,
                sessionId = sessionId,
                signal = signal,
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
                            content.displayName == session.name
                        }?.let { content ->
                            contentManager.removeContent(content, true)
                        }
                    }
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Failed to remove terminal content: ${e.message}")
                }
            }
            logger.info("Killed terminal session: $sessionId")
            true
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to kill session $sessionId", e)
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
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Failed to rename terminal tab: ${e.message}")
                }
            }

            // 更新内部会话记录
            sessions[sessionId] = session.copy(name = newName)
            logger.info("Renamed terminal session $sessionId to: $newName")
            true
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to rename session $sessionId", e)
            false
        }
    }

    /**
     * 获取可用的 Shell 类型（使用 IDEA 检测）
     */
    fun getAvailableShellTypes(): List<ShellTypeInfo> {
        val settings = AgentSettingsService.getInstance()
        val defaultShell = settings.getEffectiveDefaultShell()
        val detectedShells = TerminalCompat.detectInstalledShells()

        return detectedShells.map { shell ->
            val normalizedName = settings.run {
                // 复用 AgentSettingsService 的标准化逻辑
                val lowerName = shell.name.lowercase()
                when {
                    lowerName.contains("git bash") -> "git-bash"
                    lowerName.contains("powershell") -> "powershell"
                    lowerName.contains("command prompt") || lowerName == "cmd" -> "cmd"
                    lowerName.contains("wsl") -> "wsl"
                    lowerName.contains("zsh") -> "zsh"
                    lowerName.contains("fish") -> "fish"
                    lowerName.contains("bash") -> "bash"
                    else -> lowerName.replace(" ", "-")
                }
            }
            ShellTypeInfo(
                name = normalizedName,
                displayName = shell.name,
                command = normalizedName,
                isDefault = normalizedName == defaultShell
            )
        }
    }

    /**
     * 获取默认 Shell 名称
     *
     * 使用用户配置的默认 shell
     */
    private fun getDefaultShellName(): String {
        val settings = AgentSettingsService.getInstance()
        val defaultShell = settings.getEffectiveDefaultShell()
        logger.info("Using default shell: $defaultShell")
        return defaultShell
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
    val output: String? = null,
    val truncated: Boolean = false,
    val totalLines: Int? = null,
    val totalChars: Int? = null,
    val error: String? = null
)

/**
 * 命令中断结果
 */
data class InterruptResult(
    val success: Boolean,
    val sessionId: String,
    val signal: String? = null,  // 发送的信号类型
    val wasRunning: Boolean? = null,  // null 表示无法确定
    val isStillRunning: Boolean? = null,  // null 表示无法确定
    val message: String? = null,
    val error: String? = null
)

/**
 * 输出读取结果
 */
data class ReadResult(
    val success: Boolean,
    val sessionId: String,
    val output: String? = null,
    val isRunning: Boolean? = null,  // null 表示无法确定
    val lineCount: Int = 0,
    val searchMatches: List<SearchMatch>? = null,
    val error: String? = null,
    val waitTimedOut: Boolean = false,  // 等待是否超时
    val waitMessage: String? = null     // 等待相关的消息
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
