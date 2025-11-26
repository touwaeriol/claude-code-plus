/**
 * 服务器 URL 解析工具
 *
 * - 浏览器开发模式（Vite）：后端固定跑在 http://localhost:8765
 * - IDE 插件模式：前端由 Ktor 随机端口服务，使用相同 origin
 */

const DEFAULT_HTTP_URL = 'http://localhost:8765'
const DEFAULT_WS_URL = 'ws://localhost:8765/ws'

/**
 * 获取 HTTP 基础 URL（用于 API 调用）
 */
export function resolveServerHttpUrl(): string {
  if (typeof window === 'undefined') {
    return DEFAULT_HTTP_URL
  }

  const anyWindow = window as any

  // IDEA 插件模式：使用注入的 __serverUrl
  if (anyWindow.__serverUrl) {
    return anyWindow.__serverUrl as string
  }

  // Vite 开发模式：前后端分离，后端固定端口 8765
  if (import.meta.env.DEV) {
    return DEFAULT_HTTP_URL
  }

  // 生产部署兜底：同源
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

  // IDEA 插件模式
  if (anyWindow.__serverUrl) {
    const url = new URL(anyWindow.__serverUrl as string)
    const wsProtocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${wsProtocol}//${url.host}/ws`
  }

  // Vite 开发模式：直接连 8765
  if (import.meta.env.DEV) {
    return DEFAULT_WS_URL
  }

  // 生产同源
  const { protocol, host } = window.location
  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:'
  return `${wsProtocol}//${host}/ws`
}


