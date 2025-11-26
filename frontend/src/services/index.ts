/**
 * 服务层统一入口
 * 导出所有服务和事件监听
 */

import { ideService, ideaBridge, aiAgentBridgeService } from './ideaBridge'

// 导出所有服务
// 注意：aiAgentService 已被移除，现在每个 SessionState 直接持有 AiAgentSession 实例
export {
  aiAgentBridgeService,
  ideService,
  ideaBridge
}

// 导出 AiAgentSession 类供直接使用
export { AiAgentSession } from './AiAgentSession'
export type { ConnectOptions } from './AiAgentSession'
