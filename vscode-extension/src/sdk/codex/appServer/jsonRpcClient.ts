/**
 * Codex App-Server JSON-RPC 2.0 客户端
 *
 * 翻译自: codex-agent-sdk/.../appserver/CodexJsonRpcClient.kt
 *
 * 处理双向 JSON-RPC 通信:
 * - 客户端请求 → 服务器响应
 * - 服务器通知 → 客户端处理
 * - 服务器请求 (审批) → 客户端响应
 */

import { Writable, Readable } from 'stream'
import * as readline from 'readline'
import { EventEmitter } from 'events'
import * as crypto from 'crypto'
import type {
  JsonRpcRequest,
  JsonRpcResponse,
  JsonRpcNotification,
  ServerRequest,
  CommandExecutionRequestApprovalParams,
  FileChangeRequestApprovalParams,
} from '../types'

const DEFAULT_TIMEOUT_MS = 30000

export class CodexRpcException extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message)
    this.name = 'CodexRpcException'
  }
}

interface PendingRequest {
  resolve: (response: JsonRpcResponse) => void
  reject: (error: Error) => void
}

export class CodexJsonRpcClient extends EventEmitter {
  private stdin: Writable
  private stdout: Readable
  private rl: readline.Interface | null = null
  private pendingRequests = new Map<string, PendingRequest>()
  private isRunning = false

  constructor(stdin: Writable, stdout: Readable) {
    super()
    this.stdin = stdin
    this.stdout = stdout
  }

  start(): void {
    if (this.isRunning) return
    this.isRunning = true

    this.rl = readline.createInterface({
      input: this.stdout,
      crlfDelay: Infinity,
    })

    this.rl.on('line', (line) => {
      if (!line.trim()) return
      try {
        this.processMessage(line)
      } catch (e) {
        console.warn(`[CodexJsonRpcClient] Error processing message: ${e}`)
      }
    })

    this.rl.on('close', () => {
      this.isRunning = false
      this.emit('close')
    })
  }

  private processMessage(line: string): void {
    const obj = JSON.parse(line)

    // 响应: 有 id 且有 result 或 error
    if ('id' in obj && ('result' in obj || 'error' in obj)) {
      const response = obj as JsonRpcResponse
      const pending = this.pendingRequests.get(response.id)
      if (pending) {
        this.pendingRequests.delete(response.id)
        pending.resolve(response)
      }
      return
    }

    // 服务器请求: 有 id 和 method (审批请求)
    if ('id' in obj && 'method' in obj) {
      const request = obj as JsonRpcRequest
      console.log(`[CodexJsonRpcClient] RPC server request: method=${request.method} id=${request.id}`)
      this.handleServerRequest(request, obj.id)
      return
    }

    // 通知: 只有 method，没有 id
    if ('method' in obj && !('id' in obj)) {
      const notification = obj as JsonRpcNotification
      console.debug(`[CodexJsonRpcClient] RPC notification: method=${notification.method}`)
      this.emit('notification', notification)
      return
    }
  }

  private handleServerRequest(request: JsonRpcRequest, rawId: unknown): void {
    let serverRequest: ServerRequest | null = null

    switch (request.method) {
      case 'item/commandExecution/requestApproval':
        serverRequest = {
          type: 'commandApproval',
          requestId: request.id,
          rawId,
          params: request.params as CommandExecutionRequestApprovalParams,
        }
        break
      case 'item/fileChange/requestApproval':
        serverRequest = {
          type: 'fileChangeApproval',
          requestId: request.id,
          rawId,
          params: request.params as FileChangeRequestApprovalParams,
        }
        break
    }

    if (serverRequest) {
      this.emit('serverRequest', serverRequest)
    }
  }

  async request<T>(method: string, params?: unknown, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<T> {
    const requestId = crypto.randomUUID()
    console.debug(`[CodexJsonRpcClient] RPC request: method=${method} id=${requestId}`)

    const request = {
      method,
      id: requestId,
      ...(params !== undefined && { params }),
    }

    return new Promise<T>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingRequests.delete(requestId)
        reject(new CodexRpcException(-32603, `Request timeout: ${method}`))
      }, timeoutMs)

      this.pendingRequests.set(requestId, {
        resolve: (response) => {
          clearTimeout(timer)
          if (response.error) {
            reject(new CodexRpcException(response.error.code, response.error.message))
          } else {
            resolve(response.result as T)
          }
        },
        reject: (error) => {
          clearTimeout(timer)
          reject(error)
        },
      })

      this.sendLine(JSON.stringify(request))
    })
  }

  async notify(method: string, params?: unknown): Promise<void> {
    const notification = {
      method,
      ...(params !== undefined && { params }),
    }
    console.debug(`[CodexJsonRpcClient] RPC notify: method=${method}`)
    this.sendLine(JSON.stringify(notification))
  }

  async respondToServerRequest(rawId: unknown, result: unknown): Promise<void> {
    const response = {
      id: rawId,
      result,
    }
    console.debug(`[CodexJsonRpcClient] RPC response to server request: id=${rawId}`)
    this.sendLine(JSON.stringify(response))
  }

  private sendLine(line: string): void {
    this.stdin.write(line + '\n')
  }

  close(): void {
    this.isRunning = false
    this.rl?.close()
    for (const [, pending] of this.pendingRequests) {
      pending.reject(new Error('Client closed'))
    }
    this.pendingRequests.clear()
  }
}
