<template>
  <div class="tool-display slash-command-tool">
    <div class="tool-header">
      <span class="tool-icon">⌨️</span>
      <span class="tool-name">SlashCommand</span>
      <code class="tool-command">{{ command }}</code>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="info-row">
        <span class="label">Command:</span>
        <span class="value">{{ command }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看命令和结果
const expanded = ref(false)

const command = computed(() => (props.toolCall.input as any)?.command || '')
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

.tool-command {
  background: #eef2ff;
  color: #4338ca;
  padding: 2px 6px;
  border-radius: 4px;
}

.tool-content {
  margin-top: 8px;
}

.info-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
}

.label {
  color: #586069;
  min-width: 70px;
}

.value {
  color: #24292e;
}
</style>
