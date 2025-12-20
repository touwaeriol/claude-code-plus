package com.asakii.server.config

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.claude.agent.sdk.types.HookEvent
import com.asakii.claude.agent.sdk.types.HookMatcher

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
    // Claude CLI settings.json 路径，null 时不指定（CLI 会自动查找 ~/.claude/settings.json）
    val settings: String? = null,
    // 集成 MCP 服务器启用配置
    val enableUserInteractionMcp: Boolean = true,
    val enableJetBrainsMcp: Boolean = true,
    val enableContext7Mcp: Boolean = false,
    val context7ApiKey: String? = null,
    val enableTerminalMcp: Boolean = false,
    // MCP 服务器配置（从资源文件加载，由 plugin 模块传入）
    val mcpServersConfig: List<McpServerConfig> = emptyList(),
    // MCP 系统提示词（由 plugin 模块加载并传入）
    val mcpInstructions: String? = null,
    // 思考配置
    val defaultThinkingLevel: String = "HIGH",  // 默认思考等级：OFF, LOW, MEDIUM, HIGH, VERY_HIGH, CUSTOM
    val defaultThinkingTokens: Int = 8192,      // 默认思考 token 数量
    // IDEA 文件同步 hooks（由 jetbrains-plugin 提供）
    // PRE_TOOL_USE: 保存 IDEA 中的文件到磁盘
    // POST_TOOL_USE: 重新加载文件到 IDEA
    val ideaFileSyncHooks: Map<HookEvent, List<HookMatcher>>? = null
)

/**
 * MCP 服务器配置
 */
data class McpServerConfig(
    val name: String,
    val type: String,  // "http" or "stdio"
    val enabled: Boolean,
    // HTTP 类型配置
    val url: String? = null,
    val headers: Map<String, String>? = null,
    // stdio 类型配置
    val command: String? = null,
    val args: List<String>? = null,
    val env: Map<String, String>? = null,
    // 描述
    val description: String? = null,
    // 自定义系统提示词（仅用于自定义 MCP 服务器）
    val instructions: String? = null
)

/**
 * Codex 相关默认配置
 */
data class CodexDefaults(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val sandboxMode: String? = null
)


































