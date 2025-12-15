/**
 * RPC 类型系统 - 消息类
 *
 * 支持 instanceof 判断的消息类型
 */

import { MessageType, createBlocks, createEvent, type RpcProvider } from './base'
import {
  ContentBlock,
  TextBlock,
  ThinkingBlock,
  ToolUseBlock
} from './content-block'
import type { StreamEventData } from './stream-event'

// ==================== 消息内容 ====================

export class RpcMessageContent {
  readonly content: ContentBlock[]
  readonly model?: string

  constructor(data: any) {
    this.content = createBlocks<ContentBlock>(data?.content)
    this.model = data?.model
  }

  /** 获取所有文本块的内容拼接 */
  get text(): string {
    return this.content
      .filter((b): b is TextBlock => b instanceof TextBlock)
      .map(b => b.text)
      .join('')
  }

  /** 是否包含思考过程 */
  get hasThinking(): boolean {
    return this.content.some(b => b instanceof ThinkingBlock)
  }

  /** 获取所有工具调用 */
  get toolCalls(): ToolUseBlock[] {
    return this.content.filter(
      (b): b is ToolUseBlock => b instanceof ToolUseBlock
    )
  }
}

// ==================== 消息基类 ====================

export abstract class RpcMessage {
  abstract readonly type: string
  readonly provider: RpcProvider

  constructor(data: any) {
    this.provider = data?.provider ?? 'claude'
  }
}

// ==================== 用户消息 ====================

@MessageType('user')
export class RpcUserMessage extends RpcMessage {
  readonly type = 'user' as const
  readonly message: RpcMessageContent
  readonly id?: string
  readonly uuid?: string
  readonly isReplay?: boolean
  readonly parentToolUseId?: string

  constructor(data: any) {
    super(data)
    this.message = new RpcMessageContent(data?.message)
    this.id = data?.id
    this.uuid = data?.uuid
    this.isReplay = data?.isReplay
    this.parentToolUseId = data?.parentToolUseId
  }

  /** 是否是本地发送的消息（非回放） */
  get isLocal(): boolean {
    return !this.isReplay
  }

  /** 获取纯文本内容 */
  get textContent(): string {
    return this.message.text
  }

  /** 是否是子代理消息 */
  get isSubAgentMessage(): boolean {
    return !!this.parentToolUseId
  }
}

// ==================== 助手消息 ====================

@MessageType('assistant')
export class RpcAssistantMessage extends RpcMessage {
  readonly type = 'assistant' as const
  readonly message: RpcMessageContent
  readonly id?: string
  readonly uuid?: string
  readonly parentToolUseId?: string

  constructor(data: any) {
    super(data)
    this.message = new RpcMessageContent(data?.message)
    this.id = data?.id
    this.uuid = data?.uuid
    this.parentToolUseId = data?.parentToolUseId
  }

  /** 是否包含思考过程 */
  get hasThinking(): boolean {
    return this.message.hasThinking
  }

  /** 获取所有工具调用 */
  get toolCalls(): ToolUseBlock[] {
    return this.message.toolCalls
  }

  /** 获取纯文本内容 */
  get textContent(): string {
    return this.message.text
  }

  /** 是否是子代理消息 */
  get isSubAgentMessage(): boolean {
    return !!this.parentToolUseId
  }

  /** 获取所有思考块 */
  get thinkingBlocks(): ThinkingBlock[] {
    return this.message.content.filter(
      (b): b is ThinkingBlock => b instanceof ThinkingBlock
    )
  }
}

// ==================== 结果消息 ====================

@MessageType('result')
export class RpcResultMessage extends RpcMessage {
  readonly type = 'result' as const
  readonly subtype: string
  readonly duration_ms?: number
  readonly duration_api_ms?: number
  readonly is_error: boolean
  readonly num_turns: number
  readonly session_id?: string
  readonly total_cost_usd?: number
  readonly result?: string | null
  readonly usage?: any

  constructor(data: any) {
    super(data)
    this.subtype = data?.subtype ?? ''
    this.duration_ms = data?.duration_ms
    this.duration_api_ms = data?.duration_api_ms
    this.is_error = data?.is_error ?? false
    this.num_turns = data?.num_turns ?? 0
    this.session_id = data?.session_id
    this.total_cost_usd = data?.total_cost_usd
    this.result = data?.result
    this.usage = data?.usage
  }

  /** 是否成功完成 */
  get isSuccess(): boolean {
    return !this.is_error
  }

  /** 总耗时（秒） */
  get durationSeconds(): number {
    return (this.duration_ms ?? 0) / 1000
  }
}

// ==================== 流事件消息 ====================

@MessageType('stream_event')
export class RpcStreamEventMessage extends RpcMessage {
  readonly type = 'stream_event' as const
  readonly uuid: string
  readonly session_id: string
  readonly event: StreamEventData
  readonly parentToolUseId?: string

  constructor(data: any) {
    super(data)
    this.uuid = data?.uuid ?? ''
    this.session_id = data?.session_id ?? ''
    this.event = createEvent<StreamEventData>(data?.event)
    this.parentToolUseId = data?.parentToolUseId
  }

  /** 是否是子代理事件 */
  get isSubAgentEvent(): boolean {
    return !!this.parentToolUseId
  }
}

// ==================== 错误消息 ====================

@MessageType('error')
export class RpcErrorMessage extends RpcMessage {
  readonly type = 'error' as const
  readonly errorMessage: string

  constructor(data: any) {
    super(data)
    this.errorMessage = data?.message ?? data?.errorMessage ?? 'Unknown error'
  }
}

// ==================== 状态系统消息 ====================

@MessageType('status_system')
export class RpcStatusSystemMessage extends RpcMessage {
  readonly type = 'status_system' as const
  readonly subtype: string
  readonly status?: string
  readonly session_id?: string

  constructor(data: any) {
    super(data)
    this.subtype = data?.subtype ?? ''
    this.status = data?.status
    this.session_id = data?.session_id
  }
}

// ==================== 压缩边界消息 ====================

export interface CompactMetadata {
  trigger?: 'manual' | 'auto'
  pre_tokens?: number
}

@MessageType('compact_boundary')
export class RpcCompactBoundaryMessage extends RpcMessage {
  readonly type = 'compact_boundary' as const
  readonly subtype: string
  readonly session_id: string
  readonly compact_metadata?: CompactMetadata

  constructor(data: any) {
    super(data)
    this.subtype = data?.subtype ?? ''
    this.session_id = data?.session_id ?? ''
    this.compact_metadata = data?.compact_metadata
  }

  /** 是否是压缩开始 */
  get isStart(): boolean {
    return this.subtype === 'compacting_start'
  }

  /** 是否是压缩结束 */
  get isEnd(): boolean {
    return this.subtype === 'compacting_end'
  }
}

// ==================== 系统初始化消息 ====================

export interface McpServerInfo {
  name: string
  status: string
}

@MessageType('system_init')
export class RpcSystemInitMessage extends RpcMessage {
  readonly type = 'system_init' as const
  readonly session_id: string
  readonly cwd?: string
  readonly model?: string
  readonly permissionMode?: string
  readonly apiKeySource?: string
  readonly tools?: string[]
  readonly mcpServers?: McpServerInfo[]

  constructor(data: any) {
    super(data)
    this.session_id = data?.session_id ?? ''
    this.cwd = data?.cwd
    this.model = data?.model
    this.permissionMode = data?.permissionMode
    this.apiKeySource = data?.apiKeySource
    this.tools = data?.tools
    this.mcpServers = data?.mcpServers
  }
}

// ==================== 未知消息 ====================

export class RpcUnknownMessage extends RpcMessage {
  readonly type = 'unknown' as const
  readonly originalType?: string
  readonly raw: any

  constructor(data: any) {
    super(data)
    this.originalType = data?.type
    this.raw = data
  }
}
