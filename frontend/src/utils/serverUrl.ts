const DEFAULT_HTTP_PORT = '8765'
const DEFAULT_HTTP_URL = `http://localhost:${DEFAULT_HTTP_PORT}`
const DEFAULT_WS_URL = `ws://localhost:${DEFAULT_HTTP_PORT}/ws`

function getWindowServerUrl(): string | undefined {
  if (typeof window === 'undefined') {
    return undefined
  }
  return (window as any).__serverUrl
}

function httpUrlToWsUrl(serverUrl: string): string {
  try {
    const url = new URL(serverUrl)

    if (url.protocol === 'ws:' || url.protocol === 'wss:') {
      if (!url.pathname || url.pathname === '/') {
        url.pathname = '/ws'
      }
      return url.toString()
    }

    const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${url.host}/ws`
  } catch {
    return DEFAULT_WS_URL
  }
}

export function resolveServerHttpUrl(): string {
  // 1. 直接使用当前页面的地址（前后端共享端口）
  if (typeof window !== 'undefined') {
    const { protocol, hostname, port } = window.location
    return `${protocol}//${hostname}${port ? ':' + port : ''}`
  }

  // 2. 回退：默认端口 8765（构建时）
  return DEFAULT_HTTP_URL
}

export function resolveServerWsUrl(): string {
  // 直接使用当前页面的地址转换为 WebSocket（前后端共享端口）
  return httpUrlToWsUrl(resolveServerHttpUrl())
}


