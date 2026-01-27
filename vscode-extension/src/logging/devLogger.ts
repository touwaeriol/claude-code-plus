import * as fs from 'fs'
import * as path from 'path'
import * as vscode from 'vscode'

export type DevLogger = {
  readonly logFilePath: string
  write(message: string): void
}

export function createDevLogger(context: vscode.ExtensionContext): DevLogger {
  const dir = context.logUri.fsPath
  fs.mkdirSync(dir, { recursive: true })
  const logFilePath = path.join(dir, 'claude-code-plus.log')

  return {
    logFilePath,
    write(message: string) {
      const ts = new Date().toISOString()
      try {
        fs.appendFileSync(logFilePath, `[${ts}] ${message}\n`, 'utf8')
      } catch {
        // Ignore logging failures.
      }
    },
  }
}

