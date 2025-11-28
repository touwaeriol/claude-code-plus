/**
 * 工具类型常量定义
 *
 * 命名规范:
 * - Claude SDK 工具: CLAUDE_<TOOL> (如 CLAUDE_READ, CLAUDE_EDIT)
 * - MCP 工具: MCP
 * - 未知工具: UNKNOWN
 */

/**
 * Claude SDK 工具类型常量
 */
export const CLAUDE_TOOL_TYPE = {
  READ: 'CLAUDE_READ',
  WRITE: 'CLAUDE_WRITE',
  EDIT: 'CLAUDE_EDIT',
  MULTI_EDIT: 'CLAUDE_MULTI_EDIT',
  BASH: 'CLAUDE_BASH',
  BASH_OUTPUT: 'CLAUDE_BASH_OUTPUT',
  KILL_SHELL: 'CLAUDE_KILL_SHELL',
  GREP: 'CLAUDE_GREP',
  GLOB: 'CLAUDE_GLOB',
  TODO_WRITE: 'CLAUDE_TODO_WRITE',
  WEB_SEARCH: 'CLAUDE_WEB_SEARCH',
  WEB_FETCH: 'CLAUDE_WEB_FETCH',
  TASK: 'CLAUDE_TASK',
  ASK_USER_QUESTION: 'CLAUDE_ASK_USER_QUESTION',
  NOTEBOOK_EDIT: 'CLAUDE_NOTEBOOK_EDIT',
  EXIT_PLAN_MODE: 'CLAUDE_EXIT_PLAN_MODE',
  ENTER_PLAN_MODE: 'CLAUDE_ENTER_PLAN_MODE',
  SKILL: 'CLAUDE_SKILL',
  SLASH_COMMAND: 'CLAUDE_SLASH_COMMAND',
  LIST_MCP_RESOURCES: 'CLAUDE_LIST_MCP_RESOURCES',
  READ_MCP_RESOURCE: 'CLAUDE_READ_MCP_RESOURCE',
} as const

/**
 * 其他工具类型常量
 */
export const OTHER_TOOL_TYPE = {
  MCP: 'MCP',
  UNKNOWN: 'UNKNOWN',
} as const

/**
 * Claude 工具类型联合类型
 */
export type ClaudeToolType = typeof CLAUDE_TOOL_TYPE[keyof typeof CLAUDE_TOOL_TYPE]

/**
 * 其他工具类型联合类型
 */
export type OtherToolType = typeof OTHER_TOOL_TYPE[keyof typeof OTHER_TOOL_TYPE]

/**
 * 所有工具类型联合类型
 */
export type ToolType = ClaudeToolType | OtherToolType

/**
 * 工具名称到类型的映射（用于前端本地转换，当后端未提供 toolType 时使用）
 */
export const TOOL_NAME_TO_TYPE: Record<string, ToolType> = {
  'Read': CLAUDE_TOOL_TYPE.READ,
  'Write': CLAUDE_TOOL_TYPE.WRITE,
  'Edit': CLAUDE_TOOL_TYPE.EDIT,
  'MultiEdit': CLAUDE_TOOL_TYPE.MULTI_EDIT,
  'Bash': CLAUDE_TOOL_TYPE.BASH,
  'BashOutput': CLAUDE_TOOL_TYPE.BASH_OUTPUT,
  'KillShell': CLAUDE_TOOL_TYPE.KILL_SHELL,
  'Grep': CLAUDE_TOOL_TYPE.GREP,
  'Glob': CLAUDE_TOOL_TYPE.GLOB,
  'TodoWrite': CLAUDE_TOOL_TYPE.TODO_WRITE,
  'WebSearch': CLAUDE_TOOL_TYPE.WEB_SEARCH,
  'WebFetch': CLAUDE_TOOL_TYPE.WEB_FETCH,
  'Task': CLAUDE_TOOL_TYPE.TASK,
  'AskUserQuestion': CLAUDE_TOOL_TYPE.ASK_USER_QUESTION,
  'NotebookEdit': CLAUDE_TOOL_TYPE.NOTEBOOK_EDIT,
  'ExitPlanMode': CLAUDE_TOOL_TYPE.EXIT_PLAN_MODE,
  'EnterPlanMode': CLAUDE_TOOL_TYPE.ENTER_PLAN_MODE,
  'Skill': CLAUDE_TOOL_TYPE.SKILL,
  'SlashCommand': CLAUDE_TOOL_TYPE.SLASH_COMMAND,
  'ListMcpResourcesTool': CLAUDE_TOOL_TYPE.LIST_MCP_RESOURCES,
  'ReadMcpResourceTool': CLAUDE_TOOL_TYPE.READ_MCP_RESOURCE,
}

/**
 * 辅助函数：根据工具名称解析类型
 *
 * @param name 工具名称（如 "Read", "mcp__excel__read"）
 * @returns 工具类型标识（如 "CLAUDE_READ", "MCP"）
 */
export function resolveToolType(name: string): ToolType {
  if (name.startsWith('mcp__')) {
    return OTHER_TOOL_TYPE.MCP
  }
  return TOOL_NAME_TO_TYPE[name] || OTHER_TOOL_TYPE.UNKNOWN
}
