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
