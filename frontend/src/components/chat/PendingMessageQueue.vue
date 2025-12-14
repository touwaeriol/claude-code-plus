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
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useI18n } from '@/composables/useI18n'
import type { PendingMessage } from '@/types/session'

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

  // 输入框内容 - 不在此处截断，由 CSS 的 text-overflow: ellipsis 处理
  for (const block of msg.contents) {
    if (block.type === 'text' && 'text' in block) {
      parts.push((block as any).text as string)
    } else if (block.type === 'image') {
      parts.push('[Image]')
    }
  }

  return parts.join(' ') || '(空消息)'
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

.item-preview {
  flex: 1;
  min-width: 0;
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

</style>
