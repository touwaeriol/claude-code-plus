<template>
  <div class="display-item-renderer">
    <!-- 用户消息 -->
    <UserMessageBubble
      v-if="item.displayType === 'userMessage'"
      :message="item"
    />

    <!-- AI 文本回复 -->
    <AssistantTextDisplay
      v-else-if="item.displayType === 'assistantText'"
      :message="item"
      :is-dark="isDark"
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

    <!-- 未知类型 -->
    <div
      v-else
      class="unknown-item"
    >
      <span>Unknown item displayType: {{ 'displayType' in item ? item.displayType : 'undefined' }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { DisplayItem } from '@/types/display'
import UserMessageBubble from './UserMessageBubble.vue'
import AssistantTextDisplay from './AssistantTextDisplay.vue'
import ThinkingDisplay from './ThinkingDisplay.vue'
import ToolCallDisplay from './ToolCallDisplay.vue'
import SystemMessageDisplay from './SystemMessageDisplay.vue'

interface Props {
  // VirtualList 会把当前项作为 source 传入
  source: DisplayItem
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isDark: false
})

// 为了模板可读性，提供一个 item 计算属性
const item = computed(() => props.source)
</script>

<script lang="ts">
import { computed } from 'vue'
</script>

<style scoped>
.display-item-renderer {
  width: 100%;
  /* DynamicScroller 高度计算不包含外边距，把间距放在内边距避免重叠 */
  padding: 2px 0;
  box-sizing: border-box;
  min-height: 20px;
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
