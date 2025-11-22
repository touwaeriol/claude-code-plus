<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="true"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="todo-list">
        <div
          v-for="(todo, index) in todos"
          :key="index"
          class="todo-item"
          :class="`todo-${todo.status}`"
        >
          <span class="todo-status-icon">{{ getStatusIcon(todo.status) }}</span>
          <span class="todo-content">{{ todo.content }}</span>
          <div
            v-if="todo.status === 'in_progress'"
            class="todo-active"
          >
            <span class="active-icon">⚡</span>
            <span class="active-text">{{ todo.activeForm }}</span>
          </div>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

interface Todo {
  content: string
  status: 'pending' | 'in_progress' | 'completed'
  activeForm: string
}

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()

// 默认展开
const expanded = ref(true)

const todos = computed(() => {
  if (!props.toolUse.input.todos) return []
  return props.toolUse.input.todos as Todo[]
})

// 获取当前正在执行的任务
const currentTask = computed(() => {
  const inProgress = todos.value.find(t => t.status === 'in_progress')
  return inProgress?.activeForm || inProgress?.content || ''
})

// 提取显示信息，并添加当前任务到 primaryInfo
const displayInfo = computed(() => {
  const baseInfo = extractToolDisplayInfo(props.toolUse, props.result)
  return {
    ...baseInfo,
    primaryInfo: `${todos.value.length}项任务`,
    // 在 secondaryInfo 显示当前正在执行的任务
    secondaryInfo: currentTask.value ? `⚡ ${currentTask.value}` : ''
  }
})

function getStatusIcon(status: string): string {
  const icons = {
    'pending': '⏳',
    'in_progress': '▶️',
    'completed': '✅'
  }
  return icons[status as keyof typeof icons] || '❓'
}
</script>

<style scoped>
/* 紧凑的任务列表 */
.todo-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

/* 任务项 - 紧凑布局 */
.todo-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 8px;
  font-size: 13px;
  line-height: 1.5;
  border-radius: 3px;
  transition: background-color 0.2s;
}

/* 任务状态图标 */
.todo-status-icon {
  font-size: 14px;
  flex-shrink: 0;
  margin-top: 1px;
}

/* 任务内容 */
.todo-content {
  flex: 1;
  color: var(--ide-foreground);
}

/* 已完成任务样式 */
.todo-item.todo-completed {
  opacity: 0.6;
}

.todo-item.todo-completed .todo-content {
  text-decoration: line-through;
  color: var(--ide-foreground);
}

/* 进行中任务样式 */
.todo-item.todo-in_progress {
  background: var(--ide-warning-background);
}

/* 待处理任务样式 */
.todo-item.todo-pending {
  background: transparent;
}

/* 进行中任务的活动表单 */
.todo-active {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 4px 6px;
  background: rgba(255, 193, 7, 0.15);
  border-radius: 3px;
  font-size: 12px;
  color: var(--ide-warning);
  font-style: italic;
}

.active-icon {
  font-size: 12px;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

/* 主题适配 - 暗色模式 */
html.dark .todo-item.todo-in_progress {
  background: var(--ide-warning-background);
}

html.dark .todo-active {
  background: rgba(255, 193, 7, 0.1);
}
</style>
