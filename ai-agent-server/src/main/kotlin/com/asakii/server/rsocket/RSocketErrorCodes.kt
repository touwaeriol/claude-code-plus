package com.asakii.server.rsocket

/**
 * 自定义 RSocket 错误码
 *
 * RSocket 协议允许自定义错误码范围: 0x00000301 - 0xFFFFFFFE
 * 我们使用 0x00010000 开头的范围作为应用自定义错误码
 */
object RSocketErrorCodes {
    /**
     * 客户端未连接错误
     * 当 Claude CLI 进程意外退出或连接断开时抛出
     * 前端收到此错误码应触发自动重连
     */
    const val NOT_CONNECTED = 0x00010001

    /**
     * 会话已过期
     */
    const val SESSION_EXPIRED = 0x00010002

    /**
     * 认证失败
     */
    const val AUTH_FAILED = 0x00010003
}
