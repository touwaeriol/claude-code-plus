<template>
  <div class="error-result">
    <div class="error-header">
      <span class="error-icon">❌</span>
      <span class="error-title">{{ t('tools.error') }}</span>
    </div>
    <div class="error-content">
      <pre class="error-message">{{ errorMessage }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from '@/composables/useI18n'

defineProps<{
  errorMessage: string
}>()

const { t } = useI18n()
</script>

<style scoped>
.error-result {
  margin: 12px 0;
  padding: 12px 16px;
  background: var(--theme-error-background, #fef2f2);
  border: 1px solid var(--theme-error, #ef4444);
  border-radius: 8px;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.error-icon {
  font-size: 16px;
}

.error-title {
  font-weight: 600;
  color: var(--theme-error, #dc2626);
  font-size: 14px;
}

.error-content {
  background: var(--theme-background, #fff);
  border-radius: 4px;
  padding: 8px 12px;
}

.error-message {
  margin: 0;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--theme-error, #dc2626);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}
</style>
