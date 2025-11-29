<template>
  <div class="app">
    <!-- 测试模式：显示 TestDisplayItems -->
    <TestDisplayItems v-if="showTest" />

    <!-- 完整的 ModernChatView 组件 -->
    <ModernChatView
      v-else
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
        调试信息 {{ debugExpanded ? '▼' : '▶' }}
      </div>
      <div
        v-show="debugExpanded"
        class="debug-content"
      >
        <div class="debug-item">
          <strong>运行模式:</strong> {{ currentMode }}
        </div>
        <div class="debug-item">
          <strong>桥接状态:</strong> {{ bridgeReady ? '已连接' : '连接中' }}
        </div>
        <div class="debug-item">
          <strong>主题来源:</strong> {{ themeSource }}
        </div>
        <div class="debug-item">
          <strong>Session:</strong> {{ sessionId || '默认' }}
        </div>
        <div class="debug-item">
          <strong>项目路径:</strong> {{ projectPath || '未设置' }}
        </div>
        <button
          class="debug-button"
          @click="testBridge"
        >
          测试桥接
        </button>
        <button
          class="debug-button"
          @click="testTheme"
        >
          测试主题
        </button>
        <button
          v-if="!themeService.hasIde()"
          class="debug-button"
          @click="toggleTheme"
        >
          切换主题
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import ModernChatView from '@/components/chat/ModernChatView.vue'
import TestDisplayItems from '@/views/TestDisplayItems.vue'
import { ideaBridge } from '@/services/ideaBridge'
import { themeService } from '@/services/themeService'
import { useEnvironment } from '@/composables/useEnvironment'

const bridgeReady = ref(false)
const showDebug = ref(false) // 默认隐藏调试面板
const debugExpanded = ref(false)
const currentMode = ref('unknown')
const themeSource = ref('未初始化')

// 测试模式开关（URL 参数 ?test=1 启用）
const showTest = ref(new URLSearchParams(window.location.search).get('test') === '1')

// 会话ID和项目路径（可以从后端获取）
const sessionId = ref<string | undefined>(undefined)
const projectPath = ref<string>('')

const { detectEnvironment } = useEnvironment()

onMounted(async () => {
  console.log('App mounted - ModernChatView loaded')

  // 全局环境检测
  await detectEnvironment()

  try {
    await ideaBridge.waitForReady()
    bridgeReady.value = true

    const mode = ideaBridge.getMode()
    currentMode.value = mode
    console.log(`Bridge ready, mode: ${mode}`)

    // 初始化主题服务
    console.log('Initializing theme service...')
    themeSource.value = '初始化中...'

    await themeService.initialize()
    themeSource.value = themeService.hasIde() ? 'IDE' : 'Web (系统)'
    console.log('Theme service initialized')

    // 监听主题变化
    themeService.onThemeChange(() => {
      themeSource.value = themeService.hasIde() ? 'IDE' : 'Web (系统)'
      console.log('Theme updated')
    })

    // 从后端获取项目路径
    try {
      const response = await ideaBridge.query('ide.getProjectPath', {})
      if (response.success && response.data?.projectPath) {
        projectPath.value = response.data.projectPath as string
        console.log('Project path:', projectPath.value)
      }
    } catch (error) {
      console.error('Failed to get project path:', error)
      projectPath.value = '获取失败'
    }

    // 检查关键 DOM 元素的高度 (用于调试布局问题)
    setTimeout(() => {
      const app = document.getElementById('app')
      const messageList = document.querySelector('.message-list-wrapper')

      console.log('DOM 元素高度检查:')
      console.log('  #app:', app?.offsetHeight || 0, 'px')
      console.log('  .message-list-wrapper:', messageList?.offsetHeight || 0, 'px')

      if (!app || app.offsetHeight === 0) {
        console.error('#app 高度为 0 - 可能导致界面空白!')
      }
    }, 1000)
  } catch (error) {
    console.error('Failed to initialize:', error)
    themeSource.value = `错误: ${error}`
  }
})

function testBridge() {
  console.log('Testing bridge...')
  alert('桥接状态: ' + (bridgeReady.value ? '正常' : '未就绪'))
}

function testTheme() {
  console.log('Testing theme...')
  const theme = themeService.getCurrentTheme()
  if (theme) {
    alert(`主题信息:\n${JSON.stringify(theme, null, 2)}`)
  } else {
    alert('当前没有可用的主题数据')
  }
}

function toggleTheme() {
  console.log('Toggling theme...')
  themeService.toggleTheme()
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
  min-height: 0;
  background: var(--theme-background);
  color: var(--theme-foreground);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* 主聊天视图容器 */
.main-chat-view {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

/* 调试面板 */
.debug-panel {
  position: fixed;
  bottom: 16px;
  right: 16px;
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  overflow: hidden;
  max-width: 300px;
  z-index: 1000;
}

.debug-title {
  padding: 12px 16px;
  font-weight: 600;
  cursor: pointer;
  user-select: none;
  background: var(--theme-hover-background);
}

.debug-title:hover {
  opacity: 0.8;
}

.debug-content {
  padding: 12px 16px;
  font-size: 13px;
  border-top: 1px solid var(--theme-border);
}

.debug-item {
  margin-bottom: 6px;
  font-size: 12px;
  line-height: 1.5;
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.debug-item strong {
  min-width: 80px;
  flex-shrink: 0;
  color: var(--theme-accent);
}

.debug-button {
  margin-top: 8px;
  margin-right: 6px;
  padding: 6px 12px;
  font-size: 11px;
  border: 1px solid var(--theme-accent);
  border-radius: 4px;
  background: transparent;
  color: var(--theme-accent);
  cursor: pointer;
  transition: all 0.2s;
}

.debug-button:hover {
  background: var(--theme-accent);
  color: var(--theme-selection-foreground);
  transform: translateY(-1px);
}
</style>
