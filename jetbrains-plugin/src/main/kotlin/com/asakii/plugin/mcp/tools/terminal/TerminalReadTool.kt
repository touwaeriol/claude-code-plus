package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalRead 工具 - 读取/搜索终端输出
 *
 * 读取终端会话的输出内容，支持正则表达式搜索。
 */
class TerminalReadTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 读取终端输出
     *
     * @param arguments 参数：
     *   - session_id: String - 会话 ID（必需）
     *   - max_lines: Int? - 最大行数（默认 1000）
     *   - search: String? - 搜索模式（正则表达式）
     *   - context_lines: Int? - 搜索结果上下文行数（默认 2）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        val sessionId = arguments["session_id"] as? String
            ?: return mapOf(
                "success" to false,
                "error" to "Missing required parameter: session_id"
            )

        val maxLines = (arguments["max_lines"] as? Number)?.toInt() ?: 1000
        val search = arguments["search"] as? String
        val contextLines = (arguments["context_lines"] as? Number)?.toInt() ?: 2

        logger.info { "Reading output from session: $sessionId (maxLines: $maxLines, search: $search)" }

        val result = sessionManager.readOutput(sessionId, maxLines, search, contextLines)

        return if (result.success) {
            buildMap {
                put("success", true)
                put("session_id", result.sessionId)
                put("is_running", result.isRunning)
                put("status", if (result.isRunning) "running" else "idle")

                if (result.searchMatches != null) {
                    // 搜索模式
                    put("match_count", result.searchMatches.size)
                    put("matches", result.searchMatches.map { match ->
                        mapOf(
                            "line_number" to match.lineNumber,
                            "line" to match.line,
                            "context" to match.context
                        )
                    })
                } else {
                    // 普通读取模式
                    put("output", result.output ?: "")
                    put("line_count", result.lineCount)
                }
            }
        } else {
            mapOf(
                "success" to false,
                "session_id" to sessionId,
                "error" to (result.error ?: "Unknown error")
            )
        }
    }
}
