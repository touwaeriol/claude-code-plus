package com.asakii.server.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 强类型 Claude settings 映射。
 *
 * 对应官方文档：
 * https://code.claude.com/docs/en/settings#available-settings
 *
 * 所有字段都带有默认值，用于在没有任何配置文件时给出完整的配置对象。
 */
@Serializable
data class ClaudeSettings(
    // General settings
    val apiKeyHelper: String? = null,
    val cleanupPeriodDays: Int = 30,
    val companyAnnouncements: List<String> = emptyList(),

    /**
     * 环境变量映射，作用于所有会话。
     *
     * 例如 MAX_THINKING_TOKENS 等。
     */
    val env: Map<String, String> = emptyMap(),

    val includeCoAuthoredBy: Boolean = true,

    // Permissions & hooks
    val permissions: PermissionsConfig? = null,
    val hooks: Map<String, HookConfig> = emptyMap(),
    val disableAllHooks: Boolean = false,

    // Model / output
    val model: String? = null,
    val statusLine: StatusLineConfig? = null,
    val outputStyle: String? = null,

    // Login / org
    val forceLoginMethod: String? = null,
    val forceLoginOrgUUID: String? = null,

    // MCP/project integration
    val enableAllProjectMcpServers: Boolean? = null,
    val enabledMcpjsonServers: List<String> = emptyList(),
    val disabledMcpjsonServers: List<String> = emptyList(),
    val allowedMcpServers: List<McpServerRule> = emptyList(),
    val deniedMcpServers: List<McpServerRule> = emptyList(),

    // AWS helpers
    val awsAuthRefresh: String? = null,
    val awsCredentialExport: String? = null,

    // Sandboxing（按官方文档分组，具体键在 SandboxSettings 中建模）
    val sandbox: SandboxSettings = SandboxSettings()
)

/**
 * 权限配置，结构参考官方 permissions 文档。
 */
@Serializable
data class PermissionsConfig(
    val allow: List<String> = emptyList(),
    val ask: List<String> = emptyList(),
    val deny: List<String> = emptyList(),
    val additionalDirectories: List<String> = emptyList(),
    val defaultMode: String? = null,
    val disableBypassPermissionsMode: String? = null
)

/**
 * Hook 配置，仅保留通用结构，具体 payload 交给下游解释。
 */
@Serializable
data class HookConfig(
    val command: String? = null,
    val type: String? = null
)

/**
 * 状态栏配置，对应文档中的 statusLine 字段。
 */
@Serializable
data class StatusLineConfig(
    val type: String,
    val command: String? = null
)

/**
 * MCP 服务器规则（简化为名称字段，满足当前使用场景即可）。
 */
@Serializable
data class McpServerRule(
    val serverName: String? = null
)

/**
 * Sandboxing 配置。
 *
 * 文档中 Sandbox settings 表中的 key 以此对象中的字段建模。
 */
@Serializable
data class SandboxSettings(
    val enabled: Boolean = false,
    val autoAllowBashIfSandboxed: Boolean = true,
    val excludedCommands: List<String> = emptyList(),
    val allowUnsandboxedCommands: Boolean = true,
    val networkAllowUnixSockets: List<String> = emptyList(),
    val networkAllowLocalBinding: Boolean = false,
    val networkHttpProxyPort: Int? = null
)





















