package com.asakii.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 默认模型枚举
 */
enum class DefaultModel(val modelId: String, val displayName: String) {
    OPUS_45("claude-opus-4-5-20251101", "Opus 4.5"),
    SONNET_45("claude-sonnet-4-5-20250929", "Sonnet 4.5"),
    HAIKU_45("claude-haiku-4-5-20251001", "Haiku 4.5");

    companion object {
        fun fromModelId(modelId: String?): DefaultModel? {
            return entries.find { it.modelId == modelId }
        }

        fun fromName(name: String?): DefaultModel? {
            return entries.find { it.name == name }
        }
    }
}

/**
 * 默认思考等级枚举
 *
 * 简化为三个核心级别：Off、Think、Ultra
 */
enum class DefaultThinkingLevel(val displayName: String, val description: String) {
    OFF("Off", "Disable extended thinking"),
    THINK("Think", "Standard thinking for most tasks"),
    ULTRA("Ultra", "Deep thinking for complex tasks");

    companion object {
        fun fromName(name: String?): DefaultThinkingLevel? {
            return entries.find { it.name == name }
        }
    }
}

/**
 * 思考级别配置
 *
 * 用于存储思考级别的完整信息，包括预设级别和自定义级别
 */
@Serializable
data class ThinkingLevelConfig(
    val id: String,        // 唯一标识：off, think, ultra, custom_xxx
    val name: String,      // 显示名称
    val tokens: Int,       // token 数量
    val isCustom: Boolean = false  // 是否为自定义级别
)

/**
 * 自定义模型配置
 *
 * 用于存储用户自定义的模型信息
 */
@Serializable
data class CustomModelConfig(
    val id: String,        // 唯一标识（如 "custom_xxx"）
    val displayName: String,  // 显示名称（如 "My Custom Model"）
    val modelId: String       // 模型 ID（如 "claude-sonnet-4-5-20250929"）
)

/**
 * 统一的模型信息类
 *
 * 用于统一表示内置模型和自定义模型
 */
data class ModelInfo(
    val id: String,           // 内置模型用枚举名（如 "OPUS_45"），自定义用 "custom_xxx"
    val displayName: String,  // 显示名称
    val modelId: String,      // 实际模型 ID
    val isBuiltIn: Boolean    // 是否为内置模型
)

/**
 * AI Agent 配置持久化服务（应用级别）
 *
 * 包含所有 AI Agent 相关的配置项，对应 AiAgentServiceConfig。
 */
@State(
    name = "ClaudeCodePlusAgentSettings",
    storages = [Storage("claude-code-plus-agent.xml")]
)
@Service
class AgentSettingsService : PersistentStateComponent<AgentSettingsService.State> {

    data class State(
        // MCP 服务器启用配置
        var enableUserInteractionMcp: Boolean = true,  // 用户交互 MCP（AskUserQuestion 工具）
        var enableJetBrainsMcp: Boolean = true,        // JetBrains IDE MCP（IDE 索引工具）
        var enableContext7Mcp: Boolean = false,        // Context7 MCP（获取最新库文档）
        var context7ApiKey: String = "",               // Context7 API Key（可选）
        var enableTerminalMcp: Boolean = true,         // Terminal MCP（IDEA 内置终端）
        var terminalDisableBuiltinBash: Boolean = true, // 启用 Terminal MCP 时禁用内置 Bash
        var terminalMaxOutputLines: Int = 500,         // Terminal 输出最大行数
        var terminalMaxOutputChars: Int = 50000,       // Terminal 输出最大字符数
        var terminalDefaultShell: String = "",          // Terminal 默认 shell（空 = 使用系统默认）
        var terminalAvailableShells: String = "",      // Terminal 可用 shell 列表（逗号分隔，空 = 全部）
        var enableGitMcp: Boolean = false,             // Git MCP（VCS 集成，默认禁用）

        // MCP 系统提示词（自定义，空字符串表示使用默认值）
        var userInteractionInstructions: String = "",
        var jetbrainsInstructions: String = "",
        var context7Instructions: String = "",
        var terminalInstructions: String = "",
        var gitInstructions: String = "",

        // Git Generate 功能配置
        var gitGenerateSystemPrompt: String = "",     // Git Generate 系统提示词
        var gitGenerateUserPrompt: String = "",       // Git Generate 用户提示词（运行时）
        var gitGenerateTools: String = "[]",          // Git Generate 允许的工具列表（JSON）
        var gitGenerateModel: String = "",            // Git Generate 使用的模型（空=使用默认模型）
        var gitGenerateSaveSession: Boolean = false,  // Git Generate 是否保存会话到历史（默认不保存）

        // 默认启用 ByPass 权限（前端自动应用）
        var defaultBypassPermissions: Boolean = false,

        // Node.js 可执行文件路径，空字符串表示使用系统 PATH
        var nodePath: String = "",

        // 默认模型（使用枚举名称存储，如 "OPUS_45"）
        var defaultModel: String = DefaultModel.OPUS_45.name,

        // 默认思考等级 ID（如 "off", "think", "ultra", "custom_xxx"）
        var defaultThinkingLevelId: String = "ultra",

        // 预设思考级别的 token 配置
        var thinkTokens: Int = 2048,
        var ultraTokens: Int = 8096,

        // 自定义思考级别列表（JSON 序列化）
        var customThinkingLevels: String = "[]",

        // 默认权限模式：default, acceptEdits, plan, bypassPermissions, dontAsk
        var permissionMode: String = "default",

        // 是否包含部分消息
        var includePartialMessages: Boolean = true,

        // Agent 配置（JSON 序列化）
        var customAgents: String = "{}",

        // 自定义模型列表（JSON 序列化）
        var customModels: String = "[]"
    )

    private var state = State()

    // 设置变更监听器
    private val changeListeners = mutableListOf<(AgentSettingsService) -> Unit>()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    // ==================== 监听器管理 ====================

    /**
     * 添加设置变更监听器
     */
    fun addChangeListener(listener: (AgentSettingsService) -> Unit) {
        changeListeners.add(listener)
    }

    /**
     * 移除设置变更监听器
     */
    fun removeChangeListener(listener: (AgentSettingsService) -> Unit) {
        changeListeners.remove(listener)
    }

    /**
     * 通知所有监听器设置已变更
     */
    fun notifyChange() {
        changeListeners.forEach { it(this) }
    }

    // ==================== 便捷属性 ====================

    var enableUserInteractionMcp: Boolean
        get() = state.enableUserInteractionMcp
        set(value) { state.enableUserInteractionMcp = value }

    var enableJetBrainsMcp: Boolean
        get() = state.enableJetBrainsMcp
        set(value) { state.enableJetBrainsMcp = value }

    var enableContext7Mcp: Boolean
        get() = state.enableContext7Mcp
        set(value) { state.enableContext7Mcp = value }

    var context7ApiKey: String
        get() = state.context7ApiKey
        set(value) { state.context7ApiKey = value }

    // MCP 系统提示词属性（空字符串表示使用默认值）
    var userInteractionInstructions: String
        get() = state.userInteractionInstructions
        set(value) { state.userInteractionInstructions = value }

    var jetbrainsInstructions: String
        get() = state.jetbrainsInstructions
        set(value) { state.jetbrainsInstructions = value }

    var context7Instructions: String
        get() = state.context7Instructions
        set(value) { state.context7Instructions = value }

    var enableTerminalMcp: Boolean
        get() = state.enableTerminalMcp
        set(value) { state.enableTerminalMcp = value }

    var terminalDisableBuiltinBash: Boolean
        get() = state.terminalDisableBuiltinBash
        set(value) { state.terminalDisableBuiltinBash = value }

    var terminalMaxOutputLines: Int
        get() = state.terminalMaxOutputLines
        set(value) { state.terminalMaxOutputLines = value }

    var terminalMaxOutputChars: Int
        get() = state.terminalMaxOutputChars
        set(value) { state.terminalMaxOutputChars = value }

    var terminalInstructions: String
        get() = state.terminalInstructions
        set(value) { state.terminalInstructions = value }

    var terminalDefaultShell: String
        get() = state.terminalDefaultShell
        set(value) { state.terminalDefaultShell = value }

    var terminalAvailableShells: String
        get() = state.terminalAvailableShells
        set(value) { state.terminalAvailableShells = value }

    /**
     * 获取生效的默认 shell
     *
     * 如果未配置，则根据操作系统返回默认值：
     * - Windows: git-bash
     * - Unix: bash
     */
    fun getEffectiveDefaultShell(): String {
        val configured = state.terminalDefaultShell
        if (configured.isNotBlank()) {
            return configured
        }
        // 未配置时，根据操作系统返回默认值
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        return if (isWindows) "git-bash" else "bash"
    }

    /**
     * 获取生效的可用 shell 列表
     *
     * 如果配置为空，则返回系统中实际安装的 shell
     */
    fun getEffectiveAvailableShells(): List<String> {
        val configured = state.terminalAvailableShells.trim()
        if (configured.isNotBlank()) {
            // 用户配置了特定的 shell 列表，但仍需过滤掉未安装的
            val installed = detectInstalledShells()
            return configured.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && installed.contains(it) }
        }
        // 返回系统中实际安装的 shell
        return detectInstalledShells()
    }

    /**
     * 检查当前是否为 Windows 系统
     */
    fun isWindows(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }

    /**
     * 检测系统中已安装的 shell 列表（使用 IDEA Terminal API）
     *
     * 通过兼容层调用 TerminalShellsDetector.detectShells() 获取 IDEA 检测到的 shell 列表。
     *
     * 注意：该 API 只检测本地 shell：
     * - detectUnixShells(): 搜索本地 Unix 目录 (/bin, /usr/bin 等)
     * - detectWindowsShells(): 检测本地 Windows shell (PowerShell, cmd)
     * - detectWsl(): 检测本地 WSL 发行版
     *
     * 该 API 不会返回 SSH session 或远程 shell，因此无需额外过滤。
     *
     * @return 已安装的本地 shell 名称列表（不包含 auto）
     */
    fun detectInstalledShells(): List<String> {
        // 使用兼容层的 API（不同 IDEA 版本有不同实现）
        val detectedShells = com.asakii.plugin.compat.TerminalCompat.detectInstalledShells()

        // 转换为标准化的 shell 名称
        return detectedShells.map { shell ->
            normalizeShellName(shell.name, shell.path)
        }.distinct()
    }

    /**
     * 标准化 shell 名称
     *
     * 将 IDEA 检测到的 shell 名称转换为我们使用的标准名称
     */
    private fun normalizeShellName(name: String, path: String): String {
        val lowerName = name.lowercase()
        val lowerPath = path.lowercase()

        return when {
            // Windows shells
            lowerName.contains("git bash") || lowerPath.contains("git") && lowerPath.contains("bash") -> "git-bash"
            lowerName.contains("powershell") || lowerPath.contains("powershell") || lowerPath.contains("pwsh") -> "powershell"
            lowerName.contains("command prompt") || lowerName.contains("cmd") || lowerPath.endsWith("cmd.exe") -> "cmd"
            lowerName.contains("wsl") || lowerName.contains("ubuntu") || lowerName.contains("debian") -> "wsl"
            // Unix shells
            lowerPath.contains("zsh") -> "zsh"
            lowerPath.contains("fish") -> "fish"
            lowerPath.endsWith("/bash") || lowerPath.endsWith("bash.exe") -> "bash"
            lowerPath.endsWith("/sh") -> "sh"
            // 其他：使用原始名称
            else -> lowerName.replace(" ", "-")
        }
    }


    var enableGitMcp: Boolean
        get() = state.enableGitMcp
        set(value) { state.enableGitMcp = value }

    var gitInstructions: String
        get() = state.gitInstructions
        set(value) { state.gitInstructions = value }

    // Git Generate 配置属性
    var gitGenerateSystemPrompt: String
        get() = state.gitGenerateSystemPrompt
        set(value) { state.gitGenerateSystemPrompt = value }

    var gitGenerateUserPrompt: String
        get() = state.gitGenerateUserPrompt
        set(value) { state.gitGenerateUserPrompt = value }

    var gitGenerateToolsJson: String
        get() = state.gitGenerateTools
        set(value) { state.gitGenerateTools = value }

    /**
     * 获取 Git Generate 允许的工具列表
     */
    fun getGitGenerateTools(): List<String> {
        return try {
            json.decodeFromString<List<String>>(state.gitGenerateTools)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置 Git Generate 允许的工具列表
     */
    fun setGitGenerateTools(tools: List<String>) {
        state.gitGenerateTools = json.encodeToString(tools)
    }

    /** 获取生效的 Git Generate 系统提示词（自定义或默认） */
    val effectiveGitGenerateSystemPrompt: String
        get() = state.gitGenerateSystemPrompt.ifBlank { GitGenerateDefaults.SYSTEM_PROMPT }

    /** 获取生效的 Git Generate 用户提示词（自定义或默认） */
    val effectiveGitGenerateUserPrompt: String
        get() = state.gitGenerateUserPrompt.ifBlank { GitGenerateDefaults.USER_PROMPT }

    /** 获取生效的 Git Generate 工具列表（自定义或默认） */
    val effectiveGitGenerateTools: List<String>
        get() = getGitGenerateTools().takeIf { it.isNotEmpty() } ?: GitGenerateDefaults.TOOLS

    /** Git Generate 使用的模型 ID */
    var gitGenerateModel: String
        get() = state.gitGenerateModel
        set(value) { state.gitGenerateModel = value }

    /**
     * 获取 Git Generate 的有效模型 ID
     * 如果配置的模型不存在，则 fallback 到第一个内置模型
     */
    val effectiveGitGenerateModelId: String
        get() {
            val configuredModelId = state.gitGenerateModel
            if (configuredModelId.isBlank()) {
                return effectiveDefaultModelId  // 使用全局默认模型
            }
            // 检查模型是否存在
            val modelInfo = getModelById(configuredModelId)
            return modelInfo?.modelId ?: DefaultModel.entries.first().modelId  // fallback 到第一个内置模型
        }

    /** Git Generate 是否保存会话到历史 */
    var gitGenerateSaveSession: Boolean
        get() = state.gitGenerateSaveSession
        set(value) { state.gitGenerateSaveSession = value }

    /** 获取生效的 User Interaction MCP 提示词（自定义或默认） */
    val effectiveUserInteractionInstructions: String
        get() = state.userInteractionInstructions.ifBlank { McpDefaults.USER_INTERACTION_INSTRUCTIONS }

    /** 获取生效的 JetBrains MCP 提示词（自定义或默认） */
    val effectiveJetbrainsInstructions: String
        get() = state.jetbrainsInstructions.ifBlank { McpDefaults.JETBRAINS_INSTRUCTIONS }

    /** 获取生效的 Context7 MCP 提示词（自定义或默认） */
    val effectiveContext7Instructions: String
        get() = state.context7Instructions.ifBlank { McpDefaults.CONTEXT7_INSTRUCTIONS }

    /** 获取生效的 Terminal MCP 提示词（自定义或默认） */
    val effectiveTerminalInstructions: String
        get() = state.terminalInstructions.ifBlank { McpDefaults.TERMINAL_INSTRUCTIONS }

    /** 获取生效的 Git MCP 提示词（自定义或默认） */
    val effectiveGitInstructions: String
        get() = state.gitInstructions.ifBlank { McpDefaults.GIT_INSTRUCTIONS }

    // Agent 配置
    var customAgents: String
        get() = state.customAgents
        set(value) { state.customAgents = value }

    var defaultBypassPermissions: Boolean
        get() = state.defaultBypassPermissions
        set(value) { state.defaultBypassPermissions = value }

    var nodePath: String
        get() = state.nodePath
        set(value) { state.nodePath = value }

    var defaultModel: String
        get() = state.defaultModel
        set(value) { state.defaultModel = value }

    /** 获取默认模型枚举 */
    val defaultModelEnum: DefaultModel
        get() = DefaultModel.fromName(state.defaultModel) ?: DefaultModel.OPUS_45

    /** 获取默认模型的实际 modelId */
    val defaultModelId: String
        get() = defaultModelEnum.modelId

    var permissionMode: String
        get() = state.permissionMode
        set(value) { state.permissionMode = value }

    var includePartialMessages: Boolean
        get() = state.includePartialMessages
        set(value) { state.includePartialMessages = value }

    var customModelsJson: String
        get() = state.customModels
        set(value) { state.customModels = value }

    /**
     * 获取自定义模型列表
     */
    fun getCustomModels(): List<CustomModelConfig> {
        return try {
            json.decodeFromString<List<CustomModelConfig>>(state.customModels)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置自定义模型列表
     */
    fun setCustomModels(models: List<CustomModelConfig>) {
        state.customModels = json.encodeToString(models)
    }

    /**
     * 添加自定义模型
     */
    fun addCustomModel(displayName: String, modelId: String): CustomModelConfig {
        val models = getCustomModels().toMutableList()
        val id = "custom_${System.currentTimeMillis()}"
        val newModel = CustomModelConfig(id, displayName, modelId)
        models.add(newModel)
        setCustomModels(models)
        return newModel
    }

    /**
     * 更新自定义模型
     */
    fun updateCustomModel(id: String, displayName: String, modelId: String): CustomModelConfig? {
        val models = getCustomModels().toMutableList()
        val index = models.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = CustomModelConfig(id, displayName, modelId)
            models[index] = updated
            setCustomModels(models)
            return updated
        }
        return null
    }

    /**
     * 删除自定义模型
     */
    fun removeCustomModel(id: String) {
        val models = getCustomModels().toMutableList()
        models.removeIf { it.id == id }
        setCustomModels(models)
        // 如果删除的是当前默认模型，切换到 OPUS_45
        if (state.defaultModel == id) {
            state.defaultModel = DefaultModel.OPUS_45.name
        }
    }

    /**
     * 获取所有可用模型（内置 + 自定义）
     *
     * 返回统一的模型信息列表，包含 id, displayName, modelId
     */
    fun getAllAvailableModels(): List<ModelInfo> {
        val builtIn = DefaultModel.entries.map {
            ModelInfo(it.name, it.displayName, it.modelId, isBuiltIn = true)
        }
        val custom = getCustomModels().map {
            ModelInfo(it.id, it.displayName, it.modelId, isBuiltIn = false)
        }
        return builtIn + custom
    }

    /**
     * 根据 ID 获取模型信息
     */
    fun getModelById(id: String): ModelInfo? {
        // 先检查内置模型
        DefaultModel.fromName(id)?.let {
            return ModelInfo(it.name, it.displayName, it.modelId, isBuiltIn = true)
        }
        // 再检查自定义模型
        getCustomModels().find { it.id == id }?.let {
            return ModelInfo(it.id, it.displayName, it.modelId, isBuiltIn = false)
        }
        return null
    }

    /**
     * 获取当前默认模型的实际 modelId（支持自定义模型）
     */
    val effectiveDefaultModelId: String
        get() = getModelById(state.defaultModel)?.modelId ?: DefaultModel.OPUS_45.modelId

    var defaultThinkingLevelId: String
        get() {
            val id = state.defaultThinkingLevelId
            // 只允许预设级别 ID，无效的自定义级别 fallback 到 ultra
            return if (id in listOf("off", "think", "ultra")) id else "ultra"
        }
        set(value) { state.defaultThinkingLevelId = value }

    var thinkTokens: Int
        get() = state.thinkTokens
        set(value) { state.thinkTokens = value }

    var ultraTokens: Int
        get() = state.ultraTokens
        set(value) { state.ultraTokens = value }

    var customThinkingLevelsJson: String
        get() = state.customThinkingLevels
        set(value) { state.customThinkingLevels = value }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取自定义思考级别列表
     */
    fun getCustomThinkingLevels(): List<ThinkingLevelConfig> {
        return try {
            json.decodeFromString<List<ThinkingLevelConfig>>(state.customThinkingLevels)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置自定义思考级别列表
     */
    fun setCustomThinkingLevels(levels: List<ThinkingLevelConfig>) {
        state.customThinkingLevels = json.encodeToString(levels)
    }

    /**
     * 添加自定义思考级别
     */
    fun addCustomThinkingLevel(name: String, tokens: Int): ThinkingLevelConfig {
        val levels = getCustomThinkingLevels().toMutableList()
        val id = "custom_${System.currentTimeMillis()}"
        val newLevel = ThinkingLevelConfig(id, name, tokens, isCustom = true)
        levels.add(newLevel)
        setCustomThinkingLevels(levels)
        return newLevel
    }

    /**
     * 删除自定义思考级别
     */
    fun removeCustomThinkingLevel(id: String) {
        val levels = getCustomThinkingLevels().toMutableList()
        levels.removeIf { it.id == id }
        setCustomThinkingLevels(levels)
        // 如果删除的是当前默认级别，切换到 ultra
        if (state.defaultThinkingLevelId == id) {
            state.defaultThinkingLevelId = "ultra"
        }
    }

    /**
     * 获取所有思考级别（仅预设级别）
     */
    fun getAllThinkingLevels(): List<ThinkingLevelConfig> {
        return listOf(
            ThinkingLevelConfig("off", "Off", 0, isCustom = false),
            ThinkingLevelConfig("think", "Think", state.thinkTokens, isCustom = false),
            ThinkingLevelConfig("ultra", "Ultra", state.ultraTokens, isCustom = false)
        )
    }

    /**
     * 根据 ID 获取思考级别配置
     */
    fun getThinkingLevelById(id: String): ThinkingLevelConfig? {
        return getAllThinkingLevels().find { it.id == id }
    }

    /**
     * 获取当前默认思考级别的 token 数量
     */
    val defaultThinkingTokens: Int
        get() = getThinkingLevelById(state.defaultThinkingLevelId)?.tokens ?: state.ultraTokens

    // 为了向后兼容，保留 defaultThinkingLevel 属性（映射到新结构）
    var defaultThinkingLevel: String
        get() = when (state.defaultThinkingLevelId) {
            "off" -> "OFF"
            "think" -> "THINK"
            "ultra" -> "ULTRA"
            else -> "ULTRA"  // 自定义级别映射为 ULTRA（用于旧 API 兼容）
        }
        set(value) {
            state.defaultThinkingLevelId = when (value.uppercase()) {
                "OFF" -> "off"
                "THINK" -> "think"
                "ULTRA", "HIGH", "VERY_HIGH", "MEDIUM", "LOW" -> "ultra"  // 旧级别都映射到 ultra
                else -> "ultra"
            }
        }

    companion object {
        @JvmStatic
        fun getInstance(): AgentSettingsService = service()

        /**
         * Node.js 检测结果
         */
        data class NodeInfo(
            val path: String,
            val version: String? = null
        )

        /**
         * 检测 Node.js 路径和版本
         * @return NodeInfo 包含路径和版本，未找到返回 null
         */
        fun detectNodeInfo(): NodeInfo? {
            val path = detectNodePath()
            if (path.isEmpty()) return null

            val version = detectNodeVersion(path)
            return NodeInfo(path, version)
        }

        /**
         * 自动检测系统中的 Node.js 路径
         * 使用 login shell 执行，以正确加载用户的环境变量（PATH 等）
         * @return Node.js 可执行文件路径，未找到返回空字符串
         */
        fun detectNodePath(): String {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")

            // 1. 尝试通过 login shell 查找（与运行时逻辑一致）
            try {
                val command = if (isWindows) {
                    // Windows: 使用 cmd /c
                    arrayOf("cmd", "/c", "where", "node")
                } else {
                    // macOS/Linux: 使用 login shell 执行 which node
                    val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
                    arrayOf(defaultShell, "-l", "-c", "which node")
                }

                val process = ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()

                val result = process.inputStream.bufferedReader().readLine()?.trim()
                val exitCode = process.waitFor()

                if (exitCode == 0 && !result.isNullOrBlank() && java.io.File(result).exists()) {
                    return result
                }
            } catch (_: Exception) {
                // 忽略错误，继续尝试其他方式
            }

            // 2. 检查常见安装路径
            val commonPaths = if (isWindows) {
                listOf(
                    "C:\\Program Files\\nodejs\\node.exe",
                    "C:\\Program Files (x86)\\nodejs\\node.exe",
                    System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\node\\node.exe" },
                    System.getenv("APPDATA")?.let { "$it\\nvm\\current\\node.exe" },
                    System.getenv("NVM_HOME")?.let { "$it\\current\\node.exe" }
                )
            } else {
                listOf(
                    "/usr/local/bin/node",
                    "/usr/bin/node",
                    "/opt/homebrew/bin/node",
                    System.getenv("HOME")?.let { "$it/.nvm/current/bin/node" },
                    System.getenv("HOME")?.let { "$it/.local/bin/node" }
                )
            }

            for (path in commonPaths) {
                if (path != null && java.io.File(path).exists()) {
                    return path
                }
            }

            return ""
        }

        /**
         * 检测 Node.js 版本
         * @param nodePath Node.js 可执行文件路径
         * @return 版本号（如 v24.2.0），未检测到返回 null
         */
        private fun detectNodeVersion(nodePath: String): String? {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")

            try {
                val command = if (isWindows) {
                    arrayOf("cmd", "/c", nodePath, "--version")
                } else {
                    val defaultShell = System.getenv("SHELL") ?: "/bin/bash"
                    arrayOf(defaultShell, "-l", "-c", "$nodePath --version")
                }

                val process = ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()

                val result = process.inputStream.bufferedReader().readLine()?.trim()
                val exitCode = process.waitFor()

                if (exitCode == 0 && !result.isNullOrBlank()) {
                    return result
                }
            } catch (_: Exception) {
                // 忽略错误
            }

            return null
        }
    }
}
