<template>
  <div
    class="rich-text-input-wrapper"
    :class="{ focused: isFocused, disabled: disabled }"
  >
    <EditorContent
      :editor="editor"
      class="rich-text-editor"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount, computed } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Link from '@tiptap/extension-link'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'

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
      // 保留基本功能
      paragraph: true,
      text: true,
      hardBreak: true,
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
  ],
  editorProps: {
    attributes: {
      class: 'prose-editor',
    },
    handleKeyDown(view, event) {
      // 传递键盘事件给父组件
      emit('keydown', event)

      // Enter 发送消息（不是 Shift+Enter 换行）
      if (event.key === 'Enter' && !event.shiftKey && !event.altKey && !event.ctrlKey && !event.metaKey) {
        event.preventDefault()
        handleSubmit()
        return true
      }

      return false
    },
    handlePaste(view, event) {
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
    const text = editor.getText()
    emit('update:modelValue', text)
  },
})

// 监听 props 变化
watch(() => props.modelValue, (newValue) => {
  if (!editor.value) return
  const currentText = editor.value.getText()
  if (newValue !== currentText) {
    editor.value.commands.setContent(newValue || '')
  }
})

watch(() => props.disabled, (newValue) => {
  editor.value?.setEditable(!newValue)
})

watch(() => props.placeholder, (newValue) => {
  // Placeholder 扩展不支持动态更新，需要重新配置
  // 这里暂时不处理，因为 placeholder 通常不会动态变化
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
  editor.value.chain().focus().setImage({ src }).run()
}

// 获取纯文本
function getText(): string {
  return editor.value?.getText() || ''
}

// 设置内容
function setContent(text: string) {
  editor.value?.commands.setContent(text)
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
  getText,
  setContent,
  extractContent,
})
</script>

<style scoped>
.rich-text-input-wrapper {
  position: relative;
  width: 100%;
}

.rich-text-editor {
  width: 100%;
  min-height: 40px;
  max-height: 300px;
  overflow-y: auto;
}

/* ProseMirror 编辑器样式 */
.rich-text-editor :deep(.ProseMirror) {
  outline: none;
  font-size: 14px;
  line-height: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  color: var(--ide-foreground, #24292e);
  white-space: pre-wrap;
  word-break: break-word;
}

.rich-text-editor :deep(.ProseMirror p) {
  margin: 0;
}

/* 占位符样式 */
.rich-text-editor :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  color: var(--el-text-color-placeholder, var(--ide-text-disabled, #a8abb2));
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
  color: var(--el-color-primary, var(--ide-link, #409eff));
  text-decoration: none;
  cursor: pointer;
}

.rich-text-editor :deep(.linkified-link:hover) {
  text-decoration: underline;
}

/* 内嵌图片样式 */
.rich-text-editor :deep(.inline-image) {
  max-width: 64px;
  max-height: 64px;
  vertical-align: middle;
  border-radius: 4px;
  margin: 2px;
  cursor: pointer;
}

.rich-text-editor :deep(.inline-image:hover) {
  opacity: 0.9;
}

/* 暗色主题适配 */
:global(.theme-dark) .rich-text-editor :deep(.ProseMirror) {
  color: var(--ide-foreground, #e6edf3);
}

:global(.theme-dark) .rich-text-editor :deep(.linkified-link) {
  color: var(--el-color-primary, var(--ide-link, #58a6ff));
}

</style>
