package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Terminal 工具 - 执行命令
 *
 * 在 IDEA 内置终端中执行命令。
 * 命令执行后立即返回，不等待完成。使用 TerminalRead 读取输出。
 */
class TerminalTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 执行命令
     *
     * @param arguments 参数：
     *   - command: String - 要执行的命令（必需）
     *   - session_id: String? - 会话 ID，为空时使用当前 AI 会话的默认终端
     *   - session_name: String? - 新会话名称
     *   - shell_type: String? - Shell 类型（如 git-bash, powershell），不传则使用配置的默认终端
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val command = arguments["command"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: command"
            )

        val sessionId = arguments["session_id"] as? String
        val sessionName = arguments["session_name"] as? String
        // shell_type 为 null 时，createSession 会使用配置的默认终端
        val shellName = arguments["shell_type"] as? String

        logger.info { "Executing command: $command (session: $sessionId, shellName: $shellName)" }

        // 获取或创建会话
        val session = if (sessionId != null) {
            // 指定了 session_id，验证所有权并获取会话
            sessionManager.validateSessionOwnership(sessionId)?.let { return it }
            sessionManager.getSession(sessionId)!!
        } else {
            // 未指定 session_id，使用当前 AI 会话的默认终端
            if (sessionName != null) {
                // 如果指定了 session_name，创建新会话
                sessionManager.createSession(sessionName, shellName)
            } else {
                // 使用默认终端
                sessionManager.getOrCreateDefaultTerminal(shellName)
            } ?: return mapOf(
                "success" to false,
                "error" to "Failed to create terminal session"
            )
        }

        // 执行命令（始终立即返回，不等待）
        val result = sessionManager.executeCommandAsync(session.id, command)

        return if (result.success) {
            mapOf(
                "success" to true,
                "session_id" to result.sessionId,
                "session_name" to (result.sessionName ?: session.name),
                "message" to "Command sent. Use TerminalRead to check output."
            )
        } else {
            mapOf(
                "success" to false,
                "session_id" to result.sessionId,
                "error" to (result.error ?: "Unknown error")
            )
        }
    }
}
