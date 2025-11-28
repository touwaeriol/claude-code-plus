<template>
  <div class="tool-display glob-tool">
    <div class="tool-header">
      <span class="tool-icon">🔍</span>
      <span class="tool-name">Glob</span>
      <code class="pattern">{{ pattern }}</code>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="glob-info">
        <div class="info-row">
          <span class="label">{{ t('tools.label.pattern') }}:</span>
          <code class="value">{{ pattern }}</code>
        </div>
        <div v-if="path" class="info-row">
          <span class="label">{{ t('tools.label.path') }}:</span>
          <span class="value">{{ path }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeGlobToolCall } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeGlobToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看文件列表
const expanded = ref(false)

const pattern = computed(() => props.toolCall.input.pattern || '')
const path = computed(() => props.toolCall.input.path || '')
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

.pattern {
  background: #eef2ff;
  color: #4338ca;
  padding: 2px 6px;
  border-radius: 4px;
}

.tool-content {
  margin-top: 8px;
}

.glob-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
}

.label {
  color: #586069;
  min-width: 60px;
}

.value {
  color: #24292e;
}
</style>
