<template>
  <CompactToolCard
    :display-info="cardDisplayInfo"
    :is-expanded="expanded"
    :has-details="hasContent"
    :tool-call="toolCallData"
    @toggle="expanded = !expanded"
  >
    <template #details>
      <div
        v-if="hasContent"
        class="content-preview"
      >
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
        <div class="code-wrapper">
          <CodeSnippet :code="resultText" :language="language" :start-line="startLineNumber" />
        </div>
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

  // 移除 <system-reminder> 标签及其内容（SDK 注入的系统提示）
  const withoutReminder = cleanContent.replace(/<system-reminder>[\s\S]*?<\/system-reminder>/g, '')

  // 去除尾部空行
  const lines = withoutReminder.split('\n')
  while (lines.length > 0 && lines[lines.length - 1].trim() === '') {
    lines.pop()
  }
  return lines.join('\n')
})

const readLineCount = computed(() => {
  if (!hasContent.value) return 0
  if (!resultText.value) return 0
  return resultText.value.split('\n').length
})

const cardDisplayInfo = computed(() => ({
  ...displayInfo.value,
  readLines: readLineCount.value || undefined
}))

// 提取起始行号
const startLineNumber = computed(() => {
  // 优先使用 input 中的 offset 参数（如果存在）
  const offset = props.toolCall.input.offset
  if (typeof offset === 'number' && offset > 0) {
    // SDK 使用 offset 时，返回的行号从 1 开始，需要加上 offset
    return offset
  }

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
  font-family: var(--theme-editor-font-family);
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
  font-family: var(--theme-editor-font-family);
  color: var(--theme-foreground, #24292e);
}

.content-preview {
  position: relative;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  /* 外部容器不滚动，滚动由内部 CodeSnippet 处理 */
  overflow: hidden;
  width: 100%;
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

.code-wrapper {
  margin: 0;
  padding: 12px;
  /* 设置 CodeSnippet 内部滚动高度 */
  --code-max-height: 450px;
  overflow: visible;
  /* 彻底屏蔽外部下划线影响，保留代码自身样式 */
  text-decoration: none !important;
}

.code-wrapper :deep(*) {
  text-decoration: none !important;
}
</style>
