<template>
  <div class="model-table-container">
    <el-table
      :data="models"
      border
      @current-change="handleCurrentChange"
      highlight-current-row
      style="width: 100%"
      max-height="200"
    >
      <el-table-column prop="displayName" label="Display Name" />
      <el-table-column prop="modelId" label="Model ID" />
    </el-table>
    <div class="toolbar">
      <el-button size="small" @click="handleAdd">Add</el-button>
      <el-button size="small" @click="handleEdit" :disabled="!currentRow">Edit</el-button>
      <el-button size="small" @click="handleRemove" :disabled="!currentRow">Remove</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

interface ModelItem {
  displayName: string
  modelId: string
}

interface Props {
  models: ModelItem[]
}

defineProps<Props>()

const emit = defineEmits<{
  'add': []
  'edit': [model: ModelItem]
  'remove': [model: ModelItem]
}>()

const currentRow = ref<ModelItem | null>(null)

const handleCurrentChange = (row: ModelItem | null) => {
  currentRow.value = row
}

const handleAdd = () => {
  emit('add')
}

const handleEdit = () => {
  if (currentRow.value) {
    emit('edit', currentRow.value)
  }
}

const handleRemove = () => {
  if (currentRow.value) {
    emit('remove', currentRow.value)
  }
}
</script>

<style scoped>
.model-table-container {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
