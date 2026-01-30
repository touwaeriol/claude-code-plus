<template>
  <!-- 外层容器使用 padding 而非 margin，避免 DynamicScroller 高度计算问题 -->
  <div class="compact-summary-wrapper">
    <div class="compact-summary-card" :class="{ expanded: isExpanded }">
    <!-- 头部：图标 + 标题 + 操作按钮 -->
    <div class="card-header">
      <div class="header-left" @click="toggleExpand">
        <span class="icon">{{ isExpanded ? '📖' : '📦' }}</span>
        <span class="title">{{ $t('compact.contextRestored') }}</span>
        <span class="subtitle">{{ $t('compact.sessionSummary') }}</span>
      </div>
      <div class="header-buttons">
        <button class="action-button" :title="$t('common.copy')" @click.stop="handleCopy">
          <span v-if="copied">✓</span>
          <span v-else>📋</span>
        </button>
        <button class="expand-button" @click="toggleExpand">
          {{ isExpanded ? $t('compact.collapse') + ' ▴' : $t('compact.expand') + ' ▾' }}
        </button>
      </div>
    </div>

    <!-- 摘要内容（可展开） -->
    <div v-if="isExpanded" class="card-content">
      <div v-if="preTokens" class="token-stats expanded">
        <span class="token-label">{{ $t('compact.originalTokens') }}:</span>
        <span class="token-value">{{ formatTokens(preTokens) }}</span>
      </div>
      <div class="summary-text" v-html="renderedContent"></div>
    </div>

    <!-- 折叠时的预览 -->
    <div v-else class="card-preview">
      <div v-if="preTokens" class="token-stats">
        <span class="token-label">{{ $t('compact.originalTokens') }}:</span>
        <span class="token-value">{{ formatTokens(preTokens) }}</span>
      </div>
      <span class="preview-text">{{ previewText }}</span>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { sanitizeMarkdownHtml } from '@/utils/safeHtml'
import type { ContentBlock } from '@/types/message'

const { t: _t } = useI18n()

interface Props {
  content: ContentBlock[]
  preTokens?: number
  trigger?: 'manual' | 'auto'
}

const props = defineProps<Props>()

// 展开状态（默认折叠）
const isExpanded = ref(false)

// 复制状态
const copied = ref(false)

// 切换展开/折叠
function toggleExpand() {
  isExpanded.value = !isExpanded.value
}

// 复制内容
async function handleCopy() {
  try {
    await navigator.clipboard.writeText(textContent.value)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (e) {
    console.error('Failed to copy:', e)
  }
}

// 提取文本内容
const textContent = computed(() => {
  return props.content
    .filter(block => block.type === 'text')
    .map(block => (block as { type: 'text', text: string }).text)
    .join('\n')
})

// 预览文本（折叠时显示）
const previewText = computed(() => {
  // 移除开头的固定文本，显示摘要的关键部分
  let text = textContent.value
  if (text.startsWith('This session is being continued')) {
    text = text.replace(/^This session is being continued[^:]*:\s*/i, '')
  }
  // 截取前 100 个字符作为预览
  if (text.length > 100) {
    return text.substring(0, 100) + '...'
  }
  return text
})

// 格式化 token 数量
function formatTokens(tokens: number): string {
  if (tokens >= 1000) {
    return (tokens / 1000).toFixed(1) + 'k'
  }
  return tokens.toString()
}

// 渲染后的内容（带格式，已消毒防止 XSS）
const renderedContent = computed(() => {
  let html = textContent.value

  // 将 Markdown 标题转换为 HTML
  html = html.replace(/^### (.*?)$/gm, '<h4>$1</h4>')
  html = html.replace(/^## (.*?)$/gm, '<h3>$1</h3>')
  html = html.replace(/^# (.*?)$/gm, '<h2>$1</h2>')

  // 将 **粗体** 转换为 HTML
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')

  // 将 `代码` 转换为 HTML
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')

  // 将代码块转换为 HTML
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="lang-$1">$2</code></pre>')

  // 将换行转换为 <br>
  html = html.replace(/\n/g, '<br>')

  // 将列表项转换为 HTML
  html = html.replace(/<br>- (.*?)(?=<br>|$)/g, '<br><li>$1</li>')
  html = html.replace(/<br>\d+\. (.*?)(?=<br>|$)/g, '<br><li>$1</li>')

  // 使用 DOMPurify 消毒，防止 XSS 攻击
  return sanitizeMarkdownHtml(html)
})
</script>

<style scoped>
/* 外层容器使用 padding，DynamicScroller 能正确计算高度 */
.compact-summary-wrapper {
  padding: 12px 16px;
}

.compact-summary-card {
  border-radius: 12px;
  background: linear-gradient(135deg,
    var(--theme-info-background, rgba(59, 130, 246, 0.1)) 0%,
    var(--theme-background, #1e1e1e) 100%);
  border: 1px solid var(--theme-info-border, rgba(59, 130, 246, 0.3));
  overflow: hidden;
  transition: all 0.3s ease;
}

.compact-summary-card:hover {
  border-color: var(--theme-info-border, rgba(59, 130, 246, 0.5));
}

.compact-summary-card.expanded {
  background: var(--theme-background);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  user-select: none;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex: 1;
}

.header-left:hover {
  opacity: 0.8;
}

.header-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
}

.action-button {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  transition: all 0.2s;
}

.action-button:hover {
  background: var(--theme-hover-background);
  border-color: var(--theme-foreground);
}

.icon {
  font-size: 18px;
}

.title {
  font-weight: 600;
  color: var(--theme-foreground);
  font-size: 14px;
}

.subtitle {
  font-size: 12px;
  color: var(--theme-secondary-foreground);
}

.expand-button {
  background: transparent;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  cursor: pointer;
  transition: all 0.2s;
}

.expand-button:hover {
  background: var(--theme-hover-background);
  border-color: var(--theme-foreground);
}

.card-preview {
  padding: 0 16px 12px;
}

.token-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  padding: 6px 10px;
  background: var(--theme-selection-background);
  border-radius: 6px;
  width: fit-content;
}

.token-stats.expanded {
  margin-bottom: 12px;
}

.token-label {
  font-size: 12px;
  color: var(--theme-secondary-foreground);
}

.token-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-accent);
  font-family: var(--theme-editor-font-family);
}

.preview-text {
  font-size: 12px;
  color: var(--theme-secondary-foreground);
  font-style: italic;
  line-height: 1.4;
}

.card-content {
  padding: 0 16px 16px;
  border-top: 1px solid var(--theme-border);
  margin-top: 0;
}

.summary-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--theme-foreground);
  max-height: 400px;
  overflow-y: auto;
  padding: 12px 0;
}

.summary-text :deep(h2),
.summary-text :deep(h3),
.summary-text :deep(h4) {
  margin: 16px 0 8px;
  color: var(--theme-foreground);
}

.summary-text :deep(h2) {
  font-size: 16px;
}

.summary-text :deep(h3) {
  font-size: 14px;
}

.summary-text :deep(h4) {
  font-size: 13px;
}

.summary-text :deep(strong) {
  font-weight: 600;
  color: var(--theme-accent);
}

.summary-text :deep(code) {
  background: var(--theme-selection-background);
  padding: 1px 4px;
  border-radius: 3px;
  font-family: var(--theme-editor-font-family);
  font-size: 12px;
}

.summary-text :deep(pre) {
  background: var(--theme-selection-background);
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
}

.summary-text :deep(pre code) {
  background: transparent;
  padding: 0;
}

.summary-text :deep(li) {
  margin-left: 16px;
  list-style-type: disc;
}
</style>
