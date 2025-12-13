package com.asakii.ai.agent.sdk.client

import com.asakii.ai.agent.sdk.AiAgentProvider
import com.asakii.ai.agent.sdk.capabilities.AgentCapabilities
import com.asakii.ai.agent.sdk.capabilities.AiPermissionMode
import com.asakii.ai.agent.sdk.connect.AiAgentConnectOptions
import com.asakii.ai.agent.sdk.model.UiStreamEvent
import com.asakii.ai.agent.sdk.model.UnifiedContentBlock
import kotlinx.coroutines.flow.Flow

/**
 * 统一的 Agent 客户端接口，对外屏蔽底层 SDK 差异。
 */
interface UnifiedAgentClient {
    val provider: AiAgentProvider

    // ==================== 基础方法（所有实现必须支持）====================

    /**
     * 建立会话或恢复既有会话。
     */
    suspend fun connect(options: AiAgentConnectOptions)

    /**
     * 发送一条用户输入，方法会在当前回合结束后返回。
     */
    suspend fun sendMessage(input: AgentMessageInput)

    /**
     * 获取统一的 UI 事件流（多次调用返回同一个共享流）。
     */
    fun streamEvents(): Flow<UiStreamEvent>

    /**
     * 断开连接并释放资源。
     */
    suspend fun disconnect()

    /**
     * 获取当前客户端支持的能力（静态，编译时确定）。
     * connect() 内部会调用此方法获取能力信息。
     */
    fun getCapabilities(): AgentCapabilities

    // ==================== 可选方法（根据 capabilities 决定是否可用）====================

    /**
     * 中断当前回合。
     * @throws UnsupportedOperationException if !capabilities.canInterrupt
     */
    suspend fun interrupt()

    /**
     * 动态切换模型（不重连）。
     * @param model 目标模型名称
     * @return 实际切换后的模型名称
     * @throws UnsupportedOperationException if !capabilities.canSwitchModel
     */
    suspend fun setModel(model: String): String?

    /**
     * 切换权限模式。
     * @param mode 目标权限模式
     * @throws UnsupportedOperationException if !capabilities.canSwitchPermissionMode
     * @throws IllegalArgumentException if mode not in capabilities.supportedPermissionModes
     */
    suspend fun setPermissionMode(mode: AiPermissionMode)

    /**
     * 获取当前权限模式。
     * @return 当前权限模式，如果不支持则返回 null
     */
    fun getCurrentPermissionMode(): AiPermissionMode?
}

/**
 * 统一的用户输入结构，支持纯文本和富媒体内容（图片等）。
 *
 * 使用方式：
 * - 纯文本: AgentMessageInput(text = "Hello")
 * - 富媒体: AgentMessageInput(content = listOf(TextContent("Hello"), ImageContent(...)))
 *
 * 如果同时提供 text 和 content，优先使用 content。
 */
data class AgentMessageInput(
    val text: String? = null,
    val content: List<UnifiedContentBlock>? = null,
    val sessionId: String? = null
) {
    init {
        require(text != null || !content.isNullOrEmpty()) {
            "Either text or content must be provided"
        }
    }
}


































