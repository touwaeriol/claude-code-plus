/**
 * Stream Event 类型定义
 * 
 * 对应 Anthropic Claude API 的流式事件类型
 * 参考: https://docs.anthropic.com/claude/reference/streaming
 */

/**
 * Stream Event 基础接口
 */
export interface StreamEvent {
  type: StreamEventType
  [key: string]: any
}

/**
 * Stream Event 类型枚举
 */
export type StreamEventType =
  | 'message_start'
  | 'message_delta'
  | 'message_stop'
  | 'content_block_start'
  | 'content_block_delta'
  | 'content_block_stop'

/**
 * Message Start Event
 * 新消息开始时发送
 */
export interface MessageStartEvent extends StreamEvent {
  type: 'message_start'
  message: {
    id: string
    type: 'message'
    role: 'assistant'
    model?: string
    content: any[]
    stop_reason?: string | null
    stop_sequence?: string | null
    usage?: TokenUsage
  }
}

/**
 * Message Delta Event
 * 消息元数据变化时发送（如 usage）
 */
export interface MessageDeltaEvent extends StreamEvent {
  type: 'message_delta'
  delta: {
    stop_reason?: string | null
    stop_sequence?: string | null
  }
  usage?: TokenUsage
}

/**
 * Message Stop Event
 * 消息结束时发送
 */
export interface MessageStopEvent extends StreamEvent {
  type: 'message_stop'
}

/**
 * Content Block Start Event
 * 新内容块开始时发送
 */
export interface ContentBlockStartEvent extends StreamEvent {
  type: 'content_block_start'
  index: number
  content_block: ContentBlockData
}

/**
 * Content Block Delta Event
 * 内容块内容变化时发送
 */
export interface ContentBlockDeltaEvent extends StreamEvent {
  type: 'content_block_delta'
  index: number
  delta: TextDelta | InputJsonDelta | ThinkingDelta
}

/**
 * Content Block Stop Event
 * 内容块结束时发送
 */
export interface ContentBlockStopEvent extends StreamEvent {
  type: 'content_block_stop'
  index: number
}

/**
 * Content Block 数据（用于 content_block_start）
 */
export interface ContentBlockData {
  type: 'text' | 'tool_use' | 'thinking'
  text?: string
  id?: string
  name?: string
  input?: Record<string, any>
  thinking?: string
  signature?: string
}

/**
 * Text Delta
 * 文本内容的增量更新
 */
export interface TextDelta {
  type: 'text_delta'
  text: string
}

/**
 * Input JSON Delta
 * 工具输入 JSON 的增量更新
 */
export interface InputJsonDelta {
  type: 'input_json_delta'
  partial_json: string
}

/**
 * Thinking Delta
 * Thinking 内容的增量更新
 */
export interface ThinkingDelta {
  type: 'thinking_delta'
  delta: string
}

/**
 * Token Usage
 */
export interface TokenUsage {
  input_tokens: number
  output_tokens: number
}

/**
 * 从后端接收的 Stream Event 数据格式
 */
export interface StreamEventData {
  type: 'stream_event'
  uuid?: string
  session_id?: string
  event: StreamEvent
  parent_tool_use_id?: string
}







