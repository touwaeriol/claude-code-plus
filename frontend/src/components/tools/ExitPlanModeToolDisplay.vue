<template>
  <div class="tool-display exit-plan-mode-tool">
    <div class="tool-header">
      <span class="tool-icon">📋</span>
      <span class="tool-name">ExitPlanMode</span>
      <span class="plan-label">计划确认</span>
      <span
        v-if="result"
        class="status-badge"
        :class="statusClass"
      >{{ statusText }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="plan-section">
        <div class="section-header">
          执行计划
        </div>
        <div class="plan-content">
          <MarkdownRenderer :content="plan" />
        </div>
      </div>

      <div
        v-if="result"
        class="status-section"
      >
        <div
          class="status-indicator"
          :class="statusClass"
        >
          <span class="status-icon">{{ statusIcon }}</span>
          <span class="status-message">{{ statusMessage }}</span>
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
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)

const plan = computed(() => props.toolUse.input.plan || '')

const resultContent = computed(() => {
  if (!props.result?.content) return null
  if (typeof props.result.content === 'string') {
    try {
      return JSON.parse(props.result.content)
    } catch {
      return { success: true }
    }
  }
  return props.result.content
})

const success = computed(() => {
  if (!resultContent.value) return false
  return resultContent.value.success === true
})

const statusClass = computed(() => {
  if (!props.result) return 'status-pending'
  return success.value ? 'status-confirmed' : 'status-error'
})

const statusText = computed(() => {
  if (!props.result) return '待确认'
  return success.value ? '已确认' : '失败'
})

const statusIcon = computed(() => {
  if (!props.result) return '⏳'
  return success.value ? '✅' : '❌'
})

const statusMessage = computed(() => {
  if (!props.result) return '等待用户确认计划...'
  return success.value ? '计划已确认，开始执行' : '计划确认失败'
})
</script>

<style scoped>
.exit-plan-mode-tool {
  border-color: #fd7e14;
}

.exit-plan-mode-tool .tool-name {
  color: #fd7e14;
}

.plan-label {
  font-size: 12px;
  color: #586069;
  font-weight: 600;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  margin-left: auto;
}

.status-pending {
  background: #fff3cd;
  color: #856404;
}

.status-confirmed {
  background: #e6ffed;
  color: #22863a;
}

.status-error {
  background: #ffeef0;
  color: #d73a49;
}

.plan-section {
  margin-bottom: 2px;
}

.section-header {
  font-size: 12px;
  font-weight: 600;
  color: #586069;
  margin-bottom: 2px;
}

.plan-content {
  padding: 6px 8px;
  background: #ffffff;
  border: 2px solid #fd7e14;
  border-radius: 6px;
  max-height: 400px;
  overflow: auto;
}

.status-section {
  margin-top: 12px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
}

.status-indicator.status-pending {
  background: #fff3cd;
  border: 1px solid #ffc107;
  color: #856404;
}

.status-indicator.status-confirmed {
  background: #e6ffed;
  border: 1px solid #34d058;
  color: #22863a;
}

.status-indicator.status-error {
  background: #ffeef0;
  border: 1px solid #f97583;
  color: #d73a49;
}

.status-icon {
  font-size: 18px;
}

.status-message {
  flex: 1;
}
</style>
