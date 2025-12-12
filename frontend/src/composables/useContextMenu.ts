/**
 * 右键菜单相关的 composable
 * 处理发送按钮右键菜单等
 */
import { ref } from 'vue'

export interface UseContextMenuOptions {
  /** 发送回调 */
  onSend?: () => void
  /** 强制发送回调 */
  onForceSend?: () => void
}

export function useContextMenu(options: UseContextMenuOptions = {}) {
  // 发送按钮右键菜单状态
  const showSendContextMenu = ref(false)
  const sendContextMenuPosition = ref({ x: 0, y: 0 })

  /**
   * 处理发送按钮右键菜单
   */
  function handleSendButtonContextMenu(event: MouseEvent) {
    event.preventDefault()
    showSendContextMenu.value = true
    sendContextMenuPosition.value = {
      x: event.clientX,
      y: event.clientY
    }
  }

  /**
   * 从右键菜单发送
   */
  function handleSendFromContextMenu() {
    showSendContextMenu.value = false
    options.onSend?.()
  }

  /**
   * 从右键菜单强制发送
   */
  function handleForceSendFromContextMenu() {
    showSendContextMenu.value = false
    options.onForceSend?.()
  }

  /**
   * 关闭发送按钮右键菜单
   */
  function closeSendContextMenu() {
    showSendContextMenu.value = false
  }

  return {
    // 状态
    showSendContextMenu,
    sendContextMenuPosition,
    // 方法
    handleSendButtonContextMenu,
    handleSendFromContextMenu,
    handleForceSendFromContextMenu,
    closeSendContextMenu
  }
}
