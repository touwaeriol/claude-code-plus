<template>
  <CompactToolCard
    :display-info="cardDisplayInfo"
    :is-expanded="expanded"
    :has-details="true"
    :tool-call="toolCallData"
    @toggle="expanded = !expanded"
  >
    <template #details>
      <div class="write-tool-details">
        <div class="content-preview">
          <button class="copy-btn" :title="t('common.copy')" @click.stop="copyContent">
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
            </svg>
          </button>
          <div class="code-wrapper">
            <CodeSnippet
              :code="previewText"
              :language="language"
              :start-line="startLineNumber"
            />
          </div>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeWriteToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import CodeSnippet from './CodeSnippet.vue'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeWriteToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看详情
const expanded = ref(false)

// 提取工具显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall, props.toolCall.result))

// 构造拦截器所需的工具调用数据
const toolCallData = computed(() => ({
  toolType: 'Write',
  input: props.toolCall.input as Record<string, unknown>,
  result: props.toolCall.result
}))

const content = computed(() => props.toolCall.input.content || '')
const lineCount = computed(() => content.value ? content.value.split('\n').length : 0)
const cardDisplayInfo = computed(() => ({
  ...displayInfo.value,
  addedLines: lineCount.value || displayInfo.value.addedLines
}))

// 计算被跳过的前置空行数量（支持 \n 和 \r\n）
const skippedLines = computed(() => {
  // 统计开头连续空行的数量（按 \n 分割）
  const lines = content.value.split('\n')
  let count = 0
  for (const line of lines) {
    // 空行或只有 \r 的行
    if (line === '' || line === '\r') {
      count++
    } else {
      break
    }
  }
  return count
})

const previewText = computed(() => {
  // 展示时去掉前置空行，避免行号和正文顶端间距过大（支持 \r\n 和 \n）
  const text = content.value.replace(/^[\r\n]+/, '')
  const maxLength = 500
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '\n\n... (' + t('tools.contentTruncated') + ')'
})

// 行号从跳过的行数 + 1 开始
const startLineNumber = computed(() => skippedLines.value + 1)

const language = computed(() => {
  const path = (props.toolCall.input as any).path || (props.toolCall.input as any).file_path || ''
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
    'xml': 'xml',
    'txt': 'plaintext'
  }
  return langMap[extension] || 'plaintext'
})

async function copyContent() {
  await navigator.clipboard.writeText(content.value)
}
</script>

<style scoped>
.write-tool {
  border-color: #34d058;
}

.write-tool .tool-name {
  color: #22863a;
}

.badge.new-file {
  display: inline-block;
  padding: 2px 6px;
  background: #22863a;
  color: white;
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
}

.content-preview {
  position: relative;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow: hidden;
  margin: 12px 0;
  padding: 0;
}

.copy-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: none;
  border-radius: 3px;
  background: var(--theme-panel-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
  cursor: pointer;
  opacity: 0.6;
}

.copy-btn:hover {
  opacity: 1;
  background: var(--theme-hover-background, #e1e4e8);
}

.preview-content {
  margin: 0;
  padding: 0;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 300px;
  overflow: auto;
}

.code-wrapper {
  margin: 0;
  padding: 12px;
  /* 滚动交给 CodeSnippet 内部处理，避免双滚动条 */
  max-height: none;
  overflow: visible;
  /* 彻底屏蔽外部下划线影响，保留代码自身样式 */
  text-decoration: none !important;
}

.code-wrapper :deep(*) {
  text-decoration: none !important;
}
</style>
