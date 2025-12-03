/**
 * 会话搜索服务
 * 提供会话搜索、高亮匹配、片段提取等功能
 */

import type { Session } from '@/types/session'
import type { UnifiedMessage, TextContent } from '@/types/message'

type Message = UnifiedMessage

export interface SearchOptions {
  searchInTitles?: boolean
  searchInContent?: boolean
  caseSensitive?: boolean
  snippetLength?: number
  maxMessagesPerSession?: number
  maxResults?: number
}

export interface MessageMatch {
  messageId: string
  snippet: string
  highlights: Array<{ start: number; end: number }>
  matchType: 'TITLE' | 'CONTENT'
}

export interface SessionSearchResult {
  sessionId: string
  sessionName: string
  matchedMessages: MessageMatch[]
  relevanceScore: number
  timestamp: number
}

export class SessionSearchService {
  /**
   * 搜索会话
   */
  async search(
    query: string,
    sessions: Session[],
    messagesMap: Map<string, Message[]>,
    options: SearchOptions = {}
  ): Promise<SessionSearchResult[]> {
    if (!query.trim()) {
      return []
    }

    const {
      searchInTitles = true,
      searchInContent = true,
      caseSensitive = false,
      snippetLength = 100,
      maxMessagesPerSession = 5,
      maxResults = 50
    } = options

    const results: SessionSearchResult[] = []
    const queryTerms = this.tokenizeQuery(query, caseSensitive)

    for (const session of sessions) {
      const matchedMessages: MessageMatch[] = []
      let relevanceScore = 0

      // 搜索会话标题
      if (searchInTitles) {
        const titleMatches = this.findMatches(session.name, queryTerms, caseSensitive)
        if (titleMatches.length > 0) {
          relevanceScore += 2.0 // 标题匹配权重更高
          matchedMessages.push({
            messageId: 'title',
            snippet: session.name,
            highlights: titleMatches,
            matchType: 'TITLE'
          })
        }
      }

      // 搜索消息内容
      if (searchInContent) {
        const messages = messagesMap.get(session.id) || []
        let contentMatchCount = 0

        for (const message of messages) {
          if (maxMessagesPerSession && contentMatchCount >= maxMessagesPerSession) {
            break
          }

          const content = this.extractTextContent(message)
          const contentMatches = this.findMatches(content, queryTerms, caseSensitive)

          if (contentMatches.length > 0) {
            relevanceScore += 1.0
            contentMatchCount++

            const snippet = this.extractSnippet(content, contentMatches[0], snippetLength)
            const adjustedHighlights = this.adjustHighlightsForSnippet(
              contentMatches,
              content,
              snippet
            )

            matchedMessages.push({
              messageId: message.id,
              snippet,
              highlights: adjustedHighlights,
              matchType: 'CONTENT'
            })
          }
        }
      }

      // 添加搜索结果
      if (matchedMessages.length > 0) {
        results.push({
          sessionId: session.id,
          sessionName: session.name,
          matchedMessages,
          relevanceScore,
          timestamp: session.createdAt
        })
      }
    }

    // 按相关性排序
    results.sort((a, b) => b.relevanceScore - a.relevanceScore)

    // 限制结果数量
    return maxResults ? results.slice(0, maxResults) : results
  }

  /**
   * 分词
   */
  private tokenizeQuery(query: string, caseSensitive: boolean): string[] {
    const processedQuery = caseSensitive ? query : query.toLowerCase()
    return processedQuery
      .split(/\s+/)
      .filter(term => term.length > 0)
  }

  /**
   * 查找匹配
   */
  private findMatches(
    text: string,
    queryTerms: string[],
    caseSensitive: boolean
  ): Array<{ start: number; end: number }> {
    const matches: Array<{ start: number; end: number }> = []
    const searchText = caseSensitive ? text : text.toLowerCase()

    for (const term of queryTerms) {
      let index = 0
      while (true) {
        index = searchText.indexOf(term, index)
        if (index === -1) break
        matches.push({ start: index, end: index + term.length })
        index += term.length
      }
    }

    // 合并重叠的范围
    return this.mergeRanges(matches)
  }

  /**
   * 提取文本内容
   */
  private extractTextContent(message: Message): string {
    // 提取所有文本块的内容
    return message.content
      .filter((block): block is TextContent => block.type === 'text')
      .map(block => block.text || '')
      .join('\n')
  }

  /**
   * 提取片段
   */
  private extractSnippet(
    text: string,
    firstMatch: { start: number; end: number },
    maxLength: number
  ): string {
    const contextBefore = 30
    const contextAfter = maxLength - contextBefore - (firstMatch.end - firstMatch.start)

    const start = Math.max(0, firstMatch.start - contextBefore)
    const end = Math.min(text.length, firstMatch.end + contextAfter)

    let snippet = text.substring(start, end)

    // 添加省略号
    if (start > 0) snippet = '...' + snippet
    if (end < text.length) snippet = snippet + '...'

    return snippet
  }

  /**
   * 调整高亮范围
   */
  private adjustHighlightsForSnippet(
    highlights: Array<{ start: number; end: number }>,
    fullText: string,
    snippet: string
  ): Array<{ start: number; end: number }> {
    const snippetStart = fullText.indexOf(snippet.replace(/^\.\.\./, '').replace(/\.\.\.$/, ''))
    if (snippetStart === -1) return []

    return highlights
      .filter(h => h.start >= snippetStart && h.end <= snippetStart + snippet.length)
      .map(h => ({
        start: h.start - snippetStart,
        end: h.end - snippetStart
      }))
  }

  /**
   * 合并重叠的范围
   */
  private mergeRanges(
    ranges: Array<{ start: number; end: number }>
  ): Array<{ start: number; end: number }> {
    if (ranges.length === 0) return []

    // 按起始位置排序
    const sorted = [...ranges].sort((a, b) => a.start - b.start)
    const merged: Array<{ start: number; end: number }> = [sorted[0]]

    for (let i = 1; i < sorted.length; i++) {
      const current = sorted[i]
      const last = merged[merged.length - 1]

      if (current.start <= last.end) {
        // 重叠，合并
        last.end = Math.max(last.end, current.end)
      } else {
        // 不重叠，添加新范围
        merged.push(current)
      }
    }

    return merged
  }

  /**
   * 高亮文本
   */
  highlightText(
    text: string,
    highlights: Array<{ start: number; end: number }>
  ): Array<{ text: string; highlighted: boolean }> {
    if (highlights.length === 0) {
      return [{ text, highlighted: false }]
    }

    const result: Array<{ text: string; highlighted: boolean }> = []
    let lastIndex = 0

    for (const highlight of highlights) {
      // 添加高亮前的文本
      if (highlight.start > lastIndex) {
        result.push({
          text: text.substring(lastIndex, highlight.start),
          highlighted: false
        })
      }

      // 添加高亮文本
      result.push({
        text: text.substring(highlight.start, highlight.end),
        highlighted: true
      })

      lastIndex = highlight.end
    }

    // 添加剩余文本
    if (lastIndex < text.length) {
      result.push({
        text: text.substring(lastIndex),
        highlighted: false
      })
    }

    return result
  }
}

// 导出单例
export const sessionSearchService = new SessionSearchService()
