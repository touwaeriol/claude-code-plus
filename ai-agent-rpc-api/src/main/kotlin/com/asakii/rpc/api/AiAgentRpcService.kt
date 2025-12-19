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
     * 将当前任务移到后台运行
     */
    suspend fun runInBackground(): RpcStatusResult

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
     * @param offset     起始偏移（默认 0）
     * @return 历史会话列表
     */
    suspend fun getHistorySessions(maxResults: Int = 50, offset: Int = 0): RpcHistorySessionsResult

    /**
     * 加载指定会话历史（从本地存储 jsonl）
     * @param sessionId 会话 ID（可空则使用当前 sessionId）
     * @param projectPath 项目路径（空则使用 ideTools.getProjectPath）
     * @param offset 跳过前 offset 条消息
     * @param limit 限制最多返回条数（<=0 表示全部）
     * @return 历史加载结果（包含消息列表和分页信息）
     */
    fun loadHistory(
        sessionId: String? = null,
        projectPath: String? = null,
        offset: Int = 0,
        limit: Int = 0
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
     * 获取 Chrome 扩展状态
     *
     * 查询 Chrome 扩展的安装状态、启用状态和连接状态。
     * 需要 CLI 支持 chrome_status 控制请求。
     *
     * @return Chrome 扩展状态信息
     */
    suspend fun getChromeStatus(): RpcChromeStatusResult

}
