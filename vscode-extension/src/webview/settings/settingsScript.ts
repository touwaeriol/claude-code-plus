/**
 * 设置界面前端脚本
 * 
 * 处理：
 * - 导航切换
 * - 配置值读写
 * - 与 Extension 通信
 */

export function getSettingsScript(): string {
  return `
(function() {
  // VS Code API
  const vscode = acquireVsCodeApi();

  // 当前配置数据
  let currentSettings = {};

  // ========== 导航切换 ==========

  function initNavigation() {
    const navItems = document.querySelectorAll('.nav-item');
    const pages = document.querySelectorAll('.page');

    navItems.forEach(item => {
      item.addEventListener('click', () => {
        const pageId = item.dataset.page;
        
        // 更新导航状态
        navItems.forEach(nav => nav.classList.remove('active'));
        item.classList.add('active');
        
        // 切换页面
        pages.forEach(page => {
          page.classList.toggle('active', page.id === pageId + '-page');
        });
      });
    });
  }

  // ========== 配置值绑定 ==========

  function initSettingsBindings() {
    // 监听所有配置控件变化
    document.querySelectorAll('[data-key]').forEach(element => {
      const key = element.dataset.key;
      
      if (element.type === 'checkbox') {
        element.addEventListener('change', () => {
          updateSetting(key, element.checked);
        });
      } else if (element.tagName === 'SELECT' || element.type === 'text' || element.type === 'number') {
        element.addEventListener('change', () => {
          let value = element.value;
          if (element.type === 'number') {
            value = parseFloat(value) || 0;
          }
          updateSetting(key, value);
        });
      }
    });
  }

  // 更新配置值
  function updateSetting(key, value) {
    vscode.postMessage({
      type: 'updateSetting',
      key: key,
      value: value
    });
  }

  // 应用配置到 UI
  function applySettings(settings) {
    currentSettings = settings;

    document.querySelectorAll('[data-key]').forEach(element => {
      const key = element.dataset.key;
      const value = getNestedValue(settings, key);

      if (value !== undefined) {
        if (element.type === 'checkbox') {
          element.checked = !!value;
        } else {
          element.value = value;
        }
      }
    });
  }

  // 获取嵌套对象值 (支持 'claude.defaultModelId' 格式)
  function getNestedValue(obj, path) {
    return path.split('.').reduce((current, key) => {
      return current && current[key] !== undefined ? current[key] : undefined;
    }, obj);
  }

  // ========== 消息处理 ==========

  window.addEventListener('message', event => {
    const message = event.data;

    switch (message.type) {
      case 'settings':
        applySettings(message.data);
        break;

      case 'settingUpdated':
        // 可以显示保存成功提示
        console.log('Setting updated:', message.key, message.success);
        break;

      case 'theme':
        // 主题变化时可以更新样式
        console.log('Theme changed:', message.theme);
        break;

      case 'mcpServers':
        // 更新 MCP 服务器列表
        updateMcpServerList(message.data);
        break;
    }
  });

  // ========== MCP 服务器表格 ==========

  function updateMcpServerList(servers) {
    const tbody = document.getElementById('mcp-servers-tbody');
    if (!tbody) return;

    tbody.innerHTML = '';

    servers.forEach(server => {
      const row = document.createElement('tr');
      row.innerHTML = \`
        <td>
          <input type="checkbox" 
                 data-server="\${server.name}" 
                 data-action="toggle"
                 \${server.enabled ? 'checked' : ''}>
        </td>
        <td>\${escapeHtml(server.name)}</td>
        <td>\${server.isBuiltIn ? 'Built-in' : 'Custom'}</td>
        <td>
          <select data-server="\${server.name}" data-action="backend">
            <option value="all" \${server.backend === 'all' ? 'selected' : ''}>All</option>
            <option value="claude" \${server.backend === 'claude' ? 'selected' : ''}>Claude</option>
            <option value="codex" \${server.backend === 'codex' ? 'selected' : ''}>Codex</option>
          </select>
        </td>
      \`;
      tbody.appendChild(row);
    });

    // 绑定事件
    tbody.querySelectorAll('[data-action="toggle"]').forEach(checkbox => {
      checkbox.addEventListener('change', () => {
        vscode.postMessage({
          type: 'toggleMcpServer',
          server: checkbox.dataset.server,
          enabled: checkbox.checked
        });
      });
    });

    tbody.querySelectorAll('[data-action="backend"]').forEach(select => {
      select.addEventListener('change', () => {
        vscode.postMessage({
          type: 'setMcpServerBackend',
          server: select.dataset.server,
          backend: select.value
        });
      });
    });
  }

  function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }

  // ========== 初始化 ==========

  function init() {
    initNavigation();
    initSettingsBindings();

    // 请求配置数据
    vscode.postMessage({ type: 'getSettings' });
    vscode.postMessage({ type: 'getMcpServers' });
  }

  // DOM 加载完成后初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
  `
}
