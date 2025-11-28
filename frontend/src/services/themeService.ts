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

export type ThemeMode = 'light' | 'dark' | 'system'

type ThemeBridge = {
  getCurrent?: () => IdeTheme | null
  push?: (theme: IdeTheme) => void
  onChange?: ((theme: IdeTheme) => void) | null
}

// 默认主题配置
const DARK_THEME: IdeTheme = {
  isDark: true,
  background: '#1e1e1e',
  foreground: '#d4d4d4',
  panelBackground: '#252526',
  borderColor: '#3c3c3c',
  textFieldBackground: '#3c3c3c',
  selectionBackground: '#264f78',
  selectionForeground: '#ffffff',
  linkColor: '#3794ff',
  errorColor: '#f14c4c',
  warningColor: '#cca700',
  successColor: '#89d185',
  separatorColor: '#3c3c3c',
  hoverBackground: '#2a2d2e',
  accentColor: '#0e639c',
  infoBackground: '#2d2d2d',
  codeBackground: '#1e1e1e',
  secondaryForeground: '#858585'
}

const LIGHT_THEME: IdeTheme = {
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

export class ThemeService {
  private currentTheme: IdeTheme | null = null
  private listeners: Set<(theme: IdeTheme) => void> = new Set()
  private initialized = false
  private bridgeReadyHandler: ((event: Event) => void) | null = null
  private themeMode: ThemeMode = 'system'
  private hasIdeBridge = false

  /**
   * 初始化主题服务
   */
  async initialize() {
    if (this.initialized) {
      return
    }
    this.initialized = true
    console.log('🎨 Initializing theme service...')

    if (typeof window === 'undefined') {
      this.setTheme('system')
      return
    }

    // 尝试绑定 IDE 桥接
    if (this.bindThemeBridge()) {
      this.hasIdeBridge = true
      return
    }

    // 无 IDE 桥接，应用用户偏好
    console.log('🎨 [Browser] No IDE bridge, applying preference:', this.themeMode)
    this.setTheme(this.themeMode)
    this.watchSystemTheme()
    this.waitForThemeBridge()
  }

  /**
   * 🎯 核心方法：设置主题
   * 所有主题切换都通过此方法
   *
   * @param mode - 'light' | 'dark' | 'system' 或完整的 IdeTheme 对象
   */
  setTheme(mode: ThemeMode | IdeTheme) {
    let theme: IdeTheme

    if (typeof mode === 'object') {
      // 接收完整主题对象（来自 IDE）
      theme = mode
      console.log('🎨 [IDE] Applying theme:', theme.isDark ? 'dark' : 'light')
    } else {
      // 接收模式字符串
      this.themeMode = mode

      if (mode === 'system') {
        const isDark = this.detectSystemTheme()
        theme = isDark ? DARK_THEME : LIGHT_THEME
        console.log('🎨 [System] Detected:', isDark ? 'dark' : 'light')
      } else {
        theme = mode === 'dark' ? DARK_THEME : LIGHT_THEME
        console.log('🎨 [User] Selected:', mode)
      }
    }

    this.applyTheme(theme)
  }

  /**
   * 切换主题（亮/暗）
   */
  toggleTheme() {
    const currentIsDark = this.currentTheme?.isDark ?? false
    this.setTheme(currentIsDark ? 'light' : 'dark')
  }

  /**
   * 获取当前主题模式
   */
  getThemeMode(): ThemeMode {
    return this.themeMode
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

  /**
   * 是否有 IDE 桥接
   */
  hasIde(): boolean {
    return this.hasIdeBridge
  }

  /**
   * 监听主题变化
   */
  onThemeChange(listener: (theme: IdeTheme) => void) {
    this.listeners.add(listener)
    if (this.currentTheme) {
      listener(this.currentTheme)
    }
    return () => this.listeners.delete(listener)
  }

  // ========== 私有方法 ==========

  private detectSystemTheme(): boolean {
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  }

  private watchSystemTheme() {
    if (!window.matchMedia) return

    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      // 只有在 system 模式且无 IDE 桥接时才响应
      if (this.themeMode === 'system' && !this.hasIdeBridge) {
        console.log('🎨 [System] Theme changed:', e.matches ? 'dark' : 'light')
        this.setTheme('system')
      }
    })
  }

  private applyTheme(theme: IdeTheme) {
    this.currentTheme = theme
    this.injectCssVariables(theme)
    this.notifyListeners(theme)
  }

  private notifyListeners(theme: IdeTheme) {
    this.listeners.forEach(listener => {
      try {
        listener(theme)
      } catch (error) {
        console.error('❌ Theme listener error:', error)
      }
    })
  }

  private bindThemeBridge(): boolean {
    const bridge = (window as any).__themeBridge as ThemeBridge | undefined
    if (!bridge?.getCurrent) return false

    bridge.onChange = (theme: IdeTheme) => {
      if (theme) this.setTheme(theme)
    }

    const currentTheme = bridge.getCurrent()
    if (currentTheme) {
      this.setTheme(currentTheme)
    }

    this.clearBridgeReadyHandler()
    console.log('🎨 [IDE] Theme bridge connected')
    return true
  }

  private waitForThemeBridge() {
    if (this.bridgeReadyHandler) return
    this.bridgeReadyHandler = () => {
      if (this.bindThemeBridge()) {
        this.hasIdeBridge = true
        this.clearBridgeReadyHandler()
      }
    }
    window.addEventListener('claude:themeBridgeReady', this.bridgeReadyHandler)
  }

  private clearBridgeReadyHandler() {
    if (this.bridgeReadyHandler) {
      window.removeEventListener('claude:themeBridgeReady', this.bridgeReadyHandler)
      this.bridgeReadyHandler = null
    }
  }

  private injectCssVariables(theme: IdeTheme) {
    const root = document.documentElement

    // 设置主题类
    root.classList.toggle('theme-dark', theme.isDark)
    root.classList.toggle('theme-light', !theme.isDark)

    // 注入 CSS 变量
    const vars: Record<string, string> = {
      '--ide-background': theme.background,
      '--ide-foreground': theme.foreground,
      '--ide-panel-background': theme.panelBackground,
      '--ide-border': theme.borderColor,
      '--ide-text-field-background': theme.textFieldBackground,
      '--ide-selection-background': theme.selectionBackground,
      '--ide-selection-foreground': theme.selectionForeground,
      '--ide-link': theme.linkColor,
      '--ide-error': theme.errorColor,
      '--ide-warning': theme.warningColor,
      '--ide-success': theme.successColor,
      '--ide-separator': theme.separatorColor,
      '--ide-hover-background': theme.hoverBackground,
      '--ide-accent': theme.accentColor,
      '--ide-info-background': theme.infoBackground,
      '--ide-code-background': theme.codeBackground,
      '--ide-secondary-foreground': theme.secondaryForeground,
      '--ide-warning-background': theme.isDark ? '#3d3416' : '#fff8dc',
      '--ide-card-background': theme.isDark ? '#252526' : '#ffffff'
    }

    Object.entries(vars).forEach(([key, value]) => {
      root.style.setProperty(key, value)
    })

    console.log('✅ Theme applied:', theme.isDark ? 'dark' : 'light')
  }
}

// 导出单例
export const themeService = new ThemeService()
