package com.asakii.plugin.types

/**
 * 兼容层：JetBrains 插件侧使用的简化会话类型。
 *
 * 这些类型早期由 cli-wrapper 模块提供。随着重构撤销了该模块，
 * 插件端仍然需要占位类型来完成服务注册。为保持向后兼容，
 * 在此定义精简版的数据模型，避免 ClassNotFound 异常。
 */

data class SessionState(
    val sessionId: String,
    val messages: List<EnhancedMessage> = emptyList(),
    val contexts: List<ContextReference> = emptyList(),
    val isGenerating: Boolean = false,
    val selectedModel: AiModel = AiModel.OPUS,
    val permissionMode: UiPermissionMode = UiPermissionMode.DEFAULT
)

/**
 * 会话更新事件（精简版）。
 * JetBrains 插件暂时仅需要区分 sessionId 与是否活跃。
 */
data class SessionUpdate(
    val sessionId: String,
    val isActive: Boolean
)
