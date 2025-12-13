<template>
  <div
    class="markdown-body"
    @click="handleClick"
    v-html="renderedHtml"
  />
</template>

<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from '@/composables/useI18n'
import {markdownService} from '@/services/markdownService'
import {highlightService} from '@/services/highlightService'
import {ideaBridge} from '@/services/ideaBridge'

const { t } = useI18n()

interface Props {
  content: string
}

const props = defineProps<Props>()

const renderedHtml = ref('')
const isHighlighting = ref(false)
// 标记是否有待处理的更新
let hasPendingUpdate = false
// 记录上次渲染的内容，用于判断是否需要重新渲染
let lastRenderedContent = ''

// 初始化代码高亮
onMounted(async () => {
  await highlightService.init()
  await renderContent()
})

// 监听内容变化
watch(() => props.content, renderContent)

/**
 * 渲染 Markdown 内容
 * 支持流式更新：如果正在渲染，标记待处理，渲染完成后检查最新内容
 */
async function renderContent() {
  // 如果正在渲染，标记有待处理更新
  if (isHighlighting.value) {
    hasPendingUpdate = true
    return
  }

  try {
    isHighlighting.value = true
    const contentToRender = props.content

    // 先用 markdown-it 渲染基础 HTML
    let html = markdownService.render(contentToRender)

    // 然后对代码块进行高亮处理
    html = await highlightCodeBlocks(html)

    renderedHtml.value = html
    lastRenderedContent = contentToRender
  } catch (error) {
    console.error('Failed to render markdown:', error)
    renderedHtml.value = `<p>${t('common.renderFailed')}: ${error}</p>`
  } finally {
    isHighlighting.value = false

    // 检查是否有待处理的更新，且内容确实有变化
    if (hasPendingUpdate) {
      hasPendingUpdate = false
      // 如果当前 props.content 与上次渲染的不同，则重新渲染
      if (props.content !== lastRenderedContent) {
        await renderContent()
      }
    }
  }
}

/**
 * 高亮代码块
 */
async function highlightCodeBlocks(html: string): Promise<string> {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  const codeBlocks = doc.querySelectorAll('.code-block-wrapper')

  for (const block of codeBlocks) {
    const lang = block.getAttribute('data-lang') || 'text'
    const codeElement = block.querySelector('code')

    if (codeElement) {
      const code = codeElement.textContent || ''
      try {
        const highlightedHtml = await highlightService.highlight(code, lang)
        const contentDiv = block.querySelector('.code-content')
        if (contentDiv) {
          contentDiv.innerHTML = highlightedHtml
        }
      } catch (error) {
        console.warn(`Failed to highlight ${lang}:`, error)
      }
    }
  }

  return doc.body.innerHTML
}

/**
 * 处理点击事件
 */
function handleClick(event: MouseEvent) {
  const target = event.target as HTMLElement

  // 处理复制按钮点击
  if (target.classList.contains('copy-btn')) {
    event.preventDefault()
    const encodedCode = target.getAttribute('data-code')
    if (encodedCode) {
      const code = decodeURIComponent(encodedCode)
      copyToClipboard(code)
      showCopyFeedback(target)
    }
    return
  }

  // 处理文件链接点击
  if (target.tagName === 'A') {
    const href = target.getAttribute('href')
    if (href && (href.startsWith('/') || href.startsWith('file://'))) {
      event.preventDefault()
      const filePath = href.replace('file://', '')
      ideaBridge.query('ide.openFile', { filePath })
    }
  }
}

/**
 * 复制到剪贴板
 */
async function copyToClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
  } catch (error) {
    console.error('Failed to copy:', error)
  }
}

/**
 * 显示复制反馈（图标版）
 */
function showCopyFeedback(button: HTMLElement) {
  const originalHtml = button.innerHTML
  // 显示对勾图标
  button.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>`
  button.classList.add('copied')

  setTimeout(() => {
    button.innerHTML = originalHtml
    button.classList.remove('copied')
  }, 2000)
}
</script>

<style>
/* GitHub Markdown 样式基础 */
.markdown-body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  font-size: 14px;
  line-height: 1.6;
  word-wrap: break-word;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin-top: 8px;
  margin-bottom: 4px;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-body h1 {
  font-size: 1.5em;
  border-bottom: 1px solid var(--theme-border);
  padding-bottom: 0.2em;
}

.markdown-body h2 {
  font-size: 1.3em;
  border-bottom: 1px solid var(--theme-border);
  padding-bottom: 0.2em;
}

.markdown-body p {
  margin-top: 0;
  margin-bottom: 4px;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 1.5em;
  margin-top: 0;
  margin-bottom: 4px;
}

.markdown-body li {
  margin-top: 0.1em;
}

.markdown-body blockquote {
  padding: 0 0.8em;
  color: var(--theme-foreground);
  opacity: 0.7;
  border-left: 0.25em solid var(--theme-border);
  margin: 0 0 6px 0;
}

.markdown-body a {
  color: var(--theme-link);
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.markdown-body a.file-link {
  color: var(--theme-link);
  font-family: monospace;
}

.markdown-body a.file-link:hover {
  text-decoration: underline;
  background: var(--theme-panel-background);
}

.markdown-body code {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: var(--theme-code-background);
  color: var(--theme-code-foreground);
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
}

.markdown-body pre {
  padding: 4px 6px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: var(--theme-code-background);
  border-radius: 4px;
  margin-bottom: 6px;
}

.markdown-body pre code {
  display: inline;
  padding: 0;
  margin: 0;
  overflow: visible;
  line-height: inherit;
  word-wrap: normal;
  background-color: transparent;
  border: 0;
}

/* 代码块包装器 */
.code-block-wrapper {
  margin: 6px 0;
  border: 1px solid var(--theme-border);
  border-radius: 4px;
  overflow: hidden;
}

.code-block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  background: var(--theme-panel-background);
  border-bottom: 1px solid var(--theme-border);
}

.code-block-header .language {
  font-size: 12px;
  color: var(--theme-foreground);
  opacity: 0.7;
  text-transform: uppercase;
  font-weight: 600;
}

.code-block-header .copy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: 1px solid var(--theme-border);
  background: var(--theme-background);
  border-radius: 3px;
  cursor: pointer;
  color: var(--theme-secondary-foreground);
  transition: all 0.2s;
  line-height: 1;
}

.code-block-header .copy-btn:hover {
  background: var(--theme-panel-background);
  border-color: var(--theme-accent);
}

.code-block-header .copy-btn.copied {
  color: var(--theme-success);
  border-color: var(--theme-success);
}

.code-content {
  margin: 0;
  padding: 0;
}

.code-content pre {
  margin: 0;
  padding: 4px 6px;
  background: transparent;
  border-radius: 0;
}

/* Shiki 代码高亮覆盖 */
.code-content pre.shiki {
  background-color: transparent !important;
}

.code-content pre.shiki code {
  background: transparent !important;
}

/* 表格样式 */
.markdown-body table {
  border-spacing: 0;
  border-collapse: collapse;
  margin-top: 0;
  margin-bottom: 6px;
  display: block;
  width: max-content;
  max-width: 100%;
  overflow: auto;
}

.markdown-body table th,
.markdown-body table td {
  padding: 6px 13px;
  border: 1px solid var(--theme-border);
}

.markdown-body table tr {
  background-color: var(--theme-background);
  border-top: 1px solid var(--theme-border);
}

.markdown-body table tr:nth-child(2n) {
  background-color: var(--theme-panel-background);
}

.markdown-body table th {
  font-weight: 600;
  background-color: var(--theme-panel-background);
}

/* HR */
.markdown-body hr {
  height: 0.25em;
  padding: 0;
  margin: 8px 0;
  background-color: var(--theme-border);
  border: 0;
}
</style>
