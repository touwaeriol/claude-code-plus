<template>
  <div class="navbar">
    <el-menu
      :default-active="activePage"
      mode="horizontal"
      :ellipsis="false"
      @select="handleSelect"
    >
      <el-menu-item index="claude">
        <el-icon><Promotion /></el-icon>
        <span>Claude</span>
      </el-menu-item>
      <el-menu-item index="codex">
        <el-icon><Monitor /></el-icon>
        <span>Codex</span>
      </el-menu-item>
      <el-menu-item index="git">
        <el-icon><Document /></el-icon>
        <span>Git</span>
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
.navbar {
  display: flex;
  align-items: center;
  padding: 0 8px;
  background: var(--vscode-sideBar-background, #252526);
  border-bottom: 1px solid var(--vscode-panel-border, #3c3c3c);
  height: 40px;
}

/* 横向菜单样式 */
:deep(.el-menu) {
  border-bottom: none;
  background: transparent;
  height: 40px;
}

:deep(.el-menu--horizontal > .el-menu-item) {
  height: 40px;
  line-height: 40px;
  font-size: 13px;
  padding: 0 12px;
  border-bottom: 2px solid transparent;
  color: var(--vscode-foreground, #cccccc);
}

:deep(.el-menu--horizontal > .el-menu-item:hover) {
  background: var(--vscode-list-hoverBackground, #2a2d2e);
}

:deep(.el-menu--horizontal > .el-menu-item.is-active) {
  border-bottom-color: var(--vscode-focusBorder, #007acc);
  color: var(--vscode-foreground, #ffffff);
}

:deep(.el-menu-item .el-icon) {
  margin-right: 4px;
  font-size: 14px;
}
</style>
