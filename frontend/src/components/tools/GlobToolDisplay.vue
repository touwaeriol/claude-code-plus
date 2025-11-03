<template>
  <div class="tool-display glob-tool">
    <div class="tool-header">
      <span class="tool-icon">🗂️</span>
      <span class="tool-name">Glob</span>
      <code class="tool-pattern">{{ pattern }}</code>
      <span v-if="path" class="tool-path">in {{ pathName }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="glob-info">
        <div class="info-row">
          <span class="label">匹配模式:</span>
          <code class="value">{{ pattern }}</code>
        </div>
        <div v-if="path" class="info-row">
          <span class="label">搜索目录:</span>
          <span class="value">{{ path }}</span>
        </div>
      </div>

      <div v-if="result" class="glob-results">
        <div class="results-header">
          <span>匹配文件</span>
          <span class="file-count">{{ fileCount }}</span>
        </div>
        <div class="results-content">
          <div v-if="filesList.length > 0" class="file-list">
            <div
              v-for="(file, index) in filesList"
              :key="index"
              class="file-item clickable"
              @click="openFile(file)"
            >
              <span class="file-icon">{{ getFileIcon(file) }}</span>
              <span class="file-name">{{ getFileName(file) }}</span>
              <span class="file-path">{{ getFilePath(file) }}</span>
            </div>
          </div>
          <div v-else class="no-matches">
            <span class="empty-icon">📭</span>
            <span class="empty-text">没有匹配的文件</span>
          </div>
        </div>
      </div>
    </div>
    <button class="expand-btn" @click="expanded = !expanded">
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

const pattern = computed(() => props.toolUse.input.pattern || '')
const path = computed(() => props.toolUse.input.path || '')

const pathName = computed(() => {
  if (!path.value) return ''
  const parts = path.value.split(/[\\/]/)
  return parts[parts.length - 1] || path.value
})

const resultText = computed(() => {
  if (!props.result) return ''
  if (typeof props.result.content === 'string') {
    return props.result.content
  }
  return JSON.stringify(props.result.content, null, 2)
})

const filesList = computed(() => {
  if (!resultText.value.trim()) return []
  return resultText.value.split('\n').filter(line => line.trim())
})

const fileCount = computed(() => {
  const count = filesList.value.length
  return count === 0 ? '无匹配' : `${count} 个文件`
})

function getFileIcon(filePath: string): string {
  const ext = filePath.split('.').pop()?.toLowerCase()
  const iconMap: Record<string, string> = {
    'ts': '📘',
    'js': '📜',
    'vue': '💚',
    'kt': '🟣',
    'java': '☕',
    'py': '🐍',
    'json': '📋',
    'md': '📝',
    'xml': '📄',
    'yaml': '⚙️',
    'yml': '⚙️',
    'gradle': '🐘',
    'properties': '🔧'
  }
  return iconMap[ext || ''] || '📄'
}

function getFileName(filePath: string): string {
  const parts = filePath.split(/[\\/]/)
  return parts[parts.length - 1] || filePath
}

function getFilePath(filePath: string): string {
  const parts = filePath.split(/[\\/]/)
  if (parts.length <= 1) return ''
  return parts.slice(0, -1).join('/')
}

async function openFile(filePath: string) {
  await ideService.openFile(filePath)
}
</script>

<style scoped>
.glob-tool {
  border-color: #79b8ff;
}

.glob-tool .tool-name {
  color: #0366d6;
}

.tool-pattern {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: rgba(3, 102, 214, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  color: #24292e;
}

.tool-path {
  font-size: 11px;
  color: #586069;
}

.glob-info {
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
  color: #586069;
  min-width: 80px;
}

.info-row .value {
  font-family: monospace;
  color: #24292e;
}

.glob-results {
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow: hidden;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
  font-size: 12px;
  font-weight: 600;
}

.file-count {
  color: #0366d6;
  font-size: 11px;
}

.results-content {
  max-height: 400px;
  overflow-y: auto;
}

.file-list {
  padding: 8px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 4px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.file-item:hover {
  background: #e1e4e8;
  border-color: #0366d6;
}

.file-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.file-name {
  font-family: monospace;
  font-size: 13px;
  font-weight: 600;
  color: #24292e;
}

.file-path {
  font-size: 11px;
  color: #586069;
  margin-left: auto;
}

.no-matches {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #586069;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-text {
  font-size: 14px;
}
</style>
