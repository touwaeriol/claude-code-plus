<template>
  <div class="tool-display websearch-tool">
    <div class="tool-header">
      <span class="tool-icon">🔍</span>
      <span class="tool-name">WebSearch</span>
      <code class="tool-query">{{ queryPreview }}</code>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="search-info">
        <div class="info-row">
          <span class="label">搜索查询:</span>
          <span class="value">{{ query }}</span>
        </div>
        <div
          v-if="allowedDomains.length > 0"
          class="info-row"
        >
          <span class="label">允许域名:</span>
          <div class="domain-list">
            <span
              v-for="(domain, index) in allowedDomains"
              :key="index"
              class="domain-tag allowed"
            >
              {{ domain }}
            </span>
          </div>
        </div>
        <div
          v-if="blockedDomains.length > 0"
          class="info-row"
        >
          <span class="label">禁止域名:</span>
          <div class="domain-list">
            <span
              v-for="(domain, index) in blockedDomains"
              :key="index"
              class="domain-tag blocked"
            >
              {{ domain }}
            </span>
          </div>
        </div>
      </div>

      <div
        v-if="result"
        class="search-results"
      >
        <div class="results-header">
          <span>搜索结果</span>
          <span
            v-if="resultsCount > 0"
            class="results-count"
          >{{ resultsCount }} 个结果</span>
        </div>
        <div class="results-content">
          <div
            v-if="searchResults.length > 0"
            class="result-list"
          >
            <div
              v-for="(item, index) in searchResults"
              :key="index"
              class="search-result"
            >
              <div class="result-title">
                <a
                  :href="item.url"
                  class="result-link"
                  target="_blank"
                  rel="noopener noreferrer"
                  @click.prevent="openLink(item.url)"
                >
                  {{ item.title || item.url }}
                </a>
                <span
                  v-if="item.position"
                  class="result-position"
                >#{{ item.position }}</span>
              </div>
              <p
                v-if="item.snippet"
                class="result-snippet"
              >
                {{ item.snippet }}
              </p>
              <div class="result-url">
                {{ item.displayUrl }}
              </div>
            </div>
          </div>
          <pre
            v-else
            class="result-text"
          >{{ resultText }}</pre>
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

interface SearchResult {
  title?: string
  url: string
  snippet?: string
  position?: number
  displayUrl?: string
}

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)

const query = computed(() => props.toolUse.input.query || '')
const allowedDomains = computed(() => props.toolUse.input.allowed_domains || [])
const blockedDomains = computed(() => props.toolUse.input.blocked_domains || [])

const queryPreview = computed(() => {
  const q = query.value
  const maxLength = 40
  if (q.length <= maxLength) return q
  return q.substring(0, maxLength) + '...'
})

const resultText = computed(() => {
  if (!props.result) return ''
  if (typeof props.result.content === 'string') {
    return props.result.content
  }
  return JSON.stringify(props.result.content, null, 2)
})

const searchResults = computed<SearchResult[]>(() => {
  if (!props.result || !props.result.content) return []

  try {
    // 尝试解析结果内容
    let content = props.result.content
    if (typeof content === 'string') {
      content = JSON.parse(content)
    }

    // 检查是否有 results 数组
    if (content && typeof content === 'object' && 'results' in content) {
      const results = (content as any).results
      if (Array.isArray(results)) {
        return results.map((item: any, index: number) => ({
          title: item.title || item.name,
          url: item.url || item.link,
          snippet: item.snippet || item.description || item.content,
          position: item.position || index + 1,
          displayUrl: formatUrl(item.url || item.link)
        }))
      }
    }

    // 检查是否是搜索结果数组
    if (Array.isArray(content)) {
      return content.map((item: any, index: number) => ({
        title: item.title || item.name,
        url: item.url || item.link,
        snippet: item.snippet || item.description,
        position: item.position || index + 1,
        displayUrl: formatUrl(item.url || item.link)
      }))
    }
  } catch (e) {
    console.warn('Failed to parse search results:', e)
  }

  return []
})

const resultsCount = computed(() => searchResults.value.length)

function formatUrl(url: string): string {
  if (!url) return ''
  try {
    const urlObj = new URL(url)
    return urlObj.hostname + urlObj.pathname
  } catch (e) {
    return url
  }
}

function openLink(url: string) {
  if (!url) return

  // 验证 URL 格式
  try {
    new URL(url)
    window.open(url, '_blank', 'noopener,noreferrer')
  } catch (e) {
    console.error('Invalid URL:', url)
  }
}
</script>

<style scoped>
.websearch-tool {
  border-color: #0366d6;
}

.websearch-tool .tool-name {
  color: #0366d6;
}

.tool-query {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: rgba(3, 102, 214, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  color: #24292e;
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-info {
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  align-items: flex-start;
}

.info-row .label {
  font-weight: 600;
  color: #586069;
  min-width: 80px;
  flex-shrink: 0;
}

.info-row .value {
  color: #24292e;
  flex: 1;
  word-wrap: break-word;
}

.domain-list {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  flex: 1;
}

.domain-tag {
  padding: 3px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 10px;
  font-family: monospace;
}

.domain-tag.allowed {
  background: #e6ffed;
  color: #22863a;
}

.domain-tag.blocked {
  background: #ffeef0;
  color: #d73a49;
}

.search-results {
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow: hidden;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
  font-size: 12px;
  font-weight: 600;
}

.results-count {
  color: #0366d6;
  font-size: 11px;
}

.results-content {
  max-height: 600px;
  overflow-y: auto;
}

.result-list {
  padding: 8px;
}

.search-result {
  padding: 12px;
  margin-bottom: 8px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  transition: all 0.2s;
}

.search-result:hover {
  background: #f6f8fa;
  border-color: #0366d6;
  box-shadow: 0 1px 3px rgba(3, 102, 214, 0.1);
}

.result-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.result-link {
  font-size: 14px;
  font-weight: 600;
  color: #0366d6;
  text-decoration: none;
  flex: 1;
  word-wrap: break-word;
}

.result-link:hover {
  text-decoration: underline;
  color: #0256c0;
}

.result-position {
  font-size: 11px;
  color: #586069;
  background: #f6f8fa;
  padding: 2px 6px;
  border-radius: 3px;
  flex-shrink: 0;
}

.result-snippet {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #586069;
  line-height: 1.5;
  word-wrap: break-word;
}

.result-url {
  font-size: 12px;
  color: #22863a;
  font-family: monospace;
  word-wrap: break-word;
}

.result-text {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
