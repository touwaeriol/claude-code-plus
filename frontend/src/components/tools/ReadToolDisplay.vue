<template>
  <div class="tool-display read-tool">
    <div class="tool-header">
      <span class="tool-icon">📄</span>
      <span class="tool-name">Read</span>
      <span class="tool-file">{{ fileName }}</span>
      <span
        v-if="lineRange"
        class="tool-lines"
      >{{ lineRange }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="file-info">
        <div class="info-row">
          <span class="label">路径:</span>
          <span
            class="value clickable"
            @click="openFile"
          >{{ filePath }}</span>
        </div>
        <div
          v-if="hasLineRange"
          class="info-row"
        >
          <span class="label">行数:</span>
          <span class="value">{{ lineRange }}</span>
        </div>
      </div>
      <div
        v-if="result"
        class="tool-result"
      >
        <div class="result-header">
          <span>读取结果</span>
          <button
            class="copy-btn"
            @click="copyContent"
          >
            复制
          </button>
        </div>
        <pre class="result-content">{{ resultText }}</pre>
      </div>
    </div>
    <button
      class="expand-btn"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ideService } from '@/services/ideService'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)

const filePath = computed(() => props.toolUse.input.file_path || '')
const fileName = computed(() => {
  const path = filePath.value
  return path.split(/[\\/]/).pop() || path
})

const offset = computed(() => props.toolUse.input.offset)
const limit = computed(() => props.toolUse.input.limit)

const hasLineRange = computed(() => offset.value !== undefined || limit.value !== undefined)

const lineRange = computed(() => {
  if (!hasLineRange.value) return ''
  const start = offset.value || 1
  const end = limit.value ? start + limit.value - 1 : '∞'
  return `L${start}-${end}`
})

const resultText = computed(() => {
  if (!props.result) return ''
  if (typeof props.result.content === 'string') {
    return props.result.content
  }
  return JSON.stringify(props.result.content, null, 2)
})

async function openFile() {
  const line = offset.value || 1
  // 使用增强功能：选择读取的内容
  await ideService.openFile(filePath.value, {
    line,
    selectContent: true,
    content: resultText.value
  })
}

async function copyContent() {
  if (resultText.value) {
    await navigator.clipboard.writeText(resultText.value)
  }
}
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  background: var(--ide-panel-background, #f6f8fa);
  margin: 8px 0;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
  color: var(--ide-accent, #0366d6);
}

.tool-file {
  font-family: monospace;
  color: var(--ide-foreground, #24292e);
}

.tool-lines {
  color: var(--ide-foreground, #586069);
  opacity: 0.7;
  font-size: 12px;
}

.tool-content {
  border-top: 1px solid var(--ide-border, #e1e4e8);
  padding: 12px;
}

.file-info {
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 13px;
}

.info-row .label {
  font-weight: 600;
  color: var(--ide-foreground, #586069);
  opacity: 0.7;
  min-width: 60px;
}

.info-row .value {
  font-family: monospace;
  color: var(--ide-foreground, #24292e);
}

.clickable {
  cursor: pointer;
  color: var(--ide-link, #0366d6);
  text-decoration: underline;
}

.clickable:hover {
  color: var(--ide-link, #0256c0);
  opacity: 0.8;
}

.tool-result {
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: var(--ide-panel-background, #f6f8fa);
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
  font-size: 12px;
  font-weight: 600;
  color: var(--ide-foreground, #24292e);
}

.copy-btn {
  padding: 2px 8px;
  font-size: 11px;
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 3px;
  background: var(--ide-background, white);
  color: var(--ide-foreground, #24292e);
  cursor: pointer;
}

.copy-btn:hover {
  background: var(--ide-panel-background, #f6f8fa);
}

.result-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: var(--ide-code-foreground, #24292e);
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
}

.expand-btn {
  width: 100%;
  padding: 6px;
  border: none;
  border-top: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-panel-background, #fafbfc);
  color: var(--ide-foreground, #586069);
  font-size: 12px;
  cursor: pointer;
}

.expand-btn:hover {
  background: var(--ide-panel-background, #f6f8fa);
  opacity: 0.9;
}
</style>
