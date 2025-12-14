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
  gap: 0;
  padding: 2px 0;
  width: 100%;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-bottom: 1px solid var(--theme-border, rgba(0, 0, 0, 0.06));
  transition: background-color 0.15s ease;
}

.todo-item:last-child {
  border-bottom: none;
}

.todo-item:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.02));
}

.todo-item.pending .status-dot {
  background: #fb8c00;
}

.todo-item.in_progress .status-dot {
  background: #1976d2;
}

.todo-item.completed .status-dot {
  background: #2e7d32;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.todo-text {
  flex: 1;
  color: var(--theme-foreground, #1a1a1a);
  font-size: 12px;
  line-height: 1.3;
}

.todo-status {
  font-size: 10px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
  font-weight: 500;
}
</style>
