<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <span class="title">Settings</span>
      <span class="subtitle">Claude Code Plus</span>
    </div>
    
    <el-menu
      :default-active="activePage"
      @select="handleSelect"
    >
      <el-menu-item index="claude">
        <el-icon><Promotion /></el-icon>
        <span>Claude Code</span>
      </el-menu-item>
      <el-menu-item index="codex">
        <el-icon><Monitor /></el-icon>
        <span>Codex</span>
      </el-menu-item>
      <el-menu-item index="git">
        <el-icon><Document /></el-icon>
        <span>Git Generate</span>
      </el-menu-item>
      <el-menu-item index="mcp">
        <el-icon><Connection /></el-icon>
        <span>MCP</span>
      </el-menu-item>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { Promotion, Monitor, Document, Connection } from '@element-plus/icons-vue'

interface Props {
  activePage: string
}

defineProps<Props>()
const emit = defineEmits<{
  'update:activePage': [value: string]
}>()

const handleSelect = (index: string) => {
  emit('update:activePage', index)
}
</script>

<style scoped>
.sidebar {
  width: 200px;
  min-width: 200px;
  background: var(--vscode-sideBar-background, #252526);
  border-right: 1px solid var(--vscode-panel-border, #3c3c3c);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.sidebar-header {
  padding: 12px 16px;
}

.title {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--vscode-sideBarSectionHeader-foreground, #bbbbbb);
  opacity: 0.7;
}

.subtitle {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--vscode-foreground, #cccccc);
  margin-top: 4px;
}
</style>
