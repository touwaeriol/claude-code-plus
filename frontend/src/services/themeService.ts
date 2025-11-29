/**
 * 主题颜色接口
 */
export interface ThemeColors {
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

// Web 环境预定义主题
const DARK_THEME: ThemeColors = {
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

const LIGHT_THEME: ThemeColors = {
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
  private currentTheme: ThemeColors | null = null
  private listeners: Set<(theme: ThemeColors) => void> = new Set()
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

    // IDEA 模式但 JCEF 还没注入：等待注入后再初始化主题
    const anyWindow = window as any
    if (anyWindow.__IDEA_MODE__) {
      console.log('🎨 [IDE] Waiting for JCEF bridge...')
      this.waitForThemeBridge()
      return
    }

    // 浏览器模式：应用系统主题偏好
    console.log('🎨 [Browser] No IDE bridge, applying system preference')
    this.setTheme('system')
    this.watchSystemTheme()
    this.waitForThemeBridge()
  }

  /**
   * 设置主题
   * @param mode - 'light' | 'dark' | 'system' 或完整的 ThemeColors 对象
   */
  setTheme(mode: ThemeMode | ThemeColors) {
    let theme: ThemeColors

    if (typeof mode === 'object') {
      // IDE 模式：直接使用 IDE 返回的完整主题
      theme = mode
      console.log('🎨 [IDE] Applying IDE theme')
    } else {
      // Web 模式：使用预定义主题
      this.themeMode = mode

      if (mode === 'system') {
        const prefersDark = this.detectSystemTheme()
        theme = prefersDark ? DARK_THEME : LIGHT_THEME
        console.log('🎨 [System] Detected:', prefersDark ? 'dark' : 'light')
      } else {
        theme = mode === 'dark' ? DARK_THEME : LIGHT_THEME
        console.log('🎨 [User] Selected:', mode)
      }
    }

    this.applyTheme(theme)
  }

  /**
   * 切换主题（仅 Web 模式有效）
   */
  toggleTheme() {
    if (this.hasIdeBridge) {
      console.log('🎨 Toggle theme not available in IDE mode')
      return
    }
    const newMode = this.themeMode === 'dark' ? 'light' : 'dark'
    this.setTheme(newMode)
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
  getCurrentTheme(): ThemeColors | null {
    return this.currentTheme
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
  onThemeChange(listener: (theme: ThemeColors) => void) {
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

  private applyTheme(theme: ThemeColors) {
    this.currentTheme = theme
    this.injectCssVariables(theme)
    this.notifyListeners(theme)
  }

  private notifyListeners(theme: ThemeColors) {
    this.listeners.forEach(listener => {
      try {
        listener(theme)
      } catch (error) {
        console.error('❌ Theme listener error:', error)
      }
    })
  }

  private bindThemeBridge(): boolean {
    const ideaJcef = (window as any).__IDEA_JCEF__
    if (!ideaJcef?.theme?.getCurrent) return false

    ideaJcef.theme.onChange = (theme: ThemeColors) => {
      if (theme) this.setTheme(theme)
    }

    const currentTheme = ideaJcef.theme.getCurrent()
    if (currentTheme) {
      this.setTheme(currentTheme)
    }

    this.clearBridgeReadyHandler()
    console.log('🎨 [IDE] IDEA JCEF theme bridge connected')
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
    window.addEventListener('idea:jcefReady', this.bridgeReadyHandler)
    window.addEventListener('idea:themeChange', ((e: CustomEvent<ThemeColors>) => {
      if (e.detail) {
        this.hasIdeBridge = true
        this.setTheme(e.detail)
      }
    }) as EventListener)
  }

  private clearBridgeReadyHandler() {
    if (this.bridgeReadyHandler) {
      window.removeEventListener('idea:jcefReady', this.bridgeReadyHandler)
      this.bridgeReadyHandler = null
    }
  }

  private injectCssVariables(theme: ThemeColors) {
    const root = document.documentElement

    // 注入 CSS 变量
    const vars: Record<string, string> = {
      '--theme-background': theme.background,
      '--theme-foreground': theme.foreground,
      '--theme-panel-background': theme.panelBackground,
      '--theme-border': theme.borderColor,
      '--theme-text-field-background': theme.textFieldBackground,
      '--theme-selection-background': theme.selectionBackground,
      '--theme-selection-foreground': theme.selectionForeground,
      '--theme-link': theme.linkColor,
      '--theme-error': theme.errorColor,
      '--theme-warning': theme.warningColor,
      '--theme-success': theme.successColor,
      '--theme-separator': theme.separatorColor,
      '--theme-hover-background': theme.hoverBackground,
      '--theme-accent': theme.accentColor,
      '--theme-info-background': theme.infoBackground,
      '--theme-code-background': theme.codeBackground,
      '--theme-secondary-foreground': theme.secondaryForeground,
      '--theme-card-background': theme.panelBackground
    }

    Object.entries(vars).forEach(([key, value]) => {
      root.style.setProperty(key, value)
    })

    console.log('✅ Theme CSS variables injected')
  }
}

// 导出单例
export const themeService = new ThemeService()
