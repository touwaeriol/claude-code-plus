package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalKill 工具 - 终止终端会话（支持批量）
 */
class TerminalKillTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 终止终端会话
     *
     * @param arguments 参数：
     *   - session_ids: List<String> - 要终止的会话 ID 列表
     *   - all: Boolean - 是否终止所有会话
     *   至少提供 session_ids 或 all 中的一个
     */
    @Suppress("UNCHECKED_CAST")
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val sessionIds = arguments["session_ids"] as? List<String>
        val killAll = arguments["all"] as? Boolean ?: false

        // 收集要删除的会话 ID
        val idsToKill = when {
            killAll -> sessionManager.getAllSessions().map { it.id }
            sessionIds != null -> sessionIds
            else -> return mapOf(
                "success" to false,
                "error" to "Missing required parameter: session_ids or all"
            )
        }

        if (idsToKill.isEmpty()) {
            return mapOf(
                "success" to true,
                "message" to "No sessions to terminate",
                "killed" to emptyList<String>(),
                "failed" to emptyList<String>()
            )
        }

        logger.info { "Killing ${idsToKill.size} terminal session(s): $idsToKill" }

        // 执行删除
        val killed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (id in idsToKill) {
            if (sessionManager.killSession(id)) {
                killed.add(id)
            } else {
                failed.add(id)
            }
        }

        return buildMap {
            put("success", failed.isEmpty())
            put("killed", killed)
            put("failed", failed)
            put("message", when {
                failed.isEmpty() -> "All ${killed.size} session(s) terminated successfully"
                killed.isEmpty() -> "Failed to terminate all ${failed.size} session(s)"
                else -> "Terminated ${killed.size} session(s), failed ${failed.size}"
            })
        }
    }
}
