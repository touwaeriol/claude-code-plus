package com.asakii.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

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
        // MCP 服务器启用配置（放在最前面）
        var enableUserInteractionMcp: Boolean = true,  // 用户交互 MCP（AskUserQuestion 工具）
        var enableJetBrainsMcp: Boolean = true,        // JetBrains IDE MCP（IDE 索引工具）

        // 默认启用 ByPass 权限（前端自动应用）
        var defaultBypassPermissions: Boolean = false,

        // Node.js 可执行文件路径，空字符串表示使用系统 PATH
        var nodePath: String = "",

        // 默认模型（使用枚举名称存储，如 "OPUS_45"）
        var defaultModel: String = DefaultModel.OPUS_45.name,

        // 默认权限模式：default, acceptEdits, plan, bypassPermissions, dontAsk
        var permissionMode: String = "default",

        // 是否包含部分消息
        var includePartialMessages: Boolean = true
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

    companion object {
        @JvmStatic
        fun getInstance(): AgentSettingsService = service()

        /**
         * 自动检测系统中的 Node.js 路径
         * @return Node.js 可执行文件路径，未找到返回空字符串
         */
        fun detectNodePath(): String {
            val isWindows = System.getProperty("os.name").lowercase().contains("win")

            // 1. 尝试通过系统命令查找
            try {
                val command = if (isWindows) arrayOf("cmd", "/c", "where", "node") else arrayOf("which", "node")
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
    }
}
