/**
 * McpCallContext - MCP 调用上下文
 * 
 * 使用 Node.js AsyncLocalStorage 在异步调用链中传递上下文信息。
 * 类似于 Kotlin 的协程上下文机制。
 * 
 * 与 JetBrains 版本的 McpCallContext.kt 对应。
 * 
 * 注意：由于 MCP SDK 的 StreamableHTTPServerTransport 在内部处理请求时会丢失
 * AsyncLocalStorage 上下文，我们同时使用全局变量作为备用机制。
 * Node.js 单线程特性保证在处理单个请求时不会有并发问题。
 * 
 * 使用方式：
 * ```typescript
 * // 在 Gateway 层注入上下文
 * await withMcpContext({ connectId: 'xxx' }, async () => {
 *     await server.callToolWithContext(...)
 * })
 * 
 * // 在 MCP 工具内部获取
 * const connectId = currentConnectId()
 * ```
 */

import { AsyncLocalStorage } from 'async_hooks'

/**
 * MCP 调用上下文数据
 */
export interface McpCallContextData {
  /** 前端连接标识 */
  connectId?: string
  /** 其他上下文数据可在此扩展 */
}

/**
 * AsyncLocalStorage 实例 - 用于在异步调用链中传递上下文
 */
const mcpCallContextStorage = new AsyncLocalStorage<McpCallContextData>()

/**
 * 全局 connectId 存储 - 作为 AsyncLocalStorage 的备用机制
 * 
 * 由于 MCP SDK 的 transport 内部实现可能导致 AsyncLocalStorage 上下文丢失，
 * 我们使用全局变量作为备用。Node.js 单线程特性保证安全。
 */
let globalConnectId: string | undefined = undefined

/**
 * 设置全局 connectId
 * 在 MCP HTTP Gateway 处理请求时调用
 */
export function setGlobalConnectId(connectId: string | undefined): void {
  globalConnectId = connectId
}

/**
 * 获取全局 connectId
 */
export function getGlobalConnectId(): string | undefined {
  return globalConnectId
}

/**
 * 在指定上下文中执行异步函数
 * 
 * @param context 上下文数据
 * @param fn 要执行的异步函数
 * @returns 函数执行结果
 */
export function withMcpContext<T>(
  context: McpCallContextData,
  fn: () => T | Promise<T>
): T | Promise<T> {
  // 同时设置全局变量作为备用
  setGlobalConnectId(context.connectId)
  return mcpCallContextStorage.run(context, fn)
}

/**
 * 从当前异步上下文获取 connectId
 * 
 * 优先从 AsyncLocalStorage 获取，如果不可用则从全局变量获取
 * 
 * @returns 前端连接标识，如果不在 MCP 调用上下文中则返回 undefined
 */
export function currentConnectId(): string | undefined {
  // 优先从 AsyncLocalStorage 获取
  const fromStorage = mcpCallContextStorage.getStore()?.connectId
  if (fromStorage) {
    return fromStorage
  }
  // 备用：从全局变量获取
  return globalConnectId
}

/**
 * 获取当前完整的 MCP 调用上下文
 * 
 * @returns 上下文数据，如果不在 MCP 调用上下文中则返回 undefined
 */
export function currentMcpContext(): McpCallContextData | undefined {
  const fromStorage = mcpCallContextStorage.getStore()
  if (fromStorage) {
    return fromStorage
  }
  // 备用：构造一个包含全局 connectId 的上下文
  if (globalConnectId) {
    return { connectId: globalConnectId }
  }
  return undefined
}
