/**
 * 服务层统一入口
 * 导出所有服务和事件监听
 */

import { apiClient } from './apiClient'
import { EventService } from './eventService'
import { claudeService } from './claudeService'
import { ideService } from './ideService'

// 创建事件服务实例
export const eventService = new EventService(apiClient.getBaseUrl())

// 导出所有服务
export {
  apiClient,
  claudeService,
  ideService
}

// 自动连接 SSE
eventService.connect()

// 便捷的事件监听方法
export const onThemeChange = (handler: (data: any) => void) => {
  eventService.on('theme', handler)
}

export const onClaudeMessage = (handler: (data: any) => void) => {
  eventService.on('claude.message', handler)
}

export const onClaudeConnected = (handler: (data: any) => void) => {
  eventService.on('claude.connected', handler)
}

export const onClaudeDisconnected = (handler: (data: any) => void) => {
  eventService.on('claude.disconnected', handler)
}

export const onClaudeError = (handler: (data: any) => void) => {
  eventService.on('claude.error', handler)
}
