import { jetbrainsBridge } from './jetbrainsApi'

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
  // 字体设置
  fontFamily?: string
  fontSize?: number
  editorFontFamily?: string
  editorFontSize?: number
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
  background: '#ffffff',           // 纯白背景
  foreground: '#24292e',
  panelBackground: '#fafbfc',      // 更白的面板背景（从 #f6f8fa 调整）
  borderColor: '#e1e4e8',
  textFieldBackground: '#ffffff',
  selectionBackground: '#d2e7ff',
  selectionForeground: '#0b3d91',
  linkColor: '#0366d6',
  errorColor: '#d73a49',
  warningColor: '#ffc107',
  successColor: '#28a745',
  separatorColor: '#e1e4e8',
  hoverBackground: '#f3f4f6',      // 悬停背景（更淡）
  accentColor: '#0366d6',
  infoBackground: '#f5f5f5',       // 信息背景（更白，从 #f0f0f0 调整）
  codeBackground: '#f8f9fa',       // 代码背景（更白，从 #f6f8fa 调整）
  secondaryForeground: '#6a737d'
}

export class ThemeService {
  private currentTheme: ThemeColors | null = null
  private listeners: Set<(theme: ThemeColors) => void> = new Set()
  private initialized = false
  private themeMode: ThemeMode = 'system'
  private hasIdeBridge = false
  private unsubscribeTheme: (() => void) | null = null

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

    // 🚀 优先从 URL 参数读取初始主题（IDE 模式加载时注入）
    const initialTheme = this.getInitialThemeFromUrl()
    if (initialTheme) {
      console.log('🎨 [IDE] Applying initial theme from URL')
      this.setTheme(initialTheme)
      this.hasIdeBridge = true
      // 继续绑定 RSocket 以接收后续主题更新
      this.bindJetBrainsThemeAsync()
      return
    }

    // 先应用系统主题，避免无主题状态
    this.setTheme('system')

    // 检查 JetBrains 桥接是否已启用
    if (jetbrainsBridge.isEnabled()) {
      console.log('🎨 [IDE] JetBrains bridge detected, fetching theme...')
      await this.bindJetBrainsTheme()
      return
    }

    // 浏览器模式：应用系统主题偏好
    console.log('🎨 [Browser] No IDE bridge, applying system preference')
    this.watchSystemTheme()
  }

  /**
   * 从 URL 参数或 window.__initialTheme 读取初始主题
   */
  private getInitialThemeFromUrl(): ThemeColors | null {
    try {
      // 优先使用 index.html 中预解析的主题（更快）
      const anyWindow = window as unknown as { __initialTheme?: ThemeColors }
      if (anyWindow.__initialTheme) {
        console.log('🎨 [URL] Using pre-parsed theme from window.__initialTheme')
        return anyWindow.__initialTheme
      }

      // 回退到手动解析 URL
      const params = new URLSearchParams(window.location.search)
      const themeParam = params.get('initialTheme')
      if (!themeParam) return null

      const themeJson = decodeURIComponent(themeParam)
      const theme = JSON.parse(themeJson) as ThemeColors
      console.log('🎨 [URL] Found initial theme in URL params')
      return theme
    } catch (error) {
      console.warn('🎨 [URL] Failed to parse initial theme:', error)
      return null
    }
  }

  /**
   * 异步绑定 JetBrains 主题（用于后续更新，不阻塞初始化）
   */
  private bindJetBrainsThemeAsync() {
    // 延迟执行，不阻塞初始渲染
    setTimeout(async () => {
      try {
        // 订阅主题变化（无需再获取当前主题，已从 URL 获取）
        this.unsubscribeTheme = jetbrainsBridge.onThemeChange((theme) => {
          if (theme) {
            this.setTheme(theme as ThemeColors)
            console.log('🎨 [IDE] Theme updated via RSocket')
          }
        })
        console.log('🎨 [IDE] Theme change listener registered')
      } catch (error) {
        console.warn('🎨 [IDE] Failed to bind theme listener:', error)
      }
    }, 100)
  }

  /**
   * 绑定 JetBrains 主题（通过 RSocket）
   */
  private async bindJetBrainsTheme() {
    try {
      // 获取当前主题
      const theme = await jetbrainsBridge.getTheme()
      if (theme) {
        this.setTheme(theme as ThemeColors)
        this.hasIdeBridge = true
        console.log('🎨 [IDE] ✅ Theme loaded via RSocket')
      }

      // 订阅主题变化
      this.unsubscribeTheme = jetbrainsBridge.onThemeChange((theme) => {
        if (theme) {
          this.setTheme(theme as ThemeColors)
          console.log('🎨 [IDE] Theme updated via RSocket')
        }
      })
    } catch (error) {
      console.warn('🎨 [IDE] Failed to get theme via RSocket:', error)
      this.watchSystemTheme()
    }
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

    // 字体变量（如果存在）
    if (theme.fontFamily) {
      vars['--theme-font-family'] = theme.fontFamily
    }
    if (theme.fontSize) {
      vars['--theme-font-size'] = `${theme.fontSize}px`
    }
    if (theme.editorFontFamily) {
      vars['--theme-editor-font-family'] = theme.editorFontFamily
    }
    if (theme.editorFontSize) {
      vars['--theme-editor-font-size'] = `${theme.editorFontSize}px`
    }

    Object.entries(vars).forEach(([key, value]) => {
      root.style.setProperty(key, value)
    })

    console.log('✅ Theme CSS variables injected', theme.fontFamily ? `(font: ${theme.fontFamily})` : '')
  }
}

// 导出单例
export const themeService = new ThemeService()
