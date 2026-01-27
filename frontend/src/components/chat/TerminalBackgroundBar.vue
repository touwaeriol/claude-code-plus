<template>
  <div v-if="hasBackgroundableTasks" class="terminal-background-bar">
    <!-- 头部 -->
    <div class="background-header">
      <div class="header-left">
        <span class="background-icon">⏳</span>
        <span class="background-title">{{ t('tools.longRunningTerminals') }}</span>
        <span class="task-count">({{ backgroundableTasks.length }})</span>
        <span class="hint">{{ t('tools.ctrlBToBackground') }}</span>
      </div>
      <div class="header-actions">
        <button
          class="background-all-btn"
          :class="{ loading: isBackgroundingAll }"
          :disabled="isBackgroundingAll"
          :title="t('tools.backgroundAll')"
          @click="handleBackgroundAll"
        >
          <span v-if="isBackgroundingAll" class="spinner" />
          <span v-else>⏸ {{ t('tools.backgroundAll') }}</span>
        </button>
      </div>
    </div>

    <!-- 任务列表 -->
    <div class="task-list">
      <div
        v-for="task in backgroundableTasks"
        :key="task.toolUseId"
        class="task-item"
      >
        <div class="task-info">
          <span class="task-session">{{ task.sessionId }}</span>
          <span class="task-command" :title="task.command">{{ truncateCommand(task.command) }}</span>
          <span class="task-elapsed">{{ formatElapsed(task.elapsedMs) }}</span>
        </div>
        <button
          class="background-btn"
          :class="{ loading: isBackgrounding(task.toolUseId) }"
          :disabled="isBackgrounding(task.toolUseId) || isBackgroundingAll"
          :title="t('tools.backgroundTask')"
          @click="handleBackgroundTask(task)"
        >
          <span v-if="isBackgrounding(task.toolUseId)" class="spinner-small" />
          <span v-else>⏸</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { ideaRSocket, TerminalBackgroundStatus, type BackgroundableTerminal, type TerminalTaskUpdate } from '@/services/ideaRSocket'
import { ideaBridge } from '@/services/ideaBridge'
import { useToastStore } from '@/stores/toastStore'

const { t } = useI18n()
const toastStore = useToastStore()

// 可后台化的任务列表
const backgroundableTasks = ref<BackgroundableTerminal[]>([])

// 正在后台化的任务
const backgroundingTasks = ref<Set<string>>(new Set())
const isBackgroundingAll = ref(false)

// 注：已改为事件驱动更新，不再使用轮询
// 显示阈值（毫秒）- 超过 5 秒的任务才显示
const DISPLAY_THRESHOLD = 5000

let unsubscribeTaskUpdate: (() => void) | null = null
let elapsedTimer: ReturnType<typeof setInterval> | null = null

// 是否有可后台化的任务
const hasBackgroundableTasks = computed(() => {
  return ideaBridge.isInIde() && backgroundableTasks.value.length > 0
})

// 检查特定任务是否正在后台化
function isBackgrounding(toolUseId: string): boolean {
  return backgroundingTasks.value.has(toolUseId)
}

// 处理终端任务更新事件
function handleTerminalTaskUpdate(update: TerminalTaskUpdate) {
  console.log('[TerminalBackgroundBar] 收到任务更新:', update.action, update.toolUseId)
  
  if (update.action === 'started' && !update.isBackground) {
    // 新任务开始，添加到列表（如果不是后台任务）
    const existingIndex = backgroundableTasks.value.findIndex(t => t.toolUseId === update.toolUseId)
    if (existingIndex === -1) {
      backgroundableTasks.value.push({
        toolUseId: update.toolUseId,
        sessionId: update.sessionId,
        command: update.command,
        elapsedMs: update.elapsedMs || 0,
        startTime: update.startTime
      })
    }
  } else if (update.action === 'completed' || update.action === 'backgrounded') {
    // 任务完成或后台化，从列表中移除
    backgroundableTasks.value = backgroundableTasks.value.filter(
      t => t.toolUseId !== update.toolUseId
    )
  }
}

// 更新已运行时间（每秒更新一次）
function updateElapsedTime() {
  const now = Date.now()
  backgroundableTasks.value.forEach(task => {
    if (task.startTime) {
      task.elapsedMs = now - task.startTime
    }
  })
}

// 初始获取可后台化的任务
async function fetchInitialTasks() {
  if (!ideaBridge.isInIde()) return

  try {
    const terminals = await ideaRSocket.getBackgroundableTerminals()
    // 只显示超过阈值的任务
    backgroundableTasks.value = terminals.filter(
      task => task.elapsedMs >= DISPLAY_THRESHOLD
    )
  } catch (err) {
    console.error('[TerminalBackgroundBar] 获取初始任务失败:', err)
  }
}

// 后台化单个任务
async function handleBackgroundTask(task: BackgroundableTerminal) {
  if (isBackgrounding(task.toolUseId)) return
  
  backgroundingTasks.value.add(task.toolUseId)
  
  try {
    await new Promise<void>((resolve, reject) => {
      ideaRSocket.terminalBackground(
        [{ sessionId: task.sessionId, toolUseId: task.toolUseId }],
        (event) => {
          console.log('[TerminalBackgroundBar] 后台化事件:', event)
          if (event.status === TerminalBackgroundStatus.SUCCESS || event.status === TerminalBackgroundStatus.FAILED) {
            if (event.status === TerminalBackgroundStatus.FAILED && event.error) {
              toastStore.error(event.error)
            }
            // 从列表中移除该任务
            backgroundableTasks.value = backgroundableTasks.value.filter(
              t => t.toolUseId !== task.toolUseId
            )
            resolve()
          }
        },
        () => resolve(),
        (error) => reject(error)
      )
    })
  } catch (err) {
    console.error('[TerminalBackgroundBar] 后台化任务失败:', err)
    toastStore.error(t('tools.backgroundFailed'))
  } finally {
    backgroundingTasks.value.delete(task.toolUseId)
  }
}

// 后台化所有任务
async function handleBackgroundAll() {
  if (isBackgroundingAll.value || backgroundableTasks.value.length === 0) return
  
  isBackgroundingAll.value = true
  
  const items = backgroundableTasks.value.map(task => ({
    sessionId: task.sessionId,
    toolUseId: task.toolUseId
  }))
  
  try {
    await new Promise<void>((resolve, reject) => {
      const completedTasks = new Set<string>()
      
      ideaRSocket.terminalBackground(
        items,
        (event) => {
          console.log('[TerminalBackgroundBar] 批量后台化事件:', event)
          completedTasks.add(event.toolUseId)

          if (event.status === TerminalBackgroundStatus.FAILED && event.error) {
            toastStore.error(`${event.sessionId}: ${event.error}`)
          }

          // 从列表中移除已处理的任务
          backgroundableTasks.value = backgroundableTasks.value.filter(
            t => !completedTasks.has(t.toolUseId)
          )
        },
        () => resolve(),
        (error) => reject(error)
      )
    })
  } catch (err) {
    console.error('[TerminalBackgroundBar] 批量后台化失败:', err)
    toastStore.error(t('tools.backgroundFailed'))
  } finally {
    isBackgroundingAll.value = false
  }
}

// 格式化经过时间
function formatElapsed(ms: number): string {
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) {
    return `${seconds}s`
  }
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}m ${remainingSeconds}s`
}

// 截断命令显示
function truncateCommand(command: string, maxLength = 50): string {
  if (command.length <= maxLength) return command
  return command.substring(0, maxLength - 3) + '...'
}

// 全局 Ctrl+B 快捷键处理（在 capture 阶段处理，优先于 ChatInput）
function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'b' && event.ctrlKey && !event.shiftKey && !event.altKey) {
    if (hasBackgroundableTasks.value) {
      event.preventDefault()
      event.stopPropagation()  // 阻止事件继续传播到 ChatInput
      handleBackgroundAll()
    }
  }
}

onMounted(() => {
  // 初始获取任务列表
  fetchInitialTasks()
  
  // 订阅终端任务更新事件
  unsubscribeTaskUpdate = ideaRSocket.onTerminalTaskUpdate(handleTerminalTaskUpdate)
  
  // 启动定时器更新已运行时间
  elapsedTimer = setInterval(updateElapsedTime, 1000)

  // 在 capture 阶段监听全局快捷键，确保在 ChatInput 之前处理
  window.addEventListener('keydown', handleKeydown, true)
})

onUnmounted(() => {
  // 取消订阅终端任务更新事件
  if (unsubscribeTaskUpdate) {
    unsubscribeTaskUpdate()
    unsubscribeTaskUpdate = null
  }
  
  // 停止定时器
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }

  // 移除快捷键监听
  window.removeEventListener('keydown', handleKeydown, true)
})
</script>

<style scoped>
.terminal-background-bar {
  background: var(--theme-warning-background, #fff8e6);
  border: 1px solid var(--theme-warning-border, #f0c36d);
  border-radius: 6px;
  padding: 8px 12px;
  margin: 8px 0;
}

.background-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.background-icon {
  font-size: 14px;
}

.background-title {
  font-weight: 600;
  color: var(--theme-foreground, #24292e);
}

.task-count {
  color: var(--theme-secondary-foreground, #586069);
}

.hint {
  font-size: 11px;
  color: var(--theme-secondary-foreground, #586069);
  background: var(--theme-code-background, #f6f8fa);
  padding: 2px 6px;
  border-radius: 3px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.background-all-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: 12px;
  background: var(--theme-accent, #0366d6);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.background-all-btn:hover:not(:disabled) {
  background: var(--theme-accent-hover, #0256b9);
}

.background-all-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.background-all-btn.loading {
  pointer-events: none;
}

.task-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  background: var(--theme-background, #fff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
}

.task-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.task-session {
  font-size: 11px;
  font-family: var(--theme-editor-font-family);
  color: var(--theme-accent, #0366d6);
  background: var(--theme-code-background, #f6f8fa);
  padding: 1px 4px;
  border-radius: 3px;
  flex-shrink: 0;
}

.task-command {
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  color: var(--theme-foreground, #24292e);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.task-elapsed {
  font-size: 11px;
  color: var(--theme-warning);
  font-weight: 500;
  flex-shrink: 0;
}

.background-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  background: var(--theme-secondary-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.background-btn:hover:not(:disabled) {
  background: var(--theme-accent, #0366d6);
  color: white;
  border-color: var(--theme-accent, #0366d6);
}

.background-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spinner,
.spinner-small {
  display: inline-block;
  border: 2px solid transparent;
  border-top-color: currentColor;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.spinner {
  width: 12px;
  height: 12px;
}

.spinner-small {
  width: 10px;
  height: 10px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
