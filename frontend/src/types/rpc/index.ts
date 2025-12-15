/**
 * RPC 类型系统 - 统一导出
 *
 * 这个模块提供支持 instanceof 判断的 RPC 类型系统
 *
 * @example
 * import { createMessage, RpcUserMessage, RpcAssistantMessage } from '@/types/rpc'
 *
 * const msg = createMessage(data)
 * if (msg instanceof RpcUserMessage) {
 *   console.log('用户消息:', msg.textContent)
 * } else if (msg instanceof RpcAssistantMessage) {
 *   console.log('助手消息:', msg.textContent)
 *   console.log('工具调用:', msg.toolCalls)
 * }
 */

// ==================== 基础模块 ====================

export {
  // 装饰器
  MessageType,
  BlockType,
  EventType,
  // 工厂函数
  createMessage,
  createBlock,
  createBlocks,
  createEvent,
  // 类型
  type RpcProvider,
  type ContentStatus,
  // 调试工具
  getRegisteredTypes,
  // 内部设置函数
  setUnknownTypes
} from './base'

// ==================== 内容块 ====================

export {
  // 基类
  ContentBlock,
  // 具体类型
  TextBlock,
  ThinkingBlock,
  ToolUseBlock,
  ToolResultBlock,
  ImageBlock,
  CommandExecutionBlock,
  FileChangeBlock,
  McpToolCallBlock,
  WebSearchBlock,
  TodoListBlock,
  ErrorBlock,
  UnknownBlock,
  // 相关类型
  type ImageSource,
  type FileChange,
  type TodoItem
} from './content-block'

// ==================== 消息 ====================

export {
  // 消息内容
  RpcMessageContent,
  // 基类
  RpcMessage,
  // 具体类型
  RpcUserMessage,
  RpcAssistantMessage,
  RpcResultMessage,
  RpcStreamEventMessage,
  RpcErrorMessage,
  RpcStatusSystemMessage,
  RpcCompactBoundaryMessage,
  RpcSystemInitMessage,
  RpcUnknownMessage,
  // 相关类型
  type CompactMetadata,
  type McpServerInfo
} from './message'

// ==================== 流事件 ====================

export {
  // 基类
  StreamEventData,
  // 具体类型
  MessageStartEvent,
  ContentBlockStartEvent,
  ContentBlockDeltaEvent,
  ContentBlockStopEvent,
  MessageDeltaEvent,
  MessageStopEvent,
  UnknownEvent,
  // 相关类型
  type MessageInfo,
  type TextDelta,
  type ThinkingDelta,
  type InputJsonDelta,
  type DeltaType,
  type Usage
} from './stream-event'

// ==================== 初始化 ====================

import { setUnknownTypes } from './base'
import { UnknownBlock } from './content-block'
import { RpcUnknownMessage } from './message'
import { UnknownEvent } from './stream-event'

// 注册 Unknown 类型，避免循环依赖
setUnknownTypes(RpcUnknownMessage, UnknownBlock, UnknownEvent)

// ==================== 类型守卫工具 ====================

import { RpcMessage, RpcUserMessage, RpcAssistantMessage, RpcResultMessage, RpcStreamEventMessage } from './message'
import { ContentBlock, TextBlock, ThinkingBlock, ToolUseBlock, ToolResultBlock } from './content-block'
import { StreamEventData, ContentBlockDeltaEvent, MessageStartEvent, MessageStopEvent } from './stream-event'

/**
 * 消息类型守卫集合
 */
export const MessageGuards = {
  isUser: (msg: RpcMessage): msg is RpcUserMessage => msg instanceof RpcUserMessage,
  isAssistant: (msg: RpcMessage): msg is RpcAssistantMessage => msg instanceof RpcAssistantMessage,
  isResult: (msg: RpcMessage): msg is RpcResultMessage => msg instanceof RpcResultMessage,
  isStreamEvent: (msg: RpcMessage): msg is RpcStreamEventMessage => msg instanceof RpcStreamEventMessage
}

/**
 * 内容块类型守卫集合
 */
export const BlockGuards = {
  isText: (block: ContentBlock): block is TextBlock => block instanceof TextBlock,
  isThinking: (block: ContentBlock): block is ThinkingBlock => block instanceof ThinkingBlock,
  isToolUse: (block: ContentBlock): block is ToolUseBlock => block instanceof ToolUseBlock,
  isToolResult: (block: ContentBlock): block is ToolResultBlock => block instanceof ToolResultBlock
}

/**
 * 流事件类型守卫集合
 */
export const EventGuards = {
  isMessageStart: (event: StreamEventData): event is MessageStartEvent => event instanceof MessageStartEvent,
  isMessageStop: (event: StreamEventData): event is MessageStopEvent => event instanceof MessageStopEvent,
  isContentBlockDelta: (event: StreamEventData): event is ContentBlockDeltaEvent => event instanceof ContentBlockDeltaEvent
}
