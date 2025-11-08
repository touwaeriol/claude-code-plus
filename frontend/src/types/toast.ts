/**
 * Toast 通知类型定义
 */

export enum ToastType {
  INFO = 'info',
  SUCCESS = 'success',
  WARNING = 'warning',
  ERROR = 'error'
}

export interface Toast {
  id: string
  type: ToastType
  message: string
  duration?: number // 显示时长(毫秒),0 表示不自动关闭
  timestamp: number
}

export interface ToastOptions {
  type?: ToastType
  duration?: number
  closable?: boolean
}

/**
 * Toast 图标映射
 */
export const TOAST_ICONS: Record<ToastType, string> = {
  [ToastType.INFO]: 'ℹ️',
  [ToastType.SUCCESS]: '✅',
  [ToastType.WARNING]: '⚠️',
  [ToastType.ERROR]: '❌'
}

/**
 * Toast 默认配置
 */
export const TOAST_DEFAULTS = {
  duration: 3000, // 默认 3 秒
  closable: true
}
