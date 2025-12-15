/**
 * vue-i18n 配置
 *
 * 语言同步策略：
 * - 浏览器环境：localStorage > 浏览器语言 > 默认 en-US
 * - IDEA 环境 + 主题同步：同步 IDEA 语言设置
 * - IDEA 环境 + 无主题同步：使用 localStorage（用户自选）
 */
import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import enUS from './locales/en-US'
import koKR from './locales/ko-KR'
import jaJP from './locales/ja-JP'

export type SupportedLocale = 'zh-CN' | 'en-US' | 'ko-KR' | 'ja-JP'

const LOCALE_STORAGE_KEY = 'claude-code-plus-locale'
const SUPPORTED_LOCALES: SupportedLocale[] = ['zh-CN', 'en-US', 'ko-KR', 'ja-JP']

/**
 * 将任意语言代码标准化为支持的语言
 */
export function normalizeLocale(locale: string): SupportedLocale {
  const normalized = locale.toLowerCase().replace('_', '-')

  if (normalized.startsWith('zh')) return 'zh-CN'
  if (normalized.startsWith('ko')) return 'ko-KR'
  if (normalized.startsWith('ja')) return 'ja-JP'
  return 'en-US'
}

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
  return normalizeLocale(browserLang)
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

/**
 * 同步 IDE 语言设置
 *
 * 在 IDEA 环境中，当主题同步开启时调用此函数
 * 会从 IDEA 获取语言设置并应用到前端
 *
 * @returns 同步后的语言，如果同步失败则返回 null
 */
export async function syncIdeLocale(): Promise<SupportedLocale | null> {
  try {
    // 动态导入避免循环依赖
    const { jetbrainsBridge } = await import('./services/jetbrainsApi')

    if (!jetbrainsBridge.isEnabled()) {
      console.log('🌐 [i18n] IDE bridge not enabled, skip sync')
      return null
    }

    const ideLocale = await jetbrainsBridge.getLocale()
    if (!ideLocale) {
      console.log('🌐 [i18n] Failed to get IDE locale')
      return null
    }

    const normalizedLocale = normalizeLocale(ideLocale)
    console.log(`🌐 [i18n] Syncing IDE locale: ${ideLocale} -> ${normalizedLocale}`)

    // 应用语言（不保存到 localStorage，因为是从 IDE 同步的）
    i18n.global.locale.value = normalizedLocale

    return normalizedLocale
  } catch (error) {
    console.error('🌐 [i18n] Failed to sync IDE locale:', error)
    return null
  }
}
