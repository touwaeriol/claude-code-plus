<template>
  <div class="user-message">
    <!-- 上下文引用 -->
    <div
      v-if="message.contexts && message.contexts.length > 0"
      class="message-contexts"
    >
      <div
        v-for="(context, index) in message.contexts"
        :key="`context-${index}`"
        class="context-chip"
      >
        {{ getContextDisplay(context) }}
      </div>
    </div>

    <!-- 图片 -->
    <div
      v-if="inlineImages.length > 0"
      class="message-images"
    >
      <img
        v-for="(image, index) in inlineImages"
        :key="`image-${index}`"
        :src="getImageSrc(image)"
        alt="User uploaded image"
        class="message-image"
      />
    </div>

    <!-- 文本内容 -->
    <div
      v-if="textContent"
      class="message-content"
    >
      {{ textContent }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { UserMessage } from '@/types/display'
import type { ContentBlock, ImageBlock, TextBlock } from '@/types/message'

interface Props {
  message: UserMessage
}

const props = defineProps<Props>()

// 解析文本内容（保持用户输入顺序）
const textContent = computed(() => {
  if (!props.message.content || props.message.content.length === 0) {
    return ''
  }

  return props.message.content
    .filter((block): block is TextBlock => block.type === 'text')
    .map(block => block.text ?? '')
    .join('\n\n')
    .trim()
})

// 提取内嵌图片（用户上传）
const inlineImages = computed(() => {
  if (!props.message.content) return []
  return props.message.content.filter(isImageBlock)
})

function getContextDisplay(context: any): string {
  if (context.type === 'file') {
    return context.path || context.uri
  } else if (context.type === 'web') {
    return context.title || context.url || context.uri
  } else if (context.type === 'folder') {
    return context.path || context.uri
  }
  return context.uri
}

function getImageSrc(image: ImageBlock): string {
  if (image.source.type === 'base64' && image.source.data) {
    return `data:${image.source.media_type};base64,${image.source.data}`
  } else if (image.source.type === 'url' && image.source.url) {
    return image.source.url
  }
  return ''
}

function isImageBlock(block: ContentBlock): block is ImageBlock {
  return block.type === 'image'
}
</script>

<style scoped>
.user-message {
  margin: 16px 0;
  padding: 12px 16px;
  /* 右对齐 */
  margin-left: auto;
  max-width: 70%;
  /* 特殊背景色 - 浅蓝色气泡 */
  background: linear-gradient(135deg, #e3f2fd 0%, #bbdefb 100%);
  border-radius: 12px;
  /* 添加轻微阴影 */
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

/* 深色主题适配 */
:global(.theme-dark) .user-message {
  background: linear-gradient(135deg, #1e3a5f 0%, #2c5282 100%);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 11px;
  color: #666;
}

/* 深色主题时间戳 */
:global(.theme-dark) .message-header {
  color: #aaa;
}

.timestamp {
  opacity: 0.8;
}

.message-contexts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
  justify-content: flex-end;
}

.context-chip {
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 4px;
  font-size: 11px;
  color: #333;
}

/* 深色主题上下文芯片 */
:global(.theme-dark) .context-chip {
  background: rgba(255, 255, 255, 0.1);
  color: #ddd;
}

.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  justify-content: flex-end;
}

.message-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  object-fit: contain;
  border: 2px solid rgba(255, 255, 255, 0.5);
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  color: #1a1a1a;
  font-size: 14px;
}

/* 深色主题消息内容 */
:global(.theme-dark) .message-content {
  color: #e0e0e0;
}
</style>

