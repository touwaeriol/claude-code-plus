/**
 * RSocket 客户端基础类
 *
 * 封装 rsocket-core 和 rsocket-websocket-client，提供底层连接管理。
 * 支持 Request-Response、Request-Stream 和双向 RPC（服务端调用客户端）。
 */

import { Buffer } from 'buffer'
import { RSocketConnector } from 'rsocket-core'
import { WebsocketClientTransport } from 'rsocket-websocket-client'
import type { RSocket, Payload, OnExtensionSubscriber, OnNextSubscriber, OnTerminalSubscriber } from 'rsocket-core'
import { loggers } from '@/utils/logger'

const log = loggers.agent

/** 服务端调用客户端的 handler 类型 */
export type ServerCallHandler = (params: any) => Promise<any>

/**
 * RSocket 路由元数据编码
 * RSocket routing metadata 格式：[length:1byte][route:N bytes]
 */
function encodeRoute(route: string): Buffer {
  const routeBytes = Buffer.from(route, 'utf-8')
  const metadata = Buffer.alloc(1 + routeBytes.length)
  metadata[0] = routeBytes.length
  routeBytes.copy(metadata, 1)
  return metadata
}

/**
 * 创建带路由的 Payload
 */
function createPayload(route: string, data?: Uint8Array): Payload {
  return {
    data: data ? Buffer.from(data) : undefined,
    metadata: encodeRoute(route)
  }
}

/**
 * 从 Payload 中提取路由
 */
function extractRoute(payload: Payload): string {
  if (!payload.metadata) {
    throw new Error('Missing metadata')
  }
  const metadata = new Uint8Array(payload.metadata)
  if (metadata.length === 0) {
    throw new Error('Empty metadata')
  }
  const length = metadata[0]
  return new TextDecoder().decode(metadata.slice(1, 1 + length))
}

export interface RSocketClientOptions {
  /** WebSocket URL (e.g., ws://localhost:8765/rsocket) */
  url: string
  /** 连接超时（毫秒），默认 30000 */
  connectTimeout?: number
  /** 保持活跃间隔（毫秒），默认 30000 */
  keepAliveInterval?: number
  /** 保持活跃超时（毫秒），默认 90000 */
  keepAliveTimeout?: number
  /** 请求响应超时（毫秒），默认 35000 - 比后端 connect 超时稍长 */
  requestTimeout?: number
}

export type StreamSubscriber<T> = {
  onNext: (value: T) => void
  onError: (error: Error) => void
  onComplete: () => void
}

/**
 * RSocket 客户端
 *
 * 提供与后端 RSocket 服务器的通信能力，支持双向 RPC
 */
export class RSocketClient {
  private rsocket: RSocket | null = null
  private options: Required<RSocketClientOptions>
  /** 注册的服务端调用处理器 */
  private serverCallHandlers = new Map<string, ServerCallHandler>()

  constructor(options: RSocketClientOptions) {
    this.options = {
      connectTimeout: 30000,
      keepAliveInterval: 30000,
      keepAliveTimeout: 90000,
      requestTimeout: 35000,
      ...options
    }
  }

  /**
   * 注册服务端调用处理器
   * @param method 方法名（如 'AskUserQuestion'）
   * @param handler 处理函数
   * @returns 取消注册的函数
   */
  registerHandler(method: string, handler: ServerCallHandler): () => void {
    log.info(`[RSocketClient] 注册 handler: ${method}`)
    this.serverCallHandlers.set(method, handler)
    return () => {
      this.serverCallHandlers.delete(method)
      log.info(`[RSocketClient] 取消注册 handler: ${method}`)
    }
  }

  /**
   * 连接到 RSocket 服务器
   */
  async connect(): Promise<void> {
    log.info(`[RSocketClient] 连接到: ${this.options.url}`)

    // 创建 responder 处理服务端请求
    const responder = this.createResponder()

    const connector = new RSocketConnector({
      setup: {
        keepAlive: this.options.keepAliveInterval,
        lifetime: this.options.keepAliveTimeout,
        dataMimeType: 'application/x-protobuf',
        metadataMimeType: 'message/x.rsocket.routing.v0'
      },
      transport: new WebsocketClientTransport({
        url: this.options.url,
        wsCreator: (url) => new WebSocket(url) as any
      }),
      // 直接传递 responder 对象，确保服务端的反向调用能被处理
      responder
    })

    try {
      this.rsocket = await connector.connect()
      log.info('[RSocketClient] 连接成功')
    } catch (error) {
      log.error('[RSocketClient] 连接失败:', error)
      throw error
    }
  }

  /**
   * 创建 responder 处理服务端发来的请求
   */
  private createResponder(): Partial<RSocket> {
    return {
      // 处理 request-response 模式
      requestResponse: (
        payload: Payload,
        responderStream: OnTerminalSubscriber & OnNextSubscriber & OnExtensionSubscriber
      ) => {
        this.handleServerCall(payload)
          .then(responsePayload => {
            responderStream.onNext(responsePayload, true)
            responderStream.onComplete()
          })
          .catch(error => {
            responderStream.onError(error)
          })

        return {
          cancel: () => {
            log.debug('[RSocketClient] 服务端请求被取消')
          },
          onExtension: () => {}
        }
      },

      // 处理 fire-and-forget 模式（用于会话命令、主题变化等推送）
      fireAndForget: (
        payload: Payload,
        responderStream: OnTerminalSubscriber
      ) => {
        this.handleServerCall(payload)
          .then(() => {
            responderStream.onComplete()
          })
          .catch(error => {
            log.error('[RSocketClient] fireAndForget 处理失败:', error)
            responderStream.onError(error)
          })

        return {
          cancel: () => {
            log.debug('[RSocketClient] fireAndForget 请求被取消')
          },
          onExtension: () => {}
        }
      }
    }
  }

  /**
   * 处理服务端调用（按路由分发）
   *
   * 路由即方法名（如 'AskUserQuestion'），数据可以是 JSON 或 Protobuf
   */
  private async handleServerCall(payload: Payload): Promise<Payload> {
    const route = extractRoute(payload)
    log.info(`[RSocketClient] 收到服务端请求: route=${route}`)

    // 路由即方法名
    const handler = this.serverCallHandlers.get(route)
    if (!handler) {
      const errorMsg = `Handler not found: ${route}`
      log.warn(`[RSocketClient] ${errorMsg}`)
      throw new Error(errorMsg)
    }

    try {
      // 直接传递原始字节，让 handler 自行解析（支持 JSON 和 Protobuf）
      const data = payload.data ? new Uint8Array(payload.data) : new Uint8Array()

      // 执行 handler
      const result = await handler(data)

      // 返回 JSON 响应
      const resultJson = JSON.stringify(result)
      log.info(`[RSocketClient] 服务端调用成功: route=${route}`)

      return { data: Buffer.from(resultJson) }
    } catch (error) {
      const errorMsg = error instanceof Error ? error.message : String(error)
      log.error(`[RSocketClient] 服务端调用失败: route=${route}, error=${errorMsg}`)
      throw error
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    if (this.rsocket) {
      this.rsocket.close()
      this.rsocket = null
      log.info('[RSocketClient] 已断开连接')
    }
  }

  /**
   * 检查是否已连接
   */
  get isConnected(): boolean {
    return this.rsocket !== null
  }

  /**
   * Request-Response 模式
   *
   * @param route 路由（如 'agent.connect'）
   * @param data Protobuf 编码的数据
   * @param timeout 超时时间（毫秒），默认使用 options.requestTimeout
   * @returns Protobuf 编码的响应数据
   */
  async requestResponse(route: string, data?: Uint8Array, timeout?: number): Promise<Uint8Array> {
    if (!this.rsocket) {
      throw new Error('RSocket 未连接')
    }

    const timeoutMs = timeout ?? this.options.requestTimeout
    log.debug(`[RSocketClient] ← requestResponse: ${route}`, data ? `(${data.length} bytes)` : '', `timeout=${timeoutMs}ms`)

    return new Promise((resolve, reject) => {
      const payload = createPayload(route, data)
      let settled = false
      let timeoutId: ReturnType<typeof setTimeout> | null = null

      // 设置超时
      if (timeoutMs > 0) {
        timeoutId = setTimeout(() => {
          if (!settled) {
            settled = true
            const error = new Error(`请求超时 (${timeoutMs}ms): ${route}`)
            log.error(`[RSocketClient] → requestResponse 超时 (${route})`)
            reject(error)
          }
        }, timeoutMs)
      }

      this.rsocket!.requestResponse(
        payload,
        {
          onError: (error: Error) => {
            if (!settled) {
              settled = true
              if (timeoutId) clearTimeout(timeoutId)
              log.error(`[RSocketClient] → requestResponse 错误 (${route}):`, error)
              reject(error)
            }
          },
          onNext: (payload: Payload, _isComplete: boolean) => {
            if (!settled) {
              settled = true
              if (timeoutId) clearTimeout(timeoutId)
              const responseData = payload.data ? new Uint8Array(payload.data) : new Uint8Array()
              resolve(responseData)
            }
          },
          onComplete: () => {
            // 对于 requestResponse，onNext 已经处理了结果
          },
          onExtension: () => {}
        }
      )
    })
  }

  /**
   * Request-Stream 模式
   *
   * @param route 路由（如 'agent.query'）
   * @param data Protobuf 编码的数据
   * @param subscriber 流订阅者
   * @returns 取消订阅的函数
   */
  requestStream(
    route: string,
    data: Uint8Array | undefined,
    subscriber: StreamSubscriber<Uint8Array>
  ): () => void {
    if (!this.rsocket) {
      subscriber.onError(new Error('RSocket 未连接'))
      return () => {}
    }

    log.debug(`[RSocketClient] ← requestStream: ${route}`, data ? `(${data.length} bytes)` : '')

    const payload = createPayload(route, data)
    let cancelled = false
    let messageCount = 0

    const cancellable = this.rsocket.requestStream(
      payload,
      0x7fffffff, // 请求所有数据
      {
        onError: (error: Error) => {
          if (!cancelled) {
            log.error(`[RSocketClient] → requestStream 错误 (${route}):`, error)
            subscriber.onError(error)
          }
        },
        onNext: (payload: Payload, _isComplete: boolean) => {
          if (!cancelled && payload.data) {
            const responseData = new Uint8Array(payload.data)
            messageCount++
            subscriber.onNext(responseData)
          }
        },
        onComplete: () => {
          if (!cancelled) {
            log.debug(`[RSocketClient] → requestStream 完成 (${route}), 共 ${messageCount} 条消息`)
            subscriber.onComplete()
          }
        },
        onExtension: () => {}
      }
    )

    // 返回取消函数
    return () => {
      cancelled = true
      cancellable.cancel()
      log.debug(`[RSocketClient] requestStream 已取消 (${route})`)
    }
  }
}

/**
 * 创建 RSocket 客户端实例
 *
 * @param wsUrl WebSocket URL（不带路径）
 * @returns RSocketClient 实例
 */
export function createRSocketClient(wsUrl: string): RSocketClient {
  // 确保 URL 以 /rsocket 结尾
  const url = wsUrl.endsWith('/rsocket') ? wsUrl : `${wsUrl}/rsocket`
  return new RSocketClient({ url })
}
