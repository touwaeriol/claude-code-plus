package com.claudecodeplus.rpc.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * RPC 服务接口 - 基于 WebSocket 的 Claude 交互
 *
 * 架构原则: 一个 WebSocket 连接 = 一个 Claude 会话
 * 因此所有方法都不需要 sessionId 参数,连接本身就是会话标识
 */
interface ClaudeRpcService {

    /**
     * 连接到 Claude 会话
     * @param options 可选配置 (model, cwd, etc.)
     * @return 会话信息 (sessionId, model, etc.)
     */
    suspend fun connect(options: JsonObject?): JsonObject

    /**
     * 发送查询消息
     * @param message 用户消息
     * @return 流式响应 (Flow<JsonElement>)
     */
    fun query(message: String): Flow<JsonElement>

    /**
     * 中断当前操作
     */
    suspend fun interrupt(): JsonObject

    /**
     * 断开会话
     */
    suspend fun disconnect(): JsonObject

    /**
     * 设置模型
     * @param model 模型名称
     */
    suspend fun setModel(model: String): JsonObject

    /**
     * 获取历史消息
     * @return 消息列表
     */
    suspend fun getHistory(): JsonObject
}

