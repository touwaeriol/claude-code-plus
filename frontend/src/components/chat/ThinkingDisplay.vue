<template>
  <div
    class="thinking-display"
    :class="{ collapsed: isCollapsed, expandable: isComplete }"
    @click="handleClick"
  >
    <div class="thinking-header">
      <span class="thinking-icon">💭</span>
      <span class="thinking-label">
        {{ isCollapsed ? t('chat.thinkingCollapsed') : t('chat.thinkingLabel') }}
      </span>
      <span v-if="isComplete" class="expand-hint">
        {{ isCollapsed ? '▶' : '▼' }}
      </span>
    </div>
    <div v-if="!isCollapsed" class="thinking-content">
      <MarkdownRenderer
        :content="thinking.content"
        class="markdown-content"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import MarkdownRenderer from '../markdown/MarkdownRenderer.vue'
import type { ThinkingContent } from '@/types/display'
import { useI18n } from '@/composables/useI18n'

interface Props {
  thinking: ThinkingContent
}

const props = defineProps<Props>()
const { t } = useI18n()

// 思考是否完成（有 signature 表示完成）
const isComplete = computed(() => !!props.thinking.signature)

// 本地展开/折叠状态（用户手动操作）
const isExpanded = ref(false)

// 计算是否应该折叠：思考进行中不折叠，完成后默认折叠（除非用户展开）
const isCollapsed = computed(() => {
  if (!isComplete.value) {
    return false // 思考进行中，保持展开
  }
  return !isExpanded.value // 思考完成后，根据用户操作决定
})

// 点击切换展开/折叠（仅在思考完成后有效）
function handleClick() {
  if (isComplete.value) {
    isExpanded.value = !isExpanded.value
  }
}
</script>

<style scoped>
.thinking-display {
  width: 100%;
  margin: 0;
  padding: 6px 10px;
  background: color-mix(in srgb, var(--theme-secondary-foreground) 8%, transparent);
  border-left: 3px solid color-mix(in srgb, var(--theme-secondary-foreground) 35%, transparent);
  border-radius: 4px;
  transition: all 0.2s ease;
}

.thinking-display.expandable {
  cursor: pointer;
}

.thinking-display.expandable:hover {
  background: color-mix(in srgb, var(--theme-secondary-foreground) 12%, transparent);
}

.thinking-display.collapsed {
  padding: 4px 10px;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-secondary-foreground);
}

.thinking-icon {
  font-size: 14px;
  opacity: 0.7;
}

.thinking-label {
  opacity: 0.7;
  font-style: italic;
  flex: 1;
}

.expand-hint {
  font-size: 10px;
  opacity: 0.5;
  margin-left: auto;
}

.thinking-content {
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  font-style: italic;
  line-height: 1.6;
  opacity: 0.85;
  margin-top: 4px;
}

.markdown-content {
  width: 100%;
}
</style>
