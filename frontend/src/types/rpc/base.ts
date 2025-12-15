/**
 * RPC 类型系统 - 基类和类型注册
 *
 * 提供装饰器和工厂函数，支持 instanceof 类型判断
 */

// ==================== 类型定义 ====================

type Constructor<T = any> = new (data: any) => T

export type RpcProvider = 'claude' | 'codex'
export type ContentStatus = 'in_progress' | 'completed' | 'failed'

// ==================== 类型注册表 ====================

const registries = {
  message: new Map<string, Constructor>(),
  block: new Map<string, Constructor>(),
  event: new Map<string, Constructor>()
}

// ==================== 装饰器 ====================

/**
 * 注册消息类型
 * @example @MessageType('user')
 */
export function MessageType(type: string) {
  return function<T extends Constructor>(target: T): T {
    registries.message.set(type, target)
    return target
  }
}

/**
 * 注册内容块类型
 * @example @BlockType('text')
 */
export function BlockType(type: string) {
  return function<T extends Constructor>(target: T): T {
    registries.block.set(type, target)
    return target
  }
}

/**
 * 注册流事件类型
 * @example @EventType('message_start')
 */
export function EventType(type: string) {
  return function<T extends Constructor>(target: T): T {
    registries.event.set(type, target)
    return target
  }
}

// ==================== 工厂函数 ====================

// 前向声明，避免循环依赖
let _RpcUnknownMessage: Constructor
let _UnknownBlock: Constructor
let _UnknownEvent: Constructor

export function setUnknownTypes(
  unknownMessage: Constructor,
  unknownBlock: Constructor,
  unknownEvent: Constructor
) {
  _RpcUnknownMessage = unknownMessage
  _UnknownBlock = unknownBlock
  _UnknownEvent = unknownEvent
}

/**
 * 根据 type 字段创建消息实例
 */
export function createMessage<T = any>(data: any): T {
  if (!data || typeof data.type !== 'string') {
    console.warn('[RPC] Invalid message data:', data)
    return new _RpcUnknownMessage(data) as T
  }

  const Cls = registries.message.get(data.type)
  if (!Cls) {
    console.warn(`[RPC] Unknown message type: ${data.type}`)
    return new _RpcUnknownMessage(data) as T
  }

  return new Cls(data) as T
}

/**
 * 根据 type 字段创建内容块实例
 */
export function createBlock<T = any>(data: any): T {
  if (!data || typeof data.type !== 'string') {
    console.warn('[RPC] Invalid block data:', data)
    return new _UnknownBlock(data) as T
  }

  const Cls = registries.block.get(data.type)
  if (!Cls) {
    console.warn(`[RPC] Unknown block type: ${data.type}`)
    return new _UnknownBlock(data) as T
  }

  return new Cls(data) as T
}

/**
 * 根据 type 字段创建事件实例
 */
export function createEvent<T = any>(data: any): T {
  if (!data || typeof data.type !== 'string') {
    console.warn('[RPC] Invalid event data:', data)
    return new _UnknownEvent(data) as T
  }

  const Cls = registries.event.get(data.type)
  if (!Cls) {
    console.warn(`[RPC] Unknown event type: ${data.type}`)
    return new _UnknownEvent(data) as T
  }

  return new Cls(data) as T
}

/**
 * 批量创建内容块实例
 */
export function createBlocks<T = any>(data: any[] | undefined | null): T[] {
  if (!data || !Array.isArray(data)) return []
  return data.map(item => createBlock<T>(item))
}

// ==================== 调试工具 ====================

export function getRegisteredTypes() {
  return {
    messages: Array.from(registries.message.keys()),
    blocks: Array.from(registries.block.keys()),
    events: Array.from(registries.event.keys())
  }
}
