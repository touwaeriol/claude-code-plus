<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="todos.length > 0"
    @click="handleCardClick"
  >
    <template #details>
      <div class="todo-list">
        <div
          v-for="(todo, index) in todos"
          :key="index"
          class="todo-item"
          :class="todo.status"
        >
          <span class="status-dot" />
          <span class="todo-text">{{ todo.content }}</span>
          <span class="todo-status">{{ getStatusText(todo.status) }}</span>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ClaudeTodoWriteToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

const { t } = useI18n()

interface TodoItem {
  content: string
  status: 'pending' | 'in_progress' | 'completed'
  activeForm?: string
}

interface Props {
  toolCall: ClaudeTodoWriteToolCall
}

const props = defineProps<Props>()
// TodoWrite 工具默认展开，显示任务列表
const expanded = ref(true)

// 提取工具显示信息
const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const todos = computed(() => (props.toolCall.input?.todos || []) as TodoItem[])

// 处理卡片点击：切换展开状态
function handleCardClick() {
  expanded.value = !expanded.value
}

// 获取状态文本
function getStatusText(status: string): string {
  const statusMap: Record<string, string> = {
    'pending': t('tools.todoTool.pending'),
    'in_progress': t('tools.todoTool.inProgress'),
    'completed': t('tools.todoTool.completed')
  }
  return statusMap[status] || status
}
</script>

<style scoped>
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 4px 0;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, rgba(0, 0, 0, 0.08));
  transition: background-color 0.15s ease;
}

.todo-item:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.02));
}

.todo-item.pending .status-dot {
  background: #fb8c00;
  box-shadow: 0 0 0 2px rgba(251, 140, 0, 0.15);
}

.todo-item.in_progress .status-dot {
  background: #1976d2;
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.15);
}

.todo-item.completed .status-dot {
  background: #2e7d32;
  box-shadow: 0 0 0 2px rgba(46, 125, 50, 0.15);
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.todo-text {
  flex: 1;
  color: var(--theme-foreground, #1a1a1a);
  font-size: 13px;
  line-height: 1.4;
}

.todo-status {
  font-size: 11px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--theme-panel-background, rgba(0, 0, 0, 0.02));
}
</style>
