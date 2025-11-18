package com.claudecodeplus.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC 2.0 协议数据结构
 *
 * 参考标准：https://www.jsonrpc.org/specification
 *
 * 核心概念：
 * - Request: 客户端发送的 RPC 请求
 * - Response: 服务端返回的 RPC 响应
 * - Notification: 不需要响应的单向消息（id 为 null）
 */

/**
 * JSON-RPC 请求
 *
 * 示例：
 * {
 *   "jsonrpc": "2.0",
 *   "method": "connect",
 *   "params": { "name": "新会话" },
 *   "id": 1
 * }
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonObject? = null,
    val id: JsonElement? = null  // null = notification (无需响应)
)

/**
 * JSON-RPC 响应
 *
 * 成功响应示例：
 * {
 *   "jsonrpc": "2.0",
 *   "result": { "sessionId": "abc123" },
 *   "id": 1
 * }
 *
 * 错误响应示例：
 * {
 *   "jsonrpc": "2.0",
 *   "error": { "code": -32600, "message": "Invalid request" },
 *   "id": 1
 * }
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: JsonElement
)

/**
 * JSON-RPC 错误对象
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * JSON-RPC 错误码
 *
 * 标准错误码：-32768 到 -32000
 * 自定义错误码：-32000 到 -32099（保留给应用使用）
 */
object JsonRpcErrorCode {
    // ===== 标准错误码 =====

    /** 解析错误：无效的 JSON */
    const val PARSE_ERROR = -32700

    /** 无效的请求：不符合 JSON-RPC 格式 */
    const val INVALID_REQUEST = -32600

    /** 方法不存在 */
    const val METHOD_NOT_FOUND = -32601

    /** 无效的参数 */
    const val INVALID_PARAMS = -32602

    /** 内部错误 */
    const val INTERNAL_ERROR = -32603

    // ===== 自定义错误码 =====

    /** 会话未初始化 */
    const val SESSION_NOT_INITIALIZED = -32000

    /** 已经连接（重复 connect） */
    const val ALREADY_CONNECTED = -32001

    /** SDK 错误 */
    const val SDK_ERROR = -32002

    /** 会话不存在 */
    const val SESSION_NOT_FOUND = -32003
}

