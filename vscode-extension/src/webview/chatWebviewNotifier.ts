import * as vscode from 'vscode'

type PostMessageTarget = { postMessage(message: unknown): Thenable<boolean> }

const targets = new Set<PostMessageTarget>()

export function registerChatWebview(target: PostMessageTarget): vscode.Disposable {
  targets.add(target)
  return { dispose: () => targets.delete(target) }
}

export async function broadcastChatMessage(message: unknown): Promise<void> {
  const snapshot = Array.from(targets)
  await Promise.all(
    snapshot.map(async (t) => {
      try {
        await t.postMessage(message)
      } catch {
        // Ignore disposed webviews.
      }
    })
  )
}

export function notifyChatSettingsChanged(): void {
  void broadcastChatMessage({ type: 'ccp-settings-changed' })
}

