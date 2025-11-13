import { ideaBridge } from './ideaBridge'

/**
 * IDE 主题接口 - 与后端 BridgeProtocol.IdeTheme 保持一致
 */
export interface IdeTheme {
  isDark: boolean
  background: string
  foreground: string
  panelBackground: string
  borderColor: string
  textFieldBackground: string
  selectionBackground: string
  selectionForeground: string
  linkColor: string
  errorColor: string
  warningColor: string
  successColor: string
  separatorColor: string
  hoverBackground: string
  accentColor: string
  infoBackground: string
  codeBackground: string
  secondaryForeground: string
}

export class ThemeService {
  private currentTheme: IdeTheme | null = null
  private listeners: Set<(theme: IdeTheme) => void> = new Set()

  /**
   * 初始化主题服务
   */
  async initialize() {
    console.log('🎨 Initializing theme service...')

    // 获取初始主题
    await this.fetchAndApplyTheme()

    // 监听主题变化事件
    ideaBridge.on('theme.changed', (data) => {
      console.log('🎨 Theme changed event received:', data)
      if (data.theme) {
        this.applyTheme(data.theme)
      }
    })
  }

  /**
   * 获取并应用主题
   */
  async fetchAndApplyTheme() {
    try {
      const response = await ideaBridge.query('ide.getTheme')
      if (response.success && response.data?.theme) {
        this.applyTheme(response.data.theme)
      } else {
        console.warn('⚠️ Failed to fetch theme, using default')
        this.applyDefaultTheme()
      }
    } catch (error) {
      console.error('❌ Failed to fetch theme:', error)
      this.applyDefaultTheme()
    }
  }

  /**
   * 应用主题
   */
  private applyTheme(theme: IdeTheme) {
    console.log('🎨 Applying theme:', theme.isDark ? 'dark' : 'light')
    this.currentTheme = theme
    this.injectCssVariables(theme)
    this.notifyListeners(theme)
  }

  /**
   * 应用默认主题
   */
  private applyDefaultTheme() {
    const defaultTheme: IdeTheme = {
      isDark: false,
      background: '#ffffff',
      foreground: '#24292e',
      panelBackground: '#f6f8fa',
      borderColor: '#e1e4e8',
      textFieldBackground: '#ffffff',
      selectionBackground: '#0366d6',
      selectionForeground: '#ffffff',
      linkColor: '#0366d6',
      errorColor: '#d73a49',
      warningColor: '#ffc107',
      successColor: '#28a745',
      separatorColor: '#e1e4e8',
      hoverBackground: '#f6f8fa',
      accentColor: '#0366d6',
      infoBackground: '#f0f0f0',
      codeBackground: '#f6f8fa',
      secondaryForeground: '#6a737d'
    }
    this.applyTheme(defaultTheme)
  }

  /**
   * 注入 CSS 变量到文档根元素
   */
  private injectCssVariables(theme: IdeTheme) {
    const root = document.documentElement

    // 设置主题类
    if (theme.isDark) {
      root.classList.add('theme-dark')
      root.classList.remove('theme-light')
    } else {
      root.classList.add('theme-light')
      root.classList.remove('theme-dark')
    }

    // 注入 CSS 变量
    root.style.setProperty('--ide-background', theme.background)
    root.style.setProperty('--ide-foreground', theme.foreground)
    root.style.setProperty('--ide-panel-background', theme.panelBackground)
    root.style.setProperty('--ide-border', theme.borderColor)
    root.style.setProperty('--ide-text-field-background', theme.textFieldBackground)
    root.style.setProperty('--ide-selection-background', theme.selectionBackground)
    root.style.setProperty('--ide-selection-foreground', theme.selectionForeground)
    root.style.setProperty('--ide-link', theme.linkColor)
    root.style.setProperty('--ide-error', theme.errorColor)
    root.style.setProperty('--ide-warning', theme.warningColor)
    root.style.setProperty('--ide-success', theme.successColor)
    root.style.setProperty('--ide-accent', theme.linkColor) // 使用 linkColor 作为 accent

    // 代码相关颜色
    root.style.setProperty('--ide-code-background', theme.panelBackground)
    root.style.setProperty('--ide-code-foreground', theme.foreground)

    // 警告背景色（根据主题动态计算）
    const warningBg = theme.isDark ? '#3d3416' : '#fff8dc'
    root.style.setProperty('--ide-warning-background', warningBg)
    root.style.setProperty('--ide-separator', theme.separatorColor)
    root.style.setProperty('--ide-hover-background', theme.hoverBackground)
    root.style.setProperty('--ide-accent', theme.accentColor)
    root.style.setProperty('--ide-info-background', theme.infoBackground)
    root.style.setProperty('--ide-code-background', theme.codeBackground)
    root.style.setProperty('--ide-secondary-foreground', theme.secondaryForeground)

    // 兼容旧的 CSS 变量名
    root.style.setProperty('--ide-input-background', theme.textFieldBackground)
    root.style.setProperty('--ide-input-foreground', theme.foreground)
    root.style.setProperty('--ide-input-border', theme.borderColor)
    root.style.setProperty('--ide-code-foreground', theme.foreground)
    root.style.setProperty('--ide-button-background', theme.accentColor)
    root.style.setProperty('--ide-button-foreground', theme.selectionForeground)
    root.style.setProperty('--ide-button-hover-background', theme.selectionBackground)

    console.log('✅ CSS variables injected:', theme.isDark ? 'dark' : 'light')
  }

  /**
   * 监听主题变化
   */
  onThemeChange(listener: (theme: IdeTheme) => void) {
    this.listeners.add(listener)

    // 如果已有主题,立即通知
    if (this.currentTheme) {
      listener(this.currentTheme)
    }

    // 返回取消监听的函数
    return () => {
      this.listeners.delete(listener)
    }
  }

  /**
   * 通知所有监听器
   */
  private notifyListeners(theme: IdeTheme) {
    this.listeners.forEach(listener => {
      try {
        listener(theme)
      } catch (error) {
        console.error('❌ Theme listener error:', error)
      }
    })
  }

  /**
   * 获取当前主题
   */
  getCurrentTheme(): IdeTheme | null {
    return this.currentTheme
  }

  /**
   * 是否为暗色主题
   */
  isDarkTheme(): boolean {
    return this.currentTheme?.isDark ?? false
  }
}

// 导出单例
export const themeService = new ThemeService()
