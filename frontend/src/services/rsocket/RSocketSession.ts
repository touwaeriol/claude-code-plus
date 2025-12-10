/**
 * RSocket 会话管理
 *
 * 基于 RSocket + Protobuf 的会话实现，与 AiAgentSession 接口兼容。
 * 主要优势：
 * - 协议层的流结束信号（onComplete），无需解析消息内容判断
 * - 二进制序列化，更高效
 * - 强类型，编译时检查
 */

import { RSocketClient, createRSocketClient } from './RSocketClient'
import { ProtoCodec } from './protoCodec'
import { resolveServerWsUrl } from '@/utils/serverUrl'
import { loggers } from '@/utils/logger'
import type {
  RpcProvider,
  RpcContentBlock,
  RpcStreamEvent,
  RpcConnectOptions,
  RpcCapabilities,
  RpcPermissionMode,
  RpcSetPermissionModeResult,
  RpcMessage
} from '@/types/rpc'
import type { HistorySessionMetadata } from '@/types/session'

const log = loggers.agent

// 重新导出类型，保持向后兼容
export type { RpcProvider, RpcContentBlock, RpcStreamEvent, RpcConnectOptions }

/** 连接选项（向后兼容别名） */
export type ConnectOptions = RpcConnectOptions

/** 流式事件（向后兼容别名） */
export type AgentStreamEvent = RpcStreamEvent

/** 内容块（向后兼容别名） */
export type ContentBlock = RpcContentBlock

type MessageHandler = (message: RpcMessage) => void
type ErrorHandler = (error: Error) => void

/**
 * RSocket 会话
 *
 * 与 AiAgentSession 接口兼容，但底层使用 RSocket + Protobuf
 */
export class RSocketSession {
  private client: RSocketClient | null = null
  private _isConnected = false
  private sessionId: string | null = null
  private _capabilities: RpcCapabilities | null = null
  private messageHandlers = new Set<MessageHandler>()
  private errorHandlers = new Set<ErrorHandler>()
  private cancelStream: (() => void) | null = null
  private wsUrl: string
  /** 等待连接后注册的 handlers */
  private pendingHandlers = new Map<string, (params: any) => Promise<any>>()

  constructor(wsUrl?: string) {
    if (wsUrl) {
      this.wsUrl = wsUrl.replace(/^http/, 'ws')
    } else {
      this.wsUrl = resolveServerWsUrl()
    }
    log.debug(`[RSocketSession] WebSocket URL: ${this.wsUrl}`)
  }

  get isConnected(): boolean {
    return this._isConnected
  }

  get currentSessionId(): string | null {
    return this.sessionId
  }

  get capabilities(): RpcCapabilities | null {
    return this._capabilities
  }

  /**
   * 连接到服务器并初始化会话
   */
  async connect(options?: ConnectOptions): Promise<string> {
    console.log('[RSocket] ← agent.connect 发送:', JSON.stringify(options, null, 2))

    // 创建 RSocket 客户端
    this.client = createRSocketClient(this.wsUrl)

    try {
      // 建立 RSocket 连接
      await this.client.connect()

      // 发送 connect 请求
      const data = ProtoCodec.encodeConnectOptions(options)
      const responseData = await this.client.requestResponse('agent.connect', data)

      const result = ProtoCodec.decodeConnectResult(responseData)
      console.log('[RSocket] → agent.connect 结果:', JSON.stringify(result, null, 2))
      this.sessionId = result.sessionId
      this._capabilities = result.capabilities || null
      this._isConnected = true

      // 注册等待中的 handlers
      for (const [method, handler] of this.pendingHandlers) {
        log.info(`[RSocketSession] 注册等待中的 handler: ${method}`)
        this.client!.registerHandler(method, handler)
      }
      this.pendingHandlers.clear()

      log.info(`[RSocketSession] 会话已连接: ${this.sessionId}`)
      return this.sessionId

    } catch (error) {
      log.error('[RSocketSession] 连接失败:', error)
      this.handleError(error as Error)
      throw error
    }
  }

  /**
   * 发送消息查询 (纯文本)
   */
  async sendMessage(message: string): Promise<void> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    console.log('[RSocket] ← agent.query 发送:', JSON.stringify({ message }, null, 2))

    const data = ProtoCodec.encodeQueryRequest(message)

    // 使用 Request-Stream 获取流式响应
    this.cancelStream = this.client.requestStream('agent.query', data, {
      onNext: (responseData) => {
        try {
          const rpcMessage = ProtoCodec.decodeRpcMessage(responseData)
          console.log('[RSocket] → agent.query 消息:', JSON.stringify(rpcMessage, null, 2))
          this.notifyMessageHandlers(rpcMessage)
        } catch (error) {
          log.error('[RSocketSession] 解析消息失败:', error)
        }
      },
      onError: (error) => {
        log.error('[RSocketSession] 流错误:', error)
        this.handleError(error)
      },
      onComplete: () => {
        log.debug('[RSocketSession] 流完成 (协议层信号)')
        // RSocket 协议层的 onComplete 信号，表示流结束
        // 无需解析消息内容来判断结束
        this.cancelStream = null
      }
    })
  }

  /**
   * 发送消息查询 (支持图片等富媒体内容)
   */
  async sendMessageWithContent(content: ContentBlock[]): Promise<void> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    console.log('[RSocket] ← agent.queryWithContent 发送:', JSON.stringify({ content }, null, 2))

    const data = ProtoCodec.encodeQueryWithContentRequest(content)

    this.cancelStream = this.client.requestStream('agent.queryWithContent', data, {
      onNext: (responseData) => {
        try {
          const rpcMessage = ProtoCodec.decodeRpcMessage(responseData)
          console.log('[RSocket] → agent.queryWithContent 消息:', JSON.stringify(rpcMessage, null, 2))
          this.notifyMessageHandlers(rpcMessage)
        } catch (error) {
          log.error('[RSocketSession] 解析消息失败:', error)
        }
      },
      onError: (error) => {
        log.error('[RSocketSession] 流错误:', error)
        this.handleError(error)
      },
      onComplete: () => {
        log.debug('[RSocketSession] 流完成')
        this.cancelStream = null
      }
    })
  }

  /**
   * 中断当前操作
   */
  async interrupt(): Promise<void> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    log.info('[RSocketSession] 中断请求')

    // 取消当前流（如果有）
    if (this.cancelStream) {
      this.cancelStream()
      this.cancelStream = null
    }

    // 发送 interrupt 请求
    const responseData = await this.client.requestResponse('agent.interrupt')
    const result = ProtoCodec.decodeStatusResult(responseData)

    log.info(`[RSocketSession] 中断完成: ${result.status}`)

    // 通知 handlers
    const interruptMessage = {
      type: 'result' as const,
      subtype: 'interrupted',
      provider: 'claude' as const,
      is_error: false,
      num_turns: 0
    }
    this.notifyMessageHandlers(interruptMessage as any)
  }

  /**
   * 断开连接
   */
  async disconnect(): Promise<void> {
    if (this._isConnected && this.client) {
      try {
        await this.client.requestResponse('agent.disconnect')
      } catch (error) {
        log.warn('[RSocketSession] disconnect 请求失败:', error)
      }

      this.client.disconnect()
      this.client = null
      this._isConnected = false
      this.sessionId = null

      log.info('[RSocketSession] 已断开连接')
    }
  }

  /**
   * 重连会话（复用连接）
   */
  async reconnectSession(options?: ConnectOptions): Promise<string> {
    if (!this.client?.isConnected) {
      throw new Error('RSocket 未连接，无法重连会话')
    }

    log.info('[RSocketSession] reconnectSession: 重连会话')

    // 发送 disconnect RPC
    await this.client.requestResponse('agent.disconnect')
    this.sessionId = null

    // 发送 connect RPC
    const data = ProtoCodec.encodeConnectOptions(options)
    const responseData = await this.client.requestResponse('agent.connect', data)

    const result = ProtoCodec.decodeConnectResult(responseData)
    this.sessionId = result.sessionId
    this._capabilities = result.capabilities || null
    this._isConnected = true

    log.info(`[RSocketSession] 会话已重连: ${this.sessionId}`)
    return this.sessionId!
  }

  /**
   * 设置模型
   */
  async setModel(model: string): Promise<void> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    console.log('[RSocket] ← agent.setModel 发送:', JSON.stringify({ model }, null, 2))
    const data = ProtoCodec.encodeSetModelRequest(model)
    await this.client.requestResponse('agent.setModel', data)
    console.log('[RSocket] → agent.setModel 结果: OK')
  }

  /**
   * 设置权限模式
   */
  async setPermissionMode(mode: RpcPermissionMode): Promise<RpcSetPermissionModeResult> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    this.checkCapability('canSwitchPermissionMode', 'setPermissionMode')

    console.log('[RSocket] ← agent.setPermissionMode 发送:', JSON.stringify({ mode }, null, 2))
    const data = ProtoCodec.encodeSetPermissionModeRequest(mode)
    const responseData = await this.client.requestResponse('agent.setPermissionMode', data)
    const result = ProtoCodec.decodeSetPermissionModeResult(responseData)
    console.log('[RSocket] → agent.setPermissionMode 结果:', JSON.stringify(result, null, 2))

    return result as RpcSetPermissionModeResult
  }

  /**
   * 获取历史消息
   */
  async getHistory(): Promise<AgentStreamEvent[]> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    console.log('[RSocket] ← agent.getHistory 发送')
    const responseData = await this.client.requestResponse('agent.getHistory')
    const result = ProtoCodec.decodeHistory(responseData)
    console.log('[RSocket] → agent.getHistory 结果:', JSON.stringify(result, null, 2))
    return result.messages as AgentStreamEvent[]
  }

  /**
   * 加载历史消息（从本地存储的 jsonl）
   * 改为 Request-Response 模式，一次性返回结果
   */
  async loadHistory(params: {
    sessionId?: string
    projectPath?: string
    offset?: number
    limit?: number
  }): Promise<{ messages: RpcMessage[]; offset: number; count: number; availableCount: number }> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    console.log('[RSocket] 📜 ← agent.loadHistory 发送:', params)

    const payload = ProtoCodec.encodeLoadHistoryRequest(params)
    const responseData = await this.client.requestResponse('agent.loadHistory', payload)
    const result = ProtoCodec.decodeHistoryResult(responseData)

    console.log(
      `[RSocket] 📜 → agent.loadHistory 结果: count=${result.count}, offset=${result.offset}, availableCount=${result.availableCount}`
    )

    return result
  }

  /**
   * 获取项目的历史会话列表
   */
  async getHistorySessions(maxResults: number = 50, offset: number = 0): Promise<HistorySessionMetadata[]> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    console.log('[RSocket] ← agent.getHistorySessions 发送:', JSON.stringify({ offset, maxResults }, null, 2))
    const data = ProtoCodec.encodeGetHistorySessionsRequest(maxResults, offset)
    const responseData = await this.client.requestResponse('agent.getHistorySessions', data)
    const result = ProtoCodec.decodeHistorySessionsResult(responseData)
    console.log('[RSocket] → agent.getHistorySessions 结果:', JSON.stringify(result, null, 2))
    return result.sessions
  }

  /**
   * 获取历史文件元数据（文件总行数等）
   */
  async getHistoryMetadata(params: { sessionId?: string; projectPath?: string }): Promise<{ totalLines: number; sessionId: string; projectPath: string }> {
    if (!this._isConnected || !this.client) {
      throw new Error('Session not connected')
    }

    console.log('[RSocket] ← agent.getHistoryMetadata 发送:', JSON.stringify(params, null, 2))
    const payload = ProtoCodec.encodeGetHistoryMetadataRequest(params)
    const responseData = await this.client.requestResponse('agent.getHistoryMetadata', payload)
    const result = ProtoCodec.decodeHistoryMetadata(responseData)
    console.log('[RSocket] → agent.getHistoryMetadata 结果:', JSON.stringify(result, null, 2))
    return result
  }

  /**
   * 订阅消息事件
   */
  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler)
    return () => this.messageHandlers.delete(handler)
  }

  /**
   * 订阅错误事件
   */
  onError(handler: ErrorHandler): () => void {
    this.errorHandlers.add(handler)
    return () => this.errorHandlers.delete(handler)
  }

  /**
   * 注册服务器请求处理器（双向 RPC）
   *
   * 服务端可以通过 client.call 路由调用客户端注册的方法
   */
  register(method: string, handler: (params: any) => Promise<any>): () => void {
    if (!this.client) {
      log.warn(`[RSocketSession] register: 客户端未连接，handler 将在连接后生效 (method=${method})`)
      // 暂存 handler，等待连接
      this.pendingHandlers.set(method, handler)
      return () => {
        this.pendingHandlers.delete(method)
      }
    }

    log.info(`[RSocketSession] register: ${method}`)
    return this.client.registerHandler(method, handler)
  }

  // ==================== 私有方法 ====================

  private checkCapability(cap: keyof RpcCapabilities, method: string): void {
    if (!this._capabilities) {
      throw new Error(`${method}: 能力信息未加载，请先调用 connect()`)
    }
    if (!this._capabilities[cap]) {
      throw new Error(`${method}: 当前 provider 不支持此操作`)
    }
  }

  private notifyMessageHandlers(message: RpcMessage): void {
    this.messageHandlers.forEach(handler => {
      try {
        handler(message)
      } catch (error) {
        log.error('[RSocketSession] 消息处理器执行失败:', error)
      }
    })
  }

  private handleError(error: Error): void {
    this.errorHandlers.forEach(handler => {
      try {
        handler(error)
      } catch (err) {
        log.error('[RSocketSession] 错误处理器执行失败:', err)
      }
    })
  }
}
