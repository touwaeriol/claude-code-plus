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
    <EnterPlanModeToolDisplay
      v-else-if="toolCall.toolType === CLAUDE_TOOL_TYPE.ENTER_PLAN_MODE"
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

    <!-- JetBrains MCP 工具使用专用展示组件 -->
    <JetBrainsMcpToolDisplay
      v-else-if="isJetBrainsMcpTool"
      :tool-call="toolCall"
    />

    <!-- MCP/未知/未识别的工具统一使用紧凑卡片 -->
    <GenericMcpToolDisplay
      v-else
      :tool-call="toolCall"
    />
  </div>
</template>

<script setup lang="ts">
import type { ToolCall } from '@/types/display'
import { CLAUDE_TOOL_TYPE } from '@/constants/toolTypes'

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
import EnterPlanModeToolDisplay from '@/components/tools/EnterPlanModeToolDisplay.vue'
import SkillToolDisplay from '@/components/tools/SkillToolDisplay.vue'
import SlashCommandToolDisplay from '@/components/tools/SlashCommandToolDisplay.vue'
import ListMcpResourcesToolDisplay from '@/components/tools/ListMcpResourcesToolDisplay.vue'
import ReadMcpResourceToolDisplay from '@/components/tools/ReadMcpResourceToolDisplay.vue'

// MCP 工具
import GenericMcpToolDisplay from '@/components/tools/GenericMcpToolDisplay.vue'
import JetBrainsMcpToolDisplay from '@/components/tools/JetBrainsMcpToolDisplay.vue'

import { computed } from 'vue'

interface Props {
  toolCall: ToolCall
}

const props = defineProps<Props>()

// 判断是否为 JetBrains MCP 工具
const isJetBrainsMcpTool = computed(() => {
  return props.toolCall.toolName?.startsWith('mcp__jetbrains__')
})

</script>

<style scoped>
.tool-call-display {
  margin: 2px 0;
}
</style>
