<template>
  <div class="tool-display grep-tool">
    <div class="tool-header">
      <span class="tool-icon">🔍</span>
      <span class="tool-name">Grep</span>
      <code class="tool-pattern">{{ pattern }}</code>
      <span
        v-if="glob"
        class="tool-glob"
      >{{ glob }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="search-info">
        <div class="info-row">
          <span class="label">搜索模式:</span>
          <code class="value">{{ pattern }}</code>
        </div>
        <div
          v-if="path"
          class="info-row"
        >
          <span class="label">搜索路径:</span>
          <span class="value">{{ path }}</span>
        </div>
        <div
          v-if="glob"
          class="info-row"
        >
          <span class="label">文件过滤:</span>
          <code class="value">{{ glob }}</code>
        </div>
        <div
          v-if="type"
          class="info-row"
        >
          <span class="label">文件类型:</span>
          <span class="value">{{ type }}</span>
        </div>
        <div
          v-if="outputMode"
          class="info-row"
        >
          <span class="label">输出模式:</span>
          <span class="value">{{ outputModeText }}</span>
        </div>
      </div>

      <div
        v-if="options.length > 0"
        class="search-options"
      >
        <span
          v-for="opt in options"
          :key="opt"
          class="option-badge"
        >{{ opt }}</span>
      </div>

      <div
        v-if="result"
        class="search-results"
      >
        <div class="results-header">
          <span>搜索结果</span>
          <span class="match-count">{{ matchCount }}</span>
        </div>
        <div class="results-content">
          <div
            v-if="isFilesList"
            class="file-list"
          >
            <div
              v-for="(file, index) in filesList"
              :key="index"
              class="file-item clickable"
              @click="openFile(file)"
            >
              📄 {{ file }}
            </div>
          </div>
          <pre
            v-else
            class="search-output"
          >{{ resultText }}</pre>
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

const pattern = computed(() => props.toolUse.input.pattern || '')
const path = computed(() => props.toolUse.input.path || '')
const glob = computed(() => props.toolUse.input.glob || '')
const type = computed(() => props.toolUse.input.type || '')
const outputMode = computed(() => props.toolUse.input.output_mode || 'files_with_matches')

const outputModeText = computed(() => {
  const modes: Record<string, string> = {
    'content': '内容',
    'files_with_matches': '文件列表',
    'count': '计数'
  }
  return modes[outputMode.value] || outputMode.value
})

const options = computed(() => {
  const opts: string[] = []
  if (props.toolUse.input['-i']) opts.push('忽略大小写')
  if (props.toolUse.input['-n']) opts.push('显示行号')
  if (props.toolUse.input.multiline) opts.push('多行匹配')
  return opts
})

const resultText = computed(() => {
  if (!props.result) return ''
  if (typeof props.result.content === 'string') {
    return props.result.content
  }
  const content = props.result.content

  // 处理字符串
  if (typeof content === 'string') {
    return content
  }

  // 处理数组（ContentBlock[]）
  if (Array.isArray(content)) {
    return content
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }

  // 处理对象
  return JSON.stringify(content, null, 2)
})

const isFilesList = computed(() => {
  return outputMode.value === 'files_with_matches' && resultText.value.trim()
})

const filesList = computed(() => {
  if (!isFilesList.value) return []
  return resultText.value.split('\n').filter(line => line.trim())
})

const matchCount = computed(() => {
  if (outputMode.value === 'count') {
    // 尝试从结果中解析计数
    const match = resultText.value.match(/(\d+)/)
    return match ? `${match[1]} 个匹配` : ''
  }
  if (isFilesList.value) {
    return `${filesList.value.length} 个文件`
  }
  return ''
})

async function openFile(filePath: string) {
  await ideService.openFile(filePath)
}
</script>

<style scoped>
.grep-tool {
  border-color: #f9826c;
}

.grep-tool .tool-name {
  color: #e36209;
}

.tool-pattern {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: rgba(227, 98, 9, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  color: #24292e;
}

.tool-glob {
  font-size: 11px;
  color: #586069;
  background: #f6f8fa;
  padding: 2px 6px;
  border-radius: 3px;
}

.search-info {
  margin-bottom: 2px;
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

.search-options {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 2px;
}

.option-badge {
  display: inline-block;
  padding: 3px 8px;
  background: #e36209;
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
}

.search-results {
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

.match-count {
  color: #e36209;
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
  padding: 6px 12px;
  margin-bottom: 4px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 13px;
  font-family: monospace;
  cursor: pointer;
  transition: all 0.2s;
}

.file-item:hover {
  background: #e1e4e8;
  border-color: #d1d5da;
}

.search-output {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
