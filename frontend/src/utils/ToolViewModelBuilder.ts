/**
 * 工具 ViewModel 构建器
 *
 * 负责从 SDK 的 ToolUseBlock 构建前端 UI 所需的 ViewModel
 * 参考后端 ToolCallMapper 的实现逻辑
 */

import type { ToolUseBlock } from '@/types/message'

/**
 * 工具类型枚举
 */
export enum UiToolType {
  BASH = 'BASH',
  EDIT = 'EDIT',
  MULTI_EDIT = 'MULTI_EDIT',
  READ = 'READ',
  WRITE = 'WRITE',
  GLOB = 'GLOB',
  GREP = 'GREP',
  TODO_WRITE = 'TODO_WRITE',
  TASK = 'TASK',
  WEB_FETCH = 'WEB_FETCH',
  WEB_SEARCH = 'WEB_SEARCH',
  NOTEBOOK_EDIT = 'NOTEBOOK_EDIT',
  MCP = 'MCP',
  BASH_OUTPUT = 'BASH_OUTPUT',
  KILL_SHELL = 'KILL_SHELL',
  EXIT_PLAN_MODE = 'EXIT_PLAN_MODE',
  LIST_MCP_RESOURCES = 'LIST_MCP_RESOURCES',
  READ_MCP_RESOURCE = 'READ_MCP_RESOURCE',
  ASK_USER_QUESTION = 'ASK_USER_QUESTION',
  SLASH_COMMAND = 'SLASH_COMMAND',
  SKILL = 'SKILL',
  UNKNOWN = 'UNKNOWN',
}

/**
 * 内置工具专用 ViewModel 类型定义
 */

/** Read 工具参数 */
export interface ReadToolParameters {
  file_path?: string
  path?: string
  offset?: number
  limit?: number
  view_range?: [number, number]
}

/** Write 工具参数 */
export interface WriteToolParameters {
  file_path?: string
  path?: string
  content: string
}

/** Edit 工具参数 */
export interface EditToolParameters {
  file_path: string
  old_string: string
  new_string: string
  replace_all?: boolean
}

/** MultiEdit 工具参数 */
export interface MultiEditToolParameters {
  file_path: string
  edits: Array<{
    old_string: string
    new_string: string
  }>
}

/** Bash 工具参数 */
export interface BashToolParameters {
  command: string
  description?: string
  cwd?: string
  timeout?: number
}

/** Grep 工具参数 */
export interface GrepToolParameters {
  pattern: string
  path?: string
  glob?: string
  type?: string
  output_mode?: 'content' | 'files_with_matches' | 'count'
}

/** Glob 工具参数 */
export interface GlobToolParameters {
  pattern: string
  path?: string
}

/** TodoWrite 工具参数 */
export interface TodoWriteToolParameters {
  todos: Array<{
    content: string
    status: 'pending' | 'in_progress' | 'completed'
    activeForm: string
  }>
}

/** Task 工具参数 */
export interface TaskToolParameters {
  description: string
  prompt: string
  subagent_type: string
  model?: string
}

/** WebSearch 工具参数 */
export interface WebSearchToolParameters {
  query: string
  allowed_domains?: string[]
  blocked_domains?: string[]
}

/** WebFetch 工具参数 */
export interface WebFetchToolParameters {
  url: string
  prompt: string
}

/** NotebookEdit 工具参数 */
export interface NotebookEditToolParameters {
  notebook_path: string
  cell_id?: string
  cell_number?: number
  new_source: string
  cell_type?: 'code' | 'markdown'
  edit_mode?: 'replace' | 'insert' | 'delete'
}

/**
 * 工具详情 ViewModel - 支持类型安全的内置工具和通用的 MCP 工具
 */
export type ToolDetailViewModel =
  | { toolType: UiToolType.READ; parameters: ReadToolParameters; compactSummary: string }
  | { toolType: UiToolType.WRITE; parameters: WriteToolParameters; compactSummary: string }
  | { toolType: UiToolType.EDIT; parameters: EditToolParameters; compactSummary: string }
  | { toolType: UiToolType.MULTI_EDIT; parameters: MultiEditToolParameters; compactSummary: string }
  | { toolType: UiToolType.BASH; parameters: BashToolParameters; compactSummary: string }
  | { toolType: UiToolType.GREP; parameters: GrepToolParameters; compactSummary: string }
  | { toolType: UiToolType.GLOB; parameters: GlobToolParameters; compactSummary: string }
  | { toolType: UiToolType.TODO_WRITE; parameters: TodoWriteToolParameters; compactSummary: string }
  | { toolType: UiToolType.TASK; parameters: TaskToolParameters; compactSummary: string }
  | { toolType: UiToolType.WEB_SEARCH; parameters: WebSearchToolParameters; compactSummary: string }
  | { toolType: UiToolType.WEB_FETCH; parameters: WebFetchToolParameters; compactSummary: string }
  | { toolType: UiToolType.NOTEBOOK_EDIT; parameters: NotebookEditToolParameters; compactSummary: string }
  | { toolType: UiToolType.MCP | UiToolType.UNKNOWN; parameters: Record<string, any>; compactSummary: string }
  | { toolType: Exclude<UiToolType, UiToolType.READ | UiToolType.WRITE | UiToolType.EDIT | UiToolType.MULTI_EDIT | UiToolType.BASH | UiToolType.GREP | UiToolType.GLOB | UiToolType.TODO_WRITE | UiToolType.TASK | UiToolType.WEB_SEARCH | UiToolType.WEB_FETCH | UiToolType.NOTEBOOK_EDIT | UiToolType.MCP | UiToolType.UNKNOWN>; parameters: Record<string, any>; compactSummary: string }

/**
 * 工具调用 ViewModel
 */
export interface ToolCallViewModel {
  id: string
  name: string
  toolDetail: ToolDetailViewModel
  compactSummary: string
}

/**
 * 工具名称到类型的映射
 */
const TOOL_NAME_TO_TYPE: Record<string, UiToolType> = {
  'Bash': UiToolType.BASH,
  'Edit': UiToolType.EDIT,
  'MultiEdit': UiToolType.MULTI_EDIT,
  'Read': UiToolType.READ,
  'Write': UiToolType.WRITE,
  'Glob': UiToolType.GLOB,
  'Grep': UiToolType.GREP,
  'TodoWrite': UiToolType.TODO_WRITE,
  'Task': UiToolType.TASK,
  'WebFetch': UiToolType.WEB_FETCH,
  'WebSearch': UiToolType.WEB_SEARCH,
  'NotebookEdit': UiToolType.NOTEBOOK_EDIT,
  'BashOutput': UiToolType.BASH_OUTPUT,
  'KillShell': UiToolType.KILL_SHELL,
  'ExitPlanMode': UiToolType.EXIT_PLAN_MODE,
  'ListMcpResourcesTool': UiToolType.LIST_MCP_RESOURCES,
  'ReadMcpResourceTool': UiToolType.READ_MCP_RESOURCE,
  'AskUserQuestion': UiToolType.ASK_USER_QUESTION,
  'SlashCommand': UiToolType.SLASH_COMMAND,
  'Skill': UiToolType.SKILL,
}

/**
 * 从 ToolUseBlock 构建 ViewModel
 */
export function buildToolViewModel(block: ToolUseBlock): ToolCallViewModel {
  const toolType = TOOL_NAME_TO_TYPE[block.name] || UiToolType.UNKNOWN
  const toolDetail = createToolDetail(toolType, block.input)

  return {
    id: block.id,
    name: block.name,
    toolDetail,
    compactSummary: toolDetail.compactSummary
  }
}

/**
 * 创建工具详情 ViewModel
 */
function createToolDetail(toolType: UiToolType, input: Record<string, any>): ToolDetailViewModel {
  const parameters = input || {}
  let compactSummary = ''

  switch (toolType) {
    case UiToolType.READ:
      compactSummary = extractReadSummary(parameters)
      break

    case UiToolType.WRITE:
      compactSummary = extractWriteSummary(parameters)
      break

    case UiToolType.EDIT:
      compactSummary = extractEditSummary(parameters)
      break

    case UiToolType.MULTI_EDIT:
      compactSummary = extractMultiEditSummary(parameters)
      break

    case UiToolType.BASH:
      compactSummary = extractBashSummary(parameters)
      break

    case UiToolType.GREP:
      compactSummary = extractGrepSummary(parameters)
      break

    case UiToolType.GLOB:
      compactSummary = extractGlobSummary(parameters)
      break

    case UiToolType.TODO_WRITE:
      compactSummary = extractTodoWriteSummary(parameters)
      break

    case UiToolType.TASK:
      compactSummary = extractTaskSummary(parameters)
      break

    case UiToolType.WEB_SEARCH:
      compactSummary = extractWebSearchSummary(parameters)
      break

    case UiToolType.WEB_FETCH:
      compactSummary = extractWebFetchSummary(parameters)
      break

    case UiToolType.NOTEBOOK_EDIT:
      compactSummary = extractNotebookEditSummary(parameters)
      break

    case UiToolType.ASK_USER_QUESTION:
      compactSummary = extractAskUserQuestionSummary(parameters)
      break

    case UiToolType.BASH_OUTPUT:
      compactSummary = extractBashOutputSummary(parameters)
      break

    case UiToolType.KILL_SHELL:
      compactSummary = extractKillShellSummary(parameters)
      break

    case UiToolType.EXIT_PLAN_MODE:
      compactSummary = extractExitPlanModeSummary(parameters)
      break

    case UiToolType.SLASH_COMMAND:
      compactSummary = extractSlashCommandSummary(parameters)
      break

    case UiToolType.SKILL:
      compactSummary = extractSkillSummary(parameters)
      break

    case UiToolType.MCP:
    case UiToolType.LIST_MCP_RESOURCES:
    case UiToolType.READ_MCP_RESOURCE:
      compactSummary = extractMcpSummary(parameters)
      break

    default:
      compactSummary = extractGenericSummary(parameters)
  }

  return {
    toolType,
    parameters,
    compactSummary
  }
}

// ==================== 摘要提取函数 ====================

function extractReadSummary(params: Record<string, any>): string {
  const filePath = params.file_path || params.filePath || ''
  const fileName = extractFileName(filePath)

  if (params.offset !== undefined && params.limit !== undefined) {
    const start = params.offset || 0
    const end = start + (params.limit || 0)
    return `${fileName} lines ${start + 1}-${end}`
  }

  return `${fileName}`
}

function extractWriteSummary(params: Record<string, any>): string {
  const filePath = params.file_path || params.filePath || ''
  const fileName = extractFileName(filePath)
  const contentLength = (params.content || '').length

  if (contentLength > 0) {
    return `${fileName} (${contentLength} chars)`
  }

  return `${fileName}`
}

function extractEditSummary(params: Record<string, any>): string {
  const filePath = params.file_path || params.filePath || ''
  const fileName = extractFileName(filePath)

  const oldString = params.old_string || params.oldString || ''
  const newString = params.new_string || params.newString || ''

  const oldLines = oldString.split('\n').length
  const newLines = newString.split('\n').length
  const diff = newLines - oldLines

  if (diff !== 0) {
    const sign = diff > 0 ? '+' : ''
    return `${fileName} ${sign}${diff} lines`
  }

  return `${fileName}`
}

function extractMultiEditSummary(params: Record<string, any>): string {
  const filePath = params.file_path || params.filePath || ''
  const fileName = extractFileName(filePath)
  const edits = params.edits || []

  return `${fileName} (${edits.length} edits)`
}

function extractBashSummary(params: Record<string, any>): string {
  const command = params.command || ''
  const description = params.description || ''

  if (description) {
    return truncate(description, 50)
  }

  return `$ ${truncate(command, 50)}`
}

function extractGrepSummary(params: Record<string, any>): string {
  const pattern = params.pattern || ''
  const path = params.path || ''
  const glob = params.glob || ''

  let location = 'all files'
  if (path) {
    location = extractFileName(path)
  } else if (glob) {
    location = glob
  }

  return `"${truncate(pattern, 30)}" in ${location}`
}

function extractGlobSummary(params: Record<string, any>): string {
  const pattern = params.pattern || ''
  const path = params.path || ''

  if (path) {
    return `${pattern} in ${extractFileName(path)}`
  }

  return pattern
}

function extractTodoWriteSummary(params: Record<string, any>): string {
  const todos = params.todos || []
  return `${todos.length} todo items`
}

function extractTaskSummary(params: Record<string, any>): string {
  const description = params.description || ''
  const subagentType = params.subagent_type || params.subagentType || ''

  if (description) {
    return `${subagentType}: ${truncate(description, 40)}`
  }

  return subagentType
}

function extractWebSearchSummary(params: Record<string, any>): string {
  const query = params.query || ''
  return `"${truncate(query, 50)}"`
}

function extractWebFetchSummary(params: Record<string, any>): string {
  const url = params.url || ''
  return truncate(url, 50)
}

function extractNotebookEditSummary(params: Record<string, any>): string {
  const notebookPath = params.notebook_path || params.notebookPath || ''
  const fileName = extractFileName(notebookPath)
  const cellId = params.cell_id || params.cellId || ''

  if (cellId) {
    return `${fileName} cell ${cellId}`
  }

  return fileName
}

function extractAskUserQuestionSummary(params: Record<string, any>): string {
  const questions = params.questions || []

  if (questions.length > 0) {
    const firstQuestion = questions[0].question || ''
    return truncate(firstQuestion, 50)
  }

  return 'Ask user'
}

function extractBashOutputSummary(params: Record<string, any>): string {
  const bashId = params.bash_id || params.bashId || ''
  return `shell ${bashId}`
}

function extractKillShellSummary(params: Record<string, any>): string {
  const shellId = params.shell_id || params.shellId || ''
  return `shell ${shellId}`
}

function extractExitPlanModeSummary(params: Record<string, any>): string {
  const plan = params.plan || ''
  return truncate(plan, 50)
}

function extractSlashCommandSummary(params: Record<string, any>): string {
  const command = params.command || ''
  return command
}

function extractSkillSummary(params: Record<string, any>): string {
  const skill = params.skill || ''
  return skill
}

function extractMcpSummary(params: Record<string, any>): string {
  const serverName = params.server || params.serverName || ''
  const toolName = params.tool_name || params.toolName || ''

  if (serverName && toolName) {
    return `${serverName}:${toolName}`
  }

  if (serverName) {
    return serverName
  }

  return 'MCP tool'
}

function extractGenericSummary(params: Record<string, any>): string {
  // 尝试从常见字段提取信息
  if (params.description) {
    return truncate(params.description, 50)
  }

  if (params.message) {
    return truncate(params.message, 50)
  }

  if (params.command) {
    return truncate(params.command, 50)
  }

  if (params.query) {
    return truncate(params.query, 50)
  }

  if (params.path || params.file_path || params.filePath) {
    const path = params.path || params.file_path || params.filePath
    return extractFileName(path)
  }

  return 'Tool execution'
}

// ==================== 辅助函数 ====================

/**
 * 从路径中提取文件名
 */
function extractFileName(path: string): string {
  if (!path) return ''

  // 处理 Windows 和 Unix 路径
  const parts = path.replace(/\\/g, '/').split('/')
  return parts[parts.length - 1] || path
}

/**
 * 截断字符串
 */
function truncate(str: string, maxLength: number): string {
  if (!str) return ''
  if (str.length <= maxLength) return str
  return str.substring(0, maxLength) + '...'
}
