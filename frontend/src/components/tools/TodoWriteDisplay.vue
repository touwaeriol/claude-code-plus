<template>
  <div class="tool-display todo-tool">
    <div class="tool-header">
      <span class="tool-icon">✅</span>
      <span class="tool-name">TodoWrite</span>
      <span class="todo-count">{{ todoCount }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="todo-list">
        <div
          v-for="(todo, index) in todos"
          :key="index"
          class="todo-item"
          :class="`todo-${todo.status}`"
        >
          <div class="todo-header">
            <span class="todo-status-icon">{{ getStatusIcon(todo.status) }}</span>
            <span class="todo-content">{{ todo.content }}</span>
          </div>
          <div v-if="todo.status === 'in_progress'" class="todo-active">
            <span class="active-icon">⚡</span>
            <span class="active-text">{{ todo.activeForm }}</span>
          </div>
        </div>
      </div>

      <div v-if="stats" class="todo-stats">
        <div class="stat-item">
          <span class="stat-label">总计:</span>
          <span class="stat-value">{{ stats.total }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">完成:</span>
          <span class="stat-value completed">{{ stats.completed }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">进行中:</span>
          <span class="stat-value in-progress">{{ stats.inProgress }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">待处理:</span>
          <span class="stat-value pending">{{ stats.pending }}</span>
        </div>
      </div>
    </div>
    <button class="expand-btn" @click="expanded = !expanded">
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

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
const expanded = ref(false)

const todos = computed(() => {
  if (!props.toolUse.input.todos) return []
  return props.toolUse.input.todos as Todo[]
})

const todoCount = computed(() => {
  const total = todos.value.length
  const completed = todos.value.filter(t => t.status === 'completed').length
  return `${completed}/${total}`
})

const stats = computed(() => {
  const total = todos.value.length
  const completed = todos.value.filter(t => t.status === 'completed').length
  const inProgress = todos.value.filter(t => t.status === 'in_progress').length
  const pending = todos.value.filter(t => t.status === 'pending').length

  return { total, completed, inProgress, pending }
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
.todo-tool {
  border-color: #34d058;
}

.todo-tool .tool-name {
  color: #22863a;
}

.todo-count {
  font-size: 11px;
  font-weight: 600;
  background: #22863a;
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
}

.todo-list {
  margin-bottom: 12px;
}

.todo-item {
  padding: 10px 12px;
  margin-bottom: 6px;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  background: #ffffff;
}

.todo-item.todo-completed {
  background: #e6ffed;
  border-color: #34d058;
}

.todo-item.todo-in_progress {
  background: #fff8dc;
  border-color: #ffc107;
}

.todo-item.todo-pending {
  background: #f6f8fa;
  border-color: #e1e4e8;
}

.todo-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-status-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.todo-content {
  font-size: 13px;
  color: #24292e;
  flex: 1;
}

.todo-item.todo-completed .todo-content {
  text-decoration: line-through;
  color: #6a737d;
}

.todo-active {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 8px;
  background: rgba(255, 193, 7, 0.2);
  border-radius: 3px;
  font-size: 12px;
  color: #856404;
}

.active-icon {
  font-size: 14px;
  animation: pulse 1.5s ease-in-out infinite;
}

.active-text {
  font-style: italic;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.todo-stats {
  display: flex;
  gap: 16px;
  padding: 12px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-label {
  color: #586069;
  font-weight: 600;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: #24292e;
}

.stat-value.completed {
  color: #22863a;
}

.stat-value.in-progress {
  color: #ffc107;
}

.stat-value.pending {
  color: #6a737d;
}
</style>
