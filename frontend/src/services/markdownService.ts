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
      return `<div class="code-block-wrapper" data-lang="${lang}">
        <div class="code-block-header">
          <span class="language">${lang}</span>
          <button class="copy-btn" data-code="${encodeURIComponent(code)}">复制</button>
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
