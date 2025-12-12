/**
 * 输入框大小调整的 composable
 * 处理拖拽调整输入框高度
 */
import { ref } from 'vue'

export interface UseInputResizeOptions {
  /** 最小高度 */
  minHeight?: number
  /** 最大高度 */
  maxHeight?: number
  /** 初始高度（null 表示自动） */
  initialHeight?: number | null
}

export function useInputResize(options: UseInputResizeOptions = {}) {
  const {
    minHeight = 110,
    maxHeight = 500,
    initialHeight = null
  } = options

  const containerHeight = ref<number | null>(initialHeight)
  const isResizing = ref(false)

  /**
   * 开始拖拽调整大小
   */
  function startResize(event: MouseEvent) {
    event.preventDefault()
    isResizing.value = true
    const startY = event.clientY

    // 首次拖拽时获取当前实际高度
    const container = (event.target as HTMLElement).closest('.unified-chat-input-container') as HTMLElement
    const startHeight = containerHeight.value ?? container?.offsetHeight ?? 120

    const onMouseMove = (e: MouseEvent) => {
      // 向上拖动增加高度，向下拖动减少高度
      const deltaY = startY - e.clientY
      const newHeight = Math.min(maxHeight, Math.max(minHeight, startHeight + deltaY))
      containerHeight.value = newHeight
    }

    const onMouseUp = () => {
      isResizing.value = false
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', onMouseUp)
    }

    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }

  /**
   * 重置高度为自动
   */
  function resetHeight() {
    containerHeight.value = null
  }

  /**
   * 设置固定高度
   */
  function setHeight(height: number) {
    containerHeight.value = Math.min(maxHeight, Math.max(minHeight, height))
  }

  return {
    // 状态
    containerHeight,
    isResizing,
    // 配置
    minHeight,
    maxHeight,
    // 方法
    startResize,
    resetHeight,
    setHeight
  }
}
