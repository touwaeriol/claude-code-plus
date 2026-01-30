<template>
  <div class="settings-container">
    <Sidebar 
      :active-page="activePage" 
      @update:active-page="activePage = $event" 
    />
    <div class="content">
      <ClaudeCodePage v-if="activePage === 'claude'" />
      <CodexPage v-if="activePage === 'codex'" />
      <GitGeneratePage v-if="activePage === 'git'" />
      <McpPage v-if="activePage === 'mcp'" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Sidebar from '@/components/Sidebar.vue'
import ClaudeCodePage from '@/pages/ClaudeCodePage.vue'
import CodexPage from '@/pages/CodexPage.vue'
import GitGeneratePage from '@/pages/GitGeneratePage.vue'
import McpPage from '@/pages/McpPage.vue'
import { useSettingsStore } from '@/stores/settingsStore'
import { vscode } from '@/utils/vscodeApi'

const activePage = ref('claude')
const settings = useSettingsStore()

onMounted(() => {
  // 加载设置
  settings.loadSettings()
  
  // 监听来自扩展的消息
  vscode.onMessage((event) => {
    const message = event.data
    
    switch (message.type) {
      case 'settingsLoaded':
        // 更新设置
        if (message.payload.claude) {
          Object.assign(settings.claude, message.payload.claude)
        }
        if (message.payload.codex) {
          Object.assign(settings.codex, message.payload.codex)
        }
        if (message.payload.gitGenerate) {
          Object.assign(settings.gitGenerate, message.payload.gitGenerate)
        }
        if (message.payload.mcp) {
          Object.assign(settings.mcp, message.payload.mcp)
        }
        // 标记加载完成，启用自动保存
        settings.onSettingsLoaded()
        break
        
      case 'nodeDetected':
        // Node.js 检测结果
        settings.handleDetectionResult('node', message.payload)
        break
        
      case 'codexDetected':
        // Codex 检测结果
        settings.handleDetectionResult('codex', message.payload)
        break
        
      case 'fileSelected':
        // 处理文件选择结果
        const { settingKey, path } = message.payload
        if (settingKey === 'claude.nodePath') {
          settings.claude.nodePath = path
        } else if (settingKey === 'codex.codexPath') {
          settings.codex.codexPath = path
        }
        break
    }
  })
})
</script>

<style scoped>
.settings-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  background: var(--vscode-editor-background, #1e1e1e);
}
</style>
