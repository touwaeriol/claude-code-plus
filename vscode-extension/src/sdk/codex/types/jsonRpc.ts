/**
 * JSON-RPC 2.0 协议类型定义 (用于 codex app-server)
 *
 * 翻译自: codex-agent-sdk/.../appserver/AppServerTypes.kt 行 30-55
 */

export interface JsonRpcRequest {
  method: string
  id: string
  params?: unknown
}

export interface JsonRpcResponse {
  id: string
  result?: unknown
  error?: JsonRpcError
}

export interface JsonRpcNotification {
  method: string
  params?: unknown
}

export interface JsonRpcError {
  code: number
  message: string
  data?: unknown
}
