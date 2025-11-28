<template>
  <div class="tool-display kill-shell-tool">
    <div class="tool-header">
      <span class="tool-icon">⚡</span>
      <span class="tool-name">KillShell</span>
      <span class="shell-id">Shell: {{ shellId }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="info-row">
        <span class="label">Shell ID:</span>
        <span class="value">{{ shellId }}</span>
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
// 默认折叠，点击后展开查看详情
const expanded = ref(false)

const shellId = computed(() => (props.toolCall.input as any)?.shell_id || '')
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

.shell-id {
  margin-left: auto;
  font-size: 12px;
  color: #586069;
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
