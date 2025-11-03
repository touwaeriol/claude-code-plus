<template>
  <div class="message-list" ref="listRef">
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-icon">💬</div>
      <div class="empty-text">开始与 Claude 对话</div>
      <div class="empty-hint">输入消息并按 Enter 发送</div>
    </div>

    <MessageDisplay
      v-for="message in messages"
      :key="message.id"
      :message="message"
      :isDark="isDark"
    />

    <div v-if="isLoading" class="loading-indicator">
      <div class="loading-spinner"></div>
      <span>Claude 正在思考...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import type { Message } from '@/types/message'
import MessageDisplay from './MessageDisplay.vue'

interface Props {
  messages: Message[]
  isLoading?: boolean
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isDark: false
})

const listRef = ref<HTMLElement>()

// 自动滚动到底部
watch(() => props.messages.length, async () => {
  await nextTick()
  scrollToBottom()
})

watch(() => props.isLoading, async (newValue) => {
  if (newValue) {
    await nextTick()
    scrollToBottom()
  }
})

function scrollToBottom() {
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #fafbfc;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #586069;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-text {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 8px;
}

.empty-hint {
  font-size: 14px;
  opacity: 0.7;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 8px;
  margin-top: 12px;
  color: #586069;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #e1e4e8;
  border-top-color: #0366d6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 滚动条样式 */
.message-list::-webkit-scrollbar {
  width: 8px;
}

.message-list::-webkit-scrollbar-track {
  background: transparent;
}

.message-list::-webkit-scrollbar-thumb {
  background: #d1d5da;
  border-radius: 4px;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: #959da5;
}
</style>
