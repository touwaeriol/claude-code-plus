import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import koKr from 'element-plus/es/locale/lang/ko'
import jaJp from 'element-plus/es/locale/lang/ja'
import App from './App.vue'
import './styles/global.css'
import { resolveServerHttpUrl } from '@/utils/serverUrl'
import { i18n, getLocale } from '@/i18n'
import { jcefBridge } from '@/services/jcefBridge'
import { toolEnhancement } from '@/services/toolEnhancement'

console.log('🚀 Initializing Vue application...')

// 在 JCEF 中首次渲染时，100vh 可能无法正确计算，使用 JS 动态设置实际高度
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

// JCEF 特殊处理：延迟触发多次 resize 以确保布局正确
// JCEF 初始化时可能 innerHeight 为 0，需要等待容器准备好
const jcefLayoutFix = () => {
  const delays = [50, 100, 200, 500, 1000]
  delays.forEach(delay => {
    setTimeout(() => {
      updateViewportHeight()
      // 强制触发 resize 事件让所有组件重新计算
      window.dispatchEvent(new Event('resize'))
    }, delay)
  })
}
jcefLayoutFix()

// 使用 ResizeObserver 监听 body 尺寸变化（比 resize 事件更可靠）
if (typeof ResizeObserver !== 'undefined') {
  const resizeObserver = new ResizeObserver(() => {
    updateViewportHeight()
  })
  resizeObserver.observe(document.body)
}

if (!(window as any).__serverUrl) {
  ;(window as any).__serverUrl = resolveServerHttpUrl()
  console.log('🔧 Bootstrap: Backend URL resolved to', (window as any).__serverUrl)
}

// 初始化 JCEF 桥接和工具增强拦截器
jcefBridge.init().then(() => {
  toolEnhancement.init()
  console.log('✅ JCEF Bridge and Tool Enhancement initialized')
}).catch(error => {
  console.error('❌ Failed to initialize JCEF Bridge:', error)
})

function getElementPlusLocale(locale: string) {
  const localeMap: Record<string, any> = {
    'zh-CN': zhCn,
    'en-US': en,
    'ko-KR': koKr,
    'ja-JP': jaJp
  }
  return localeMap[locale] || en
}

async function initApp() {
  const locale = getLocale()
  const elementPlusLocale = getElementPlusLocale(locale)

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

  console.log('✅ Vue application mounted with locale:', locale)
}

initApp().catch((error) => {
  console.error('❌ Failed to initialize app:', error)
  // 回退到默认配置
  const app = createApp(App)
  const pinia = createPinia()
  app.use(pinia)
  app.use(ElementPlus, {
    locale: en,
    size: 'default',
    zIndex: 3000
  })
  app.mount('#app')
})
