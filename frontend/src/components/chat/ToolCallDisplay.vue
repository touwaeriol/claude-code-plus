<template>
  <div class="tool-call-display">
    <component :is="resolvedComponent" :tool-call="toolCall" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCall } from '@/types/display'
import { CLAUDE_TOOL_COMPONENTS } from '@/components/chat/tool-displays/claudeRegistry'
import { CODEX_TOOL_COMPONENTS } from '@/components/chat/tool-displays/codexRegistry'
import GenericToolDisplay from '@/components/tools/GenericToolDisplay.vue'
import JetBrainsMcpToolDisplay from '@/components/tools/JetBrainsMcpToolDisplay.vue'
import TerminalMcpToolDisplay from '@/components/tools/TerminalMcpToolDisplay.vue'
import AskUserQuestionDisplay from '@/components/tools/AskUserQuestionDisplay.vue'

interface Props {
  toolCall: ToolCall
}

const props = defineProps<Props>()

const isJetBrainsMcpTool = computed(() => {
  const name = props.toolCall?.toolName || ''
  // 匹配所有 IDE 相关的 MCP 工具：ide-lsp、ide-file、ide-git 等
  // 支持命名格式：mcp__ide-xxx（统一格式）
  // 注意：ide-terminal 由 TerminalMcpToolDisplay 专门处理
  return name.startsWith('mcp__ide-') && !name.startsWith('mcp__ide-terminal__')
})

const isTerminalMcpTool = computed(() => {
  const name = props.toolCall?.toolName || ''
  // 支持两种格式：mcp__terminal__ (旧格式) 和 mcp__ide-terminal__ (新格式)
  return name.startsWith('mcp__terminal__') || name.startsWith('mcp__ide-terminal__')
})

const isAskUserQuestionMcpTool = computed(() => {
  const name = props.toolCall?.toolName || ''
  // MCP 版本的 AskUserQuestion（Codex 后端仍通过 MCP 调用）
  return name.endsWith('AskUserQuestion') && (
    name.includes('user-interaction') || name.includes('user_interaction')
  )
})

const isCodexToolType = computed(() => {
  return props.toolCall?.toolType?.startsWith('CODEX_') ?? false
})

const resolvedComponent = computed(() => {
  if (isJetBrainsMcpTool.value) {
    return JetBrainsMcpToolDisplay
  }
  if (isTerminalMcpTool.value) {
    return TerminalMcpToolDisplay
  }
  if (isAskUserQuestionMcpTool.value) {
    return AskUserQuestionDisplay
  }

  const registry = isCodexToolType.value ? CODEX_TOOL_COMPONENTS : CLAUDE_TOOL_COMPONENTS
  return registry[props.toolCall.toolType] ?? GenericToolDisplay
})
</script>

<style scoped>
.tool-call-display {
  margin: 1px 0;
}
</style>
