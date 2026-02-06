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
    const protocol = url.protocol.toLowerCase()
    if (protocol !== 'https:' && protocol !== 'http:') return false
    const host = url.hostname.toLowerCase()
    const isLoopbackHost =
      host === '127.0.0.1' ||
      host === 'localhost' ||
      host === '::1' ||
      host === '[::1]'

    if (isLoopbackHost) {
      return true
    }

    return (
      host.endsWith('.vscode-webview.net') ||
      host.endsWith('.vscode-webview-test.com') ||
      host.endsWith('.vscode-resource.vscode-cdn.net')
    )
  } catch {
    return false
  }
}
