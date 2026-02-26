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
     * 订阅全局事件流
     *
     * 返回一个永不自动结束的 Flow，持续推送所有 SDK 事件。
     * 只有在断开连接或手动取消订阅时才会结束。
     *
     * 使用方式：
     * 1. 前端连接后调用此方法订阅全局事件
     * 2. 所有 SDK 事件（包括 query 的响应）都会通过此流推送
     * 3. 可以配合 query 使用，实现 Query/Result 分离模式
     *
     * 注意：
     * - 如果同时使用 query() 返回的流和此全局流，会收到重复事件
     * - 如果没有订阅者，事件会被丢弃（SharedFlow 语义）
     *
     * @return 持续的事件流
     */
    fun subscribeGlobalEvents(): Flow<RpcMessage>

    /**
     * 中断当前操作
     */
    suspend fun interrupt(): RpcStatusResult

    /**
     * 动态设置思考 token 上限（无需重连）
     *
     * @param maxThinkingTokens 思考 token 上限：
     *   - null: 禁用思考（使用默认行为）
     *   - 0: 禁用思考
     *   - 正整数: 设置上限（如 8000, 16000）
     * @return 设置结果
     */
    suspend fun setMaxThinkingTokens(maxThinkingTokens: Int?): RpcSetMaxThinkingTokensResult

    /**
     * 断开会话（保留 MCP 资源，支持重连）
     */
    suspend fun disconnect(): RpcStatusResult

    /**
     * 销毁会话（完全清理所有资源）
     *
     * 在前端删除 Tab 时调用，执行完整清理：
     * 1. 断开 Claude CLI 客户端
     * 2. 注销 MCP HTTP Gateway 端点
     * 3. 释放 Terminal MCP 会话资源
     *
     * 注意：disconnect() 仅断开客户端，不清理 MCP 资源，支持重连。
     * disposeSession() 执行完整清理，Tab 删除时调用。
     *
     * @return 销毁结果
     */
    suspend fun disposeSession(): RpcStatusResult

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
     * 设置沙箱模式（仅 Codex 支持，无需重连）
     *
     * Codex 的 turn/start API 支持每轮设置 sandboxPolicy，因此可以实时切换。
     * 下一次 query 时会使用新的沙箱模式。
     *
     * @param mode 沙箱模式枚举
     * @return 切换结果
     */
    suspend fun setSandboxMode(mode: RpcSandboxMode): RpcSetSandboxModeResult

    /**
     * 获取历史消息
     * @return 消息列表
     */
    suspend fun getHistory(): RpcHistory

    /**
     * 获取项目的历史会话列表
     * @param maxResults 最大结果数（默认 50）
     * @param offset     起始偏移（默认 0）
     * @return 历史会话列表
     */
    suspend fun getHistorySessions(maxResults: Int = 50, offset: Int = 0): RpcHistorySessionsResult

    /**
     * 加载指定会话历史（从本地存储 jsonl）
     *
     * 使用消息树算法（复刻官方 CLI 的 Nm 函数）：
     * 1. 使用 parentUuid 构建消息树
     * 2. 如果提供了 leafUuid，使用它定位到特定分支
     * 3. 否则自动选择时间戳最新的分支
     * 4. 从叶节点回溯到根节点，重建线性对话历史
     *
     * @param sessionId 会话 ID（可空则使用当前 sessionId）
     * @param projectPath 项目路径（空则使用 ideTools.getProjectPath）
     * @param offset 跳过前 offset 条消息
     * @param limit 限制最多返回条数（<=0 表示全部）
     * @param leafUuid 可选的叶节点 UUID，用于恢复到特定分支
     * @return 历史加载结果（包含消息列表和分页信息）
     */
    fun loadHistory(
        sessionId: String? = null,
        projectPath: String? = null,
        offset: Int = 0,
        limit: Int = 0,
        leafUuid: String? = null
    ): RpcHistoryResult

    /**
     * 获取历史会话文件的元数据（总行数等）
     * @param sessionId 会话 ID（可空则使用当前 sessionId）
     * @param projectPath 项目路径（空则使用 ideTools.getProjectPath）
     * @return 历史文件元数据
     */
    suspend fun getHistoryMetadata(
        sessionId: String? = null,
        projectPath: String? = null
    ): RpcHistoryMetadata

    /**
     * 截断历史记录（用于编辑重发功能）
     *
     * 从指定的消息 UUID 开始截断 JSONL 历史文件，该消息及其后续所有消息都会被删除。
     * 通常在截断后需要断开连接并创建新会话。
     *
     * @param sessionId 会话 ID
     * @param messageUuid 要截断的消息 UUID（从该消息开始截断，包含该消息）
     * @param projectPath 项目路径（用于定位 JSONL 文件）
     * @return 截断结果
     */
    suspend fun truncateHistory(
        sessionId: String,
        messageUuid: String,
        projectPath: String
    ): RpcTruncateHistoryResult


    /**
     * 获取 MCP 服务器状态
     *
     * 返回所有已连接的 MCP 服务器的详细状态信息，包括服务器名称、连接状态和服务器信息。
     *
     * @return MCP 服务器状态列表
     */
    suspend fun getMcpStatus(): RpcMcpStatusResult

    /**
     * 重连指定的 MCP 服务器
     *
     * 调用 CLI 内部的重连函数重新建立 MCP 服务器连接，
     * 无需进行完整的服务器替换。适用于服务器暂时断开或需要刷新的情况。
     *
     * @param serverName MCP 服务器名称
     * @return 重连结果
     */
    suspend fun reconnectMcp(serverName: String): RpcReconnectMcpResult

    /**
     * 获取可用模型列表
     *
     * 返回所有可用的模型（内置模型 + 自定义模型），用于前端模型选择器。
     *
     * @return 可用模型列表和当前默认模型
     */
    suspend fun getAvailableModels(): RpcAvailableModelsResult

}
