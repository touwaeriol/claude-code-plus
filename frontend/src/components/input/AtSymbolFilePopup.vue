<template>
  <Teleport to="body">
    <div
      v-if="visible && files.length > 0"
      ref="popupRef"
      class="at-symbol-popup"
      :style="popupStyle"
      @mousedown.prevent
    >
      <div class="popup-content">
        <div class="file-list">
          <div
            v-for="(file, index) in files"
            :key="file.absolutePath"
            :class="['file-item', { selected: index === selectedIndex }]"
            @click="selectFile(file)"
            @mouseenter="selectedIndex = index"
          >
            <div class="file-icon">📄</div>
            <div class="file-info">
              <div class="file-name">{{ file.name }}</div>
              <div class="file-path">{{ file.relativePath }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import type { IndexedFileInfo } from '@/services/fileSearchService'

const props = defineProps<{
  visible: boolean
  files: IndexedFileInfo[]
  anchorElement: HTMLElement | null
  atPosition: number
}>()

const emit = defineEmits<{
  select: [file: IndexedFileInfo]
  dismiss: []
}>()

const popupRef = ref<HTMLElement | null>(null)
const selectedIndex = ref(0)

// 计算弹窗位置
const popupStyle = computed(() => {
  if (!props.anchorElement) {
    return {
      display: 'none'
    }
  }

  const rect = props.anchorElement.getBoundingClientRect()
  
  // 弹窗显示在输入框下方
  return {
    position: 'fixed' as const,
    left: `${rect.left}px`,
    top: `${rect.bottom + 4}px`,
    zIndex: 10000
  }
})

// 选择文件
function selectFile(file: IndexedFileInfo) {
  emit('select', file)
  selectedIndex.value = 0
}

// 键盘导航
function handleKeyDown(event: KeyboardEvent) {
  if (!props.visible || props.files.length === 0) {
    return
  }

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      selectedIndex.value = Math.min(selectedIndex.value + 1, props.files.length - 1)
      break
    case 'ArrowUp':
      event.preventDefault()
      selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
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

// 监听 visible 变化，重置选择索引
watch(() => props.visible, (newVisible) => {
  if (newVisible) {
    selectedIndex.value = 0
  }
})

// 监听 files 变化，重置选择索引
watch(() => props.files, () => {
  selectedIndex.value = 0
})

onMounted(() => {
  document.addEventListener('keydown', handleKeyDown)
  document.addEventListener('mousedown', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
  document.removeEventListener('mousedown', handleClickOutside)
})
</script>

<style scoped>
.at-symbol-popup {
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  min-width: 300px;
  max-width: 500px;
  max-height: 300px;
  overflow: hidden;
}

.popup-content {
  padding: 4px;
}

.file-list {
  max-height: 292px;
  overflow-y: auto;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 3px;
  transition: background-color 0.1s;
}

.file-item:hover,
.file-item.selected {
  background: var(--ide-hover-background, #f6f8fa);
}

.file-icon {
  font-size: 16px;
  margin-right: 8px;
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-size: 13px;
  color: var(--ide-foreground, #24292e);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-path {
  font-size: 11px;
  color: var(--ide-text-secondary, #6a737d);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}
</style>

