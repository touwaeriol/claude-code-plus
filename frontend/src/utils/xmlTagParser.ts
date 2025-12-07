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
