<template>
  <div class="assistant-message-display">
    <!-- 模型显示 -->
    <div
      v-if="message.model"
      class="model-display"
    >
      <span class="model-label">AI</span>
      <span class="model-name">{{ getModelDisplayName(message.model) }}</span>
    </div>

    <!-- orderedElements 渲染 -->
    <div class="ordered-elements">
      <div
        v-for="(element, index) in message.orderedElements"
        :key="getElementKey(element, index)"
        class="timeline-element"
      >
        <!-- ContentItem - 文本内容 -->
        <div
          v-if="element.type === 'content'"
          class="content-item"
        >
          <div v-if="isContentNotBlank(element.content)">
            <MarkdownRenderer
              :content="element.content"
              class="markdown-content"
              @link-click="handleLinkClick"
              @code-action="handleCodeAction"
            />
          </div>
        </div>

        <!-- ThinkingItem - 思考链 -->
        <div
          v-else-if="element.type === 'thinking'"
          class="thinking-item"
        >
          <div class="thinking-header">
            <span class="thinking-icon">💭</span>
            <span class="thinking-label">思考过程</span>
          </div>
          <div class="thinking-content">
            <MarkdownRenderer
              :content="element.content"
              class="markdown-content"
            />
          </div>
        </div>

        <!-- ToolCallItem - 工具调用 -->
        <CompactToolCallDisplay
          v-else-if="element.type === 'toolCall'"
          :tool-calls="[element.toolCall]"
          :expanded-tools="expandedTools"
          class="tool-call-item"
          @expanded-change="handleExpandedChange"
        />

        <!-- StatusItem - 状态显示 -->
        <div
          v-else-if="element.type === 'status'"
          class="status-item status-message-row"
        >
          <div v-if="element.isStreaming" class="jumping-dots-container">
            <span class="jumping-dot"></span>
            <span class="jumping-dot"></span>
            <span class="jumping-dot"></span>
          </div>
          <span class="status-text">{{ element.status }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownRenderer from '../markdown/MarkdownRenderer.vue'
import CompactToolCallDisplay from '../tools/CompactToolCallDisplay.vue'
import type { EnhancedMessage, AiModel, MessageTimelineItem } from '@/types/enhancedMessage'

// Props 定义
interface Props {
  message: EnhancedMessage
  expandedTools?: Map<string, boolean>
}

const props = withDefaults(defineProps<Props>(), {
  expandedTools: () => new Map()
})

// Emits 定义
const emit = defineEmits<{
  (e: 'expanded-change', toolId: string, expanded: boolean): void
}>()

// ============================================
// 工具函数
// ============================================

/**
 * 获取模型显示名称 (对应 model.displayName)
 */
function getModelDisplayName(model: AiModel): string {
  const modelMap: Record<string, string> = {
    'DEFAULT': '默认',
    'OPUS': 'Opus',
    'SONNET': 'Sonnet',
    'OPUS_PLAN': 'Opus Plan'
  }
  return modelMap[model] || model
}

/**
 * 获取元素唯一key (对应 "${message.id}-${element.timestamp}-$index-${element::class.simpleName}")
 */
function getElementKey(element: MessageTimelineItem, index: number): string {
  return `${props.message.id}-${element.timestamp}-${index}-${element.type}`
}

/**
 * 检查内容是否非空白 (对应 content.isNotBlank())
 */
function isContentNotBlank(content: string): boolean {
  return content && content.trim().length > 0
}

/**
 * 日志调试:文本片段渲染 (对应 logD)
 */
function _logContentRender(content: string, index: number, total: number) {
  const preview = content.length > 80 ? content.substring(0, 80) + '...' : content
  console.log(`[AssistantMessageDisplay] 渲染文本片段(${index + 1}/${total}): ${preview}`)
}

/**
 * 日志调试:工具调用渲染 (对应 logD)
 */
function _logToolCallRender(toolCall: any) {
  console.log(`[AssistantMessageDisplay] 渲染工具调用: ${toolCall.name} (${toolCall.id})`)
}

// ============================================
// 事件处理器
// ============================================

/**
 * 处理链接点击 (对应 onLinkClick)
 */
function handleLinkClick(url: string) {
  console.log('[AssistantMessageDisplay] 链接点击:', url)
}

/**
 * 处理代码操作 (对应 onCodeAction)
 */
function handleCodeAction(code: string, language: string) {
  console.log('[AssistantMessageDisplay] 代码操作: 语言=', language)
}

/**
 * 处理工具展开状态变化 (对应 onExpandedChange)
 */
function handleExpandedChange(toolId: string, expanded: boolean) {
  emit('expanded-change', toolId, expanded)
}
</script>

<style scoped>
/* 主容器样式 */
.assistant-message-display {
  display: flex;
  flex-direction: column;
  gap: 0px;
  width: 100%;
}

/* 模型显示样式 */
.model-display {
  display: flex;
  align-items: center;
  gap: 4px;
  user-select: text;
}

.model-label {
  font-size: 12px;
  color: var(--ide-foreground, #24292e);
}

.model-name {
  font-size: 12px;
  color: var(--ide-secondary-foreground, #586069);
  opacity: 0.7;
}

/* orderedElements 容器 */
.ordered-elements {
  display: flex;
  flex-direction: column;
  gap: 0px;
}

/* 时间线元素 */
.timeline-element {
  /* 继承父级样式 */
}

/* 文本内容项 */
.content-item {
  width: 100%;
}

.markdown-content {
  width: 100%;
}

/* 思考链项 */
.thinking-item {
  width: 100%;
  margin: 8px 0;
  padding: 12px;
  background: var(--ide-background-secondary, #f6f8fa);
  border-left: 3px solid var(--ide-accent, #0366d6);
  border-radius: 4px;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--ide-foreground, #24292e);
}

.thinking-icon {
  font-size: 16px;
}

.thinking-label {
  opacity: 0.8;
}

.thinking-content {
  font-size: 12px;
  color: var(--ide-secondary-foreground, #6b7280);
  font-style: italic;
  line-height: 1.6;
}

/* 工具调用项 */
.tool-call-item {
  width: 100%;
}

/* 状态项 */
.status-item {
  /* 继承父级样式 */
}

/* 降级内容 */
.fallback-content {
  width: 100%;
}

/* StatusMessageRow 样式 (对应 lines 111-129) */
.status-message-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-text {
  font-size: 12px;
  color: var(--ide-secondary-foreground, #586069);
  opacity: 0.7;
}

/* JumpingDots 动画组件样式 */
.jumping-dots-container {
  display: flex;
  align-items: center;
  gap: 3px;
  padding-right: 6px;
}

.jumping-dot {
  width: 4px;
  height: 4px;
  background-color: var(--ide-secondary-foreground, #586069);
  border-radius: 50%;
  animation: jump 1.4s infinite ease-in-out;
}

.jumping-dot:nth-child(1) {
  animation-delay: 0s;
}

.jumping-dot:nth-child(2) {
  animation-delay: 0.2s;
}

.jumping-dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes jump {
  0%, 80%, 100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  40% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

.dot {
  display: inline-block;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background-color: var(--ide-accent, #0366d6);
  animation: jump 1.4s infinite ease-in-out both;
}

.dot-1 {
  animation-delay: -0.32s;
}

.dot-2 {
  animation-delay: -0.16s;
}

.dot-3 {
  animation-delay: 0s;
}

@keyframes jump {
  0%, 80%, 100% {
    transform: translateY(0);
    opacity: 0.7;
  }
  40% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

/* 暗色主题适配 */
.theme-dark .model-label {
  color: var(--ide-foreground, #e0e0e0);
}

.theme-dark .model-name {
  color: var(--ide-secondary-foreground, #8b949e);
}

.theme-dark .status-text {
  color: var(--ide-secondary-foreground, #8b949e);
}

.theme-dark .dot {
  background-color: var(--ide-accent, #4a9eff);
}
</style>
