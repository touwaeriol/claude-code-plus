/**
 * 服务层统一入口
 * 导出所有服务和事件监听
 */

import { ideService, ideaBridge, aiAgentBridgeService } from './ideaBridge'

// 导出所有服务
export {
  aiAgentBridgeService,
  ideService,
  ideaBridge
}

// 导出 RSocketSession 类供直接使用（已迁移到 RSocket + Protobuf）
export { RSocketSession } from './rsocket'
export type { ConnectOptions } from './rsocket'
// 向后兼容的类型别名
export { RSocketSession as AiAgentSession } from './rsocket'
