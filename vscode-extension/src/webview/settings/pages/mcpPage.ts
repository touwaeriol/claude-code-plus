/**
 * MCP configuration page HTML
 */
export function getMcpPageHtml(): string {
  return `
    <h2>MCP Server Settings</h2>
    
    <div class="settings-group">
      <h3>Built-in MCP Servers</h3>
      <p class="description">Enable or disable MCP servers that provide IDE integration features</p>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="enableJetBrainsMcp" data-key="agent.enableJetBrainsMcp">
          <span>IDE LSP MCP (Code Search, File Index)</span>
        </label>
        <p class="description">Enable JetBrains-style IDE integration tools for code navigation and search</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="enableJetBrainsFileMcp" data-key="agent.enableJetBrainsFileMcp">
          <span>IDE File MCP (Read, Write, Edit)</span>
        </label>
        <p class="description">Enable file operations through MCP with IDE integration</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="enableTerminalMcp" data-key="agent.enableTerminalMcp">
          <span>Terminal MCP</span>
        </label>
        <p class="description">Enable terminal execution through MCP (can replace built-in Bash)</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="enableGitMcp" data-key="agent.enableGitMcp">
          <span>Git MCP</span>
        </label>
        <p class="description">Enable Git operations through MCP</p>
      </div>
    </div>
    
    <div class="settings-group">
      <h3>Tool Replacement Settings</h3>
      <p class="description">Configure which built-in Claude tools should be replaced by MCP servers</p>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="terminalDisableBuiltinBash" data-key="agent.terminalDisableBuiltinBash">
          <span>Disable Built-in Bash (when Terminal MCP enabled)</span>
        </label>
        <p class="description">Replace Claude's built-in Bash tool with Terminal MCP for better IDE integration</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="jetbrainsFileDisableBuiltinTools" data-key="agent.jetbrainsFileDisableBuiltinTools">
          <span>Disable Built-in File Tools (when File MCP enabled)</span>
        </label>
        <p class="description">Replace Claude's built-in Read/Write/Edit tools with File MCP</p>
      </div>
      
      <div class="setting-item">
        <label for="jetbrainsFileDisabledTools">Disabled Built-in Tools</label>
        <input type="text" id="jetbrainsFileDisabledTools" data-key="agent.jetbrainsFileDisabledTools" 
               placeholder="Read,Write,Edit">
        <p class="description">Comma-separated list of built-in tools to disable when File MCP is enabled</p>
      </div>
    </div>
    
    <div class="settings-group">
      <h3>Server Status</h3>
      <p class="description">Current status of MCP servers (requires extension restart to apply changes)</p>
      
      <div class="mcp-status-list" id="mcp-status-list">
        <div class="status-item">
          <span class="status-dot" id="status-lsp"></span>
          <span class="status-name">IDE LSP MCP</span>
          <span class="status-state" id="status-lsp-state">Unknown</span>
        </div>
        <div class="status-item">
          <span class="status-dot" id="status-file"></span>
          <span class="status-name">IDE File MCP</span>
          <span class="status-state" id="status-file-state">Unknown</span>
        </div>
        <div class="status-item">
          <span class="status-dot" id="status-terminal"></span>
          <span class="status-name">Terminal MCP</span>
          <span class="status-state" id="status-terminal-state">Unknown</span>
        </div>
        <div class="status-item">
          <span class="status-dot" id="status-git"></span>
          <span class="status-name">Git MCP</span>
          <span class="status-state" id="status-git-state">Unknown</span>
        </div>
      </div>
      
      <div class="mcp-actions">
        <p class="info-text">⚠️ Changes to MCP settings require restarting the extension to take effect.</p>
      </div>
    </div>
  `
}
