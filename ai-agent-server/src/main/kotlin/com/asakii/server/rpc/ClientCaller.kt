package com.asakii.server.rpc

import com.asakii.rpc.proto.AskUserQuestionRequest
import com.asakii.rpc.proto.AskUserQuestionResponse
import com.asakii.rpc.proto.RequestPermissionRequest
import com.asakii.rpc.proto.RequestPermissionResponse

/**
 * 客户端调用接口 - 用于服务器向前端发起 RPC 请求
 *
 * 这个接口允许后端（如 MCP Server）调用前端方法并等待响应。
 * 主要用于需要用户交互的场景，如 AskUserQuestion 工具。
 *
 * 使用 Protobuf 序列化进行类型化调用。
 */
interface ClientCaller {
    /**
     * 调用前端 AskUserQuestion（Protobuf 序列化）
     *
     * @param request AskUserQuestion 请求
     * @return AskUserQuestion 响应
     */
    suspend fun callAskUserQuestion(request: AskUserQuestionRequest): AskUserQuestionResponse

    /**
     * 调用前端 RequestPermission（Protobuf 序列化）
     *
     * @param request RequestPermission 请求
     * @return RequestPermission 响应
     */
    suspend fun callRequestPermission(request: RequestPermissionRequest): RequestPermissionResponse
}
