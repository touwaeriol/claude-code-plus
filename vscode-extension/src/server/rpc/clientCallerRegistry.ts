/**
 * ClientCallerRegistry - 全局注册表
 * 
 * 维护 connectId → ClientCaller 的映射，
 * 用于 MCP 工具调用时通过 connectId 找到对应的 RSocket 连接。
 * 
 * 与 JetBrains 版本的 ClientCallerRegistry.kt 对应。
 */

import { ClientCaller } from './clientCaller'

/**
 * ClientCaller 全局注册表
 * 
 * 生命周期管理：
 * - 注册时可选绑定清理回调
 * - 连接关闭时应主动调用 unregister
 * - 确保零内存泄漏
 */
class ClientCallerRegistryImpl {
  private registry: Map<string, ClientCaller> = new Map()

  /**
   * 注册 ClientCaller
   * 
   * @param connectId 前端连接标识（tab ID）
   * @param caller ClientCaller 实例
   */
  register(connectId: string, caller: ClientCaller): void {
    this.registry.set(connectId, caller)
    console.log(`✅ [ClientCallerRegistry] 注册 ClientCaller: connectId=${connectId}`)
  }

  /**
   * 注销 ClientCaller
   * 
   * @param connectId 前端连接标识
   */
  unregister(connectId: string): void {
    if (this.registry.delete(connectId)) {
      console.log(`🗑️ [ClientCallerRegistry] 移除 ClientCaller: connectId=${connectId}`)
    }
  }

  /**
   * 获取 ClientCaller
   * 
   * @param connectId 前端连接标识
   * @returns ClientCaller 或 undefined
   */
  get(connectId: string): ClientCaller | undefined {
    return this.registry.get(connectId)
  }

  /**
   * 检查 connectId 是否已注册
   */
  contains(connectId: string): boolean {
    return this.registry.has(connectId)
  }

  /**
   * 获取当前注册数量（用于调试/监控）
   */
  size(): number {
    return this.registry.size
  }

  /**
   * 获取所有已注册的 connectId
   */
  getAllConnectIds(): string[] {
    return Array.from(this.registry.keys())
  }

  /**
   * 获取所有已注册的 ClientCaller (用于调试)
   */
  getAll(): Map<string, ClientCaller> {
    return new Map(this.registry)
  }

  /**
   * 清空所有注册
   */
  clear(): void {
    this.registry.clear()
    console.log(`🗑️ [ClientCallerRegistry] 清空所有注册`)
  }
}

/**
 * 全局单例
 */
export const ClientCallerRegistry = new ClientCallerRegistryImpl()
