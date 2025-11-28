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
        <span class="item-preview">{{ formatPreview(msg) }}</span>
        <div class="item-actions">
          <button
            class="action-btn"
            :title="t('common.edit')"
            @click="emit('edit', msg.id)"
          >
            ✏️
          </button>
          <button
            class="action-btn"
            :title="t('common.delete')"
            @click="emit('remove', msg.id)"
          >
            🗑️
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useI18n } from '@/composables/useI18n'
import type { PendingMessage } from '@/types/session'

const { t } = useI18n()
const sessionStore = useSessionStore()
const messageQueue = computed(() => sessionStore.messageQueue)

const emit = defineEmits<{
  (e: 'edit', id: string): void
  (e: 'remove', id: string): void
}>()

/**
 * 格式化消息预览（一行显示）
 */
function formatPreview(msg: PendingMessage): string {
  const parts: string[] = []

  // 上下文
  if (msg.contexts?.length) {
    for (const ctx of msg.contexts) {
      if (ctx.type === 'image') {
        parts.push('[Img]')
      } else if (ctx.type === 'file') {
        const fileName = ctx.uri?.split('/').pop() || ctx.uri
        parts.push(`[@${fileName}]`)
      }
    }
  }

  // 输入框内容
  for (const block of msg.contents) {
    if (block.type === 'text' && 'text' in block) {
      const text = (block as any).text as string
      const truncated = text.length > 30 ? text.slice(0, 30) + '...' : text
      parts.push(truncated)
    } else if (block.type === 'image') {
      parts.push('[Image]')
    }
  }

  return parts.join(' ') || '(空消息)'
}
</script>

<style scoped>
.pending-queue {
  background: var(--ide-editorWidget-background, #f3f4f6);
  border: 1px solid var(--ide-editorWidget-border, #e5e7eb);
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
  color: var(--ide-descriptionForeground, #6b7280);
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
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
  font-size: 13px;
  background: var(--ide-background, #ffffff);
  transition: background 0.15s;
}

.queue-item:hover {
  background: var(--ide-list-hoverBackground, #f9fafb);
}

.item-index {
  color: var(--ide-descriptionForeground, #9ca3af);
  min-width: 20px;
  font-weight: 500;
}

.item-preview {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ide-foreground, #374151);
}

.item-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.queue-item:hover .item-actions {
  opacity: 1;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 6px;
  font-size: 14px;
  border-radius: 4px;
  line-height: 1;
  transition: background 0.15s;
}

.action-btn:hover {
  background: var(--ide-toolbar-hoverBackground, #e5e7eb);
}

/* 暗色主题适配 */
:global(.theme-dark) .pending-queue {
  background: var(--ide-editorWidget-background, #1f2937);
  border-color: var(--ide-editorWidget-border, #374151);
}

:global(.theme-dark) .queue-item {
  background: var(--ide-background, #111827);
}

:global(.theme-dark) .queue-item:hover {
  background: var(--ide-list-hoverBackground, #1f2937);
}

:global(.theme-dark) .action-btn:hover {
  background: var(--ide-toolbar-hoverBackground, #374151);
}
</style>
