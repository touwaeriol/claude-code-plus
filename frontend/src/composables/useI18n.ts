/**
 * 国际化 composable
 * 提供翻译函数和当前语言状态
 */

import { computed } from 'vue'
import localeService from '@/services/localeService'
import translations from '@/locales'

export function useI18n() {
  /**
   * 获取当前语言
   */
  const locale = computed(() => localeService.getLocale())

  /**
   * 翻译函数
   * @param key 翻译键，支持点号分隔的嵌套路径，如 'common.send' 或 'chat.placeholder'
   * @param params 可选的参数对象，用于替换占位符
   * @returns 翻译后的文本
   */
  function t(key: string, params?: Record<string, string | number>): string {
    const currentLocale = locale.value
    const translationMap = translations[currentLocale] || translations['en-US']

    // 支持嵌套键，如 'common.send' 或 'tools.status.running'
    const keys = key.split('.')
    let value: any = translationMap

    for (const k of keys) {
      if (value && typeof value === 'object' && k in value) {
        value = value[k]
      } else {
        // 如果找不到翻译，返回键本身（开发时便于发现缺失的翻译）
        console.warn(`[i18n] Missing translation for key: ${key} (locale: ${currentLocale})`)
        return key
      }
    }

    // 如果最终值不是字符串，返回键
    if (typeof value !== 'string') {
      console.warn(`[i18n] Translation value is not a string for key: ${key}`)
      return key
    }

    // 替换参数占位符，支持 {name} 格式
    if (params) {
      return value.replace(/\{(\w+)\}/g, (match, paramKey) => {
        return params[paramKey]?.toString() || match
      })
    }

    return value
  }

  /**
   * 检查翻译键是否存在
   */
  function hasKey(key: string): boolean {
    const currentLocale = locale.value
    const translationMap = translations[currentLocale] || translations['en-US']
    const keys = key.split('.')
    let value: any = translationMap

    for (const k of keys) {
      if (value && typeof value === 'object' && k in value) {
        value = value[k]
      } else {
        return false
      }
    }

    return typeof value === 'string'
  }

  return {
    locale,
    t,
    hasKey,
    isChinese: computed(() => localeService.isChinese()),
    isEnglish: computed(() => localeService.isEnglish())
  }
}

