/**
 * 增强版工具图标系统
 * 参考 Augment Code 的图标设计，提供更统一、更直观的图标映射
 */

export interface ToolIconConfig {
  /** 图标字符（Emoji 或 Unicode） */
  icon: string
  /** 图标颜色（可选，用于自定义主题） */
  color?: string
  /** 图标描述 */
  description: string
}

/**
 * 工具图标映射表
 * 支持多种命名格式：kebab-case, camelCase, PascalCase
 */
export const ENHANCED_TOOL_ICONS: Record<string, ToolIconConfig> = {
  // ==================== 文件操作 ====================
  'read': {
    icon: '📄',
    description: '读取文件',
  },
  'Read': {
    icon: '📄',
    description: '读取文件',
  },
  'write': {
    icon: '✏️',
    description: '写入文件',
  },
  'Write': {
    icon: '✏️',
    description: '写入文件',
  },
  'edit': {
    icon: '✏️',
    description: '编辑文件',
  },
  'Edit': {
    icon: '✏️',
    description: '编辑文件',
  },
  'multi-edit': {
    icon: '📝',
    description: '多处编辑',
  },
  'MultiEdit': {
    icon: '📝',
    description: '多处编辑',
  },
  'str-replace-editor': {
    icon: '✏️',
    description: '字符串替换编辑器',
  },
  'save-file': {
    icon: '💾',
    description: '保存文件',
  },
  'remove-files': {
    icon: '🗑️',
    description: '删除文件',
  },

  // ==================== 搜索和检索 ====================
  'grep': {
    icon: '🔍',
    description: '文本搜索',
  },
  'Grep': {
    icon: '🔍',
    description: '文本搜索',
  },
  'glob': {
    icon: '📁',
    description: '文件匹配',
  },
  'Glob': {
    icon: '📁',
    description: '文件匹配',
  },
  'view': {
    icon: '👁️',
    description: '查看文件',
  },
  'codebase-retrieval': {
    icon: '🧠',
    description: '代码库检索',
  },
  'git-commit-retrieval': {
    icon: '🔍',
    description: 'Git 提交检索',
  },
  'search-untruncated': {
    icon: '🔎',
    description: '搜索未截断内容',
  },

  // ==================== 命令执行 ====================
  'bash': {
    icon: '💻',
    description: '终端命令',
  },
  'Bash': {
    icon: '💻',
    description: '终端命令',
  },
  'launch-process': {
    icon: '🚀',
    description: '启动进程',
  },
  'bash-output': {
    icon: '📤',
    description: '命令输出',
  },
  'BashOutput': {
    icon: '📤',
    description: '命令输出',
  },
  'kill-shell': {
    icon: '🛑',
    description: '终止进程',
  },
  'KillShell': {
    icon: '🛑',
    description: '终止进程',
  },
  'kill-process': {
    icon: '🛑',
    description: '终止进程',
  },
  'read-process': {
    icon: '📖',
    description: '读取进程输出',
  },
  'write-process': {
    icon: '✍️',
    description: '写入进程输入',
  },
  'list-processes': {
    icon: '📋',
    description: '列出进程',
  },

  // ==================== 网络操作 ====================
  'web-search': {
    icon: '🌐',
    description: '网络搜索',
  },
  'WebSearch': {
    icon: '🌐',
    description: '网络搜索',
  },
  'web-fetch': {
    icon: '🌐',
    description: '网页抓取',
  },
  'WebFetch': {
    icon: '🌐',
    description: '网页抓取',
  },

  // ==================== 任务管理 ====================
  'task': {
    icon: '📋',
    description: '任务',
  },
  'Task': {
    icon: '📋',
    description: '任务',
  },
  'todo-write': {
    icon: '✅',
    description: '待办事项',
  },
  'TodoWrite': {
    icon: '✅',
    description: '待办事项',
  },
  'add_tasks': {
    icon: '➕',
    description: '添加任务',
  },
  'update_tasks': {
    icon: '🔄',
    description: '更新任务',
  },
  'view_tasklist': {
    icon: '📝',
    description: '查看任务列表',
  },
  'reorganize_tasklist': {
    icon: '🔀',
    description: '重组任务列表',
  },

  // ==================== AI 功能 ====================
  'sequential-thinking': {
    icon: '🤔',
    description: '思维链',
  },
  'ask-user-question': {
    icon: '❓',
    description: '询问用户',
  },
  'AskUserQuestion': {
    icon: '❓',
    description: '询问用户',
  },
  'remember': {
    icon: '💭',
    description: '记忆',
  },

  // ==================== 浏览器操作 ====================
  'browser_navigate': {
    icon: '🌐',
    description: '浏览器导航',
  },
  'browser_click': {
    icon: '👆',
    description: '浏览器点击',
  },
  'browser_type': {
    icon: '⌨️',
    description: '浏览器输入',
  },
  'browser_snapshot': {
    icon: '📸',
    description: '浏览器快照',
  },
  'browser_take_screenshot': {
    icon: '📷',
    description: '浏览器截图',
  },
  'open-browser': {
    icon: '🌐',
    description: '打开浏览器',
  },

  // ==================== 其他工具 ====================
  'notebook-edit': {
    icon: '📓',
    description: '笔记本编辑',
  },
  'NotebookEdit': {
    icon: '📓',
    description: '笔记本编辑',
  },
  'slash-command': {
    icon: '⚡',
    description: '斜杠命令',
  },
  'SlashCommand': {
    icon: '⚡',
    description: '斜杠命令',
  },
  'skill': {
    icon: '🎯',
    description: '技能调用',
  },
  'Skill': {
    icon: '🎯',
    description: '技能调用',
  },
  'diagnostics': {
    icon: '🔧',
    description: '诊断',
  },
  'read-terminal': {
    icon: '📟',
    description: '读取终端',
  },
  'convert_to_markdown': {
    icon: '📝',
    description: '转换为 Markdown',
  },
  'render-mermaid': {
    icon: '📊',
    description: '渲染 Mermaid 图表',
  },

  // ==================== MCP 工具 ====================
  'list-mcp-resources': {
    icon: '📚',
    description: 'MCP 资源列表',
  },
  'ListMcpResources': {
    icon: '📚',
    description: 'MCP 资源列表',
  },
  'read-mcp-resource': {
    icon: '📖',
    description: '读取 MCP 资源',
  },
  'ReadMcpResource': {
    icon: '📖',
    description: '读取 MCP 资源',
  },
  'generic-mcp-tool': {
    icon: '🔌',
    description: '通用 MCP 工具',
  },
}

/**
 * 获取工具图标
 * @param toolName 工具名称（支持多种格式）
 * @returns 图标字符，如果未找到则返回默认图标
 */
export function getToolIcon(toolName: string): string {
  const config = ENHANCED_TOOL_ICONS[toolName]
  return config?.icon || '🔧'
}

/**
 * 获取工具图标配置
 * @param toolName 工具名称
 * @returns 图标配置对象
 */
export function getToolIconConfig(toolName: string): ToolIconConfig {
  return ENHANCED_TOOL_ICONS[toolName] || {
    icon: '🔧',
    description: '未知工具',
  }
}

