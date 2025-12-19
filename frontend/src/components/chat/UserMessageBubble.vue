<template>
  <!-- 统一的用户消息容器 -->
  <div
    class="user-message-container"
    :class="{
      'is-replay': props.message.isReplay,
      'is-editing': isEditing,
      [props.message.style || '']: props.message.isReplay && props.message.style
    }"
    @mouseenter="showEditButton = true"
    @mouseleave="showEditButton = false"
  >
    <!-- 编辑模式：内嵌 ChatInput -->
    <div v-if="isEditing" ref="editorContainerRef" class="inline-editor-container">
      <ChatInput
        ref="chatInputRef"
        :inline="true"
        :contexts="editContexts"
        :show-context-controls="true"
        :show-model-selector="false"
        :show-permission-controls="false"
        @send="handleEditSubmit"
        @cancel="exitEditMode"
        @context-add="handleContextAdd"
        @context-remove="handleContextRemove"
      />
    </div>

    <!-- 显示模式 -->
    <template v-else>
      <!-- 非回放消息的左侧占位 -->
      <div v-if="!props.message.isReplay" class="row-spacer"></div>

      <div class="message-wrapper">
        <!-- 编辑按钮（hover 时显示） -->
        <button
          v-if="!props.message.isReplay"
          v-show="showEditButton && props.message.uuid && !props.message.isStreaming"
          class="edit-button"
          title="Edit and resend message"
          @click="enterEditMode"
        >
          <span class="edit-icon">✏️</span>
        </button>

        <!-- 消息内容区域 -->
        <div
          class="message-content"
          :class="{
            'bubble-style': !props.message.isReplay
          }"
        >
          <!-- 头部：上下文标签 + 操作按钮 -->
          <div class="content-header">
            <!-- 上下文标签区域（当前打开的文件 + 文件/图片上下文按添加顺序混合显示） -->
            <div v-if="hasCurrentOpenFile || allContextRefs.length > 0" class="context-tags">
              <!-- 当前打开的文件（始终在最前面） -->
              <span
                v-if="hasCurrentOpenFile"
                class="file-tag"
                :title="currentOpenFileFullPath"
                @click.stop="handleOpenFileClick"
              >
                <span class="tag-file-name">{{ currentOpenFileName }}</span>
                <span v-if="currentOpenFileLineRange" class="tag-line-range">{{ currentOpenFileLineRange }}</span>
              </span>
              <!-- 文件和图片上下文按原始顺序显示 -->
              <template v-for="(ctx, index) in allContextRefs" :key="`ctx-${index}`">
                <!-- 文件引用 -->
                <span
                  v-if="ctx.type === 'file'"
                  class="file-tag file-ref"
                  :title="ctx.fullPath || ctx.uri"
                  @click.stop="handleFileRefClick(ctx)"
                >
                  <span class="tag-prefix">@</span>
                  <span class="tag-file-name">{{ getFileRefName(ctx) }}</span>
                </span>
                <!-- 图片 -->
                <img
                  v-else-if="ctx.type === 'image' && ctx.base64Data"
                  :src="getContextImageSrc(ctx)"
                  :alt="`Context image ${index + 1}`"
                  class="context-thumb-inline"
                  @click.stop="openContextImagePreview(ctx)"
                />
              </template>
            </div>
            <div v-else class="header-spacer"></div>

            <!-- 操作按钮（右上角） -->
            <div class="action-buttons">
              <button class="action-btn" :title="t('common.copy')" @click.stop="handleCopy">
                <span v-if="copied">✓</span>
                <span v-else>📋</span>
              </button>
              <button v-if="isOverflowing" class="action-btn" @click.stop="toggleCollapse">
                {{ isCollapsed ? t('common.expand') + ' ▾' : t('common.collapse') + ' ▴' }}
              </button>
            </div>
          </div>

          <!-- 消息内容（根据折叠状态限制高度） -->
          <div
            ref="contentRef"
            class="message-body"
            :class="{ collapsed: isCollapsed && isOverflowing }"
          >
            <!-- 文本内容 -->
            <div
              v-if="messageText"
              class="message-text"
              :class="{ 'use-markdown': props.message.isReplay }"
            >
              <MarkdownRenderer v-if="props.message.isReplay" :content="messageText" />
              <span v-else v-html="renderedText" @click="handleMessageClick"></span>
            </div>

            <!-- 内嵌图片 -->
            <div v-if="imageBlocks.length > 0" class="inline-images">
              <img
                v-for="(image, index) in imageBlocks"
                :key="`img-${index}`"
                :src="getImageSrc(image)"
                :alt="`Image ${index + 1}`"
                class="inline-thumb"
                @click.stop="openImagePreview(image)"
              />
            </div>
          </div>
        </div>

        <!-- 上下文大小指示器（非回放消息） -->
        <ContextUsageIndicator
          v-if="!props.message.isReplay && contextTokens > 0"
          :session-token-usage="{ inputTokens: contextTokens, outputTokens: 0 }"
        />
      </div>
    </template>
  </div>

  <!-- 图片预览模态框 -->
  <ImagePreviewModal
    :visible="previewVisible"
    :image-src="previewImageSrc"
    :image-alt="previewImageAlt"
    @close="closeImagePreview"
  />
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { onClickOutside } from '@vueuse/core'
import { useI18n } from '@/composables/useI18n'
import type { ImageBlock, ContentBlock } from '@/types/message'
import type { ContextReference } from '@/types/display'
import type { ParsedCurrentOpenFile, ParsedOpenFileReminder, ParsedSelectLinesReminder } from '@/utils/xmlTagParser'
import { hasCurrentOpenFileTag, parseCurrentOpenFileTag, removeCurrentOpenFileTag, isSystemReminderTag } from '@/utils/xmlTagParser'
import { isFileReference } from '@/utils/userMessageBuilder'
import { linkifyText, getLinkFromEvent, handleLinkClick } from '@/utils/linkify'
import ImagePreviewModal from '@/components/common/ImagePreviewModal.vue'
import ChatInput from './ChatInput.vue'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import { ideaBridge } from '@/services/ideaBridge'
import { useSessionStore } from '@/stores/sessionStore'

// 兼容 Message 和 UserMessage (DisplayItem) 类型
interface Props {
  message: {
    id?: string
    /** JSONL 历史文件中的 UUID，用于编辑重发时定位截断位置 */
    uuid?: string
    content?: ContentBlock[]
    contexts?: ContextReference[]  // DisplayItem 中的 contexts 已经包含图片
    style?: 'hint' | 'error'  // 消息样式：hint=md渲染，error=错误颜色
    isReplay?: boolean  // 是否是回放消息：true=左对齐，false/undefined=右对齐
    isStreaming?: boolean  // 是否正在流式响应（此时不显示编辑按钮）
    [key: string]: unknown
  }
}

const props = defineProps<Props>()

// i18n
const { t } = useI18n()

// 内容容器引用
const contentRef = ref<HTMLDivElement>()

// 折叠状态
const isCollapsed = ref(true)

// 是否内容溢出（需要折叠）
const isOverflowing = ref(false)

// 检测内容是否溢出
function checkOverflow() {
  nextTick(() => {
    if (contentRef.value) {
      const el = contentRef.value
      // 临时移除折叠样式来测量实际高度
      const originalMaxHeight = el.style.maxHeight
      el.style.maxHeight = 'none'
      const fullHeight = el.scrollHeight
      el.style.maxHeight = originalMaxHeight
      // 折叠高度约为 3 行（line-height 1.4 * font-size 13px * 3 ≈ 55px）
      const maxCollapsedHeight = 55
      isOverflowing.value = fullHeight > maxCollapsedHeight
    }
  })
}

// 组件挂载时检测溢出
onMounted(checkOverflow)

// 监听消息内容变化
watch(() => props.message.content, checkOverflow, { deep: true })

// 复制状态
const copied = ref(false)

// 复制消息内容
async function handleCopy() {
  try {
    await navigator.clipboard.writeText(messageText.value)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (e) {
    console.error('Failed to copy:', e)
  }
}

// 编辑模式状态
const isEditing = ref(false)
const showEditButton = ref(false)
const chatInputRef = ref<InstanceType<typeof ChatInput>>()
const editorContainerRef = ref<HTMLDivElement>()

// 编辑时的上下文（从原始消息复制，可在编辑时修改）
const editContexts = ref<ContextReference[]>([])

// 点击外部退出编辑模式
onClickOutside(editorContainerRef, () => {
  if (isEditing.value) {
    exitEditMode()
  }
})

// 进入编辑模式
function enterEditMode() {
  isEditing.value = true

  // 复制原始上下文到编辑上下文
  editContexts.value = props.message.contexts ? [...props.message.contexts] : []

  // 恢复内容到输入框
  nextTick(() => {
    if (chatInputRef.value && props.message.content) {
      chatInputRef.value.setContent(props.message.content)
    }
  })
}

// 退出编辑模式
function exitEditMode() {
  isEditing.value = false
  editContexts.value = []
}

// 处理编辑提交
async function handleEditSubmit(contents: ContentBlock[]) {
  const uuid = props.message.uuid
  if (!uuid) {
    console.warn('[UserMessageBubble] 消息没有 uuid，无法编辑重发')
    exitEditMode()
    return
  }

  const sessionStore = useSessionStore()
  const currentTab = sessionStore.currentTab
  if (!currentTab) {
    console.warn('[UserMessageBubble] 没有活动的 tab')
    exitEditMode()
    return
  }

  // 从 currentTab 获取项目路径
  const projectPath = currentTab.projectPath.value
  console.log('[UserMessageBubble] 项目路径:', projectPath)

  if (!projectPath) {
    console.warn('[UserMessageBubble] 项目路径为空，无法编辑重发')
    exitEditMode()
    return
  }

  // 保存编辑上下文的副本（因为 exitEditMode 会清空它）
  const contextsToSend = [...editContexts.value]

  // 先退出编辑模式，让用户立即看到反馈
  exitEditMode()

  // 异步调用编辑重发方法
  try {
    await currentTab.editAndResendMessage(
      uuid,
      {
        contexts: contextsToSend,
        contents
      },
      projectPath
    )
  } catch (e) {
    console.error('[UserMessageBubble] 编辑重发失败:', e)
  }
}

// 处理上下文添加
function handleContextAdd(context: ContextReference) {
  editContexts.value.push(context)
}

// 处理上下文移除
function handleContextRemove(context: ContextReference) {
  const index = editContexts.value.findIndex(c => c.uri === context.uri)
  if (index !== -1) {
    editContexts.value.splice(index, 1)
  }
}

// 所有上下文引用（保持原始顺序，包含文件和图片）
const allContextRefs = computed(() => {
  const ctxs = props.message.contexts
  if (!ctxs || !Array.isArray(ctxs)) return []
  // 过滤出文件和图片类型，保持原始顺序
  return ctxs.filter(ctx => ctx.type === 'file' || (ctx.type === 'image' && ctx.base64Data))
})

// 获取文件引用的显示名称
function getFileRefName(fileRef: ContextReference): string {
  const path = fileRef.fullPath || fileRef.path || fileRef.uri?.replace(/^file:\/\//, '') || ''
  return getFileName(path)
}

// 点击文件引用打开文件
function handleFileRefClick(fileRef: ContextReference) {
  const filePath = fileRef.fullPath || fileRef.path || fileRef.uri?.replace(/^file:\/\//, '')
  if (filePath) {
    ideaBridge.query('ide.openFile', { filePath })
  }
}

// 获取上下文图片的 src（针对 ContextReference 类型）
function getContextImageSrc(ctx: ContextReference): string {
  if (ctx.type === 'image' && ctx.base64Data) {
    const mimeType = ctx.mimeType || 'image/png'
    return `data:${mimeType};base64,${ctx.base64Data}`
  }
  return ''
}

// 打开上下文图片预览（针对 ContextReference 类型）
function openContextImagePreview(ctx: ContextReference) {
  const src = getContextImageSrc(ctx)
  if (src) {
    previewImageSrc.value = src
    previewImageAlt.value = ctx.name || 'Context image'
    previewVisible.value = true
  }
}


// 提取用户输入的文本内容（排除文件引用、system-reminder 标签）
const messageText = computed(() => {
  const content = props.message.content
  if (!content || !Array.isArray(content)) {
    return ''
  }

  // 从用户输入内容块中提取文本（排除文件引用和系统提醒标签）
  return content
    .filter(block => {
      if (block.type === 'text' && 'text' in block) {
        const text = (block as any).text?.trim() || ''
        // 排除文件引用格式的文本
        if (isFileReference(text)) return false
        // 排除 system-reminder 标签（新格式）
        if (isSystemReminderTag(text)) return false
        // 排除 diff 内容标签
        if (text.startsWith('<diff-old-content>') || text.startsWith('<diff-new-content>')) return false
        return true
      }
      return false
    })
    .map(block => {
      if (block.type === 'text' && 'text' in block) {
        let text = (block as any).text
        // 移除 current-open-file 标签（旧格式，它会在文件标记区域单独显示）
        if (hasCurrentOpenFileTag(text)) {
          text = removeCurrentOpenFileTag(text)
        }
        return text
      }
      return ''
    })
    .join('\n')
})

// 从消息文本中解析 currentOpenFile（用于非 replay 消息）
const parsedCurrentOpenFile = computed((): ParsedCurrentOpenFile | undefined => {
  const content = props.message.content
  if (!content || !Array.isArray(content)) {
    return undefined
  }

  for (const block of content) {
    if (block.type === 'text' && 'text' in block) {
      const text = (block as any).text || ''
      if (hasCurrentOpenFileTag(text)) {
        return parseCurrentOpenFileTag(text) || undefined
      }
    }
  }
  return undefined
})

// 渲染后的文本（带链接）
const renderedText = computed(() => {
  if (!messageText.value) return ''
  const result = linkifyText(messageText.value)
  return result.html
})

// 预览文本（折叠时显示，截取前 100 个字符）
const previewText = computed(() => {
  const text = messageText.value
  if (!text) return ''
  if (text.length > 100) {
    return text.substring(0, 100) + '...'
  }
  return text
})

// 获取当前打开文件标记（支持新旧两种格式）
const currentOpenFile = computed((): ParsedCurrentOpenFile | undefined => {
  // 1. 优先使用新格式的 openFile（从 parseUserMessage 解析的）
  if (props.message.openFile) {
    const openFile = props.message.openFile as ParsedOpenFileReminder
    // 转换为 ParsedCurrentOpenFile 格式
    const result: ParsedCurrentOpenFile = {
      path: openFile.path
    }
    // 如果有 selectedLines，合并选区信息
    if (props.message.selectedLines) {
      const sel = props.message.selectedLines as ParsedSelectLinesReminder
      result.startLine = sel.start
      result.endLine = sel.end
      result.startColumn = sel.startColumn
      result.endColumn = sel.endColumn
      result.selectedContent = sel.content
    }
    return result
  }

  // 2. 兼容旧格式：从 props 中的 currentOpenFile（历史消息中解析的）
  if (props.message.currentOpenFile) {
    return props.message.currentOpenFile as ParsedCurrentOpenFile
  }

  // 3. 从消息文本中解析（旧格式）
  return parsedCurrentOpenFile.value
})

// 是否显示当前打开文件标记（replay 和非 replay 消息都支持）
const hasCurrentOpenFile = computed(() => {
  return !!currentOpenFile.value
})

// 从路径中提取文件名
function getFileName(filePath: string): string {
  const parts = filePath.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || filePath
}

// 当前打开文件的文件名（可能被截断）
const currentOpenFileName = computed(() => {
  const file = currentOpenFile.value
  if (!file) return ''
  return getFileName(file.path)
})

// 当前打开文件的行号范围（包含列信息）
const currentOpenFileLineRange = computed(() => {
  const file = currentOpenFile.value
  if (!file) return ''
  if (file.startLine && file.endLine) {
    // 选区：显示起始行:列-结束行:列
    const startCol = file.startColumn || 1
    const endCol = file.endColumn || 1
    return `:${file.startLine}:${startCol}-${file.endLine}:${endCol}`
  } else if (file.line) {
    // 光标：显示行:列
    const col = file.column || 1
    return `:${file.line}:${col}`
  }
  return ''
})

// 当前打开文件的显示文本（只显示文件名，悬停显示全路径）- 保留以兼容
const currentOpenFileDisplayText = computed(() => {
  const file = currentOpenFile.value
  if (!file) return ''
  const fileName = getFileName(file.path)
  if (file.startLine && file.endLine) {
    // 有选区
    return `${fileName}:${file.startLine}-${file.endLine}`
  } else if (file.line) {
    // 有光标位置
    return `${fileName}:${file.line}`
  }
  return fileName
})

// 当前打开文件的完整路径提示（用于 title 属性，包含选中内容）
const currentOpenFileFullPath = computed(() => {
  const file = currentOpenFile.value
  if (!file) return ''
  let pathInfo = ''
  if (file.startLine && file.endLine) {
    pathInfo = `${file.path}:${file.startLine}-${file.endLine}`
  } else if (file.line) {
    pathInfo = `${file.path}:${file.line}`
  } else {
    pathInfo = file.path
  }
  // 如果有选中内容，添加到提示中
  if (file.selectedContent) {
    return `${pathInfo}\n\n选中内容:\n${file.selectedContent}`
  }
  return pathInfo
})

// 点击文件标记打开文件
function handleOpenFileClick() {
  const file = currentOpenFile.value
  if (!file) return
  ideaBridge.query('ide.openFile', {
    filePath: file.path,
    line: file.startLine || file.line || 1,
    column: file.startColumn || file.column || 1
  })
}

// 处理内容区域点击（统一的展开/折叠）
function handleContentClick() {
  if (isLongMessage.value) {
    toggleCollapse()
  }
}

// 处理消息文本中的链接点击
function handleMessageClick(event: MouseEvent) {
  // 阻止冒泡到 bubble-content，避免重复触发 toggleCollapse
  event.stopPropagation()

  const linkInfo = getLinkFromEvent(event)
  if (linkInfo) {
    // 点击的是链接，打开链接
    event.preventDefault()

    // 打开链接
    handleLinkClick(linkInfo.href, linkInfo.type, (filePath) => {
      // 文件路径：调用 IDE 打开文件
      ideaBridge.query('ide.openFile', { filePath })
    })
  } else if (isLongMessage.value) {
    // 点击的不是链接，如果是长消息则切换展开状态
    toggleCollapse()
  }
}

// 提取用户输入的图片内容（内嵌图片，在 content 中的图片）
const imageBlocks = computed(() => {
  const content = props.message.content
  if (!content || !Array.isArray(content)) {
    return []
  }

  // 返回 content 中的图片块
  return content.filter(block => block.type === 'image') as ImageBlock[]
})

// 判断是否为长消息（检查原始 content 的文本长度）
const isLongMessage = computed(() => {
  const content = props.message.content
  if (!content || !Array.isArray(content)) return false

  // 计算所有文本块的总长度和总行数
  let totalLength = 0
  let totalLines = 0
  for (const block of content) {
    if (block.type === 'text' && 'text' in block) {
      const text = (block as any).text || ''
      totalLength += text.length
      totalLines += text.split('\n').length
    }
  }

  return totalLines > 3 || totalLength > 150
})

// 发送时的上下文大小（从前一条 AI 回复的 stats 中获取）
const contextTokens = computed(() => {
  const sessionStore = useSessionStore()
  const currentTab = sessionStore.currentTab
  if (!currentTab) return 0

  // displayItems 是 reactive 数组，不需要 .value
  const displayItems = currentTab.displayItems
  const currentIndex = displayItems.findIndex(item => item.id === props.message.id)
  if (currentIndex <= 0) return 0

  // 向前查找最近的带有 stats.inputTokens 的 AssistantText
  for (let i = currentIndex - 1; i >= 0; i--) {
    const item = displayItems[i] as any
    if (item.displayType === 'assistantText' && item.stats?.inputTokens) {
      return item.stats.inputTokens
    }
  }
  return 0
})

// 切换折叠状态
function toggleCollapse() {
  isCollapsed.value = !isCollapsed.value
}

// 获取图片源地址
function getImageSrc(image: ImageBlock): string {
  if (image.source.type === 'url' && image.source.url) {
    return image.source.url
  }
  if (image.source.type === 'base64' && image.source.data) {
    return `data:${image.source.media_type};base64,${image.source.data}`
  }
  return ''
}

// 获取图片名称
function getImageName(image: ImageBlock, index: number): string {
  if (image.source.type === 'url' && image.source.url) {
    const urlParts = image.source.url.split('/')
    return urlParts[urlParts.length - 1] || `image-${index + 1}`
  }
  const ext = image.source.media_type.split('/')[1] || 'png'
  return `image.${ext}`
}


// 图片预览状态
const previewVisible = ref(false)
const previewImageSrc = ref('')
const previewImageAlt = ref('')

// 打开图片预览
function openImagePreview(image: ImageBlock) {
  const src = getImageSrc(image)
  if (src) {
    previewImageSrc.value = src
    previewImageAlt.value = getImageName(image, 0)
    previewVisible.value = true
  }
}

// 关闭图片预览
function closeImagePreview() {
  previewVisible.value = false
}
</script>

<style scoped>
/* ===== 统一的用户消息容器 ===== */
.user-message-container {
  display: flex;
  align-items: flex-start;
  padding: 4px 12px;
  width: 100%;
  box-sizing: border-box;
}

/* 回放消息：左对齐 */
.user-message-container.is-replay {
  justify-content: flex-start;
}

/* 非回放消息：右对齐 */
.user-message-container:not(.is-replay) {
  justify-content: flex-end;
}

/* 左侧占位元素 */
.row-spacer {
  flex: 1;
  min-width: 0;
}

/* 消息包装器 */
.message-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  max-width: 95%;
}

/* 回放消息的 wrapper 样式 */
.user-message-container.is-replay .message-wrapper {
  flex-direction: column;
  align-items: flex-start;
}

/* ===== 编辑按钮 ===== */
.edit-button {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.2s;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
}

.user-message-container:hover .edit-button {
  opacity: 1;
}

.edit-button:hover {
  background: var(--theme-hover-background);
}

.edit-icon {
  font-size: 14px;
}

/* ===== 内嵌编辑器 ===== */
.inline-editor-container {
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
}

.user-message-container.is-editing {
  justify-content: stretch;
}

/* ===== 消息内容区域 ===== */
.message-content {
  max-width: 100%;
  padding: 10px 14px;
  border-radius: 8px;
  transition: all 0.2s ease;
}

/* 气泡样式（非回放消息） */
.message-content.bubble-style {
  background: var(--theme-selection-background);
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
}

/* 回放消息的内容样式 */
.user-message-container.is-replay .message-content {
  background: transparent;
  padding: 4px 8px;
}

/* ===== 消息主体 ===== */
.message-body {
  overflow: hidden;
  transition: max-height 0.2s ease;
}

.message-body.collapsed {
  /* 约 3 行高度：line-height 1.4 * font-size 13px * 3 行 ≈ 55px */
  max-height: calc(1.4em * 3);
  position: relative;
}

/* 折叠时的渐变遮罩 */
.message-body.collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1.5em;
  background: linear-gradient(transparent, var(--theme-background));
  pointer-events: none;
}

.message-content.bubble-style .message-body.collapsed::after {
  background: linear-gradient(transparent, var(--theme-selection-background));
}

/* 可点击展开 */
.message-content.clickable {
  cursor: pointer;
}

.message-content.clickable:hover {
  background: var(--theme-hover-background);
}

.message-content.bubble-style.clickable:hover {
  background: color-mix(in srgb, var(--theme-selection-background) 90%, white 10%);
}

/* ===== 预览文本（折叠时） ===== */
.preview-text {
  font-size: 13px;
  line-height: 1.4;
  color: var(--theme-foreground);
  opacity: 0.8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  max-width: 100%;
}

.message-content.bubble-style .preview-text {
  color: var(--theme-selection-foreground);
}

/* ===== 消息文本 ===== */
.message-text {
  font-size: 13px;
  line-height: 1.4;
  white-space: pre-wrap;
  word-break: break-word;
  user-select: text;
  -webkit-user-select: text;
}

.message-content.bubble-style .message-text {
  color: var(--theme-selection-foreground);
}

/* markdown 渲染时的样式 */
.message-text.use-markdown {
  white-space: normal;
}

/* 文本选中样式 */
.message-content ::selection {
  background: var(--theme-accent, #0366d6);
  color: #ffffff;
}

/* 链接样式 */
.message-text :deep(.linkified-link) {
  color: var(--theme-link);
  text-decoration: none;
  cursor: pointer;
}

.message-text :deep(.linkified-link:hover) {
  text-decoration: underline;
}

.message-text :deep(.file-link) {
  background: var(--theme-hover-background);
  padding: 1px 6px;
  border-radius: 3px;
}

/* ===== 头部区域 ===== */
.content-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.header-spacer {
  flex: 1;
}

/* ===== 操作按钮 ===== */
.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.action-btn {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.action-btn:hover {
  background: var(--theme-hover-background);
  border-color: var(--theme-foreground);
}

.message-content.bubble-style .action-btn {
  border-color: rgba(255, 255, 255, 0.3);
  color: var(--theme-selection-foreground);
}

.message-content.bubble-style .action-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.5);
}

/* ===== 上下文标签 ===== */
.context-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.file-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 6px;
  background: rgba(3, 102, 214, 0.08);
  border: 1px solid var(--theme-accent, #0366d6);
  border-radius: 3px;
  font-size: 11px;
  cursor: pointer;
  transition: background 0.2s;
}

.file-tag:hover {
  background: rgba(3, 102, 214, 0.15);
}

.file-tag .tag-file-name {
  color: var(--theme-accent, #0366d6);
  font-weight: 500;
  font-family: var(--editor-font-family, monospace);
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-tag .tag-line-range {
  color: var(--theme-accent, #0366d6);
  font-weight: 600;
  font-family: var(--editor-font-family, monospace);
  flex-shrink: 0;
  white-space: nowrap;
}

.file-tag .tag-prefix {
  font-weight: 600;
  opacity: 0.7;
}

/* 气泡内的文件标签样式 */
.message-content.bubble-style .file-tag {
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.message-content.bubble-style .file-tag:hover {
  background: rgba(255, 255, 255, 0.25);
}

.message-content.bubble-style .file-tag .tag-file-name,
.message-content.bubble-style .file-tag .tag-line-range {
  color: inherit;
}

/* ===== 内联上下文图片（与文件标签混合显示） ===== */
.context-thumb-inline {
  width: 24px;
  height: 24px;
  object-fit: cover;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid var(--theme-border);
  transition: transform 0.2s;
  vertical-align: middle;
}

.context-thumb-inline:hover {
  transform: scale(1.1);
}

.inline-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.inline-thumb {
  max-width: 80px;
  max-height: 80px;
  object-fit: cover;
  border-radius: 6px;
  cursor: pointer;
  transition: transform 0.2s;
}

.inline-thumb:hover {
  transform: scale(1.02);
}

/* ===== 特殊样式 ===== */
/* hint 样式 */
.user-message-container.hint .message-content {
  color: var(--theme-secondary-foreground);
}

/* error 样式 */
.user-message-container.error .message-content {
  color: var(--theme-error);
}
</style>

