import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import App from './App.vue'
import './styles/global.css'
import { resolveServerHttpUrl } from '@/utils/serverUrl'
import localeService from '@/services/localeService'

console.log('🚀 Initializing Vue application...')

if (!(window as any).__serverUrl) {
  ;(window as any).__serverUrl = resolveServerHttpUrl()
  console.log('🔧 Bootstrap: Backend URL resolved to', (window as any).__serverUrl)
}

async function initApp() {
  // 初始化语言服务
  await localeService.init()
  const locale = localeService.getElementPlusLocale()
  const elementPlusLocale = locale === 'zh-cn' ? zhCn : en

  const app = createApp(App)
  const pinia = createPinia()

  app.use(pinia)
  app.use(ElementPlus, {
    locale: elementPlusLocale,
    size: 'default',
    zIndex: 3000
  })

  app.mount('#app')

  console.log('✅ Vue application mounted with locale:', localeService.getLocale())
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
