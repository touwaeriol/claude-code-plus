/**
 * 语言服务 - 管理应用语言设置
 * 从后端获取和设置 IDE 语言设置，持久化到 IDEA 配置
 */

import { ideService } from './ideaBridge'

export type SupportedLocale = 'zh-CN' | 'en-US'

/**
 * 语言服务类
 */
class LocaleService {
  private currentLocale: SupportedLocale = 'en-US'
  private isInitialized = false
  private initPromise: Promise<void> | null = null

  /**
   * 初始化语言服务
   * 从后端获取 IDE 语言设置
   */
  async init(): Promise<void> {
    if (this.isInitialized) {
      return
    }

    if (this.initPromise) {
      return this.initPromise
    }

    this.initPromise = this.doInit()
    await this.initPromise
    this.isInitialized = true
  }

  private async doInit(): Promise<void> {
    try {
      const response = await ideService.getLocale()
      if (response.success && response.data) {
        const locale = this.normalizeLocale(response.data as string)
        this.currentLocale = locale
        console.log('🌐 Locale initialized from IDE:', locale)
      } else {
        // 回退到浏览器语言
        this.currentLocale = this.detectBrowserLocale()
        console.log('🌐 Locale fallback to browser:', this.currentLocale)
      }
    } catch (error) {
      console.warn('⚠️ Failed to get locale from IDE, using browser default:', error)
      this.currentLocale = this.detectBrowserLocale()
    }
  }

  /**
   * 标准化语言代码
   */
  private normalizeLocale(locale: string): SupportedLocale {
    const normalized = locale.toLowerCase().replace('_', '-')
    if (normalized.startsWith('zh')) {
      return 'zh-CN'
    }
    if (normalized.startsWith('en')) {
      return 'en-US'
    }
    // 默认返回英文
    return 'en-US'
  }

  /**
   * 检测浏览器语言
   */
  private detectBrowserLocale(): SupportedLocale {
    if (typeof window === 'undefined') {
      return 'en-US'
    }

    const browserLang = navigator.language || (navigator as any).userLanguage || 'en-US'
    return this.normalizeLocale(browserLang)
  }

  /**
   * 获取当前语言
   */
  getLocale(): SupportedLocale {
    return this.currentLocale
  }

  /**
   * 设置语言（持久化到 IDE）
   */
  async setLocale(locale: SupportedLocale): Promise<void> {
    try {
      const response = await ideService.setLocale(locale)
      if (response.success) {
        this.currentLocale = locale
        console.log('🌐 Locale changed to:', locale)
      } else {
        console.warn('⚠️ Failed to set locale:', response.error)
        // 即使后端失败，也更新前端状态
        this.currentLocale = locale
      }
    } catch (error) {
      console.warn('⚠️ Failed to set locale:', error)
      // 即使后端失败，也更新前端状态
      this.currentLocale = locale
    }
  }

  /**
   * 获取语言代码（用于 Element Plus 等库）
   */
  getElementPlusLocale(): string {
    return this.currentLocale === 'zh-CN' ? 'zh-cn' : 'en'
  }

  /**
   * 获取语言代码（用于 vue-i18n）
   */
  getI18nLocale(): string {
    return this.currentLocale
  }

  /**
   * 是否为中文
   */
  isChinese(): boolean {
    return this.currentLocale === 'zh-CN'
  }

  /**
   * 是否为英文
   */
  isEnglish(): boolean {
    return this.currentLocale === 'en-US'
  }
}

// 单例
const localeService = new LocaleService()

export default localeService

