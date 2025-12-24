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

  /**
   * Node.js 未找到或配置的路径无效
   * 前端收到此错误码应提示用户检查 Node.js 配置
   */
  NODE_NOT_FOUND: 0x00010004,

  /**
   * CLI 未找到
   * Claude CLI 未安装或路径无效
   */
  CLI_NOT_FOUND: 0x00010005,
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

/**
 * 检查错误是否是 Node.js 未找到错误
 */
export function isNodeNotFoundError(error: unknown): boolean {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = (error as { code: number }).code
    return code === RSocketErrorCodes.NODE_NOT_FOUND
  }
  return false
}

/**
 * 检查错误是否是 CLI 未找到错误
 */
export function isCliNotFoundError(error: unknown): boolean {
  if (error && typeof error === 'object' && 'code' in error) {
    const code = (error as { code: number }).code
    return code === RSocketErrorCodes.CLI_NOT_FOUND
  }
  return false
}

/**
 * 获取用户友好的错误消息
 */
export function getErrorMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'code' in error && 'message' in error) {
    const code = (error as { code: number }).code
    const message = (error as { message: string }).message

    switch (code) {
      case RSocketErrorCodes.NODE_NOT_FOUND:
        return `Node.js 未找到\n\n${message}\n\n请在 Settings > Claude Code > Node.js path 中配置正确的路径。`
      case RSocketErrorCodes.CLI_NOT_FOUND:
        return `Claude CLI 未找到\n\n${message}\n\n请运行 npm install -g @anthropic-ai/claude-code 安装。`
      case RSocketErrorCodes.NOT_CONNECTED:
        return `连接已断开: ${message}`
      default:
        return message
    }
  }
  return String(error)
}
