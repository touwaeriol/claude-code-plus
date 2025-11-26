package com.asakii.ai.agent.sdk

/**
 * 标识统一 SDK 当前使用的底层 AI Agent 提供方。
 */
enum class AiAgentProvider {
    CLAUDE,
    CODEX;

    val isClaude: Boolean get() = this == CLAUDE
    val isCodex: Boolean get() = this == CODEX
}

















