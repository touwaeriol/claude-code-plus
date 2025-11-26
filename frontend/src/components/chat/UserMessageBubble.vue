<template>
  <div class="user-message-bubble">
    <!-- 时间戳 -->
    <!-- 隐藏时间戳，使消息展示更紧凑 -->
    <!-- <div class="timestamp">{{ formattedTime }}</div> -->
    
    <!-- 上下文引用（如果有）- 从左边开始排列 -->
    <div v-if="hasContexts" class="contexts-wrapper">
      <div class="contexts">
        <div v-for="(context, index) in contexts" :key="index" class="context-item">
          <span class="context-icon">{{ getContextIcon(context.type) }}</span>
          <span class="context-label">{{ context.label }}</span>
        </div>
      </div>
      
      <!-- 上下文图片预览（如果有） -->
      <div v-if="parsedMessage.contextImages.length > 0" class="context-images">
        <div
          v-for="(image, index) in parsedMessage.contextImages"
          :key="`context-image-${index}`"
          class="context-image-item"
        >
          <img
            :src="getImageSrc(image)"
            :alt="`Context image ${index + 1}`"
            class="context-image-preview"
            @click="openImagePreview(image)"
          />
        </div>
      </div>
    </div>
    
    <!-- 消息内容 -->
    <div class="bubble-content" :class="{ collapsed: isCollapsed && isLongMessage }">
      <!-- 文本内容 -->
      <div v-if="messageText" class="message-text">{{ messageText }}</div>

      <!-- 图片内容 -->
      <div v-if="imageBlocks.length > 0" class="message-images">
        <div
          v-for="(image, index) in imageBlocks"
          :key="index"
          class="image-item"
        >
          <img
            :src="getImageSrc(image)"
            :alt="`Image ${index + 1}`"
            class="message-image"
            @click="openImagePreview(image)"
          />
          <div class="image-info">
            <span class="image-name">{{ getImageName(image, index) }}</span>
            <span class="image-size">{{ getImageSize(image) }}</span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 折叠/展开按钮（长消息） -->
    <button
      v-if="isLongMessage"
      class="toggle-button"
      @click="toggleCollapse"
    >
      <span class="toggle-icon">{{ isCollapsed ? '▾' : '▴' }}</span>
      <span class="toggle-text">{{ isCollapsed ? '展开' : '收起' }}</span>
    </button>
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
import { ref, computed } from 'vue'
import type { Message, ImageBlock, ContentBlock } from '@/types/message'
import { parseUserMessage, isFileReference } from '@/utils/userMessageBuilder'
import ImagePreviewModal from '@/components/common/ImagePreviewModal.vue'

interface Props {
  message: Message
  contexts?: Array<{
    type: 'file' | 'folder' | 'url' | 'code'
    label: string
    path?: string
  }>
}

const props = defineProps<Props>()

// 折叠状态
const isCollapsed = ref(true)

// 解析用户消息，分离上下文和用户输入
const parsedMessage = computed(() => {
  if (!props.message.content || !Array.isArray(props.message.content)) {
    return {
      contexts: [],
      contextImages: [],
      userContent: []
    }
  }
  return parseUserMessage(props.message.content as ContentBlock[])
})

// 提取用户输入的文本内容（排除文件引用）
const messageText = computed(() => {
  const userContent = parsedMessage.value.userContent
  if (!userContent || userContent.length === 0) {
    return ''
  }

  // 从用户输入内容块中提取文本（排除文件引用）
  return userContent
    .filter(block => {
      if (block.type === 'text') {
        const text = (block as any).text?.trim() || ''
        // 排除文件引用格式的文本
        return !isFileReference(text)
      }
      return false
    })
    .map(block => (block as any).text)
    .join('\n')
})

// 提取用户输入的图片内容（排除上下文图片）
const imageBlocks = computed(() => {
  const userContent = parsedMessage.value.userContent
  if (!userContent || userContent.length === 0) {
    return []
  }

  // 只返回用户输入区域的图片（内嵌图片）
  return userContent
    .filter(block => block.type === 'image') as ImageBlock[]
})

// 提取上下文引用
const contexts = computed(() => {
  const parsed = parsedMessage.value
  const result: Array<{ type: string; label: string; path?: string }> = []
  
  // 添加文件上下文
  parsed.contexts.forEach(ctx => {
    if (ctx.type === 'file' && ctx.path) {
      result.push({
        type: 'file',
        label: ctx.path,
        path: ctx.path
      })
    }
  })
  
  // 添加图片上下文
  if (parsed.contextImages.length > 0) {
    result.push({
      type: 'image',
      label: `图片 (${parsed.contextImages.length})`
    })
  }
  
  return result
})

// 判断是否为长消息（超过 200 字符或有多张图片）
const isLongMessage = computed(() => {
  return messageText.value.length > 200 || imageBlocks.value.length > 2
})

// 是否有上下文引用
const hasContexts = computed(() => {
  return contexts.value.length > 0
})

// 格式化时间戳
const formattedTime = computed(() => {
  const date = new Date(props.message.timestamp)
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
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
.user-message-bubble {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  margin-bottom: 16px;
  max-width: 100%;
}

/* 上下文包装器 - 从左边开始排列 */
.contexts-wrapper {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  max-width: 80%;
}

.timestamp {
  font-size: 11px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.5));
  padding: 0 8px;
}

.bubble-content {
  background: #E3F2FD;
  border-radius: 12px;
  padding: 12px 16px;
  max-width: 80%;
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
  background: linear-gradient(to bottom, transparent, #E3F2FD);
}

.message-text {
  font-size: 14px;
  line-height: 1.5;
  color: var(--ide-foreground, #1a1a1a);
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 8px;
}

.message-text:last-child {
  margin-bottom: 0;
}

/* 图片显示样式 */
.message-images {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 8px;
}

.image-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  padding: 8px;
  transition: background 0.2s ease;
}

.image-item:hover {
  background: rgba(255, 255, 255, 0.8);
}

.message-image {
  max-width: 100%;
  max-height: 300px;
  border-radius: 6px;
  cursor: pointer;
  object-fit: contain;
  transition: transform 0.2s ease;
}

.message-image:hover {
  transform: scale(1.02);
}

.image-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.6));
}

.image-name {
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.image-size {
  margin-left: 8px;
  opacity: 0.7;
}

.contexts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: flex-start;
}

.context-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.7));
  background: rgba(255, 255, 255, 0.6);
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.context-icon {
  font-size: 14px;
}

.context-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 上下文图片预览 */
.context-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.context-image-item {
  flex-shrink: 0;
}

.context-image-preview {
  max-width: 100px;
  max-height: 100px;
  border-radius: 6px;
  cursor: pointer;
  object-fit: contain;
  border: 1px solid rgba(0, 0, 0, 0.1);
  transition: transform 0.2s ease;
}

.context-image-preview:hover {
  transform: scale(1.05);
}

.theme-dark .context-images {
  border-top-color: rgba(255, 255, 255, 0.1);
}

.theme-dark .context-image-preview {
  border-color: rgba(255, 255, 255, 0.1);
}

.toggle-button {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: transparent;
  border: 1px solid rgba(0, 0, 0, 0.2);
  border-radius: 6px;
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.6));
  cursor: pointer;
  transition: all 0.2s ease;
}

.toggle-button:hover {
  background: rgba(0, 0, 0, 0.05);
  border-color: rgba(0, 0, 0, 0.3);
}

.toggle-icon {
  font-size: 10px;
}

.toggle-text {
  font-size: 11px;
}

/* 暗色主题适配 */
.theme-dark .bubble-content {
  background: #1E3A5F;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.theme-dark .bubble-content.collapsed::after {
  background: linear-gradient(to bottom, transparent, #1E3A5F);
}

.theme-dark .message-text {
  color: var(--ide-foreground, #e0e0e0);
}

.theme-dark .timestamp {
  color: var(--ide-secondary-foreground, rgba(255, 255, 255, 0.5));
}

.theme-dark .context-item {
  color: var(--ide-secondary-foreground, rgba(255, 255, 255, 0.7));
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.1);
}

.theme-dark .toggle-button {
  border-color: rgba(255, 255, 255, 0.2);
  color: var(--ide-secondary-foreground, rgba(255, 255, 255, 0.6));
}

.theme-dark .toggle-button:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.3);
}
</style>

