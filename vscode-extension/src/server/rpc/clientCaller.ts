/**
 * ClientCaller - 客户端调用接口
 * 
 * 用于服务器向前端发起 RPC 请求。
 * 主要用于需要用户交互的场景，如 AskUserQuestion 工具。
 * 
 * 与 JetBrains 版本的 ClientCaller.kt 对应。
 */

import type {
  AskUserQuestionRequest,
  AskUserQuestionResponse,
  RequestPermissionRequest,
  RequestPermissionResponse,
} from '@proto/ide_pb'

/**
 * 客户端调用接口
 * 
 * 允许后端（如 MCP Server）调用前端方法并等待响应。
 */
export interface ClientCaller {
  /**
   * 调用前端 AskUserQuestion
   * 
   * @param request AskUserQuestion 请求
   * @returns AskUserQuestion 响应
   */
  callAskUserQuestion(request: AskUserQuestionRequest): Promise<AskUserQuestionResponse>

  /**
   * 调用前端 RequestPermission
   * 
   * @param request RequestPermission 请求
   * @returns RequestPermission 响应
   */
  callRequestPermission(request: RequestPermissionRequest): Promise<RequestPermissionResponse>
}
