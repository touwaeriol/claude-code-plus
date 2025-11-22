<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    @click="handleCardClick"
  >
    <template #details>
      <div class="write-tool-details">
        <div class="content-preview">
          <div class="preview-header">
            <span></span>
            <button class="copy-btn" title="复制" @click.stop="copyContent">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </button>
          </div>
          <pre class="preview-content code-with-lines"><code><span v-for="(line, index) in previewLines" :key="index" class="code-line">{{ line }}</span></code></pre>
        </div>
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

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看详情
const expanded = ref(false)

// 环境检测
const { isInIde } = useEnvironment()

// 提取工具显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolUse, props.result))

// 判断是否应该使用 IDE 集成
function shouldUseIdeIntegration(): boolean {
  return isInIde.value && displayInfo.value.status === 'success'
}

// 处理卡片点击
function handleCardClick() {
  if (shouldUseIdeIntegration()) {
    // IDE 操作：直接打开文件
    openFile()
  } else {
    // 其他情况：切换展开状态
    expanded.value = !expanded.value
  }
}

const filePath = computed(() => props.toolUse.input.file_path || '')
const fileName = computed(() => {
  const path = filePath.value
  return path.split(/[\\/]/).pop() || path
})

const content = computed(() => props.toolUse.input.content || '')

const isNewFile = computed(() => {
  // 可以根据结果或输入判断是否是新文件
  // 简化处理:假设 Write 都可能创建新文件
  return true
})

const contentSize = computed(() => {
  const bytes = new Blob([content.value]).size
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
})

const previewText = computed(() => {
  const text = content.value
  const maxLength = 500
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '\n\n... (内容已截断)'
})

const previewLines = computed(() => {
  return previewText.value.split('\n')
})

async function openFile() {
  // 打开新创建的文件
  await ideService.openFile(filePath.value)
}

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
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow: hidden;
  margin: 12px 0;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
  font-size: 12px;
  font-weight: 600;
}

.copy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--ide-foreground, #24292e);
  cursor: pointer;
  opacity: 0.6;
}

.copy-btn:hover {
  opacity: 1;
  background: var(--ide-panel-background, #f6f8fa);
}

.preview-content {
  margin: 0;
  padding: 12px 12px 12px 0;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 300px;
  overflow: auto;
}

/* 带行号的代码块 */
.code-with-lines {
  counter-reset: line;
}

.code-with-lines code {
  display: block;
}

/* 每一行代码 */
.code-line {
  display: block;
  counter-increment: line;
  white-space: pre;
}

/* 为每一行添加行号 */
.code-line::before {
  content: counter(line);
  display: inline-block;
  width: 3em;
  padding-right: 1em;
  margin-right: 0.5em;
  text-align: right;
  color: var(--ide-secondary-foreground, #999);
  user-select: none; /* 行号不可选中复制 */
  border-right: 1px solid #e1e4e8;
}

/* 暗色主题适配 */
.theme-dark .code-line::before {
  color: var(--ide-secondary-foreground, #666);
  border-right-color: rgba(255, 255, 255, 0.1);
}

</style>
