/**
 * 拖放功能相关的 composable
 * 处理文件和图片的拖放
 */
import { ref } from 'vue'
import type { ContextReference, ContextDisplayType } from '@/types/display'
import type { ImageReference } from '@/types/enhancedMessage'

export interface UseDragAndDropOptions {
  /** 添加上下文回调 */
  onContextAdd?: (context: ContextReference) => void
  /** 添加图片回调 */
  onImageAdd?: (file: File) => Promise<void>
  /** 插入图片到编辑器回调 */
  onInsertImageToEditor?: (base64: string, mimeType: string) => void
  /** 检查光标是否在最前面 */
  isCursorAtStart?: () => boolean
  /** 读取图片为 base64 的函数 */
  readImageAsBase64?: (file: File) => Promise<string>
}

export function useDragAndDrop(options: UseDragAndDropOptions = {}) {
  const isDragging = ref(false)

  /**
   * 默认的 readImageAsBase64 实现
   */
  function defaultReadImageAsBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = (e) => {
        const result = e.target?.result as string
        const base64 = result.split(',')[1]
        resolve(base64)
      }
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
  }

  const readImageAsBase64 = options.readImageAsBase64 || defaultReadImageAsBase64

  /**
   * 处理拖拽进入
   */
  function handleDragOver(event: DragEvent) {
    event.preventDefault()
    isDragging.value = true
  }

  /**
   * 处理拖拽离开
   */
  function handleDragLeave(event: DragEvent) {
    event.preventDefault()
    // 只有当离开整个拖放区域时才设置为 false
    if (event.target === event.currentTarget) {
      isDragging.value = false
    }
  }

  /**
   * 将文件添加到上下文
   */
  async function addFileToContext(file: File) {
    try {
      // 创建上下文引用
      const contextRef: ContextReference = {
        type: 'file',
        uri: file.name,
        displayType: 'TAG' as ContextDisplayType,
        path: file.name, // 在实际项目中应该获取相对路径
        fullPath: file.name
      }

      // 添加到上下文列表
      options.onContextAdd?.(contextRef)
    } catch (error) {
      console.error('Failed to read file:', error)
    }
  }

  /**
   * 处理文件拖放
   */
  async function handleDrop(event: DragEvent) {
    event.preventDefault()
    isDragging.value = false

    const files = event.dataTransfer?.files
    if (!files || files.length === 0) return

    // 判断光标是否在最前面
    const isAtStart = options.isCursorAtStart?.() ?? true

    for (let i = 0; i < files.length; i++) {
      const file = files[i]

      // 检查是否为图片文件
      if (file.type && file.type.startsWith('image/')) {
        if (isAtStart) {
          // 光标在最前面：作为上下文处理
          console.log('📋 [handleDrop] 光标在最前面，将图片作为上下文')
          if (options.onImageAdd) {
            await options.onImageAdd(file)
          }
        } else {
          // 光标不在最前面：插入到编辑器中
          console.log('📋 [handleDrop] 光标不在最前面，将图片插入编辑器')
          const base64 = await readImageAsBase64(file)
          options.onInsertImageToEditor?.(base64, file.type)
        }
      } else {
        // 非图片文件：作为上下文处理
        await addFileToContext(file)
      }
    }
  }

  return {
    // 状态
    isDragging,
    // 方法
    handleDragOver,
    handleDragLeave,
    handleDrop,
    addFileToContext
  }
}
