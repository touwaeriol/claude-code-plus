package com.asakii.settings

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        var enableJetBrainsMcp: Boolean = true,        // JetBrains LSP MCP（IDE 索引工具）
        var enableJetBrainsFileMcp: Boolean = true,    // JetBrains File MCP（文件操作工具）
        var enableContext7Mcp: Boolean = false,        // Context7 MCP（获取最新库文档）
        var context7ApiKey: String = "",               // Context7 API Key（可选）
        var enableTerminalMcp: Boolean = false,        // Terminal MCP（IDEA 内置终端，默认禁用）
        var terminalDisableBuiltinBash: Boolean = true, // 启用 Terminal MCP 时禁用内置 Bash
        var terminalMaxOutputLines: Int = 500,         // Terminal 输出最大行数
        var terminalMaxOutputChars: Int = 50000,       // Terminal 输出最大字符数
        var terminalDefaultShell: String = "",          // Terminal 默认 shell（空 = 使用系统默认）
        var terminalAvailableShells: String = "",      // Terminal 可用 shell 列表（逗号分隔，空 = 全部）
        var terminalReadTimeout: Int = 10,             // TerminalRead 默认超时时间（秒）
        var terminalDisableInteractive: Boolean = true, // 是否禁用交互式终端（TERM=dumb）
        var enableGitMcp: Boolean = false,             // Git MCP（VCS 集成，默认禁用）
        // JetBrains File MCP 禁用内置工具配置
        var jetbrainsFileDisableBuiltinTools: Boolean = false, // 启用 JetBrains File MCP 时禁用内置工具
        var jetbrainsFileDisabledTools: String = "Read,Write,Edit", // 禁用的内置工具列表（逗号分隔）
        // JetBrains File MCP 外部文件配置
        var jetbrainsFileAllowExternal: Boolean = true, // 是否允许访问项目外部文件（默认开启）
        var jetbrainsFileExternalRules: String = "[]", // 外部路径规则列表（JSON 序列化）
        // 旧版配置（用于迁移，新版本不再使用）
        @Deprecated("Use jetbrainsFileExternalRules instead")
        var jetbrainsFileExternalDir1: String = "", // 外部目录 1（已废弃）
        @Deprecated("Use jetbrainsFileExternalRules instead")
        var jetbrainsFileExternalDir2: String = "", // 外部目录 2（已废弃）
        @Deprecated("Use jetbrainsFileExternalRules instead")
        var jetbrainsFileExternalDir3: String = "", // 外部目录 3（已废弃）
        // MCP 工具调用超时配置（秒）
        var userInteractionMcpTimeout: Int = 3600,     // User Interaction MCP 默认 1 小时（用户交互需要较长等待时间）
        var jetbrainsMcpTimeout: Int = 60,             // JetBrains MCP 默认 60 秒
        var jetbrainsFileMcpTimeout: Int = 60,         // JetBrains File MCP 默认 60 秒
        var context7McpTimeout: Int = 60,              // Context7 MCP 默认 60 秒
        var terminalMcpTimeout: Int = 60,              // Terminal MCP 默认 60 秒
        var gitMcpTimeout: Int = 60,                   // Git MCP 默认 60 秒
        // MCP 后端启用范围（每个 MCP 单独配置，空 = 使用旧全局配置作为兼容）
        var userInteractionMcpBackends: String = "",
        var jetbrainsMcpBackends: String = "",
        var jetbrainsFileMcpBackends: String = "",
        var context7McpBackends: String = "",
        var terminalMcpBackends: String = "",
        var gitMcpBackends: String = "",
        // 旧版全局 MCP 后端配置（兼容）
        var mcpEnabledBackends: String = "all",

        // MCP 系统提示词（自定义，空字符串表示使用默认值）
        var userInteractionInstructions: String = "",
        var userInteractionInstructionsClaude: String = "",
        var userInteractionInstructionsCodex: String = "",
        var jetbrainsInstructions: String = "",
        var jetbrainsInstructionsClaude: String = "",
        var jetbrainsInstructionsCodex: String = "",
        var jetbrainsFileInstructions: String = "",
        var jetbrainsFileInstructionsClaude: String = "",
        var jetbrainsFileInstructionsCodex: String = "",
        var context7Instructions: String = "",
        var context7InstructionsClaude: String = "",
        var context7InstructionsCodex: String = "",
        var terminalInstructions: String = "",
        var terminalInstructionsClaude: String = "",
        var terminalInstructionsCodex: String = "",
        var gitInstructions: String = "",
        var gitInstructionsClaude: String = "",
        var gitInstructionsCodex: String = "",
        /** Git MCP Commit 语言 (en, zh, ja, ko, auto) */
        var gitCommitLanguage: String = "en",

        // Codex 模式下各 MCP 自动批准的工具列表（逗号分隔，空字符串表示使用默认值）
        var jetbrainsFileAutoApprovedTools: String = "",
        var jetbrainsLspAutoApprovedTools: String = "",
        var terminalAutoApprovedTools: String = "",
        var gitAutoApprovedTools: String = "",
        var userInteractionAutoApprovedTools: String = "",

        // Git Generate 功能配置
        var gitGenerateSystemPrompt: String = "",     // Git Generate 系统提示词
        var gitGenerateUserPrompt: String = "",       // Git Generate 用户提示词（运行时）
        var gitGenerateTools: String = "[]",          // Git Generate 允许的工具列表（JSON）
        var gitGenerateModel: String = "",            // Git Generate 使用的模型（空=使用默认模型）
        var gitGenerateSaveSession: Boolean = false,  // Git Generate 是否保存会话到历史（默认不保存）
        var gitGenerateEnabled: Boolean = false,      // Git Generate 是否启用
        var gitGenerateBackend: String = MCP_BACKEND_CLAUDE, // Git Generate 后端（claude/codex）
        var gitGenerateClaudeThinkingLevelId: String = "ultra", // Git Generate Claude 思考级别
        var gitGenerateCodexReasoningEffort: String = "xhigh",  // Git Generate Codex 推理强度

        // 默认启用 ByPass 权限（前端自动应用）
        var defaultBypassPermissions: Boolean = false,
        // 默认自动清理上下文（按后端分别配置）
        var claudeDefaultAutoCleanupContexts: Boolean = true,
        var codexDefaultAutoCleanupContexts: Boolean = true,
        var defaultBackendType: String = MCP_BACKEND_CLAUDE,

        // Claude CLI 可执行文件路径，空字符串表示使用系统 PATH
        var claudePath: String = "",
        // Node.js 可执行文件路径，空字符串表示使用系统 PATH（用于 Codex 等）
        var nodePath: String = "",
        var codexPath: String = "",
        var codexWebSearchEnabled: Boolean = false,
        var codexDefaultModelId: String = "gpt-5.4",
        var codexDefaultReasoningEffort: String = "xhigh",
        var codexDefaultReasoningSummary: String = "auto",
        var codexDefaultSandboxMode: String = "workspace-write",
        var codexCustomModels: String = "[]",

        // 默认模型（使用实际 modelId 存储）
        var defaultModel: String = "claude-opus-4-6",

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

        // Agent Teams 模式：default（不传环境变量）、enable（启用）、disable（禁用）
        var agentTeamsMode: String = "default",

        // 自定义模型列表（JSON 序列化）
        var customModels: String = "[]"
    )

    private var state = State()

    private val builtInClaudeModels = listOf(
        ModelInfo(
            modelId = "claude-haiku-4-5-20251001",
            displayName = "Haiku 4.5",
            isBuiltIn = true
        ),
        ModelInfo(
            modelId = "claude-opus-4-6",
            displayName = "Opus 4.6",
            isBuiltIn = true
        ),
        ModelInfo(
            modelId = "claude-sonnet-4-6",
            displayName = "Sonnet 4.6",
            isBuiltIn = true
        )
    )

    private val legacyClaudeModelAliases = mapOf(
        "HAIKU_45" to "claude-haiku-4-5-20251001",
        "claude-haiku-4-5-20250929" to "claude-haiku-4-5-20251001"
    )

    private fun normalizeClaudeModelId(rawModelId: String): String {
        return legacyClaudeModelAliases[rawModelId] ?: rawModelId
    }

    private fun migrateLegacyModelIds() {
        val normalizedDefault = normalizeClaudeModelId(state.defaultModel)
        if (normalizedDefault != state.defaultModel) {
            state.defaultModel = normalizedDefault
        }

        if (state.gitGenerateModel.isNotBlank()) {
            val normalizedGitModel = normalizeClaudeModelId(state.gitGenerateModel)
            if (normalizedGitModel != state.gitGenerateModel) {
                state.gitGenerateModel = normalizedGitModel
            }
        }
    }

    // 设置变更监听器
    private val changeListeners = mutableListOf<(AgentSettingsService) -> Unit>()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
        migrateLegacyModelIds()
        migrateOldExternalDirsConfig()  // 迁移旧版外部目录配置
        this.state.defaultBackendType = normalizeBackendType(this.state.defaultBackendType)
        this.state.gitGenerateBackend = normalizeGitGenerateBackend(this.state.gitGenerateBackend)
        this.state.gitGenerateClaudeThinkingLevelId = normalizeGitGenerateThinkingLevelId(this.state.gitGenerateClaudeThinkingLevelId)
        this.state.gitGenerateCodexReasoningEffort = normalizeCodexReasoningEffort(this.state.gitGenerateCodexReasoningEffort)
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

    private fun normalizeBackendType(value: String): String {
        return when (value.trim().lowercase()) {
            MCP_BACKEND_CODEX -> MCP_BACKEND_CODEX
            else -> MCP_BACKEND_CLAUDE
        }
    }

    private fun normalizeGitGenerateBackend(value: String): String {
        return normalizeBackendType(value)
    }

    private fun normalizeGitGenerateThinkingLevelId(value: String?): String {
        val normalized = value?.trim()?.lowercase().orEmpty().ifBlank { "ultra" }
        return getAllThinkingLevels().firstOrNull { it.id == normalized }?.id ?: "ultra"
    }

    var defaultBackendType: String
        get() = normalizeBackendType(state.defaultBackendType)
        set(value) { state.defaultBackendType = normalizeBackendType(value) }

    fun getDefaultBackendProvider(): AiAgentProvider {
        return if (defaultBackendType == MCP_BACKEND_CODEX) {
            AiAgentProvider.CODEX
        } else {
            AiAgentProvider.CLAUDE
        }
    }

    var enableUserInteractionMcp: Boolean
        get() = state.enableUserInteractionMcp
        set(value) { state.enableUserInteractionMcp = value }

    var enableJetBrainsMcp: Boolean
        get() = state.enableJetBrainsMcp
        set(value) { state.enableJetBrainsMcp = value }

    var enableJetBrainsFileMcp: Boolean
        get() = state.enableJetBrainsFileMcp
        set(value) { state.enableJetBrainsFileMcp = value }

    var jetbrainsFileDisableBuiltinTools: Boolean
        get() = state.jetbrainsFileDisableBuiltinTools
        set(value) { state.jetbrainsFileDisableBuiltinTools = value }

    var jetbrainsFileDisabledTools: String
        get() = state.jetbrainsFileDisabledTools
        set(value) { state.jetbrainsFileDisabledTools = value }

    /**
     * 获取 JetBrains File MCP 需要禁用的内置工具列表
     * @return 工具名称列表
     */
    fun getJetbrainsFileDisabledToolsList(): List<String> {
        return state.jetbrainsFileDisabledTools
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 设置 JetBrains File MCP 需要禁用的内置工具列表
     */
    fun setJetbrainsFileDisabledToolsList(tools: List<String>) {
        state.jetbrainsFileDisabledTools = tools.joinToString(",")
    }

    // ==================== MCP Auto-Approved Tools (Claude Code & Codex) ====================

    /**
     * 获取 JetBrains File MCP 的自动批准工具列表
     * 空字符串表示使用默认值
     */
    fun getJetbrainsFileAutoApprovedTools(): List<String> {
        val custom = state.jetbrainsFileAutoApprovedTools
        return if (custom.isBlank()) {
            McpAutoApprovedDefaults.JETBRAINS_FILE
        } else {
            custom.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun setJetbrainsFileAutoApprovedTools(tools: List<String>) {
        state.jetbrainsFileAutoApprovedTools = tools.joinToString(",")
    }

    /**
     * 获取 JetBrains LSP MCP 的自动批准工具列表
     */
    fun getJetbrainsLspAutoApprovedTools(): List<String> {
        val custom = state.jetbrainsLspAutoApprovedTools
        return if (custom.isBlank()) {
            McpAutoApprovedDefaults.JETBRAINS_LSP
        } else {
            custom.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun setJetbrainsLspAutoApprovedTools(tools: List<String>) {
        state.jetbrainsLspAutoApprovedTools = tools.joinToString(",")
    }

    /**
     * 获取 Terminal MCP 的自动批准工具列表
     */
    fun getTerminalAutoApprovedTools(): List<String> {
        val custom = state.terminalAutoApprovedTools
        return if (custom.isBlank()) {
            McpAutoApprovedDefaults.JETBRAINS_TERMINAL
        } else {
            custom.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun setTerminalAutoApprovedTools(tools: List<String>) {
        state.terminalAutoApprovedTools = tools.joinToString(",")
    }

    /**
     * 获取 Git MCP 的自动批准工具列表
     */
    fun getGitAutoApprovedTools(): List<String> {
        val custom = state.gitAutoApprovedTools
        return if (custom.isBlank()) {
            McpAutoApprovedDefaults.IDE_GIT
        } else {
            custom.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun setGitAutoApprovedTools(tools: List<String>) {
        state.gitAutoApprovedTools = tools.joinToString(",")
    }

    /**
     * 获取 User Interaction MCP 的自动批准工具列表
     */
    fun getUserInteractionAutoApprovedTools(): List<String> {
        val custom = state.userInteractionAutoApprovedTools
        return if (custom.isBlank()) {
            McpAutoApprovedDefaults.USER_INTERACTION
        } else {
            custom.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun setUserInteractionAutoApprovedTools(tools: List<String>) {
        state.userInteractionAutoApprovedTools = tools.joinToString(",")
    }

    // JetBrains File MCP 外部文件配置
    var jetbrainsFileAllowExternal: Boolean
        get() = state.jetbrainsFileAllowExternal
        set(value) { state.jetbrainsFileAllowExternal = value }

    var jetbrainsFileExternalRules: String
        get() = state.jetbrainsFileExternalRules
        set(value) { state.jetbrainsFileExternalRules = value }

    // 旧版配置属性（用于迁移，已废弃）
    @Suppress("DEPRECATION")
    @Deprecated("Use jetbrainsFileExternalRules instead")
    var jetbrainsFileExternalDir1: String
        get() = state.jetbrainsFileExternalDir1
        set(value) { state.jetbrainsFileExternalDir1 = value }

    @Suppress("DEPRECATION")
    @Deprecated("Use jetbrainsFileExternalRules instead")
    var jetbrainsFileExternalDir2: String
        get() = state.jetbrainsFileExternalDir2
        set(value) { state.jetbrainsFileExternalDir2 = value }

    @Suppress("DEPRECATION")
    @Deprecated("Use jetbrainsFileExternalRules instead")
    var jetbrainsFileExternalDir3: String
        get() = state.jetbrainsFileExternalDir3
        set(value) { state.jetbrainsFileExternalDir3 = value }

    /**
     * 获取外部路径规则列表
     */
    fun getExternalPathRules(): List<ExternalPathRule> {
        return try {
            Json.decodeFromString<List<ExternalPathRule>>(state.jetbrainsFileExternalRules)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置外部路径规则列表
     */
    fun setExternalPathRules(rules: List<ExternalPathRule>) {
        state.jetbrainsFileExternalRules = Json.encodeToString(rules)
    }

    /**
     * 添加外部路径规则
     */
    fun addExternalPathRule(rule: ExternalPathRule) {
        val rules = getExternalPathRules().toMutableList()
        // 避免重复添加相同路径和类型的规则
        if (rules.none { it.path == rule.path && it.type == rule.type }) {
            rules.add(rule)
            setExternalPathRules(rules)
        }
    }

    /**
     * 移除外部路径规则
     */
    fun removeExternalPathRule(index: Int) {
        val rules = getExternalPathRules().toMutableList()
        if (index in rules.indices) {
            rules.removeAt(index)
            setExternalPathRules(rules)
        }
    }

    /**
     * 获取 JetBrains File MCP 允许的外部目录列表（兼容旧接口）
     * @return Include 规则中的目录路径列表
     */
    fun getJetbrainsFileExternalDirs(): List<String> {
        if (!state.jetbrainsFileAllowExternal) return emptyList()
        return getExternalPathRules()
            .filter { it.type == ExternalPathRuleType.INCLUDE }
            .map { it.path }
    }

    /**
     * 检查文件路径是否在允许的范围内（项目内或允许的外部目录内）
     *
     * 规则优先级：
     * 1. 项目内文件始终允许
     * 2. 如果不允许外部文件访问，返回 false
     * 3. Exclude 规则优先于 Include 规则
     * 4. 如果没有任何规则，允许所有外部路径
     * 5. 如果只有 Exclude 规则，允许未被排除的路径
     * 6. 如果有 Include 规则，只允许匹配的路径
     *
     * @param filePath 文件的绝对路径
     * @param projectBasePath 项目根目录
     * @return true 如果文件在允许的范围内
     */
    fun isFilePathAllowed(filePath: String, projectBasePath: String): Boolean {
        val normalizedPath = java.io.File(filePath).canonicalPath.replace("\\", "/").lowercase()
        val normalizedProjectPath = java.io.File(projectBasePath).canonicalPath.replace("\\", "/").lowercase()

        // 项目内文件始终允许
        if (normalizedPath.startsWith(normalizedProjectPath)) {
            return true
        }

        // 如果不允许外部文件访问，返回 false
        if (!state.jetbrainsFileAllowExternal) {
            return false
        }

        val rules = getExternalPathRules()

        // 如果没有任何规则，允许所有外部路径
        if (rules.isEmpty()) {
            return true
        }

        // 先检查 Exclude 规则（优先级高）
        for (rule in rules.filter { it.type == ExternalPathRuleType.EXCLUDE }) {
            val rulePath = rule.path.replace("\\", "/").lowercase()
            if (normalizedPath.startsWith(rulePath) || normalizedPath.startsWith("$rulePath/")) {
                return false  // 在排除列表中
            }
        }

        // 检查 Include 规则
        val includeRules = rules.filter { it.type == ExternalPathRuleType.INCLUDE }

        // 如果没有 Include 规则（只有 Exclude 规则），允许未被排除的路径
        if (includeRules.isEmpty()) {
            return true
        }

        // 如果有 Include 规则，检查是否匹配
        for (rule in includeRules) {
            val rulePath = rule.path.replace("\\", "/").lowercase()
            if (normalizedPath.startsWith(rulePath) || normalizedPath.startsWith("$rulePath/")) {
                return true  // 在包含列表中
            }
        }

        return false  // 有 Include 规则但不匹配任何
    }

    /**
     * 迁移旧版配置到新版
     */
    @Suppress("DEPRECATION")
    fun migrateOldExternalDirsConfig() {
        val oldDirs = listOfNotNull(
            state.jetbrainsFileExternalDir1.takeIf { it.isNotBlank() },
            state.jetbrainsFileExternalDir2.takeIf { it.isNotBlank() },
            state.jetbrainsFileExternalDir3.takeIf { it.isNotBlank() }
        )

        if (oldDirs.isNotEmpty() && getExternalPathRules().isEmpty()) {
            // 将旧配置迁移为 Include 规则
            val newRules = oldDirs.map { ExternalPathRule(it, ExternalPathRuleType.INCLUDE) }
            setExternalPathRules(newRules)

            // 清空旧配置
            state.jetbrainsFileExternalDir1 = ""
            state.jetbrainsFileExternalDir2 = ""
            state.jetbrainsFileExternalDir3 = ""
        }
    }

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

    var userInteractionInstructionsClaude: String
        get() = state.userInteractionInstructionsClaude
        set(value) { state.userInteractionInstructionsClaude = value }

    var userInteractionInstructionsCodex: String
        get() = state.userInteractionInstructionsCodex
        set(value) { state.userInteractionInstructionsCodex = value }

    var jetbrainsInstructions: String
        get() = state.jetbrainsInstructions
        set(value) { state.jetbrainsInstructions = value }

    var jetbrainsInstructionsClaude: String
        get() = state.jetbrainsInstructionsClaude
        set(value) { state.jetbrainsInstructionsClaude = value }

    var jetbrainsInstructionsCodex: String
        get() = state.jetbrainsInstructionsCodex
        set(value) { state.jetbrainsInstructionsCodex = value }

    var jetbrainsFileInstructions: String
        get() = state.jetbrainsFileInstructions
        set(value) { state.jetbrainsFileInstructions = value }

    var jetbrainsFileInstructionsClaude: String
        get() = state.jetbrainsFileInstructionsClaude
        set(value) { state.jetbrainsFileInstructionsClaude = value }

    var jetbrainsFileInstructionsCodex: String
        get() = state.jetbrainsFileInstructionsCodex
        set(value) { state.jetbrainsFileInstructionsCodex = value }

    var context7Instructions: String
        get() = state.context7Instructions
        set(value) { state.context7Instructions = value }

    var context7InstructionsClaude: String
        get() = state.context7InstructionsClaude
        set(value) { state.context7InstructionsClaude = value }

    var context7InstructionsCodex: String
        get() = state.context7InstructionsCodex
        set(value) { state.context7InstructionsCodex = value }

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

    var terminalInstructionsClaude: String
        get() = state.terminalInstructionsClaude
        set(value) { state.terminalInstructionsClaude = value }

    var terminalInstructionsCodex: String
        get() = state.terminalInstructionsCodex
        set(value) { state.terminalInstructionsCodex = value }

    var terminalDefaultShell: String
        get() = state.terminalDefaultShell
        set(value) { state.terminalDefaultShell = value }

    var terminalAvailableShells: String
        get() = state.terminalAvailableShells
        set(value) { state.terminalAvailableShells = value }

    var terminalReadTimeout: Int
        get() = state.terminalReadTimeout
        set(value) { state.terminalReadTimeout = value }

    /** 获取 TerminalRead 超时时间（毫秒） */
    val terminalReadTimeoutMs: Long
        get() = state.terminalReadTimeout * 1000L

    var terminalDisableInteractive: Boolean
        get() = state.terminalDisableInteractive
        set(value) { state.terminalDisableInteractive = value }

    /**
     * 获取终端环境变量
     *
     * 如果启用了禁用交互式终端功能，则返回 TERM=dumb 等环境变量，
     * 用于禁止 less/more 等分页器和交互式命令。
     */
    fun getTerminalEnvVariables(): Map<String, String> {
        return if (state.terminalDisableInteractive) {
            mapOf(
                "TERM" to "dumb",        // 禁用大多数交互式功能
                "GIT_PAGER" to "cat",    // Git 专用：禁用 less
                "PAGER" to "cat"         // 通用分页器：禁用 less
            )
        } else {
            emptyMap()
        }
    }

    /**
     * 获取生效的默认 shell
     *
     * 如果未配置，则根据操作系统和实际安装情况返回默认值：
     * - Windows: 如果检测到 Git Bash 可用则使用 git-bash，否则使用 powershell
     * - Unix: bash
     */
    fun getEffectiveDefaultShell(): String {
        val configured = state.terminalDefaultShell
        if (configured.isNotBlank()) {
            return configured
        }
        // 未配置时，根据操作系统和实际安装情况返回默认值
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        return if (isWindows) {
            // Windows：检查 Git Bash 是否可用
            val installedShells = detectInstalledShells()
            if (installedShells.contains("git-bash")) {
                "git-bash"
            } else {
                // Git Bash 不可用时，fallback 到 powershell
                "powershell"
            }
        } else {
            "bash"
        }
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

    var gitInstructionsClaude: String
        get() = state.gitInstructionsClaude
        set(value) { state.gitInstructionsClaude = value }

    var gitInstructionsCodex: String
        get() = state.gitInstructionsCodex
        set(value) { state.gitInstructionsCodex = value }

    /** Git MCP Commit 语言 (en, zh, ja, ko, auto) */
    var gitCommitLanguage: String
        get() = state.gitCommitLanguage
        set(value) { state.gitCommitLanguage = value }

    var userInteractionMcpBackends: String
        get() = state.userInteractionMcpBackends
        set(value) { state.userInteractionMcpBackends = value }

    var jetbrainsMcpBackends: String
        get() = state.jetbrainsMcpBackends
        set(value) { state.jetbrainsMcpBackends = value }

    var context7McpBackends: String
        get() = state.context7McpBackends
        set(value) { state.context7McpBackends = value }

    var terminalMcpBackends: String
        get() = state.terminalMcpBackends
        set(value) { state.terminalMcpBackends = value }

    var gitMcpBackends: String
        get() = state.gitMcpBackends
        set(value) { state.gitMcpBackends = value }

    // ==================== MCP 超时配置 ====================

    /** User Interaction MCP 超时时间（秒），最小 1 秒 */
    var userInteractionMcpTimeout: Int
        get() = state.userInteractionMcpTimeout
        set(value) { state.userInteractionMcpTimeout = value.coerceAtLeast(1) }

    /** JetBrains MCP 超时时间（秒），最小 1 秒 */
    var jetbrainsMcpTimeout: Int
        get() = state.jetbrainsMcpTimeout
        set(value) { state.jetbrainsMcpTimeout = value.coerceAtLeast(1) }

    /** JetBrains File MCP 超时时间（秒），最小 1 秒 */
    var jetbrainsFileMcpTimeout: Int
        get() = state.jetbrainsFileMcpTimeout
        set(value) { state.jetbrainsFileMcpTimeout = value.coerceAtLeast(1) }

    /** Context7 MCP 超时时间（秒），最小 1 秒 */
    var context7McpTimeout: Int
        get() = state.context7McpTimeout
        set(value) { state.context7McpTimeout = value.coerceAtLeast(1) }

    /** Terminal MCP 超时时间（秒），最小 1 秒 */
    var terminalMcpTimeout: Int
        get() = state.terminalMcpTimeout
        set(value) { state.terminalMcpTimeout = value.coerceAtLeast(1) }

    /** Git MCP 超时时间（秒），最小 1 秒 */
    var gitMcpTimeout: Int
        get() = state.gitMcpTimeout
        set(value) { state.gitMcpTimeout = value.coerceAtLeast(1) }

    private fun normalizeMcpBackendKeys(keys: Set<String>): Set<String> {
        val normalized = keys.map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (normalized.contains(MCP_BACKEND_ALL)) {
            return setOf(MCP_BACKEND_ALL)
        }
        return normalized.intersect(setOf(MCP_BACKEND_CLAUDE, MCP_BACKEND_CODEX))
    }

    private fun parseBackendKeys(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        val parsed = try {
            json.decodeFromString<List<String>>(raw).toSet()
        } catch (_: Exception) {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
        return normalizeMcpBackendKeys(parsed)
    }

    private fun formatBackendKeys(keys: Set<String>): String {
        val normalized = normalizeMcpBackendKeys(keys)
        return json.encodeToString(normalized.sorted())
    }

    private fun resolveBackendKeys(raw: String): Set<String> {
        if (raw.isNotBlank()) {
            return parseBackendKeys(raw)
        }
        val fallback = getMcpEnabledBackendKeys()
        return if (fallback.isNotEmpty()) {
            fallback
        } else {
            setOf(MCP_BACKEND_ALL)
        }
    }

    fun getMcpEnabledBackendKeys(): Set<String> {
        return parseBackendKeys(state.mcpEnabledBackends.trim())
    }

    fun setMcpEnabledBackendKeys(keys: Set<String>) {
        val normalized = normalizeMcpBackendKeys(keys)
        state.mcpEnabledBackends = if (normalized.isEmpty()) {
            ""
        } else {
            json.encodeToString(normalized.sorted())
        }
    }

    fun getUserInteractionMcpBackendKeys(): Set<String> =
        resolveBackendKeys(state.userInteractionMcpBackends)

    fun setUserInteractionMcpBackendKeys(keys: Set<String>) {
        state.userInteractionMcpBackends = formatBackendKeys(keys)
    }

    fun getJetbrainsMcpBackendKeys(): Set<String> =
        resolveBackendKeys(state.jetbrainsMcpBackends)

    fun setJetbrainsMcpBackendKeys(keys: Set<String>) {
        state.jetbrainsMcpBackends = formatBackendKeys(keys)
    }

    fun getJetbrainsFileMcpBackendKeys(): Set<String> =
        resolveBackendKeys(state.jetbrainsFileMcpBackends)

    fun setJetbrainsFileMcpBackendKeys(keys: Set<String>) {
        state.jetbrainsFileMcpBackends = formatBackendKeys(keys)
    }

    fun getContext7McpBackendKeys(): Set<String> =
        resolveBackendKeys(state.context7McpBackends)

    fun setContext7McpBackendKeys(keys: Set<String>) {
        state.context7McpBackends = formatBackendKeys(keys)
    }

    fun getTerminalMcpBackendKeys(): Set<String> =
        resolveBackendKeys(state.terminalMcpBackends)

    fun setTerminalMcpBackendKeys(keys: Set<String>) {
        state.terminalMcpBackends = formatBackendKeys(keys)
    }

    fun getGitMcpBackendKeys(): Set<String> =
        resolveBackendKeys(state.gitMcpBackends)

    fun setGitMcpBackendKeys(keys: Set<String>) {
        state.gitMcpBackends = formatBackendKeys(keys)
    }

    fun toProviders(keys: Set<String>): Set<AiAgentProvider> {
        val normalized = normalizeMcpBackendKeys(keys)
        if (normalized.contains(MCP_BACKEND_ALL)) {
            return setOf(AiAgentProvider.CLAUDE, AiAgentProvider.CODEX)
        }
        val providers = mutableSetOf<AiAgentProvider>()
        if (normalized.contains(MCP_BACKEND_CLAUDE)) providers.add(AiAgentProvider.CLAUDE)
        if (normalized.contains(MCP_BACKEND_CODEX)) providers.add(AiAgentProvider.CODEX)
        return providers
    }

    fun getUserInteractionMcpProviders(): Set<AiAgentProvider> =
        toProviders(getUserInteractionMcpBackendKeys())

    fun getJetbrainsMcpProviders(): Set<AiAgentProvider> =
        toProviders(getJetbrainsMcpBackendKeys())

    fun getJetbrainsFileMcpProviders(): Set<AiAgentProvider> =
        toProviders(getJetbrainsFileMcpBackendKeys())

    fun getContext7McpProviders(): Set<AiAgentProvider> =
        toProviders(getContext7McpBackendKeys())

    fun getTerminalMcpProviders(): Set<AiAgentProvider> =
        toProviders(getTerminalMcpBackendKeys())

    fun getGitMcpProviders(): Set<AiAgentProvider> =
        toProviders(getGitMcpBackendKeys())

    fun getMcpEnabledProviders(): Set<AiAgentProvider> {
        val keys = getMcpEnabledBackendKeys()
        return toProviders(keys)
    }

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

    /** Git Generate 是否启用 */
    var gitGenerateEnabled: Boolean
        get() = state.gitGenerateEnabled
        set(value) { state.gitGenerateEnabled = value }

    /** Git Generate 后端类型 */
    var gitGenerateBackend: String
        get() = normalizeGitGenerateBackend(state.gitGenerateBackend)
        set(value) { state.gitGenerateBackend = normalizeGitGenerateBackend(value) }

    /** Git Generate Claude 思考级别 */
    var gitGenerateClaudeThinkingLevelId: String
        get() = normalizeGitGenerateThinkingLevelId(state.gitGenerateClaudeThinkingLevelId)
        set(value) { state.gitGenerateClaudeThinkingLevelId = normalizeGitGenerateThinkingLevelId(value) }

    /** Git Generate Codex 推理强度 */
    var gitGenerateCodexReasoningEffort: String
        get() = normalizeCodexReasoningEffort(state.gitGenerateCodexReasoningEffort)
        set(value) { state.gitGenerateCodexReasoningEffort = normalizeCodexReasoningEffort(value) }

    /** Git Generate 后端提供者 */
    fun getGitGenerateBackendProvider(): AiAgentProvider {
        return if (gitGenerateBackend == MCP_BACKEND_CODEX) {
            AiAgentProvider.CODEX
        } else {
            AiAgentProvider.CLAUDE
        }
    }

    /**
     * 获取 Git Generate 的有效模型 ID
     * 如果配置的模型不存在，则 fallback 到第一个内置模型
     */
    val effectiveGitGenerateModelId: String
        get() {
            val backend = gitGenerateBackend
            val configuredModelId = state.gitGenerateModel
            if (configuredModelId.isBlank()) {
                return if (backend == MCP_BACKEND_CODEX) {
                    effectiveCodexDefaultModelId
                } else {
                    effectiveDefaultModelId
                }
            }
            if (backend == MCP_BACKEND_CODEX) {
                val modelInfo = getCodexModelById(configuredModelId)
                return modelInfo?.modelId ?: getCodexBuiltInModels().first().modelId
            }
            val modelInfo = getModelById(normalizeClaudeModelId(configuredModelId))
            return modelInfo?.modelId ?: builtInClaudeModels.first().modelId
        }

    /** 获取 Git Generate Claude 思考级别（有效值） */
    val effectiveGitGenerateClaudeThinkingLevelId: String
        get() = normalizeGitGenerateThinkingLevelId(state.gitGenerateClaudeThinkingLevelId)

    /** 获取 Git Generate Claude 思考 token 上限 */
    val effectiveGitGenerateClaudeThinkingTokens: Int
        get() = getThinkingLevelById(effectiveGitGenerateClaudeThinkingLevelId)?.tokens ?: state.ultraTokens

    /** 获取 Git Generate Codex 推理强度（有效值） */
    val effectiveGitGenerateCodexReasoningEffort: String
        get() = normalizeCodexReasoningEffort(
            state.gitGenerateCodexReasoningEffort.ifBlank { state.codexDefaultReasoningEffort }
        )

    /** Git Generate 是否保存会话到历史 */
    var gitGenerateSaveSession: Boolean
        get() = state.gitGenerateSaveSession
        set(value) { state.gitGenerateSaveSession = value }

    private fun resolveBackendInstruction(
        backendKey: String,
        claudeValue: String,
        codexValue: String,
        legacyValue: String
    ): String {
        return when (backendKey.trim().lowercase()) {
            MCP_BACKEND_CLAUDE -> if (claudeValue.isNotBlank()) claudeValue else legacyValue
            MCP_BACKEND_CODEX -> if (codexValue.isNotBlank()) codexValue else legacyValue
            else -> legacyValue
        }
    }

    private fun resolveProviderInstruction(
        provider: AiAgentProvider?,
        claudeValue: String,
        codexValue: String,
        legacyValue: String
    ): String {
        return when (provider) {
            AiAgentProvider.CODEX -> if (codexValue.isNotBlank()) codexValue else legacyValue
            AiAgentProvider.CLAUDE -> if (claudeValue.isNotBlank()) claudeValue else legacyValue
            else -> legacyValue
        }
    }

    private fun resolveEffectiveInstruction(
        provider: AiAgentProvider?,
        claudeValue: String,
        codexValue: String,
        legacyValue: String,
        defaultValue: String
    ): String {
        val selected = resolveProviderInstruction(provider, claudeValue, codexValue, legacyValue).trim()
        return if (selected.isNotBlank()) selected else defaultValue
    }

    private fun resolveEffectiveInstructionForBackend(
        backendKey: String,
        claudeValue: String,
        codexValue: String,
        legacyValue: String,
        defaultValue: String
    ): String {
        val selected = resolveBackendInstruction(backendKey, claudeValue, codexValue, legacyValue).trim()
        return if (selected.isNotBlank()) selected else defaultValue
    }

    private fun buildInstructionsByBackendMap(
        claudeValue: String,
        codexValue: String,
        legacyValue: String,
        defaultValue: String
    ): Map<String, String> {
        val effectiveClaude = resolveEffectiveInstructionForBackend(
            MCP_BACKEND_CLAUDE,
            claudeValue,
            codexValue,
            legacyValue,
            defaultValue
        )
        val effectiveCodex = resolveEffectiveInstructionForBackend(
            MCP_BACKEND_CODEX,
            claudeValue,
            codexValue,
            legacyValue,
            defaultValue
        )
        val result = mutableMapOf<String, String>()
        if (effectiveClaude.isNotBlank()) result[MCP_BACKEND_CLAUDE] = effectiveClaude
        if (effectiveCodex.isNotBlank()) result[MCP_BACKEND_CODEX] = effectiveCodex
        return result
    }

    fun getUserInteractionInstructionsForBackend(backendKey: String): String =
        resolveBackendInstruction(
            backendKey,
            state.userInteractionInstructionsClaude,
            state.userInteractionInstructionsCodex,
            state.userInteractionInstructions
        )

    fun getJetbrainsInstructionsForBackend(backendKey: String): String =
        resolveBackendInstruction(
            backendKey,
            state.jetbrainsInstructionsClaude,
            state.jetbrainsInstructionsCodex,
            state.jetbrainsInstructions
        )

    fun getJetbrainsFileInstructionsForBackend(backendKey: String): String =
        resolveBackendInstruction(
            backendKey,
            state.jetbrainsFileInstructionsClaude,
            state.jetbrainsFileInstructionsCodex,
            state.jetbrainsFileInstructions
        )

    fun getContext7InstructionsForBackend(backendKey: String): String =
        resolveBackendInstruction(
            backendKey,
            state.context7InstructionsClaude,
            state.context7InstructionsCodex,
            state.context7Instructions
        )

    fun getTerminalInstructionsForBackend(backendKey: String): String =
        resolveBackendInstruction(
            backendKey,
            state.terminalInstructionsClaude,
            state.terminalInstructionsCodex,
            state.terminalInstructions
        )

    fun getGitInstructionsForBackend(backendKey: String): String =
        resolveBackendInstruction(
            backendKey,
            state.gitInstructionsClaude,
            state.gitInstructionsCodex,
            state.gitInstructions
        )

    fun getEffectiveUserInteractionInstructionsForProvider(provider: AiAgentProvider?): String =
        resolveEffectiveInstruction(
            provider,
            state.userInteractionInstructionsClaude,
            state.userInteractionInstructionsCodex,
            state.userInteractionInstructions,
            McpDefaults.USER_INTERACTION_INSTRUCTIONS
        )

    fun getEffectiveJetbrainsInstructionsForProvider(provider: AiAgentProvider?): String =
        resolveEffectiveInstruction(
            provider,
            state.jetbrainsInstructionsClaude,
            state.jetbrainsInstructionsCodex,
            state.jetbrainsInstructions,
            McpDefaults.JETBRAINS_INSTRUCTIONS
        )

    fun getEffectiveJetbrainsFileInstructionsForProvider(provider: AiAgentProvider?): String =
        resolveEffectiveInstruction(
            provider,
            state.jetbrainsFileInstructionsClaude,
            state.jetbrainsFileInstructionsCodex,
            state.jetbrainsFileInstructions,
            McpDefaults.JETBRAINS_FILE_INSTRUCTIONS
        )

    fun getEffectiveContext7InstructionsForProvider(provider: AiAgentProvider?): String =
        resolveEffectiveInstruction(
            provider,
            state.context7InstructionsClaude,
            state.context7InstructionsCodex,
            state.context7Instructions,
            McpDefaults.CONTEXT7_INSTRUCTIONS
        )

    fun getEffectiveTerminalInstructionsForProvider(provider: AiAgentProvider?): String =
        resolveEffectiveInstruction(
            provider,
            state.terminalInstructionsClaude,
            state.terminalInstructionsCodex,
            state.terminalInstructions,
            McpDefaults.TERMINAL_INSTRUCTIONS
        )

    fun getEffectiveGitInstructionsForProvider(provider: AiAgentProvider?): String =
        resolveEffectiveInstruction(
            provider,
            state.gitInstructionsClaude,
            state.gitInstructionsCodex,
            state.gitInstructions,
            McpDefaults.GIT_INSTRUCTIONS
        )

    fun getUserInteractionInstructionsByBackend(): Map<String, String> =
        buildInstructionsByBackendMap(
            state.userInteractionInstructionsClaude,
            state.userInteractionInstructionsCodex,
            state.userInteractionInstructions,
            McpDefaults.USER_INTERACTION_INSTRUCTIONS
        )

    fun getJetbrainsInstructionsByBackend(): Map<String, String> =
        buildInstructionsByBackendMap(
            state.jetbrainsInstructionsClaude,
            state.jetbrainsInstructionsCodex,
            state.jetbrainsInstructions,
            McpDefaults.JETBRAINS_INSTRUCTIONS
        )

    fun getJetbrainsFileInstructionsByBackend(): Map<String, String> =
        buildInstructionsByBackendMap(
            state.jetbrainsFileInstructionsClaude,
            state.jetbrainsFileInstructionsCodex,
            state.jetbrainsFileInstructions,
            McpDefaults.JETBRAINS_FILE_INSTRUCTIONS
        )

    fun getContext7InstructionsByBackend(): Map<String, String> =
        buildInstructionsByBackendMap(
            state.context7InstructionsClaude,
            state.context7InstructionsCodex,
            state.context7Instructions,
            McpDefaults.CONTEXT7_INSTRUCTIONS
        )

    fun getTerminalInstructionsByBackend(): Map<String, String> =
        buildInstructionsByBackendMap(
            state.terminalInstructionsClaude,
            state.terminalInstructionsCodex,
            state.terminalInstructions,
            McpDefaults.TERMINAL_INSTRUCTIONS
        )

    fun getGitInstructionsByBackend(): Map<String, String> =
        buildInstructionsByBackendMap(
            state.gitInstructionsClaude,
            state.gitInstructionsCodex,
            state.gitInstructions,
            McpDefaults.GIT_INSTRUCTIONS
        )

    /** 获取生效的 User Interaction MCP 提示词（自定义或默认） */
    val effectiveUserInteractionInstructions: String
        get() = state.userInteractionInstructions.ifBlank { McpDefaults.USER_INTERACTION_INSTRUCTIONS }

    /** 获取生效的 JetBrains MCP 提示词（自定义或默认） */
    val effectiveJetbrainsInstructions: String
        get() = state.jetbrainsInstructions.ifBlank { McpDefaults.JETBRAINS_INSTRUCTIONS }

    /** 获取生效的 JetBrains File MCP 提示词（自定义或默认） */
    val effectiveJetbrainsFileInstructions: String
        get() = state.jetbrainsFileInstructions.ifBlank { McpDefaults.JETBRAINS_FILE_INSTRUCTIONS }

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

    var claudeDefaultAutoCleanupContexts: Boolean
        get() = state.claudeDefaultAutoCleanupContexts
        set(value) { state.claudeDefaultAutoCleanupContexts = value }

    var codexDefaultAutoCleanupContexts: Boolean
        get() = state.codexDefaultAutoCleanupContexts
        set(value) { state.codexDefaultAutoCleanupContexts = value }

    var claudePath: String
        get() = state.claudePath
        set(value) { state.claudePath = value }

    var nodePath: String
        get() = state.nodePath
        set(value) { state.nodePath = value }

    var codexPath: String
        get() = state.codexPath
        set(value) { state.codexPath = value }

    var codexWebSearchEnabled: Boolean
        get() = state.codexWebSearchEnabled
        set(value) { state.codexWebSearchEnabled = value }

    var codexDefaultModelId: String
        get() = state.codexDefaultModelId
        set(value) { state.codexDefaultModelId = value }

    var codexDefaultReasoningEffort: String
        get() = normalizeCodexReasoningEffort(state.codexDefaultReasoningEffort)
        set(value) { state.codexDefaultReasoningEffort = normalizeCodexReasoningEffort(value) }

    var codexDefaultReasoningSummary: String
        get() = normalizeCodexReasoningSummary(state.codexDefaultReasoningSummary)
        set(value) { state.codexDefaultReasoningSummary = normalizeCodexReasoningSummary(value) }

    var codexDefaultSandboxMode: String
        get() = normalizeCodexSandboxMode(state.codexDefaultSandboxMode)
        set(value) { state.codexDefaultSandboxMode = normalizeCodexSandboxMode(value) }

    var defaultModel: String
        get() = state.defaultModel
        set(value) { state.defaultModel = value }

    var permissionMode: String
        get() = state.permissionMode
        set(value) { state.permissionMode = value }

    var includePartialMessages: Boolean
        get() = state.includePartialMessages
        set(value) { state.includePartialMessages = value }

    var agentTeamsMode: String
        get() = state.agentTeamsMode
        set(value) { state.agentTeamsMode = value }

    var customModelsJson: String
        get() = state.customModels
        set(value) { state.customModels = value }

    var codexCustomModelsJson: String
        get() = state.codexCustomModels
        set(value) { state.codexCustomModels = value }

    private fun normalizeCodexReasoningEffort(value: String?): String {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "minimal", "low", "medium", "high", "xhigh" -> normalized
            else -> "medium"
        }
    }

    private fun normalizeCodexReasoningSummary(value: String?): String {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "auto", "concise", "detailed", "none" -> normalized
            else -> "auto"
        }
    }

    private fun normalizeCodexSandboxMode(value: String?): String {
        val normalized = value?.trim()?.lowercase().orEmpty()
        return when (normalized) {
            "read-only", "workspace-write", "danger-full-access" -> normalized
            "full-access" -> "danger-full-access"
            else -> "workspace-write"
        }
    }

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
     * 获取 Codex 自定义模型列表
     */
    fun getCodexCustomModels(): List<CustomModelConfig> {
        return try {
            json.decodeFromString<List<CustomModelConfig>>(state.codexCustomModels)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 设置 Codex 自定义模型列表
     */
    fun setCodexCustomModels(models: List<CustomModelConfig>) {
        state.codexCustomModels = json.encodeToString(models)
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
        val removedModel = models.firstOrNull { it.id == id }
        models.removeIf { it.id == id }
        setCustomModels(models)
        // 如果删除的是当前默认模型，切换到第一个内置模型
        if (removedModel != null && state.defaultModel == removedModel.modelId) {
            state.defaultModel = builtInClaudeModels.first().modelId
        }
    }

    /**
     * 获取所有可用模型（内置 + 自定义）
     *
     * 返回统一的模型信息列表，包含 id, displayName, modelId
     */
    fun getAllAvailableModels(): List<ModelInfo> {
        val custom = getCustomModels().map {
            ModelInfo(
                modelId = it.modelId,
                displayName = it.displayName,
                isBuiltIn = false
            )
        }
        return builtInClaudeModels + custom
    }

    /**
     * 获取 Codex 内置模型列表
     */
    fun getCodexBuiltInModels(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                modelId = "gpt-5.4",
                displayName = "GPT-5.4",
                isBuiltIn = true
            ),
            ModelInfo(
                modelId = "gpt-5.3-codex",
                displayName = "GPT-5.3-Codex",
                isBuiltIn = true
            )
        )
    }

    /**
     * 获取 Codex 所有可用模型（内置 + 自定义）
     */
    fun getAllCodexModels(): List<ModelInfo> {
        val builtIn = getCodexBuiltInModels()
        val custom = getCodexCustomModels().map {
            ModelInfo(
                modelId = it.modelId,
                displayName = it.displayName,
                isBuiltIn = false
            )
        }
        return builtIn + custom
    }

    /**
     * 根据 ID 获取模型信息
     */
    fun getModelById(modelId: String): ModelInfo? {
        val normalizedModelId = normalizeClaudeModelId(modelId)
        builtInClaudeModels.find { it.modelId == normalizedModelId }?.let { return it }
        return getCustomModels()
            .find { it.modelId == normalizedModelId }
            ?.let {
                ModelInfo(
                    modelId = it.modelId,
                    displayName = it.displayName,
                    isBuiltIn = false
                )
            }
    }

    /**
     * 根据 ID 获取 Codex 模型信息
     */
    fun getCodexModelById(modelId: String): ModelInfo? {
        getCodexBuiltInModels().find { it.modelId == modelId }?.let { return it }
        return getCodexCustomModels().find { it.modelId == modelId }?.let {
            ModelInfo(
                modelId = it.modelId,
                displayName = it.displayName,
                isBuiltIn = false
            )
        }
    }

    /**
     * 获取当前默认模型的实际 modelId（支持自定义模型）
     */
    val effectiveDefaultModelId: String
        get() = getModelById(state.defaultModel)?.modelId ?: builtInClaudeModels.first().modelId

    /**
     * 获取当前 Codex 默认模型的 modelId
     */
    val effectiveCodexDefaultModelId: String
        get() = getCodexModelById(state.codexDefaultModelId)?.modelId
            ?: getCodexBuiltInModels().first().modelId

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

    // ========== 配置选项列表（用于前端下拉选择器） ==========

    /**
     * 获取 Codex 推理努力级别选项
     */
    fun getCodexReasoningEffortOptions(): List<OptionConfig> {
        val defaultId = state.codexDefaultReasoningEffort
        return listOf(
            OptionConfig("none", "None", "No reasoning", defaultId == "none"),
            OptionConfig("minimal", "Minimal", "Minimal reasoning", defaultId == "minimal"),
            OptionConfig("low", "Low", "Low reasoning", defaultId == "low"),
            OptionConfig("medium", "Medium", "Balanced reasoning", defaultId == "medium"),
            OptionConfig("high", "High", "High reasoning", defaultId == "high"),
            OptionConfig("xhigh", "Extra High", "Extra high reasoning", defaultId == "xhigh")
        )
    }

    /**
     * 获取 Codex 推理总结模式选项
     */
    fun getCodexReasoningSummaryOptions(): List<OptionConfig> {
        val defaultId = state.codexDefaultReasoningSummary
        return listOf(
            OptionConfig("auto", "Auto", "Automatic summary", defaultId == "auto"),
            OptionConfig("concise", "Concise", "Brief summary", defaultId == "concise"),
            OptionConfig("detailed", "Detailed", "Full summary", defaultId == "detailed"),
            OptionConfig("none", "None", "No summary", defaultId == "none")
        )
    }

    /**
     * 获取 Codex 沙盒模式选项
     */
    fun getCodexSandboxModeOptions(): List<OptionConfig> {
        val defaultId = state.codexDefaultSandboxMode
        return listOf(
            OptionConfig("read-only", "Read Only", "Read-only access", defaultId == "read-only"),
            OptionConfig("workspace-write", "Workspace Write", "Write to workspace only", defaultId == "workspace-write"),
            OptionConfig("danger-full-access", "Full Access", "Full file system access (dangerous)", defaultId == "danger-full-access")
        )
    }

    /**
     * 获取权限模式选项
     */
    fun getPermissionModeOptions(): List<OptionConfig> {
        val defaultId = state.permissionMode
        return listOf(
            OptionConfig("default", "Default", "Normal permission checks", defaultId == "default"),
            OptionConfig("acceptEdits", "Accept Edits", "Auto-accept file edits", defaultId == "acceptEdits"),
            OptionConfig("plan", "Plan Mode", "Plan before execution", defaultId == "plan"),
            OptionConfig("bypassPermissions", "Bypass", "Skip all permission checks", defaultId == "bypassPermissions")
        )
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
        const val MCP_BACKEND_ALL = "all"
        const val MCP_BACKEND_CLAUDE = "claude"
        const val MCP_BACKEND_CODEX = "codex"

        @JvmStatic
        fun getInstance(): AgentSettingsService = service()

        // 委托给 EnvironmentDetection 对象
        fun detectClaudeInfo(): ClaudeCliInfo? = EnvironmentDetection.detectClaudeInfo()
        fun detectClaudePath(): String = EnvironmentDetection.detectClaudePath()
        fun detectNodeInfo(): NodeInfo? = EnvironmentDetection.detectNodeInfo()
        fun detectCodexInfo(): CodexInfo? = EnvironmentDetection.detectCodexInfo()
        fun detectNodePath(): String = EnvironmentDetection.detectNodePath()
        fun detectCodexPath(): String = EnvironmentDetection.detectCodexPath()
    }
}
