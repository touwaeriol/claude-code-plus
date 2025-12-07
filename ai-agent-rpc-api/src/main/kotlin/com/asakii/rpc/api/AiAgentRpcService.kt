package com.asakii.rpc.api

import kotlinx.coroutines.flow.Flow

/**
 * WebSocket RPC 服务接口 - 统一的 AI Agent 会话抽象
 *
 * 架构原则：一个 WebSocket 连接 = 一个 AI Agent 会话。
 * 客户端与后端之间通过 JSON-RPC 协议交互，并共享统一的流式事件结构。
 */
interface AiAgentRpcService {

    /**
     * 初始化会话及底层 SDK 客户端
     * @param options 可选配置 (model, cwd, etc.)
     * @return 会话信息 (sessionId, model, etc.)
     */
    suspend fun connect(options: RpcConnectOptions? = null): RpcConnectResult

    /**
     * 发送一条纯文本消息
     * @param message 用户消息
     * @return 流式响应 (Flow<RpcMessage>)
     */
    fun query(message: String): Flow<RpcMessage>

    /**
     * 发送带富媒体内容的消息（兼容历史 stream-json 格式）
     *
     * Content 格式:
     * - 文本: { "type": "text", "text": "..." }
     * - 图片: { "type": "image", "data": "base64...", "mimeType": "image/png" }
     *
     * @param content 内容块数组
     * @return 统一 UI 事件流 (Flow<RpcMessage>)
     */
    fun queryWithContent(content: List<RpcContentBlock>): Flow<RpcMessage>

    /**
     * 中断当前操作
     */
    suspend fun interrupt(): RpcStatusResult

    /**
     * 断开会话
     */
    suspend fun disconnect(): RpcStatusResult

    /**
     * 设置模型
     * @param model 模型名称
     */
    suspend fun setModel(model: String): RpcSetModelResult

    /**
     * 设置权限模式
     * @param mode 权限模式枚举
     * @return 切换结果
     */
    suspend fun setPermissionMode(mode: RpcPermissionMode): RpcSetPermissionModeResult

    /**
     * 获取历史消息
     * @return 消息列表
     */
    suspend fun getHistory(): RpcHistory

    /**
     * 获取项目的历史会话列表
     * @param maxResults 最大结果数（默认 50）
     * @return 历史会话列表
     */
    suspend fun getHistorySessions(maxResults: Int = 50): RpcHistorySessionsResult
}

