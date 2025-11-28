/**
 * 链接识别和渲染工具
 * 支持 HTTP URL、@文件路径、绝对路径
 */

export interface LinkifyResult {
  html: string
  hasLinks: boolean
}

// HTML 转义
function escapeHtml(text: string): string {
  const map: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
  }
  return text.replace(/[&<>"']/g, (m) => map[m])
}

// 链接类型
type LinkType = 'url' | 'file'

interface LinkMatch {
  start: number
  end: number
  text: string
  href: string
  type: LinkType
}

/**
 * 识别文本中的所有链接
 */
function findLinks(text: string): LinkMatch[] {
  const matches: LinkMatch[] = []

  // HTTP/HTTPS URL
  const urlPattern = /https?:\/\/[^\s<>"{}|\\^`\[\]]+/g
  let match
  while ((match = urlPattern.exec(text)) !== null) {
    matches.push({
      start: match.index,
      end: match.index + match[0].length,
      text: match[0],
      href: match[0],
      type: 'url',
    })
  }

  // @文件路径: @src/App.vue, @./relative/path.ts
  const atFilePattern = /@([.\w\-\/\\]+\.\w+)/g
  while ((match = atFilePattern.exec(text)) !== null) {
    // 检查是否与已有匹配重叠
    const start = match.index
    const end = match.index + match[0].length
    const overlaps = matches.some((m) => start < m.end && end > m.start)
    if (!overlaps) {
      matches.push({
        start,
        end,
        text: match[0],
        href: match[1], // 不含 @ 符号的路径
        type: 'file',
      })
    }
  }

  // 绝对路径: /path/to/file.ts, C:\path\file.ts
  // 需要在空格或行首后面
  const absolutePathPattern =
    /(?:^|[\s(])([/\\][\w\-.@/\\]+\.\w+|[A-Z]:[/\\][\w\-.@/\\]+\.\w+)/gm
  while ((match = absolutePathPattern.exec(text)) !== null) {
    const path = match[1]
    const start = match.index + match[0].indexOf(path)
    const end = start + path.length
    const overlaps = matches.some((m) => start < m.end && end > m.start)
    if (!overlaps) {
      matches.push({
        start,
        end,
        text: path,
        href: path,
        type: 'file',
      })
    }
  }

  // 按位置排序
  matches.sort((a, b) => a.start - b.start)

  return matches
}

/**
 * 将纯文本转换为带链接的 HTML
 * 用于消息气泡显示
 */
export function linkifyText(text: string): LinkifyResult {
  const links = findLinks(text)

  if (links.length === 0) {
    return {
      html: escapeHtml(text),
      hasLinks: false,
    }
  }

  const parts: string[] = []
  let lastIndex = 0

  for (const link of links) {
    // 添加链接前的文本
    if (link.start > lastIndex) {
      parts.push(escapeHtml(text.slice(lastIndex, link.start)))
    }

    // 添加链接
    const className = link.type === 'file' ? 'linkified-link file-link' : 'linkified-link'
    const dataType = link.type
    const escapedHref = escapeHtml(link.href)
    const escapedText = escapeHtml(link.text)

    parts.push(
      `<a href="${escapedHref}" class="${className}" data-link-type="${dataType}">${escapedText}</a>`
    )

    lastIndex = link.end
  }

  // 添加最后的文本
  if (lastIndex < text.length) {
    parts.push(escapeHtml(text.slice(lastIndex)))
  }

  return {
    html: parts.join(''),
    hasLinks: true,
  }
}

/**
 * 处理链接点击事件
 * @param href 链接地址
 * @param type 链接类型
 * @param openFile 打开文件的回调函数
 */
export function handleLinkClick(
  href: string,
  type: LinkType,
  openFile?: (path: string) => void
): void {
  if (type === 'url') {
    // 打开外部 URL
    window.open(href, '_blank', 'noopener,noreferrer')
  } else if (type === 'file' && openFile) {
    // 打开文件
    openFile(href)
  }
}

/**
 * 从点击事件中提取链接信息
 */
export function getLinkFromEvent(
  event: MouseEvent
): { href: string; type: LinkType } | null {
  const target = event.target as HTMLElement
  if (target.tagName !== 'A') return null

  const href = target.getAttribute('href')
  const type = (target.getAttribute('data-link-type') as LinkType) || 'url'

  if (!href) return null

  return { href, type }
}
