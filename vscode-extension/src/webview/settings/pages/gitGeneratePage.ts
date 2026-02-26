/**
 * Git Generate configuration page HTML
 */
export function getGitGeneratePageHtml(): string {
  return `
    <h2>Git Generate Settings</h2>
    
    <div class="settings-group">
      <h3>Commit Message Generation</h3>
      
      <div class="setting-item">
        <label for="gitGenerate-backend">Backend</label>
        <select id="gitGenerate-backend" data-key="gitGenerate.backend">
          <option value="claude">Claude</option>
          <option value="codex">Codex</option>
        </select>
        <p class="description">Which AI backend to use for generating commit messages</p>
      </div>
      
      <div class="setting-item">
        <label for="gitGenerate-model">Model</label>
        <select id="gitGenerate-model" data-key="gitGenerate.model">
          <option value="default">Default (use backend default)</option>
          <option value="claude-haiku-3-5-20241022">Claude Haiku 3.5</option>
          <option value="o4-mini">o4-mini</option>
          <option value="gpt-4.1">gpt-4.1</option>
        </select>
        <p class="description">Specific model for commit message generation</p>
      </div>
      
      <div class="setting-item">
        <label for="gitGenerate-language">Language</label>
        <select id="gitGenerate-language" data-key="gitGenerate.language">
          <option value="en">English</option>
          <option value="zh">Chinese (中文)</option>
          <option value="ja">Japanese (日本語)</option>
          <option value="ko">Korean (한국어)</option>
          <option value="es">Spanish (Español)</option>
          <option value="fr">French (Français)</option>
          <option value="de">German (Deutsch)</option>
        </select>
        <p class="description">Language for generated commit messages</p>
      </div>
    </div>
  `
}
