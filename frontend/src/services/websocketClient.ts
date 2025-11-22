/**
 * WebSocket 响应类型定义
 * 
 * 定义从后端 WebSocket RPC 接收到的消息类型
 * 这些类型对应后端的 Message 类型(AssistantMessage, ResultMessage 等)
 */

/**
 * WebSocket 响应的基础接口
 * 
 * 后端通过 RPC 流式数据发送: { id: "...", type: "stream", data: {...} }
 * 这里定义的是 data 字段的类型
 */
export interface WebSocketResponse {
  type: string
  timestamp?: number
  message?: any
}

/**
 * 用户消息响应
 */
export interface UserMessageResponse extends WebSocketResponse {
  type: 'user'
  message: {
    content: string | any[]
  }
}

/**
 * AI 助手消息响应
 */
export interface AssistantMessageResponse extends WebSocketResponse {
  type: 'assistant'
  message: {
    content: any[]  // ContentBlock[]
    model: string
    token_usage?: {
      input_tokens: number
      output_tokens: number
      cache_creation_input_tokens?: number
      cache_read_input_tokens?: number
    }
  }
}

/**
 * 系统消息响应
 */
export interface SystemMessageResponse extends WebSocketResponse {
  type: 'system'
  message: {
    subtype: string
    data?: any
    message?: string
  }
}

/**
 * 结果消息响应(会话结束)
 */
export interface ResultMessageResponse extends WebSocketResponse {
  type: 'result'
  message: {
    subtype: string
    duration_ms: number
    duration_api_ms: number
    is_error: boolean        // ← 关键:错误标记
    num_turns: number
    session_id: string
    total_cost_usd?: number
    usage?: any
    result?: string          // ← 错误信息或结果文本
  }
}

/**
 * 流事件响应
 */
export interface StreamEventResponse extends WebSocketResponse {
  type: 'stream_event'
  message: {
    uuid: string
    session_id: string
    event: any  // JsonElement
  }
}

/**
 * 错误消息响应
 */
export interface ErrorMessageResponse extends WebSocketResponse {
  type: 'error'
  message: {
    error: string
  }
}

