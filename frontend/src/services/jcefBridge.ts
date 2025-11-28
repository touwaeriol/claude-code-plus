import { ideService } from './ideaBridge'
import { themeService, type IdeTheme } from './themeService'

/**
 * JCEF 桥接服务：在 JCEF 环境里暴露 IDE 能力给前端，同时在浏览器模式提供降级实现。
 */
export interface JcefCapabilities {
  isAvailable: boolean
  openFile: (filePath: string, options?: {
    line?: number
    endLine?: number
    column?: number
    selectContent?: boolean
    content?: string
  }) => Promise<void>
  showDiff: (options: {
    filePath: string
    oldContent: string
    newContent: string
    title?: string
    rebuildFromFile?: boolean
    edits?: Array<{
      oldString: string
      newString: string
      replaceAll: boolean
    }>
  }) => Promise<void>
  switchTheme: (theme: IdeTheme | 'light' | 'dark') => void
}

class JcefBridgeService {
  private capabilities: JcefCapabilities | null = null
  private isInitialized = false

  private detectJcef(): boolean {
    if (typeof window === 'undefined') {
      return false
    }
    // 检测 JCEF 原生注入的 cefQuery 函数
    return typeof (window as any).cefQuery === 'function'
  }

  async init(): Promise<void> {
    if (this.isInitialized) return

    const isJcef = this.detectJcef()
    if (isJcef) {
      this.capabilities = {
        isAvailable: true,
        openFile: this.createOpenFileHandler(),
        showDiff: this.createShowDiffHandler(),
        switchTheme: this.createSwitchThemeHandler()
      }
      this.exposeFrontendMethods()
      console.log('JCEF Bridge initialized')
    } else {
      this.capabilities = {
        isAvailable: false,
        openFile: async () => { console.warn('JCEF not available: openFile') },
        showDiff: async () => { console.warn('JCEF not available: showDiff') },
        switchTheme: () => { console.warn('JCEF not available: switchTheme') }
      }
      console.log('Running in browser mode (JCEF not available)')
    }

    this.isInitialized = true
  }

  private createOpenFileHandler() {
    return async (filePath: string, options?: {
      line?: number
      endLine?: number
      column?: number
      selectContent?: boolean
      content?: string
    }) => {
      await ideService.openFile(filePath, options)
    }
  }

  private createShowDiffHandler() {
    return async (options: {
      filePath: string
      oldContent: string
      newContent: string
      title?: string
      rebuildFromFile?: boolean
      edits?: Array<{
        oldString: string
        newString: string
        replaceAll: boolean
      }>
    }) => {
      await ideService.showDiff(options)
    }
  }

  private createSwitchThemeHandler() {
    return (theme: IdeTheme | 'light' | 'dark') => {
      console.log('[JCEF Bridge] switchTheme called with:', typeof theme === 'object' ? 'full theme object' : theme)
      // 只支持完整主题对象，禁止降级处理
        themeService.setTheme(theme)
    }
  }

  private exposeFrontendMethods() {
    if (typeof window === 'undefined') {
      return
    }

    const bridge = (window as any).__CLAUDE_IDE_BRIDGE__ = (window as any).__CLAUDE_IDE_BRIDGE__ || {}

    // switchTheme 支持接收完整主题对象或 'light'/'dark' 字符串
    bridge.switchTheme = (theme: IdeTheme | 'light' | 'dark') => {
      console.log(`[JCEF Bridge] switchTheme called:`, typeof theme === 'object' ? 'full theme object' : theme)
      this.capabilities?.switchTheme(theme)
    }

    // initTheme 用于初始化时推送主题（由后端调用）
    bridge.initTheme = () => {
      console.log('[JCEF Bridge] initTheme called, waiting for theme push from IDE...')
      // 不再通过 HTTP API 获取，等待后端通过 switchTheme 推送完整主题对象
      // 如果没有收到推送，将使用默认亮色主题（在 themeService.initialize 中已设置）
    }

    bridge.getCapabilities = () => {
      return {
        isAvailable: this.capabilities?.isAvailable || false,
        version: '1.0.0'
      }
    }

    console.log('Frontend methods exposed to IDE host')
  }

  getCapabilities(): JcefCapabilities {
    if (!this.isInitialized) {
      console.warn('JCEF Bridge not initialized, returning stub capabilities')
      return {
        isAvailable: false,
        openFile: async () => {},
        showDiff: async () => {},
        switchTheme: () => {}
      }
    }
    return this.capabilities!
  }

  isJcefAvailable(): boolean {
    return this.capabilities?.isAvailable || false
  }
}

let _jcefBridge: JcefBridgeService | null = null

function getJcefBridge(): JcefBridgeService {
  if (!_jcefBridge) {
    _jcefBridge = new JcefBridgeService()
  }
  return _jcefBridge
}

export const jcefBridge = {
  init: () => getJcefBridge().init(),
  getCapabilities: () => getJcefBridge().getCapabilities(),
  isJcefAvailable: () => getJcefBridge().isJcefAvailable()
}

if (typeof window !== 'undefined') {
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      getJcefBridge().init()
    })
  } else {
    getJcefBridge().init()
  }
}
