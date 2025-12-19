/**
 * XML 标签解析器
 *
 * 用于解析 Claude 返回的本地命令输出标签
 * 如 <local-command-stdout>Compacted</local-command-stdout>
 */

export interface ParsedLocalCommand {
  type: 'stdout' | 'stderr'
  content: string
}

/**
 * 解析本地命令输出标签
 *
 * @param text 要解析的文本
 * @returns 解析结果，如果不是本地命令输出则返回 null
 */
export function parseLocalCommandTags(text: string): ParsedLocalCommand | null {
  // 匹配 <local-command-stdout>...</local-command-stdout>
  const stdoutMatch = text.match(/<local-command-stdout>([\s\S]*?)<\/local-command-stdout>/)
  if (stdoutMatch) {
    return {
      type: 'stdout',
      content: stdoutMatch[1].trim()
    }
  }

  // 匹配 <local-command-stderr>...</local-command-stderr>
  const stderrMatch = text.match(/<local-command-stderr>([\s\S]*?)<\/local-command-stderr>/)
  if (stderrMatch) {
    return {
      type: 'stderr',
      content: stderrMatch[1].trim()
    }
  }

  return null
}

/**
 * 检查文本是否包含本地命令输出标签
 */
export function hasLocalCommandTags(text: string): boolean {
  return /<local-command-(stdout|stderr)>/.test(text)
}

// ========== 当前打开文件标记解析 ==========

/**
 * 解析后的当前打开文件标记
 */
export interface ParsedCurrentOpenFile {
  path: string
  // 光标位置（仅光标模式）
  line?: number
  column?: number
  // 选区范围（选区模式）
  startLine?: number
  startColumn?: number
  endLine?: number
  endColumn?: number
  // 选中的内容
  selectedContent?: string
}

/**
 * 解析 <current-open-file/> 标记
 *
 * 支持的格式：
 * - <current-open-file path="path"/>
 * - <current-open-file path="path" line="N" column="M"/>
 * - <current-open-file path="path" start-line="N" start-column="M" end-line="X" end-column="Y"/>
 * - <current-open-file path="path" ... selected-content="..."/>
 *
 * @param text 要解析的文本
 * @returns 解析结果，如果不是 current-open-file 标记则返回 null
 */
export function parseCurrentOpenFileTag(text: string): ParsedCurrentOpenFile | null {
  // 匹配自闭合标签 <current-open-file ... />
  const match = text.match(/<current-open-file\s+([^>]+)\/>/)
  if (!match) {
    return null
  }

  const attrsStr = match[1]

  // 解析属性
  const getAttr = (name: string): string | undefined => {
    const attrMatch = attrsStr.match(new RegExp(`${name}="([^"]*)"`, 'i'))
    return attrMatch ? attrMatch[1] : undefined
  }

  const path = getAttr('path')
  if (!path) {
    return null
  }

  const result: ParsedCurrentOpenFile = { path }

  // 解析光标位置
  const line = getAttr('line')
  const column = getAttr('column')
  if (line) result.line = parseInt(line, 10)
  if (column) result.column = parseInt(column, 10)

  // 解析选区范围
  const startLine = getAttr('start-line')
  const startColumn = getAttr('start-column')
  const endLine = getAttr('end-line')
  const endColumn = getAttr('end-column')
  if (startLine) result.startLine = parseInt(startLine, 10)
  if (startColumn) result.startColumn = parseInt(startColumn, 10)
  if (endLine) result.endLine = parseInt(endLine, 10)
  if (endColumn) result.endColumn = parseInt(endColumn, 10)

  // 解析选中的内容（需要 XML 反转义）
  const selectedContent = getAttr('selected-content')
  if (selectedContent) {
    result.selectedContent = selectedContent
      .replace(/&quot;/g, '"')
      .replace(/&gt;/g, '>')
      .replace(/&lt;/g, '<')
      .replace(/&amp;/g, '&')
  }

  return result
}

/**
 * 检查文本是否包含 current-open-file 标记
 */
export function hasCurrentOpenFileTag(text: string): boolean {
  return /<current-open-file\s+[^>]+\/>/.test(text)
}

/**
 * 从文本中移除 current-open-file 标记，返回剩余文本
 */
export function removeCurrentOpenFileTag(text: string): string {
  return text.replace(/<current-open-file\s+[^>]+\/>\s*/g, '').trim()
}

// ========== 通用 XML 标签解析（使用 DOMParser） ==========

/**
 * XML 标签信息
 */
export interface XmlTagInfo {
  /** 标签名 */
  tagName: string
  /** 属性键值对 */
  attributes: Record<string, string>
  /** 是否是结束标签 </xxx> */
  isEndTag: boolean
  /** 是否是自闭合标签 <xxx/> */
  isSelfClosing: boolean
  /** 标签内的文本内容（仅带内容的标签有） */
  textContent?: string
}

// DOMParser 实例（复用）
const domParser = new DOMParser()

/**
 * 解析 XML 标签（使用 DOMParser）
 *
 * 支持：
 * - 结束标签：</tag-name>
 * - 自闭合标签：<tag-name attr="value"/>
 * - 开始标签：<tag-name attr="value">
 * - 带内容的标签：<tag-name attr="value">content</tag-name>
 *
 * @param text 要解析的文本
 * @returns 标签信息，非 XML 标签返回 null
 */
export function parseXmlTag(text: string): XmlTagInfo | null {
  const trimmed = text.trim()
  if (!trimmed.startsWith('<')) return null

  // 1. 结束标签 </xxx>
  const endTagMatch = trimmed.match(/^<\/(\w[\w-]*)>$/)
  if (endTagMatch) {
    return {
      tagName: endTagMatch[1],
      attributes: {},
      isEndTag: true,
      isSelfClosing: false
    }
  }

  // 2. 准备 XML 文本用于 DOMParser
  let xmlText = trimmed
  const isSelfClosing = trimmed.endsWith('/>')

  // 如果是开始标签（不闭合），补上闭合标签
  if (!isSelfClosing && trimmed.endsWith('>') && !trimmed.includes('</')) {
    const tagNameMatch = trimmed.match(/^<(\w[\w-]*)/)
    if (tagNameMatch) {
      const tagName = tagNameMatch[1]
      // <tag attr="value"> → <tag attr="value"></tag>
      xmlText = trimmed.slice(0, -1) + `></${tagName}>`
    }
  }

  // 3. 使用 DOMParser 解析
  try {
    const doc = domParser.parseFromString(xmlText, 'text/xml')

    // 检查解析错误
    const parseError = doc.querySelector('parsererror')
    if (parseError) return null

    const element = doc.documentElement
    if (!element) return null

    // 提取属性
    const attributes: Record<string, string> = {}
    for (const attr of element.attributes) {
      attributes[attr.name] = attr.value
    }

    return {
      tagName: element.tagName,
      attributes,
      isEndTag: false,
      isSelfClosing,
      textContent: element.textContent || undefined
    }
  } catch {
    return null
  }
}

// ========== System Reminder 标签解析（新格式） ==========

/**
 * System Reminder 类型
 */
export type SystemReminderType = 'open-file' | 'select-lines' | 'attachment'

/**
 * 解析后的 open-file 系统提醒
 */
export interface ParsedOpenFileReminder {
  type: 'open-file'
  path: string
  /** 文件类型：text, diff, image, binary */
  fileType?: string
  /** Diff 标题（仅 diff 类型） */
  diffTitle?: string
}

/**
 * 解析后的 select-lines 系统提醒
 */
export interface ParsedSelectLinesReminder {
  type: 'select-lines'
  path: string
  start: number
  end: number
  startColumn?: number
  endColumn?: number
  /** 选中的内容 */
  content?: string
}

/**
 * 解析后的 attachment 系统提醒（标记开始/结束）
 */
export interface ParsedAttachmentReminder {
  type: 'attachment'
  /** 是开始标签还是结束标签 */
  isStart: boolean
}

/**
 * 所有系统提醒类型的联合
 */
export type ParsedSystemReminder =
  | ParsedOpenFileReminder
  | ParsedSelectLinesReminder
  | ParsedAttachmentReminder

/**
 * 解析 system-reminder 标签（使用 parseXmlTag）
 *
 * 支持的格式：
 * - <system-reminder type="open-file" path="xxx"/>
 * - <system-reminder type="select-lines" path="xxx" start="N" end="M">内容</system-reminder>
 * - <system-reminder type="attachment"> 或 </system-reminder>
 *
 * @param text 要解析的文本
 * @returns 解析结果，如果不是 system-reminder 标记则返回 null
 */
export function parseSystemReminder(text: string): ParsedSystemReminder | null {
  const tagInfo = parseXmlTag(text)
  if (!tagInfo) return null
  if (tagInfo.tagName !== 'system-reminder') return null

  // 结束标签 </system-reminder>
  if (tagInfo.isEndTag) {
    return {
      type: 'attachment',
      isStart: false
    }
  }

  const reminderType = tagInfo.attributes['type'] as SystemReminderType
  if (!reminderType) return null

  switch (reminderType) {
    case 'open-file': {
      const path = tagInfo.attributes['path']
      if (!path) return null
      return {
        type: 'open-file',
        path,
        fileType: tagInfo.attributes['file-type'],
        diffTitle: tagInfo.attributes['diff-title']
      }
    }

    case 'select-lines': {
      const path = tagInfo.attributes['path']
      const start = tagInfo.attributes['start']
      const end = tagInfo.attributes['end']
      if (!path || !start || !end) return null
      return {
        type: 'select-lines',
        path,
        start: parseInt(start, 10),
        end: parseInt(end, 10),
        startColumn: tagInfo.attributes['start-column'] ? parseInt(tagInfo.attributes['start-column'], 10) : undefined,
        endColumn: tagInfo.attributes['end-column'] ? parseInt(tagInfo.attributes['end-column'], 10) : undefined,
        content: tagInfo.textContent
      }
    }

    case 'attachment': {
      return {
        type: 'attachment',
        isStart: true
      }
    }

    default:
      return null
  }
}

/**
 * 检查文本是否是 system-reminder 标签
 */
export function isSystemReminderTag(text: string): boolean {
  const tagInfo = parseXmlTag(text)
  if (!tagInfo) return false
  return tagInfo.tagName === 'system-reminder'
}

/**
 * 检查文本是否是 system-reminder 开始标签（attachment 类型）
 */
export function isAttachmentStartTag(text: string): boolean {
  const tagInfo = parseXmlTag(text)
  if (!tagInfo || tagInfo.isEndTag) return false
  return tagInfo.tagName === 'system-reminder' && tagInfo.attributes['type'] === 'attachment'
}

/**
 * 检查文本是否是 system-reminder 结束标签
 */
export function isAttachmentEndTag(text: string): boolean {
  const tagInfo = parseXmlTag(text)
  if (!tagInfo) return false
  return tagInfo.tagName === 'system-reminder' && tagInfo.isEndTag
}

/**
 * XML 转义
 */
export function escapeXml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
