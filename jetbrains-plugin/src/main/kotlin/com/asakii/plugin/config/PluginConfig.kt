package com.asakii.plugin.config

import com.intellij.openapi.diagnostic.Logger

/**
 * 插件配置（动态插件兼容版本）
 *
 * 使用内部状态替代 System.setProperty，支持动态加载/卸载。
 */
object PluginConfig {

    private val logger = Logger.getInstance(PluginConfig::class.java)

    /**
     * Claude 命令是否可用
     */
    @Volatile
    var isClaudeAvailable: Boolean = false
        private set

    /**
     * Claude 命令检查结果
     */
    @Volatile
    var claudeCheckResult: String = ""
        private set

    /**
     * 检查 claude 命令是否可用
     * @return Pair<是否可用, 详细信息>
     */
    fun checkClaudeCommand(): Pair<Boolean, String> {
        return try {
            val osName = System.getProperty("os.name").lowercase()
            val command = if (osName.contains("windows")) {
                listOf("cmd", "/c", "claude", "--version")
            } else {
                listOf("/bin/bash", "-c", "claude --version")
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Pair(true, "Claude CLI 可用: ${output.trim()}")
            } else {
                Pair(false, "Claude CLI 执行失败 (退出码: $exitCode): ${output.trim()}")
            }
        } catch (e: Exception) {
            Pair(false, "无法执行 claude 命令: ${e.message}")
        }
    }

    /**
     * 设置环境并检查 claude 命令可用性
     * 不再修改全局系统属性，改用内部状态
     */
    fun setupEnvironment() {
        val (isAvailable, message) = checkClaudeCommand()

        // 使用内部状态替代 System.setProperty
        isClaudeAvailable = isAvailable
        claudeCheckResult = message

        logger.info("Claude 命令检查: $message")
    }
}
