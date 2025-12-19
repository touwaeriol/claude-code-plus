package com.asakii.ai.agent.sdk.capabilities

/**
 * AI Agent 权限模式枚举
 *
 * 定义 Agent 执行工具时的权限控制级别。
 * 不同模式决定了工具调用时是否需要用户确认。
 */
enum class AiPermissionMode {
    /**
     * 默认模式：危险工具需要用户确认
     */
    DEFAULT,

    /**
     * 自动接受文件编辑：文件相关操作自动批准
     */
    ACCEPT_EDITS,

    /**
     * 跳过所有权限检查：所有工具自动执行（谨慎使用）
     */
    BYPASS_PERMISSIONS,

    /**
     * 计划模式：只分析不执行，用于预览操作
     */
    PLAN
}
