<template>
  <div class="tool-display exit-plan-tool">
    <div class="tool-header">
      <span class="tool-icon">🛑</span>
      <span class="tool-name">ExitPlanMode</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="section-title">Plan</div>
      <pre>{{ plan }}</pre>
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
// 默认折叠，点击后展开查看计划内容
const expanded = ref(false)

const plan = computed(() => (props.toolCall.input as any)?.plan || '')
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

.tool-content {
  margin-top: 8px;
}

.section-title {
  font-weight: 600;
  font-size: 12px;
  margin-bottom: 4px;
}

pre {
  margin: 0;
  padding: 6px;
  background: #fff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
}
</style>
