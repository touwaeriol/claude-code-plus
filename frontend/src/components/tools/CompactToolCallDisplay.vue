<template>
  <div class="compact-tool-call-display">
    <div
      v-for="toolCall in toolCalls"
      :key="toolCall.id"
      class="tool-call-item"
    >
      <ToolCallDisplay :tool-call="toolCall" />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ToolCall } from '@/types/display'
import ToolCallDisplay from '@/components/chat/ToolCallDisplay.vue'

interface Props {
  toolCalls: ToolCall[]
}

defineProps<Props>()
</script>

<style scoped>
.compact-tool-call-display {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-call-item {
  padding: 4px;
  border-radius: 8px;
  background: var(--ide-panel-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
}
</style>
