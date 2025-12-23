package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalRead 工具 - 读取/搜索终端输出
 *
 * 读取终端会话的输出内容，支持正则表达式搜索。
 * 支持等待命令执行完成后再读取。
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
     *   - wait: Boolean? - 是否等待命令执行完成（默认 false）
     *   - timeout: Int? - 等待超时时间（毫秒，默认 30000）
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
        // 默认不等待，可通过 wait=true 等待命令完成
        val waitForIdle = arguments["wait"] as? Boolean ?: false
        val timeout = (arguments["timeout"] as? Number)?.toLong() ?: 30_000L

        logger.info { "Reading output from session: $sessionId (maxLines: $maxLines, search: $search, waitForIdle: $waitForIdle)" }

        val result = sessionManager.readOutput(sessionId, maxLines, search, contextLines, waitForIdle, timeout)

        return if (result.success) {
            buildMap {
                put("success", true)
                put("session_id", result.sessionId)
                result.isRunning?.let { put("is_running", it) }
                put("status", when (result.isRunning) {
                    true -> "running"
                    false -> "idle"
                    null -> "unknown"
                })

                // 等待状态信息
                if (result.waitTimedOut) {
                    put("wait_timed_out", true)
                }
                result.waitMessage?.let { put("wait_message", it) }

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
