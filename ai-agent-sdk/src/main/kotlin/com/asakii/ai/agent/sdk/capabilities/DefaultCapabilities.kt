package com.asakii.ai.agent.sdk.capabilities

/**
 * Claude SDK 的能力声明
 *
 * Claude 支持完整的功能集，包括：
 * - 动态切换模型
 * - 权限模式切换
 * - 富媒体输入（图片）
 * - 思考功能
 * - 会话恢复
 */
object ClaudeCapabilities : AgentCapabilities {
    override val canInterrupt = true
    override val canSwitchModel = true
    override val canSwitchPermissionMode = true
    override val supportedPermissionModes = listOf(
        AiPermissionMode.DEFAULT,
        AiPermissionMode.ACCEPT_EDITS,
        AiPermissionMode.BYPASS_PERMISSIONS,
        AiPermissionMode.PLAN
    )
    override val canSkipPermissions = true
    override val canSendRichContent = true
    override val canThink = true
    override val canResumeSession = true
    override val canRunInBackground = true
}

/**
 * Codex SDK 的能力声明
 *
 * Codex 功能相对有限：
 * - 不支持动态切换模型
 * - 不支持权限模式
 * - 只支持纯文本输入
 * - 不支持思考功能
 */
object CodexCapabilities : AgentCapabilities {
    override val canInterrupt = true
    override val canSwitchModel = false
    override val canSwitchPermissionMode = false
    override val supportedPermissionModes = emptyList<AiPermissionMode>()
    override val canSkipPermissions = false
    override val canSendRichContent = false
    override val canThink = false
    override val canResumeSession = true
    override val canRunInBackground = false
}
