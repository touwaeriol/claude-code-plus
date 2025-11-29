<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasContent"
    :tool-call="toolCallData"
    @toggle="expanded = !expanded"
  >
    <template #details>
      <div
        v-if="hasContent"
        class="tool-result"
      >
        <div class="result-header">
          <span></span>
          <button
            class="copy-btn"
            :title="t('common.copy')"
            @click="copyContent"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
            </svg>
          </button>
        </div>
        <CodeSnippet :code="resultText" :language="language" :start-line="startLineNumber" />
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeReadToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import CodeSnippet from './CodeSnippet.vue'

const { t } = useI18n()

interface Props {
  toolCall: ClaudeReadToolCall
}

const props = defineProps<Props>()
// Read 工具默认折叠，点击后展开查看文件内容
// 根据设计文档，Read 展开后显示读取的内容（结果），用户关心读取的内容
const expanded = ref(false)

// 提取显示信息（使用工具卡通用提取函数）
const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall, props.toolCall.result))

const filePath = computed(() => props.toolCall.input.path || props.toolCall.input.file_path || '')

// 构造拦截器所需的工具调用数据
const toolCallData = computed(() => ({
  toolType: 'Read',
  input: props.toolCall.input as Record<string, unknown>,
  result: props.toolCall.result
}))

// Read 工具展示的是文件内容（结果），只有成功且有内容时才显示详情
// 错误信息由 CompactToolCard 的 errorMessage 处理
const hasContent = computed(() => {
  const result = props.toolCall.result
  if (!result || result.is_error) return false
  return !!resultText.value
})

/**
 * 提取 SDK 返回的行号和代码内容
 * SDK 返回的格式：
 *     1→代码内容
 * 返回：{ content: 代码内容, startLine: 起始行号 }
 */
function extractLineNumbersAndContent(text: string): { content: string; startLine: number } {
  const lines = text.split('\n')
  let startLine = 1
  let firstLineNumberFound = false

  const contentLines = lines.map(line => {
    // 匹配行首的"空格 + 数字 + →"
    const match = line.match(/^\s*(\d+)→/)
    if (match) {
      const lineNum = parseInt(match[1], 10)
      // 记录第一行的行号
      if (!firstLineNumberFound) {
        startLine = lineNum
        firstLineNumberFound = true
      }
      // 移除行号部分
      return line.substring(match[0].length)
    }
    return line
  })

  return {
    content: contentLines.join('\n'),
    startLine
  }
}

const resultText = computed(() => {
  const result = props.toolCall.result
  // 使用后端格式：检查 is_error
  if (!result || result.is_error) return ''
  const content = result.content

  let rawText = ''

  if (typeof content === 'string') {
    rawText = content
  } else if (Array.isArray(content)) {
    rawText = (content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  } else {
    rawText = JSON.stringify(content, null, 2)
  }

  const { content: cleanContent } = extractLineNumbersAndContent(rawText)

  // 去除尾部空行
  const lines = cleanContent.split('\n')
  while (lines.length > 0 && lines[lines.length - 1].trim() === '') {
    lines.pop()
  }
  return lines.join('\n')
})

// 提取起始行号
const startLineNumber = computed(() => {
  const result = props.toolCall.result
  // 使用后端格式：检查 is_error
  if (!result || result.is_error) return 1
  const content = result.content

  let rawText = ''
  if (typeof content === 'string') {
    rawText = content
  } else if (Array.isArray(content)) {
    rawText = (content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  } else {
    rawText = JSON.stringify(content, null, 2)
  }

  const { startLine } = extractLineNumbersAndContent(rawText)
  return startLine
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

async function copyContent() {
  if (resultText.value) {
    await navigator.clipboard.writeText(resultText.value)
  }
}
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  background: var(--theme-panel-background, #f6f8fa);
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
  color: var(--theme-accent, #0366d6);
}

.tool-file {
  font-family: monospace;
  color: var(--theme-foreground, #24292e);
}

.tool-lines {
  color: var(--theme-foreground, #586069);
  opacity: 0.7;
  font-size: 12px;
}

.tool-content {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding: 12px;
}

.file-info {
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
  color: var(--theme-foreground, #586069);
  opacity: 0.7;
  min-width: 60px;
}

.info-row .value {
  font-family: monospace;
  color: var(--theme-foreground, #24292e);
}

.clickable {
  cursor: pointer;
  color: var(--theme-link, #0366d6);
  text-decoration: underline;
}

.clickable:hover {
  color: var(--theme-link, #0256c0);
  opacity: 0.8;
}

.tool-result {
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: var(--theme-panel-background, #f6f8fa);
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-foreground, #24292e);
}

.copy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--theme-foreground, #24292e);
  cursor: pointer;
  opacity: 0.6;
}

.copy-btn:hover {
  opacity: 1;
  background: var(--theme-panel-background, #f6f8fa);
}

.result-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  color: var(--theme-code-foreground, #24292e);
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
}

.expand-btn {
  width: 100%;
  padding: 6px;
  border: none;
  border-top: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-panel-background, #fafbfc);
  color: var(--theme-foreground, #586069);
  font-size: 12px;
  cursor: pointer;
}

.expand-btn:hover {
  background: var(--theme-panel-background, #f6f8fa);
  opacity: 0.9;
}
</style>
