// Buffer polyfill for rsocket (browser compatibility)
import { Buffer } from 'buffer'
;(globalThis as any).Buffer = Buffer

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import zhTw from 'element-plus/es/locale/lang/zh-tw'
import en from 'element-plus/es/locale/lang/en'
import koKr from 'element-plus/es/locale/lang/ko'
import jaJp from 'element-plus/es/locale/lang/ja'
import App from './App.vue'
import './styles/global.css'
import { resolveServerHttpUrl } from '@/utils/serverUrl'
import { i18n, getLocale } from '@/i18n'
import { initScrollBoost } from '@/utils/scrollBoost'
import { initBrowserSecurity, registerOpenFileCallback, setTranslateFunction } from '@/utils/browserSecurity'
import { ideaBridge } from '@/services/ideaBridge'

console.log('🚀 Initializing Vue application...')

// 🔒 初始化浏览器安全限制（禁止跳转、禁用默认右键菜单）
initBrowserSecurity()

// 📂 注册全局文件打开回调（用于处理文件链接点击）
registerOpenFileCallback((filePath) => {
  ideaBridge.query('ide.openFile', { filePath })
})

// 在 IDE 嵌入式浏览器中首次渲染时，100vh 可能无法正确计算，使用 JS 动态设置实际高度
const updateViewportHeight = () => {
  const height = window.innerHeight
  if (height > 0) {
    document.documentElement.style.setProperty('--app-viewport-height', `${height}px`)
    console.log(`📐 Viewport height updated: ${height}px`)
  }
}

// 初始更新
updateViewportHeight()

// 监听 resize 和 orientationchange
window.addEventListener('resize', updateViewportHeight)
window.addEventListener('orientationchange', updateViewportHeight)

// IDE 嵌入式浏览器特殊处理：延迟触发多次 resize 以确保布局正确
// 初始化时可能 innerHeight 为 0，需要等待容器准备好
const ideLayoutFix = () => {
  const delays = [50, 100, 200, 500, 1000]
  delays.forEach(delay => {
    setTimeout(() => {
      updateViewportHeight()
      // 强制触发 resize 事件让所有组件重新计算
      window.dispatchEvent(new Event('resize'))
    }, delay)
  })
}
ideLayoutFix()

// 使用 ResizeObserver 监听 body 尺寸变化（比 resize 事件更可靠）
if (typeof ResizeObserver !== 'undefined') {
  const resizeObserver = new ResizeObserver(() => {
    updateViewportHeight()
  })
  resizeObserver.observe(document.body)
}

const anyWindow = window as any
if (!anyWindow.__serverUrl && !anyWindow.__IDE_MODE__) {
  anyWindow.__serverUrl = resolveServerHttpUrl()
  console.log('🔧 Bootstrap: Backend URL resolved to', anyWindow.__serverUrl)
}
if (anyWindow.__IDE_MODE__ && !anyWindow.__serverUrl) {
  console.error('❌ IDE 模式缺少 window.__serverUrl 注入')
}

function getElementPlusLocale(locale: string) {
  const localeMap: Record<string, any> = {
    'zh-CN': zhCn,
    'zh-TW': zhTw,
    'en-US': en,
    'ko-KR': koKr,
    'ja-JP': jaJp
  }
  return localeMap[locale] || en
}

async function initApp() {
  const locale = getLocale()
  const elementPlusLocale = getElementPlusLocale(locale)

  // 初始化滚动增强（根据 URL 参数 scrollMultiplier）
  initScrollBoost()

  // IDE integration init is handled by App.vue. Don't block mount here (VS Code webview could go blank).
  const ideIntegrationEnabled = false

  const app = createApp(App)
  const pinia = createPinia()

  app.use(pinia)
  app.use(i18n)  // 注册 vue-i18n
  app.use(ElementPlus, {
    locale: elementPlusLocale,
    size: 'default',
    zIndex: 3000
  })

  app.mount('#app')

  // 🌐 设置 browserSecurity 的翻译函数（与 Vue i18n 集成）
  setTranslateFunction((key: string) => i18n.global.t(key) as string)

  console.log('✅ Vue application mounted with locale:', locale)
  if (ideIntegrationEnabled) {
    console.log('✅ IDE integration enabled')
  }
}

initApp().catch((error) => {
  console.error('❌ Failed to initialize app:', error)
  // 回退到默认配置
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)
  app.use(i18n)  // 确保 i18n 也被注册
  app.use(ElementPlus, {
    locale: en,
    size: 'default',
    zIndex: 3000
  })
  app.mount('#app')
})
