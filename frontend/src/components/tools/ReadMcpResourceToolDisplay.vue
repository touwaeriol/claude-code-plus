<template>
  <div class="tool-display read-mcp-resource-tool">
    <div class="tool-header">
      <span class="tool-icon">📖</span>
      <span class="tool-name">ReadMcpResource</span>
      <span class="server-badge">{{ serverName }}</span>
      <span
        v-if="mimeType"
        class="mime-badge"
      >{{ mimeType }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="tool-meta">
        <div class="info-row">
          <span class="label">服务器:</span>
          <span class="value">{{ serverName }}</span>
        </div>
        <div class="info-row">
          <span class="label">URI:</span>
          <code
            class="value uri-link"
            :title="uri"
            @click="copyUri"
          >{{ uriDisplay }}</code>
        </div>
        <div
          v-if="mimeType"
          class="info-row"
        >
          <span class="label">类型:</span>
          <span class="value">{{ mimeType }}</span>
        </div>
      </div>

      <div
        v-if="result"
        class="content-section"
      >
        <div class="section-header">
          <span>资源内容</span>
          <div class="header-actions">
            <button
              v-if="content"
              class="copy-btn"
              @click="copyContent"
            >
              复制
            </button>
          </div>
        </div>

        <!-- Markdown 渲染 -->
        <div
          v-if="isMarkdown"
          class="content-markdown"
        >
          <MarkdownRenderer :content="content" />
        </div>

        <!-- JSON 格式化 -->
        <pre
          v-else-if="isJson"
          class="content-json"
        >{{ formattedJson }}</pre>

        <!-- 纯文本 -->
        <pre
          v-else-if="content"
          class="content-text"
        >{{ content }}</pre>

        <div
          v-else
          class="no-content"
        >
          无内容
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

const serverName = computed(() => props.toolUse.input.server || '')
const uri = computed(() => props.toolUse.input.uri || '')

const uriDisplay = computed(() => {
  const u = uri.value
  const maxLength = 50
  if (u.length <= maxLength) return u
  return u.substring(0, maxLength) + '...'
})

const resultContent = computed(() => {
  if (!props.result?.content) return null
  if (typeof props.result.content === 'string') {
    try {
      return JSON.parse(props.result.content)
    } catch {
      return { content: props.result.content }
    }
  }
  return props.result.content
})

const success = computed(() => {
  if (!resultContent.value) return false
  return resultContent.value.success === true
})

const content = computed(() => {
  if (!resultContent.value || !success.value) return ''
  return resultContent.value.content || ''
})

const mimeType = computed(() => {
  if (!resultContent.value) return ''
  return resultContent.value.mimeType || ''
})

const isMarkdown = computed(() => {
  const mime = mimeType.value.toLowerCase()
  return mime.includes('markdown') || mime.includes('text/markdown')
})

const isJson = computed(() => {
  const mime = mimeType.value.toLowerCase()
  if (mime.includes('json')) return true

  // 尝试解析 JSON
  try {
    JSON.parse(content.value)
    return true
  } catch {
    return false
  }
})

const formattedJson = computed(() => {
  try {
    const obj = JSON.parse(content.value)
    return JSON.stringify(obj, null, 2)
  } catch {
    return content.value
  }
})

async function copyUri() {
  await navigator.clipboard.writeText(uri.value)
}

async function copyContent() {
  if (content.value) {
    await navigator.clipboard.writeText(content.value)
  }
}
</script>

<style scoped>
.read-mcp-resource-tool {
  border-color: #28a745;
}

.read-mcp-resource-tool .tool-name {
  color: #28a745;
}

.server-badge {
  font-size: 11px;
  background: rgba(40, 167, 69, 0.1);
  color: #28a745;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.mime-badge {
  font-size: 11px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  padding: 2px 8px;
  border-radius: 10px;
  color: #586069;
  font-family: monospace;
  margin-left: auto;
}

.tool-meta {
  margin-bottom: 2px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}

.info-row .label {
  font-weight: 600;
  color: #586069;
  min-width: 80px;
}

.info-row .value {
  font-family: monospace;
  color: #24292e;
}

.uri-link {
  cursor: pointer;
  color: #0366d6;
  text-decoration: underline;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.uri-link:hover {
  color: #0256c0;
}

.content-section {
  margin-top: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #586069;
  margin-bottom: 2px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.copy-btn {
  padding: 2px 8px;
  font-size: 11px;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  background: white;
  cursor: pointer;
}

.copy-btn:hover {
  background: #f6f8fa;
}

.content-markdown {
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  max-height: 500px;
  overflow: auto;
}

.content-json {
  margin: 0;
  padding: 12px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 500px;
  overflow: auto;
  color: #24292e;
}

.content-text {
  margin: 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #24292e;
}

.no-content {
  padding: 24px;
  text-align: center;
  color: #586069;
  font-size: 13px;
  font-style: italic;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
}
</style>
