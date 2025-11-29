<template>
  <div
    ref="wrapperRef"
    class="message-list-wrapper"
  >
    <div
      v-if="displayMessages.length === 0"
      class="empty-state"
    >
      <div class="empty-content">
        <div class="empty-icon-wrapper">
          <svg
            class="empty-icon"
            viewBox="0 0 24 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <path
              d="M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12C4 7.59 7.59 4 12 4C16.41 4 20 7.59 20 12C20 16.41 16.41 20 12 20Z"
              fill="currentColor"
              opacity="0.3"
            />
            <path
              d="M13 7H11V13H13V7Z"
              fill="currentColor"
            />
            <path
              d="M13 15H11V17H13V15Z"
              fill="currentColor"
            />
          </svg>
        </div>
        <h2 class="empty-title">
          {{ t('chat.welcomeScreen.title') }}
        </h2>
        <p class="empty-description">
          {{ t('chat.welcomeScreen.description') }}
        </p>
        <div class="empty-tips">
          <div class="tip-item">
            <span class="tip-icon">💡</span>
            <span class="tip-text">{{ t('chat.welcomeScreen.askCode') }}</span>
          </div>
          <div class="tip-item">
            <span class="tip-icon">🔧</span>
            <span class="tip-text">{{ t('chat.welcomeScreen.refactor') }}</span>
          </div>
          <div class="tip-item">
            <span class="tip-icon">🐛</span>
            <span class="tip-text">{{ t('chat.welcomeScreen.debug') }}</span>
          </div>
        </div>
        <div class="empty-hint">
          <kbd class="keyboard-key">Enter</kbd> {{ t('chat.welcomeScreen.sendHint') }} ·
          <kbd class="keyboard-key">Shift</kbd> + <kbd class="keyboard-key">Enter</kbd> {{ t('chat.welcomeScreen.newLineHint') }}
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
      <span>{{ t('chat.claudeThinking') }}</span>
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
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import type { Message } from '@/types/message'
import type { DisplayItem } from '@/types/display'
import MessageDisplay from './MessageDisplay.vue'
import DisplayItemRenderer from './DisplayItemRenderer.vue'

const { t } = useI18n()

interface Props {
  messages?: Message[]  // 保留向后兼容
  displayItems?: DisplayItem[]  // 新的 prop
  isLoading?: boolean
  isStreaming?: boolean  // 是否正在流式响应
  streamingStartTime?: number  // 流式响应开始时间
  inputTokens?: number  // 上行 token
  outputTokens?: number  // 下行 token
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isStreaming: false,
  streamingStartTime: 0,
  inputTokens: 0,
  outputTokens: 0
})

const wrapperRef = ref<HTMLElement>()
const scrollerRef = ref<InstanceType<typeof DynamicScroller>>()
const showScrollToBottom = ref(false)
const newMessageCount = ref(0)
const isNearBottom = ref(true)
const lastMessageCount = ref(0)

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

// 监听 isStreaming 变化
watch(
  () => props.isStreaming,
  (streaming) => {
    if (streaming) {
      startTimer()
    } else {
      stopTimer()
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (props.isStreaming) {
    startTimer()
  }
})

onUnmounted(() => {
  stopTimer()
})

// 为虚拟列表准备数据源
// 优先使用 displayItems，如果没有则使用 messages（向后兼容）
const displayMessages = computed(() => props.displayItems || props.messages || [])

// 使用新的 DisplayItemRenderer 还是旧的 MessageDisplay
const messageComponent = computed(() => props.displayItems ? DisplayItemRenderer : MessageDisplay)

// 监听消息变化
watch(() => displayMessages.value.length, async (newCount, oldCount) => {
  // 如果不在底部，计数新消息
  if (!isNearBottom.value && newCount > oldCount) {
    newMessageCount.value += (newCount - oldCount)
  }

  // 如果在底部，自动滚动
  if (isNearBottom.value) {
    await nextTick()
    scrollToBottom()
    newMessageCount.value = 0
  }

  lastMessageCount.value = newCount

  // 强制 DynamicScroller 重新计算尺寸
  await nextTick()
  forceUpdateScroller()
})

// 监听消息内容变化（深度监听），强制重新计算尺寸
watch(() => displayMessages.value, async () => {
  await nextTick()
  forceUpdateScroller()
}, { deep: true })

// 强制 DynamicScroller 重新计算所有项目尺寸
function forceUpdateScroller() {
  if (scrollerRef.value) {
    // @ts-ignore - forceUpdate 是 DynamicScroller 的方法
    scrollerRef.value.forceUpdate?.()
  }
}

watch(() => props.isLoading, async (newValue) => {
  if (newValue && isNearBottom.value) {
    await nextTick()
    scrollToBottom()
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
</script>

<style scoped>
.message-list-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0; /* 关键：防止 flex 子元素溢出 */
  background: var(--theme-background, #fafbfc);
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
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.empty-icon-wrapper {
  margin-bottom: 24px;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-10px);
  }
}

.empty-icon {
  width: 80px;
  height: 80px;
  color: var(--theme-accent, #0366d6);
  opacity: 0.8;
}

.empty-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 6px 0;
  color: var(--theme-foreground, #24292e);
}

.empty-description {
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 12px 0;
  color: var(--theme-secondary-foreground, #6a737d);
}

.empty-tips {
  display: flex;
  gap: 8px;
  justify-content: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.tip-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: var(--theme-panel-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 8px;
  min-width: 120px;
  transition: all 0.2s ease;
}

.tip-item:hover {
  background: var(--theme-hover-background, #f6f8fa);
  border-color: var(--theme-accent, #0366d6);
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.tip-icon {
  font-size: 24px;
}

.tip-text {
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  font-weight: 500;
}

.empty-hint {
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
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
