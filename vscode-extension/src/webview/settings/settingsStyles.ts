/**
 * 设置界面 CSS 样式
 * 
 * 支持 VS Code 主题变量，适配亮色/暗色主题
 */

export function getSettingsStyles(): string {
  return `
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }

    body {
      font-family: var(--vscode-font-family, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif);
      font-size: var(--vscode-font-size, 13px);
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      line-height: 1.5;
    }

    /* 主容器 */
    .settings-container {
      display: flex;
      height: 100vh;
      overflow: hidden;
    }

    /* 左侧导航栏 */
    .sidebar {
      width: 200px;
      min-width: 200px;
      background: var(--vscode-sideBar-background, var(--vscode-editor-background));
      border-right: 1px solid var(--vscode-panel-border, var(--vscode-editorGroup-border));
      padding: 12px 0;
      overflow-y: auto;
    }

    .nav-header {
      padding: 8px 16px;
      font-weight: 600;
      color: var(--vscode-foreground);
      font-size: 11px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      opacity: 0.8;
    }

    .nav-item {
      padding: 8px 16px 8px 24px;
      cursor: pointer;
      color: var(--vscode-foreground);
      transition: background 0.1s;
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .nav-item:hover {
      background: var(--vscode-list-hoverBackground);
    }

    .nav-item.active {
      background: var(--vscode-list-activeSelectionBackground);
      color: var(--vscode-list-activeSelectionForeground);
    }

    .nav-item::before {
      content: '';
      width: 4px;
      height: 4px;
      border-radius: 50%;
      background: currentColor;
      opacity: 0.5;
    }

    .nav-item.active::before {
      opacity: 1;
    }

    /* 右侧内容区 */
    .content {
      flex: 1;
      overflow-y: auto;
      padding: 20px 24px;
    }

    .page {
      display: none;
    }

    .page.active {
      display: block;
    }

    .page-title {
      font-size: 20px;
      font-weight: 600;
      margin-bottom: 20px;
      color: var(--vscode-foreground);
    }

    /* 设置分组 */
    .settings-group {
      margin-bottom: 24px;
      background: var(--vscode-editorWidget-background, var(--vscode-editor-background));
      border: 1px solid var(--vscode-panel-border, var(--vscode-editorGroup-border));
      border-radius: 6px;
      overflow: hidden;
    }

    .settings-group h3 {
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      padding: 12px 16px;
      background: var(--vscode-sideBarSectionHeader-background, rgba(128, 128, 128, 0.1));
      border-bottom: 1px solid var(--vscode-panel-border, var(--vscode-editorGroup-border));
      color: var(--vscode-foreground);
      opacity: 0.9;
    }

    .settings-group-content {
      padding: 16px;
    }

    /* 设置项 */
    .setting-item {
      margin-bottom: 16px;
    }

    .setting-item:last-child {
      margin-bottom: 0;
    }

    .setting-item > label {
      display: block;
      font-weight: 500;
      margin-bottom: 6px;
      color: var(--vscode-foreground);
    }

    .setting-item .description {
      font-size: 12px;
      color: var(--vscode-descriptionForeground);
      margin-top: 4px;
    }

    /* 复选框样式 */
    .checkbox-item {
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }

    .checkbox-item input[type="checkbox"] {
      margin-top: 2px;
      width: 16px;
      height: 16px;
      accent-color: var(--vscode-focusBorder);
    }

    .checkbox-item .checkbox-content {
      flex: 1;
    }

    .checkbox-item .checkbox-label {
      font-weight: 500;
      color: var(--vscode-foreground);
    }

    .checkbox-item .checkbox-description {
      font-size: 12px;
      color: var(--vscode-descriptionForeground);
      margin-top: 2px;
    }

    /* 输入框 */
    input[type="text"],
    input[type="number"],
    select {
      width: 100%;
      max-width: 400px;
      padding: 6px 10px;
      font-size: 13px;
      font-family: inherit;
      background: var(--vscode-input-background);
      color: var(--vscode-input-foreground);
      border: 1px solid var(--vscode-input-border, transparent);
      border-radius: 4px;
      outline: none;
    }

    input[type="text"]:focus,
    input[type="number"]:focus,
    select:focus {
      border-color: var(--vscode-focusBorder);
    }

    input[type="number"] {
      max-width: 150px;
    }

    select {
      cursor: pointer;
    }

    /* 文件选择 */
    .file-input {
      display: flex;
      gap: 8px;
      max-width: 500px;
    }

    .file-input input[type="text"] {
      flex: 1;
      max-width: none;
    }

    .file-input button {
      padding: 6px 12px;
      background: var(--vscode-button-secondaryBackground);
      color: var(--vscode-button-secondaryForeground);
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 13px;
    }

    .file-input button:hover {
      background: var(--vscode-button-secondaryHoverBackground);
    }

    /* 表格样式 */
    .table-container {
      overflow-x: auto;
      border: 1px solid var(--vscode-panel-border, var(--vscode-editorGroup-border));
      border-radius: 4px;
    }

    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 13px;
    }

    th, td {
      text-align: left;
      padding: 10px 12px;
      border-bottom: 1px solid var(--vscode-panel-border, var(--vscode-editorGroup-border));
    }

    th {
      background: var(--vscode-sideBarSectionHeader-background, rgba(128, 128, 128, 0.1));
      font-weight: 600;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.3px;
    }

    tr:last-child td {
      border-bottom: none;
    }

    tr:hover {
      background: var(--vscode-list-hoverBackground);
    }

    td select {
      width: auto;
      min-width: 100px;
    }

    /* 按钮 */
    button {
      padding: 8px 16px;
      background: var(--vscode-button-background);
      color: var(--vscode-button-foreground);
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 13px;
      font-family: inherit;
    }

    button:hover {
      background: var(--vscode-button-hoverBackground);
    }

    button:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    button.secondary {
      background: var(--vscode-button-secondaryBackground);
      color: var(--vscode-button-secondaryForeground);
    }

    button.secondary:hover {
      background: var(--vscode-button-secondaryHoverBackground);
    }

    /* 工具栏 */
    .toolbar {
      display: flex;
      gap: 8px;
      margin-top: 12px;
    }

    /* 状态指示器 */
    .status-indicator {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .status-dot.enabled {
      background: var(--vscode-testing-iconPassed, #4caf50);
    }

    .status-dot.disabled {
      background: var(--vscode-testing-iconSkipped, #9e9e9e);
    }

    /* 滚动条样式 */
    ::-webkit-scrollbar {
      width: 10px;
      height: 10px;
    }

    ::-webkit-scrollbar-track {
      background: transparent;
    }

    ::-webkit-scrollbar-thumb {
      background: var(--vscode-scrollbarSlider-background);
      border-radius: 5px;
    }

    ::-webkit-scrollbar-thumb:hover {
      background: var(--vscode-scrollbarSlider-hoverBackground);
    }

    /* 响应式 */
    @media (max-width: 600px) {
      .sidebar {
        width: 150px;
        min-width: 150px;
      }

      .nav-item {
        padding: 8px 12px 8px 16px;
        font-size: 12px;
      }

      .content {
        padding: 16px;
      }
    }
  `
}
