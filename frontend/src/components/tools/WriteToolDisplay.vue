<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    @click="handleCardClick"
  >
    <template #details>
      <div class="write-tool-details">
        <div class="file-info">
          <div class="info-row">
            <span class="label">路径:</span>
            <span class="value clickable" @click="openFile">{{ filePath }}</span>
          </div>
          <div class="info-row">
            <span class="label">大小:</span>
            <span class="value">{{ contentSize }}</span>
          </div>
        </div>

        <div class="content-preview">
          <div class="preview-header">
            <span>内容预览</span>
            <button class="copy-btn" @click="copyContent">复制</button>
          </div>
          <pre class="preview-content">{{ previewText }}</pre>
        </div>

        <div v-if="result" class="tool-result">
          <div class="result-status" :class="resultStatus">
            {{ resultMessage }}
          </div>
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

const resultStatus = computed(() => {
  if (!props.result) return 'pending'
  // 将 content 转换为字符串
  let contentStr = ''
  if (typeof props.result.content === 'string') {
    contentStr = props.result.content
  } else if (Array.isArray(props.result.content)) {
    contentStr = props.result.content
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  } else {
    contentStr = JSON.stringify(props.result.content)
  }
  const content = contentStr.toLowerCase()
  if (content.includes('error') || content.includes('failed')) return 'error'
  return 'success'
})

const resultMessage = computed(() => {
  if (!props.result) return '等待执行...'
  if (resultStatus.value === 'error') return '写入失败'
  return '写入成功'
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
  padding: 2px 8px;
  font-size: 11px;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  background: white;
  cursor: pointer;
}

.copy-btn:hover {
  background: #f6f8fa;
}

.preview-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 300px;
  overflow: auto;
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
