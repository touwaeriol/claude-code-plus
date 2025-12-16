package com.asakii.server.config

import com.asakii.ai.agent.sdk.AiAgentProvider

/**
 * AI Agent 服务配置
 *
 * 所有配置项由外部传入（如 IDEA 设置），不再从环境变量读取。
 */
data class AiAgentServiceConfig(
    val defaultProvider: AiAgentProvider = AiAgentProvider.CLAUDE,
    val defaultModel: String? = null,
    val defaultSystemPrompt: String? = null,
    val claude: ClaudeDefaults = ClaudeDefaults(),
    val codex: CodexDefaults = CodexDefaults()
)

/**
 * Claude 相关默认配置
 */
data class ClaudeDefaults(
    val dangerouslySkipPermissions: Boolean = false,
    val allowDangerouslySkipPermissions: Boolean = true,
    val includePartialMessages: Boolean = true,
    val permissionMode: String? = null,
    // Node.js 可执行文件路径，null 时使用系统 PATH 中的 "node"
    val nodePath: String? = null,
    // 集成 MCP 服务器启用配置
    val enableUserInteractionMcp: Boolean = true,
    val enableJetBrainsMcp: Boolean = true
)

/**
 * Codex 相关默认配置
 */
data class CodexDefaults(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val sandboxMode: String? = null
)

































