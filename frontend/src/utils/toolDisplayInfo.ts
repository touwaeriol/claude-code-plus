/**
 * 工具调用显示信息提取器
 * 参考 Augment Code 的紧凑单行设计
 */

import type { ToolUseContent, ToolResultContent } from '@/types/message'
import { i18n } from '@/i18n'

// 获取翻译函数
const t = (key: string, params?: Record<string, any>) => i18n.global.t(key, params ?? {})

// 终端 SVG 图标
const TERMINAL_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 17 10 11 4 5"></polyline><line x1="12" y1="19" x2="20" y2="19"></line></svg>'

// JetBrains MCP 文件操作 SVG 图标
// 文件读取图标（眼睛/查看）
const FILE_READ_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>'
// 文件写入图标（文件+加号）
const FILE_WRITE_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="12" y1="18" x2="12" y2="12"></line><line x1="9" y1="15" x2="15" y2="15"></line></svg>'
// 文件编辑图标（铅笔）
const FILE_EDIT_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>'

export interface ToolDisplayInfo {
  /** 工具图标 */
  icon: string
  /** 操作类型（如 "Read file", "Edited file"） */
  actionType: string
  /** 主要信息（如文件名、行号范围） */
  primaryInfo: string
  /** 次要信息（如文件路径） */
  secondaryInfo: string
  /** 行数变化（如 "+2 -3"，仅 edit 工具） */
  lineChanges?: string
  /** 增加的行数（折叠状态展示绿色 +N） */
  addedLines?: number
  /** 删除的行数（折叠状态展示红色 -N） */
  removedLines?: number
  /** 读取的行数（折叠状态提示读了多少行） */
  readLines?: number
  /** 状态（success/error/pending） */
  status: 'success' | 'error' | 'pending'
  /** 输入参数是否还在加载中（stream event 增量更新时为 true） */
  isInputLoading?: boolean
  /** 错误信息（仅当 status 为 error 时有值） */
  errorMessage?: string
}

/**
 * 工具图标映射
 * 注意：key 支持多种格式（首字母大写、小写、kebab-case）
 */
const TOOL_ICONS: Record<string, string> = {
  // 首字母大写格式（标准格式）
  Read: '📄',
  Write: '✏️',
  Edit: '✏️',
  MultiEdit: '✏️',
  Bash: '💻',
  Grep: '🔍',
  Glob: '🔍',
  TodoWrite: '✅',
  WebSearch: '🌐',
  WebFetch: '🌐',
  AskUserQuestion: '❓',
  NotebookEdit: '📓',
  Task: '📋',
  SlashCommand: '⚡',
  Skill: '🎯',
  BashOutput: '📤',
  KillShell: '🛑',
  ListMcpResources: '📚',
  ReadMcpResource: '📖',
  ExitPlanMode: '✅',
  EnterPlanMode: '📝',
  // IDE Terminal MCP 工具（统一使用终端 SVG 图标）
  'mcp__ide-terminal__Terminal': TERMINAL_SVG,
  'mcp__ide-terminal__TerminalRead': TERMINAL_SVG,
  'mcp__ide-terminal__TerminalList': TERMINAL_SVG,
  'mcp__ide-terminal__TerminalKill': TERMINAL_SVG,
  'mcp__ide-terminal__TerminalInterrupt': TERMINAL_SVG,
  'mcp__ide-terminal__TerminalTypes': TERMINAL_SVG,
  'mcp__ide-terminal__TerminalRename': TERMINAL_SVG,
  // IDE File MCP 文件操作工具
  'mcp__ide-file__ReadFile': FILE_READ_SVG,
  'mcp__ide-file__WriteFile': FILE_WRITE_SVG,
  'mcp__ide-file__EditFile': FILE_EDIT_SVG,
  // 小写格式（兼容旧格式）
  read: '📄',
  write: '✏️',
  edit: '✏️',
  'multi-edit': '✏️',
  bash: '💻',
  grep: '🔍',
  glob: '🔍',
  'codebase-retrieval': '🧠',
  'todo-write': '✅',
  'web-search': '🌐',
  'web-fetch': '🌐',
  'ask-user-question': '❓',
  'notebook-edit': '📓',
  task: '📋',
  'slash-command': '⚡',
  skill: '🎯',
  'bash-output': '📤',
  'kill-shell': '🛑',
  'list-mcp-resources': '📚',
}

/**
 * 操作类型映射（简洁版）
 * 注意：key 支持多种格式（首字母大写、小写、kebab-case）
 */
const ACTION_TYPES: Record<string, string> = {
  // 首字母大写格式（标准格式）
  Read: 'Read',
  Write: 'Write',
  Edit: 'Edit',
  MultiEdit: 'MultiEdit',
  Bash: 'Bash',
  Grep: 'Grep',
  Glob: 'Glob',
  TodoWrite: 'TodoWrite',
  WebSearch: 'WebSearch',
  WebFetch: 'WebFetch',
  AskUserQuestion: 'AskUser',
  NotebookEdit: 'Notebook',
  Task: 'Task',
  SlashCommand: 'Command',
  Skill: 'Skill',
  BashOutput: 'Output',
  KillShell: 'Kill',
  ListMcpResources: 'ListMCP',
  ReadMcpResource: 'ReadMCP',
  ExitPlanMode: 'ExitPlan',
  EnterPlanMode: 'EnterPlan',
  // IDE Terminal MCP 工具
  'mcp__ide-terminal__Terminal': 'Terminal',
  'mcp__ide-terminal__TerminalRead': 'TerminalRead',
  'mcp__ide-terminal__TerminalList': 'TerminalList',
  'mcp__ide-terminal__TerminalKill': 'TerminalKill',
  'mcp__ide-terminal__TerminalInterrupt': 'TerminalInterrupt',
  'mcp__ide-terminal__TerminalTypes': 'TerminalTypes',
  'mcp__ide-terminal__TerminalRename': 'TerminalRename',
  // IDE File MCP 文件操作工具
  'mcp__ide-file__ReadFile': 'ReadFile',
  'mcp__ide-file__WriteFile': 'WriteFile',
  'mcp__ide-file__EditFile': 'EditFile',
  // 小写格式（兼容旧格式）
  read: 'Read',
  write: 'Write',
  edit: 'Edit',
  'multi-edit': 'MultiEdit',
  bash: 'Bash',
  grep: 'Grep',
  glob: 'Glob',
  'codebase-retrieval': 'Codebase',
  'todo-write': 'TodoWrite',
  'web-search': 'WebSearch',
  'web-fetch': 'WebFetch',
  'ask-user-question': 'AskUser',
  'notebook-edit': 'Notebook',
  task: 'Task',
  'slash-command': 'Command',
  skill: 'Skill',
  'bash-output': 'Output',
  'kill-shell': 'Kill',
  'list-mcp-resources': 'ListMCP',
}

/**
 * 提取工具显示信息
 */
export function extractToolDisplayInfo(
  tool: ToolUseContent | { toolName?: string; toolType?: string; input?: any },
  result?: ToolResultContent | { is_error?: boolean; content?: any }
): ToolDisplayInfo {
  // 统一使用 toolName 字段
  const toolName = (tool as any)?.toolName || ''
  const toolInput = tool?.input || {}

  const icon = TOOL_ICONS[toolName] || '🔧'
  const rawActionType = ACTION_TYPES[toolName] || toolName || 'Unknown'
  const actionType = rawActionType

  // 直接使用后端格式：result.is_error
  const status = result?.is_error ? 'error' : (result ? 'success' : 'pending')

  // 判断是否还在解析参数（input 为空或只有部分字段）
  const isInputLoading = !result && (!toolInput || Object.keys(toolInput).length === 0)

  let primaryInfo = ''
  let secondaryInfo = ''
  let lineChanges: string | undefined
  let addedLines: number | undefined
  let removedLines: number | undefined
  let readLines: number | undefined

  // 如果 input 还在加载中，显示 loading 提示；否则根据工具类型解析
  if (!isInputLoading) {
    switch (toolName) {
    case 'read':
    case 'Read':
      // 折叠状态：显示文件名:行号范围
      primaryInfo = formatReadPrimaryInfo(toolInput)
      secondaryInfo = toolInput.path || toolInput.file_path || ''
      break

    case 'write':
    case 'Write':
      // 写入：显示文件名
      primaryInfo = formatWritePrimaryInfo(toolInput)
      secondaryInfo = toolInput.path || toolInput.file_path || ''
      if (toolInput?.content || toolInput?.file_content) {
        const contentText = toolInput.content || toolInput.file_content || ''
        const lineCount = contentText ? contentText.toString().split(/\r?\n/).length : 0
        addedLines = lineCount || undefined
      }
      break

    case 'edit':
    case 'Edit':
      // 编辑：显示文件名与行范围
      primaryInfo = formatEditPrimaryInfo(toolInput)
      secondaryInfo = toolInput.file_path || toolInput.path || ''
      lineChanges = calculateLineChanges(toolInput)
      if (toolInput) {
        const oldLines = (toolInput.old_string || toolInput.old_str || '').toString().split(/\r?\n/).length
        const newLines = (toolInput.new_string || toolInput.new_str || '').toString().split(/\r?\n/).length
        removedLines = oldLines || undefined
        addedLines = newLines || undefined
      }
      break

    case 'multi-edit':
    case 'MultiEdit': {
      // 折叠状态：显示文件名 (N处修改)
      const editsCount = toolInput.edits?.length || 0
      primaryInfo = `${extractFileName(toolInput.file_path || toolInput.path || '')} (${t('tools.multiEdit.changes', { n: editsCount })})`
      secondaryInfo = toolInput.file_path || toolInput.path || ''
      break
    }

    case 'bash':
    case 'Bash':
      // 折叠状态：显示命令（截断）
      primaryInfo = formatBashCommand(toolInput.command || '')
      secondaryInfo = toolInput.cwd || ''
      break

    case 'grep':
    case 'Grep':
      // 折叠状态：显示 "pattern" in path
      primaryInfo = formatGrepInfo(toolInput)
      secondaryInfo = ''
      break

    case 'glob':
    case 'Glob':
      // 折叠状态：显示 pattern in path
      primaryInfo = formatGlobInfo(toolInput)
      secondaryInfo = ''
      break

    case 'web-search':
    case 'WebSearch':
      // 折叠状态：显示搜索查询（加引号）
      primaryInfo = `"${toolInput.query || ''}"`
      secondaryInfo = ''
      break

    case 'web-fetch':
    case 'WebFetch':
      // 折叠状态：显示简化的 URL
      primaryInfo = simplifyUrl(toolInput.url || '')
      secondaryInfo = ''
      break

    case 'todo-write':
    case 'TodoWrite': {
      const todos = toolInput?.todos
      // 如果 input 为空或 todos 未定义，显示"加载中"而不是"0项任务"
      if (!toolInput || todos === undefined) {
        primaryInfo = t('common.loading')
      } else {
        primaryInfo = t('tools.todoTool.tasksCount', { n: todos.length })
      }
      secondaryInfo = ''
      break
    }

    case 'task':
    case 'Task':
      primaryInfo = truncateText(toolInput.description || 'Running task', 40)
      secondaryInfo = ''
      break

    case 'notebook-edit':
    case 'NotebookEdit':
      primaryInfo = extractFileName(toolInput.notebook_path || '')
      secondaryInfo = `Cell ${toolInput.cell_number || 0}`
      break

    case 'ask-user-question':
    case 'AskUserQuestion': {
      const questions = toolInput.questions || []
      primaryInfo = questions.length > 0 ? questions[0].question : 'Asking question'
      secondaryInfo = questions.length > 1 ? `+${questions.length - 1} more` : ''
      break
    }

    case 'codebase-retrieval':
      primaryInfo = 'Retrieving from: <> Codebase'
      secondaryInfo = toolInput.information_request || ''
      break

    case 'bash-output':
    case 'BashOutput':
      primaryInfo = toolInput.bash_id || toolInput.shell_id || ''
      secondaryInfo = ''
      break

    case 'kill-shell':
    case 'KillShell':
      primaryInfo = toolInput.shell_id || ''
      secondaryInfo = ''
      break

    case 'skill':
    case 'Skill':
      primaryInfo = toolInput.skill || ''
      secondaryInfo = ''
      break

    case 'slash-command':
    case 'SlashCommand':
      primaryInfo = toolInput.command || ''
      secondaryInfo = ''
      break

    case 'exit-plan-mode':
    case 'ExitPlanMode':
      primaryInfo = 'Exit plan mode'
      secondaryInfo = ''
      break

    case 'enter-plan-mode':
    case 'EnterPlanMode':
      primaryInfo = 'Enter plan mode'
      secondaryInfo = ''
      break

    case 'list-mcp-resources':
    case 'ListMcpResources':
      primaryInfo = toolInput.server || ''
      secondaryInfo = ''
      break

    case 'read-mcp-resource':
    case 'ReadMcpResource':
      primaryInfo = toolInput.uri || ''
      secondaryInfo = toolInput.server || ''
      break

    // JetBrains Terminal MCP 工具
    case 'mcp__jetbrains-terminal__Terminal': {
      // 显示命令，session_id 作为次要信息
      const cmd = toolInput.command || ''
      primaryInfo = formatBashCommand(cmd)
      secondaryInfo = toolInput.session_id ? `session: ${toolInput.session_id}` : ''
      break
    }

    case 'mcp__jetbrains-terminal__TerminalRead':
      // 显示 session_id
      primaryInfo = toolInput.session_id || ''
      secondaryInfo = toolInput.search ? `search: ${toolInput.search}` : ''
      break

    case 'mcp__jetbrains-terminal__TerminalList':
      primaryInfo = 'List sessions'
      secondaryInfo = ''
      break

    case 'mcp__jetbrains-terminal__TerminalKill': {
      // 显示要关闭的 session_ids
      const ids = toolInput.session_ids as string[] | undefined
      if (toolInput.all) {
        primaryInfo = 'all sessions'
      } else if (ids && ids.length > 0) {
        primaryInfo = ids.length === 1 ? ids[0] : `${ids.length} sessions`
      } else {
        primaryInfo = ''
      }
      secondaryInfo = ''
      break
    }

    case 'mcp__jetbrains-terminal__TerminalInterrupt':
      // 显示 session_id
      primaryInfo = toolInput.session_id || ''
      secondaryInfo = ''
      break

    case 'mcp__jetbrains-terminal__TerminalTypes':
      primaryInfo = 'Get shell types'
      secondaryInfo = ''
      break

    case 'mcp__jetbrains-terminal__TerminalRename':
      primaryInfo = toolInput.session_id || ''
      secondaryInfo = toolInput.new_name ? `→ ${toolInput.new_name}` : ''
      break

    // ====== IDE File MCP 工具（camelCase 参数）======
    case 'mcp__ide-file__ReadFile': {
      // 文件读取：显示文件名:行号范围
      const fileName = extractFileName(toolInput.filePath || '')
      let lineInfo = ''
      if (toolInput.offset !== undefined && toolInput.maxLines !== undefined) {
        const start = toolInput.offset
        const end = toolInput.offset + toolInput.maxLines - 1
        lineInfo = `:${start}-${end}`
      } else if (toolInput.offset !== undefined) {
        lineInfo = `:${toolInput.offset}+`
      }
      primaryInfo = fileName + lineInfo
      secondaryInfo = toolInput.filePath || ''
      break
    }

    case 'mcp__ide-file__WriteFile': {
      // 文件写入：显示文件名 (行数)
      const fileName = extractFileName(toolInput.filePath || '')
      const content = toolInput.content || ''
      if (content) {
        const lineCount = content.split('\n').length
        primaryInfo = `${fileName} (${t('tools.writeTool.lines', { n: lineCount })})`
        addedLines = lineCount || undefined
      } else {
        primaryInfo = fileName
      }
      secondaryInfo = toolInput.filePath || ''
      break
    }

    case 'mcp__ide-file__EditFile': {
      // 文件编辑：显示文件名
      const fileName = extractFileName(toolInput.filePath || '')
      primaryInfo = fileName
      secondaryInfo = toolInput.filePath || ''
      if (toolInput.oldString || toolInput.newString) {
        const oldLines = (toolInput.oldString || '').toString().split(/\r?\n/).length
        const newLines = (toolInput.newString || '').toString().split(/\r?\n/).length
        removedLines = oldLines || undefined
        addedLines = newLines || undefined
        const diff = newLines - oldLines
        if (diff !== 0) {
          lineChanges = diff > 0 ? `+${diff}` : `${diff}`
        }
      }
      break
    }

    default:
      // 通用处理
      primaryInfo = extractGenericPrimaryInfo(toolInput)
      secondaryInfo = extractGenericSecondaryInfo(toolInput)
    }
  }

  // 提取错误信息（直接使用后端格式：result.content）
  let errorMessage: string | undefined
  if (status === 'error' && result) {
    if (typeof result.content === 'string') {
      errorMessage = result.content
    } else if (Array.isArray(result.content)) {
      // 如果 content 是数组，提取文本内容
      const textContent = (result.content as any[])
        .filter((item: any) => item.type === 'text')
        .map((item: any) => item.text)
        .join('\n')
      if (textContent) {
        errorMessage = textContent
      }
    }
  }

  return {
    icon,
    actionType,
    primaryInfo,
    secondaryInfo,
    lineChanges,
    addedLines,
    removedLines,
    readLines,
    status,
    isInputLoading,
    errorMessage,
  }
}

/**
 * 格式化 Read 工具的主要信息（文件名:行号范围）
 */
function formatReadPrimaryInfo(input: any): string {
  const fileName = extractFileName(input.path || input.file_path || '')
  let lineInfo = ''

  // 优先使用 view_range
  if (input.view_range && Array.isArray(input.view_range)) {
    const [start, end] = input.view_range
    lineInfo = `:${start}-${end}`
  }
  // 其次使用 offset 和 limit
  else if (input.offset !== undefined && input.limit !== undefined) {
    const start = input.offset
    const end = input.offset + input.limit - 1
    lineInfo = `:${start}-${end}`
  }
  // 只有 offset
  else if (input.offset !== undefined) {
    lineInfo = `:${input.offset}+`
  }

  return fileName + lineInfo
}

/**
 * 格式化 Write 工具的主要信息（文件名 (行数)）
 */
function formatWritePrimaryInfo(input: any): string {
  const fileName = extractFileName(input.path || input.file_path || '')
  const content = input.content || input.file_content || ''

  if (!content) {
    return fileName
  }

  const lineCount = content.split('\n').length
  return `${fileName} (${t('tools.writeTool.lines', { n: lineCount })})`
}

/**
 * 格式化 Edit 工具的主要信息（文件名:行号）
 */
function formatEditPrimaryInfo(input: any): string {
  const fileName = extractFileName(input.file_path || input.path || '')

  // 尝试从 old_str_start_line_number_1 获取行号
  const lineNumber = input.old_str_start_line_number_1 || input.old_str_start_line_number

  if (lineNumber) {
    return `${fileName}:${lineNumber}`
  }

  return fileName
}

/**
 * 格式化 Bash 命令
 */
function formatBashCommand(command: string): string {
  if (!command) return ''
  const maxLength = 50
  if (command.length <= maxLength) return command
  return command.substring(0, maxLength - 3) + '...'
}

/**
 * 格式化 Grep 信息
 */
function formatGrepInfo(input: any): string {
  const pattern = input.pattern || input.search_query_regex || ''
  const path = input.path || input.glob || ''

  if (path) {
    return `"${pattern}" in ${simplifyPath(path)}`
  }
  return `"${pattern}"`
}

/**
 * 格式化 Glob 信息
 */
function formatGlobInfo(input: any): string {
  const pattern = input.pattern || ''
  const path = input.path || ''

  if (path) {
    return `${pattern} in ${simplifyPath(path)}`
  }
  return pattern
}

/**
 * 简化 URL（只显示域名和路径）
 */
function simplifyUrl(url: string): string {
  if (!url) return ''
  try {
    const urlObj = new URL(url)
    const path = urlObj.pathname === '/' ? '' : urlObj.pathname
    return urlObj.hostname + path
  } catch {
    return truncateText(url, 50)
  }
}

/**
 * 简化路径（保留关键部分）
 */
function simplifyPath(path: string, maxLength: number = 30): string {
  if (!path || path.length <= maxLength) return path

  // 如果是文件路径，优先保留文件名
  const parts = path.split(/[\\/]/)
  const fileName = parts[parts.length - 1]

  if (fileName && fileName.length < maxLength - 4) {
    return '.../' + fileName
  }

  return path.substring(0, maxLength - 3) + '...'
}

/**
 * 截断文本
 */
function truncateText(text: string, maxLength: number): string {
  if (!text || text.length <= maxLength) return text
  return text.substring(0, maxLength - 3) + '...'
}

/**
 * 提取文件名（支持 Windows 和 Unix 路径分隔符）
 */
function extractFileName(path: string): string {
  if (!path) return ''
  // 同时支持 / 和 \ 两种路径分隔符
  const parts = path.split(/[\\/]/)
  return parts[parts.length - 1] || path
}

/**
 * 提取文件路径（去掉文件名）
 */
function extractFilePath(path: string): string {
  if (!path) return ''
  const parts = path.split('/')
  if (parts.length <= 1) return ''
  return parts.slice(0, -1).join('/')
}

/**
 * 计算行数变化
 */
function calculateLineChanges(input: any): string | undefined {
  const oldString = input.old_string || input.old_str
  const newString = input.new_string || input.new_str

  if (!oldString || !newString) {
    return undefined
  }

  const oldLines = oldString.split('\n').length
  const newLines = newString.split('\n').length
  const diff = newLines - oldLines

  if (diff === 0) return undefined
  if (diff > 0) return `+${diff}`
  return `${diff}`
}

/**
 * 提取通用主要信息
 */
function extractGenericPrimaryInfo(input: any): string {
  if (input.path) return extractFileName(input.path)
  if (input.command) return `$ ${input.command}`
  if (input.query) return input.query
  if (input.message) return input.message
  return ''
}

/**
 * 提取通用次要信息
 */
function extractGenericSecondaryInfo(input: any): string {
  if (input.path) return extractFilePath(input.path)
  if (input.cwd) return input.cwd
  if (input.description) return input.description
  return ''
}
