/**
 * Codex App-Server 事件流适配器
 *
 * 翻译自: ai-agent-sdk/.../adapter/CodexAppServerStreamAdapter.kt
 *
 * 将 AppServerEvent 转换为 NormalizedStreamEvent，
 * 用于统一的 UI 事件处理。
 */

import * as crypto from 'crypto'
import type { AppServerEvent } from '../appServer/client'
import type {
  ThreadItem,
  ThreadItemCommandExecution,
  ThreadItemFileChange,
  ThreadItemMcpToolCall,
  ThreadItemWebSearch,
  ThreadTokenUsage,
} from '../types'

// NormalizedStreamEvent 类型定义
export type NormalizedStreamEvent =
  | { type: 'messageStarted'; provider: string; sessionId: string; messageId: string }
  | { type: 'turnStarted'; provider: string }
  | {
      type: 'contentStarted'
      provider: string
      index: number
      contentType: string
      toolName?: string
      content?: unknown
    }
  | {
      type: 'contentDelta'
      provider: string
      index: number
      delta: { type: string; text?: string; thinking?: string }
    }
  | { type: 'contentCompleted'; provider: string; index: number; content?: unknown }
  | { type: 'turnCompleted'; provider: string; usage?: unknown }
  | { type: 'turnFailed'; provider: string; error: string }
  | { type: 'assistantMessage'; provider: string; id?: string; content: unknown[]; tokenUsage?: unknown }
  | {
      type: 'resultSummary'
      provider: string
      subtype: string
      durationMs: number
      isError: boolean
      sessionId: string
      result?: string
    }

export class CodexAppServerStreamAdapter {
  private sessionIdProvider: () => string | undefined
  private idGenerator: () => string

  private itemIdToIndex = new Map<string, number>()
  private indexCounter = 0
  private turnStartTimeMs: number | null = null
  private currentSessionId: string | undefined
  private lastUsage: ThreadTokenUsage | null = null

  // 累积内容块
  private textByIndex = new Map<number, string>()
  private thinkingByIndex = new Map<number, string>()
  private toolUseByIndex = new Map<number, unknown>()
  private currentMessageId: string | undefined

  constructor(sessionIdProvider: () => string | undefined, idGenerator: () => string = () => crypto.randomUUID()) {
    this.sessionIdProvider = sessionIdProvider
    this.idGenerator = idGenerator
  }

  convert(event: AppServerEvent): NormalizedStreamEvent[] {
    const result: NormalizedStreamEvent[] = []
    const provider = 'codex'

    switch (event.type) {
      case 'threadStarted':
        this.currentSessionId = this.sessionIdProvider() || this.currentSessionId
        break

      case 'turnStarted':
        this.turnStartTimeMs = Date.now()
        this.indexCounter = 0
        this.itemIdToIndex.clear()
        this.lastUsage = null
        this.textByIndex.clear()
        this.thinkingByIndex.clear()
        this.toolUseByIndex.clear()
        this.currentMessageId = event.turn.id

        result.push({
          type: 'messageStarted',
          provider,
          sessionId: this.resolveSessionId(),
          messageId: event.turn.id,
        })
        result.push({ type: 'turnStarted', provider })
        break

      case 'itemStarted': {
        const index = this.nextIndexForItem(event.item.id)
        const toolContent = this.buildToolUseContent(event.item)
        if (toolContent) {
          this.toolUseByIndex.set(index, toolContent)
        }
        result.push({
          type: 'contentStarted',
          provider,
          index,
          contentType: this.resolveContentType(event.item),
          toolName: toolContent?.name || this.resolveToolName(event.item),
          content: toolContent,
        })
        break
      }

      case 'itemCompleted': {
        const index = this.itemIdToIndex.get(event.item.id)
        if (index !== undefined) {
          this.itemIdToIndex.delete(event.item.id)
          result.push({
            type: 'contentCompleted',
            provider,
            index,
            content: this.buildToolResultContent(event.item),
          })
        }
        break
      }

      case 'agentMessageDelta': {
        const { index, started } = this.ensureIndexWithStart(event.itemId)
        if (started) {
          result.push({
            type: 'contentStarted',
            provider,
            index,
            contentType: 'text',
          })
        }
        const existing = this.textByIndex.get(index) || ''
        this.textByIndex.set(index, existing + event.delta)
        result.push({
          type: 'contentDelta',
          provider,
          index,
          delta: { type: 'text', text: event.delta },
        })
        break
      }

      case 'reasoningDelta': {
        const { index, started } = this.ensureIndexWithStart(event.itemId)
        if (started) {
          result.push({
            type: 'contentStarted',
            provider,
            index,
            contentType: 'thinking',
          })
        }
        const existing = this.thinkingByIndex.get(index) || ''
        this.thinkingByIndex.set(index, existing + event.delta)
        result.push({
          type: 'contentDelta',
          provider,
          index,
          delta: { type: 'thinking', thinking: event.delta },
        })
        break
      }

      case 'commandOutputDelta': {
        const { index, started } = this.ensureIndexWithStart(event.itemId)
        if (started) {
          result.push({
            type: 'contentStarted',
            provider,
            index,
            contentType: 'command_output',
          })
        }
        result.push({
          type: 'contentDelta',
          provider,
          index,
          delta: { type: 'text', text: event.delta },
        })
        break
      }

      case 'tokenUsageUpdated':
        this.lastUsage = event.usage
        break

      case 'turnCompleted': {
        const durationMs = this.resolveDurationMs()
        const status = event.turn.status

        if (status === 'failed') {
          const errorMsg = event.turn.error?.message || 'Codex turn failed'
          result.push({
            type: 'resultSummary',
            provider,
            subtype: 'error_during_execution',
            durationMs,
            isError: true,
            sessionId: this.resolveSessionId(),
            result: errorMsg,
          })
          result.push({ type: 'turnFailed', provider, error: errorMsg })
        } else if (status === 'interrupted') {
          result.push({
            type: 'resultSummary',
            provider,
            subtype: 'interrupted',
            durationMs,
            isError: false,
            sessionId: this.resolveSessionId(),
            result: 'Turn interrupted',
          })
          result.push({ type: 'turnFailed', provider, error: 'Turn interrupted' })
        } else {
          // completed or inProgress
          const contentBlocks = this.buildContentBlocks()
          if (contentBlocks.length > 0) {
            result.push({
              type: 'assistantMessage',
              provider,
              id: this.currentMessageId,
              content: contentBlocks,
              tokenUsage: this.lastUsage,
            })
          }
          result.push({ type: 'turnCompleted', provider, usage: this.lastUsage })
          result.push({
            type: 'resultSummary',
            provider,
            subtype: 'completed',
            durationMs,
            isError: false,
            sessionId: this.resolveSessionId(),
          })
        }
        break
      }

      case 'error': {
        const durationMs = this.resolveDurationMs()
        result.push({
          type: 'resultSummary',
          provider,
          subtype: 'error_during_execution',
          durationMs,
          isError: true,
          sessionId: this.resolveSessionId(),
          result: event.message || 'Codex error',
        })
        result.push({ type: 'turnFailed', provider, error: event.message })
        break
      }
    }

    return result
  }

  private resolveSessionId(): string {
    return this.currentSessionId || this.sessionIdProvider() || 'unknown'
  }

  private resolveDurationMs(): number {
    return this.turnStartTimeMs ? Date.now() - this.turnStartTimeMs : 0
  }

  private nextIndexForItem(itemId: string): number {
    const index = this.indexCounter++
    this.itemIdToIndex.set(itemId, index)
    return index
  }

  private ensureIndexWithStart(itemId: string): { index: number; started: boolean } {
    const existing = this.itemIdToIndex.get(itemId)
    if (existing !== undefined) {
      return { index: existing, started: false }
    }
    const index = this.nextIndexForItem(itemId)
    return { index, started: true }
  }

  private resolveContentType(item: ThreadItem): string {
    switch (item.type) {
      case 'agentMessage':
      case 'userMessage':
      case 'enteredReviewMode':
      case 'exitedReviewMode':
      case 'imageView':
        return 'text'
      case 'reasoning':
        return 'thinking'
      case 'commandExecution':
        return 'command_execution'
      case 'fileChange':
        return 'file_change'
      case 'mcpToolCall':
        return 'tool_use'
      case 'webSearch':
        return 'web_search'
      default:
        return 'text'
    }
  }

  private resolveToolName(item: ThreadItem): string | undefined {
    switch (item.type) {
      case 'commandExecution':
        return 'Bash'
      case 'fileChange':
        return 'Edit'
      case 'mcpToolCall': {
        const mcp = item as ThreadItemMcpToolCall
        return `mcp__${mcp.server}__${mcp.tool}`
      }
      case 'webSearch':
        return 'WebSearch'
      default:
        return undefined
    }
  }

  private buildToolUseContent(item: ThreadItem): { id: string; name: string; input: unknown } | null {
    switch (item.type) {
      case 'commandExecution': {
        const cmd = item as ThreadItemCommandExecution
        return {
          id: cmd.id,
          name: 'Bash',
          input: { command: cmd.command, cwd: cmd.cwd },
        }
      }
      case 'fileChange': {
        const fc = item as ThreadItemFileChange
        return {
          id: fc.id,
          name: this.resolveFileChangeToolName(fc),
          input: { changes: fc.changes },
        }
      }
      case 'mcpToolCall': {
        const mcp = item as ThreadItemMcpToolCall
        return {
          id: mcp.id,
          name: `mcp__${mcp.server}__${mcp.tool}`,
          input: mcp.arguments,
        }
      }
      case 'webSearch': {
        const ws = item as ThreadItemWebSearch
        return {
          id: ws.id,
          name: 'WebSearch',
          input: { query: ws.query },
        }
      }
      default:
        return null
    }
  }

  private buildToolResultContent(item: ThreadItem): unknown {
    switch (item.type) {
      case 'commandExecution': {
        const cmd = item as ThreadItemCommandExecution
        return {
          toolUseId: cmd.id,
          content: cmd.aggregatedOutput,
          isError:
            cmd.status === 'failed' || cmd.status === 'declined' || (cmd.exitCode !== undefined && cmd.exitCode !== 0),
        }
      }
      case 'fileChange': {
        const fc = item as ThreadItemFileChange
        return {
          toolUseId: fc.id,
          content: { status: fc.status, changes: fc.changes },
          isError: fc.status === 'failed' || fc.status === 'declined',
        }
      }
      case 'mcpToolCall': {
        const mcp = item as ThreadItemMcpToolCall
        return {
          toolUseId: mcp.id,
          content: mcp.result?.structuredContent || mcp.result?.content,
          isError: mcp.status === 'failed' || !!mcp.error,
        }
      }
      default:
        return { type: item.type, id: item.id }
    }
  }

  private resolveFileChangeToolName(item: ThreadItemFileChange): string {
    const primary = item.changes[0]
    return primary?.kind.type === 'add' ? 'Write' : 'Edit'
  }

  private buildContentBlocks(): unknown[] {
    const blocks: unknown[] = []

    // 合并所有索引
    const allIndices = new Set([
      ...this.textByIndex.keys(),
      ...this.thinkingByIndex.keys(),
      ...this.toolUseByIndex.keys(),
    ])

    const sortedIndices = Array.from(allIndices).sort((a, b) => a - b)

    for (const index of sortedIndices) {
      const text = this.textByIndex.get(index)
      if (text) {
        blocks.push({ type: 'text', text })
      }

      const thinking = this.thinkingByIndex.get(index)
      if (thinking) {
        blocks.push({ type: 'thinking', thinking, signature: '' })
      }

      const toolUse = this.toolUseByIndex.get(index)
      if (toolUse) {
        blocks.push({ type: 'tool_use', ...toolUse })
      }
    }

    return blocks
  }
}
