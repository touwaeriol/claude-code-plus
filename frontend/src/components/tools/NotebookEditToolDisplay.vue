<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="notebook-details">
        <!-- 参数区域 -->
        <div class="params-section">
          <div class="info-row">
            <span class="label">Path:</span>
            <span class="value path">{{ notebookPath }}</span>
          </div>
          <div class="info-row">
            <span class="label">Cell:</span>
            <span class="value">#{{ cellId }} ({{ cellType }})</span>
          </div>
          <div class="info-row">
            <span class="label">Mode:</span>
            <span class="value badge">{{ editMode }}</span>
          </div>
          <div v-if="newSource" class="source-section">
            <div class="section-title">New Content</div>
            <pre class="source-content">{{ newSource }}</pre>
          </div>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">Result</div>
          <pre class="result-content">{{ resultText }}</pre>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const notebookPath = computed(() => props.toolCall.input?.notebook_path || '')
const cellId = computed(() => props.toolCall.input?.cell_id || props.toolCall.input?.cell_number || 0)
const newSource = computed(() => props.toolCall.input?.new_source || '')
const cellType = computed(() => props.toolCall.input?.cell_type || 'code')
const editMode = computed(() => props.toolCall.input?.edit_mode || 'replace')

const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  return JSON.stringify(r.content, null, 2)
})

const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

const hasDetails = computed(() => !!notebookPath.value)
</script>

<style scoped>
.notebook-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  align-items: baseline;
}

.label {
  color: var(--theme-secondary-foreground, #586069);
  min-width: 50px;
  flex-shrink: 0;
}

.value {
  color: var(--theme-foreground, #24292e);
}

.value.path {
  font-family: monospace;
  word-break: break-all;
}

.value.badge {
  background: var(--theme-badge-background, #eef2ff);
  color: var(--theme-badge-foreground, #4338ca);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
}

.source-section {
  margin-top: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.source-content {
  margin: 0;
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

.result-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
}

.result-content {
  margin: 0;
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
}
</style>
