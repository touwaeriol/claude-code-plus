import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import './styles/global.css'
import { resolveServerHttpUrl } from '@/utils/serverUrl'

console.log('🚀 Initializing Vue application...')

if (!(window as any).__serverUrl) {
  ;(window as any).__serverUrl = resolveServerHttpUrl()
  console.log('🔧 Bootstrap: Backend URL resolved to', (window as any).__serverUrl)
}

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(ElementPlus, {
  locale: zhCn,
  size: 'default',
  zIndex: 3000
})

app.mount('#app')

console.log('✅ Vue application mounted')
