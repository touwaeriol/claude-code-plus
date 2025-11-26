/**
 * 代码高亮服务
 * 基于 Shiki 实现语法高亮（按需动态加载，避免主包体过大）
 */

import type { Highlighter, BundledLanguage, BundledTheme } from 'shiki'

class HighlightService {
  private highlighter: Highlighter | null = null
  private initialized = false
  private initPromise: Promise<void> | null = null

  /**
   * 初始化高亮器
   */
  async init(): Promise<void> {
    if (this.initialized) return
    if (this.initPromise) return this.initPromise

    this.initPromise = (async () => {
      try {
        console.log('🎨 Initializing syntax highlighter...')

        // 动态加载 Shiki，拆分大体积依赖为独立 chunk
        const { createHighlighter } = await import('shiki')
        this.highlighter = await createHighlighter({
          themes: ['github-light', 'github-dark'],
          langs: [
            'javascript',
            'typescript',
            'python',
            'java',
            'kotlin',
            'go',
            'rust',
            'cpp',
            'c',
            'csharp',
            'php',
            'ruby',
            'swift',
            'bash',
            'shell',
            'json',
            'xml',
            'html',
            'css',
            'scss',
            'sql',
            'yaml',
            'markdown',
            'dockerfile',
            'vue',
            'jsx',
            'tsx'
          ] as BundledLanguage[]
        })

        this.initialized = true
        console.log('✅ Syntax highlighter initialized')
      } catch (error) {
        console.error('❌ Failed to initialize highlighter:', error)
        throw error
      }
    })()

    return this.initPromise
  }

  /**
   * 高亮代码
   */
  async highlight(code: string, lang: string, isDark: boolean): Promise<string> {
    await this.init()

    if (!this.highlighter) {
      return this.escapeHtml(code)
    }

    try {
      const theme = (isDark ? 'github-dark' : 'github-light') as BundledTheme
      const validLang = this.normalizeLanguage(lang)

      return this.highlighter.codeToHtml(code, {
        lang: validLang,
        theme
      })
    } catch (error) {
      console.warn(`Failed to highlight ${lang}:`, error)
      return `<pre><code>${this.escapeHtml(code)}</code></pre>`
    }
  }

  /**
   * 规范化语言名称
   */
  private normalizeLanguage(lang: string): BundledLanguage {
    const normalized = lang.toLowerCase().trim()

    // 语言别名映射
    const aliasMap: Record<string, BundledLanguage> = {
      'js': 'javascript',
      'ts': 'typescript',
      'py': 'python',
      'kt': 'kotlin',
      'sh': 'bash',
      'yml': 'yaml',
      'md': 'markdown',
      'cs': 'csharp',
      'c++': 'cpp',
      'h': 'c',
      'hpp': 'cpp'
    }

    const mappedLang = aliasMap[normalized] || normalized

    // 验证语言是否支持
    const supportedLangs = [
      'javascript', 'typescript', 'python', 'java', 'kotlin',
      'go', 'rust', 'cpp', 'c', 'csharp', 'php', 'ruby', 'swift',
      'bash', 'shell', 'json', 'xml', 'html', 'css', 'scss',
      'sql', 'yaml', 'markdown', 'dockerfile', 'vue', 'jsx', 'tsx'
    ]

    return supportedLangs.includes(mappedLang)
      ? mappedLang as BundledLanguage
      : 'text' as BundledLanguage
  }

  /**
   * HTML 转义
   */
  private escapeHtml(text: string): string {
    const div = document.createElement('div')
    div.textContent = text
    return div.innerHTML
  }
}

// 导出单例
export const highlightService = new HighlightService()
