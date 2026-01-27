import * as crypto from 'crypto'
import * as vscode from 'vscode'

export const DIFF_SCHEME = 'claude-code-plus-diff'

export class DiffContentProvider implements vscode.TextDocumentContentProvider {
  private readonly onDidChangeEmitter = new vscode.EventEmitter<vscode.Uri>()
  readonly onDidChange = this.onDidChangeEmitter.event

  private readonly contents = new Map<string, string>()

  provideTextDocumentContent(uri: vscode.Uri): string {
    const id = uri.authority
    return this.contents.get(id) ?? ''
  }

  createUri(content: string, label: string): vscode.Uri {
    const id = crypto.randomUUID()
    this.contents.set(id, content)
    return vscode.Uri.parse(`${DIFF_SCHEME}://${id}/${encodeURIComponent(label)}`)
  }

  disposeUri(uri: vscode.Uri) {
    this.contents.delete(uri.authority)
  }
}

