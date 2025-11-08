import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ToastType, TOAST_DEFAULTS, type Toast, type ToastOptions } from '@/types/toast'

export const useToastStore = defineStore('toast', () => {
  const toasts = ref<Toast[]>([])
  const maxToasts = 5 // 最多同时显示 5 个通知

  /**
   * 生成唯一 ID
   */
  function generateId(): string {
    return `toast-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
  }

  /**
   * 添加通知
   */
  function addToast(message: string, options: ToastOptions = {}): string {
    const {
      type = ToastType.INFO,
      duration = TOAST_DEFAULTS.duration,
      _closable = TOAST_DEFAULTS.closable
    } = options

    const toast: Toast = {
      id: generateId(),
      type,
      message,
      duration,
      timestamp: Date.now()
    }

    // 如果超过最大数量,移除最早的通知
    if (toasts.value.length >= maxToasts) {
      toasts.value.shift()
    }

    toasts.value.push(toast)

    // 自动关闭
    if (duration > 0) {
      setTimeout(() => {
        removeToast(toast.id)
      }, duration)
    }

    return toast.id
  }

  /**
   * 移除通知
   */
  function removeToast(id: string) {
    const index = toasts.value.findIndex(t => t.id === id)
    if (index !== -1) {
      toasts.value.splice(index, 1)
    }
  }

  /**
   * 清除所有通知
   */
  function clearAll() {
    toasts.value = []
  }

  /**
   * 便捷方法 - 信息通知
   */
  function info(message: string, duration?: number): string {
    return addToast(message, { type: ToastType.INFO, duration })
  }

  /**
   * 便捷方法 - 成功通知
   */
  function success(message: string, duration?: number): string {
    return addToast(message, { type: ToastType.SUCCESS, duration })
  }

  /**
   * 便捷方法 - 警告通知
   */
  function warning(message: string, duration?: number): string {
    return addToast(message, { type: ToastType.WARNING, duration })
  }

  /**
   * 便捷方法 - 错误通知
   */
  function error(message: string, duration?: number): string {
    return addToast(message, { type: ToastType.ERROR, duration: duration ?? 5000 })
  }

  return {
    toasts,
    addToast,
    removeToast,
    clearAll,
    info,
    success,
    warning,
    error
  }
})
