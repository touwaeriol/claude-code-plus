<template>
  <div class="tool-use-display">
    <!-- Codex CommandExecution → Bash 风格显示 -->
    <BashToolDisplay
      v-if="isCodexCommandExecution"
      :tool-call="asClaudeBashToolCall"
    />

    <!-- Codex FileChange → Write/Edit 显示 -->
    <template v-else-if="isCodexFileChange">
      <!-- 新建文件 → Write 显示 -->
      <WriteToolDisplay
        v-if="fileChangeIsCreate"
        :tool-call="asClaudeWriteToolCall"
      />
      <!-- 修改文件 → Edit 显示 -->
      <EditToolDisplay
        v-else
        :tool-call="asClaudeEditToolCall"
      />
    </template>

    <!-- Codex McpToolCall → MCP 显示 -->
    <GenericMcpToolDisplay
      v-else-if="isCodexMcpToolCall"
      :tool-call="asMcpToolCall"
    />

    <!-- Codex Reasoning → Thinking 显示（注意：Reasoning 通常作为 ThinkingContent 显示，不是 ToolCall） -->
    <div
      v-else-if="isCodexReasoning"
      class="codex-reasoning-placeholder"
    >
      <CompactToolCard
        :display-info="reasoningDisplayInfo"
        :is-expanded="false"
        :has-details="false"
      />
    </div>

    <!-- Claude SDK 工具调用 - 使用现有的 ToolCallDisplay -->
    <ToolCallDisplay
      v-else-if="isClaudeToolCall"
      :tool-call="toolCall"
    />

    <!-- 未知后端工具 -->
    <div
      v-else
      class="unknown-backend-tool"
    >
      <CompactToolCard
        :display-info="unknownToolDisplayInfo"
        :is-expanded="false"
        :has-details="false"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCall } from '@/types/display'
import type { BackendType } from '@/types/backend'
import { CLAUDE_TOOL_TYPE, OTHER_TOOL_TYPE } from '@/constants/toolTypes'
import CompactToolCard from './CompactToolCard.vue'

// 工具显示组件
import ToolCallDisplay from '@/components/chat/ToolCallDisplay.vue'
import BashToolDisplay from './BashToolDisplay.vue'
import WriteToolDisplay from './WriteToolDisplay.vue'
import EditToolDisplay from './EditToolDisplay.vue'
import GenericMcpToolDisplay from './GenericMcpToolDisplay.vue'

interface Props {
  toolCall: ToolCall
  /** 后端类型（用于区分不同后端的工具） */
  backendType?: BackendType
}

const props = withDefaults(defineProps<Props>(), {
  backendType: 'claude' as BackendType
})

// ============================================================================
// 后端类型判断
// ============================================================================

const isClaudeBackend = computed(() => props.backendType === 'claude')
const isCodexBackend = computed(() => props.backendType === 'codex')

// ============================================================================
// Codex 工具类型判断
// ============================================================================

/**
 * Codex CommandExecution 工具
 * 参考 Codex JSON-RPC 格式：
 * {
 *   "type": "CommandExecution",
 *   "command": "ls -la",
 *   "cwd": "/path/to/dir",
 *   "output": "...",
 *   "exitCode": 0
 * }
 */
const isCodexCommandExecution = computed(() => {
  if (!isCodexBackend.value) return false
  return props.toolCall.toolName === 'CommandExecution' ||
         (props.toolCall.input as any)?.type === 'CommandExecution'
})

/**
 * Codex FileChange 工具
 * 参考 Codex JSON-RPC 格式：
 * {
 *   "type": "FileChange",
 *   "path": "/path/to/file",
 *   "operation": "create" | "edit" | "delete",
 *   "content": "...",
 *   "diff": "..."
 * }
 */
const isCodexFileChange = computed(() => {
  if (!isCodexBackend.value) return false
  return props.toolCall.toolName === 'FileChange' ||
         (props.toolCall.input as any)?.type === 'FileChange'
})

const fileChangeIsCreate = computed(() => {
  const operation = (props.toolCall.input as any)?.operation
  return operation === 'create'
})

/**
 * Codex McpToolCall 工具
 */
const isCodexMcpToolCall = computed(() => {
  if (!isCodexBackend.value) return false
  return props.toolCall.toolName === 'McpToolCall' ||
         (props.toolCall.input as any)?.type === 'McpToolCall'
})

/**
 * Codex Reasoning 项
 * 注意：通常 Reasoning 应该作为 ThinkingContent 显示，而非 ToolCall
 * 这里作为兜底处理
 */
const isCodexReasoning = computed(() => {
  if (!isCodexBackend.value) return false
  return props.toolCall.toolName === 'Reasoning' ||
         (props.toolCall.input as any)?.type === 'Reasoning'
})

const isClaudeToolCall = computed(() => {
  return isClaudeBackend.value || (
    !isCodexCommandExecution.value &&
    !isCodexFileChange.value &&
    !isCodexMcpToolCall.value &&
    !isCodexReasoning.value
  )
})

// ============================================================================
// Codex 工具转换为 Claude 格式（适配现有显示组件）
// ============================================================================

/**
 * 将 Codex CommandExecution 转换为 Claude Bash 工具格式
 */
const asClaudeBashToolCall = computed(() => {
  const input = props.toolCall.input as any
  return {
    ...props.toolCall,
    toolType: CLAUDE_TOOL_TYPE.BASH,
    input: {
      command: input.command || input.cmd || '',
      cwd: input.cwd || input.workingDirectory,
      timeout: input.timeout,
      description: input.description
    },
    result: adaptCodexResultToClaudeFormat(props.toolCall.result, input.output || input.stdout)
  }
})

/**
 * 将 Codex FileChange (create) 转换为 Claude Write 工具格式
 */
const asClaudeWriteToolCall = computed(() => {
  const input = props.toolCall.input as any
  return {
    ...props.toolCall,
    toolType: CLAUDE_TOOL_TYPE.WRITE,
    input: {
      file_path: input.path || input.filePath,
      path: input.path || input.filePath,
      content: input.content || ''
    },
    result: adaptCodexResultToClaudeFormat(props.toolCall.result)
  }
})

/**
 * 将 Codex FileChange (edit) 转换为 Claude Edit 工具格式
 */
const asClaudeEditToolCall = computed(() => {
  const input = props.toolCall.input as any
  return {
    ...props.toolCall,
    toolType: CLAUDE_TOOL_TYPE.EDIT,
    input: {
      file_path: input.path || input.filePath,
      old_string: input.oldContent || input.before || '',
      new_string: input.newContent || input.after || input.content || '',
      replace_all: input.replaceAll ?? false
    },
    result: adaptCodexResultToClaudeFormat(props.toolCall.result)
  }
})

/**
 * 将 Codex McpToolCall 转换为 MCP 工具格式
 */
const asMcpToolCall = computed(() => {
  const input = props.toolCall.input as any
  return {
    ...props.toolCall,
    toolType: OTHER_TOOL_TYPE.MCP,
    toolName: input.toolName || input.name || 'mcp__unknown',
    input: input.parameters || input.args || input,
    result: adaptCodexResultToClaudeFormat(props.toolCall.result)
  }
})

/**
 * 适配 Codex 结果格式到 Claude 格式
 * Codex 格式：{ success: boolean, output?: string, error?: string }
 * Claude 格式：{ content: string | unknown[], is_error: boolean }
 */
function adaptCodexResultToClaudeFormat(codexResult: any, fallbackOutput?: string) {
  if (!codexResult) {
    return {
      content: fallbackOutput || '',
      is_error: false
    }
  }

  // Codex 格式
  if ('success' in codexResult || 'error' in codexResult) {
    const isError = codexResult.success === false || !!codexResult.error
    const content = isError
      ? (codexResult.error || 'Unknown error')
      : (codexResult.output || codexResult.result || fallbackOutput || '')

    return {
      content,
      is_error: isError
    }
  }

  // 已经是 Claude 格式或其他格式，直接返回
  return codexResult
}

// ============================================================================
// 显示信息（用于兜底显示）
// ============================================================================

const reasoningDisplayInfo = computed(() => ({
  icon: '🧠',
  actionType: 'Reasoning',
  primaryInfo: 'Thinking',
  status: props.toolCall.status === 'SUCCESS' ? 'success' : 'pending'
}))

const unknownToolDisplayInfo = computed(() => ({
  icon: '🔧',
  actionType: 'Unknown Tool',
  primaryInfo: props.toolCall.toolName,
  secondaryInfo: `Backend: ${props.backendType}`,
  status: props.toolCall.status === 'SUCCESS' ? 'success' : 'error'
}))
</script>

<style scoped>
.tool-use-display {
  width: 100%;
}

.codex-reasoning-placeholder,
.unknown-backend-tool {
  opacity: 0.8;
}

.unknown-backend-tool {
  border: 1px dashed var(--theme-border);
  border-radius: 4px;
}
</style>
