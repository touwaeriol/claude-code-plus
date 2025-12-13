/**
 * Markdown 渲染服务
 * 基于 markdown-it 实现 GitHub Flavored Markdown 支持
 */

import MarkdownIt from 'markdown-it'

class MarkdownService {
  private md: MarkdownIt

  constructor() {
    this.md = new MarkdownIt({
      html: false, // 安全考虑,禁用 HTML
      linkify: true, // 自动识别链接
      typographer: true, // 智能标点
      breaks: true // 换行符转 <br>
    })

    this.setupCustomRules()
  }

  /**
   * 渲染 Markdown 为 HTML
   */
  render(markdown: string): string {
    return this.md.render(markdown)
  }

  /**
   * 设置自定义规则
   */
  private setupCustomRules() {
    // 自定义代码块渲染
    this.md.renderer.rules.fence = (tokens, idx, _options, _env, _slf) => {
      const token = tokens[idx]
      const lang = token.info.trim() || 'text'
      const code = token.content

      // 返回自定义结构,供 Vue 组件接管渲染
      // 复制按钮使用 SVG 图标，不使用文字（避免国际化问题）
      const copyIcon = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>`
      return `<div class="code-block-wrapper" data-lang="${lang}">
        <div class="code-block-header">
          <span class="language">${lang}</span>
          <button class="copy-btn" data-code="${encodeURIComponent(code)}">${copyIcon}</button>
        </div>
        <div class="code-content"><code class="language-${lang}">${this.escapeHtml(code)}</code></div>
      </div>`
    }

    // 自定义链接渲染 (支持文件路径)
    const defaultLinkOpen = this.md.renderer.rules.link_open ||
      ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

    this.md.renderer.rules.link_open = (tokens, idx, _options, env, _slf) => {
      const token = tokens[idx]
      const hrefIndex = token.attrIndex('href')

      if (hrefIndex >= 0) {
        const href = token.attrs![hrefIndex][1]

        // 文件路径链接添加特殊类
        if (href.startsWith('/') || href.startsWith('file://')) {
          token.attrPush(['class', 'file-link'])
        }
      }

      return defaultLinkOpen(tokens, idx, _options, env, _slf)
    }
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
export const markdownService = new MarkdownService()
