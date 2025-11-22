<template>
  <div class="typed-tool-display">
    <!-- 根据工具类型调用专业化组件 -->
    <component
      :is="getToolComponent(toolCall.name)"
      :tool-use="toolUseBlock"
      :result="toolResultBlock"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import type { ToolCall } from '@/types/enhancedMessage'
import ReadToolDisplay from './ReadToolDisplay.vue'
import EditToolDisplay from './EditToolDisplay.vue'
import MultiEditToolDisplay from './MultiEditToolDisplay.vue'
import WriteToolDisplay from './WriteToolDisplay.vue'
import BashToolDisplay from './BashToolDisplay.vue'
import GrepToolDisplay from './GrepToolDisplay.vue'
import GlobToolDisplay from './GlobToolDisplay.vue'
import TodoWriteDisplay from './TodoWriteDisplay.vue'
import WebSearchToolDisplay from './WebSearchToolDisplay.vue'
import WebFetchToolDisplay from './WebFetchToolDisplay.vue'
import AskUserQuestionDisplay from './AskUserQuestionDisplay.vue'
import NotebookEditToolDisplay from './NotebookEditToolDisplay.vue'
import TaskToolDisplay from './TaskToolDisplay.vue'
import SlashCommandToolDisplay from './SlashCommandToolDisplay.vue'
import SkillToolDisplay from './SkillToolDisplay.vue'
import GenericMcpToolDisplay from './GenericMcpToolDisplay.vue'
import BashOutputToolDisplay from './BashOutputToolDisplay.vue'
import KillShellToolDisplay from './KillShellToolDisplay.vue'
import ListMcpResourcesToolDisplay from './ListMcpResourcesToolDisplay.vue'
import ReadMcpResourceToolDisplay from './ReadMcpResourceToolDisplay.vue'
import ExitPlanModeToolDisplay from './ExitPlanModeToolDisplay.vue'

interface Props {
  toolCall: ToolCall
}

const props = defineProps<Props>()

// 将 ToolCall 转换为旧的 ToolUseBlock 格式（兼容现有组件）
const toolUseBlock = computed(() => ({
  type: 'tool_use' as const,
  id: props.toolCall.id,
  name: props.toolCall.name,
  input: props.toolCall.viewModel?.toolDetail?.parameters || {}
}))

// 将 ToolCall.result 转换为 ToolResultBlock 格式
const toolResultBlock = computed(() => {
  if (!props.toolCall.result) return undefined
  
  return {
    type: 'tool_result' as const,
    tool_use_id: props.toolCall.id,
    content: props.toolCall.result.output,
    is_error: props.toolCall.result.isError || false
  }
})

// 根据工具名称返回对应的组件
function getToolComponent(toolName: string): Component {
  // 防御性检查：如果 toolName 为 undefined 或 null，返回默认组件
  if (!toolName) {
    return GenericMcpToolDisplay
  }

  const componentMap: Record<string, Component> = {
    'Read': ReadToolDisplay,
    'Edit': EditToolDisplay,
    'MultiEdit': MultiEditToolDisplay,
    'Write': WriteToolDisplay,
    'Bash': BashToolDisplay,
    'Grep': GrepToolDisplay,
    'Glob': GlobToolDisplay,
    'TodoWrite': TodoWriteDisplay,
    'WebSearch': WebSearchToolDisplay,
    'WebFetch': WebFetchToolDisplay,
    'AskUserQuestion': AskUserQuestionDisplay,
    'NotebookEdit': NotebookEditToolDisplay,
    'Task': TaskToolDisplay,
    'SlashCommand': SlashCommandToolDisplay,
    'Skill': SkillToolDisplay,
    'BashOutput': BashOutputToolDisplay,
    'KillShell': KillShellToolDisplay,
    'ListMcpResourcesTool': ListMcpResourcesToolDisplay,
    'ReadMcpResourceTool': ReadMcpResourceToolDisplay,
    'ExitPlanMode': ExitPlanModeToolDisplay
  }

  // 尝试精确匹配
  if (componentMap[toolName]) {
    return componentMap[toolName]
  }

  // MCP 工具使用通用显示器
  if (toolName.startsWith('mcp__')) {
    return GenericMcpToolDisplay
  }

  // 默认返回通用 MCP 显示器
  return GenericMcpToolDisplay
}
</script>

<style scoped>
.typed-tool-display {
  width: 100%;
}
</style>

