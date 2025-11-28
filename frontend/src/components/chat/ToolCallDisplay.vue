<template>
  <div class="tool-call-display">
    <!-- 使用 v-if 判断实现 TypeScript 类型收窄 -->

    <!-- 文件操作工具 -->
    <ReadToolDisplay
      v-if="toolCall.toolType === CLAUDE_TOOL_TYPE.READ"
      :tool-call="toolCall"
    />
    <WriteToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.WRITE"
      :tool-call="toolCall"
    />
    <EditToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.EDIT"
      :tool-call="toolCall"
    />
    <MultiEditToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.MULTI_EDIT"
      :tool-call="toolCall"
    />

    <!-- 终端工具 -->
    <BashToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.BASH"
      :tool-call="toolCall"
    />
    <BashOutputToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.BASH_OUTPUT"
      :tool-call="toolCall"
    />
    <KillShellToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.KILL_SHELL"
      :tool-call="toolCall"
    />

    <!-- 搜索工具 -->
    <GrepToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.GREP"
      :tool-call="toolCall"
    />
    <GlobToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.GLOB"
      :tool-call="toolCall"
    />
    <WebSearchToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.WEB_SEARCH"
      :tool-call="toolCall"
    />
    <WebFetchToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.WEB_FETCH"
      :tool-call="toolCall"
    />

    <!-- 任务管理工具 -->
    <TodoWriteDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.TODO_WRITE"
      :tool-call="toolCall"
    />
    <TaskToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.TASK"
      :tool-call="toolCall"
    />

    <!-- 交互工具 -->
    <AskUserQuestionDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.ASK_USER_QUESTION"
      :tool-call="toolCall"
    />

    <!-- 其他 Claude 工具 -->
    <NotebookEditToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.NOTEBOOK_EDIT"
      :tool-call="toolCall"
    />
    <ExitPlanModeToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.EXIT_PLAN_MODE"
      :tool-call="toolCall"
    />
    <SkillToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.SKILL"
      :tool-call="toolCall"
    />
    <SlashCommandToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.SLASH_COMMAND"
      :tool-call="toolCall"
    />
    <ListMcpResourcesToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.LIST_MCP_RESOURCES"
      :tool-call="toolCall"
    />
    <ReadMcpResourceToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.READ_MCP_RESOURCE"
      :tool-call="toolCall"
    />

    <!-- MCP 工具（通用组件） -->
    <GenericMcpToolDisplay
      v-else-if="toolCall.toolType === OTHER_TOOL_TYPE.MCP"
      :tool-call="toolCall"
    />

    <!-- 未知工具（兜底） -->
    <div v-else class="generic-tool">
      <div class="tool-header">
        <span class="tool-icon">🛠</span>
        <span class="tool-name">{{ toolCall.toolName }}</span>
        <span class="tool-type-badge">{{ toolCall.toolType }}</span>
        <span
          class="tool-status"
          :class="`status-${toolCall.status.toLowerCase()}`"
        >
          {{ statusText }}
        </span>
      </div>

      <div v-if="toolCall.input" class="tool-section">
        <div class="section-title">Input</div>
        <pre>{{ formatJson(toolCall.input) }}</pre>
      </div>

      <div v-if="toolCall.result" class="tool-section">
        <pre>{{ formatResult(toolCall.result) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'
import { CLAUDE_TOOL_TYPE, OTHER_TOOL_TYPE } from '@/constants/toolTypes'

// 文件操作工具
import ReadToolDisplay from '@/components/tools/ReadToolDisplay.vue'
import WriteToolDisplay from '@/components/tools/WriteToolDisplay.vue'
import EditToolDisplay from '@/components/tools/EditToolDisplay.vue'
import MultiEditToolDisplay from '@/components/tools/MultiEditToolDisplay.vue'

// 终端工具
import BashToolDisplay from '@/components/tools/BashToolDisplay.vue'
import BashOutputToolDisplay from '@/components/tools/BashOutputToolDisplay.vue'
import KillShellToolDisplay from '@/components/tools/KillShellToolDisplay.vue'

// 搜索工具
import GrepToolDisplay from '@/components/tools/GrepToolDisplay.vue'
import GlobToolDisplay from '@/components/tools/GlobToolDisplay.vue'
import WebSearchToolDisplay from '@/components/tools/WebSearchToolDisplay.vue'
import WebFetchToolDisplay from '@/components/tools/WebFetchToolDisplay.vue'

// 任务管理工具
import TodoWriteDisplay from '@/components/tools/TodoWriteDisplay.vue'
import TaskToolDisplay from '@/components/tools/TaskToolDisplay.vue'

// 交互工具
import AskUserQuestionDisplay from '@/components/tools/AskUserQuestionDisplay.vue'

// 其他 Claude 工具
import NotebookEditToolDisplay from '@/components/tools/NotebookEditToolDisplay.vue'
import ExitPlanModeToolDisplay from '@/components/tools/ExitPlanModeToolDisplay.vue'
import SkillToolDisplay from '@/components/tools/SkillToolDisplay.vue'
import SlashCommandToolDisplay from '@/components/tools/SlashCommandToolDisplay.vue'
import ListMcpResourcesToolDisplay from '@/components/tools/ListMcpResourcesToolDisplay.vue'
import ReadMcpResourceToolDisplay from '@/components/tools/ReadMcpResourceToolDisplay.vue'

// MCP 工具
import GenericMcpToolDisplay from '@/components/tools/GenericMcpToolDisplay.vue'

interface Props {
  toolCall: ToolCall
}

const props = defineProps<Props>()

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
  if (!result) return ''
  // 使用后端格式：直接读取 content
  if (typeof result.content === 'string') {
    return result.content
  }
  if (Array.isArray(result.content)) {
    return (result.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }
  if (typeof result === 'string') return result
  return JSON.stringify(result, null, 2)
}

function formatJson(value: any): string {
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value ?? '')
  }
}
</script>

<style scoped>
.tool-call-display {
  margin: 2px 0;
}

.generic-tool {
  padding: 8px 12px;
  background: var(--tool-bg, #f8f9fa);
  border: 1px solid var(--tool-border, #e9ecef);
  border-radius: 6px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
  color: var(--text-primary, #333);
}

.tool-type-badge {
  padding: 2px 6px;
  background: var(--badge-bg, #e9ecef);
  border-radius: 4px;
  font-size: 11px;
  color: var(--text-secondary, #666);
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

.tool-section {
  margin-top: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-secondary, #666);
  margin-bottom: 4px;
}

.tool-section pre {
  margin: 0;
  padding: 8px;
  background: var(--code-bg, #fff);
  border: 1px solid var(--code-border, #e0e0e0);
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 200px;
  overflow: auto;
}
</style>
