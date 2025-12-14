<template>
  <div
    class="rich-text-input-wrapper"
    :class="{ focused: isFocused, disabled: disabled }"
  >
    <EditorContent
      :editor="editor"
      class="rich-text-editor"
      @mouseover="handleImageHover"
      @mouseout="handleImageLeave"
    />
    <!-- 浮动删除按钮 -->
    <button
      v-if="hoveredImage"
      ref="deleteBtnRef"
      class="floating-delete-btn"
      :style="deleteBtnStyle"
      @click="deleteHoveredImage"
    >
      ×
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import type { ContentBlock } from '@/types/message'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import { Node, mergeAttributes } from '@tiptap/core'
import { ideService } from '@/services/ideaBridge'

// 文件引用扩展 - 用于在编辑器中显示 @文件路径
const FileReference = Node.create({
  name: 'fileReference',
  group: 'inline',
  inline: true,
  atom: true,  // 不可分割，Backspace 整体删除

  addAttributes() {
    return {
      path: {
        default: '',
      },
    }
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-file-ref]',
        getAttrs: (node) => {
          if (typeof node === 'string') return false
          return { path: node.getAttribute('data-file-ref') }
        },
      },
    ]
  },

  renderHTML({ node, HTMLAttributes }) {
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        class: 'file-reference',
        'data-file-ref': node.attrs.path,
      }),
      `@${node.attrs.path}`,
    ]
  },
})

interface ImageData {
  id: string
  data: string // base64
  mimeType: string
  name: string
}

interface RichContent {
  text: string
  images: ImageData[]
}

interface Props {
  placeholder?: string
  disabled?: boolean
  modelValue?: string
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'submit', content: RichContent): void
  (e: 'paste-image', file: File): void
  (e: 'preview-image', src: string): void
  (e: 'focus'): void
  (e: 'blur'): void
  (e: 'keydown', event: KeyboardEvent): void
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '',
  disabled: false,
  modelValue: '',
})

const emit = defineEmits<Emits>()

const isFocused = ref(false)

// 浮动删除按钮状态
const hoveredImage = ref<HTMLImageElement | null>(null)
const deleteBtnPosition = ref({ top: 0, left: 0 })

const deleteBtnStyle = computed(() => ({
  top: `${deleteBtnPosition.value.top}px`,
  left: `${deleteBtnPosition.value.left}px`,
}))

// 处理图片 hover
function handleImageHover(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (target.tagName === 'IMG' && target.classList.contains('inline-image')) {
    hoveredImage.value = target as HTMLImageElement
    updateDeleteBtnPosition()
  }
}

function handleImageLeave(event: MouseEvent) {
  const relatedTarget = event.relatedTarget as HTMLElement
  // 如果移动到删除按钮上，不隐藏
  if (relatedTarget?.classList?.contains('floating-delete-btn')) {
    return
  }
  hoveredImage.value = null
}

function updateDeleteBtnPosition() {
  if (!hoveredImage.value) return
  const rect = hoveredImage.value.getBoundingClientRect()
  const wrapperRect = hoveredImage.value.closest('.rich-text-input-wrapper')?.getBoundingClientRect()
  if (wrapperRect) {
    deleteBtnPosition.value = {
      top: rect.top - wrapperRect.top - 4,
      left: rect.right - wrapperRect.left - 10,
    }
  }
}

function deleteHoveredImage() {
  if (!hoveredImage.value || !editor.value) return
  const src = hoveredImage.value.getAttribute('src')
  if (src) {
    let deleted = false
    editor.value.state.doc.descendants((node, pos) => {
      if (!deleted && node.type.name === 'image' && node.attrs.src === src) {
        editor.value?.chain().focus().deleteRange({ from: pos, to: pos + node.nodeSize }).run()
        deleted = true
        return false
      }
      return true
    })
  }
  hoveredImage.value = null
}

// Tiptap editor
const editor = useEditor({
  content: props.modelValue,
  editable: !props.disabled,
  extensions: [
    StarterKit.configure({
      // 禁用不需要的功能
      heading: false,
      bulletList: false,
      orderedList: false,
      blockquote: false,
      codeBlock: false,
      horizontalRule: false,
      // 避免重复注册 Link，改由单独的 Link 扩展提供
      link: false,
      // 保留基本功能 - 使用 false 禁用或省略以使用默认配置
      // paragraph, text, hardBreak 使用默认配置
    }),
    Link.configure({
      autolink: true,
      openOnClick: false,
      linkOnPaste: true,
      HTMLAttributes: {
        class: 'linkified-link',
        rel: 'noopener noreferrer',
      },
    }),
    Image.configure({
      inline: true,
      allowBase64: true,
      HTMLAttributes: {
        class: 'inline-image',
      },
    }),
    Placeholder.configure({
      placeholder: props.placeholder,
    }),
    FileReference,
  ],
  editorProps: {
    attributes: {
      class: 'prose-editor',
    },
    handleClick(_view, _pos, event) {
      const target = event.target as HTMLElement

      // 检查点击的是否是图片 - 打开预览
      if (target.tagName === 'IMG' && target.classList.contains('inline-image')) {
        const src = target.getAttribute('src')
        if (src) {
          emit('preview-image', src)
          return true
        }
      }

      // 检查点击的是否是文件引用 - 通过 HTTP API 打开文件
      if (target.classList.contains('file-reference')) {
        const filePath = target.getAttribute('data-file-ref')
        if (filePath) {
          // 通过 HTTP API 调用，IDEA 插件和 Web 环境都支持
          ideService.openFile(filePath)
          return true
        }
      }

      return false
    },
    handleKeyDown(_view, event) {
      // 传递键盘事件给父组件
      emit('keydown', event)

      // ESC 键 - 阻止默认行为，让父组件处理
      if (event.key === 'Escape') {
        // 不阻止默认行为，但返回 true 表示已处理
        // 父组件的 handleKeydown 已经通过 emit 收到事件
        return true
      }

      // Tab 键 - 阻止默认行为，让父组件处理（切换思考/模式）
      if (event.key === 'Tab') {
        event.preventDefault()
        return true
      }

      // Ctrl+Enter - 强制发送，让父组件处理
      if (event.key === 'Enter' && event.ctrlKey && !event.shiftKey && !event.altKey) {
        event.preventDefault()
        return true
      }

      // Enter 发送消息（不是 Shift+Enter 换行）
      if (event.key === 'Enter' && !event.shiftKey && !event.altKey && !event.ctrlKey && !event.metaKey) {
        event.preventDefault()
        handleSubmit()
        return true
      }

      return false
    },
    handlePaste(_view, event) {
      const items = event.clipboardData?.items
      if (!items) return false

      // 检查是否有图片
      for (let i = 0; i < items.length; i++) {
        const item = items[i]
        if (item.type.startsWith('image/')) {
          const file = item.getAsFile()
          if (file) {
            event.preventDefault()
            emit('paste-image', file)
            return true
          }
        }
      }

      return false
    },
  },
  onFocus() {
    isFocused.value = true
    emit('focus')
  },
  onBlur() {
    isFocused.value = false
    emit('blur')
  },
  onUpdate({ editor }) {
    // 使用 JSON 格式存储，完全无损保留所有节点和属性
    const json = JSON.stringify(editor.getJSON())
    emit('update:modelValue', json)
  },
  onCreate({ editor }) {
    // 编辑器创建后，确保 placeholder 使用最新的翻译文本
    if (props.placeholder) {
      const firstParagraph = editor.view.dom.querySelector('p.is-editor-empty')
      if (firstParagraph) {
        firstParagraph.setAttribute('data-placeholder', props.placeholder)
      }
    }
  },
})

// 监听 props 变化（modelValue 现在是 JSON 字符串格式）
watch(() => props.modelValue, (newValue) => {
  if (!editor.value) return
  const currentJson = JSON.stringify(editor.value.getJSON())
  if (newValue !== currentJson) {
    // 尝试解析 JSON，如果失败则作为纯文本处理（兼容旧数据）
    try {
      const content = newValue ? JSON.parse(newValue) : ''
      editor.value.commands.setContent(content, false, { preserveWhitespace: 'full' })
    } catch {
      // 非 JSON 格式，作为纯文本处理
      editor.value.commands.setContent(newValue || '', false, { preserveWhitespace: 'full' })
    }
  }
})

watch(() => props.disabled, (newValue) => {
  editor.value?.setEditable(!newValue)
})

watch(() => props.placeholder, (newValue) => {
  // Placeholder 扩展不支持直接动态更新，通过 DOM 更新 data-placeholder 属性
  if (editor.value && newValue) {
    const editorElement = editor.value.view.dom
    const firstParagraph = editorElement.querySelector('p.is-editor-empty')
    if (firstParagraph) {
      firstParagraph.setAttribute('data-placeholder', newValue)
    }
  }
})

// 提取内容
function extractContent(): RichContent {
  if (!editor.value) {
    return { text: '', images: [] }
  }

  const text = editor.value.getText()
  const images: ImageData[] = []

  // 遍历文档查找图片节点
  editor.value.state.doc.descendants((node) => {
    if (node.type.name === 'image') {
      const src = node.attrs.src as string
      if (src && src.startsWith('data:')) {
        // base64 图片
        const match = src.match(/^data:([^;]+);base64,(.+)$/)
        if (match) {
          images.push({
            id: `img-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
            mimeType: match[1],
            data: match[2],
            name: `image.${match[1].split('/')[1] || 'png'}`,
          })
        }
      }
    }
    return true
  })

  return { text, images }
}

// 提取内容块（保持顺序）
function extractContentBlocks(): ContentBlock[] {
  if (!editor.value) return []

  const blocks: ContentBlock[] = []
  let currentText = ''

  editor.value.state.doc.descendants((node) => {
    if (node.isText && node.text) {
      currentText += node.text
    } else if (node.type.name === 'fileReference') {
      // 文件引用节点 - 先保存累积的文本
      if (currentText.trim()) {
        blocks.push({ type: 'text', text: currentText.trim() })
        currentText = ''
      }
      // 文件引用作为独立 TextBlock，格式为 @路径
      const path = node.attrs.path as string
      if (path) {
        blocks.push({ type: 'text', text: `@${path}` })
      }
    } else if (node.type.name === 'image') {
      // 先保存累积的文本
      if (currentText.trim()) {
        blocks.push({ type: 'text', text: currentText.trim() })
        currentText = ''
      }
      // 添加图片块
      const src = node.attrs.src as string
      if (src?.startsWith('data:')) {
        const match = src.match(/^data:([^;]+);base64,(.+)$/)
        if (match) {
          blocks.push({
            type: 'image',
            source: {
              type: 'base64',
              media_type: match[1],
              data: match[2]
            }
          })
        }
      }
    } else if (node.type.name === 'hardBreak') {
      currentText += '\n'
    }
    return true
  })

  // 保存剩余文本
  if (currentText.trim()) {
    blocks.push({ type: 'text', text: currentText.trim() })
  }

  return blocks
}

// 提交内容
function handleSubmit() {
  const content = extractContent()
  if (content.text.trim() || content.images.length > 0) {
    emit('submit', content)
  }
}

// 清空内容
function clear() {
  editor.value?.commands.clearContent()
}

// 聚焦编辑器
function focus() {
  editor.value?.commands.focus()
}

// 在光标位置插入图片
function insertImage(base64Data: string, mimeType: string) {
  if (!editor.value) return

  const src = `data:${mimeType};base64,${base64Data}`
  editor.value.chain()
    .focus()
    .setImage({ src })
    .insertContent(' ')  // 插入空格确保光标可以移动到图片后
    .run()
}

// 在光标位置插入文件引用
function insertFileReference(path: string) {
  if (!editor.value) return

  editor.value.chain()
    .focus()
    .insertContent({
      type: 'fileReference',
      attrs: { path },
    })
    .insertContent(' ')  // 插入空格确保光标可以移动到文件引用后
    .run()
}

// 获取光标位置（相对于纯文本）
function getCursorPosition(): number {
  if (!editor.value) return 0
  return editor.value.state.selection.from - 1  // -1 因为 ProseMirror 位置从 1 开始
}

// 删除指定范围的文本并插入文件引用
function replaceRangeWithFileReference(from: number, to: number, path: string) {
  if (!editor.value) return

  // ProseMirror 位置从 1 开始，所以需要 +1
  const proseMirrorFrom = from + 1
  const proseMirrorTo = to + 1

  editor.value.chain()
    .focus()
    .deleteRange({ from: proseMirrorFrom, to: proseMirrorTo })
    .insertContent({
      type: 'fileReference',
      attrs: { path },
    })
    .insertContent(' ')
    .run()
}

// 判断光标是否在编辑器最前面
function isCursorAtStart(): boolean {
  if (!editor.value) return true

  const { from } = editor.value.state.selection
  // 位置 1 表示文档开头（位置 0 是文档节点本身）
  return from <= 1
}

// 获取纯文本
function getText(): string {
  return editor.value?.getText() || ''
}

// 设置内容（保留空白字符）
function setContent(text: string) {
  editor.value?.commands.setContent(text, false, { preserveWhitespace: 'full' })
}

// 删除从光标位置到行首的内容
function deleteToLineStart() {
  if (!editor.value) return

  const { state } = editor.value
  const { selection } = state
  const { $from } = selection

  // 获取当前行的开始位置
  // $from.start() 返回当前块（段落）的起始位置
  const lineStart = $from.start()
  const currentPos = $from.pos

  // 如果光标已经在行首，不做任何操作
  if (currentPos === lineStart) return

  // 删除从行首到当前位置的内容
  editor.value.chain()
    .focus()
    .deleteRange({ from: lineStart, to: currentPos })
    .run()
}

// 插入换行（用于 Ctrl+J 快捷键）
function insertNewLine() {
  if (!editor.value) return
  // 使用 TipTap 的 splitBlock 命令插入新段落（等同于换行）
  editor.value.chain().focus().splitBlock().run()
}

// 清理
onBeforeUnmount(() => {
  editor.value?.destroy()
})

// 暴露方法
defineExpose({
  clear,
  focus,
  insertImage,
  insertFileReference,
  replaceRangeWithFileReference,
  getCursorPosition,
  isCursorAtStart,
  getText,
  setContent,
  extractContent,
  extractContentBlocks,
  deleteToLineStart,
  insertNewLine,
})
</script>

<style scoped>
.rich-text-input-wrapper {
  position: relative;
  width: 100%;
  height: 100%;  /* 填充父容器 */
  display: flex;
  flex-direction: column;
}

/* 浮动删除按钮 - 与 contexts 图片一致 */
.floating-delete-btn {
  position: absolute;
  width: 14px;
  height: 14px;
  border: none;
  border-radius: 50%;
  background: var(--theme-error, #d73a49);
  color: white;
  font-size: 10px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  padding: 0;
  z-index: 100;
  transition: transform 0.15s;
}

.floating-delete-btn:hover {
  transform: scale(1.15);
}

.rich-text-editor {
  width: 100%;
  min-height: 40px;
  flex: 1;  /* 填充剩余空间 */
  overflow-y: auto;
}

/* ProseMirror 编辑器样式 */
.rich-text-editor :deep(.ProseMirror) {
  outline: none;
  font-size: 14px;
  line-height: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  color: var(--theme-foreground, #24292e);
  white-space: pre-wrap;
  word-break: break-word;
}

.rich-text-editor :deep(.ProseMirror p) {
  margin: 0;
}

/* 占位符样式 */
.rich-text-editor :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  color: var(--el-text-color-placeholder, var(--theme-text-disabled, #a8abb2));
  pointer-events: none;
  float: left;
  height: 0;
}

/* 禁用状态 */
.rich-text-input-wrapper.disabled .rich-text-editor :deep(.ProseMirror) {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 链接样式 */
.rich-text-editor :deep(.linkified-link) {
  color: var(--el-color-primary, var(--theme-link, #409eff));
  text-decoration: none;
  cursor: pointer;
}

.rich-text-editor :deep(.linkified-link:hover) {
  text-decoration: underline;
}

/* 内嵌图片样式 - 与 contexts 图片一致 (32x32) */
.rich-text-editor :deep(.inline-image) {
  width: 32px;
  height: 32px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
  vertical-align: middle;
  margin: 2px;
  border: 1px solid var(--theme-border, #e1e4e8);
  transition: transform 0.15s;
}

.rich-text-editor :deep(.inline-image:hover) {
  transform: scale(1.05);
}


/* 文件引用样式 - 蓝色文字 + 下划线 */
.rich-text-editor :deep(.file-reference) {
  color: var(--theme-link, #0366d6);
  text-decoration: underline;
  text-decoration-style: solid;
  text-underline-offset: 2px;
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
}

.rich-text-editor :deep(.file-reference:hover) {
  color: var(--theme-link-hover, #0550ae);
  text-decoration-thickness: 2px;
}


</style>
