<template>
  <div class="tool-display notebook-edit-tool">
    <div class="tool-header">
      <span class="tool-icon">📒</span>
      <span class="tool-name">NotebookEdit</span>
      <span class="file-path">{{ notebookPath }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="info-row">
        <span class="label">{{ t('tools.label.path') }}:</span>
        <span class="value">{{ notebookPath }}</span>
      </div>
      <div class="info-row">
        <span class="label">{{ t('tools.label.cell') }}:</span>
        <span class="value">#{{ cellId }} ({{ cellType }})</span>
      </div>
      <div class="info-row">
        <span class="label">{{ t('tools.label.mode') }}:</span>
        <span class="value">{{ editMode }}</span>
      </div>
      <div class="section-title">{{ t('tools.newContent') }}</div>
      <pre>{{ newSource }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { GenericToolCall } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看 Cell 内容
const expanded = ref(false)

const notebookPath = computed(() => (props.toolCall.input as any)?.notebook_path || '')
const cellId = computed(() => (props.toolCall.input as any)?.cell_id || (props.toolCall.input as any)?.cell_number || 0)
const newSource = computed(() => (props.toolCall.input as any)?.new_source || '')
const cellType = computed(() => (props.toolCall.input as any)?.cell_type || 'code')
const editMode = computed(() => (props.toolCall.input as any)?.edit_mode || 'replace')
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

.file-path {
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
  margin-bottom: 4px;
}

.label {
  color: #586069;
  min-width: 60px;
}

.value {
  color: #24292e;
}

.section-title {
  margin-top: 6px;
  font-weight: 600;
  font-size: 12px;
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
