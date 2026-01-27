export function isAllowedWebviewOrigin(origin: unknown): origin is string {
  if (typeof origin !== 'string') return false
  if (origin.startsWith('vscode-webview://')) return true

  // Newer VS Code webviews use an https origin like:
  //   https://<uuid>.vscode-webview.net
  //   https://<uuid>.vscode-webview-test.com
  // Some builds (and resource-backed webviews) can surface an origin like:
  //   https://file+.vscode-resource.vscode-cdn.net
  try {
    const url = new URL(origin)
    if (url.protocol !== 'https:') return false
    const host = url.hostname.toLowerCase()
    return (
      host.endsWith('.vscode-webview.net') ||
      host.endsWith('.vscode-webview-test.com') ||
      host.endsWith('.vscode-resource.vscode-cdn.net')
    )
  } catch {
    return false
  }
}
