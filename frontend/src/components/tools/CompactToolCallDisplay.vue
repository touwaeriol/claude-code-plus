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
        :class="{ 'ide-action-tool': isIdeActionTool(toolCall) }"
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

          <!-- 使用 toolDisplayInfo 提取的信息 -->
          <template v-if="getToolDisplayInfo(toolCall)">
            <!-- 主要信息 -->
            <span v-if="getToolDisplayInfo(toolCall)?.primaryInfo" class="primary-info">
              {{ getToolDisplayInfo(toolCall)?.primaryInfo }}
            </span>

            <!-- 次要信息 -->
            <span v-if="getToolDisplayInfo(toolCall)?.secondaryInfo" class="secondary-info">
              {{ getToolDisplayInfo(toolCall)?.secondaryInfo }}
            </span>

            <!-- 行数变化徽章 -->
            <span v-if="getToolDisplayInfo(toolCall)?.lineChanges" class="line-changes-badge">
              {{ getToolDisplayInfo(toolCall)?.lineChanges }}
            </span>
          </template>

          <!-- 降级显示: 如果没有 displayInfo,使用原始 summary -->
          <span
            v-else-if="getToolSummary(toolCall)"
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
          <!-- 根据工具类型显示专业化内容 -->
          <TypedToolCallDisplay :tool-call="toolCall" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolCall, ToolCallStatus } from '@/types/enhancedMessage'
import TypedToolCallDisplay from './TypedToolCallDisplay.vue'
import { useEnvironment } from '@/composables/useEnvironment'
import { ideaBridge } from '@/services/ideaBridge'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'
import { UiToolType } from '@/utils/ToolViewModelBuilder'

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

// 环境检测
const { isInIde } = useEnvironment()

// 本地展开状态 - 使用 Map 存储明确的展开/折叠状态
const localExpanded = ref<Map<string, boolean>>(new Map())

// 判断工具默认是否应该展开
function getDefaultExpanded(toolCall: ToolCall): boolean {
  // Task 工具默认展开，其余工具遵循统一的折叠行为
  const toolType = getToolType(toolCall)
  return toolType === UiToolType.TASK
}

function getToolType(toolCall: ToolCall): UiToolType | undefined {
  return toolCall.viewModel?.toolDetail?.toolType as UiToolType | undefined
}

function isExpanded(toolId: string): boolean {
  const toolCall = props.toolCalls.find(t => t.id === toolId)
  if (!toolCall) return false

  // 优先使用外部传入的状态
  if (props.expandedTools.has(toolId)) {
    return props.expandedTools.get(toolId) || false
  }
  // 否则使用本地状态，如果本地也没有则使用默认值
  if (localExpanded.value.has(toolId)) {
    return localExpanded.value.get(toolId) || false
  }
  // 使用默认展开逻辑
  return getDefaultExpanded(toolCall)
}

function toggleExpand(toolId: string) {
  const toolCall = props.toolCalls.find(t => t.id === toolId)
  if (!toolCall) return

  // 如果工具正在运行,不处理点击
  if (toolCall.status === 'RUNNING') {
    return
  }

  // 检查是否应该使用 IDE 集成
  if (shouldUseIdeIntegration(toolCall)) {
    // ✅ 在 IDE 环境中: 调用 IDE API
    handleIdeIntegration(toolCall)
    return
  }

  // ✅ 在浏览器环境中 或 不支持IDE集成: 切换展开状态
  const newExpanded = !isExpanded(toolId)

  // 存储明确的展开/折叠状态
  localExpanded.value.set(toolId, newExpanded)

  emit('expanded-change', toolId, newExpanded)
}

/**
 * 处理 IDE 集成操作
 */
function handleIdeIntegration(toolCall: ToolCall) {
  const toolType = getToolType(toolCall)
  const params = toolCall.viewModel?.toolDetail?.parameters

  if (!toolType || !params) {
    console.warn('[CompactToolCallDisplay] 无法获取工具类型或参数')
    return
  }

  console.log(`[CompactToolCallDisplay] IDE 集成操作: ${toolType}`)

  switch (toolType) {
    case UiToolType.READ:
      ideaBridge.query('ide.openFile', {
        filePath: params.file_path,
        line: params.offset
      })
      break
    case UiToolType.WRITE:
      ideaBridge.query('ide.openFile', {
        filePath: params.file_path
      })
      break
    case UiToolType.EDIT:
      ideaBridge.query('ide.showDiff', {
        filePath: params.file_path,
        oldContent: params.old_string,
        newContent: params.new_string
      })
      break
    case UiToolType.MULTI_EDIT:
      // TODO: 实现多处编辑的IDE集成
      console.log('[CompactToolCallDisplay] Multi-edit IDE 集成尚未实现')
      break
    case UiToolType.NOTEBOOK_EDIT:
      ideaBridge.query('ide.openFile', {
        filePath: params.notebook_path
      })
      break
  }
}

/**
 * 判断工具是否应该使用 IDE 集成
 *
 * IDE 操作工具（不展开）：
 * - READ: 打开文件并跳转到行号
 * - WRITE: 打开新创建的文件
 * - EDIT: 显示文件差异
 * - MULTI_EDIT: 显示多处修改
 * - NOTEBOOK_EDIT: 打开笔记本
 *
 * 必须同时满足：
 * 1. 运行在 IDE 环境中 ✅ 新增!
 * 2. 工具类型支持 IDE 集成
 * 3. 执行状态为成功
 */
function shouldUseIdeIntegration(toolCall: ToolCall): boolean {
  // 1. 必须运行在 IDE 环境中
  if (!isInIde.value) {
    return false
  }

  // 2. 只有成功的工具调用才能在 IDE 中打开
  if (toolCall.status !== 'SUCCESS') {
    return false
  }

  // 3. 工具类型必须支持 IDE 集成
  const toolType = getToolType(toolCall)
  if (!toolType) {
    return false
  }

  // 支持 IDE 集成的工具类型
  const IDE_INTEGRATION_TOOLS: UiToolType[] = [
    UiToolType.READ,
    UiToolType.WRITE,
    UiToolType.EDIT,
    UiToolType.MULTI_EDIT,
    UiToolType.NOTEBOOK_EDIT
  ]

  return IDE_INTEGRATION_TOOLS.includes(toolType)
}

/**
 * 判断工具是否为 IDE 操作工具 (用于模板样式)
 * 这是 shouldUseIdeIntegration 的别名
 */
function isIdeActionTool(toolCall: ToolCall): boolean {
  return shouldUseIdeIntegration(toolCall)
}

function getToolDisplayName(toolCall: ToolCall): string {
  // TODO: 根据 viewModel 返回特殊名称
  return toolCall.name
}

function getToolSummary(toolCall: ToolCall): string {
  return toolCall.viewModel?.compactSummary || ''
}

/**
 * 获取工具的详细显示信息
 * 使用 toolDisplayInfo 提取更丰富的信息
 */
function getToolDisplayInfo(toolCall: ToolCall) {
  if (!toolCall.viewModel?.toolDetail) {
    return null
  }

  // 从 viewModel 构造 ToolUseBlock
  const toolUseBlock = {
    type: 'tool_use',
    id: toolCall.id,
    name: toolCall.name,
    input: toolCall.viewModel.toolDetail.parameters
  }

  // 提取 result 用于状态判断
  // ToolResult 是 discriminated union,需要根据 type 字段来判断类型
  const result = toolCall.result ? (() => {
    const r = toolCall.result!

    // 根据 type 字段判断类型
    const type = r.type?.toLowerCase()

    if (type === 'success') {
      return {
        is_error: false,
        content: r.output || ''
      }
    } else if (type === 'failure') {
      return {
        is_error: true,
        content: r.error || ''
      }
    } else if (type === 'fileedit') {
      // fileEdit 类型特殊处理
      return {
        is_error: false,
        content: `${r.oldContent || ''}\n---\n${r.newContent || ''}`
      }
    } else if (type === 'fileread') {
      return {
        is_error: false,
        content: r.content || ''
      }
    } else if (type === 'command') {
      return {
        is_error: r.exitCode !== 0,
        content: r.output || ''
      }
    } else {
      // 其他类型：尝试提取通用字段
      return {
        is_error: false,
        content: (r as any).output || (r as any).content || JSON.stringify(r)
      }
    }
  })() : undefined

  return extractToolDisplayInfo(toolUseBlock as any, result)
}

function getToolIcon(toolCall: ToolCall): string {
  const toolType = getToolType(toolCall)

  const iconMap: Partial<Record<UiToolType, string>> = {
    [UiToolType.READ]: '📖',
    [UiToolType.WRITE]: '📝',
    [UiToolType.EDIT]: '✏️',
    [UiToolType.MULTI_EDIT]: '🧰',
    [UiToolType.NOTEBOOK_EDIT]: '📒',
    [UiToolType.BASH]: '💻',
    [UiToolType.BASH_OUTPUT]: '💻',
    [UiToolType.KILL_SHELL]: '⛔',
    [UiToolType.GLOB]: '🔍',
    [UiToolType.GREP]: '🔍',
    [UiToolType.TODO_WRITE]: '✅',
    [UiToolType.TASK]: '🗂',
    [UiToolType.WEB_FETCH]: '🌐',
    [UiToolType.WEB_SEARCH]: '🌐',
    [UiToolType.MCP]: '🧩',
    [UiToolType.LIST_MCP_RESOURCES]: '🧩',
    [UiToolType.READ_MCP_RESOURCE]: '🧩',
    [UiToolType.EXIT_PLAN_MODE]: '🛑',
    [UiToolType.SLASH_COMMAND]: '⌨️',
    [UiToolType.ASK_USER_QUESTION]: '❓',
    [UiToolType.SKILL]: '🧠',
    [UiToolType.UNKNOWN]: '🛠'
  }

  if (toolType && iconMap[toolType]) {
    return iconMap[toolType]!
  }

  return '🛠'
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

.primary-info {
  font-size: 12px;
  color: var(--ide-foreground, #333);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.secondary-info {
  font-size: 11px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.6));
  font-style: italic;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.line-changes-badge {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  font-family: monospace;
  background: rgba(46, 125, 50, 0.15);
  color: #2E7D32;
  white-space: nowrap;
  flex-shrink: 0;
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

.theme-dark .primary-info {
  color: var(--ide-foreground, #e0e0e0);
}

.theme-dark .secondary-info {
  color: var(--ide-secondary-foreground, rgba(255, 255, 255, 0.6));
}

.theme-dark .tool-placeholder pre {
  background: var(--ide-code-background, #2b2b2b);
  color: var(--ide-foreground, #e0e0e0);
}
</style>
