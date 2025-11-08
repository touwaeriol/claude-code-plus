<template>
  <div
    class="message"
    :class="`message-${message.role.toLowerCase()}`"
  >
    <div class="message-header">
      <span class="message-role">
        {{ getRoleDisplay(message.role) }}
      </span>
      <span
        v-if="message.model"
        class="message-model"
      >{{ getModelDisplay(message.model) }}</span>
      <span class="message-time">{{ formattedTime }}</span>
      <span
        v-if="message.isStreaming"
        class="streaming-indicator"
      >●</span>
    </div>

    <!-- 上下文引用显示 -->
    <div
      v-if="message.contexts && message.contexts.length > 0"
      class="message-contexts"
    >
      <div
        v-for="(context, index) in message.contexts"
        :key="`context-${index}`"
        class="context-chip"
      >
        {{ getContextDisplay(context) }}
      </div>
    </div>

    <div class="message-content">
      <!-- 按时间线顺序渲染所有元素 -->
      <div
        v-for="(element, index) in message.orderedElements"
        :key="`element-${index}-${element.timestamp}`"
        class="timeline-element"
      >
        <!-- 文本内容元素 -->
        <div
          v-if="element.type === 'content'"
          class="content-element"
        >
          <MarkdownRenderer :content="element.content" />
        </div>

        <!-- 工具调用元素 -->
        <component
          :is="getToolComponent(element.toolCall.name)"
          v-else-if="element.type === 'toolCall'"
          :tool-call="element.toolCall"
          class="tool-element"
        />

        <!-- 状态元素 -->
        <div
          v-else-if="element.type === 'status'"
          class="status-element"
          :class="{ streaming: element.isStreaming }"
        >
          <span class="status-icon">⏳</span>
          <span class="status-text">{{ element.status }}</span>
        </div>
      </div>
    </div>

    <!-- Token 使用信息 -->
    <div
      v-if="message.tokenUsage && message.status === 'COMPLETE'"
      class="token-usage"
    >
      <span class="token-label">Token:</span>
      <span class="token-value">{{ message.tokenUsage.totalTokens }}</span>
      <span class="token-detail">(输入: {{ message.tokenUsage.inputTokens }}, 输出: {{ message.tokenUsage.outputTokens }})</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownRenderer from '../markdown/MarkdownRenderer.vue'
import type { EnhancedMessage, ContextReference, AiModel, MessageRole } from '@/types/enhancedMessage'

// 导入所有工具组件
import ReadToolDisplay from '../tools/ReadToolDisplay.vue'
import EditToolDisplay from '../tools/EditToolDisplay.vue'
import WriteToolDisplay from '../tools/WriteToolDisplay.vue'
import BashToolDisplay from '../tools/BashToolDisplay.vue'
import GrepToolDisplay from '../tools/GrepToolDisplay.vue'
import GlobToolDisplay from '../tools/GlobToolDisplay.vue'
import TaskToolDisplay from '../tools/TaskToolDisplay.vue'
import TodoWriteDisplay from '../tools/TodoWriteDisplay.vue'
import WebFetchToolDisplay from '../tools/WebFetchToolDisplay.vue'
import WebSearchToolDisplay from '../tools/WebSearchToolDisplay.vue'
import MultiEditToolDisplay from '../tools/MultiEditToolDisplay.vue'
import NotebookEditToolDisplay from '../tools/NotebookEditToolDisplay.vue'
import BashOutputToolDisplay from '../tools/BashOutputToolDisplay.vue'
import KillShellToolDisplay from '../tools/KillShellToolDisplay.vue'
import ExitPlanModeToolDisplay from '../tools/ExitPlanModeToolDisplay.vue'
import SlashCommandToolDisplay from '../tools/SlashCommandToolDisplay.vue'
import SkillToolDisplay from '../tools/SkillToolDisplay.vue'
import AskUserQuestionDisplay from '../tools/AskUserQuestionDisplay.vue'
import ListMcpResourcesToolDisplay from '../tools/ListMcpResourcesToolDisplay.vue'
import ReadMcpResourceToolDisplay from '../tools/ReadMcpResourceToolDisplay.vue'
import GenericMcpToolDisplay from '../tools/GenericMcpToolDisplay.vue'

interface Props {
  message: EnhancedMessage
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isDark: false
})

// 格式化时间
const formattedTime = computed(() => {
  const date = new Date(props.message.timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

// 获取角色显示文本
function getRoleDisplay(role: MessageRole): string {
  const roleMap: Record<string, string> = {
    USER: '👤 你',
    ASSISTANT: '🤖 Claude',
    SYSTEM: '⚙️ 系统',
    ERROR: '❌ 错误'
  }
  return roleMap[role] || role
}

// 获取模型显示文本
function getModelDisplay(model: AiModel): string {
  const modelMap: Record<string, string> = {
    DEFAULT: '默认',
    OPUS: 'Opus',
    SONNET: 'Sonnet',
    OPUS_PLAN: 'Opus Plan'
  }
  return modelMap[model] || model
}

// 获取上下文显示文本
function getContextDisplay(context: ContextReference): string {
  if ('path' in context) {
    const pathStr = (context as any).path
    return pathStr.split(/[\\/]/).pop() || pathStr
  }
  if ('url' in context) {
    return (context as any).title || (context as any).url
  }
  return context.uri
}

// 根据工具名称获取对应的组件
function getToolComponent(toolName: string) {
  const normalized = toolName.toLowerCase()

  // 精确匹配
  const toolMap: Record<string, any> = {
    'read': ReadToolDisplay,
    'edit': EditToolDisplay,
    'write': WriteToolDisplay,
    'bash': BashToolDisplay,
    'grep': GrepToolDisplay,
    'glob': GlobToolDisplay,
    'task': TaskToolDisplay,
    'todowrite': TodoWriteDisplay,
    'webfetch': WebFetchToolDisplay,
    'websearch': WebSearchToolDisplay,
    'multiedit': MultiEditToolDisplay,
    'notebookedit': NotebookEditToolDisplay,
    'bashoutput': BashOutputToolDisplay,
    'bash_output': BashOutputToolDisplay,
    'killshell': KillShellToolDisplay,
    'kill_shell': KillShellToolDisplay,
    'exitplanmode': ExitPlanModeToolDisplay,
    'exit_plan_mode': ExitPlanModeToolDisplay,
    'slashcommand': SlashCommandToolDisplay,
    'slash_command': SlashCommandToolDisplay,
    'skill': SkillToolDisplay,
    'askuserquestion': AskUserQuestionDisplay,
    'ask_user_question': AskUserQuestionDisplay,
    'listmcpresourcestool': ListMcpResourcesToolDisplay,
    'list_mcp_resources': ListMcpResourcesToolDisplay,
    'readmcpresourcetool': ReadMcpResourceToolDisplay,
    'read_mcp_resource': ReadMcpResourceToolDisplay
  }

  // 移除下划线和短横线进行匹配
  const cleanName = normalized.replace(/[-_]/g, '')
  const component = toolMap[cleanName] || toolMap[normalized]

  // MCP 工具特殊处理
  if (!component && normalized.includes('mcp__')) {
    return GenericMcpToolDisplay
  }

  // 返回组件或通用工具显示
  return component || GenericMcpToolDisplay
}
</script>

<style scoped>
.message {
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 12px;
  background: var(--ide-card-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
}

.message-user {
  background: var(--ide-selection-background, #e3f2fd);
  border-color: var(--ide-accent, #0366d6);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--ide-secondary-foreground, #586069);
}

.message-role {
  font-weight: 600;
  color: var(--ide-foreground, #24292e);
}

.message-model {
  padding: 2px 6px;
  background: var(--ide-info-background, #f0f0f0);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.message-time {
  margin-left: auto;
  font-size: 11px;
}

.streaming-indicator {
  color: var(--ide-accent, #0366d6);
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

.message-contexts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.context-chip {
  padding: 4px 8px;
  background: var(--ide-panel-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 11px;
  font-family: monospace;
  color: var(--ide-link, #0366d6);
}

.message-content {
  color: var(--ide-foreground, #24292e);
  line-height: 1.6;
}

.timeline-element {
  margin-bottom: 8px;
}

.timeline-element:last-child {
  margin-bottom: 0;
}

.content-element {
  /* Markdown 内容样式由 MarkdownRenderer 处理 */
}

.tool-element {
  /* 工具组件样式由各自的工具组件处理 */
}

.status-element {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--ide-info-background, #f0f0f0);
  border-left: 3px solid var(--ide-accent, #0366d6);
  border-radius: 4px;
  font-size: 13px;
  color: var(--ide-secondary-foreground, #586069);
}

.status-element.streaming {
  border-left-color: var(--ide-warning, #ffc107);
}

.status-icon {
  font-size: 14px;
}

.status-text {
  font-style: italic;
}

.token-usage {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--ide-panel-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 11px;
  color: var(--ide-secondary-foreground, #586069);
}

.token-label {
  font-weight: 600;
  margin-right: 4px;
}

.token-value {
  font-weight: 600;
  color: var(--ide-foreground, #24292e);
}

.token-detail {
  margin-left: 8px;
  opacity: 0.8;
}

/* 暗色主题适配 */
.theme-dark .message {
  background: var(--ide-card-background, #2b2b2b);
  border-color: var(--ide-border, #3c3c3c);
}

.theme-dark .message-user {
  background: var(--ide-selection-background, #1a3a52);
  border-color: var(--ide-accent, #4a9eff);
}

.theme-dark .context-chip {
  background: var(--ide-panel-background, #323232);
  border-color: var(--ide-border, #3c3c3c);
}

.theme-dark .status-element {
  background: var(--ide-info-background, #2a2a2a);
}

.theme-dark .token-usage {
  background: var(--ide-panel-background, #323232);
  border-color: var(--ide-border, #3c3c3c);
}
</style>
