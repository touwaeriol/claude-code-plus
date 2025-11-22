<template>
  <div class="tool-call-display">
    <!-- 根据工具类型渲染不同的组件 -->
    <!-- 现有组件使用旧的 ToolUseBlock/ToolResultBlock 接口，需要转换 -->
    <component
      :is="toolComponent"
      v-if="toolComponent"
      :tool-use="toolUseBlock"
      :result="toolResultBlock"
    />

    <!-- 通用工具显示（用于未知工具） -->
    <div
      v-else
      class="generic-tool"
    >
      <div class="tool-header">
        <span class="tool-icon">🔧</span>
        <span class="tool-name">{{ toolCall.toolType }}</span>
        <span
          class="tool-status"
          :class="`status-${toolCall.status.toLowerCase()}`"
        >
          {{ statusText }}
        </span>
      </div>

      <div
        v-if="toolCall.result"
        class="tool-result"
      >
        <pre>{{ formatResult(toolCall.result) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { TOOL_TYPE } from '@/constants/toolTypes'

// 导入工具组件
import ReadToolDisplay from '@/components/tools/ReadToolDisplay.vue'
import WriteToolDisplay from '@/components/tools/WriteToolDisplay.vue'
import EditToolDisplay from '@/components/tools/EditToolDisplay.vue'
import MultiEditToolDisplay from '@/components/tools/MultiEditToolDisplay.vue'
import BashToolDisplay from '@/components/tools/BashToolDisplay.vue'
import GrepToolDisplay from '@/components/tools/GrepToolDisplay.vue'
import GlobToolDisplay from '@/components/tools/GlobToolDisplay.vue'
import TodoWriteDisplay from '@/components/tools/TodoWriteDisplay.vue'
import WebSearchToolDisplay from '@/components/tools/WebSearchToolDisplay.vue'
import WebFetchToolDisplay from '@/components/tools/WebFetchToolDisplay.vue'

interface Props {
  toolCall: ToolCall
}

const props = defineProps<Props>()

// 工具类型到组件的映射
const toolComponentMap: Record<string, any> = {
  [TOOL_TYPE.READ]: ReadToolDisplay,
  [TOOL_TYPE.WRITE]: WriteToolDisplay,
  [TOOL_TYPE.EDIT]: EditToolDisplay,
  [TOOL_TYPE.MULTI_EDIT]: MultiEditToolDisplay,
  [TOOL_TYPE.BASH]: BashToolDisplay,
  [TOOL_TYPE.GREP]: GrepToolDisplay,
  [TOOL_TYPE.GLOB]: GlobToolDisplay,
  [TOOL_TYPE.TODO_WRITE]: TodoWriteDisplay,
  [TOOL_TYPE.WEB_SEARCH]: WebSearchToolDisplay,
  [TOOL_TYPE.WEB_FETCH]: WebFetchToolDisplay
}

const toolComponent = computed(() => {
  return toolComponentMap[props.toolCall.toolType] || null
})

const statusText = computed(() => {
  switch (props.toolCall.status) {
    case ToolCallStatus.RUNNING:
      return 'Running...'
    case ToolCallStatus.SUCCESS:
      return 'Success'
    case ToolCallStatus.FAILED:
      return 'Failed'
    default:
      return 'Unknown'
  }
})

function formatResult(result: any): string {
  if (typeof result === 'string') {
    return result
  }
  return JSON.stringify(result, null, 2)
}

// 将新的 ToolCall 转换为旧的 ToolUseBlock 格式
const toolUseBlock = computed(() => {
  return {
    type: 'tool_use',
    id: props.toolCall.id,
    name: props.toolCall.toolType,
    input: props.toolCall.input
  }
})

// 将新的 ToolCall.result 转换为旧的 ToolResultBlock 格式
const toolResultBlock = computed(() => {
  if (!props.toolCall.result) return undefined

  const result = props.toolCall.result
  return {
    type: 'tool_result',
    tool_use_id: props.toolCall.id,
    content: result.type === 'success' ? result.output : result.error,
    is_error: result.type === 'error'
  }
})
</script>

<style scoped>
.tool-call-display {
  margin: 2px 0;
}

.generic-tool {
  padding: 4px 8px;
  background: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
  font-size: 12px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
  color: #333;
}

.tool-status {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.tool-status.status-running {
  background: #fff3cd;
  color: #856404;
}

.tool-status.status-success {
  background: #d4edda;
  color: #155724;
}

.tool-status.status-failed {
  background: #f8d7da;
  color: #721c24;
}

.tool-result {
  margin-top: 8px;
  padding: 8px;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  font-size: 12px;
}

.tool-result pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Consolas', 'Monaco', monospace;
}
</style>
