/**
 * 服务层统一入口
 * 导出所有服务和事件监听
 */

import { claudeService } from './claudeService'
import { ideService, ideaBridge } from './ideaBridge'

// 导出所有服务
export {
  claudeService,
  ideService,
  ideaBridge
}
