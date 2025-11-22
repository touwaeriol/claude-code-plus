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
  const windowUrl = getWindowServerUrl()
  if (windowUrl) {
    return windowUrl
  }

  const envUrl = import.meta.env.VITE_SERVER_URL
  if (envUrl) {
    return envUrl
  }

  const backendPort = import.meta.env.VITE_BACKEND_PORT
  if (backendPort) {
    return `http://localhost:${backendPort}`
  }

  return DEFAULT_HTTP_URL
}

export function resolveServerWsUrl(): string {
  const windowUrl = getWindowServerUrl()
  if (windowUrl) {
    return httpUrlToWsUrl(windowUrl)
  }

  const envUrl = import.meta.env.VITE_SERVER_URL
  if (envUrl) {
    return httpUrlToWsUrl(envUrl)
  }

  const backendPort = import.meta.env.VITE_BACKEND_PORT
  if (backendPort) {
    return `ws://localhost:${backendPort}/ws`
  }

  return DEFAULT_WS_URL
}


