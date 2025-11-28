/**
 * vue-i18n 配置
 */
import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'
import koKR from './locales/ko-KR'
import jaJP from './locales/ja-JP'

export type SupportedLocale = 'zh-CN' | 'en-US' | 'ko-KR' | 'ja-JP'

const LOCALE_STORAGE_KEY = 'claude-code-plus-locale'
const SUPPORTED_LOCALES: SupportedLocale[] = ['zh-CN', 'en-US', 'ko-KR', 'ja-JP']

// 检测浏览器语言
function detectBrowserLocale(): SupportedLocale {
  if (typeof window === 'undefined') return 'en-US'

  // 优先使用 localStorage 保存的语言
  const savedLocale = localStorage.getItem(LOCALE_STORAGE_KEY)
  if (savedLocale && SUPPORTED_LOCALES.includes(savedLocale as SupportedLocale)) {
    return savedLocale as SupportedLocale
  }

  // 其次检测浏览器语言
  const browserLang = navigator.language || (navigator as any).userLanguage || 'en-US'
  const normalized = browserLang.toLowerCase().replace('_', '-')

  if (normalized.startsWith('zh')) return 'zh-CN'
  if (normalized.startsWith('ko')) return 'ko-KR'
  if (normalized.startsWith('ja')) return 'ja-JP'
  return 'en-US'
}

export const i18n = createI18n({
  legacy: false, // 使用 Composition API 模式
  locale: detectBrowserLocale(),
  fallbackLocale: 'en-US',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
    'ko-KR': koKR,
    'ja-JP': jaJP
  }
})

// 导出便捷方法
export function setLocale(locale: SupportedLocale) {
  i18n.global.locale.value = locale
  // 持久化到 localStorage
  if (typeof window !== 'undefined') {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale)
  }
}

export function getLocale(): SupportedLocale {
  return i18n.global.locale.value as SupportedLocale
}
