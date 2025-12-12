<template>
  <Teleport to="body">
    <div
      v-if="shouldShow"
      ref="popupRef"
      class="file-select-popup"
      :style="popupStyle"
      @mousedown.prevent
    >
      <!-- 搜索输入框（可选显示） -->
      <div v-if="showSearchInput" class="popup-search">
        <input
          ref="searchInputRef"
          v-model="searchQuery"
          type="text"
          class="search-input"
          :placeholder="placeholder"
          @input="handleSearchInput"
          @keydown="handleKeyDown"
        >
      </div>

      <!-- 文件列表 -->
      <div class="file-list">
        <div
          v-for="(file, index) in files"
          :key="file.absolutePath || file.relativePath"
          :class="['file-item', { selected: index === selectedIndex }]"
          :title="file.absolutePath || file.relativePath"
          @click="selectFile(file)"
          @mouseenter="selectedIndex = index"
        >
          <span class="file-icon">{{ file.isDirectory ? '📁' : getFileIcon(file.fileType) }}</span>
          <span class="file-name">{{ file.name }}</span>
          <span class="file-path">{{ file.relativePath }}</span>
        </div>
        <!-- 无结果提示 -->
        <div v-if="files.length === 0 && showSearchInput" class="no-results">
No results
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import type { IndexedFileInfo } from '@/services/fileSearchService'

const props = defineProps<{
  visible: boolean
  files: IndexedFileInfo[]
  anchorElement: HTMLElement | null
  showSearchInput?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  select: [file: IndexedFileInfo]
  dismiss: []
  search: [query: string]
}>()

const popupRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const selectedIndex = ref(0)
const searchQuery = ref('')

// 是否显示弹窗
// - 有搜索框时：只要 visible 就显示（即使没有搜索结果）
// - 无搜索框时：需要 visible 且有文件才显示
const shouldShow = computed(() => {
  if (!props.visible) return false
  if (props.showSearchInput) return true
  return props.files.length > 0
})

// 计算弹窗位置
const popupStyle = computed(() => {
  if (!props.anchorElement) {
    return {
      display: 'none'
    }
  }

  const rect = props.anchorElement.getBoundingClientRect()

  // 弹窗显示在输入框上方
  return {
    position: 'fixed' as const,
    left: `${rect.left}px`,
    bottom: `${window.innerHeight - rect.top + 8}px`,
    zIndex: 10000
  }
})

// 选择文件
function selectFile(file: IndexedFileInfo) {
  emit('select', file)
  selectedIndex.value = 0
  searchQuery.value = ''
}

// 根据文件类型获取图标
function getFileIcon(fileType: string): string {
  const iconMap: Record<string, string> = {
    ts: '📘',
    js: '📒',
    vue: '💚',
    kt: '🟣',
    java: '☕',
    py: '🐍',
    md: '📝',
    json: '📋',
    yaml: '📋',
    yml: '📋',
    xml: '📋',
    html: '🌐',
    css: '🎨',
    scss: '🎨',
    less: '🎨',
    gradle: '🐘',
    kts: '🐘',
  }
  return iconMap[fileType?.toLowerCase()] || '📄'
}

// 处理搜索输入
function handleSearchInput() {
  emit('search', searchQuery.value)
  selectedIndex.value = 0
}

// 键盘导航
function handleKeyDown(event: KeyboardEvent) {
  if (!props.visible) {
    return
  }

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      if (props.files.length > 0) {
        selectedIndex.value = Math.min(selectedIndex.value + 1, props.files.length - 1)
      }
      break
    case 'ArrowUp':
      event.preventDefault()
      if (props.files.length > 0) {
        selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
      }
      break
    case 'Enter':
      event.preventDefault()
      if (selectedIndex.value >= 0 && selectedIndex.value < props.files.length) {
        selectFile(props.files[selectedIndex.value])
      }
      break
    case 'Escape':
      event.preventDefault()
      emit('dismiss')
      break
  }
}

// 全局键盘监听（用于没有搜索框时）
function handleGlobalKeyDown(event: KeyboardEvent) {
  if (!props.visible) {
    return
  }

  // 如果有搜索框，由搜索框处理键盘事件
  if (props.showSearchInput) {
    return
  }

  handleKeyDown(event)
}

// 点击外部关闭
function handleClickOutside(event: MouseEvent) {
  if (!props.visible) {
    return
  }

  const target = event.target as Node
  if (popupRef.value && !popupRef.value.contains(target)) {
    emit('dismiss')
  }
}

// 监听 visible 变化，重置选择索引和搜索
watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    selectedIndex.value = 0
    searchQuery.value = ''
    // 自动聚焦搜索框
    if (props.showSearchInput) {
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    }
  }
})

// 监听 files 变化，重置选择索引
watch(() => props.files, () => {
  selectedIndex.value = 0
})

onMounted(() => {
  document.addEventListener('keydown', handleGlobalKeyDown)
  document.addEventListener('mousedown', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleGlobalKeyDown)
  document.removeEventListener('mousedown', handleClickOutside)
})
</script>

<style scoped>
.file-select-popup {
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-width: 500px;
  max-height: 300px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.popup-search {
  padding: 8px;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
  flex-shrink: 0;
}

.search-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 13px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  box-sizing: border-box;
}

.search-input:focus {
  outline: none;
  border-color: var(--theme-accent, #0366d6);
}

.search-input::placeholder {
  color: var(--theme-text-disabled, #6a737d);
}

.file-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.1s;
  gap: 6px;
  height: 28px;
  line-height: 28px;
}

.file-item:hover,
.file-item.selected {
  background: var(--theme-hover-background, #f6f8fa);
}

.file-icon {
  font-size: 14px;
  flex-shrink: 0;
  width: 18px;
  text-align: center;
}

.file-name {
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  font-weight: 500;
  white-space: nowrap;
  flex-shrink: 0;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-path {
  font-size: 12px;
  color: var(--theme-text-secondary, #6a737d);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
  opacity: 0.7;
}

.no-results {
  padding: 12px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--theme-text-secondary, #6a737d);
}
</style>
