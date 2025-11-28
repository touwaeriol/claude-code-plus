<template>
  <div class="tool-display webfetch-tool">
    <div class="tool-header">
      <span class="tool-icon">🌐</span>
      <span class="tool-name">WebFetch</span>
      <span class="tool-url">{{ urlPreview }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="info-row">
        <span class="label">URL:</span>
        <span class="value">{{ url }}</span>
      </div>
      <div class="info-row">
        <span class="label">Prompt:</span>
        <span class="value">{{ prompt }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ClaudeWebFetchToolCall } from '@/types/display'

interface Props {
  toolCall: ClaudeWebFetchToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看网页内容
const expanded = ref(false)

const url = computed(() => props.toolCall.input.url || '')
const prompt = computed(() => props.toolCall.input.prompt || '')
const urlPreview = computed(() => url.value.length > 50 ? `${url.value.slice(0, 50)}...` : url.value)
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  background: var(--ide-panel-background, #f6f8fa);
  margin: 8px 0;
  padding: 8px 12px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
}

.tool-url {
  color: #0366d6;
  font-size: 12px;
}

.tool-content {
  margin-top: 8px;
}

.info-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
  margin-bottom: 4px;
}

.label {
  color: #586069;
  min-width: 60px;
}

.value {
  color: #24292e;
}
</style>
