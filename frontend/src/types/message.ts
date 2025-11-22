/**
 * 消息类型定义
 */

/**
 * Token 使用统计
 */
export interface MessageTokenUsage {
  input_tokens: number
  output_tokens: number
  cache_creation_input_tokens?: number
  cache_read_input_tokens?: number
}

export interface Message {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: ContentBlock[]
  timestamp: number
  tokenUsage?: MessageTokenUsage  // Token 使用统计
}

export type ContentBlock = TextBlock | ImageBlock | ToolUseBlock | ToolResultBlock | ThinkingBlock

export interface ThinkingBlock {
  type: 'thinking'
  thinking: string
  signature?: string
}

export interface TextBlock {
  type: 'text'
  text: string
}

export interface ImageBlock {
  type: 'image'
  source: {
    type: 'base64' | 'url'
    media_type: string  // e.g., 'image/png', 'image/jpeg'
    data?: string       // base64 encoded image data
    url?: string        // image URL
  }
}

export interface ToolUseBlock {
  type: 'tool_use' | string  // 支持 'tool_use' 和具体工具类型 (如 'edit_tool_use')
  id: string
  name: string
  input: Record<string, any>
}

export interface ToolResultBlock {
  type: 'tool_result'
  tool_use_id: string
  content: string | any[]
  is_error?: boolean
}
