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

defineProps<Props>()
const { t } = useI18n()
</script>

<style scoped>
.thinking-display {
  width: 100%;
  margin: 0;
  padding: 6px 10px;
  background: color-mix(in srgb, var(--theme-secondary-foreground) 8%, transparent);
  border-left: 3px solid color-mix(in srgb, var(--theme-secondary-foreground) 35%, transparent);
  border-radius: 4px;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-secondary-foreground);
}

.thinking-icon {
  font-size: 14px;
  opacity: 0.7;
}

.thinking-label {
  opacity: 0.7;
  font-style: italic;
}

.thinking-content {
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  font-style: italic;
  line-height: 1.6;
  opacity: 0.85;
}

.markdown-content {
  width: 100%;
}
</style>
