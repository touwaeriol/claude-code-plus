/**
 * XSS 防护工具
 * 使用 DOMPurify 对 HTML 内容进行消毒，防止 XSS 攻击
 */
import DOMPurify, { Config } from 'dompurify'

/**
 * 预设的消毒配置
 */
const PRESETS: Record<string, Config> = {
  /**
   * Markdown 渲染：允许常见的 Markdown 生成的 HTML 标签
   */
  markdown: {
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'em', 'b', 'i', 'u', 's', 'del',
      'code', 'pre', 'kbd', 'samp', 'var',
      'ul', 'ol', 'li',
      'a',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'blockquote', 'hr',
      'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
      'span', 'div',
      'sup', 'sub',
      'details', 'summary'
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class', 'id', 'title', 'colspan', 'rowspan'],
    ALLOW_DATA_ATTR: false
  },

  /**
   * 代码高亮：只允许 span 和 code 标签（用于语法高亮）
   */
  code: {
    ALLOWED_TAGS: ['span', 'pre', 'code', 'br'],
    ALLOWED_ATTR: ['class', 'data-language'],
    ALLOW_DATA_ATTR: false
  },

  /**
   * 图标/SVG：允许 SVG 相关标签
   */
  icon: {
    ALLOWED_TAGS: [
      'svg', 'path', 'circle', 'rect', 'ellipse', 'line', 'polyline', 'polygon',
      'g', 'defs', 'use', 'symbol', 'clipPath', 'mask',
      'linearGradient', 'radialGradient', 'stop',
      'text', 'tspan',
      'span'
    ],
    ALLOWED_ATTR: [
      'class', 'id', 'style',
      'viewBox', 'width', 'height', 'xmlns', 'xmlns:xlink',
      'd', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin',
      'cx', 'cy', 'r', 'rx', 'ry', 'x', 'y', 'x1', 'y1', 'x2', 'y2',
      'points', 'transform', 'opacity', 'fill-opacity', 'stroke-opacity',
      'xlink:href', 'href', 'clip-path', 'mask',
      'offset', 'stop-color', 'stop-opacity',
      'font-size', 'font-family', 'text-anchor', 'dominant-baseline'
    ],
    ALLOW_DATA_ATTR: false
  },

  /**
   * 纯文本链接：只允许链接和基本文本格式
   */
  link: {
    ALLOWED_TAGS: ['a', 'br', 'span'],
    ALLOWED_ATTR: ['href', 'class', 'target', 'rel', 'data-link-type', 'data-file-path'],
    ALLOW_DATA_ATTR: true
  },

  /**
   * 严格模式：几乎不允许任何 HTML
   */
  strict: {
    ALLOWED_TAGS: ['br'],
    ALLOWED_ATTR: [],
    ALLOW_DATA_ATTR: false
  }
}

export type SanitizePreset = keyof typeof PRESETS

/**
 * 使用指定预设对 HTML 进行消毒
 * @param html 需要消毒的 HTML 字符串
 * @param preset 预设名称，默认为 'markdown'
 * @returns 消毒后的安全 HTML 字符串
 */
export function sanitizeHtml(html: string, preset: SanitizePreset = 'markdown'): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, PRESETS[preset])
}

/**
 * 消毒 Markdown 渲染输出
 */
export function sanitizeMarkdownHtml(html: string): string {
  return sanitizeHtml(html, 'markdown')
}

/**
 * 消毒代码高亮输出
 */
export function sanitizeCodeHtml(html: string): string {
  return sanitizeHtml(html, 'code')
}

/**
 * 消毒 SVG/图标内容
 */
export function sanitizeIconHtml(html: string): string {
  return sanitizeHtml(html, 'icon')
}

/**
 * 消毒链接文本
 */
export function sanitizeLinkHtml(html: string): string {
  return sanitizeHtml(html, 'link')
}

/**
 * 严格消毒，几乎移除所有 HTML
 */
export function sanitizeStrictHtml(html: string): string {
  return sanitizeHtml(html, 'strict')
}

/**
 * 转义 HTML 特殊字符（不使用 DOMPurify，直接字符替换）
 * 用于完全不需要任何 HTML 的场景
 */
export function escapeHtml(text: string): string {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

/**
 * 检查字符串是否包含潜在的 XSS 攻击向量
 * 仅用于日志/监控，不应作为唯一的安全措施
 */
export function containsXssPatterns(text: string): boolean {
  if (!text) return false
  const patterns = [
    /<script\b/i,
    /javascript:/i,
    /on\w+\s*=/i, // onclick=, onerror=, etc.
    /data:/i,
    /<iframe\b/i,
    /<object\b/i,
    /<embed\b/i,
    /<form\b/i
  ]
  return patterns.some(pattern => pattern.test(text))
}
