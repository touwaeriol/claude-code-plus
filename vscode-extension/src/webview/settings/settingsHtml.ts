/**
 * Main HTML template generator for VS Code settings panel
 */
import { getSettingsStyles } from './settingsStyles'
import { getSettingsScript } from './settingsScript'
import { getClaudeCodePageHtml } from './pages/claudeCodePage'
import { getCodexPageHtml } from './pages/codexPage'
import { getGitGeneratePageHtml } from './pages/gitGeneratePage'
import { getMcpPageHtml } from './pages/mcpPage'

export interface SettingsHtmlOptions {
  nonce: string
  cspSource: string
}

/**
 * Generate the complete settings panel HTML
 */
export function generateSettingsHtml(options: SettingsHtmlOptions): string {
  const { nonce, cspSource } = options
  
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}'; font-src ${cspSource};">
  <title>Claude Code Plus Settings</title>
  <style>
${getSettingsStyles()}
  </style>
</head>
<body>
  <div class="settings-container">
    <!-- Left Sidebar Navigation -->
    <div class="sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title">Settings</span>
      </div>
      <nav class="nav-list">
        <div class="nav-group">
          <div class="nav-group-header">Claude Code Plus</div>
          <div class="nav-item active" data-page="claude">
            <span class="nav-icon">⚡</span>
            <span class="nav-text">Claude Code</span>
          </div>
          <div class="nav-item" data-page="codex">
            <span class="nav-icon">🤖</span>
            <span class="nav-text">Codex</span>
          </div>
          <div class="nav-item" data-page="git">
            <span class="nav-icon">📝</span>
            <span class="nav-text">Git Generate</span>
          </div>
          <div class="nav-item" data-page="mcp">
            <span class="nav-icon">🔌</span>
            <span class="nav-text">MCP</span>
          </div>
        </div>
      </nav>
    </div>
    
    <!-- Right Content Area -->
    <div class="content">
      <div id="claude-page" class="page active">
        ${getClaudeCodePageHtml()}
      </div>
      <div id="codex-page" class="page">
        ${getCodexPageHtml()}
      </div>
      <div id="git-page" class="page">
        ${getGitGeneratePageHtml()}
      </div>
      <div id="mcp-page" class="page">
        ${getMcpPageHtml()}
      </div>
    </div>
  </div>
  
  <script nonce="${nonce}">
${getSettingsScript()}
  </script>
</body>
</html>`
}
