package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalList 工具 - 列出当前 AI 会话的终端
 */
class TerminalListTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 列出当前 AI 会话的终端（默认终端 + 溢出终端）
     *
     * @param arguments 参数：
     *   - include_output_preview: Boolean? - 是否包含输出预览（默认 false）
     *   - preview_lines: Int? - 预览行数（默认 5）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val includePreview = arguments["include_output_preview"] as? Boolean ?: false
        val previewLines = (arguments["preview_lines"] as? Number)?.toInt() ?: 5

        logger.info { "Listing terminal sessions for current AI session (includePreview: $includePreview)" }

        // 只获取当前 AI 会话的终端
        val sessions = sessionManager.getCurrentSessionTerminals()

        val sessionList = sessions.map { session ->
            buildMap {
                put("id", session.id)
                put("name", session.name)
                put("shell_type", session.shellType)
                put("is_running", session.hasRunningCommands())
                put("created_at", session.createdAt)
                put("last_command_at", session.lastCommandAt)
                put("is_background", session.isBackground)

                if (includePreview) {
                    val output = session.getOutput(previewLines)
                    put("output_preview", output)
                }
            }
        }

        return mapOf(
            "success" to true,
            "count" to sessions.size,
            "sessions" to sessionList
        )
    }
}
