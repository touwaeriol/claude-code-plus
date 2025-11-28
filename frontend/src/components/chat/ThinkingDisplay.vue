<template>
  <div class="thinking-display">
    <div class="thinking-header">
      <span class="thinking-icon">💭</span>
      <span class="thinking-label">{{ t('chat.thinkingLabel') }}</span>
    </div>
    <div class="thinking-content">
      <MarkdownRenderer
        :content="thinking.content"
        class="markdown-content"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import MarkdownRenderer from '../markdown/MarkdownRenderer.vue'
import type { ThinkingContent } from '@/types/display'
import { useI18n } from '@/composables/useI18n'

interface Props {
  thinking: ThinkingContent
}

const props = defineProps<Props>()
const { t } = useI18n()
</script>

<style scoped>
.thinking-display {
  width: 100%;
  margin: 0;
  padding: 6px 10px;
  background: rgba(107, 114, 128, 0.05); /* 淡灰色背景 */
  border-left: 3px solid rgba(107, 114, 128, 0.3); /* 灰色左边框 */
  border-radius: 4px;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px; /* 稍小一点 */
  font-weight: 500;
  color: #6b7280; /* 灰色 */
}

.thinking-icon {
  font-size: 14px; /* 稍小一点 */
  opacity: 0.7;
}

.thinking-label {
  opacity: 0.7; /* 更淡 */
  font-style: italic; /* 斜体 */
}

/* 深色模式下的思考块 */
@media (prefers-color-scheme: dark) {
  .thinking-display {
    background: rgba(156, 163, 175, 0.08);
    border-left-color: rgba(156, 163, 175, 0.3);
  }
  
  .thinking-header {
    color: #9ca3af;
  }
}

.thinking-content {
  font-size: 11px;
  color: #6b7280; /* 明确使用灰色，不依赖主题变量 */
  font-style: italic;
  line-height: 1.6;
  opacity: 0.85; /* 增加透明度让文字更"淡" */
}

.markdown-content {
  width: 100%;
}

/* 深色模式下的思考内容 */
@media (prefers-color-scheme: dark) {
  .thinking-content {
    color: #9ca3af; /* 深色模式下的灰色 */
  }
}
</style>
