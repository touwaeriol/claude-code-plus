<template>
  <!-- @ 符号文件选择弹窗 - 不显示搜索框 -->
  <FileSelectPopup
    :visible="visible"
    :files="files"
    :anchor-element="anchorElement"
    :show-search-input="false"
    :is-indexing="isIndexing"
    @select="handleSelect"
    @dismiss="handleDismiss"
  />
</template>

<script setup lang="ts">
import FileSelectPopup from './FileSelectPopup.vue'
import type { IndexedFileInfo } from '@/services/fileSearchService'

defineProps<{
  visible: boolean
  files: IndexedFileInfo[]
  anchorElement: HTMLElement | null
  atPosition: number
  isIndexing?: boolean  // 是否正在索引
}>()

const emit = defineEmits<{
  select: [file: IndexedFileInfo]
  dismiss: []
}>()

function handleSelect(file: IndexedFileInfo) {
  emit('select', file)
}

function handleDismiss() {
  emit('dismiss')
}
</script>
