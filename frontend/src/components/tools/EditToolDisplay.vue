<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    @click="handleCardClick"
  >
    <template #details>
      <div class="edit-tool-details">
        <div class="file-info">
          <div class="info-row">
            <span class="label">文件:</span>
            <span class="value clickable" @click="showDiff">{{ filePath }}</span>
          </div>
        </div>

        <DiffViewer :old-content="oldString" :new-content="newString" />

        <div v-if="replaceAll" class="replace-mode">
          <span class="badge">全部替换</span>
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

import DiffViewer from './DiffViewer.vue'
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
    showDiff()
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

const oldString = computed(() => props.toolUse.input.old_string || '')
const newString = computed(() => props.toolUse.input.new_string || '')
const replaceAll = computed(() => props.toolUse.input.replace_all || false)

const resultStatus = computed(() => {
  if (!props.result) return 'pending'
  // 简单判断:如果 content 包含 "success" 或没有 "error"
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
  if (resultStatus.value === 'error') return '编辑失败'
  return '编辑成功'
})

async function showDiff() {
  // 使用增强功能：从文件重建完整 Diff
  await ideService.showDiff({
    filePath: filePath.value,
    oldContent: oldString.value,
    newContent: newString.value,
    rebuildFromFile: true,
    edits: [{
      oldString: oldString.value,
      newString: newString.value,
      replaceAll: false
    }]
  })
}
</script>

<style scoped>
.edit-tool {
  border-color: var(--ide-error, #f9826c);
}

.edit-tool .tool-name {
  color: var(--ide-error, #d73a49);
}

.edit-preview {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 12px;
  margin: 12px 0;
}

.diff-section {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.diff-header {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.diff-header.old {
  background: #ffeef0;
  color: var(--ide-error, #d73a49);
}

.diff-header.new {
  background: #e6ffed;
  color: var(--ide-success, #22863a);
}

.diff-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 200px;
  overflow: auto;
  background: var(--ide-background, white);
  color: var(--ide-code-foreground, #24292e);
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
  color: var(--ide-foreground, #586069);
  opacity: 0.7;
}

.replace-mode {
  margin-top: 8px;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  background: var(--ide-accent, #0366d6);
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
  color: var(--ide-accent, #0366d6);
}

.result-status.success {
  background: #e6ffed;
  color: var(--ide-success, #22863a);
}

.result-status.error {
  background: #ffeef0;
  color: var(--ide-error, #d73a49);
}

/* 暗色主题适配 */
.theme-dark .diff-header.old {
  background: #3d1f1f;
}

.theme-dark .diff-header.new {
  background: #1f3d1f;
}

.theme-dark .diff-content.old {
  background: #3d1f1f;
}

.theme-dark .diff-content.new {
  background: #1f3d1f;
}

.theme-dark .result-status.pending {
  background: #1a2a3a;
}

.theme-dark .result-status.success {
  background: #1f3d1f;
}

.theme-dark .result-status.error {
  background: #3d1f1f;
}
</style>
