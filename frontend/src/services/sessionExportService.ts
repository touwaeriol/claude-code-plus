/**
 * 会话导出服务
 * 支持导出为 Markdown、JSON、HTML 格式
 */

import type { Session } from '@/types/message'
import type { Message } from '@/types/enhancedMessage'

export interface ExportConfig {
  format: 'markdown' | 'json' | 'html'
  includeTimestamps?: boolean
  includeMetadata?: boolean
  theme?: 'light' | 'dark'
  customCss?: string
}

export class SessionExportService {
  /**
   * 导出会话
   */
  async exportSession(
    session: Session,
    messages: Message[],
    config: ExportConfig
  ): Promise<string> {
    switch (config.format) {
      case 'markdown':
        return this.exportToMarkdown(session, messages, config)
      case 'json':
        return this.exportToJson(session, messages, config)
      case 'html':
        return this.exportToHtml(session, messages, config)
      default:
        throw new Error(`Unsupported format: ${config.format}`)
    }
  }

  /**
   * 导出为 Markdown
   */
  private exportToMarkdown(
    session: Session,
    messages: Message[],
    config: ExportConfig
  ): string {
    const lines: string[] = []

    // 标题
    lines.push(`# ${session.name}`)
    lines.push('')

    // 元数据
    if (config.includeMetadata) {
      lines.push('## 元数据')
      lines.push('')
      lines.push(`- **会话 ID**: ${session.id}`)
      lines.push(`- **创建时间**: ${this.formatTime(session.timestamp)}`)
      lines.push(`- **消息数量**: ${messages.length}`)
      lines.push('')
      lines.push('---')
      lines.push('')
    }

    // 对话内容
    lines.push('## 对话内容')
    lines.push('')

    for (const message of messages) {
      // 角色标识
      const roleEmoji = message.type === 'user' ? '👤' : '🤖'
      const roleName = message.type === 'user' ? '用户' : 'AI'

      lines.push(`### ${roleEmoji} ${roleName}`)

      if (config.includeTimestamps) {
        lines.push(`*${this.formatTime(message.timestamp)}*`)
        lines.push('')
      }

      // 消息内容
      if (message.type === 'user') {
        lines.push(message.text || '')
      } else if (message.type === 'assistant') {
        // 提取所有文本块
        const textBlocks = message.content
          .filter(block => block.type === 'text')
          .map(block => block.text || '')
        lines.push(textBlocks.join('\n\n'))
      }

      lines.push('')
      lines.push('---')
      lines.push('')
    }

    return lines.join('\n')
  }

  /**
   * 导出为 JSON
   */
  private exportToJson(
    session: Session,
    messages: Message[],
    config: ExportConfig
  ): string {
    const data: any = {
      id: session.id,
      name: session.name,
      timestamp: session.timestamp
    }

    if (config.includeMetadata) {
      data.metadata = {
        createdAt: new Date(session.timestamp).toISOString(),
        messageCount: messages.length
      }
    }

    data.messages = messages.map(message => {
      const msg: any = {
        id: message.id,
        type: message.type,
        timestamp: config.includeTimestamps ? message.timestamp : undefined
      }

      if (message.type === 'user') {
        msg.text = message.text
      } else if (message.type === 'assistant') {
        msg.content = message.content
      }

      return msg
    })

    return JSON.stringify(data, null, 2)
  }

  /**
   * 导出为 HTML
   */
  private exportToHtml(
    session: Session,
    messages: Message[],
    config: ExportConfig
  ): string {
    const markdown = this.exportToMarkdown(session, messages, config)
    const htmlContent = this.convertMarkdownToHtml(markdown)

    return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${this.escapeHtml(session.name)}</title>
    <style>
${this.getHtmlStyles(config)}
    </style>
</head>
<body>
    <div class="container">
${htmlContent}
    </div>
</body>
</html>`
  }

  /**
   * 获取 HTML 样式
   */
  private getHtmlStyles(config: ExportConfig): string {
    const isDark = config.theme === 'dark'

    return `
      body {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        line-height: 1.6;
        color: ${isDark ? '#e0e0e0' : '#333'};
        background-color: ${isDark ? '#1e1e1e' : '#fff'};
        margin: 0;
        padding: 0;
      }

      .container {
        max-width: 800px;
        margin: 0 auto;
        padding: 20px;
      }

      h1, h2, h3 {
        color: ${isDark ? '#fff' : '#000'};
      }

      h1 { border-bottom: 2px solid ${isDark ? '#444' : '#e0e0e0'}; padding-bottom: 10px; }
      h2 { margin-top: 30px; }
      h3 { margin-top: 20px; }

      code {
        background-color: ${isDark ? '#2d2d2d' : '#f4f4f4'};
        padding: 2px 4px;
        border-radius: 3px;
        font-family: 'Consolas', 'Monaco', monospace;
      }

      pre {
        background-color: ${isDark ? '#2d2d2d' : '#f4f4f4'};
        padding: 15px;
        border-radius: 5px;
        overflow-x: auto;
      }

      hr {
        border: none;
        border-top: 1px solid ${isDark ? '#444' : '#e0e0e0'};
        margin: 20px 0;
      }

      ${config.customCss || ''}
    `.trim()
  }

  /**
   * Markdown 转 HTML（简单实现）
   */
  private convertMarkdownToHtml(markdown: string): string {
    return markdown
      .replace(/^# (.+)$/gm, '<h1>$1</h1>')
      .replace(/^## (.+)$/gm, '<h2>$1</h2>')
      .replace(/^### (.+)$/gm, '<h3>$1</h3>')
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.+?)\*/g, '<em>$1</em>')
      .replace(/`(.+?)`/g, '<code>$1</code>')
      .replace(/^---$/gm, '<hr>')
      .replace(/^- (.+)$/gm, '<li>$1</li>')
      .replace(/\n\n/g, '</p>\n<p>')
      .replace(/^(.+)$/gm, (match) => {
        if (match.startsWith('<h') || match.startsWith('<hr') || match.startsWith('<li')) {
          return match
        }
        return `<p>${match}</p>`
      })
  }

  /**
   * 转义 HTML
   */
  private escapeHtml(str: string): string {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;')
  }

  /**
   * 格式化时间
   */
  private formatTime(timestamp: number): string {
    const date = new Date(timestamp)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  }

  /**
   * 下载文件
   */
  downloadFile(content: string, filename: string, mimeType: string) {
    const blob = new Blob([content], { type: mimeType })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  /**
   * 清理文件名
   */
  sanitizeFilename(name: string): string {
    return name
      .replace(/[<>:"/\\|?*]/g, '_')
      .replace(/\s+/g, '_')
      .substring(0, 100) // 限制长度
  }
}

// 导出单例
export const sessionExportService = new SessionExportService()
