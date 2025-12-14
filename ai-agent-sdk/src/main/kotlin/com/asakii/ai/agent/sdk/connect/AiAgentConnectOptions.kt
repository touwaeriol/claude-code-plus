package com.asakii.ai.agent.sdk.connect

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.claude.agent.sdk.types.ClaudeAgentOptions
import com.asakii.codex.agent.sdk.CodexClientOptions
import com.asakii.codex.agent.sdk.ThreadOptions

/**
 * 统一的会话连接参数。
 *
 * 该结构抽象了 Claude 与 Codex 在创建会话时需要的共同字段，并保留了
 * provider 专属的扩展位，便于上层只维护一套 connect() 调用。
 */
data class AiAgentConnectOptions(
    val provider: AiAgentProvider,
    val model: String? = null,
    val systemPrompt: Any? = null,
    val initialPrompt: String? = null,
    val sessionId: String? = null,
    /**
     * 跨 SDK 的统一“恢复会话”参数：
     * - Claude：映射到 resume。
     * - Codex：映射到 threadId。
     */
    val resumeSessionId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val claude: ClaudeOverrides = ClaudeOverrides(),
    val codex: CodexOverrides = CodexOverrides()
)

/**
 * Claude 专属的 connect 扩展位。
 */
data class ClaudeOverrides(
    val options: ClaudeAgentOptions? = null
)

/**
 * Codex 专属的 connect 扩展位。
 */
data class CodexOverrides(
    val clientOptions: CodexClientOptions? = null,
    val threadOptions: ThreadOptions? = null
)

/**
 * 归一化后的连接上下文，供内部真正发起 connect 时使用。
 */
data class AiAgentConnectContext(
    val provider: AiAgentProvider,
    val initialPrompt: String?,
    val sessionId: String?,
    val metadata: Map<String, String>,
    val claudeOptions: ClaudeAgentOptions? = null,
    val codexClientOptions: CodexClientOptions? = null,
    val codexThreadOptions: ThreadOptions? = null,
    val codexThreadId: String? = null
)

/**
 * 根据 provider 将 connect 参数归一化为底层 SDK 所需格式。
 */
fun AiAgentConnectOptions.normalize(): AiAgentConnectContext = when (provider) {
    AiAgentProvider.CLAUDE -> normalizeForClaude()
    AiAgentProvider.CODEX -> normalizeForCodex()
}

private fun AiAgentConnectOptions.normalizeForClaude(): AiAgentConnectContext {
    val base = claude.options ?: ClaudeAgentOptions()

    // 🔍 调试日志：检查 base 中的 canUseTool
    println("🔍 [normalizeForClaude] base.canUseTool=${base.canUseTool != null}, base.permissionMode=${base.permissionMode}")

    // 注意：copy() 会保留所有未显式指定的字段（包括 mcpServers、permissionPromptToolName 等）
    // print = true: 必须启用非交互式模式，否则 Claude CLI 会尝试启动 TUI，在非 TTY 环境会失败
    val normalized = base.copy(
        model = model ?: base.model,
        systemPrompt = systemPrompt ?: base.systemPrompt,
        continueConversation = sessionId != null || base.continueConversation,
        resume = resumeSessionId ?: base.resume,
        print = true  // 非交互式模式，避免 "Raw mode is not supported" 错误
    )

    // 🔍 调试日志：检查 normalized 中的 canUseTool（copy 后是否保留）
    println("🔍 [normalizeForClaude] normalized.canUseTool=${normalized.canUseTool != null}, normalized.permissionMode=${normalized.permissionMode}")

    return AiAgentConnectContext(
        provider = AiAgentProvider.CLAUDE,
        initialPrompt = initialPrompt,
        sessionId = sessionId,
        metadata = metadata,
        claudeOptions = normalized
    )
}

private fun AiAgentConnectOptions.normalizeForCodex(): AiAgentConnectContext {
    val normalizedClient = codex.clientOptions ?: CodexClientOptions()

    val baseThread = codex.threadOptions ?: ThreadOptions()
    val normalizedThread = baseThread.copy(
        model = model ?: baseThread.model
    )

    return AiAgentConnectContext(
        provider = AiAgentProvider.CODEX,
        initialPrompt = initialPrompt,
        sessionId = sessionId,
        metadata = metadata,
        codexClientOptions = normalizedClient,
        codexThreadOptions = normalizedThread,
        codexThreadId = resumeSessionId
    )
}

