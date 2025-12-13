<template>
  <div
    ref="wrapperRef"
    class="message-list-wrapper"
  >
    <div v-if="displayMessages.length === 0" class="empty-state">
      <div class="empty-content">
        <div class="shortcut-hints">
          <div class="shortcut-item">
            <kbd class="keyboard-key">Enter</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.sendHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Shift</kbd> + <kbd class="keyboard-key">Enter</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.newLineHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Esc</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.stopHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Tab</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.toggleThinkingHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Shift</kbd> + <kbd class="keyboard-key">Tab</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.switchModeHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Ctrl</kbd> + <kbd class="keyboard-key">Enter</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.interruptHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Ctrl</kbd> + <kbd class="keyboard-key">J</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.newLineHint') }}</span>
          </div>
          <div class="shortcut-item">
            <kbd class="keyboard-key">Ctrl</kbd> + <kbd class="keyboard-key">U</kbd>
            <span class="shortcut-desc">{{ t('chat.welcomeScreen.clearToLineStartHint') }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 使用 vue-virtual-scroller 的 DynamicScroller -->
    <DynamicScroller
      v-else
      ref="scrollerRef"
      class="message-list"
      :items="displayMessages"
      :min-item-size="60"
      :buffer="200"
      key-field="id"
      @scroll="handleScroll"
    >
      <template #default="{ item, index, active }">
        <DynamicScrollerItem
          :item="item"
          :active="active"
          :data-index="index"
          :size-dependencies="[
            item.content,
            item.status,
            item.result,
            item.input
          ]"
          :emit-resize="true"
        >
          <component
            :is="messageComponent"
            :source="item"
          />
        </DynamicScrollerItem>
      </template>
    </DynamicScroller>

    <!-- Streaming 状态指示器 -->
    <div
      v-if="isStreaming"
      class="streaming-indicator"
    >
      <span class="streaming-dot">●</span>
      <span class="streaming-stats">{{ streamingStats }}</span>
    </div>

    <div
      v-else-if="isLoading"
      class="loading-indicator"
    >
      <div class="loading-spinner" />
      <span>{{ t('chat.loadingHistory') }}</span>
    </div>

    <!-- 回到底部按钮 -->
    <transition name="fade-slide">
      <button
        v-if="showScrollToBottom"
        class="scroll-to-bottom-btn"
        :title="t('chat.scrollToBottom')"
        @click="scrollToBottom"
      >
        <span class="btn-icon">↓</span>
        <span
          v-if="newMessageCount > 0"
          class="new-message-badge"
        >{{ newMessageCount }}</span>
      </button>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { useSessionStore } from '@/stores/sessionStore'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import type { Message } from '@/types/message'
import type { DisplayItem } from '@/types/display'
import MessageDisplay from './MessageDisplay.vue'
import DisplayItemRenderer from './DisplayItemRenderer.vue'
import {
  HISTORY_TRIGGER_THRESHOLD,
  HISTORY_RESET_THRESHOLD,
  HISTORY_AUTO_LOAD_MAX
} from '@/constants/messageWindow'

const { t } = useI18n()
const sessionStore = useSessionStore()

interface Props {
  messages?: Message[]  // 保留向后兼容
  displayItems?: DisplayItem[]  // 新的 prop
  isLoading?: boolean
  isStreaming?: boolean  // 是否正在流式响应
  streamingStartTime?: number  // 流式响应开始时间
  inputTokens?: number  // 上行 token
  outputTokens?: number  // 下行 token
  connectionStatus?: 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'  // 连接状态
  hasMoreHistory?: boolean  // 顶部分页可用
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isStreaming: false,
  streamingStartTime: 0,
  inputTokens: 0,
  outputTokens: 0,
  connectionStatus: 'DISCONNECTED',
  hasMoreHistory: false
})

const emit = defineEmits<{
  (e: 'load-more-history'): void
}>()

const wrapperRef = ref<HTMLElement>()
const scrollerRef = ref<InstanceType<typeof DynamicScroller>>()
const showScrollToBottom = ref(false)
// newMessageCount 绑定到 sessionStore，随会话切换自动保存/恢复
const newMessageCount = computed({
  get: () => sessionStore.currentTab?.uiState.value.newMessageCount ?? 0,
  set: (val: number) => sessionStore.currentTab?.saveUiState({ newMessageCount: val })
})
const isNearBottom = ref(true)
const lastMessageCount = ref(0)
const lastTailId = ref<string | null>(null)
const historyLoadInProgress = ref(false)
const historyLoadRequested = ref(false)
const historyScrollHeightBefore = ref(0)
const historyScrollTopBefore = ref(0)
const hasLoadedHistory = ref(false)  // 标记是否已完成首次历史加载

// Streaming 计时器
const elapsedTime = ref(0)
let timerId: number | null = null

// 格式化耗时
function formatDuration(ms: number): string {
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSecs = seconds % 60
  if (minutes < 60) return `${minutes}m${remainingSecs}s`
  const hours = Math.floor(minutes / 60)
  const remainingMins = minutes % 60
  return `${hours}h${remainingMins}m${remainingSecs}s`
}

// 格式化 token 数量
function formatTokens(count: number): string {
  if (count < 1000) return `${count}`
  return `${(count / 1000).toFixed(1)}k`
}

// Streaming 状态显示
const streamingStats = computed(() => {
  const duration = formatDuration(elapsedTime.value)
  const input = formatTokens(props.inputTokens)
  const output = formatTokens(props.outputTokens)
  return `${duration} ↑${input} ↓${output} tokens`
})

// 启动计时器
function startTimer() {
  if (timerId !== null) return
  const startTime = props.streamingStartTime || Date.now()
  elapsedTime.value = Date.now() - startTime
  timerId = window.setInterval(() => {
    elapsedTime.value = Date.now() - startTime
  }, 100)
}

// 停止计时器
function stopTimer() {
  if (timerId !== null) {
    clearInterval(timerId)
    timerId = null
  }
}

// 自动滚动定时器
let autoScrollTimerId: number | null = null

// 启动自动滚动（在流式响应期间，如果用户在底部则定期滚动）
function startAutoScroll() {
  if (autoScrollTimerId !== null) return
  autoScrollTimerId = window.setInterval(() => {
    if (isNearBottom.value) {
      scrollToBottom()
    }
  }, 200) // 每 200ms 检查并滚动
}

// 停止自动滚动
function stopAutoScroll() {
  if (autoScrollTimerId !== null) {
    clearInterval(autoScrollTimerId)
    autoScrollTimerId = null
  }
}

// 监听 isStreaming 变化
watch(
  () => props.isStreaming,
  (streaming) => {
    if (streaming) {
      startTimer()
      startAutoScroll()  // 开始自动滚动
    } else {
      stopTimer()
      stopAutoScroll()   // 停止自动滚动
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (props.isStreaming) {
    startTimer()
    startAutoScroll()
  }
})

onUnmounted(() => {
  stopTimer()
  stopAutoScroll()
})

// 为虚拟列表准备数据源
// 优先使用 displayItems，如果没有则使用 messages（向后兼容）
const displayMessages = computed(() => props.displayItems || props.messages || [])

// 使用新的 DisplayItemRenderer 还是旧的 MessageDisplay
const messageComponent = computed(() => props.displayItems ? DisplayItemRenderer : MessageDisplay)

// 监听消息变化
watch(() => displayMessages.value.length, async (newCount, oldCount) => {
  const added = newCount - oldCount
  const tailId = newCount > 0 ? displayMessages.value[newCount - 1]?.id ?? null : null
  const tailChanged = tailId !== lastTailId.value

  // 首次批量加载（如历史回放尾页）默认跳到底部
  if (oldCount === 0 && newCount > 0) {
    lastMessageCount.value = newCount
    lastTailId.value = tailId
    await nextTick()
    scrollToBottom()
    forceUpdateScroller()
    return
  }

  // 历史分页期间不计未读，但需要更新滚动位置保持
  if (historyLoadInProgress.value && added > 0) {
    lastMessageCount.value = newCount
    lastTailId.value = tailId
    await nextTick()
    forceUpdateScroller()
    // 不滚动，由 isLoading watch 处理滚动位置保持
    return
  }

  // 如果是加载历史会话完成（从 loading 变为 false，且消息数量大于 0）
  // 此时应该滚动到底部
  if (props.isLoading === false && added > 0 && !historyLoadInProgress.value) {
    lastMessageCount.value = newCount
    lastTailId.value = tailId
    await nextTick()
    scrollToBottom()
    newMessageCount.value = 0
    forceUpdateScroller()
    return
  }

  // 如果不在底部，计数新消息并显示滚动按钮（不自动滚动）
  if (!isNearBottom.value && (added > 0 || tailChanged)) {
    newMessageCount.value = newMessageCount.value + (added > 0 ? added : 1)
    showScrollToBottom.value = true
    // 不自动滚动，让用户决定是否点击按钮
    lastMessageCount.value = newCount
    lastTailId.value = tailId
    await nextTick()
    forceUpdateScroller()
    return
  }

  // 只有在底部时才自动滚动
  if (isNearBottom.value) {
    await nextTick()
    scrollToBottom()
    newMessageCount.value = 0
  }

  lastMessageCount.value = newCount
  lastTailId.value = tailId

  // 强制 DynamicScroller 重新计算尺寸
  await nextTick()
  forceUpdateScroller()
})

// 监听消息内容变化（深度监听），强制重新计算尺寸
// 注意：虚拟滚动列表无法使用 CSS overflow-anchor，需手动保持滚动位置
watch(() => displayMessages.value, async () => {
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  const scrollTopBefore = el?.scrollTop ?? 0
  const wasNearBottom = isNearBottom.value

  await nextTick()
  forceUpdateScroller()

  // 用户不在底部时，保持滚动位置防止跳动（虚拟滚动的标准做法）
  if (!wasNearBottom && el) {
    requestAnimationFrame(() => {
      el.scrollTop = scrollTopBefore
    })
  }

  // 如果用户不在底部，确保按钮可见
  if (!isNearBottom.value && displayMessages.value.length > 0) {
    showScrollToBottom.value = true
  }
}, { deep: true })

// 强制 DynamicScroller 重新计算所有项目尺寸
  function forceUpdateScroller() {
    if (scrollerRef.value) {
      // @ts-expect-error - forceUpdate 是 DynamicScroller 暴露的实例方法
      scrollerRef.value.forceUpdate?.()
    }
  }

watch(() => props.isLoading, async (newValue, oldValue) => {
  // 加载开始时，如果在底部则保持在底部
  if (newValue && isNearBottom.value) {
    await nextTick()
    scrollToBottom()
  }

  // 加载完成
  if (!newValue && oldValue) {
    if (historyLoadInProgress.value) {
      // 历史分页加载完成：保持滚动位置
      await nextTick()
      const el = scrollerRef.value?.$el as HTMLElement | undefined
      if (el) {
        const delta = el.scrollHeight - historyScrollHeightBefore.value
        el.scrollTop = historyScrollTopBefore.value + delta
      }
      historyLoadInProgress.value = false
      // 重置懒加载请求标志，允许下次加载
      historyLoadRequested.value = false
    } else if (!hasLoadedHistory.value) {
      // 首次加载历史会话完成：自动填满视口并可靠滚动到底部
      hasLoadedHistory.value = true

      await nextTick()
      forceUpdateScroller()

      // 1. 先填满视口
      await ensureScrollable()

      // 2. 再可靠滚动
      await scrollToBottomReliably()

      // 3. 重置懒加载标志，允许后续手动触发
      historyLoadRequested.value = false
      historyLoadInProgress.value = false

      newMessageCount.value = 0
      isNearBottom.value = true
    }
  }
})

// 处理滚动事件
function handleScroll() {
  if (!scrollerRef.value) return

  const el = scrollerRef.value.$el as HTMLElement
  if (!el) return

  const scrollTop = el.scrollTop
  const scrollHeight = el.scrollHeight
  const clientHeight = el.clientHeight

  // 顶部分页 - 添加调试日志
  const shouldTrigger = scrollTop < HISTORY_TRIGGER_THRESHOLD &&
    props.hasMoreHistory &&
    !props.isLoading &&
    !historyLoadInProgress.value &&
    !historyLoadRequested.value

  if (scrollTop < HISTORY_TRIGGER_THRESHOLD && scrollTop < 100) {
    console.log('🔍 [懒加载检查]', {
      scrollTop,
      threshold: HISTORY_TRIGGER_THRESHOLD,
      hasMoreHistory: props.hasMoreHistory,
      isLoading: props.isLoading,
      historyLoadInProgress: historyLoadInProgress.value,
      historyLoadRequested: historyLoadRequested.value,
      shouldTrigger
    })
  }

  if (shouldTrigger) {
    console.log('✅ [懒加载] 触发加载更多历史')
    historyLoadRequested.value = true
    historyLoadInProgress.value = true
    historyScrollHeightBefore.value = scrollHeight
    historyScrollTopBefore.value = scrollTop
    emit('load-more-history')
  } else if (scrollTop > HISTORY_RESET_THRESHOLD) {
    // 只在加载完成后才重置，避免加载中重置
    if (!historyLoadInProgress.value) {
      historyLoadRequested.value = false
    }
  }

  // 判断是否在底部（允许 100px 的误差）
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight
  isNearBottom.value = distanceFromBottom < 100

  // 更新按钮显示状态
  showScrollToBottom.value = !isNearBottom.value && displayMessages.value.length > 0
}

function scrollToBottom() {
  if (scrollerRef.value) {
    // 使用 DynamicScroller 的 scrollToBottom 方法
    scrollerRef.value.scrollToBottom()
  } else if (wrapperRef.value) {
    // 降级方案: 消息列表为空时虚拟列表未渲染，使用原生滚动
    wrapperRef.value.scrollTop = wrapperRef.value.scrollHeight
  }

  // 重置状态
  showScrollToBottom.value = false
  newMessageCount.value = 0
  isNearBottom.value = true
}

/**
 * 检查是否有滚动条（视口是否被填满）
 */
function hasScrollbar(): boolean {
  if (!scrollerRef.value) return false
  const el = scrollerRef.value.$el as HTMLElement
  return el.scrollHeight > el.clientHeight
}

/**
 * 可靠地滚动到底部
 * 策略: 轮询检查滚动位置，直到真正到达底部或超时
 */
async function scrollToBottomReliably(maxRetries = 10, interval = 100): Promise<void> {
  let retries = 0

  const tryScroll = async () => {
    if (!scrollerRef.value) return false

    // 执行滚动
    scrollerRef.value.scrollToBottom()
    await nextTick()

    // 验证是否到达底部
    const el = scrollerRef.value.$el as HTMLElement
    if (!el) return false

    const scrollTop = el.scrollTop
    const scrollHeight = el.scrollHeight
    const clientHeight = el.clientHeight
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight

    // 允许10px的误差
    return distanceFromBottom < 10
  }

  // 第一次尝试
  const firstTry = await tryScroll()
  if (firstTry) return

  // 轮询重试
  return new Promise((resolve) => {
    const timer = setInterval(async () => {
      retries++
      const success = await tryScroll()

      if (success || retries >= maxRetries) {
        clearInterval(timer)
        if (!success && retries >= maxRetries) {
          console.warn('⚠️ 滚动到底部失败，已重试', maxRetries, '次')
        }
        resolve()
      }
    }, interval)
  })
}

/**
 * 自动加载直到填满视口或达到上限
 */
async function ensureScrollable(): Promise<void> {
  // 等待虚拟滚动器渲染
  await nextTick()
  await nextTick()

  let attempts = 0
  const MAX_ATTEMPTS = 10  // 防御性限制
  let totalLoaded = 0  // 记录自动加载的总消息数

  while (attempts < MAX_ATTEMPTS) {
    // 1️⃣ 先检查：视口是否已填满
    if (hasScrollbar()) {
      console.log('✅ 视口已填满，停止自动加载')
      break
    }

    // 2️⃣ 再判断：是否还有更多历史消息
    if (!props.hasMoreHistory) {
      console.log('📭 没有更多历史消息，停止加载（消息数量不足以填满视口）')
      break
    }

    // 3️⃣ 检查：是否超过自动加载上限
    if (totalLoaded >= HISTORY_AUTO_LOAD_MAX) {
      console.log(`📊 已自动加载 ${totalLoaded} 条消息，达到上限 ${HISTORY_AUTO_LOAD_MAX}，停止加载`)
      break
    }

    // 4️⃣ 继续加载
    console.log(`📏 视口未填满且有更多历史，自动加载第 ${attempts + 1} 批...`)
    emit('load-more-history')
    await nextTick()
    await new Promise(resolve => setTimeout(resolve, 300))  // 等待加载完成
    totalLoaded += 50  // 假设每次加载50条
    attempts++
  }

  if (attempts >= MAX_ATTEMPTS) {
    console.warn('⚠️ 达到最大尝试次数，停止自动加载')
  }
}
</script>

<style scoped>
.message-list-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0; /* 关键：防止 flex 子元素溢出 */
  background: var(--theme-background, #ffffff);
}

.message-list {
  flex: 1;
  min-height: 0; /* 关键：防止 flex 子元素溢出 */
  overflow-y: auto !important;
  overflow-x: hidden;
  padding: 4px 6px 16px 6px; /* 底部留出空隙 */
}

/* 修复 vue-virtual-scroller 的默认样式可能导致的内容截断 */
.message-list :deep(.vue-recycle-scroller__item-wrapper) {
  overflow: visible !important;
}

.message-list :deep(.vue-recycle-scroller__item-view) {
  overflow: visible !important;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px;
  color: var(--theme-foreground, #24292e);
}

.empty-content {
  max-width: 520px;
  text-align: center;
}

.shortcut-hints {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}

.shortcut-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
}

.shortcut-desc {
  min-width: 80px;
  text-align: left;
}

.keyboard-key {
  display: inline-block;
  padding: 3px 6px;
  font-size: 11px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  background: var(--theme-panel-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  color: var(--theme-foreground, #24292e);
  font-weight: 600;
}

/* Streaming 状态指示器 - 固定在底部（输入框上方） */
.streaming-indicator {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 4px 12px;
  margin: 0 8px;
  background: var(--theme-card-background, #ffffff);
  border: 1px solid var(--theme-accent, #0366d6);
  border-radius: 6px 6px 0 0;
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  color: var(--theme-text-secondary, #586069);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.1);
  z-index: 10;
}

.streaming-dot {
  color: var(--theme-accent, #0366d6);
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.streaming-stats {
  color: var(--theme-foreground, #24292e);
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  margin: 0 8px 8px 8px;
  background: var(--theme-card-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  color: var(--theme-text-secondary, #586069);
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--theme-border, #e1e4e8);
  border-top-color: var(--theme-primary, #0366d6);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 滚动条样式 */
.message-list::-webkit-scrollbar {
  width: 8px;
}

.message-list::-webkit-scrollbar-track {
  background: transparent;
}

.message-list::-webkit-scrollbar-thumb {
  background: var(--theme-scrollbar-thumb, #d1d5da);
  border-radius: 4px;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: var(--theme-scrollbar-thumb-hover, #959da5);
}

/* 回到底部按钮 */
.scroll-to-bottom-btn {
  position: absolute;
  bottom: 80px;
  right: 24px;
  width: 48px;
  height: 48px;
  background: var(--theme-accent, #0366d6);
  color: white;
  border: none;
  border-radius: 24px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.2s ease;
  z-index: 10;
}

.scroll-to-bottom-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
  background: var(--theme-accent-hover, #0256c2);
}

.scroll-to-bottom-btn:active {
  transform: translateY(0);
}

.scroll-to-bottom-btn .btn-icon {
  font-size: 20px;
  font-weight: bold;
}

.new-message-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  background: var(--theme-error, #d73a49);
  color: white;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

/* 过渡动画 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
