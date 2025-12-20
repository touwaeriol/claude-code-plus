package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Terminal 工具 - 执行命令
 *
 * 在 IDEA 内置终端中执行命令，支持前台和后台执行。
 */
class TerminalTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 执行命令
     *
     * @param arguments 参数：
     *   - command: String - 要执行的命令（必需）
     *   - session_id: String? - 会话 ID，为空时创建新会话
     *   - session_name: String? - 新会话名称
     *   - shell_type: String? - Shell 类型（git-bash, powershell, cmd, wsl, bash, zsh, fish）
     *   - background: Boolean? - 是否后台执行（默认 false）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val command = arguments["command"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: command"
            )

        val sessionId = arguments["session_id"] as? String
        val sessionName = arguments["session_name"] as? String
        val shellType = (arguments["shell_type"] as? String)?.let { ShellType.fromString(it) }
            ?: ShellType.AUTO
        val background = arguments["background"] as? Boolean ?: false

        logger.info { "Executing command: $command (session: $sessionId, background: $background)" }

        // 获取或创建会话
        val session = if (sessionId != null) {
            sessionManager.getSession(sessionId)
                ?: return mapOf(
                    "success" to false,
                    "error" to "Session not found: $sessionId"
                )
        } else {
            sessionManager.createSession(sessionName, shellType)
                ?: return mapOf(
                    "success" to false,
                    "error" to "Failed to create terminal session"
                )
        }

        // 执行命令
        val result = sessionManager.executeCommand(session.id, command, background)

        return if (result.success) {
            buildMap {
                put("success", true)
                put("session_id", result.sessionId)
                result.sessionName?.let { put("session_name", it) }
                put("background", result.background)
                if (result.background) {
                    put("message", "Command started in background. Use TerminalRead to check output.")
                } else {
                    // 前台执行：直接返回输出
                    result.output?.let { put("output", it) }
                    // 如果输出被截断，添加提示信息
                    if (result.truncated) {
                        put("truncated", true)
                        result.totalLines?.let { put("total_lines", it) }
                        result.totalChars?.let { put("total_chars", it) }
                        put("truncation_hint", "Output was truncated. Use TerminalRead with 'search' parameter to find specific content, or read with offset/limit for pagination.")
                    }
                }
            }
        } else {
            mapOf(
                "success" to false,
                "session_id" to result.sessionId,
                "error" to (result.error ?: "Unknown error")
            )
        }
    }
}
