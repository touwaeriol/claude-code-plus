<template>
  <div
    class="user-message-row"
    :class="{ 'is-editing': isEditing }"
    @mouseenter="showEditButton = true"
    @mouseleave="showEditButton = false"
  >
    <!-- 显示模式 -->
    <template v-if="!isEditing">
      <!-- 左侧占位元素，填充空白区域使整行可交互 -->
      <div class="row-spacer"></div>
      <div class="message-wrapper">
        <!-- 编辑按钮（悬浮显示，紧贴气泡左侧） -->
        <button
          v-show="showEditButton"
          class="edit-button"
          title="编辑消息"
          @click="enterEditMode"
        >
          <span class="edit-icon">✏️</span>
        </button>

        <!-- 消息气泡 -->
        <div class="user-message-bubble">
          <!-- 单一气泡容器 -->
          <div class="bubble-content" :class="{ collapsed: isCollapsed && isLongMessage }">
            <!-- 上下文图片（在文字上方） -->
            <div v-if="contextImagesAsBlocks.length > 0" class="context-images">
              <img
                v-for="(image, index) in contextImagesAsBlocks"
                :key="`ctx-${index}`"
                :src="getImageSrc(image)"
                :alt="`Context image ${index + 1}`"
                class="context-thumb"
                @click="openImagePreview(image)"
              />
            </div>

            <!-- 文本内容（支持链接渲染） -->
            <div
              v-if="messageText"
              class="message-text"
              v-html="renderedText"
              @click="handleMessageClick"
            ></div>

            <!-- 内嵌图片（用户输入的图片） -->
            <div v-if="imageBlocks.length > 0" class="inline-images">
              <img
                v-for="(image, index) in imageBlocks"
                :key="`img-${index}`"
                :src="getImageSrc(image)"
                :alt="`Image ${index + 1}`"
                class="inline-thumb"
                @click="openImagePreview(image)"
              />
            </div>
          </div>

          <!-- 折叠/展开按钮 -->
          <button
            v-if="isLongMessage"
            class="toggle-button"
            @click="toggleCollapse"
          >
            {{ isCollapsed ? '展开 ▾' : '收起 ▴' }}
          </button>
        </div>
      </div>
    </template>

    <!-- 编辑模式：内嵌 ChatInput -->
    <div v-else ref="editorContainerRef" class="inline-editor-container">
      <ChatInput
        ref="chatInputRef"
        :inline="true"
        :edit-disabled="true"
        :contexts="editContexts"
        :show-context-controls="true"
        :show-model-selector="true"
        :show-permission-controls="true"
        @send="handleEditSubmit"
        @cancel="exitEditMode"
        @context-add="handleContextAdd"
        @context-remove="handleContextRemove"
      />
    </div>
  </div>

  <!-- 图片预览模态框 -->
  <ImagePreviewModal
    :visible="previewVisible"
    :image-src="previewImageSrc"
    :image-alt="previewImageAlt"
    @close="closeImagePreview"
  />
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import type { ImageBlock, ContentBlock } from '@/types/message'
import type { ContextReference } from '@/types/display'
import { isFileReference } from '@/utils/userMessageBuilder'
import { linkifyText, getLinkFromEvent, handleLinkClick } from '@/utils/linkify'
import ImagePreviewModal from '@/components/common/ImagePreviewModal.vue'
import ChatInput from './ChatInput.vue'
import { ideaBridge } from '@/services/ideaBridge'

// 兼容 Message 和 UserMessage (DisplayItem) 类型
interface Props {
  message: {
    id?: string
    content?: ContentBlock[]
    contexts?: ContextReference[]  // DisplayItem 中的 contexts 已经包含图片
    [key: string]: unknown
  }
}

const props = defineProps<Props>()

// 折叠状态
const isCollapsed = ref(true)

// 编辑模式状态
const isEditing = ref(false)
const showEditButton = ref(false)
const chatInputRef = ref<InstanceType<typeof ChatInput>>()
const editorContainerRef = ref<HTMLDivElement>()

// 编辑时的上下文（从原始消息复制，可在编辑时修改）
const editContexts = ref<ContextReference[]>([])

// 点击外部退出编辑模式
onClickOutside(editorContainerRef, () => {
  if (isEditing.value) {
    exitEditMode()
  }
})

// 进入编辑模式
function enterEditMode() {
  isEditing.value = true

  // 复制原始上下文到编辑上下文
  editContexts.value = props.message.contexts ? [...props.message.contexts] : []

  // 恢复内容到输入框
  nextTick(() => {
    if (chatInputRef.value && props.message.content) {
      chatInputRef.value.setContent(props.message.content)
    }
  })
}

// 退出编辑模式
function exitEditMode() {
  isEditing.value = false
  editContexts.value = []
}

// 处理编辑提交（当前阶段禁用，预留接口）
function handleEditSubmit(_contents: ContentBlock[]) {
  // 当前阶段不实现，预留接口
  // 后续开放时：调用 sessionStore 更新消息
  exitEditMode()
}

// 处理上下文添加
function handleContextAdd(context: ContextReference) {
  editContexts.value.push(context)
}

// 处理上下文移除
function handleContextRemove(context: ContextReference) {
  const index = editContexts.value.findIndex(c => c.uri === context.uri)
  if (index !== -1) {
    editContexts.value.splice(index, 1)
  }
}

// 从 DisplayItem.contexts 中提取图片上下文（ContextReference 类型）
const contextImageRefs = computed(() => {
  const ctxs = props.message.contexts
  if (!ctxs || !Array.isArray(ctxs)) return []
  return ctxs.filter(ctx => ctx.type === 'image' && ctx.base64Data)
})

// 将 ContextReference 转换为 ImageBlock 格式（用于复用 getImageSrc 等函数）
const contextImagesAsBlocks = computed((): ImageBlock[] => {
  return contextImageRefs.value.map(ctx => ({
    type: 'image' as const,
    source: {
      type: 'base64' as const,
      media_type: ctx.mimeType || 'image/png',
      data: ctx.base64Data || ''
    }
  }))
})

// 提取用户输入的文本内容（排除文件引用）
const messageText = computed(() => {
  const content = props.message.content
  if (!content || !Array.isArray(content)) {
    return ''
  }

  // 从用户输入内容块中提取文本（排除文件引用）
  return content
    .filter(block => {
      if (block.type === 'text' && 'text' in block) {
        const text = (block as any).text?.trim() || ''
        // 排除文件引用格式的文本
        return !isFileReference(text)
      }
      return false
    })
    .map(block => {
      if (block.type === 'text' && 'text' in block) {
        return (block as any).text
      }
      return ''
    })
    .join('\n')
})

// 渲染后的文本（带链接）
const renderedText = computed(() => {
  if (!messageText.value) return ''
  const result = linkifyText(messageText.value)
  return result.html
})

// 处理消息文本中的链接点击
function handleMessageClick(event: MouseEvent) {
  const linkInfo = getLinkFromEvent(event)
  if (!linkInfo) return

  event.preventDefault()

  // 打开链接
  handleLinkClick(linkInfo.href, linkInfo.type, (filePath) => {
    // 文件路径：调用 IDE 打开文件
    ideaBridge.query('ide.openFile', { filePath })
  })
}

// 提取用户输入的图片内容（内嵌图片，在 content 中的图片）
const imageBlocks = computed(() => {
  const content = props.message.content
  if (!content || !Array.isArray(content)) {
    return []
  }

  // 返回 content 中的图片块
  return content.filter(block => block.type === 'image') as ImageBlock[]
})

// 提取上下文引用（用于显示标签）
const contexts = computed(() => {
  const ctxs = props.message.contexts
  if (!ctxs || !Array.isArray(ctxs)) return []

  const result: Array<{ type: string; label: string; path?: string }> = []

  // 添加文件上下文
  ctxs.forEach(ctx => {
    if (ctx.type === 'file' && ctx.path) {
      result.push({
        type: 'file',
        label: ctx.path,
        path: ctx.path
      })
    }
  })

  // 添加图片上下文计数
  const imageCount = contextImageRefs.value.length
  if (imageCount > 0) {
    result.push({
      type: 'image',
      label: `图片 (${imageCount})`
    })
  }

  return result
})

// 判断是否为长消息（超过 200 字符或有多张图片）
const isLongMessage = computed(() => {
  return messageText.value.length > 200 || imageBlocks.value.length > 2
})

// 是否有上下文引用（包括文件上下文和图片上下文）
const hasContexts = computed(() => {
  return contexts.value.length > 0 || contextImagesAsBlocks.value.length > 0
})

// 切换折叠状态
function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

// 获取上下文图标
function getContextIcon(type: string): string {
  const icons: Record<string, string> = {
    file: '📄',
    folder: '📁',
    url: '🔗',
    code: '💻'
  }
  return icons[type] || '📎'
}

// 获取图片源地址
function getImageSrc(image: ImageBlock): string {
  if (image.source.type === 'url' && image.source.url) {
    return image.source.url
  }
  if (image.source.type === 'base64' && image.source.data) {
    return `data:${image.source.media_type};base64,${image.source.data}`
  }
  return ''
}

// 获取图片名称
function getImageName(image: ImageBlock, index: number): string {
  if (image.source.type === 'url' && image.source.url) {
    const urlParts = image.source.url.split('/')
    return urlParts[urlParts.length - 1] || `image-${index + 1}`
  }
  const ext = image.source.media_type.split('/')[1] || 'png'
  return `image.${ext}`
}

// 获取图片大小（估算）
function getImageSize(image: ImageBlock): string {
  if (image.source.type === 'base64' && image.source.data) {
    // Base64 编码后的大小约为原始大小的 4/3
    const bytes = (image.source.data.length * 3) / 4
    if (bytes < 1024) return `${bytes.toFixed(0)} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }
  return ''
}

// 图片预览状态
const previewVisible = ref(false)
const previewImageSrc = ref('')
const previewImageAlt = ref('')

// 打开图片预览
function openImagePreview(image: ImageBlock) {
  const src = getImageSrc(image)
  if (src) {
    previewImageSrc.value = src
    previewImageAlt.value = getImageName(image, 0)
    previewVisible.value = true
  }
}

// 关闭图片预览
function closeImagePreview() {
  previewVisible.value = false
}
</script>

<style scoped>
/* 消息行容器 */
.user-message-row {
  display: flex;
  align-items: flex-start;
  padding: 4px 12px;
  width: 100%;
  box-sizing: border-box;
}

/* 左侧占位元素 - 填充空白使整行可交互 */
.row-spacer {
  flex: 1;
  min-width: 0;
}

/* 消息包装器 - 编辑按钮+气泡作为整体 */
.message-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 95%;
  flex-shrink: 0;
}

/* 编辑按钮 - flex 项，紧贴气泡左侧 */
.edit-button {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.user-message-row:hover .edit-button {
  opacity: 1;
}

.edit-button:hover {
  background: var(--theme-hover-background);
}

.edit-icon {
  font-size: 14px;
}

/* 内嵌编辑器容器 */
.inline-editor-container {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}

/* 编辑模式下的行样式 */
.user-message-row.is-editing {
  justify-content: stretch;
}

.user-message-bubble {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  max-width: 100%;
}

.timestamp {
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  padding: 0 8px;
}

.bubble-content {
  background: var(--theme-selection-background);
  border-radius: 12px;
  padding: 10px 14px;
  max-width: 100%;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  transition: all 0.2s ease;
}

.bubble-content.collapsed {
  max-height: 120px;
  overflow: hidden;
  position: relative;
}

.bubble-content.collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 40px;
  background: linear-gradient(to bottom, transparent, var(--theme-selection-background));
}

.message-text {
  font-size: 13px;
  line-height: 1.4;
  color: var(--theme-selection-foreground);
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 6px;
  user-select: text;
  -webkit-user-select: text;
}

/* 用户消息内的文本选中样式 - 使用对比色 */
.bubble-content ::selection {
  background: var(--theme-accent, #0366d6);
  color: #ffffff;
}

.message-text:last-child {
  margin-bottom: 0;
}

/* 链接样式 */
.message-text :deep(.linkified-link) {
  color: var(--theme-link);
  text-decoration: none;
  cursor: pointer;
}

.message-text :deep(.linkified-link:hover) {
  text-decoration: underline;
}

/* 文件路径链接 */
.message-text :deep(.file-link) {
  background: var(--theme-hover-background);
  padding: 1px 6px;
  border-radius: 3px;
}

/* 上下文图片 - 横向排列 */
.context-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

/* 上下文缩略图 */
.context-thumb {
  width: 32px;
  height: 32px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid var(--theme-border);
  transition: transform 0.2s;
}

.context-thumb:hover {
  transform: scale(1.05);
}

/* 内嵌图片 - 横向排列 */
.inline-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.inline-thumb {
  max-width: 80px;
  max-height: 80px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  transition: transform 0.2s;
}

.inline-thumb:hover {
  transform: scale(1.02);
}


.toggle-button {
  padding: 4px 10px;
  margin-top: 6px;
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  transition: all 0.2s ease;
}

.toggle-button:hover {
  background: var(--theme-hover-background);
  border-color: var(--theme-border);
}

</style>

