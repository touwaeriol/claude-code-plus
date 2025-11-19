<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    @click="handleCardClick"
  >
    <template #details>
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
        <CodeSnippet :code="resultText" :language="language" />
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ideService } from '@/services/ideaBridge'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import { useEnvironment } from '@/composables/useEnvironment'
import CodeSnippet from './CodeSnippet.vue'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看详情
const expanded = ref(false)

// 环境检测
const { isInIde } = useEnvironment()

// 提取显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolUse, props.result))

const filePath = computed(() => props.toolUse.input.path || props.toolUse.input.file_path || '')
const fileName = computed(() => {
  const path = filePath.value
  return path.split(/[\\/]/).pop() || path
})

const offset = computed(() => props.toolUse.input.offset)
const limit = computed(() => props.toolUse.input.limit)

const hasLineRange = computed(() => {
  const viewRange = props.toolUse.input.view_range
  return Array.isArray(viewRange) || offset.value !== undefined || limit.value !== undefined
})

const lineRange = computed(() => {
  const viewRange = props.toolUse.input.view_range
  if (Array.isArray(viewRange)) {
    return `L${viewRange[0]}-${viewRange[1]}`
  }
  if (!hasLineRange.value) return ''
  const start = offset.value || 1
  const end = limit.value ? start + limit.value - 1 : '∞'
  return `L${start}-${end}`
})

const resultText = computed(() => {
  if (!props.result) return ''
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

const language = computed(() => {
  const path = filePath.value
  const extension = path.split('.').pop()?.toLowerCase() || ''
  const langMap: Record<string, string> = {
    'js': 'javascript',
    'ts': 'typescript',
    'kt': 'kotlin',
    'java': 'java',
    'py': 'python',
    'sh': 'shell',
    'bash': 'shell',
    'md': 'markdown',
    'json': 'json',
    'css': 'css',
    'html': 'html',
    'xml': 'xml'
  }
  return langMap[extension] || 'plaintext'
})


// 检查是否应该使用 IDE 集成（不展开，直接打开文件）
function shouldUseIdeIntegration(): boolean {
  return isInIde.value && displayInfo.value.status === 'success'
}

function handleCardClick() {
  if (shouldUseIdeIntegration()) {
    // IDE 操作：直接打开文件
    openFile()
  } else {
    // 其他情况：切换展开状态
    expanded.value = !expanded.value
  }
}

async function openFile() {
  const viewRange = props.toolUse.input.view_range
  let startLine: number
  let endLine: number | undefined

  // 计算起始和结束行号
  if (Array.isArray(viewRange) && viewRange.length >= 2) {
    // 使用 view_range
    startLine = viewRange[0]
    endLine = viewRange[1]
  } else if (offset.value !== undefined && limit.value !== undefined) {
    // 使用 offset 和 limit 计算范围
    startLine = offset.value
    endLine = offset.value + limit.value - 1
  } else if (offset.value !== undefined) {
    // 只有 offset，没有 limit
    startLine = offset.value
    endLine = undefined
  } else {
    // 默认从第一行开始
    startLine = 1
    endLine = undefined
  }

  // 打开文件并选中行范围
  await ideService.openFile(filePath.value, {
    line: startLine,
    endLine: endLine,
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
