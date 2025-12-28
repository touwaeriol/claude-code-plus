package com.asakii.plugin.config

import com.intellij.openapi.diagnostic.Logger

/**
 * 后端类型枚举
 */
enum class BackendType {
    CLAUDE,
    CODEX
}

/**
 * Codex 模型提供商
 */
enum class CodexModelProvider {
    OPENAI,
    ANTHROPIC,
    CUSTOM
}

/**
 * Codex 沙箱模式
 */
enum class CodexSandboxMode {
    NONE,
    DOCKER,
    VM
}

/**
 * Codex 设置数据类
 */
data class CodexSettings(
    /**
     * Codex 可执行文件路径
     */
    val binaryPath: String = "",

    /**
     * 模型提供商
     */
    val modelProvider: CodexModelProvider = CodexModelProvider.OPENAI,

    /**
     * 沙箱模式
     */
    val sandboxMode: CodexSandboxMode = CodexSandboxMode.NONE,

    /**
     * 是否启用 MCP 服务器
     */
    val enableMcpServer: Boolean = false,

    /**
     * MCP 服务器配置路径
     */
    val mcpConfigPath: String = ""
)

/**
 * 插件配置（动态插件兼容版本 + 多后端支持）
 *
 * 使用内部状态替代 System.setProperty，支持动态加载/卸载。
 * 支持 Claude 和 Codex 两种后端。
 */
object PluginConfig {

    private val logger = Logger.getInstance(PluginConfig::class.java)

    // ==================== Claude 配置 ====================

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

    // ==================== Codex 配置 ====================

    /**
     * Codex 设置
     */
    @Volatile
    var codexSettings: CodexSettings = CodexSettings()
        private set

    /**
     * Codex 是否可用
     */
    @Volatile
    var isCodexAvailable: Boolean = false
        private set

    /**
     * Codex 检查结果
     */
    @Volatile
    var codexCheckResult: String = ""
        private set

    /**
     * 检查 Codex 可执行文件是否可用
     * @param binaryPath Codex 可执行文件路径，如果为空则使用默认配置
     * @return Pair<是否可用, 详细信息>
     */
    fun checkCodexBinary(binaryPath: String = codexSettings.binaryPath): Pair<Boolean, String> {
        if (binaryPath.isEmpty()) {
            return Pair(false, "Codex 二进制路径未配置")
        }

        return try {
            val process = ProcessBuilder(binaryPath, "--version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Pair(true, "Codex 可用: ${output.trim()}")
            } else {
                Pair(false, "Codex 执行失败 (退出码: $exitCode): ${output.trim()}")
            }
        } catch (e: Exception) {
            Pair(false, "无法执行 Codex 命令: ${e.message}")
        }
    }

    /**
     * 更新 Codex 设置
     */
    fun updateCodexSettings(settings: CodexSettings) {
        codexSettings = settings

        // 重新检查 Codex 可用性
        val (isAvailable, message) = checkCodexBinary(settings.binaryPath)
        isCodexAvailable = isAvailable
        codexCheckResult = message

        logger.info("Codex 设置已更新: $settings")
        logger.info("Codex 可用性: $message")
    }

    // ==================== 多后端配置 ====================

    /**
     * 默认后端类型
     */
    @Volatile
    var defaultBackendType: BackendType = BackendType.CLAUDE
        private set

    /**
     * 设置默认后端类型
     */
    fun setDefaultBackendType(type: BackendType) {
        defaultBackendType = type
        logger.info("默认后端已设置为: $type")
    }

    /**
     * 获取可用的后端列表
     */
    fun getAvailableBackends(): List<BackendType> {
        return buildList {
            if (isClaudeAvailable) {
                add(BackendType.CLAUDE)
            }
            if (isCodexAvailable) {
                add(BackendType.CODEX)
            }
        }
    }

    /**
     * 检查指定后端是否可用
     */
    fun isBackendAvailable(type: BackendType): Boolean {
        return when (type) {
            BackendType.CLAUDE -> isClaudeAvailable
            BackendType.CODEX -> isCodexAvailable
        }
    }

    // ==================== 初始化 ====================

    /**
     * 设置环境并检查所有后端可用性
     */
    fun setupEnvironment() {
        // 检查 Claude
        val (claudeAvailable, claudeMessage) = checkClaudeCommand()
        isClaudeAvailable = claudeAvailable
        claudeCheckResult = claudeMessage
        logger.info("Claude 命令检查: $claudeMessage")

        // 检查 Codex（仅当已配置路径时）
        if (codexSettings.binaryPath.isNotEmpty()) {
            val (codexAvailable, codexMessage) = checkCodexBinary()
            isCodexAvailable = codexAvailable
            codexCheckResult = codexMessage
            logger.info("Codex 检查: $codexMessage")
        }

        // 设置默认后端
        defaultBackendType = when {
            isClaudeAvailable -> BackendType.CLAUDE
            isCodexAvailable -> BackendType.CODEX
            else -> BackendType.CLAUDE // 即使不可用也默认为 Claude
        }

        logger.info("环境设置完成，默认后端: $defaultBackendType")
        logger.info("可用后端: ${getAvailableBackends()}")
    }
}
