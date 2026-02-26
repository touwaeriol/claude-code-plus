/**
 * Claude Code configuration page HTML
 */
export function getClaudeCodePageHtml(): string {
  return `
    <h2>Claude Code Settings</h2>
    
    <div class="settings-group">
      <h3>Model Settings</h3>
      
      <div class="setting-item">
        <label for="claude-defaultModelId">Default Model</label>
        <select id="claude-defaultModelId" data-key="claude.defaultModelId">
          <option value="claude-sonnet-4-20250514">Claude Sonnet 4</option>
          <option value="claude-haiku-4-5-20251001">Claude Haiku 4.5</option>
          <option value="claude-opus-4-6">Claude Opus 4.6</option>
          <option value="claude-sonnet-4-6">Claude Sonnet 4.6</option>
        </select>
        <p class="description">The default model to use for Claude conversations</p>
      </div>
      
      <div class="setting-item">
        <label for="claude-defaultThinkingLevel">Thinking Level</label>
        <select id="claude-defaultThinkingLevel" data-key="claude.defaultThinkingLevel">
          <option value="OFF">Off</option>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
          <option value="VERY_HIGH">Very High</option>
          <option value="ULTRA">Ultra</option>
        </select>
        <p class="description">Controls how much thinking the model does before responding</p>
      </div>
      
      <div class="setting-item">
        <label for="claude-defaultThinkingTokens">Thinking Tokens</label>
        <input type="number" id="claude-defaultThinkingTokens" data-key="claude.defaultThinkingTokens" 
               min="0" max="100000" step="1024" placeholder="8192">
        <p class="description">Maximum number of tokens for model thinking (default: 8192)</p>
      </div>
    </div>
    
    <div class="settings-group">
      <h3>Permission Settings</h3>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="defaultBypassPermissions" data-key="defaultBypassPermissions">
          <span>Bypass Permissions</span>
        </label>
        <p class="description">Skip all permission confirmations (use with caution)</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="claude-defaultAutoCleanupContexts" data-key="claude.defaultAutoCleanupContexts">
          <span>Auto Cleanup Contexts</span>
        </label>
        <p class="description">Automatically clean up old conversation contexts</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="includePartialMessages" data-key="includePartialMessages">
          <span>Include Partial Messages</span>
        </label>
        <p class="description">Include partial messages in UI during streaming</p>
      </div>
    </div>
    
    <div class="settings-group">
      <h3>Runtime Settings</h3>
      
      <div class="setting-item">
        <label for="claude-nodePath">Node.js Path</label>
        <div class="path-input">
          <input type="text" id="claude-nodePath" data-key="claude.nodePath" 
                 placeholder="Leave empty to use system Node.js">
          <button type="button" class="browse-btn" data-target="claude-nodePath">Browse...</button>
        </div>
        <p class="description">Custom Node.js executable path (optional)</p>
      </div>
    </div>
  `
}
