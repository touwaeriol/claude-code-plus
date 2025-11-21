/**
 * 工具类型常量定义
 */
export const TOOL_TYPE = {
  READ: 'Read',
  WRITE: 'Write',
  EDIT: 'Edit',
  MULTI_EDIT: 'MultiEdit',
  TODO_WRITE: 'TodoWrite',
  BASH: 'Bash',
  GREP: 'Grep',
  GLOB: 'Glob',
  WEB_SEARCH: 'WebSearch',
  WEB_FETCH: 'WebFetch',
  // 仅用于占位或未知
  GENERIC: 'Generic'
} as const;

// 导出 ToolType 类型，等同于所有 value 的联合类型
export type ToolType = typeof TOOL_TYPE[keyof typeof TOOL_TYPE];

