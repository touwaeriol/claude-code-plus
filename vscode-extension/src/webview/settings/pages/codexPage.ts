/**
 * Codex configuration page HTML
 */
export function getCodexPageHtml(): string {
  return `
    <h2>Codex Settings</h2>
    
    <div class="settings-group">
      <h3>Model Settings</h3>
      
      <div class="setting-item">
        <label for="codex-defaultModelId">Default Model</label>
        <select id="codex-defaultModelId" data-key="codex.defaultModelId">
          <option value="gpt-5.2-codex">GPT-5.2 Codex</option>
          <option value="o3">o3</option>
          <option value="o4-mini">o4-mini</option>
          <option value="gpt-4.1">gpt-4.1</option>
          <option value="codex-1">codex-1</option>
        </select>
        <p class="description">The default model to use for Codex sessions</p>
      </div>
    </div>
    
    <div class="settings-group">
      <h3>Runtime Settings</h3>
      
      <div class="setting-item">
        <label for="codex-path">Codex Path</label>
        <div class="path-input">
          <input type="text" id="codex-path" data-key="codex.path" 
                 placeholder="Leave empty to use bundled Codex">
          <button type="button" class="browse-btn" data-target="codex-path">Browse...</button>
        </div>
        <p class="description">Custom Codex CLI executable path (optional)</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="codex-webSearchEnabled" data-key="codex.webSearchEnabled">
          <span>Enable Web Search</span>
        </label>
        <p class="description">Allow Codex to search the web for information</p>
      </div>
      
      <div class="setting-item checkbox-item">
        <label>
          <input type="checkbox" id="codex-defaultAutoCleanupContexts" data-key="codex.defaultAutoCleanupContexts">
          <span>Auto Cleanup Contexts</span>
        </label>
        <p class="description">Automatically clean up old Codex conversation contexts</p>
      </div>
    </div>
    
    <div class="settings-group">
      <h3>Session Defaults</h3>
      
      <div class="setting-item">
        <label for="codex-reasoningEffort">Reasoning Effort</label>
        <select id="codex-reasoningEffort" data-key="codex.reasoningEffort">
          <option value="none">None</option>
          <option value="minimal">Minimal</option>
          <option value="low">Low</option>
          <option value="medium">Medium</option>
          <option value="high">High</option>
          <option value="xhigh">Extra High</option>
        </select>
        <p class="description">Default reasoning effort level for Codex sessions</p>
      </div>
      
      <div class="setting-item">
        <label for="codex-reasoningSummary">Reasoning Summary</label>
        <select id="codex-reasoningSummary" data-key="codex.reasoningSummary">
          <option value="auto">Auto</option>
          <option value="concise">Concise</option>
          <option value="detailed">Detailed</option>
          <option value="none">None</option>
        </select>
        <p class="description">How to summarize the reasoning process</p>
      </div>
      
      <div class="setting-item">
        <label for="codex-sandboxMode">Sandbox Mode</label>
        <select id="codex-sandboxMode" data-key="codex.sandboxMode">
          <option value="read-only">Read Only</option>
          <option value="workspace-write">Workspace Write</option>
          <option value="danger-full-access">Full Access (Dangerous)</option>
        </select>
        <p class="description">Sandbox isolation level for code execution</p>
      </div>
    </div>
  `
}
