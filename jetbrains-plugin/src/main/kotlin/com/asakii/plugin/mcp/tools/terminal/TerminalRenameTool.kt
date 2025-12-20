package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalRename 工具 - 重命名终端会话
 */
class TerminalRenameTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 重命名终端会话
     *
     * @param arguments 参数：
     *   - session_id: String - 会话 ID（必需）
     *   - new_name: String - 新名称（必需）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val sessionId = arguments["session_id"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: session_id"
            )

        val newName = arguments["new_name"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: new_name"
            )

        logger.info { "Renaming terminal session $sessionId to: $newName" }

        val success = sessionManager.renameSession(sessionId, newName)

        return if (success) {
            mapOf(
                "success" to true,
                "session_id" to sessionId,
                "new_name" to newName,
                "message" to "Session renamed successfully"
            )
        } else {
            mapOf(
                "success" to false,
                "session_id" to sessionId,
                "error" to "Failed to rename session or session not found"
            )
        }
    }
}
