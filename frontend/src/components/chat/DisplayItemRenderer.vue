<template>
  <div class="display-item-renderer">
    <!-- 用户消息 -->
    <UserMessageBubble
      v-if="item.displayType === 'userMessage'"
      :message="item as any"
    />

    <!-- AI 文本回复 -->
    <AssistantTextDisplay
      v-else-if="item.displayType === 'assistantText'"
      :message="item"
    />

    <!-- 思考内容 -->
    <ThinkingDisplay
      v-else-if="item.displayType === 'thinking'"
      :thinking="item"
    />

    <!-- 工具调用 -->
    <ToolCallDisplay
      v-else-if="item.displayType === 'toolCall'"
      :tool-call="item"
    />

    <!-- 系统消息 -->
    <SystemMessageDisplay
      v-else-if="item.displayType === 'systemMessage'"
      :message="item"
    />

    <!-- 错误结果 -->
    <ErrorResultDisplay
      v-else-if="item.displayType === 'errorResult'"
      :error-message="item.message"
    />

    <!-- 压缩摘要 -->
    <CompactSummaryCard
      v-else-if="item.displayType === 'compactSummary'"
      :content="item.content"
      :pre-tokens="item.preTokens"
      :trigger="item.trigger"
    />

    <!-- 本地命令输出 -->
    <LocalCommandOutput
      v-else-if="item.displayType === 'localCommandOutput'"
      :command="item.command"
      :output-type="item.outputType"
    />

    <!-- 未知类型 -->
    <div
      v-else
      class="unknown-item"
    >
      <span>Unknown item displayType: {{ (item as any).displayType ?? 'undefined' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DisplayItem } from '@/types/display'
import UserMessageBubble from './UserMessageBubble.vue'
import AssistantTextDisplay from './AssistantTextDisplay.vue'
import ThinkingDisplay from './ThinkingDisplay.vue'
import ToolCallDisplay from './ToolCallDisplay.vue'
import SystemMessageDisplay from './SystemMessageDisplay.vue'
import ErrorResultDisplay from './ErrorResultDisplay.vue'
import CompactSummaryCard from './CompactSummaryCard.vue'
import LocalCommandOutput from './LocalCommandOutput.vue'

interface Props {
  source: DisplayItem
}

const props = defineProps<Props>()

const item = computed(() => props.source)
</script>

<style scoped>
.display-item-renderer {
  width: 100%;
  /* DynamicScroller 高度计算不包含外边距，把间距放在内边距避免重叠 */
  padding: 1px 0;
  box-sizing: border-box;
  min-height: 18px;
}

.unknown-item {
  padding: 6px 8px;
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 6px;
  color: #856404;
  font-size: 13px;
  text-align: center;
}
</style>
