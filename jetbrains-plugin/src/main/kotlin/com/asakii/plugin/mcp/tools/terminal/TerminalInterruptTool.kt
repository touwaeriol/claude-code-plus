package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalInterrupt 工具 - 中断正在执行的命令（发送 Ctrl+C）
 */
class TerminalInterruptTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 中断正在执行的命令
     *
     * @param arguments 参数：
     *   - session_id: String - 会话 ID（必需）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val sessionId = arguments["session_id"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: session_id"
            )

        logger.info { "Interrupting command in session: $sessionId" }

        val result = sessionManager.interruptCommand(sessionId)

        return if (result.success) {
            buildMap {
                put("success", true)
                put("session_id", result.sessionId)
                put("was_running", result.wasRunning)
                put("is_still_running", result.isStillRunning)
                result.message?.let { put("message", it) }
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
