package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider

object UnifiedAgentClientFactory {
    fun create(provider: AiAgentProvider): UnifiedAgentClient = when (provider) {
        AiAgentProvider.CLAUDE -> ClaudeAgentClientImpl()
        AiAgentProvider.CODEX -> CodexAgentClientImpl()
    }
}






























