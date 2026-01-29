<template>
  <el-collapse v-model="activeNames" class="collapsible-group">
    <el-collapse-item :title="title" :name="name">
      <slot />
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  title: string
  name?: string
  defaultExpanded?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  name: 'default',
  defaultExpanded: false
})

const activeNames = ref<string[]>(props.defaultExpanded ? [props.name] : [])

watch(() => props.defaultExpanded, (val) => {
  if (val && !activeNames.value.includes(props.name)) {
    activeNames.value.push(props.name)
  }
})
</script>

<style scoped>
.collapsible-group {
  margin-bottom: 20px;
  border: 1px solid var(--vscode-panel-border, #3c3c3c);
  border-radius: 4px;
  overflow: hidden;
}

:deep(.el-collapse-item__header) {
  padding: 12px 16px;
  background: var(--vscode-sideBarSectionHeader-background, #333333);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

:deep(.el-collapse-item__content) {
  padding: 16px;
  background: var(--vscode-editorWidget-background, #252526);
}

:deep(.el-collapse-item__wrap) {
  border-top: 1px solid var(--vscode-panel-border, #3c3c3c);
}
</style>
