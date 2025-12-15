package com.asakii.ai.agent.sdk.capabilities

/**
 * Agent 能力声明接口
 *
 * 不同的 SDK 实现返回不同的能力集合，
 * 调用方可以根据能力来决定是否显示/启用某些功能。
 *
 * 设计原则：
 * - 静态能力：编译时确定，运行时不变
 * - 能力发现：connect() 返回时包含能力信息
 * - 安全调用：调用可选方法前应先检查对应能力
 */
interface AgentCapabilities {
    /** 是否支持中断会话 */
    val canInterrupt: Boolean

    /** 是否支持动态切换模型（不重连） */
    val canSwitchModel: Boolean

    /** 是否支持切换权限模式 */
    val canSwitchPermissionMode: Boolean

    /** 支持的权限模式列表（前端可用于渲染下拉选项） */
    val supportedPermissionModes: List<AiPermissionMode>

    /** 是否支持跳过工具权限认证 */
    val canSkipPermissions: Boolean

    /** 是否支持富媒体输入（图片等） */
    val canSendRichContent: Boolean

    /** 是否支持思考功能 */
    val canThink: Boolean

    /** 是否支持会话恢复 */
    val canResumeSession: Boolean

    /** 是否支持后台运行（将当前任务移到后台） */
    val canRunInBackground: Boolean
}
