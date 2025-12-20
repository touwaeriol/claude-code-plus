package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalKill 工具 - 终止终端会话
 */
class TerminalKillTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 终止终端会话
     *
     * @param arguments 参数：
     *   - session_id: String - 要终止的会话 ID（必需）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val sessionId = arguments["session_id"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: session_id"
            )

        logger.info { "Killing terminal session: $sessionId" }

        val success = sessionManager.killSession(sessionId)

        return if (success) {
            mapOf(
                "success" to true,
                "session_id" to sessionId,
                "message" to "Session terminated successfully"
            )
        } else {
            mapOf(
                "success" to false,
                "session_id" to sessionId,
                "error" to "Failed to terminate session or session not found"
            )
        }
    }
}
