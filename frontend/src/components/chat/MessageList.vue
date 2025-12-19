<template>
  <div
    ref="wrapperRef"
    class="message-list-wrapper"
  >
    <div v-if="isLoading" class="loading-indicator">
      <div class="loading-spinner" />
      <span>{{ t('chat.loadingHistory') }}</span>
    </div>

    <div v-else-if="displayMessages.length === 0" class="empty-state">
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

      <!-- Streaming 状态指示器 - 在消息列表末尾 -->
      <template #after>
        <div
          v-if="isStreaming"
          class="streaming-indicator"
        >
          <span class="generating-text">Generating</span>
          <span class="bouncing-dots">
            <span class="dot">.</span>
            <span class="dot">.</span>
            <span class="dot">.</span>
          </span>
          <span class="streaming-stats">({{ streamingStats }})</span>
        </div>
      </template>
    </DynamicScroller>

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
import type { ScrollState, ScrollAnchor } from '@/composables/useSessionTab'
import { DEFAULT_SCROLL_STATE } from '@/composables/useSessionTab'
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
  contentVersion?: number  // 流式内容版本号（用于触发自动滚动）
  connectionStatus?: 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED'  // 连接状态
  hasMoreHistory?: boolean  // 顶部分页可用
}

const props = withDefaults(defineProps<Props>(), {
  isLoading: false,
  isStreaming: false,
  streamingStartTime: 0,
  inputTokens: 0,
  outputTokens: 0,
  contentVersion: 0,
  connectionStatus: 'DISCONNECTED',
  hasMoreHistory: false
})

const emit = defineEmits<{
  (e: 'load-more-history'): void
}>()

const wrapperRef = ref<HTMLElement>()
const scrollerRef = ref<InstanceType<typeof DynamicScroller>>()

// ========== 滚动状态管理（基于 ID + Offset 锚点方案） ==========

/**
 * 滚动状态（双向绑定到 sessionStore）
 */
const scrollState = computed({
  get: (): ScrollState => sessionStore.currentTab?.uiState.scrollState ?? { ...DEFAULT_SCROLL_STATE },
  set: (val: Partial<ScrollState>) => {
    if (sessionStore.currentTab) {
      sessionStore.currentTab.saveUiState({
        scrollState: { ...scrollState.value, ...val }
      })
    }
  }
})

/**
 * 是否显示"回到底部"按钮（browse 模式下显示）
 */
const showScrollToBottom = computed(() =>
  scrollState.value.mode === 'browse' && displayMessages.value.length > 0
)

/**
 * 新消息计数
 */
const newMessageCount = computed(() => scrollState.value.newMessageCount)

/**
 * 计算当前滚动锚点
 * 策略：找到视口 30% 位置的 item 作为锚点（更靠上，高度变化时更稳定）
 */
function computeScrollAnchor(): ScrollAnchor | null {
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (!el || displayMessages.value.length === 0) return null

  const clientHeight = el.clientHeight
  const targetPosition = clientHeight * 0.3  // 视口 30% 位置

  // 遍历已渲染的 item，找到覆盖目标位置的 item
  const items = el.querySelectorAll('[data-index]')
  let anchorItem: Element | null = null
  let anchorOffsetFromTop = 0
  let anchorIndex = -1

  for (const item of items) {
    const rect = item.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const itemTopRelativeToViewport = rect.top - elRect.top
    const itemBottomRelativeToViewport = rect.bottom - elRect.top

    // 找到覆盖视口 30% 位置的 item
    if (itemTopRelativeToViewport <= targetPosition &&
        itemBottomRelativeToViewport >= targetPosition) {
      anchorItem = item
      anchorOffsetFromTop = itemTopRelativeToViewport
      anchorIndex = parseInt(item.getAttribute('data-index') || '-1', 10)
      break
    }
  }

  // 回退：使用第一个可见 item
  if (!anchorItem && items.length > 0) {
    anchorItem = items[0]
    const rect = anchorItem.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    anchorOffsetFromTop = rect.top - elRect.top
    anchorIndex = parseInt(anchorItem.getAttribute('data-index') || '-1', 10)
  }

  if (anchorIndex < 0 || anchorIndex >= displayMessages.value.length) return null

  const itemId = displayMessages.value[anchorIndex].id

  return {
    itemId,
    offsetFromViewportTop: anchorOffsetFromTop,
    viewportHeight: clientHeight,
    savedAt: Date.now()
  }
}

/**
 * 恢复滚动位置
 * 策略：通过 ID 找到 item -> 估算滚动 -> 等待渲染 -> 微调
 */
async function restoreScrollPosition(anchor: ScrollAnchor): Promise<void> {
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (!el) return

  // 1. 通过 ID 找到当前 index
  const index = displayMessages.value.findIndex(item => item.id === anchor.itemId)
  if (index === -1) {
    // ID 不存在（被清理了），回退到底部
    console.log('🔄 [Scroll] Anchor item not found, scrolling to bottom')
    scrollToBottom()
    return
  }

  // 2. 估算滚动位置（假设每个 item 平均高度 100px）
  const estimatedScrollTop = index * 100 - anchor.offsetFromViewportTop
  el.scrollTop = Math.max(0, estimatedScrollTop)

  // 3. 等待 DynamicScroller 渲染
  await nextTick()
  scrollerRef.value?.forceUpdate?.()
  await nextTick()
  await new Promise(resolve => requestAnimationFrame(resolve))

  // 4. 精确定位：找到实际渲染的 item 并微调
  const renderedItem = el.querySelector(`[data-index="${index}"]`)
  if (renderedItem) {
    const rect = renderedItem.getBoundingClientRect()
    const elRect = el.getBoundingClientRect()
    const currentOffsetFromTop = rect.top - elRect.top
    const adjustment = currentOffsetFromTop - anchor.offsetFromViewportTop
    el.scrollTop += adjustment
  }

  // 5. 更新状态
  lastScrollTop.value = el.scrollTop
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  const nearBottom = distanceFromBottom < 50

  if (nearBottom) {
    // 恢复后发现在底部，切换到 follow 模式
    scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0, isNearBottom: true }
  }

  console.log(`🔄 [Scroll] Restored to item ${anchor.itemId} (index=${index})`)
}

// 防抖保存锚点
let saveAnchorTimer: number | null = null
function debouncedSaveAnchor() {
  if (saveAnchorTimer) clearTimeout(saveAnchorTimer)
  saveAnchorTimer = window.setTimeout(() => {
    if (scrollState.value.mode === 'browse' && !isTabSwitching.value) {
      const anchor = computeScrollAnchor()
      if (anchor) {
        scrollState.value = { ...scrollState.value, anchor }
      }
    }
  }, 100)
}

const lastScrollTop = ref(0)       // 上次滚动位置，用于检测滚动方向
const isTabSwitching = ref(false)  // Tab 切换中，阻止其他滚动逻辑
const isUserInteracting = ref(false)  // 用户正在交互（拖动滚动条/触摸滚动）
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
  return `esc to interrupt · ${duration} ↑${input} ↓${output}`
})

// 格式化耗时显示（保留以备后用）
const formattedElapsedTime = computed(() => formatDuration(elapsedTime.value))

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
      // 注意：流式结束时不再自动解锁，避免打断用户阅读历史消息
      // 用户需要手动滚动到底部或点击按钮才会解锁
    }
  },
  { immediate: true }
)

// 监听 streamingStartTime 变化（切换 Tab 时重启计时器）
// 解决问题：多个 Tab 同时生成时，切换 Tab 后计时器显示错误
watch(
  () => props.streamingStartTime,
  () => {
    if (props.isStreaming) {
      // 重启计时器以使用新的 startTime
      stopTimer()
      startTimer()
    }
  }
)

// 监听流式响应时的内容变化（通过 outputTokens 变化检测）
// 解决问题：消息数量不变但内容更新时，需要自动滚动
watch(
  () => props.outputTokens,
  () => {
    // 只在流式响应中、follow 模式、且用户没有在交互时才自动滚动
    // 检查 isUserInteracting 防止用户拖动滚动条时界面晃动
    if (props.isStreaming && scrollState.value.mode === 'follow' && !isUserInteracting.value) {
      scrollToBottomSilent()
    }
  }
)

// 监听流式内容版本号变化（thinking/text delta 更新时触发）
// 解决问题：思考内容换行时自动滚动
watch(
  () => props.contentVersion,
  () => {
    // 只在流式响应中、follow 模式、且用户没有在交互时才自动滚动
    // 检查 isUserInteracting 防止用户拖动滚动条时界面晃动
    if (props.isStreaming && scrollState.value.mode === 'follow' && !isUserInteracting.value) {
      scrollToBottomSilent()
    }
  }
)

// 监听用户滚轮事件 - 向上滚动切换到 browse 模式
function handleWheel(e: WheelEvent) {
  console.log(`🖱️ [Wheel] deltaY=${e.deltaY}, mode=${scrollState.value.mode}, streaming=${props.isStreaming}`)
  // deltaY < 0 表示向上滚动
  if (e.deltaY < 0 && scrollState.value.mode === 'follow') {
    // 切换到 browse 模式，保存当前锚点
    const anchor = computeScrollAnchor()
    scrollState.value = {
      mode: 'browse',
      anchor,
      newMessageCount: 0,
      isNearBottom: false
    }
    console.log('🔄 [Scroll] Switched to browse mode (wheel up)')
  }
}

// 用户交互检测（滚动条拖动/触摸滚动）
function handlePointerDown(e: PointerEvent) {
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (!el) return

  // 检测是否点击在滚动条区域（元素右侧约 20px）
  const rect = el.getBoundingClientRect()
  const isOnScrollbar = e.clientX > rect.right - 20

  if (isOnScrollbar) {
    isUserInteracting.value = true
    console.log('🔄 [Scroll] User started dragging scrollbar')
  }
}

function handlePointerUp() {
  if (isUserInteracting.value) {
    isUserInteracting.value = false
    console.log('🔄 [Scroll] User stopped dragging scrollbar')
  }
}

function handleTouchStart() {
  isUserInteracting.value = true
}

function handleTouchEnd() {
  // 延迟重置，因为惯性滚动可能还在继续
  setTimeout(() => {
    isUserInteracting.value = false
  }, 150)
}

// 添加用户交互事件监听器（需要在 DynamicScroller 渲染后调用）
let scrollListenersAdded = false
function addScrollListeners() {
  if (scrollListenersAdded) return
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (el) {
    el.addEventListener('wheel', handleWheel, { passive: true })
    el.addEventListener('pointerdown', handlePointerDown, { passive: true })
    el.addEventListener('touchstart', handleTouchStart, { passive: true })
    // 全局监听 pointerup 和 touchend，因为用户可能在元素外释放
    window.addEventListener('pointerup', handlePointerUp)
    window.addEventListener('touchend', handleTouchEnd)
    scrollListenersAdded = true
    console.log('🔄 [Scroll] User interaction listeners added')
  }
}

onMounted(() => {
  if (props.isStreaming) {
    startTimer()
  }
  // 延迟添加事件监听，确保 scrollerRef 已挂载
  nextTick(() => {
    addScrollListeners()
  })
})

onUnmounted(() => {
  stopTimer()
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  if (el) {
    el.removeEventListener('wheel', handleWheel)
    el.removeEventListener('pointerdown', handlePointerDown)
    el.removeEventListener('touchstart', handleTouchStart)
  }
  window.removeEventListener('pointerup', handlePointerUp)
  window.removeEventListener('touchend', handleTouchEnd)
})

// 监听 tab 切换，保存旧 tab 滚动位置并恢复新 tab 位置
watch(
  () => sessionStore.currentTabId,
  async (newTabId, oldTabId) => {
    if (!newTabId || newTabId === oldTabId) return

    // ✅ 切换前：保存旧 tab 的滚动位置（在 DOM 更新之前同步执行）
    if (oldTabId) {
      const oldTab = sessionStore.tabs.find(t => t.tabId === oldTabId)
      if (oldTab) {
        const oldScrollState = oldTab.uiState.scrollState
        // 如果旧 tab 是 browse 模式，立即计算并保存锚点
        if (oldScrollState.mode === 'browse') {
          const anchor = computeScrollAnchor()
          if (anchor) {
            oldTab.saveUiState({
              scrollState: { ...oldScrollState, anchor }
            })
            console.log(`💾 [Scroll] Saved anchor for old tab ${oldTabId}: item=${anchor.itemId}`)
          }
        }
        // 如果是 follow 模式，无需保存（切换回来时自动滚到底部）
      }
    }

    // 标记 tab 切换中，阻止其他滚动逻辑
    isTabSwitching.value = true

    // 获取新 tab 的滚动状态
    const savedScrollState = sessionStore.currentTab?.uiState.scrollState

    // 等待 Vue 渲染 + 浏览器重绘
    await nextTick()
    await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))

    if (savedScrollState?.mode === 'browse' && savedScrollState.anchor) {
      // browse 模式：恢复锚点位置
      await restoreScrollPosition(savedScrollState.anchor)
    } else {
      // follow 模式或无锚点：滚动到底部
      // 使用可靠的滚动方法，因为虚拟列表可能还没完全渲染
      await scrollToBottomReliably()
      const el = scrollerRef.value?.$el as HTMLElement | undefined
      if (el) {
        lastScrollTop.value = el.scrollTop
      }
      // 确保状态为 follow
      if (sessionStore.currentTab) {
        scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0, isNearBottom: true }
      }
    }

    await nextTick()
    isTabSwitching.value = false
    console.log(`🔄 [Scroll] Tab switched to ${newTabId}, mode=${savedScrollState?.mode ?? 'follow'}`)
  }
)

// 为虚拟列表准备数据源
// 优先使用 displayItems，如果没有则使用 messages（向后兼容）
const displayMessages = computed(() => props.displayItems || props.messages || [])

// 使用新的 DisplayItemRenderer 还是旧的 MessageDisplay
const messageComponent = computed(() => props.displayItems ? DisplayItemRenderer : MessageDisplay)

// 监听 displayMessages 变化，当从空变为有内容时添加事件监听器
watch(
  () => displayMessages.value.length,
  (newLen, oldLen) => {
    if (newLen > 0 && oldLen === 0) {
      // 消息从无到有，需要等待 DynamicScroller 渲染后添加事件监听器
      nextTick(() => {
        addScrollListeners()
      })
    }
  }
)

// 监听消息变化 - 基于双模式的滚动处理
watch(() => displayMessages.value.length, async (newCount, oldCount) => {
  // Tab 切换中，不处理消息变化
  if (isTabSwitching.value) {
    return
  }

  const added = newCount - oldCount

  // 首次批量加载：跳到底部
  if (oldCount === 0 && newCount > 0) {
    await nextTick()
    scrollToBottom()
    forceUpdateScroller()
    return
  }

  // 历史分页加载中：不滚动，由 isLoading watch 处理
  if (historyLoadInProgress.value) {
    await nextTick()
    forceUpdateScroller()
    return
  }

  // 新消息到达
  if (added > 0) {
    if (scrollState.value.mode === 'browse') {
      // browse 模式：累计新消息计数，保持位置
      const currentCount = scrollState.value.newMessageCount
      scrollState.value = { ...scrollState.value, newMessageCount: currentCount + added }
      // 保存当前滚动位置
      const el = scrollerRef.value?.$el as HTMLElement | undefined
      const savedScrollTop = el?.scrollTop ?? 0
      await nextTick()
      forceUpdateScroller()
      // 恢复滚动位置
      await nextTick()
      if (el) el.scrollTop = savedScrollTop
    } else {
      // follow 模式：自动滚动到底部
      await nextTick()
      scrollToBottomSilent()
      forceUpdateScroller()
    }
  } else {
    // 消息数量没有增加（可能是更新），正常更新 scroller
    await nextTick()
    forceUpdateScroller()
  }
})


// 强制 DynamicScroller 重新计算所有项目尺寸
function forceUpdateScroller() {
  if (scrollerRef.value) {
    scrollerRef.value.forceUpdate?.()
  }
}

watch(() => props.isLoading, async (newValue, oldValue) => {
  // 加载开始时，如果是 follow 模式则保持在底部
  if (newValue && scrollState.value.mode === 'follow') {
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

      // 确保是 follow 模式
      scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0, isNearBottom: true }
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
  const distanceFromBottom = scrollHeight - scrollTop - clientHeight

  // 调试日志：每次滚动都打印关键信息
  const debugScrollingUp = scrollTop < lastScrollTop.value
  console.log(`📜 [Scroll] top=${scrollTop.toFixed(0)}, last=${lastScrollTop.value.toFixed(0)}, bottom=${distanceFromBottom.toFixed(0)}, up=${debugScrollingUp}, mode=${scrollState.value.mode}, streaming=${props.isStreaming}, interact=${isUserInteracting.value}`)

  // 顶部分页 - 触发加载更多历史
  const shouldTrigger = scrollTop < HISTORY_TRIGGER_THRESHOLD &&
    props.hasMoreHistory &&
    !props.isLoading &&
    !historyLoadInProgress.value &&
    !historyLoadRequested.value

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

  // 判断是否在底部（允许 50px 的误差）
  const nearBottom = distanceFromBottom < 50
  // 判断滚动方向（必须在更新 lastScrollTop 之前计算！）
  const isScrollingUp = scrollTop < lastScrollTop.value

  // 更新 lastScrollTop
  lastScrollTop.value = scrollTop

  // 到达底部时自动切换回 follow 模式
  if (nearBottom && scrollState.value.mode === 'browse') {
    scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0, isNearBottom: true }
    console.log('🔄 [Scroll] Switched to follow mode (reached bottom)')
  } else if (!nearBottom && scrollState.value.mode === 'follow') {
    // 离开底部且当前是 follow 模式
    // 判断是否应该切换到 browse 模式：
    // 1. 用户正在交互（拖动滚动条/触摸滚动）→ 切换
    // 2. 向上滚动（scrollTop 变小）→ 切换（兜底 wheel 事件在某些环境下不触发）
    // 3. 非 streaming 状态 → 切换（兜底，处理其他边缘情况）
    // 4. streaming 期间的程序性滚动（向下且非用户交互）→ 不切换
    if (isUserInteracting.value || isScrollingUp || !props.isStreaming) {
      const anchor = computeScrollAnchor()
      scrollState.value = {
        mode: 'browse',
        anchor,
        newMessageCount: 0,
        isNearBottom: false
      }
      const reason = isUserInteracting.value ? 'user dragging' : isScrollingUp ? 'scroll up' : 'left bottom'
      console.log(`🔄 [Scroll] Switched to browse mode (${reason})`)
    }
  } else if (!nearBottom && scrollState.value.mode === 'browse') {
    // browse 模式下，防抖保存锚点
    debouncedSaveAnchor()
  }
}

/**
 * 程序调用的滚动到底部（follow 模式下使用）
 */
function scrollToBottomSilent() {
  if (scrollerRef.value) {
    scrollerRef.value.scrollToBottom()
  } else if (wrapperRef.value) {
    wrapperRef.value.scrollTop = wrapperRef.value.scrollHeight
  }
}

/**
 * 用户主动点击"回到底部"按钮（切换到 follow 模式）
 */
function scrollToBottom() {
  scrollToBottomSilent()
  // 用户主动操作，切换到 follow 模式
  scrollState.value = { mode: 'follow', anchor: null, newMessageCount: 0, isNearBottom: true }
  console.log('🔄 [Scroll] User clicked scroll to bottom')
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
  position: relative; /* 为 scroll-to-bottom 按钮提供定位上下文 */
}

.message-list {
  flex: 1;
  min-height: 0; /* 关键：防止 flex 子元素溢出 */
  overflow-y: auto !important;
  overflow-x: hidden;
  padding: 4px 6px 4px 6px; /* 减少底部留白 */
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
  font-family: var(--theme-editor-font-family);
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  color: var(--theme-foreground, #24292e);
  font-weight: 600;
}

/* Streaming 状态指示器 - 左对齐，宽度自适应 */
.streaming-indicator {
  display: inline-flex;
  align-items: center;
  gap: 0;
  padding: 4px 10px;
  margin: 8px 0 4px -6px;
  font-size: 12px;
  font-family: var(--theme-editor-font-family);
  color: var(--theme-secondary-foreground);
}

.generating-text {
  color: #D97706;
  font-weight: 500;
}

.bouncing-dots {
  display: inline-flex;
  margin-right: 4px;
}

.bouncing-dots .dot {
  color: #D97706;
  font-weight: bold;
  animation: bounce 1.4s ease-in-out infinite;
}

.bouncing-dots .dot:nth-child(1) {
  animation-delay: 0s;
}

.bouncing-dots .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.bouncing-dots .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 1;
  }
  30% {
    transform: translateY(-3px);
    opacity: 0.6;
  }
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
  background: var(--theme-panel-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
  border: 1px solid var(--theme-border, #e1e4e8);
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
  background: var(--theme-hover-background, #f0f0f0);
  border-color: var(--theme-accent, #0366d6);
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
