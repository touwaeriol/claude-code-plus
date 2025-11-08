<template>
  <div class="compact-tool-call-display">
    <div
      v-for="toolCall in toolCalls"
      :key="toolCall.id"
      class="tool-call-item"
      :class="`status-${toolCall.status.toLowerCase()}`"
    >
      <!-- 工具头部 -->
      <div
        class="tool-header"
        @click="toggleExpand(toolCall.id)"
      >
        <!-- 状态指示条 -->
        <div
          class="status-indicator"
          :style="{ background: getStatusColor(toolCall.status) }"
        />

        <!-- 工具图标 -->
        <span class="tool-icon">{{ getToolIcon(toolCall) }}</span>

        <!-- 工具名称和摘要 -->
        <div class="tool-info">
          <span class="tool-name">{{ getToolDisplayName(toolCall) }}</span>
          <span
            v-if="getToolSummary(toolCall)"
            class="tool-summary"
          >
            {{ getToolSummary(toolCall) }}
          </span>
        </div>

        <!-- 状态徽章 -->
        <span
          class="status-badge"
          :class="`status-${toolCall.status.toLowerCase()}`"
        >
          {{ getStatusLabel(toolCall.status) }}
        </span>

        <!-- 展开箭头 -->
        <span class="expand-chevron">{{ isExpanded(toolCall.id) ? '▴' : '▾' }}</span>
      </div>

      <!-- 展开内容 -->
      <div
        v-if="isExpanded(toolCall.id)"
        class="tool-content"
      >
        <div class="tool-details">
          <!-- 这里之后会根据工具类型显示专业化内容 -->
          <div class="tool-placeholder">
            <p>工具: {{ toolCall.name }}</p>
            <p>状态: {{ toolCall.status }}</p>
            <p v-if="toolCall.viewModel">
              ViewModel: 已加载
            </p>
            <!-- 临时显示原始数据用于调试 -->
            <pre v-if="toolCall.viewModel">{{ JSON.stringify(toolCall.viewModel.toolDetail, null, 2) }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolCall, ToolCallStatus } from '@/types/enhancedMessage'

interface Props {
  toolCalls: ToolCall[]
  expandedTools?: Map<string, boolean>
}

const props = withDefaults(defineProps<Props>(), {
  expandedTools: () => new Map()
})

const emit = defineEmits<{
  (e: 'expanded-change', toolId: string, expanded: boolean): void
}>()

// 本地展开状态
const localExpanded = ref<Set<string>>(new Set())

function isExpanded(toolId: string): boolean {
  // 优先使用外部传入的状态
  if (props.expandedTools.has(toolId)) {
    return props.expandedTools.get(toolId) || false
  }
  // 否则使用本地状态
  return localExpanded.value.has(toolId)
}

function toggleExpand(toolId: string) {
  const newExpanded = !isExpanded(toolId)

  if (newExpanded) {
    localExpanded.value.add(toolId)
  } else {
    localExpanded.value.delete(toolId)
  }

  emit('expanded-change', toolId, newExpanded)
}

function getToolDisplayName(toolCall: ToolCall): string {
  // TODO: 根据 viewModel 返回特殊名称
  return toolCall.name
}

function getToolSummary(toolCall: ToolCall): string {
  return toolCall.viewModel?.compactSummary || ''
}

function getToolIcon(toolCall: ToolCall): string {
  const toolType = toolCall.viewModel?.toolDetail?.toolType

  const iconMap: Record<string, string> = {
    'READ': '📖',
    'WRITE': '📝',
    'EDIT': '✏️',
    'MULTI_EDIT': '🧰',
    'NOTEBOOK_EDIT': '📒',
    'BASH': '💻',
    'BASH_OUTPUT': '💻',
    'KILL_SHELL': '⛔',
    'GLOB': '🔍',
    'GREP': '🔍',
    'TODO_WRITE': '✅',
    'TASK': '🗂',
    'WEB_FETCH': '🌐',
    'WEB_SEARCH': '🌐',
    'MCP': '🧩',
    'LIST_MCP_RESOURCES': '🧩',
    'READ_MCP_RESOURCE': '🧩',
    'EXIT_PLAN_MODE': '🛑',
    'SLASH_COMMAND': '⌨️'
  }

  return iconMap[toolType || ''] || '🛠'
}

function getStatusColor(status: ToolCallStatus): string {
  const colorMap: Record<ToolCallStatus, string> = {
    'SUCCESS': '#2E7D32',
    'RUNNING': '#1976D2',
    'FAILED': '#D32F2F',
    'CANCELLED': '#546E7A',
    'PENDING': '#FB8C00'
  }
  return colorMap[status] || '#8A8D97'
}

function getStatusLabel(status: ToolCallStatus): string {
  const labelMap: Record<ToolCallStatus, string> = {
    'PENDING': '等待',
    'RUNNING': '执行中',
    'SUCCESS': '成功',
    'FAILED': '失败',
    'CANCELLED': '已取消'
  }
  return labelMap[status] || status
}
</script>

<style scoped>
.compact-tool-call-display {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.tool-call-item {
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  border: 1px solid var(--ide-border, rgba(0, 0, 0, 0.18));
  background: var(--ide-panel-background, rgba(0, 0, 0, 0.08));
  overflow: hidden;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}

.tool-header:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.05));
}

.status-indicator {
  width: 3px;
  min-height: 24px;
  border-radius: 999px;
}

.tool-icon {
  font-size: 12px;
}

.tool-info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.tool-name {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tool-summary {
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.75));
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 500;
  white-space: nowrap;
}

.status-badge.status-success {
  background: rgba(46, 125, 50, 0.2);
  color: #2E7D32;
}

.status-badge.status-running {
  background: rgba(33, 150, 243, 0.2);
  color: #1976D2;
}

.status-badge.status-failed {
  background: rgba(229, 57, 53, 0.2);
  color: #D32F2F;
}

.status-badge.status-cancelled {
  background: rgba(176, 190, 197, 0.2);
  color: #546E7A;
}

.status-badge.status-pending {
  background: rgba(255, 179, 0, 0.2);
  color: #FB8C00;
}

.expand-chevron {
  font-size: 11px;
  color: var(--ide-disabled-foreground, #999);
}

.tool-content {
  padding: 6px 12px 12px;
}

.tool-details {
  padding: 10px;
  border-radius: 6px;
  background: var(--ide-panel-background, rgba(0, 0, 0, 0.06));
}

.tool-placeholder {
  font-size: 12px;
  color: var(--ide-foreground, #333);
}

.tool-placeholder p {
  margin-bottom: 4px;
}

.tool-placeholder pre {
  margin-top: 8px;
  padding: 8px;
  background: var(--ide-code-background, #f5f5f5);
  border-radius: 4px;
  font-size: 11px;
  overflow-x: auto;
  max-height: 200px;
  overflow-y: auto;
}

/* 暗色主题 */
.theme-dark .tool-header:hover {
  background: var(--ide-hover-background, rgba(255, 255, 255, 0.05));
}

.theme-dark .tool-summary {
  color: var(--ide-secondary-foreground, rgba(255, 255, 255, 0.75));
}

.theme-dark .tool-placeholder pre {
  background: var(--ide-code-background, #2b2b2b);
  color: var(--ide-foreground, #e0e0e0);
}
</style>
