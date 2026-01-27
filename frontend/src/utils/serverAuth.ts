export const SERVER_TOKEN_HEADER = 'X-Claude-Code-Plus-Token'
export const SERVER_TOKEN_QUERY_PARAM = 'token'

export function getServerToken(): string | null {
  if (typeof window === 'undefined') return null
  const token = (window as any).__serverToken
  return typeof token === 'string' && token.length > 0 ? token : null
}

export function withServerToken(headers: Record<string, string> = {}): Record<string, string> {
  const token = getServerToken()
  if (!token) return headers
  return { ...headers, [SERVER_TOKEN_HEADER]: token }
}

/**
 * WebSocket 无法自定义 header，这里用 query param 透传 token。
 */
export function withServerTokenInUrl(url: string): string {
  const token = getServerToken()
  if (!token) return url

  try {
    const u = new URL(url)
    u.searchParams.set(SERVER_TOKEN_QUERY_PARAM, token)
    return u.toString()
  } catch (_e) {
    // 兜底：不抛错，保持原 URL
    return url
  }
}
