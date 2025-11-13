<template>
  <div class="session-search">
    <!-- 搜索输入框 -->
    <div class="search-input-container">
      <input
        ref="searchInputRef"
        v-model="searchQuery"
        type="text"
        class="search-input"
        placeholder="搜索会话..."
        @input="handleSearch"
        @keydown.escape="handleClose"
      >
      <button v-if="searchQuery" class="clear-btn" @click="handleClear">
        ✕
      </button>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searchResults.length > 0" class="search-results">
      <div class="results-header">
        找到 {{ searchResults.length }} 个结果
      </div>
      <div class="results-list">
        <div
          v-for="result in searchResults"
          :key="result.sessionId"
          class="result-item"
          @click="handleSelectSession(result.sessionId)"
        >
          <!-- 会话标题 -->
          <div class="result-title">
            <span
              v-for="(part, index) in highlightedTitle(result)"
              :key="index"
              :class="{ highlighted: part.highlighted }"
            >
              {{ part.text }}
            </span>
          </div>

          <!-- 匹配的消息片段 -->
          <div
            v-for="match in result.matchedMessages.filter(m => m.matchType === 'CONTENT')"
            :key="match.messageId"
            class="result-snippet"
          >
            <span
              v-for="(part, index) in highlightedSnippet(match)"
              :key="index"
              :class="{ highlighted: part.highlighted }"
            >
              {{ part.text }}
            </span>
          </div>

          <!-- 相关性分数（调试用） -->
          <div class="result-meta">
            相关性: {{ result.relevanceScore.toFixed(1) }} |
            {{ new Date(result.timestamp).toLocaleString() }}
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="searchQuery && !isSearching" class="empty-state">
      <div class="empty-icon">🔍</div>
      <div class="empty-text">未找到匹配的会话</div>
    </div>

    <!-- 加载状态 -->
    <div v-if="isSearching" class="loading-state">
      <div class="loading-spinner"></div>
      <div class="loading-text">搜索中...</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { sessionSearchService, type SessionSearchResult } from '@/services/sessionSearchService'
import type { Session } from '@/types/message'
import type { Message } from '@/types/enhancedMessage'

interface Props {
  sessions: Session[]
  messagesMap: Map<string, Message[]>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  selectSession: [sessionId: string]
  close: []
}>()

const searchInputRef = ref<HTMLInputElement>()
const searchQuery = ref('')
const searchResults = ref<SessionSearchResult[]>([])
const isSearching = ref(false)

let searchTimeout: number | null = null

onMounted(() => {
  nextTick(() => {
    searchInputRef.value?.focus()
  })
})

async function handleSearch() {
  // 防抖
  if (searchTimeout) {
    clearTimeout(searchTimeout)
  }

  if (!searchQuery.value.trim()) {
    searchResults.value = []
    return
  }

  isSearching.value = true

  searchTimeout = window.setTimeout(async () => {
    try {
      searchResults.value = await sessionSearchService.search(
        searchQuery.value,
        props.sessions,
        props.messagesMap,
        {
          searchInTitles: true,
          searchInContent: true,
          maxMessagesPerSession: 3,
          maxResults: 20
        }
      )
    } catch (error) {
      console.error('搜索失败:', error)
      searchResults.value = []
    } finally {
      isSearching.value = false
    }
  }, 300)
}

function handleClear() {
  searchQuery.value = ''
  searchResults.value = []
  searchInputRef.value?.focus()
}

function handleClose() {
  emit('close')
}

function handleSelectSession(sessionId: string) {
  emit('selectSession', sessionId)
  emit('close')
}

function highlightedTitle(result: SessionSearchResult) {
  const titleMatch = result.matchedMessages.find(m => m.matchType === 'TITLE')
  if (!titleMatch) {
    return [{ text: result.sessionName, highlighted: false }]
  }
  return sessionSearchService.highlightText(titleMatch.snippet, titleMatch.highlights)
}

function highlightedSnippet(match: SessionSearchResult['matchedMessages'][0]) {
  return sessionSearchService.highlightText(match.snippet, match.highlights)
}



<style scoped>
.session-search {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--ide-panel-background, #f6f8fa);
}

.search-input-container {
  position: relative;
  padding: 12px;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.search-input {
  width: 100%;
  padding: 8px 32px 8px 12px;
  background: var(--ide-input-background, #ffffff);
  color: var(--ide-foreground, #24292e);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 13px;
  outline: none;
}

.search-input:focus {
  border-color: var(--ide-accent, #0366d6);
}

.clear-btn {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  color: var(--ide-foreground, #24292e);
  cursor: pointer;
  padding: 4px 8px;
  opacity: 0.6;
}

.clear-btn:hover {
  opacity: 1;
}

.search-results {
  flex: 1;
  overflow-y: auto;
}

.results-header {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--ide-text-secondary, #6a737d);
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.results-list {
  padding: 4px 0;
}

.result-item {
  padding: 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
  transition: background-color 0.15s;
}

.result-item:hover {
  background: var(--ide-hover-background, #f6f8fa);
}

.result-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
  color: var(--ide-foreground, #24292e);
}

.result-snippet {
  font-size: 12px;
  color: var(--ide-text-secondary, #6a737d);
  margin: 4px 0;
  line-height: 1.5;
}

.result-meta {
  font-size: 11px;
  color: var(--ide-text-secondary, #6a737d);
  margin-top: 6px;
  opacity: 0.7;
}

.highlighted {
  background: #ffeb3b;
  color: var(--ide-foreground, #24292e);
  padding: 0 2px;
  border-radius: 2px;
}

.empty-state,
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--ide-text-secondary, #6a737d);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-text {
  font-size: 14px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--ide-border, #e1e4e8);
  border-top-color: var(--ide-accent, #0366d6);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

.loading-text {
  font-size: 14px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>


