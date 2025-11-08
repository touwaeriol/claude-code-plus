<template>
  <div class="tool-display webfetch-tool">
    <div class="tool-header">
      <span class="tool-icon">🌐</span>
      <span class="tool-name">WebFetch</span>
      <a
        :href="url"
        class="tool-url"
        target="_blank"
        rel="noopener noreferrer"
        @click.prevent="openLink(url)"
      >
        {{ domain }}
      </a>
      <span
        v-if="statusCode"
        class="status-badge"
        :class="statusClass"
      >{{ statusCode }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="fetch-info">
        <div class="info-row">
          <span class="label">目标 URL:</span>
          <a
            :href="url"
            class="value url-link"
            target="_blank"
            rel="noopener noreferrer"
            @click.prevent="openLink(url)"
          >
            {{ url }}
          </a>
        </div>
        <div
          v-if="prompt"
          class="info-row"
        >
          <span class="label">Prompt:</span>
          <span class="value">{{ prompt }}</span>
        </div>
        <div
          v-if="statusCode"
          class="info-row"
        >
          <span class="label">状态码:</span>
          <span class="value">{{ statusCode }} {{ statusText }}</span>
        </div>
        <div
          v-if="contentType"
          class="info-row"
        >
          <span class="label">内容类型:</span>
          <span class="value">{{ contentType }}</span>
        </div>
        <div
          v-if="fetchTime"
          class="info-row"
        >
          <span class="label">抓取时间:</span>
          <span class="value">{{ fetchTime }}</span>
        </div>
      </div>

      <div
        v-if="result"
        class="fetch-result"
      >
        <div class="result-header">
          <span>抓取内容</span>
          <div class="header-actions">
            <span
              v-if="contentLength"
              class="content-size"
            >{{ contentSize }}</span>
            <button
              v-if="canCopy"
              class="action-btn"
              title="复制内容"
              @click="copyContent"
            >
              📋 复制
            </button>
            <button
              v-if="isTruncated"
              class="action-btn"
              @click="toggleFullContent"
            >
              {{ showFullContent ? '收起' : '查看完整内容' }}
            </button>
          </div>
        </div>
        <div
          class="result-content"
          :class="{ 'full-content': showFullContent }"
        >
          <div
            v-if="isMarkdown"
            class="markdown-preview"
          >
            <MarkdownRenderer :content="displayContent" />
          </div>
          <pre
            v-else-if="isJson"
            class="json-content"
          >{{ formattedJson }}</pre>
          <pre
            v-else
            class="text-content"
          >{{ displayContent }}</pre>
        </div>
      </div>
    </div>
    <button
      class="expand-btn"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)
const showFullContent = ref(false)

const url = computed(() => props.toolUse.input.url || '')
const prompt = computed(() => props.toolUse.input.prompt || '')

const domain = computed(() => {
  const urlStr = url.value
  try {
    const urlObj = new URL(urlStr)
    return urlObj.hostname
  } catch (e) {
    // 如果不是完整 URL，尝试提取域名部分
    return urlStr
      .replace(/^https?:\/\//, '')
      .replace(/^www\./, '')
      .split('/')[0]
      .substring(0, 40)
  }
})

// 解析结果内容
const resultContent = computed(() => {
  if (!props.result) return null

  try {
    let content = props.result.content
    if (typeof content === 'string') {
      // 尝试解析 JSON
      try {
        content = JSON.parse(content)
      } catch {
        // 如果不是 JSON，保持原样
      }
    }
    return content
  } catch (e) {
    console.warn('Failed to parse result content:', e)
    return null
  }
})

const statusCode = computed(() => {
  if (!resultContent.value || typeof resultContent.value !== 'object') return null
  return (resultContent.value as any).status_code || (resultContent.value as any).statusCode
})

const statusText = computed(() => {
  if (!statusCode.value) return ''
  const code = statusCode.value
  if (code >= 200 && code < 300) return 'OK'
  if (code >= 300 && code < 400) return 'Redirect'
  if (code >= 400 && code < 500) return 'Client Error'
  if (code >= 500) return 'Server Error'
  return ''
})

const statusClass = computed(() => {
  const code = statusCode.value
  if (!code) return 'unknown'
  if (code >= 200 && code < 300) return 'success'
  if (code >= 300 && code < 400) return 'redirect'
  if (code >= 400 && code < 500) return 'client-error'
  if (code >= 500) return 'server-error'
  return 'unknown'
})

const contentType = computed(() => {
  if (!resultContent.value || typeof resultContent.value !== 'object') return null
  return (resultContent.value as any).content_type || (resultContent.value as any).contentType
})

const fetchTime = computed(() => {
  if (!resultContent.value || typeof resultContent.value !== 'object') return null
  const timestamp = (resultContent.value as any).timestamp
  if (!timestamp) return null

  try {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN')
  } catch (e) {
    return null
  }
})

const rawContent = computed(() => {
  if (!resultContent.value) return ''

  // 如果是字符串，直接返回
  if (typeof resultContent.value === 'string') {
    return resultContent.value
  }

  // 如果是对象，尝试提取 content 字段
  if (typeof resultContent.value === 'object' && 'content' in resultContent.value) {
    const content = (resultContent.value as any).content
    return typeof content === 'string' ? content : JSON.stringify(content, null, 2)
  }

  // 否则返回 JSON 字符串
  return JSON.stringify(resultContent.value, null, 2)
})

const contentLength = computed(() => rawContent.value.length)

const contentSize = computed(() => {
  const bytes = contentLength.value
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
})

const maxPreviewLength = 2000
const isTruncated = computed(() => contentLength.value > maxPreviewLength)

const displayContent = computed(() => {
  if (showFullContent.value || !isTruncated.value) {
    return rawContent.value
  }
  return rawContent.value.substring(0, maxPreviewLength) + '\n\n... (内容已截断)'
})

const isMarkdown = computed(() => {
  const ct = contentType.value?.toLowerCase() || ''
  return ct.includes('markdown') || ct.includes('md')
})

const isJson = computed(() => {
  const ct = contentType.value?.toLowerCase() || ''
  if (ct.includes('json')) return true

  // 尝试解析内容判断是否为 JSON
  try {
    JSON.parse(rawContent.value)
    return true
  } catch {
    return false
  }
})

const formattedJson = computed(() => {
  try {
    const obj = JSON.parse(displayContent.value)
    return JSON.stringify(obj, null, 2)
  } catch {
    return displayContent.value
  }
})

const canCopy = computed(() => contentLength.value > 0)

function openLink(url: string) {
  if (!url) return
  try {
    new URL(url)
    window.open(url, '_blank', 'noopener,noreferrer')
  } catch (e) {
    console.error('Invalid URL:', url)
  }
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(rawContent.value)
    // TODO: 显示成功提示
  } catch (e) {
    console.error('Failed to copy content:', e)
  }
}

function toggleFullContent() {
  showFullContent.value = !showFullContent.value
}
</script>

<style scoped>
.webfetch-tool {
  border-color: #28a745;
}

.webfetch-tool .tool-name {
  color: #28a745;
}

.tool-url {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #0366d6;
  text-decoration: none;
  padding: 2px 6px;
  background: rgba(40, 167, 69, 0.1);
  border-radius: 3px;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-url:hover {
  text-decoration: underline;
  color: #0256c0;
}

.status-badge {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
  font-family: monospace;
}

.status-badge.success {
  background: #e6ffed;
  color: #22863a;
}

.status-badge.redirect {
  background: #fff8c5;
  color: #735c0f;
}

.status-badge.client-error,
.status-badge.server-error {
  background: #ffeef0;
  color: #d73a49;
}

.status-badge.unknown {
  background: #f6f8fa;
  color: #586069;
}

.fetch-info {
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
  align-items: flex-start;
}

.info-row .label {
  font-weight: 600;
  color: #586069;
  min-width: 90px;
  flex-shrink: 0;
}

.info-row .value {
  color: #24292e;
  flex: 1;
  word-wrap: break-word;
}

.url-link {
  color: #0366d6;
  text-decoration: none;
}

.url-link:hover {
  text-decoration: underline;
  color: #0256c0;
}

.fetch-result {
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow: hidden;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
  font-size: 12px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.content-size {
  color: #586069;
  font-size: 11px;
  font-weight: normal;
}

.action-btn {
  padding: 3px 8px;
  font-size: 11px;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  background: white;
  color: #586069;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f6f8fa;
  border-color: #d1d5da;
}

.result-content {
  max-height: 400px;
  overflow-y: auto;
}

.result-content.full-content {
  max-height: 800px;
}

.markdown-preview {
  padding: 12px;
}

.json-content,
.text-content {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #24292e;
  line-height: 1.5;
}

.json-content {
  background: #f6f8fa;
}
</style>
