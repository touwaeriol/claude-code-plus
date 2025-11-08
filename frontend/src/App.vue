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
          <strong>运行模式:</strong> {{ currentMode }}
        </div>
        <div class="debug-item">
          <strong>桥接状态:</strong> {{ bridgeReady ? '✅ 已连接' : '⏳ 连接中' }}
        </div>
        <div class="debug-item">
          <strong>主题模式:</strong> {{ isDark ? '🌙 暗色' : '☀️ 亮色' }}
        </div>
        <div class="debug-item">
          <strong>HTML Class:</strong> {{ htmlClasses }}
        </div>
        <div class="debug-item">
          <strong>主题服务:</strong> {{ themeServiceStatus }}
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
import { ideaBridge } from '@/services/ideaBridge'
import { themeService } from '@/services/themeService'

const bridgeReady = ref(false)
const isDark = ref(false)
const showDebug = ref(true) // 可以改为 false 隐藏调试面板
const debugExpanded = ref(false)
const currentMode = ref('unknown')
const htmlClasses = ref('')
const themeServiceStatus = ref('未初始化')

// 会话ID和项目路径（可以从后端获取）
const sessionId = ref<string | undefined>(undefined)
const projectPath = ref<string>('') // 将从后端获取

onMounted(async () => {
  console.log('🚀 App mounted - ModernChatView loaded')

  // 更新 HTML class 显示
  const updateHtmlClasses = () => {
    htmlClasses.value = document.documentElement.className || '(空)'
  }
  updateHtmlClasses()
  setInterval(updateHtmlClasses, 1000) // 每秒更新一次

  try {
    await ideaBridge.waitForReady()
    bridgeReady.value = true
    
    const mode = ideaBridge.getMode()
    currentMode.value = mode
    console.log(`✅ Bridge ready, mode: ${mode}`)

    // 两种模式都使用相同的主题初始化逻辑
    console.log('🎨 Initializing theme service...')
    themeServiceStatus.value = '初始化中...'
    
    // 初始化主题服务（会通过 HTTP 获取初始主题）
    await themeService.initialize()
    themeServiceStatus.value = '已激活'
    console.log('✅ Theme service initialized')

    // 监听主题变化（JCEF 通过回调，HTTP 通过 SSE）
    themeService.onThemeChange((theme) => {
      isDark.value = theme.isDark
      console.log('🎨 Theme updated:', theme.isDark ? 'dark' : 'light')
      
      // 为 Element Plus 添加/移除 dark class
      if (theme.isDark) {
        document.documentElement.classList.add('dark')
        console.log('✅ Added "dark" class to <html>')
      } else {
        document.documentElement.classList.remove('dark')
        console.log('✅ Removed "dark" class from <html>')
      }
      updateHtmlClasses()
      themeServiceStatus.value = `已激活 (${theme.isDark ? '暗色' : '亮色'})`
    })
    
    // 获取当前主题并应用
    const currentTheme = themeService.getCurrentTheme()
    if (currentTheme) {
      console.log('📋 Current theme:', currentTheme)
      isDark.value = currentTheme.isDark
      if (currentTheme.isDark) {
        document.documentElement.classList.add('dark')
      }
    }

    // TODO: 从后端获取当前会话ID和项目路径
    // sessionId.value = await ideaBridge.getCurrentSessionId()
    // projectPath.value = await ideaBridge.getProjectPath()
  } catch (error) {
    console.error('❌ Failed to initialize:', error)
    themeServiceStatus.value = `错误: ${error}`
  }
})

function testBridge() {
  console.log('🧪 Testing bridge...')
  alert('桥接状态: ' + (bridgeReady.value ? '正常' : '未就绪'))
}

async function testTheme() {
  console.log('🧪 Testing theme...')
  try {
    const response = await ideaBridge.query('ide.getTheme')
    console.log('🎨 Theme response:', response)
    alert(`主题信息:\n${JSON.stringify(response, null, 2)}`)
  } catch (error) {
    console.error('❌ Theme test error:', error)
    alert(`主题测试失败: ${error}`)
  }
}

function toggleTheme() {
  console.log('🔄 Manually toggling theme...')
  isDark.value = !isDark.value
  
  if (isDark.value) {
    document.documentElement.classList.add('dark')
    document.documentElement.classList.add('theme-dark')
  } else {
    document.documentElement.classList.remove('dark')
    document.documentElement.classList.remove('theme-dark')
  }
  
  console.log(`✅ Theme toggled to: ${isDark.value ? 'dark' : 'light'}`)
  alert(`主题已切换为: ${isDark.value ? '暗色' : '亮色'}`)
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
  color: var(--ide-accent, #007bff);
}

.debug-button {
  margin-top: 8px;
  margin-right: 6px;
  padding: 6px 12px;
  font-size: 11px;
  border: 1px solid var(--ide-accent, #007bff);
  border-radius: 4px;
  background: transparent;
  color: var(--ide-accent, #007bff);
  cursor: pointer;
  transition: all 0.2s;
}

.debug-button:hover {
  background: var(--ide-accent, #007bff);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 123, 255, 0.3);
}
</style>
