/**
 * RPC 类型系统 - 内容块类
 *
 * 支持 instanceof 判断的内容块类型
 */

import { BlockType, type ContentStatus } from './base'

// ==================== 基类 ====================

export abstract class ContentBlock {
  abstract readonly type: string

  constructor(_data?: any) {
    // 子类实现具体初始化
  }
}

// ==================== 文本块 ====================

@BlockType('text')
export class TextBlock extends ContentBlock {
  readonly type = 'text' as const
  readonly text: string

  constructor(data: any) {
    super(data)
    this.text = data?.text ?? ''
  }

  get isEmpty(): boolean {
    return !this.text || this.text.trim() === ''
  }
}

// ==================== 思考块 ====================

@BlockType('thinking')
export class ThinkingBlock extends ContentBlock {
  readonly type = 'thinking' as const
  readonly thinking: string
  readonly signature?: string

  constructor(data: any) {
    super(data)
    this.thinking = data?.thinking ?? ''
    this.signature = data?.signature
  }

  get isEmpty(): boolean {
    return !this.thinking || this.thinking.trim() === ''
  }
}

// ==================== 工具调用块 ====================

@BlockType('tool_use')
export class ToolUseBlock extends ContentBlock {
  readonly type = 'tool_use' as const
  readonly id: string
  readonly name: string
  readonly toolType?: string
  readonly input: any
  readonly status: ContentStatus

  constructor(data: any) {
    super(data)
    this.id = data?.id ?? ''
    this.name = data?.name ?? data?.tool_name ?? ''
    this.toolType = data?.toolType ?? data?.tool_type
    this.status = data?.status ?? 'in_progress'

    // input 可能是 JSON 字符串或对象
    if (data?.input_json) {
      try {
        this.input = typeof data.input_json === 'string'
          ? JSON.parse(data.input_json)
          : data.input_json
      } catch {
        this.input = data.input_json
      }
    } else {
      this.input = data?.input ?? {}
    }
  }

  get isCompleted(): boolean {
    return this.status === 'completed'
  }

  get isFailed(): boolean {
    return this.status === 'failed'
  }

  get isInProgress(): boolean {
    return this.status === 'in_progress'
  }
}

// ==================== 工具结果块 ====================

@BlockType('tool_result')
export class ToolResultBlock extends ContentBlock {
  readonly type = 'tool_result' as const
  readonly tool_use_id: string
  readonly content: any
  readonly is_error: boolean
  readonly agent_id?: string

  constructor(data: any) {
    super(data)
    this.tool_use_id = data?.tool_use_id ?? ''
    this.is_error = data?.is_error ?? false
    this.agent_id = data?.agent_id

    // content 可能是 JSON 字符串或对象
    if (data?.content_json) {
      try {
        this.content = typeof data.content_json === 'string'
          ? JSON.parse(data.content_json)
          : data.content_json
      } catch {
        this.content = data.content_json
      }
    } else {
      this.content = data?.content
    }
  }

  /** 是否是子代理结果 */
  get isAgentResult(): boolean {
    return !!this.agent_id
  }
}

// ==================== 图片块 ====================

export interface ImageSource {
  type: 'base64' | 'url'
  media_type: string
  data?: string
  url?: string
}

@BlockType('image')
export class ImageBlock extends ContentBlock {
  readonly type = 'image' as const
  readonly source: ImageSource

  constructor(data: any) {
    super(data)
    this.source = {
      type: data?.source?.type ?? 'base64',
      media_type: data?.source?.media_type ?? data?.source?.mediaType ?? 'image/png',
      data: data?.source?.data,
      url: data?.source?.url
    }
  }

  get isBase64(): boolean {
    return this.source.type === 'base64'
  }

  get isUrl(): boolean {
    return this.source.type === 'url'
  }

  /** 获取可用于 <img src> 的 URL */
  get dataUrl(): string {
    if (this.source.url) return this.source.url
    if (this.source.data) {
      return `data:${this.source.media_type};base64,${this.source.data}`
    }
    return ''
  }
}

// ==================== 命令执行块 ====================

@BlockType('command_execution')
export class CommandExecutionBlock extends ContentBlock {
  readonly type = 'command_execution' as const
  readonly command: string
  readonly output?: string
  readonly exit_code?: number
  readonly status: ContentStatus

  constructor(data: any) {
    super(data)
    this.command = data?.command ?? ''
    this.output = data?.output
    this.exit_code = data?.exit_code
    this.status = data?.status ?? 'in_progress'
  }

  get isSuccess(): boolean {
    return this.exit_code === 0
  }

  get isCompleted(): boolean {
    return this.status === 'completed'
  }

  get isFailed(): boolean {
    return this.status === 'failed' || (this.isCompleted && this.exit_code !== 0)
  }
}

// ==================== 文件变更块 ====================

export interface FileChange {
  path: string
  kind: string
}

@BlockType('file_change')
export class FileChangeBlock extends ContentBlock {
  readonly type = 'file_change' as const
  readonly status: ContentStatus
  readonly changes: FileChange[]

  constructor(data: any) {
    super(data)
    this.status = data?.status ?? 'completed'
    this.changes = data?.changes ?? []
  }

  get fileCount(): number {
    return this.changes.length
  }
}

// ==================== MCP 工具调用块 ====================

@BlockType('mcp_tool_call')
export class McpToolCallBlock extends ContentBlock {
  readonly type = 'mcp_tool_call' as const
  readonly server?: string
  readonly tool?: string
  readonly arguments: any
  readonly result?: any
  readonly status: ContentStatus

  constructor(data: any) {
    super(data)
    this.server = data?.server
    this.tool = data?.tool
    this.status = data?.status ?? 'in_progress'

    // arguments 可能是 JSON 字符串
    if (data?.arguments_json) {
      try {
        this.arguments = typeof data.arguments_json === 'string'
          ? JSON.parse(data.arguments_json)
          : data.arguments_json
      } catch {
        this.arguments = data.arguments_json
      }
    } else {
      this.arguments = data?.arguments ?? {}
    }

    // result 可能是 JSON 字符串
    if (data?.result_json) {
      try {
        this.result = typeof data.result_json === 'string'
          ? JSON.parse(data.result_json)
          : data.result_json
      } catch {
        this.result = data.result_json
      }
    } else {
      this.result = data?.result
    }
  }

  get isCompleted(): boolean {
    return this.status === 'completed'
  }

  get fullName(): string {
    if (this.server && this.tool) {
      return `${this.server}:${this.tool}`
    }
    return this.tool ?? ''
  }
}

// ==================== 网页搜索块 ====================

@BlockType('web_search')
export class WebSearchBlock extends ContentBlock {
  readonly type = 'web_search' as const
  readonly query: string

  constructor(data: any) {
    super(data)
    this.query = data?.query ?? ''
  }
}

// ==================== 待办列表块 ====================

export interface TodoItem {
  text: string
  completed: boolean
}

@BlockType('todo_list')
export class TodoListBlock extends ContentBlock {
  readonly type = 'todo_list' as const
  readonly items: TodoItem[]

  constructor(data: any) {
    super(data)
    this.items = data?.items ?? []
  }

  get completedCount(): number {
    return this.items.filter(item => item.completed).length
  }

  get totalCount(): number {
    return this.items.length
  }

  get progress(): number {
    if (this.totalCount === 0) return 0
    return this.completedCount / this.totalCount
  }
}

// ==================== 错误块 ====================

@BlockType('error')
export class ErrorBlock extends ContentBlock {
  readonly type = 'error' as const
  readonly message: string

  constructor(data: any) {
    super(data)
    this.message = data?.message ?? 'Unknown error'
  }
}

// ==================== 未知块 ====================

export class UnknownBlock extends ContentBlock {
  readonly type = 'unknown' as const
  readonly originalType?: string
  readonly raw: any

  constructor(data: any) {
    super(data)
    this.originalType = data?.type
    this.raw = data
  }
}
