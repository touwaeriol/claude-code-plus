/**
 * Tool types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/ToolTypes.kt
 */

import type { JsonValue, JsonObject } from './common';
import type { ToolUseLike, ContentBlock } from './contentBlocks';

/**
 * Tool type enum.
 *
 * Defines all known Claude Code tool types.
 */
export const ToolType = {
  // Basic file operation tools
  BASH: { toolName: 'Bash', type: 'CLAUDE_BASH' },
  BASH_OUTPUT: { toolName: 'BashOutput', type: 'CLAUDE_BASH_OUTPUT' },
  KILL_SHELL: { toolName: 'KillShell', type: 'CLAUDE_KILL_SHELL' },
  EDIT: { toolName: 'Edit', type: 'CLAUDE_EDIT' },
  MULTI_EDIT: { toolName: 'MultiEdit', type: 'CLAUDE_MULTI_EDIT' },
  READ: { toolName: 'Read', type: 'CLAUDE_READ' },
  WRITE: { toolName: 'Write', type: 'CLAUDE_WRITE' },

  // Search and find tools
  GLOB: { toolName: 'Glob', type: 'CLAUDE_GLOB' },
  GREP: { toolName: 'Grep', type: 'CLAUDE_GREP' },

  // Network tools
  WEB_FETCH: { toolName: 'WebFetch', type: 'CLAUDE_WEB_FETCH' },
  WEB_SEARCH: { toolName: 'WebSearch', type: 'CLAUDE_WEB_SEARCH' },

  // Development and task management tools
  TODO_WRITE: { toolName: 'TodoWrite', type: 'CLAUDE_TODO_WRITE' },
  TASK: { toolName: 'Task', type: 'CLAUDE_TASK' },
  EXIT_PLAN_MODE: { toolName: 'ExitPlanMode', type: 'CLAUDE_EXIT_PLAN_MODE' },
  ASK_USER_QUESTION: { toolName: 'AskUserQuestion', type: 'CLAUDE_ASK_USER_QUESTION' },
  SKILL: { toolName: 'Skill', type: 'CLAUDE_SKILL' },
  SLASH_COMMAND: { toolName: 'SlashCommand', type: 'CLAUDE_SLASH_COMMAND' },
  ENTER_PLAN_MODE: { toolName: 'EnterPlanMode', type: 'CLAUDE_ENTER_PLAN_MODE' },

  // Jupyter notebook tool
  NOTEBOOK_EDIT: { toolName: 'NotebookEdit', type: 'CLAUDE_NOTEBOOK_EDIT' },

  // MCP (Model Context Protocol) tools
  MCP_TOOL: { toolName: 'mcp__', type: 'MCP' },
  LIST_MCP_RESOURCES: { toolName: 'ListMcpResourcesTool', type: 'CLAUDE_LIST_MCP_RESOURCES' },
  READ_MCP_RESOURCE: { toolName: 'ReadMcpResourceTool', type: 'CLAUDE_READ_MCP_RESOURCE' },

  // Unknown tool type
  UNKNOWN: { toolName: 'unknown', type: 'UNKNOWN' },
} as const;

export type ToolTypeInfo = (typeof ToolType)[keyof typeof ToolType];
export type ToolTypeName = keyof typeof ToolType;

/**
 * Get tool type from tool name.
 */
export function getToolType(toolName: string): ToolTypeInfo {
  if (toolName.startsWith('mcp__')) {
    return ToolType.MCP_TOOL;
  }
  const entry = Object.values(ToolType).find((t) => t.toolName === toolName);
  return entry ?? ToolType.UNKNOWN;
}

// ============================================================================
// Specific Tool Use Types
// ============================================================================

/**
 * Base interface for specific tool use.
 *
 * This interface inherits from ContentBlock to ensure backward compatibility.
 * All specific tool classes should implement this interface.
 *
 * Note:
 * - name field contains tool name (like "TodoWrite", "Edit", "Write", etc.)
 * - input field contains raw tool parameters (matches Claude API format)
 * - When serializing to JSON for frontend, should manually construct {type: "tool_use", name: "...", id: "...", input: {...}} format
 */
export interface SpecificToolUse extends ToolUseLike {
  /** Tool call ID */
  id: string;
  /** Tool name, like "TodoWrite", "Edit", "Write", etc. */
  name: string;
  /** Raw parameters (matches Claude API format) */
  input: JsonValue;
  /** Internal enum type, not serialized to JSON */
  toolType: ToolTypeInfo;
}

/**
 * Bash tool use - execute shell commands.
 */
export interface BashToolUse extends SpecificToolUse {
  name: 'Bash';
  toolType: typeof ToolType.BASH;
  command: string;
  description?: string;
  timeout?: number;
  runInBackground?: boolean;
}

/**
 * Edit tool use - edit file content.
 */
export interface EditToolUse extends SpecificToolUse {
  name: 'Edit';
  toolType: typeof ToolType.EDIT;
  filePath: string;
  oldString: string;
  newString: string;
  replaceAll?: boolean;
}

/**
 * Edit operation for MultiEdit.
 */
export interface EditOperation {
  oldString: string;
  newString: string;
  replaceAll?: boolean;
}

/**
 * MultiEdit tool use - multiple edits on a single file.
 */
export interface MultiEditToolUse extends SpecificToolUse {
  name: 'MultiEdit';
  toolType: typeof ToolType.MULTI_EDIT;
  filePath: string;
  edits: EditOperation[];
}

/**
 * Read tool use - read file content.
 */
export interface ReadToolUse extends SpecificToolUse {
  name: 'Read';
  toolType: typeof ToolType.READ;
  filePath: string;
  offset?: number;
  limit?: number;
}

/**
 * Write tool use - write file content.
 */
export interface WriteToolUse extends SpecificToolUse {
  name: 'Write';
  toolType: typeof ToolType.WRITE;
  filePath: string;
  content: string;
}

/**
 * Glob tool use - file pattern matching.
 */
export interface GlobToolUse extends SpecificToolUse {
  name: 'Glob';
  toolType: typeof ToolType.GLOB;
  pattern: string;
  path?: string;
}

/**
 * Grep tool use - text search.
 */
export interface GrepToolUse extends SpecificToolUse {
  name: 'Grep';
  toolType: typeof ToolType.GREP;
  pattern: string;
  path?: string;
  outputMode?: string;
  glob?: string;
  type?: string;
  caseInsensitive?: boolean;
  showLineNumbers?: boolean;
  contextBefore?: number;
  contextAfter?: number;
  headLimit?: number;
}

/**
 * WebFetch tool use - fetch web page content.
 */
export interface WebFetchToolUse extends SpecificToolUse {
  name: 'WebFetch';
  toolType: typeof ToolType.WEB_FETCH;
  url: string;
  prompt: string;
}

/**
 * WebSearch tool use - web search.
 */
export interface WebSearchToolUse extends SpecificToolUse {
  name: 'WebSearch';
  toolType: typeof ToolType.WEB_SEARCH;
  query: string;
  allowedDomains?: string[];
  blockedDomains?: string[];
}

/**
 * Todo item for TodoWrite.
 */
export interface TodoItem {
  content: string;
  /** Status: pending, in_progress, completed */
  status: 'pending' | 'in_progress' | 'completed';
  activeForm: string;
}

/**
 * TodoWrite tool use - manage todo list.
 */
export interface TodoWriteToolUse extends SpecificToolUse {
  name: 'TodoWrite';
  toolType: typeof ToolType.TODO_WRITE;
  todos: TodoItem[];
}

/**
 * Task tool use - start subtask agent.
 */
export interface TaskToolUse extends SpecificToolUse {
  name: 'Task';
  toolType: typeof ToolType.TASK;
  description: string;
  prompt: string;
  subagentType: string;
}

/**
 * NotebookEdit tool use - edit Jupyter Notebook.
 */
export interface NotebookEditToolUse extends SpecificToolUse {
  name: 'NotebookEdit';
  toolType: typeof ToolType.NOTEBOOK_EDIT;
  notebookPath: string;
  newSource: string;
  cellId?: string;
  cellType?: string;
  editMode?: string;
}

/**
 * MCP tool use - Model Context Protocol tool generic container.
 */
export interface McpToolUse extends SpecificToolUse {
  toolType: typeof ToolType.MCP_TOOL;
  /** Full tool name, like "mcp__server_name__function_name" */
  fullToolName: string;
  serverName: string;
  functionName: string;
  parameters: JsonObject;
}

/**
 * BashOutput tool use - get Bash command output.
 */
export interface BashOutputToolUse extends SpecificToolUse {
  name: 'BashOutput';
  toolType: typeof ToolType.BASH_OUTPUT;
  bashId: string;
  filter?: string;
}

/**
 * KillShell tool use - terminate running shell process.
 */
export interface KillShellToolUse extends SpecificToolUse {
  name: 'KillShell';
  toolType: typeof ToolType.KILL_SHELL;
  shellId: string;
}

/**
 * ExitPlanMode tool use - exit plan mode.
 */
export interface ExitPlanModeToolUse extends SpecificToolUse {
  name: 'ExitPlanMode';
  toolType: typeof ToolType.EXIT_PLAN_MODE;
  plan: string;
}

/**
 * ListMcpResources tool use - list available MCP server resources.
 */
export interface ListMcpResourcesToolUse extends SpecificToolUse {
  name: 'ListMcpResourcesTool';
  toolType: typeof ToolType.LIST_MCP_RESOURCES;
  server?: string;
}

/**
 * ReadMcpResource tool use - read specified MCP resource.
 */
export interface ReadMcpResourceToolUse extends SpecificToolUse {
  name: 'ReadMcpResourceTool';
  toolType: typeof ToolType.READ_MCP_RESOURCE;
  server: string;
  uri: string;
}

/**
 * Skill tool use - call registered skill (like /codex, /commit, etc.).
 */
export interface SkillToolUse extends SpecificToolUse {
  name: 'Skill';
  toolType: typeof ToolType.SKILL;
  /** Skill name, like "codex" */
  skill: string;
  /** Optional arguments */
  args?: string;
}

/**
 * Unknown tool use - for handling unrecognized tool types.
 */
export interface UnknownToolUse extends SpecificToolUse {
  toolType: typeof ToolType.UNKNOWN;
  toolName: string;
  parameters: JsonObject;
}

/**
 * Union type for all specific tool uses.
 */
export type AnyToolUse =
  | BashToolUse
  | EditToolUse
  | MultiEditToolUse
  | ReadToolUse
  | WriteToolUse
  | GlobToolUse
  | GrepToolUse
  | WebFetchToolUse
  | WebSearchToolUse
  | TodoWriteToolUse
  | TaskToolUse
  | NotebookEditToolUse
  | McpToolUse
  | BashOutputToolUse
  | KillShellToolUse
  | ExitPlanModeToolUse
  | ListMcpResourcesToolUse
  | ReadMcpResourceToolUse
  | SkillToolUse
  | UnknownToolUse;
