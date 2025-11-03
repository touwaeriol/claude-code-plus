<template>
  <div
    class="markdown-body"
    v-html="renderedHtml"
    @click="handleClick"
  ></div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { markdownService } from '@/services/markdownService'
import { highlightService } from '@/services/highlightService'
import { ideService } from '@/services/ideaBridge'

interface Props {
  content: string
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isDark: false
})

const renderedHtml = ref('')
const isHighlighting = ref(false)

// 初始化代码高亮
onMounted(async () => {
  await highlightService.init()
  await renderContent()
})

// 监听内容变化
watch(() => props.content, renderContent)
watch(() => props.isDark, renderContent)

/**
 * 渲染 Markdown 内容
 */
async function renderContent() {
  if (isHighlighting.value) return

  try {
    isHighlighting.value = true

    // 先用 markdown-it 渲染基础 HTML
    let html = markdownService.render(props.content)

    // 然后对代码块进行高亮处理
    html = await highlightCodeBlocks(html)

    renderedHtml.value = html
  } catch (error) {
    console.error('Failed to render markdown:', error)
    renderedHtml.value = `<p>渲染失败: ${error}</p>`
  } finally {
    isHighlighting.value = false
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
        const highlightedHtml = await highlightService.highlight(code, lang, props.isDark)
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
      ideService.openFile(filePath)
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
 * 显示复制反馈
 */
function showCopyFeedback(button: HTMLElement) {
  const originalText = button.textContent
  button.textContent = '✓ 已复制'
  button.classList.add('copied')

  setTimeout(() => {
    button.textContent = originalText
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
  margin-top: 24px;
  margin-bottom: 16px;
  font-weight: 600;
  line-height: 1.25;
}

.markdown-body h1 {
  font-size: 2em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3em;
}

.markdown-body h2 {
  font-size: 1.5em;
  border-bottom: 1px solid #eaecef;
  padding-bottom: 0.3em;
}

.markdown-body p {
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 2em;
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-body li {
  margin-top: 0.25em;
}

.markdown-body blockquote {
  padding: 0 1em;
  color: #6a737d;
  border-left: 0.25em solid #dfe2e5;
  margin: 0 0 16px 0;
}

.markdown-body a {
  color: #0366d6;
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.markdown-body a.file-link {
  color: #0366d6;
  font-family: monospace;
}

.markdown-body a.file-link:hover {
  text-decoration: underline;
  background: #f6f8fa;
}

.markdown-body code {
  padding: 0.2em 0.4em;
  margin: 0;
  font-size: 85%;
  background-color: rgba(27, 31, 35, 0.05);
  border-radius: 3px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
}

.markdown-body pre {
  padding: 16px;
  overflow: auto;
  font-size: 85%;
  line-height: 1.45;
  background-color: #f6f8fa;
  border-radius: 6px;
  margin-bottom: 16px;
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
  margin: 16px 0;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  overflow: hidden;
}

.code-block-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
}

.code-block-header .language {
  font-size: 12px;
  color: #586069;
  text-transform: uppercase;
  font-weight: 600;
}

.code-block-header .copy-btn {
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid #e1e4e8;
  background: white;
  border-radius: 3px;
  cursor: pointer;
  color: #586069;
  transition: all 0.2s;
}

.code-block-header .copy-btn:hover {
  background: #f3f4f6;
  border-color: #d1d5da;
}

.code-block-header .copy-btn.copied {
  color: #22863a;
  border-color: #34d058;
}

.code-content {
  margin: 0;
  padding: 0;
}

.code-content pre {
  margin: 0;
  padding: 16px;
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
  margin-bottom: 16px;
  display: block;
  width: max-content;
  max-width: 100%;
  overflow: auto;
}

.markdown-body table th,
.markdown-body table td {
  padding: 6px 13px;
  border: 1px solid #dfe2e5;
}

.markdown-body table tr {
  background-color: #fff;
  border-top: 1px solid #c6cbd1;
}

.markdown-body table tr:nth-child(2n) {
  background-color: #f6f8fa;
}

.markdown-body table th {
  font-weight: 600;
  background-color: #f6f8fa;
}

/* HR */
.markdown-body hr {
  height: 0.25em;
  padding: 0;
  margin: 24px 0;
  background-color: #e1e4e8;
  border: 0;
}
</style>
