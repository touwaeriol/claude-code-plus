package com.asakii.server.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 客户端调用接口 - 用于服务器向前端发起 RPC 请求
 *
 * 这个接口允许后端（如 MCP Server）调用前端方法并等待响应。
 * 主要用于需要用户交互的场景，如 AskUserQuestion 工具。
 */
interface ClientCaller {
    /**
     * 调用前端方法（返回原始 JsonElement）
     *
     * @param method 方法名（如 "AskUserQuestion"）
     * @param params 参数
     * @return 前端返回的结果
     */
    suspend fun call(method: String, params: Any): JsonElement
}

/**
 * 调用前端方法并反序列化为指定类型
 *
 * @param method 方法名
 * @param params 参数
 * @return 类型化的响应对象
 */
suspend inline fun <reified T> ClientCaller.callTyped(method: String, params: Any): T {
    val response = call(method, params)
    return Json.decodeFromJsonElement(response)
}
