/**
 * @ 符号检测工具
 * 检测光标位置是否在 @ 查询中
 */

export interface AtQueryResult {
  atPosition: number  // @ 符号的位置
  query: string       // 查询字符串（@ 符号后的文本）
}

/**
 * 检查光标位置是否在 @ 查询中
 * 
 * 触发条件：
 * 1. 光标必须在 @ 符号后的查询文本中（在 @xxxx 的中间）
 * 2. @ 符号前必须是空格/回车/文本开头
 * 3. 光标到 @ 之间不能有空格/回车
 * 
 * @param text 输入文本
 * @param cursorPosition 光标位置
 * @returns 如果在 @ 查询中，返回 @ 位置和查询字符串；否则返回 null
 */
export function isInAtQuery(text: string, cursorPosition: number): AtQueryResult | null {
  if (cursorPosition <= 0 || cursorPosition > text.length) {
    return null
  }

  // 向前查找最近的 @ 符号，确保光标到 @ 之间没有空白字符
  let atPosition = -1
  for (let i = cursorPosition - 1; i >= 0; i--) {
    const char = text[i]
    if (char === '@') {
      atPosition = i
      break
    }
    // 遇到空白字符则说明不在 @ 查询中
    if (char === ' ' || char === '\n' || char === '\t') {
      return null
    }
  }

  if (atPosition === -1) {
    return null
  }

  // 检查 @ 符号前的字符是否合法（空格/回车/文本开头）
  if (atPosition > 0) {
    const beforeAtChar = text[atPosition - 1]
    if (beforeAtChar !== ' ' && beforeAtChar !== '\n' && beforeAtChar !== '\t') {
      // @ 符号前不是空白字符或文本开头，不触发
      return null
    }
  }

  // 确保光标在 @ 符号后面（包括紧跟 @ 符号的情况）
  if (cursorPosition < atPosition + 1) {
    return null
  }

  // 提取查询字符串（@ 符号后到光标位置的文本）
  const queryStart = atPosition + 1
  const query = text.substring(queryStart, cursorPosition)

  return {
    atPosition,
    query
  }
}

/**
 * 替换 @ 查询为文件引用
 * 
 * @param text 原始文本
 * @param atPosition @ 符号位置
 * @param cursorPosition 当前光标位置
 * @param fileReference 文件引用（如 @src/main.ts）
 * @returns 新的文本和光标位置
 */
export function replaceAtQuery(
  text: string,
  atPosition: number,
  cursorPosition: number,
  fileReference: string
): { newText: string; newCursorPosition: number } {
  // 找到查询的结束位置
  const queryStart = atPosition + 1
  let queryEnd = cursorPosition
  
  for (let i = queryStart; i < text.length; i++) {
    const char = text[i]
    if (char === ' ' || char === '\n' || char === '\t') {
      queryEnd = i
      break
    }
  }
  
  // 如果查询结束位置是文本末尾，则 queryEnd 就是 text.length
  if (queryEnd === cursorPosition && cursorPosition === text.length) {
    queryEnd = text.length
  }

  // 检查替换后的位置是否需要添加空格
  const needsSpace = queryEnd >= text.length || 
                     (queryEnd < text.length && 
                      text[queryEnd] !== ' ' && 
                      text[queryEnd] !== '\n' && 
                      text[queryEnd] !== '\t')

  // 构建新文本
  const before = text.substring(0, atPosition)
  const after = text.substring(queryEnd)
  const newText = before + fileReference + (needsSpace ? ' ' : '') + after

  // 计算新的光标位置（在文件引用后面）
  const newCursorPosition = atPosition + fileReference.length + (needsSpace ? 1 : 0)

  return {
    newText,
    newCursorPosition
  }
}

