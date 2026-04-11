package com.asakii.server.config

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.claude.agent.sdk.types.HookEvent
import com.asakii.claude.agent.sdk.types.HookMatcher

/**
 * 自定义模型配置（服务端传输用）
 */
data class CustomModelInfo(
    val displayName: String,  // 显示名称
    val modelId: String       // 实际模型 ID
)

/**
 * AI Agent 服务配置
 *
 * 所有配置项由外部传入（如 IDEA 设置），不再从环境变量读取。
 */
data class AiAgentServiceConfig(
    val defaultProvider: AiAgentProvider = AiAgentProvider.CLAUDE,
    val defaultModel: String = "claude-opus-4-6",
    val defaultSystemPrompt: String? = null,
    val claude: ClaudeDefaults = ClaudeDefaults(),
    val codex: CodexDefaults = CodexDefaults(),
    val customModels: List<CustomModelInfo> = emptyList(),  // Claude ???????
    val codexCustomModels: List<CustomModelInfo> = emptyList(),  // Codex ???????
    // MCP ????????? = ?????
    val mcpEnabledBackends: Set<AiAgentProvider> = setOf(
        AiAgentProvider.CLAUDE,
        AiAgentProvider.CODEX
    )
)

/**
 * Claude 相关默认配置
 */
data class ClaudeDefaults(
    val dangerouslySkipPermissions: Boolean = false,
    val defaultAutoCleanupContexts: Boolean = true,
    val allowDangerouslySkipPermissions: Boolean = true,
    val includePartialMessages: Boolean = true,
    val permissionMode: String? = null,
    // Claude CLI 可执行文件路径，null 时自动检测系统 PATH
    val claudePath: String? = null,
    // Node.js 可执行文件路径，null 时使用系统 PATH 中的 "node"（用于 Codex 等）
    val nodePath: String? = null,
    // Claude CLI settings.json 路径，null 时不指定（CLI 会自动查找 ~/.claude/settings.json）
    val settings: String? = null,
    // 集成 MCP 服务器启用配置
    val enableUserInteractionMcp: Boolean = true,
    val enableJetBrainsMcp: Boolean = true,
    val enableJetBrainsFileMcp: Boolean = true,
    val enableContext7Mcp: Boolean = false,
    val context7ApiKey: String? = null,
    val enableTerminalMcp: Boolean = false,
    val enableGitMcp: Boolean = false,
    val userInteractionMcpBackends: Set<AiAgentProvider> = setOf(AiAgentProvider.CLAUDE, AiAgentProvider.CODEX),
    val jetbrainsMcpBackends: Set<AiAgentProvider> = setOf(AiAgentProvider.CLAUDE, AiAgentProvider.CODEX),
    val jetbrainsFileMcpBackends: Set<AiAgentProvider> = setOf(AiAgentProvider.CLAUDE, AiAgentProvider.CODEX),
    val context7McpBackends: Set<AiAgentProvider> = setOf(AiAgentProvider.CLAUDE, AiAgentProvider.CODEX),
    val terminalMcpBackends: Set<AiAgentProvider> = setOf(AiAgentProvider.CLAUDE, AiAgentProvider.CODEX),
    val gitMcpBackends: Set<AiAgentProvider> = setOf(AiAgentProvider.CLAUDE, AiAgentProvider.CODEX),
    val userInteractionInstructionsByBackend: Map<String, String>? = null,
    // User Interaction MCP 超时（秒）
    val userInteractionMcpTimeoutSec: Int? = null,
    // MCP 服务器配置（从资源文件加载，由 plugin 模块传入）
    val mcpServersConfig: List<McpServerConfig> = emptyList(),
    // MCP 系统提示词（由 plugin 模块加载并传入）
    val mcpInstructions: String? = null,
    // 思考配置
    val defaultThinkingLevel: String = "HIGH",  // 默认思考等级：OFF, LOW, MEDIUM, HIGH, VERY_HIGH, CUSTOM
    val defaultThinkingTokens: Int = 8192,      // 默认思考 token 数量
    // Agent Teams 模式: "default"(不传环境变量), "enable"(启用), "disable"(禁用)
    val agentTeamsMode: String = "default",
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
    val instructions: String? = null,
    // 按后端区分的系统提示词（claude/codex），优先级高于 instructions
    val instructionsByBackend: Map<String, String>? = null,
    // 允许启用的后端（空集表示对所有后端禁用）
    val enabledBackends: Set<AiAgentProvider>? = null
)

/**
 * Codex 相关默认配置
 */
data class CodexDefaults(
    val binaryPath: String? = null,
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val sandboxMode: String? = null,
    val defaultReasoningEffort: String? = null,
    val defaultReasoningSummary: String? = null,
    val webSearchEnabled: Boolean? = null,
    val defaultModelId: String? = "gpt-5.4",
    val defaultAutoCleanupContexts: Boolean = true
)


































