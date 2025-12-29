package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalInterrupt 工具 - 发送终端控制信号
 *
 * 只能操作当前 AI 会话的终端。
 *
 * 支持的信号：
 * - SIGINT (Ctrl+C): 中断信号，默认值
 * - SIGQUIT (Ctrl+\): 强制退出信号
 * - SIGTSTP (Ctrl+Z): 暂停进程信号
 */
class TerminalInterruptTool(private val sessionManager: TerminalSessionManager) {

    companion object {
        val VALID_SIGNALS = setOf("SIGINT", "SIGQUIT", "SIGTSTP")
    }

    /**
     * 发送终端控制信号
     *
     * @param arguments 参数：
     *   - session_id: String - 会话 ID（必需）
     *   - signal: String - 信号类型（可选，默认 SIGINT）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val sessionId = arguments["session_id"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: session_id"
            )

        // 验证会话所有权
        sessionManager.validateSessionOwnership(sessionId)?.let { return it }

        // 解析 signal 参数，默认 SIGINT
        val signal = (arguments["signal"] as? String)?.uppercase() ?: "SIGINT"

        // 验证 signal 值
        if (signal !in VALID_SIGNALS) {
            return mapOf(
                "success" to false,
                "error" to "Invalid signal: $signal. Valid values: ${VALID_SIGNALS.joinToString(", ")}"
            )
        }

        logger.info { "Sending $signal to session: $sessionId" }

        val result = sessionManager.interruptCommand(sessionId, signal)

        return if (result.success) {
            buildMap {
                put("success", true)
                put("session_id", result.sessionId)
                result.signal?.let { put("signal", it) }
                result.wasRunning?.let { put("was_running", it) }
                result.isStillRunning?.let { put("is_still_running", it) }
                result.message?.let { put("message", it) }
            }
        } else {
            buildMap {
                put("success", false)
                put("session_id", result.sessionId)
                result.signal?.let { put("signal", it) }
                put("error", result.error ?: "Unknown error")
            }
        }
    }
}
