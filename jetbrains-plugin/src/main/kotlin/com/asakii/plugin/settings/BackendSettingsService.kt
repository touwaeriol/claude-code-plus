package com.asakii.plugin.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 后端类型枚举
 */
enum class BackendType(val displayName: String, val icon: String) {
    CLAUDE("Claude", "🤖"),
    CODEX("Codex", "🔧");

    companion object {
        fun fromName(name: String?): BackendType? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * 后端可用性状态
 */
@Serializable
data class BackendAvailability(
    val type: String,
    val available: Boolean,
    val reason: String? = null
)

/**
 * 后端配置 DTO（用于推送到前端）
 */
@Serializable
data class BackendConfigDto(
    val type: String,
    val enabled: Boolean,
    val modelId: String?,
    val modelProvider: String? = null,
    val sandboxMode: String? = null,
    val thinkingEnabled: Boolean = false,
    val thinkingTokenBudget: Int? = null,
    val reasoningEffort: String? = null,
    val reasoningSummary: String? = null
)

/**
 * 统一后端设置服务
 *
 * 管理 Claude 和 Codex 两个后端的配置，提供：
 * - 后端可用性检测
 * - 配置推送到前端
 * - 设置变更事件监听
 */
@State(
    name = "BackendSettings",
    storages = [Storage("backend-settings.xml")]
)
@Service(Service.Level.PROJECT)
class BackendSettingsService(private val project: Project) : PersistentStateComponent<BackendSettingsService.State> {

    data class State(
        // 默认后端类型
        var defaultBackend: String = BackendType.CLAUDE.name,

        // Claude 配置
        var claudeEnabled: Boolean = true,
        var claudeModelId: String = "claude-sonnet-4-6",
        var claudeThinkingEnabled: Boolean = true,
        var claudeThinkingTokenBudget: Int = 8096,
        var claudeIncludePartialMessages: Boolean = true,

        // Codex 配置
        var codexEnabled: Boolean = false,
        var codexModelId: String = "gpt-5.2-codex",
        var codexModelProvider: String = "openai",
        var codexSandboxMode: String = "workspace-write",
        var codexReasoningEffort: String = "xhigh",
        var codexReasoningSummary: String = "auto"
    )

    private var state = State()
    private val changeListeners = mutableListOf<(BackendSettingsService) -> Unit>()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    // ==================== 监听器管理 ====================

    /**
     * 添加设置变更监听器
     */
    fun addChangeListener(listener: (BackendSettingsService) -> Unit) {
        changeListeners.add(listener)
    }

    /**
     * 移除设置变更监听器
     */
    fun removeChangeListener(listener: (BackendSettingsService) -> Unit) {
        changeListeners.remove(listener)
    }

    /**
     * 通知所有监听器设置已变更
     */
    fun notifyChange() {
        changeListeners.forEach { it(this) }
    }

    // ==================== 后端可用性检测 ====================

    /**
     * 检查 Claude 后端是否可用
     *
     * Claude 后端始终可用（使用 SDK）
     */
    fun isClaudeAvailable(): Boolean {
        return state.claudeEnabled
    }

    /**
     * 检查 Codex 后端是否可用
     *
     * 检查 Codex 二进制文件是否存在且可执行
     */
    fun isCodexAvailable(): Boolean {
        if (!state.codexEnabled) return false

        val codexSettings = CodexSettings.getInstance(project)
        return codexSettings.isValid()
    }

    /**
     * 获取所有后端的可用性状态
     */
    fun getBackendAvailability(): List<BackendAvailability> {
        val result = mutableListOf<BackendAvailability>()

        // Claude
        result.add(BackendAvailability(
            type = BackendType.CLAUDE.name.lowercase(),
            available = isClaudeAvailable(),
            reason = if (!state.claudeEnabled) "Claude is disabled" else null
        ))

        // Codex
        val codexSettings = CodexSettings.getInstance(project)
        val codexReason = when {
            !state.codexEnabled -> "Codex is disabled"
            codexSettings.binaryPath.isEmpty() -> "Codex binary path not configured"
            !codexSettings.isValid() -> "Codex binary not found or not executable"
            else -> null
        }
        result.add(BackendAvailability(
            type = BackendType.CODEX.name.lowercase(),
            available = isCodexAvailable(),
            reason = codexReason
        ))

        return result
    }

    /**
     * 获取可用性 JSON（用于 HTTP API）
     */
    fun getBackendAvailabilityJson(): String {
        return json.encodeToString(getBackendAvailability())
    }

    // ==================== 配置获取 ====================

    /**
     * 获取默认后端类型
     */
    fun getDefaultBackend(): BackendType {
        return BackendType.fromName(state.defaultBackend) ?: BackendType.CLAUDE
    }

    /**
     * 设置默认后端类型
     */
    fun setDefaultBackend(type: BackendType) {
        state.defaultBackend = type.name
        notifyChange()
    }

    /**
     * 获取 Claude 后端配置 DTO
     */
    fun getClaudeConfigDto(): BackendConfigDto {
        return BackendConfigDto(
            type = "claude",
            enabled = state.claudeEnabled,
            modelId = state.claudeModelId,
            thinkingEnabled = state.claudeThinkingEnabled,
            thinkingTokenBudget = state.claudeThinkingTokenBudget
        )
    }

    /**
     * 获取 Codex 后端配置 DTO
     */
    fun getCodexConfigDto(): BackendConfigDto {
        val codexSettings = CodexSettings.getInstance(project)
        return BackendConfigDto(
            type = "codex",
            enabled = state.codexEnabled,
            modelId = state.codexModelId,
            modelProvider = codexSettings.modelProvider.lowercase(),
            sandboxMode = state.codexSandboxMode,
            reasoningEffort = state.codexReasoningEffort,
            reasoningSummary = state.codexReasoningSummary
        )
    }

    /**
     * 获取所有后端配置（用于推送到前端）
     */
    fun getAllConfigsJson(): String {
        val configs = mapOf(
            "defaultBackend" to state.defaultBackend.lowercase(),
            "claude" to getClaudeConfigDto(),
            "codex" to getCodexConfigDto(),
            "availability" to getBackendAvailability()
        )
        return json.encodeToString(configs)
    }

    // ==================== 配置更新 ====================

    /**
     * 更新 Claude 配置
     */
    fun updateClaudeConfig(
        enabled: Boolean? = null,
        modelId: String? = null,
        thinkingEnabled: Boolean? = null,
        thinkingTokenBudget: Int? = null,
        includePartialMessages: Boolean? = null
    ) {
        enabled?.let { state.claudeEnabled = it }
        modelId?.let { state.claudeModelId = it }
        thinkingEnabled?.let { state.claudeThinkingEnabled = it }
        thinkingTokenBudget?.let { state.claudeThinkingTokenBudget = it }
        includePartialMessages?.let { state.claudeIncludePartialMessages = it }
        notifyChange()
    }

    /**
     * 更新 Codex 配置
     */
    fun updateCodexConfig(
        enabled: Boolean? = null,
        modelId: String? = null,
        modelProvider: String? = null,
        sandboxMode: String? = null,
        reasoningEffort: String? = null,
        reasoningSummary: String? = null
    ) {
        enabled?.let { state.codexEnabled = it }
        modelId?.let { state.codexModelId = it }
        modelProvider?.let { state.codexModelProvider = it }
        sandboxMode?.let { state.codexSandboxMode = it }
        reasoningEffort?.let { state.codexReasoningEffort = it }
        reasoningSummary?.let { state.codexReasoningSummary = it }
        notifyChange()
    }

    // ==================== 便捷属性 ====================

    var defaultBackend: String
        get() = state.defaultBackend
        set(value) {
            state.defaultBackend = value
            notifyChange()
        }

    var claudeEnabled: Boolean
        get() = state.claudeEnabled
        set(value) {
            state.claudeEnabled = value
            notifyChange()
        }

    var codexEnabled: Boolean
        get() = state.codexEnabled
        set(value) {
            state.codexEnabled = value
            notifyChange()
        }

    companion object {
        fun getInstance(project: Project): BackendSettingsService {
            return project.service<BackendSettingsService>()
        }
    }
}
