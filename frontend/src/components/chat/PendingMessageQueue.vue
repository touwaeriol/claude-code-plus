<template>
  <div v-if="messageQueue.length > 0" class="pending-queue">
    <!-- 标题 -->
    <div class="queue-header">
      <span class="queue-icon">📋</span>
      <span class="queue-title">{{ t('chat.pendingQueue', { count: messageQueue.length }) }}</span>
    </div>

    <!-- 列表 -->
    <div class="queue-list">
      <div
        v-for="(msg, index) in messageQueue"
        :key="msg.id"
        class="queue-item"
      >
        <span class="item-index">{{ index + 1 }}.</span>
        <!-- 按原始顺序渲染内容：contexts → 文本 → 图片 -->
        <div class="item-content">
          <template v-for="(item, itemIndex) in getOrderedPreviewItems(msg)" :key="itemIndex">
            <!-- 图片缩略图 -->
            <img
              v-if="item.type === 'image'"
              :src="item.src"
              class="item-image-thumb"
              alt="图片"
              @click="openImagePreview(item.src!)"
            />
            <!-- 文本/标签 -->
            <span v-else class="item-text">{{ item.text }}</span>
          </template>
        </div>
        <div class="item-actions">
          <!-- 打断并发送按钮 -->
          <button
            class="action-btn force-send-btn"
            :title="t('chat.interruptAndSend')"
            @click="emit('force-send', msg.id)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="19" x2="12" y2="5"/>
              <polyline points="5 12 12 5 19 12"/>
            </svg>
          </button>
          <!-- 编辑按钮 -->
          <button
            class="action-btn edit-btn"
            :title="t('common.edit')"
            @click="emit('edit', msg.id)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          </button>
          <!-- 删除按钮 -->
          <button
            class="action-btn delete-btn"
            :title="t('common.delete')"
            @click="emit('remove', msg.id)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              <line x1="10" y1="11" x2="10" y2="17"/>
              <line x1="14" y1="11" x2="14" y2="17"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 图片预览弹窗 -->
    <div v-if="previewImage" class="image-preview-overlay" @click="closeImagePreview">
      <button class="preview-close-btn" @click="closeImagePreview">×</button>
      <img :src="previewImage" class="preview-image" alt="预览图片" @click.stop />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useI18n } from '@/composables/useI18n'
import type { PendingMessage } from '@/types/session'
// XML 解析工具已不再需要，IDE 上下文现在直接从 msg.ideContext 读取

const { t } = useI18n()
const sessionStore = useSessionStore()

/**
 * 待发送消息队列
 * 只在生成中用户发送新消息时才有内容
 */
const messageQueue = computed(() => sessionStore.messageQueue)

const emit = defineEmits<{
  (e: 'edit', id: string): void
  (e: 'remove', id: string): void
  (e: 'force-send', id: string): void
}>()

// 图片预览状态
const previewImage = ref<string | null>(null)

function openImagePreview(src: string) {
  previewImage.value = src
}

function closeImagePreview() {
  previewImage.value = null
}

/**
 * 从路径中提取文件名
 */
function getFileName(filePath: string): string {
  const parts = filePath.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || filePath
}

/**
 * 预览项类型
 */
interface PreviewItem {
  type: 'text' | 'image'
  text?: string
  src?: string
}

/**
 * 按原始顺序生成预览项
 * 顺序：IDE 上下文 → contexts（文件标签、图片）→ contents（文本、图片）
 *
 * 重构后：直接从 msg.ideContext 读取 IDE 上下文，不再解析 XML
 */
function getOrderedPreviewItems(msg: PendingMessage): PreviewItem[] {
  const items: PreviewItem[] = []

  // 1. 处理 IDE 上下文（直接从 ideContext 读取，不再解析 XML）
  if (msg.ideContext) {
    const file = msg.ideContext
    const fileName = getFileName(file.relativePath)

    if (file.hasSelection && file.startLine && file.endLine) {
      // 有选区：显示行号范围
      const startCol = file.startColumn || 1
      const endCol = file.endColumn || 1
      items.push({ type: 'text', text: `[${fileName}:${file.startLine}:${startCol}-${file.endLine}:${endCol}]` })
    } else if (file.line) {
      // 有光标位置
      const col = file.column || 1
      items.push({ type: 'text', text: `[${fileName}:${file.line}:${col}]` })
    } else {
      // 只有文件名
      items.push({ type: 'text', text: `[${fileName}]` })
    }
  }

  // 2. contexts（文件标签、图片）- 按原始顺序
  if (msg.contexts?.length) {
    for (const ctx of msg.contexts) {
      if (ctx.type === 'file') {
        const fileName = ctx.uri?.split('/').pop() || ctx.uri
        items.push({ type: 'text', text: `[@${fileName}]` })
      } else if (ctx.type === 'image' && (ctx as any).base64Data) {
        const mimeType = (ctx as any).mimeType || 'image/png'
        items.push({ type: 'image', src: `data:${mimeType};base64,${(ctx as any).base64Data}` })
      }
    }
  }

  // 3. contents（文本、图片）- 直接显示，不再有 XML 需要过滤
  for (const block of msg.contents) {
    if (block.type === 'text' && 'text' in block) {
      const text = ((block as any).text as string).trim()
      if (text) {
        items.push({ type: 'text', text })
      }
    } else if (block.type === 'image' && 'source' in block) {
      const source = (block as any).source
      if (source?.type === 'base64' && source.data) {
        const mimeType = source.media_type || 'image/png'
        items.push({ type: 'image', src: `data:${mimeType};base64,${source.data}` })
      }
    }
  }

  // 如果没有任何内容，显示空消息提示
  if (items.length === 0) {
    items.push({ type: 'text', text: '(空消息)' })
  }

  return items
}
</script>

<style scoped>
.pending-queue {
  width: 100%;
  box-sizing: border-box;
  background: var(--theme-panel-background, var(--theme-background));
  border: 1px solid var(--theme-border);
  border-radius: 6px;
  margin: 4px 0;
  padding: 8px 12px;
}

.queue-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-secondary-foreground);
  margin-bottom: 6px;
}

.queue-icon {
  font-size: 14px;
}

.queue-title {
  flex: 1;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 13px;
  background: var(--theme-background);
  transition: background 0.15s;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.queue-item:hover {
  background: var(--theme-hover-background);
}

.item-index {
  color: var(--theme-secondary-foreground);
  min-width: 20px;
  font-weight: 500;
  flex-shrink: 0;
}

/* 内容区域 - 按顺序显示文本和图片 */
.item-content {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
}

.item-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--theme-foreground);
}

.item-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  line-height: 1;
  transition: all 0.15s;
  color: var(--theme-secondary-foreground);
}

.action-btn:hover {
  background: var(--theme-hover-background);
}

/* 打断并发送按钮 - 绿色 */
.action-btn.force-send-btn {
  color: var(--theme-success, #22c55e);
}

.action-btn.force-send-btn:hover {
  background: rgba(34, 197, 94, 0.15);
  color: var(--theme-success, #16a34a);
}

/* 编辑按钮 - 蓝色 */
.action-btn.edit-btn {
  color: var(--theme-accent, #0366d6);
}

.action-btn.edit-btn:hover {
  background: rgba(3, 102, 214, 0.15);
  color: var(--theme-accent, #0550ae);
}

/* 删除按钮 - 红色 */
.action-btn.delete-btn {
  color: var(--theme-error, #ef4444);
}

.action-btn.delete-btn:hover {
  background: rgba(239, 68, 68, 0.15);
  color: var(--theme-error, #dc2626);
}

/* 图片缩略图 */
.item-image-thumb {
  width: 20px;
  height: 20px;
  object-fit: cover;
  border-radius: 3px;
  border: 1px solid var(--theme-border);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.item-image-thumb:hover {
  transform: scale(1.1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

/* 图片预览弹窗 */
.image-preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  cursor: pointer;
}

.preview-image {
  max-width: 90vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 4px;
  cursor: default;
}

.preview-close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  color: #333;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s, background 0.15s;
}

.preview-close-btn:hover {
  transform: scale(1.1);
  background: #fff;
}

</style>
