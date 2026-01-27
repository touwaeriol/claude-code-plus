/**
 * Diff 内容处理辅助类
 * 负责重建修改前的文件内容和字符串匹配
 * 
 * 翻译自: jetbrains-plugin/.../tools/DiffContentHelper.kt
 */

// 日志工具
const log = {
  info: (msg: string) => console.log(`[DiffContentHelper] ${msg}`),
  warn: (msg: string) => console.warn(`[DiffContentHelper] ${msg}`),
};

export interface EditOperation {
  oldString: string;
  newString: string;
  replaceAll: boolean;
}

export class DiffContentHelper {
  /**
   * 从当前文件内容逆向重建修改前的内容
   *
   * 注意：如果文件被 linter/formatter 修改过，newString 可能无法精确匹配。
   * 此时会尝试标准化空白后再匹配，如果仍失败则跳过该操作。
   */
  rebuildBeforeContent(afterContent: string, operations: EditOperation[]): string {
    let content = afterContent;

    // 逆序处理操作
    const reversedOps = [...operations].reverse();

    for (const operation of reversedOps) {
      if (operation.replaceAll) {
        if (content.includes(operation.newString)) {
          content = content.split(operation.newString).join(operation.oldString);
        } else {
          // 尝试标准化空白后匹配
          const normalizedNew = this.normalizeWhitespace(operation.newString);
          const normalizedContent = this.normalizeWhitespace(content);

          if (normalizedContent.includes(normalizedNew)) {
            // 找到标准化匹配，使用原始 oldString 替换（保持格式）
            content = this.replaceNormalized(content, operation.newString, operation.oldString);
          } else {
            log.warn('⚠️ rebuildBeforeContent: newString not found (replace_all), skipping operation');
            // 继续处理其他操作，不抛出异常
          }
        }
      } else {
        const index = content.indexOf(operation.newString);

        if (index >= 0) {
          content =
            content.substring(0, index) +
            operation.oldString +
            content.substring(index + operation.newString.length);
        } else {
          // 尝试标准化空白后匹配
          const fuzzyIndex = this.findNormalizedIndex(content, operation.newString);

          if (fuzzyIndex >= 0) {
            // 找到模糊匹配位置，计算实际结束位置
            const actualEnd = this.findActualEndIndex(content, fuzzyIndex, operation.newString);
            content =
              content.substring(0, fuzzyIndex) +
              operation.oldString +
              content.substring(actualEnd);
          } else {
            log.warn('⚠️ rebuildBeforeContent: newString not found, skipping operation');
            // 继续处理其他操作，不抛出异常
          }
        }
      }
    }

    log.info(`✅ Successfully rebuilt before content (${operations.length} operations)`);
    return content;
  }

  /**
   * 标准化空白字符（用于模糊匹配）
   */
  normalizeWhitespace(s: string): string {
    return s.replace(/\s+/g, ' ').trim();
  }

  /**
   * 在标准化空白后查找子串位置
   */
  private findNormalizedIndex(content: string, target: string): number {
    const normalizedTarget = this.normalizeWhitespace(target);
    const lines = content.split('\n');
    let charIndex = 0;

    for (let lineIdx = 0; lineIdx < lines.length; lineIdx++) {
      const line = lines[lineIdx];
      // 尝试在当前行开始的多行区域中匹配
      const remainingContent = lines.slice(lineIdx).join('\n');
      const normalizedRemaining = this.normalizeWhitespace(remainingContent);

      if (
        normalizedRemaining.startsWith(normalizedTarget) ||
        normalizedRemaining.includes(normalizedTarget)
      ) {
        // 找到了匹配的起始位置
        return charIndex;
      }
      charIndex += line.length + 1; // +1 for newline
    }

    return -1;
  }

  /**
   * 找到实际的结束索引（考虑空白差异）
   */
  private findActualEndIndex(content: string, startIndex: number, target: string): number {
    const normalizedTarget = this.normalizeWhitespace(target);
    const targetNormalizedLen = normalizedTarget.length;

    let normalizedCount = 0;
    let actualIndex = startIndex;

    while (actualIndex < content.length && normalizedCount < targetNormalizedLen) {
      const c = content[actualIndex];
      const isWhitespace = /\s/.test(c);

      if (!isWhitespace || (normalizedCount > 0 && normalizedTarget[normalizedCount] === ' ')) {
        normalizedCount++;
      }
      actualIndex++;
    }

    // 跳过尾部空白（但不跳过换行符）
    while (actualIndex < content.length && /[ \t]/.test(content[actualIndex])) {
      actualIndex++;
    }

    return actualIndex;
  }

  /**
   * 使用标准化匹配进行替换
   */
  private replaceNormalized(content: string, target: string, replacement: string): string {
    const index = this.findNormalizedIndex(content, target);
    if (index < 0) return content;

    const endIndex = this.findActualEndIndex(content, index, target);
    return content.substring(0, index) + replacement + content.substring(endIndex);
  }
}
