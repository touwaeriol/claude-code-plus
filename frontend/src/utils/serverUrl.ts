/**
 * 服务器 URL 解析工具
 *
 * 约束：
 * - IDE 模式必须注入 window.__serverUrl，不做任何回退
 * - 浏览器开发模式（Vite）：后端固定跑在 http://localhost:8765
 * - 生产浏览器模式：同源
 */

const DEFAULT_HTTP_URL = 'http://localhost:8765'
const DEFAULT_WS_URL = 'ws://localhost:8765'

/**
 * 获取 HTTP 基础 URL（用于 API 调用）
 */
export function resolveServerHttpUrl(): string {
  if (typeof window === 'undefined') {
    return DEFAULT_HTTP_URL
  }

  const anyWindow = window as any
  const isIdeMode = anyWindow.__IDE_MODE__ === true

  // IDE 模式必须注入 __serverUrl
  if (isIdeMode && !anyWindow.__serverUrl) {
    throw new Error('IDE 模式缺少 window.__serverUrl 注入')
  }

  if (anyWindow.__serverUrl) {
    return anyWindow.__serverUrl as string
  }

  // Vite 开发模式：前后端分离，后端固定端口 8765
  if (import.meta.env.DEV) {
    return DEFAULT_HTTP_URL
  }

  // 浏览器部署：同源
  return window.location.origin
}

/**
 * 获取 WebSocket URL
 */
export function resolveServerWsUrl(): string {
  if (typeof window === 'undefined') {
    return DEFAULT_WS_URL
  }

  const anyWindow = window as any
  const isIdeMode = anyWindow.__IDE_MODE__ === true

  // IDE 模式必须注入 __serverUrl
  if (isIdeMode && !anyWindow.__serverUrl) {
    throw new Error('IDE 模式缺少 window.__serverUrl 注入')
  }

  if (anyWindow.__serverUrl) {
    const url = new URL(anyWindow.__serverUrl as string)
    const wsProtocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${wsProtocol}//${url.host}`
  }

  // Vite 开发模式：直接连 8765
  if (import.meta.env.DEV) {
    return DEFAULT_WS_URL
  }

  // 浏览器部署：同源
  const { protocol, host } = window.location
  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${host}`
}


