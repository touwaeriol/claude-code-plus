/**
 * RPC 类型系统 - 流事件类
 *
 * 支持 instanceof 判断的流事件类型
 */

import { EventType, createBlock } from './base'
import { ContentBlock, TextBlock, ThinkingBlock } from './content-block'

// ==================== 事件基类 ====================

export abstract class StreamEventData {
  abstract readonly type: string

  constructor(_data?: any) {
    // 子类实现具体初始化
  }
}

// ==================== 消息开始事件 ====================

export interface MessageInfo {
  id?: string
  model?: string
  content: ContentBlock[]
}

@EventType('message_start')
export class MessageStartEvent extends StreamEventData {
  readonly type = 'message_start' as const
  readonly message?: MessageInfo

  constructor(data: any) {
    super(data)
    if (data?.message) {
      this.message = {
        id: data.message.id,
        model: data.message.model,
        content: data.message.content?.map((b: any) => createBlock(b)) ?? []
      }
    }
  }
}

// ==================== 内容块开始事件 ====================

@EventType('content_block_start')
export class ContentBlockStartEvent extends StreamEventData {
  readonly type = 'content_block_start' as const
  readonly index: number
  readonly content_block: ContentBlock

  constructor(data: any) {
    super(data)
    this.index = data?.index ?? 0
    this.content_block = createBlock(data?.content_block)
  }

  /** 是否是文本块 */
  get isTextBlock(): boolean {
    return this.content_block instanceof TextBlock
  }

  /** 是否是思考块 */
  get isThinkingBlock(): boolean {
    return this.content_block instanceof ThinkingBlock
  }
}

// ==================== Delta 类型 ====================

export interface TextDelta {
  type: 'text_delta'
  text: string
}

export interface ThinkingDelta {
  type: 'thinking_delta'
  thinking: string
}

export interface InputJsonDelta {
  type: 'input_json_delta'
  partial_json: string
}

export type DeltaType = TextDelta | ThinkingDelta | InputJsonDelta

// ==================== 内容块增量事件 ====================

@EventType('content_block_delta')
export class ContentBlockDeltaEvent extends StreamEventData {
  readonly type = 'content_block_delta' as const
  readonly index: number
  readonly delta: DeltaType

  constructor(data: any) {
    super(data)
    this.index = data?.index ?? 0
    this.delta = data?.delta ?? { type: 'text_delta', text: '' }
  }

  /** 是否是文本增量 */
  get isTextDelta(): boolean {
    return this.delta.type === 'text_delta'
  }

  /** 是否是思考增量 */
  get isThinkingDelta(): boolean {
    return this.delta.type === 'thinking_delta'
  }

  /** 是否是 JSON 输入增量 */
  get isInputJsonDelta(): boolean {
    return this.delta.type === 'input_json_delta'
  }

  /** 获取文本内容（如果是文本增量） */
  get text(): string {
    if (this.delta.type === 'text_delta') {
      return this.delta.text
    }
    if (this.delta.type === 'thinking_delta') {
      return this.delta.thinking
    }
    return ''
  }
}

// ==================== 内容块停止事件 ====================

@EventType('content_block_stop')
export class ContentBlockStopEvent extends StreamEventData {
  readonly type = 'content_block_stop' as const
  readonly index: number

  constructor(data: any) {
    super(data)
    this.index = data?.index ?? 0
  }
}

// ==================== Usage 类型 ====================

export interface Usage {
  input_tokens?: number
  output_tokens?: number
  cached_input_tokens?: number
  provider?: string
}

// ==================== 消息增量事件 ====================

@EventType('message_delta')
export class MessageDeltaEvent extends StreamEventData {
  readonly type = 'message_delta' as const
  readonly delta?: any
  readonly usage?: Usage

  constructor(data: any) {
    super(data)
    this.delta = data?.delta
    this.usage = data?.usage
  }

  /** 获取 token 统计 */
  get tokenStats(): { input: number; output: number; cached: number } {
    return {
      input: this.usage?.input_tokens ?? 0,
      output: this.usage?.output_tokens ?? 0,
      cached: this.usage?.cached_input_tokens ?? 0
    }
  }
}

// ==================== 消息停止事件 ====================

@EventType('message_stop')
export class MessageStopEvent extends StreamEventData {
  readonly type = 'message_stop' as const

  constructor(data: any) {
    super(data)
  }
}

// ==================== 未知事件 ====================

export class UnknownEvent extends StreamEventData {
  readonly type = 'unknown' as const
  readonly originalType?: string
  readonly raw: any

  constructor(data: any) {
    super(data)
    this.originalType = data?.type
    this.raw = data
  }
}
