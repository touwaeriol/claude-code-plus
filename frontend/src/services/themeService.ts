import { jetbrainsBridge, isIdeEnvironment } from './jetbrainsApi'

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
  private _unsubscribeTheme: (() => void) | null = null
  private loadedFonts: Set<string> = new Set() // 记录已加载的字体

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

    // 🚀 优先从 URL 参数读取初始主题
    const initialTheme = this.getInitialThemeFromUrl()
    if (initialTheme) {
      console.log('🎨 [URL] Applying initial theme from URL')
      this.setTheme(initialTheme)
      this.hasIdeBridge = true
      // 加载字体
      await this.loadFontsFromBackend(initialTheme)
      // 继续绑定 RSocket 以接收后续主题更新
      this.bindJetBrainsThemeAsync()
      return
    }

    // 先应用系统主题，避免无主题状态
    this.setTheme('system')

    // 统一逻辑：浏览器和 IDEA 插件都使用 IDEA 主题
    // 只要后端支持 JetBrains 集成就使用 IDEA 主题
    if (jetbrainsBridge.isEnabled()) {
      console.log('🎨 [Unified] Using IDEA theme (backend supports JetBrains)')
      await this.bindJetBrainsTheme()
      return
    }

    // 后端不支持 JetBrains 集成：使用系统主题
    console.log('🎨 [Fallback] Using system theme (no JetBrains backend)')
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
   * 从后端加载字体
   * 通过 HTTP API 下载字体文件并注入 @font-face
   */
  private async loadFontsFromBackend(theme: ThemeColors): Promise<void> {
    const fontsToLoad: string[] = []

    // 收集需要加载的字体
    if (theme.fontFamily) {
      const primaryFont = this.extractPrimaryFont(theme.fontFamily)
      if (primaryFont && !this.isSystemFont(primaryFont)) {
        fontsToLoad.push(primaryFont)
      }
    }

    if (theme.editorFontFamily) {
      const editorFont = this.extractPrimaryFont(theme.editorFontFamily)
      if (editorFont && !this.isSystemFont(editorFont) && !fontsToLoad.includes(editorFont)) {
        fontsToLoad.push(editorFont)
      }
    }

    if (fontsToLoad.length === 0) {
      console.log('🔤 [Font] No custom fonts to load')
      return
    }

    console.log('🔤 [Font] Loading fonts:', fontsToLoad)

    // 并行加载所有字体
    await Promise.all(fontsToLoad.map((fontName) => this.loadFont(fontName)))
  }

  /**
   * 从字体族字符串中提取主字体名称
   * 例如: "JetBrains Mono, Consolas, monospace" -> "JetBrains Mono"
   */
  private extractPrimaryFont(fontFamily: string): string | null {
    const fonts = fontFamily.split(',').map((f) => f.trim().replace(/['"]/g, ''))
    return fonts[0] || null
  }

  /**
   * 检查是否为系统字体（不需要下载）
   */
  private isSystemFont(fontName: string): boolean {
    const systemFonts = [
      'sans-serif',
      'serif',
      'monospace',
      'cursive',
      'fantasy',
      'system-ui',
      'ui-sans-serif',
      'ui-serif',
      'ui-monospace',
      'ui-rounded',
      'Arial',
      'Helvetica',
      'Times New Roman',
      'Times',
      'Courier New',
      'Courier',
      'Verdana',
      'Georgia',
      'Palatino',
      'Garamond',
      'Bookman',
      'Comic Sans MS',
      'Trebuchet MS',
      'Arial Black',
      'Impact',
      'Consolas',
      'Monaco',
      'Lucida Console',
      'Lucida Sans Typewriter',
      'Menlo',
      'SF Mono',
      'Segoe UI',
      'Tahoma',
      'Geneva'
    ]
    return systemFonts.some((sf) => sf.toLowerCase() === fontName.toLowerCase())
  }

  /**
   * 加载单个字体
   */
  private async loadFont(fontName: string): Promise<void> {
    // 检查是否已加载
    if (this.loadedFonts.has(fontName)) {
      console.log(`🔤 [Font] Already loaded: ${fontName}`)
      return
    }

    try {
      // 获取后端 URL
      const serverUrl = this.getServerUrl()
      const fontUrl = `${serverUrl}/api/font/${encodeURIComponent(fontName)}`

      console.log(`🔤 [Font] Fetching: ${fontUrl}`)

      const response = await fetch(fontUrl)

      if (!response.ok) {
        if (response.status === 404) {
          console.log(`🔤 [Font] Not found on server: ${fontName} (using fallback)`)
        } else {
          console.warn(`🔤 [Font] Failed to load ${fontName}: ${response.status}`)
        }
        return
      }

      // 获取字体数据
      const fontBlob = await response.blob()
      const fontDataUrl = URL.createObjectURL(fontBlob)

      // 检测字体格式
      const contentType = response.headers.get('Content-Type') || 'font/ttf'
      const format = this.getFormatFromMimeType(contentType)

      // 创建 @font-face 规则
      const fontFace = new FontFace(fontName, `url(${fontDataUrl})`, {
        style: 'normal',
        weight: '400'
      })

      // 加载字体
      await fontFace.load()

      // 添加到文档字体
      document.fonts.add(fontFace)
      this.loadedFonts.add(fontName)

      console.log(`✅ [Font] Loaded: ${fontName} (format: ${format})`)
    } catch (error) {
      console.warn(`🔤 [Font] Error loading ${fontName}:`, error)
    }
  }

  /**
   * 根据 MIME 类型获取字体格式
   */
  private getFormatFromMimeType(mimeType: string): string {
    const formatMap: Record<string, string> = {
      'font/ttf': 'truetype',
      'font/otf': 'opentype',
      'font/woff': 'woff',
      'font/woff2': 'woff2',
      'application/x-font-ttf': 'truetype',
      'application/x-font-opentype': 'opentype'
    }
    return formatMap[mimeType] || 'truetype'
  }

  /**
   * 获取服务器 URL
   */
  private getServerUrl(): string {
    // 优先使用注入的 serverUrl
    const anyWindow = window as unknown as { __serverUrl?: string }
    if (anyWindow.__serverUrl) {
      return anyWindow.__serverUrl
    }

    // 从环境变量获取
    const envUrl = import.meta.env.VITE_SERVER_URL
    if (envUrl) {
      return envUrl
    }

    // 从端口获取
    const envPort = import.meta.env.VITE_BACKEND_PORT
    if (envPort) {
      return `http://localhost:${envPort}`
    }

    // 默认端口
    return 'http://localhost:8765'
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
            // 主题变化时检查是否需要加载新字体
            this.loadFontsFromBackend(theme as ThemeColors)
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
        // 加载字体
        await this.loadFontsFromBackend(theme as ThemeColors)
      }

      // 订阅主题变化
      this.unsubscribeTheme = jetbrainsBridge.onThemeChange((theme) => {
        if (theme) {
          this.setTheme(theme as ThemeColors)
          console.log('🎨 [IDE] Theme updated via RSocket')
          // 主题变化时也检查是否需要加载新字体
          this.loadFontsFromBackend(theme as ThemeColors)
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
