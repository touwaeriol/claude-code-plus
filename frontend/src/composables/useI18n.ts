/**
 * 国际化 composable
 * 基于 vue-i18n，保持现有组件 API 兼容
 */

import { useI18n as vueUseI18n } from 'vue-i18n'
import { computed } from 'vue'

export function useI18n() {
  const { t, locale } = vueUseI18n()

  return {
    t,
    locale,
    /**
     * 检查翻译键是否存在
     */
    hasKey(key: string): boolean {
      const result = t(key)
      return result !== key
    },
    isChinese: computed(() => locale.value === 'zh-CN'),
    isEnglish: computed(() => locale.value === 'en-US'),
    isKorean: computed(() => locale.value === 'ko-KR'),
    isJapanese: computed(() => locale.value === 'ja-JP')
  }
}
