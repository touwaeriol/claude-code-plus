<template>
  <div class="tool-display edit-tool">
    <div class="tool-header">
      <span class="tool-icon">✏️</span>
      <span class="tool-name">Edit</span>
      <span class="tool-file">{{ fileName }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="file-info">
        <div class="info-row">
          <span class="label">文件:</span>
          <span
            class="value clickable"
            @click="showDiff"
          >{{ filePath }}</span>
        </div>
      </div>
      <div class="edit-preview">
        <div class="diff-section">
          <div class="diff-header old">
            替换内容 (旧)
          </div>
          <pre class="diff-content old">{{ oldString }}</pre>
        </div>
        <div class="diff-arrow">
          →
        </div>
        <div class="diff-section">
          <div class="diff-header new">
            新内容
          </div>
          <pre class="diff-content new">{{ newString }}</pre>
        </div>
      </div>
      <div
        v-if="replaceAll"
        class="replace-mode"
      >
        <span class="badge">全部替换</span>
      </div>
      <div
        v-if="result"
        class="tool-result"
      >
        <div
          class="result-status"
          :class="resultStatus"
        >
          {{ resultMessage }}
        </div>
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
import { ideService } from '@/services/ideaBridge'
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

const oldString = computed(() => props.toolUse.input.old_string || '')
const newString = computed(() => props.toolUse.input.new_string || '')
const replaceAll = computed(() => props.toolUse.input.replace_all || false)

const resultStatus = computed(() => {
  if (!props.result) return 'pending'
  // 简单判断:如果 content 包含 "success" 或没有 "error"
  const content = JSON.stringify(props.result.content).toLowerCase()
  if (content.includes('error') || content.includes('failed')) return 'error'
  return 'success'
})

const resultMessage = computed(() => {
  if (!props.result) return '等待执行...'
  if (resultStatus.value === 'error') return '编辑失败'
  return '编辑成功'
})

async function showDiff() {
  // 调用 IDE 服务显示 diff
  await ideService.showDiff(filePath.value, oldString.value, newString.value)
}
</script>

<style scoped>
.edit-tool {
  border-color: #f9826c;
}

.edit-tool .tool-name {
  color: #d73a49;
}

.edit-preview {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
  margin: 12px 0;
}

.diff-section {
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow: hidden;
}

.diff-header {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid #e1e4e8;
}

.diff-header.old {
  background: #ffeef0;
  color: #d73a49;
}

.diff-header.new {
  background: #e6ffed;
  color: #22863a;
}

.diff-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 200px;
  overflow: auto;
  background: white;
}

.diff-content.old {
  background: #ffeef0;
}

.diff-content.new {
  background: #e6ffed;
}

.diff-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #586069;
}

.replace-mode {
  margin-top: 8px;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  background: #0366d6;
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 12px;
}

.result-status {
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  margin-top: 12px;
}

.result-status.pending {
  background: #f1f8ff;
  color: #0366d6;
}

.result-status.success {
  background: #e6ffed;
  color: #22863a;
}

.result-status.error {
  background: #ffeef0;
  color: #d73a49;
}
</style>
