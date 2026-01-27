import * as vscode from 'vscode'

export type TerminalTaskAction = 'started' | 'completed' | 'backgrounded'

export type TerminalTaskUpdate = {
  toolUseId: string
  sessionId: string
  action: TerminalTaskAction
  command: string
  isBackground: boolean
  startTime: number
  elapsedMs?: number
}

export type BackgroundableTerminal = {
  sessionId: string
  toolUseId: string
  command: string
  startTime: number
  elapsedMs: number
}

type TerminalBackgroundTask = {
  sessionId: string
  toolUseId: string
  command: string
  startTime: number
  isBackground: boolean
  backgroundTime?: number
}

/**
 * Tracks long-running bash commands so the frontend can "background" them.
 * This mirrors the IDEA TerminalSessionManager behavior at a minimal level.
 */
export class TerminalTaskManager implements vscode.Disposable {
  private readonly tasks = new Map<string, TerminalBackgroundTask>()
  private readonly pendingStartTimers = new Map<string, NodeJS.Timeout>()
  private readonly emitter = new vscode.EventEmitter<TerminalTaskUpdate>()

  readonly onDidUpdate = this.emitter.event

  recordTaskStart(sessionId: string, toolUseId: string, command: string): void {
    const startTime = Date.now()
    const task: TerminalBackgroundTask = { sessionId, toolUseId, command, startTime, isBackground: false }
    this.tasks.set(toolUseId, task)

    // Only surface tasks after they become "backgroundable" (>= threshold),
    // matching the frontend's intent to show long-running terminals.
    const thresholdMs = 5000
    const timer = setTimeout(() => {
      this.pendingStartTimers.delete(toolUseId)
      const current = this.tasks.get(toolUseId)
      if (!current || current.isBackground) return
      this.emitter.fire({
        toolUseId,
        sessionId: current.sessionId,
        action: 'started',
        command: current.command,
        isBackground: false,
        startTime: current.startTime,
        elapsedMs: Date.now() - current.startTime,
      })
    }, thresholdMs)
    this.pendingStartTimers.set(toolUseId, timer)
  }

  recordTaskComplete(toolUseId: string): void {
    const task = this.tasks.get(toolUseId)
    if (!task) return
    this.tasks.delete(toolUseId)
    const timer = this.pendingStartTimers.get(toolUseId)
    if (timer) {
      clearTimeout(timer)
      this.pendingStartTimers.delete(toolUseId)
    }

    const elapsedMs = Date.now() - task.startTime
    this.emitter.fire({
      toolUseId,
      sessionId: task.sessionId,
      action: 'completed',
      command: task.command,
      isBackground: task.isBackground,
      startTime: task.startTime,
      elapsedMs,
    })
  }

  markTaskAsBackground(toolUseId: string): boolean {
    const task = this.tasks.get(toolUseId)
    if (!task) return false
    if (task.isBackground) return true

    task.isBackground = true
    task.backgroundTime = Date.now()
    const timer = this.pendingStartTimers.get(toolUseId)
    if (timer) {
      clearTimeout(timer)
      this.pendingStartTimers.delete(toolUseId)
    }

    this.emitter.fire({
      toolUseId,
      sessionId: task.sessionId,
      action: 'backgrounded',
      command: task.command,
      isBackground: true,
      startTime: task.startTime,
      elapsedMs: Date.now() - task.startTime,
    })

    return true
  }

  getBackgroundableTasks(thresholdMs: number = 5000): BackgroundableTerminal[] {
    const now = Date.now()
    const tasks = [...this.tasks.values()]
      .filter((t) => !t.isBackground && now - t.startTime >= thresholdMs)
      .map((t) => ({
        sessionId: t.sessionId,
        toolUseId: t.toolUseId,
        command: t.command,
        startTime: t.startTime,
        elapsedMs: now - t.startTime,
      }))

    // Keep a stable order for UI.
    tasks.sort((a, b) => a.startTime - b.startTime)
    return tasks
  }

  dispose(): void {
    this.tasks.clear()
    for (const timer of this.pendingStartTimers.values()) {
      clearTimeout(timer)
    }
    this.pendingStartTimers.clear()
    this.emitter.dispose()
  }
}
