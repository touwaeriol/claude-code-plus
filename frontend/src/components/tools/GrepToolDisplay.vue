<template>
  <div class="tool-display grep-tool">
    <div class="tool-header">
      <span class="tool-icon">🔍</span>
      <span class="tool-name">Grep</span>
      <code class="tool-pattern">{{ pattern }}</code>
      <span v-if="glob" class="tool-glob">{{ glob }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="search-info">
        <div class="info-row">
          <span class="label">{{ t('tools.label.searchPattern') }}:</span>
          <code class="value">{{ pattern }}</code>
        </div>
        <div v-if="path" class="info-row">
          <span class="label">{{ t('tools.label.searchPath') }}:</span>
          <span class="value">{{ path }}</span>
        </div>
        <div v-if="glob" class="info-row">
          <span class="label">{{ t('tools.label.fileFilter') }}:</span>
          <code class="value">{{ glob }}</code>
        </div>
        <div v-if="type" class="info-row">
          <span class="label">{{ t('tools.label.fileType') }}:</span>
          <span class="value">{{ type }}</span>
        </div>
        <div v-if="outputMode" class="info-row">
          <span class="label">{{ t('tools.label.outputMode') }}:</span>
          <span class="value">{{ outputModeText }}</span>
        </div>
      </div>
      <div v-if="options.length > 0" class="search-options">
        <span v-for="opt in options" :key="opt" class="option-badge">{{ opt }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeGrepToolCall } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeGrepToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看搜索结果
const expanded = ref(false)

const pattern = computed(() => props.toolCall.input.pattern || '')
const path = computed(() => props.toolCall.input.path || '')
const glob = computed(() => props.toolCall.input.glob || '')
const type = computed(() => props.toolCall.input.type || '')
const outputMode = computed(() => props.toolCall.input.output_mode || 'files_with_matches')

const outputModeText = computed(() => {
  const map: Record<string, string> = {
    'content': t('tools.grepTool.outputModes.content'),
    'files_with_matches': t('tools.grepTool.outputModes.filesWithMatches'),
    'count': t('tools.grepTool.outputModes.count')
  }
  return map[outputMode.value] || outputMode.value
})

const options = computed(() => {
  const opts: string[] = []
  const input = props.toolCall.input as any
  if (input['-i']) opts.push(t('tools.grepTool.options.ignoreCase'))
  if (input['-n']) opts.push(t('tools.grepTool.options.showLineNumbers'))
  if (input.multiline) opts.push(t('tools.grepTool.options.multiline'))
  return opts
})
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

.tool-pattern {
  background: #eef2ff;
  color: #4338ca;
  padding: 2px 6px;
  border-radius: 4px;
}

.tool-glob {
  margin-left: auto;
  font-size: 12px;
  color: #586069;
}

.tool-content {
  margin-top: 8px;
}

.search-info {
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
  min-width: 72px;
}

.value {
  color: #24292e;
}

.search-options {
  margin-top: 6px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.option-badge {
  padding: 2px 6px;
  border-radius: 10px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
}
</style>
