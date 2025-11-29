package com.asakii.server.config

import com.asakii.ai.agent.sdk.AiAgentProvider

data class AiAgentServiceConfig(
    val defaultProvider: AiAgentProvider = resolveProvider(),
    val defaultModel: String? = System.getenv("AI_AGENT_MODEL"),
    val defaultSystemPrompt: String? = System.getenv("AI_AGENT_SYSTEM_PROMPT"),
    val claude: ClaudeDefaults = ClaudeDefaults(),
    val codex: CodexDefaults = CodexDefaults()
)

data class ClaudeDefaults(
    // 默认启用跳过权限检查，便于开发时自动执行工具调用
    // 可通过环境变量 AI_AGENT_CLAUDE_SKIP_PERMISSIONS=false 关闭
    val dangerouslySkipPermissions: Boolean = envFlag("AI_AGENT_CLAUDE_SKIP_PERMISSIONS", defaultValue = true),
    val allowDangerouslySkipPermissions: Boolean = envFlag("AI_AGENT_CLAUDE_ALLOW_SKIP", defaultValue = true),
    val includePartialMessages: Boolean = envFlag("AI_AGENT_CLAUDE_INCLUDE_PARTIALS", defaultValue = true),
    val permissionMode: String? = System.getenv("AI_AGENT_CLAUDE_PERMISSION_MODE")
)

data class CodexDefaults(
    val baseUrl: String? = System.getenv("AI_AGENT_CODEX_BASE_URL"),
    val apiKey: String? = System.getenv("AI_AGENT_CODEX_API_KEY"),
    val sandboxMode: String? = System.getenv("AI_AGENT_CODEX_SANDBOX_MODE")
)

private fun resolveProvider(): AiAgentProvider {
    val override = System.getenv("AI_AGENT_PROVIDER")?.uppercase()
    return override
        ?.runCatching { AiAgentProvider.valueOf(this) }
        ?.getOrNull()
        ?: AiAgentProvider.CLAUDE
}

private fun envFlag(name: String, defaultValue: Boolean = false): Boolean {
    return System.getenv(name)?.equals("true", ignoreCase = true) ?: defaultValue
}





























