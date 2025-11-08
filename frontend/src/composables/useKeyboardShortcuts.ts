/**
 * 快捷键管理 Composable
 */

import { onMounted, onUnmounted } from 'vue'

/**
 * 快捷键配置
 */
export interface ShortcutConfig {
  key: string
  ctrl?: boolean
  shift?: boolean
  alt?: boolean
  meta?: boolean
  handler: (event: KeyboardEvent) => void
  preventDefault?: boolean
  description?: string
}

/**
 * 预定义的快捷键
 */
export const SHORTCUTS = {
  SEND_MESSAGE: { key: 'Enter', ctrl: true, description: '发送消息' },
  OPEN_SETTINGS: { key: 'k', ctrl: true, description: '打开设置' },
  TOGGLE_SESSION_LIST: { key: '/', ctrl: true, description: '切换会话列表' },
  NEW_SESSION: { key: 'n', ctrl: true, description: '新建会话' },
  CLOSE_DIALOG: { key: 'Escape', description: '关闭对话框' }
}

/**
 * 快捷键管理 Hook
 */
export function useKeyboardShortcuts(shortcuts: ShortcutConfig[]) {
  const handleKeyDown = (event: KeyboardEvent) => {
    for (const shortcut of shortcuts) {
      // 检查修饰键
      const ctrlMatch = shortcut.ctrl === undefined || event.ctrlKey === shortcut.ctrl
      const shiftMatch = shortcut.shift === undefined || event.shiftKey === shortcut.shift
      const altMatch = shortcut.alt === undefined || event.altKey === shortcut.alt
      const metaMatch = shortcut.meta === undefined || event.metaKey === shortcut.meta

      // 检查按键（不区分大小写）
      const keyMatch = event.key.toLowerCase() === shortcut.key.toLowerCase()

      // 如果所有条件都匹配
      if (ctrlMatch && shiftMatch && altMatch && metaMatch && keyMatch) {
        if (shortcut.preventDefault !== false) {
          event.preventDefault()
        }
        shortcut.handler(event)
        break
      }
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKeyDown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeyDown)
  })

  return {
    handleKeyDown
  }
}

/**
 * 创建快捷键配置
 */
export function createShortcut(
  key: string,
  handler: (event: KeyboardEvent) => void,
  options: Partial<ShortcutConfig> = {}
): ShortcutConfig {
  return {
    key,
    handler,
    ...options
  }
}

/**
 * 格式化快捷键显示
 */
export function formatShortcut(shortcut: Partial<ShortcutConfig>): string {
  const parts: string[] = []

  if (shortcut.ctrl) parts.push('Ctrl')
  if (shortcut.shift) parts.push('Shift')
  if (shortcut.alt) parts.push('Alt')
  if (shortcut.meta) parts.push('Meta')
  if (shortcut.key) {
    // 特殊键名处理
    const keyName = shortcut.key === 'Escape' ? 'Esc' :
      shortcut.key === 'Enter' ? '↵' :
      shortcut.key.toUpperCase()
    parts.push(keyName)
  }

  return parts.join('+')
}
