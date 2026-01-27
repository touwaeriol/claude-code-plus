/**
 * Session Store - Terminal 后台运行辅助
 */

import { ideaRSocket, TerminalBackgroundStatus } from '@/services/ideaRSocket'

export interface TerminalBackgroundResult {
  success: boolean
  count: number
  backgroundedIds: string[]
  error?: string
}

/**
 * 运行终端任务到后台
 * 
 * @param toolUseId 可选的工具调用 ID，不传则后台化所有可后台化的终端任务
 */
export async function runTerminalBackground(toolUseId?: string): Promise<TerminalBackgroundResult> {
  try {
    // Get backgroundable terminals
    const terminals = await ideaRSocket.getBackgroundableTerminals()
    
    // Filter by toolUseId if provided
    const items = toolUseId 
      ? terminals.filter(t => t.toolUseId === toolUseId).map(t => ({ sessionId: t.sessionId, toolUseId: t.toolUseId }))
      : terminals.map(t => ({ sessionId: t.sessionId, toolUseId: t.toolUseId }))
    
    if (items.length === 0) {
      return { success: true, count: 0, backgroundedIds: [] }
    }

    // Wrap streaming API as Promise
    return new Promise((resolve) => {
      const backgroundedIds: string[] = []
      let hasError = false
      let errorMsg: string | undefined

      ideaRSocket.terminalBackground(
        items,
        (event) => {
          if (event.status === TerminalBackgroundStatus.SUCCESS) {
            backgroundedIds.push(event.toolUseId)
          } else if (event.status === TerminalBackgroundStatus.FAILED) {
            hasError = true
            errorMsg = event.error
          }
        },
        () => {
          resolve({
            success: !hasError,
            count: backgroundedIds.length,
            backgroundedIds,
            error: errorMsg
          })
        },
        (error) => {
          resolve({
            success: false,
            count: backgroundedIds.length,
            backgroundedIds,
            error: error.message
          })
        }
      )
    })
  } catch (error) {
    return {
      success: false,
      count: 0,
      backgroundedIds: [],
      error: error instanceof Error ? error.message : String(error)
    }
  }
}
