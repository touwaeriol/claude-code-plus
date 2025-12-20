package com.asakii.plugin.mcp.tools.terminal

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * TerminalTypes 工具 - 获取可用的 Shell 类型
 */
class TerminalTypesTool(private val sessionManager: TerminalSessionManager) {

    /**
     * 获取可用的 Shell 类型
     *
     * @param arguments 参数（无需参数）
     */
    fun execute(arguments: Map<String, Any>): Map<String, Any> {
        logger.info { "Getting available shell types" }

        val types = sessionManager.getAvailableShellTypes()
        val platform = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "windows"
        } else {
            "unix"
        }

        val defaultType = types.find { it.isDefault }?.name ?: types.firstOrNull()?.name ?: "bash"

        return mapOf(
            "success" to true,
            "platform" to platform,
            "types" to types.map { type ->
                buildMap {
                    put("name", type.name)
                    put("display_name", type.displayName)
                    type.command?.let { put("command", it) }
                    put("is_default", type.isDefault)
                }
            },
            "default_type" to defaultType
        )
    }
}
