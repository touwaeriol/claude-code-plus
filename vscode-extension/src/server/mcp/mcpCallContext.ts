/**
 * McpCallContext - MCP 调用上下文
 * 
 * 使用 Node.js AsyncLocalStorage 在异步调用链中传递上下文信息。
 * 类似于 Kotlin 的协程上下文机制。
 * 
 * 与 JetBrains 版本的 McpCallContext.kt 对应。
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
  return mcpCallContextStorage.run(context, fn)
}

/**
 * 从当前异步上下文获取 connectId
 * 
 * @returns 前端连接标识，如果不在 MCP 调用上下文中则返回 undefined
 */
export function currentConnectId(): string | undefined {
  return mcpCallContextStorage.getStore()?.connectId
}

/**
 * 获取当前完整的 MCP 调用上下文
 * 
 * @returns 上下文数据，如果不在 MCP 调用上下文中则返回 undefined
 */
export function currentMcpContext(): McpCallContextData | undefined {
  return mcpCallContextStorage.getStore()
}
