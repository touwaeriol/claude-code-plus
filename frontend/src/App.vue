<template>
  <div
    class="app"
    :class="{ 'theme-dark': isDark }"
  >
    <!-- 完整的 ModernChatView 组件 -->
    <ModernChatView
      :session-id="sessionId"
      :project-path="projectPath"
      class="main-chat-view"
    />

    <!-- 调试信息（可选） -->
    <div
      v-if="showDebug"
      class="debug-panel"
    >
      <div
        class="debug-title"
        @click="debugExpanded = !debugExpanded"
      >
        🐛 调试信息 {{ debugExpanded ? '▼' : '▶' }}
      </div>
      <div
        v-show="debugExpanded"
        class="debug-content"
      >
        <div class="debug-item">
          桥接: {{ bridgeReady ? '✅' : '⏳' }}
        </div>
        <div class="debug-item">
          主题: {{ isDark ? '🌙 暗色' : '☀️ 亮色' }}
        </div>
        <div class="debug-item">
          Session: {{ sessionId || '默认' }}
        </div>
        <div class="debug-item">
          项目路径: {{ projectPath }}
        </div>
        <button
          class="debug-button"
          @click="testBridge"
        >
          测试桥接
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ModernChatView from '@/components/chat/ModernChatView.vue'
import { ideaBridge } from '@/services/ideaBridge'
import { themeService } from '@/services/themeService'

const bridgeReady = ref(false)
const isDark = ref(false)
const showDebug = ref(true) // 可以改为 false 隐藏调试面板
const debugExpanded = ref(false)

// 会话ID和项目路径（可以从后端获取）
const sessionId = ref<string | undefined>(undefined)
const projectPath = ref<string>('') // 将从后端获取

onMounted(async () => {
  console.log('🚀 App mounted - ModernChatView loaded')

  try {
    await ideaBridge.waitForReady()
    bridgeReady.value = true
    console.log('✅ Bridge ready')

    // 初始化主题服务
    await themeService.initialize()

    // 监听主题变化
    themeService.onThemeChange((theme) => {
      isDark.value = theme.isDark
      console.log('🎨 Theme updated:', theme.isDark ? 'dark' : 'light')
    })

    // TODO: 从后端获取当前会话ID和项目路径
    // sessionId.value = await ideaBridge.getCurrentSessionId()
    // projectPath.value = await ideaBridge.getProjectPath()
  } catch (error) {
    console.error('❌ Failed to initialize:', error)
  }
})

function testBridge() {
  console.log('🧪 Testing bridge...')
  alert('桥接状态: ' + (bridgeReady.value ? '正常' : '未就绪'))
}

</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.app {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--ide-background, #f5f5f5);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.app.theme-dark {
  background: var(--ide-background, #1e1e1e);
  color: var(--ide-foreground, #e0e0e0);
}

/* 主聊天视图容器 */
.main-chat-view {
  flex: 1;
  overflow: hidden;
}

/* 调试面板 */
.debug-panel {
  position: fixed;
  bottom: 16px;
  right: 16px;
  background: var(--ide-panel-background, white);
  border: 1px solid var(--ide-border, #e0e0e0);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  overflow: hidden;
  max-width: 300px;
  z-index: 1000;
}

.theme-dark .debug-panel {
  background: var(--ide-panel-background, #252525);
  border-color: var(--ide-border, #3c3c3c);
}

.debug-title {
  padding: 12px 16px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  background: var(--ide-hover-background, #f8f9fa);
}

.theme-dark .debug-title {
  background: var(--ide-hover-background, #2a2a2a);
}

.debug-title:hover {
  background: var(--ide-hover-background, #e9ecef);
}

.theme-dark .debug-title:hover {
  background: var(--ide-hover-background, #323232);
}

.debug-content {
  padding: 12px 16px;
  font-size: 13px;
  border-top: 1px solid var(--ide-border, #e0e0e0);
}

.theme-dark .debug-content {
  border-top-color: var(--ide-border, #3c3c3c);
}

.debug-item {
  margin-bottom: 8px;
}

.debug-button {
  margin-top: 8px;
  padding: 6px 12px;
  font-size: 12px;
  border: 1px solid var(--ide-accent, #007bff);
  border-radius: 4px;
  background: transparent;
  color: var(--ide-accent, #007bff);
  cursor: pointer;
}

.debug-button:hover {
  background: var(--ide-accent, #007bff);
  color: white;
}
</style>
