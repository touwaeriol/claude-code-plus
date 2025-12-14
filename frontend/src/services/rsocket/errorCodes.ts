/**
 * 自定义 RSocket 错误码
 *
 * RSocket 协议允许自定义错误码范围: 0x00000301 - 0xFFFFFFFE
 * 我们使用 0x00010000 开头的范围作为应用自定义错误码
 *
 * 注意: 这些错误码必须与后端 RSocketErrorCodes.kt 保持一致
 */
export const RSocketErrorCodes = {
  /**
   * 客户端未连接错误
   * 当 Claude CLI 进程意外退出或连接断开时抛出
   * 前端收到此错误码应触发自动重连
   */
  NOT_CONNECTED: 0x00010001,

  /**
   * 会话已过期
   */
  SESSION_EXPIRED: 0x00010002,

  /**
   * 认证失败
   */
  AUTH_FAILED: 0x00010003,
} as const

export type RSocketErrorCode = typeof RSocketErrorCodes[keyof typeof RSocketErrorCodes]

/**
 * 检查错误是否是需要重连的错误
 */
export function isReconnectRequiredError(error: unknown): boolean {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = (error as { code: number }).code
    return code === RSocketErrorCodes.NOT_CONNECTED
  }
  return false
}
